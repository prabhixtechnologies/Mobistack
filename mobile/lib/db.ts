import * as SQLite from "expo-sqlite";

/**
 * Bump when the local schema changes and add a matching step in `migrate`.
 * Without this, an app update leaves existing installs on an older schema.
 */
const SCHEMA_VERSION = 3;

let dbPromise: Promise<SQLite.SQLiteDatabase> | null = null;

/**
 * Cached rows and queued operations belong to one person in one workspace. The
 * counter phone is shared — staff hand it over and owners switch shops on it —
 * so every local read and write is namespaced. Without this the next person to
 * sign in sees the previous one's stock, sales and pending queue.
 */
const NO_SCOPE = "none";
let activeScope = NO_SCOPE;

function part(value: string | null | undefined): string {
  return value && value.trim() ? value.trim() : NO_SCOPE;
}

export function setActiveScope(userId: string | null | undefined,
                               workspaceId: string | null | undefined): void {
  activeScope = userId || workspaceId ? `${part(userId)}/${part(workspaceId)}` : NO_SCOPE;
}

export function currentScope(): string {
  return activeScope;
}

function scopedKey(key: string): string {
  return `${activeScope}:${key}`;
}

async function migrate(db: SQLite.SQLiteDatabase): Promise<void> {
  const row = await db.getFirstAsync<{ user_version: number }>("PRAGMA user_version");
  let version = row?.user_version ?? 0;

  if (version < 1) {
    await db.execAsync(`
      CREATE TABLE IF NOT EXISTS kv (
        key TEXT PRIMARY KEY NOT NULL,
        value TEXT NOT NULL
      );
      CREATE TABLE IF NOT EXISTS outbox (
        idempotency_key TEXT PRIMARY KEY NOT NULL,
        type TEXT NOT NULL,
        payload TEXT NOT NULL,
        created_at INTEGER NOT NULL
      );
    `);
    version = 1;
  }

  if (version < 2) {
    // Cached snapshots are keyed per scope from here on, so unscoped rows are
    // unreachable and are dropped. Queued operations are kept: they are adopted
    // by whichever sign-in flushes first, matching prior behaviour.
    await db.execAsync("DELETE FROM kv");
    const columns = await db.getAllAsync<{ name: string }>("PRAGMA table_info(outbox)");
    if (!columns.some((column) => column.name === "workspace_id")) {
      await db.execAsync("ALTER TABLE outbox ADD COLUMN workspace_id TEXT");
    }
    version = 2;
  }

  if (version < 3) {
    // The scope gained the signed-in person, so anything written under a
    // workspace-only scope no longer resolves. Cached rows are dropped, but
    // queued operations are un-scoped instead: they are real work the shop has
    // not been paid for yet, so the next sign-in adopts and sends them.
    await db.execAsync("DELETE FROM kv");
    const columns = await db.getAllAsync<{ name: string }>("PRAGMA table_info(outbox)");
    if (columns.some((column) => column.name === "workspace_id")
        && !columns.some((column) => column.name === "scope_key")) {
      await db.execAsync("ALTER TABLE outbox RENAME COLUMN workspace_id TO scope_key");
    }
    await db.execAsync("UPDATE outbox SET scope_key = NULL");
    version = 3;
  }

  await db.execAsync(`PRAGMA user_version = ${SCHEMA_VERSION}`);
}

export async function getDb(): Promise<SQLite.SQLiteDatabase> {
  if (!dbPromise) {
    dbPromise = (async () => {
      const db = await SQLite.openDatabaseAsync("fixflow.db");
      await db.execAsync("PRAGMA journal_mode = WAL;");
      await migrate(db);
      return db;
    })();
  }
  return dbPromise;
}

export async function kvGet(key: string): Promise<string | null> {
  const db = await getDb();
  const row = await db.getFirstAsync<{ value: string }>(
    "SELECT value FROM kv WHERE key = ?",
    scopedKey(key),
  );
  return row?.value ?? null;
}

export async function kvSet(key: string, value: string): Promise<void> {
  const db = await getDb();
  await db.runAsync("INSERT OR REPLACE INTO kv (key, value) VALUES (?, ?)", scopedKey(key), value);
}
