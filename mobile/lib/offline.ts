import { api } from "./api";
import { kvGet, kvSet } from "./db";
import { flush, pendingCount } from "./outbox";

const VARIANTS = "snapshot.variants";
const SALES = "snapshot.sales";
const REPAIRS = "snapshot.repairs";
const CUSTOMERS = "snapshot.customers";
const DEVICES = "snapshot.devices";
const DASHBOARD = "snapshot.dashboard";
const INBOX = "snapshot.inbox";
const SUPPORT = "snapshot.support";
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

export interface CachedCustomer {
  id: string;
  name: string;
  phone?: string;
  outstandingAmount: number;
}

export interface CachedDevice {
  id: string;
  name: string;
  brandName: string;
  modelCode?: string;
  aliases?: { alias: string }[];
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
  customers?: CachedCustomer[];
  devices?: CachedDevice[];
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

export async function cachedCustomers(): Promise<CachedCustomer[]> {
  return readJson(CUSTOMERS, []);
}

export async function cachedDevices(): Promise<CachedDevice[]> {
  return readJson(DEVICES, []);
}

export async function cachedDashboard(): Promise<CachedDashboard | null> {
  return readJson(DASHBOARD, null);
}

export async function cachedInbox<T>(fallback: T): Promise<T> {
  return readJson(INBOX, fallback);
}

export async function saveInbox(value: unknown): Promise<void> {
  await kvSet(INBOX, JSON.stringify(value));
}

export async function cachedSupport<T>(fallback: T): Promise<T> {
  return readJson(SUPPORT, fallback);
}

export async function saveSupport(value: unknown): Promise<void> {
  await kvSet(SUPPORT, JSON.stringify(value));
}

export async function cachedDeviceView<T>(id: string): Promise<T | null> {
  return readJson(`device.compat.${id}`, null);
}

export async function saveDeviceView(id: string, value: unknown): Promise<void> {
  await kvSet(`device.compat.${id}`, JSON.stringify(value));
}

export async function lastPulledAt(): Promise<string | null> {
  return kvGet(PULLED);
}

function haystack(values: Array<string | undefined>): string {
  return values
    .filter(Boolean)
    .join(" ")
    .toLowerCase();
}

export function searchVariants(rows: CachedVariant[], query: string): CachedVariant[] {
  const q = query.trim().toLowerCase();
  if (!q) {
    return rows;
  }
  return rows.filter((row) => haystack([row.productName, row.variantName, row.sku, row.barcode]).includes(q));
}

export function searchDevices(rows: CachedDevice[], query: string): CachedDevice[] {
  const q = query.trim().toLowerCase();
  if (!q) {
    return rows;
  }
  return rows.filter((row) =>
    haystack([row.brandName, row.name, row.modelCode, ...(row.aliases ?? []).map((alias) => alias.alias)]).includes(q),
  );
}

export async function saveSnapshot(snapshot: Snapshot): Promise<void> {
  if (snapshot.variants) await kvSet(VARIANTS, JSON.stringify(snapshot.variants));
  if (snapshot.sales) await kvSet(SALES, JSON.stringify(snapshot.sales));
  if (snapshot.repairs) await kvSet(REPAIRS, JSON.stringify(snapshot.repairs));
  if (snapshot.customers) await kvSet(CUSTOMERS, JSON.stringify(snapshot.customers));
  if (snapshot.devices) await kvSet(DEVICES, JSON.stringify(snapshot.devices));
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
