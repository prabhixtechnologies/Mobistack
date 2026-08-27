import { api, ApiError } from "./api";
import { kvGet, kvSet } from "./db";
import { flush, pendingCount, type FailedOp } from "./outbox";

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

export interface SyncOutcome {
  pending: number;
  pulledAt: string | null;
  failed: FailedOp[];
  offline: boolean;
  /**
   * Why the server turned the whole batch away — a lapsed plan, most often.
   * Set only when the request reached the server and was refused, so the UI can
   * say what is actually wrong instead of blaming the connection.
   */
  blocked: string | null;
}

/**
 * Sync is triggered from app resume, the home screen, the sales screen and the
 * manual button. Without this guard those can overlap and post the same queued
 * batch twice, so callers share one run instead of starting another.
 */
let syncInFlight: Promise<SyncOutcome> | null = null;

export function syncNow(): Promise<SyncOutcome> {
  if (!syncInFlight) {
    syncInFlight = runSync().finally(() => {
      syncInFlight = null;
    });
  }
  return syncInFlight;
}

async function runSync(): Promise<SyncOutcome> {
  let failed: FailedOp[] = [];
  let offline = false;
  let blocked: string | null = null;
  try {
    failed = (await flush()).failed;
    await pullSnapshot();
  } catch (cause) {
    if (cause instanceof ApiError && cause.permanent) {
      // The server answered and refused, so retrying changes nothing. The queue
      // is left alone: the work is still valid once the shop is paid up again.
      blocked = cause.message;
    } else {
      // Stay on the last local snapshot when the radio is down. The queue is
      // untouched, so nothing is lost and the next run retries it.
      offline = true;
    }
  }
  return { pending: await pendingCount(), pulledAt: await lastPulledAt(), failed, offline, blocked };
}

/**
 * Sends anything queued before the app changes workspace. Switching with work
 * still pending would leave those operations attributed to the wrong shop, so
 * the caller is expected to surface this error rather than switch anyway.
 */
export async function drainBeforeWorkspaceChange(): Promise<void> {
  if ((await pendingCount()) === 0) {
    return;
  }
  const outcome = await syncNow();
  const left = await pendingCount();
  if (left === 0) {
    return;
  }
  if (outcome.blocked) {
    throw new Error(`${outcome.blocked} Your queued work is safe and will send once that is sorted.`);
  }
  if (outcome.failed.length > 0) {
    // A rejected operation never drains on its own, so pointing at the retry
    // button would strand the shopkeeper. More offers a discard.
    throw new Error(
      `The server rejected a queued ${outcome.failed[0].type.toLowerCase()}: ${outcome.failed[0].message}. `
        + "Review it under Sync now in More, then switch shops.",
    );
  }
  throw new Error(
    `${left} offline change${left === 1 ? "" : "s"} still waiting to sync. `
      + "Connect to the internet and sync from More, then switch shops.",
  );
}

export async function loadOrFetch<T>(remote: () => Promise<T>, fallback: () => Promise<T>): Promise<{ data: T; offline: boolean }> {
  try {
    return { data: await remote(), offline: false };
  } catch {
    return { data: await fallback(), offline: true };
  }
}
