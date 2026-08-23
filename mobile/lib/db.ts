import * as SQLite from "expo-sqlite";

let dbPromise: Promise<SQLite.SQLiteDatabase> | null = null;

export async function getDb(): Promise<SQLite.SQLiteDatabase> {
  if (!dbPromise) {
    dbPromise = (async () => {
      const db = await SQLite.openDatabaseAsync("fixflow.db");
      await db.execAsync(`
        PRAGMA journal_mode = WAL;
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
      return db;
    })();
  }
  return dbPromise;
}

export async function kvGet(key: string): Promise<string | null> {
  const db = await getDb();
  const row = await db.getFirstAsync<{ value: string }>("SELECT value FROM kv WHERE key = ?", key);
  return row?.value ?? null;
}

export async function kvSet(key: string, value: string): Promise<void> {
  const db = await getDb();
  await db.runAsync("INSERT OR REPLACE INTO kv (key, value) VALUES (?, ?)", key, value);
}
