import { api } from "./api";
import { kvGet, kvSet } from "./db";
import { flush, pendingCount } from "./outbox";

const VARIANTS = "snapshot.variants";
const SALES = "snapshot.sales";
const REPAIRS = "snapshot.repairs";
const DASHBOARD = "snapshot.dashboard";
const PULLED = "snapshot.pulledAt";

export interface CachedVariant {
  id: string;
  productName: string;
  variantName: string;
  sku: string;
  barcode?: string;
  availableQty: number;
  retailPrice: number;
  stockStatus: string;
}

export interface CachedSale {
  id: string;
  invoiceNumber: string;
  total: number;
  status: string;
}

export interface CachedRepair {
  id: string;
  jobNumber: string;
  status: string;
  problem: string;
  total: number;
  outstanding: number;
}

export interface CachedDashboard {
  sales: { todaySales: number; todayProfit: number; todayTransactions: number };
  repairs: { pending: number };
  inventory: {
    totalProducts: number;
    stockUnits: number;
    stockValueAtCost: number;
    lowStockCount: number;
    outOfStockCount: number;
  };
  alerts: { id: string; message: string; severity: string; productName?: string }[];
}

interface Snapshot {
  pulledAt?: string;
  variants?: CachedVariant[];
  sales?: CachedSale[];
  repairs?: CachedRepair[];
  dashboard?: CachedDashboard;
}

async function readJson<T>(key: string, fallback: T): Promise<T> {
  const raw = await kvGet(key);
  return raw ? (JSON.parse(raw) as T) : fallback;
}

export async function cachedVariants(): Promise<CachedVariant[]> {
  return readJson(VARIANTS, []);
}

export async function cachedSales(): Promise<CachedSale[]> {
  return readJson(SALES, []);
}

export async function cachedRepairs(): Promise<CachedRepair[]> {
  return readJson(REPAIRS, []);
}

export async function cachedDashboard(): Promise<CachedDashboard | null> {
  return readJson(DASHBOARD, null);
}

export async function lastPulledAt(): Promise<string | null> {
  return kvGet(PULLED);
}

export function searchVariants(rows: CachedVariant[], query: string): CachedVariant[] {
  const q = query.trim().toLowerCase();
  if (!q) {
    return rows;
  }
  return rows.filter((row) =>
    [row.productName, row.variantName, row.sku, row.barcode].some((value) =>
      (value ?? "").toLowerCase().includes(q),
    ),
  );
}

export async function saveSnapshot(snapshot: Snapshot): Promise<void> {
  if (snapshot.variants) await kvSet(VARIANTS, JSON.stringify(snapshot.variants));
  if (snapshot.sales) await kvSet(SALES, JSON.stringify(snapshot.sales));
  if (snapshot.repairs) await kvSet(REPAIRS, JSON.stringify(snapshot.repairs));
  if (snapshot.dashboard) await kvSet(DASHBOARD, JSON.stringify(snapshot.dashboard));
  await kvSet(PULLED, snapshot.pulledAt ?? new Date().toISOString());
}

export async function pullSnapshot(): Promise<Snapshot> {
  const snapshot = await api<Snapshot>("/api/v1/sync/snapshot");
  await saveSnapshot(snapshot);
  return snapshot;
}

export async function syncNow(): Promise<{ pending: number; pulledAt: string | null }> {
  try {
    await flush();
    await pullSnapshot();
  } catch {
    /* stay on the last local snapshot when the radio is down */
  }
  return { pending: await pendingCount(), pulledAt: await lastPulledAt() };
}

export async function loadOrFetch<T>(remote: () => Promise<T>, fallback: () => Promise<T>): Promise<{ data: T; offline: boolean }> {
  try {
    return { data: await remote(), offline: false };
  } catch {
    return { data: await fallback(), offline: true };
  }
}
