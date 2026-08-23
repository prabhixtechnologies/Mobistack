import { api } from "./api";
import { getDb } from "./db";

export type OutboxType = "SALE" | "RECEIVE" | "REPAIR";

export interface OutboxOp {
  type: OutboxType;
  idempotencyKey: string;
  sale?: unknown;
  receive?: unknown;
  repair?: unknown;
}

interface SyncResult {
  idempotencyKey: string;
  status: string;
  message?: string;
}

export async function enqueue(op: OutboxOp): Promise<void> {
  const db = await getDb();
  await db.runAsync(
    "INSERT OR REPLACE INTO outbox (idempotency_key, type, payload, created_at) VALUES (?, ?, ?, ?)",
    op.idempotencyKey,
    op.type,
    JSON.stringify(op),
    Date.now(),
  );
}

export async function peek(): Promise<OutboxOp[]> {
  const db = await getDb();
  const rows = await db.getAllAsync<{ payload: string }>(
    "SELECT payload FROM outbox ORDER BY created_at ASC",
  );
  return rows.map((row) => JSON.parse(row.payload) as OutboxOp);
}

export async function pendingCount(): Promise<number> {
  const db = await getDb();
  const row = await db.getFirstAsync<{ n: number }>("SELECT COUNT(*) as n FROM outbox");
  return row?.n ?? 0;
}

export async function flush(): Promise<SyncResult[]> {
  const operations = await peek();
  if (operations.length === 0) {
    return [];
  }
  const results = await api<SyncResult[]>("/api/v1/sync", {
    method: "POST",
    body: JSON.stringify({ operations }),
  });
  const failed = new Set(results.filter((row) => row.status !== "SYNCED").map((row) => row.idempotencyKey));
  const db = await getDb();
  for (const op of operations) {
    if (!failed.has(op.idempotencyKey)) {
      await db.runAsync("DELETE FROM outbox WHERE idempotency_key = ?", op.idempotencyKey);
    }
  }
  return results;
}
