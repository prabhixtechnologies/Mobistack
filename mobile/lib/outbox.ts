import { api } from "./api";
import { currentScope, getDb } from "./db";

export type OutboxType = "SALE" | "RECEIVE" | "REPAIR" | "CUSTOMER" | "REPAIR_STATUS";

export interface OutboxOp {
  type: OutboxType;
  idempotencyKey: string;
  sale?: unknown;
  receive?: unknown;
  repair?: unknown;
  customer?: unknown;
  repairStatus?: { repairId: string; status: string };
}

interface SyncResult {
  idempotencyKey: string;
  status: string;
  message?: string;
}

export interface FailedOp {
  type: OutboxType;
  idempotencyKey: string;
  message: string;
}

export async function enqueue(op: OutboxOp): Promise<void> {
  const db = await getDb();
  await db.runAsync(
    "INSERT OR REPLACE INTO outbox (idempotency_key, type, payload, created_at, scope_key)"
      + " VALUES (?, ?, ?, ?, ?)",
    op.idempotencyKey,
    op.type,
    JSON.stringify(op),
    Date.now(),
    currentScope(),
  );
}

/**
 * Operations for the current sign-in, oldest first. Rows written before local
 * scoping existed carry no scope and are included so they still reach the
 * server rather than being stranded.
 */
export async function peek(): Promise<OutboxOp[]> {
  const db = await getDb();
  const rows = await db.getAllAsync<{ payload: string }>(
    "SELECT payload FROM outbox WHERE scope_key IS NULL OR scope_key = ? ORDER BY created_at ASC",
    currentScope(),
  );
  return rows.map((row) => JSON.parse(row.payload) as OutboxOp);
}

export async function pendingCount(): Promise<number> {
  const db = await getDb();
  const row = await db.getFirstAsync<{ n: number }>(
    "SELECT COUNT(*) as n FROM outbox WHERE scope_key IS NULL OR scope_key = ?",
    currentScope(),
  );
  return row?.n ?? 0;
}

/** Everything still queued, so the UI can show queued rows alongside synced ones. */
export async function pendingOps(): Promise<OutboxOp[]> {
  return peek();
}

/**
 * Sends the queue and removes whatever the server accepted. Anything the server
 * rejected is returned so the caller can tell the user which operation is stuck
 * instead of silently retrying it forever.
 */
export async function flush(): Promise<{ results: SyncResult[]; failed: FailedOp[] }> {
  const operations = await peek();
  if (operations.length === 0) {
    return { results: [], failed: [] };
  }
  const results = await api<SyncResult[]>("/api/v1/sync", {
    method: "POST",
    body: JSON.stringify({ operations }),
  });
  const rejected = new Map(
    results.filter((row) => row.status !== "SYNCED").map((row) => [row.idempotencyKey, row.message ?? "Rejected"]),
  );
  const db = await getDb();
  for (const op of operations) {
    if (!rejected.has(op.idempotencyKey)) {
      await db.runAsync("DELETE FROM outbox WHERE idempotency_key = ?", op.idempotencyKey);
    }
  }
  const failed: FailedOp[] = operations
    .filter((op) => rejected.has(op.idempotencyKey))
    .map((op) => ({
      type: op.type,
      idempotencyKey: op.idempotencyKey,
      message: rejected.get(op.idempotencyKey) ?? "Rejected",
    }));
  return { results, failed };
}

/** Drops one stuck operation after the user has been told why it failed. */
export async function discard(idempotencyKey: string): Promise<void> {
  const db = await getDb();
  await db.runAsync("DELETE FROM outbox WHERE idempotency_key = ?", idempotencyKey);
}
