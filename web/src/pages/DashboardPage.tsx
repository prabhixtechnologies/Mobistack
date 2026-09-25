import { useEffect, useMemo, useState } from "react";
import { Link, Navigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../lib/auth";
import { useAccess } from "../lib/access";
import { api, money, qty } from "../lib/api";
import { humanLabel } from "../lib/labels";
import { openCommandPalette } from "../ui/GlobalSearch";
import { ErrorState } from "../ui/EmptyState";
import type { DashboardResponse, PageResponse, StockAlert } from "../lib/types";

interface SaleRow {
  id: string;
  invoiceNumber: string;
  status: string;
  customerName?: string;
  total: number;
  occurredAt: string;
  items?: { variantName?: string; quantity: number }[];
}

interface RepairRow {
  id: string;
  jobNumber: string;
  status: string;
  customerName?: string;
  deviceName?: string;
  expectedAt?: string;
  createdAt?: string;
}

interface MovementRow {
  id: string;
  type: string;
  quantity: number;
  onHandDelta: number;
  reason?: string;
  referenceLabel?: string;
  occurredAt: string;
}

type Station = "COUNTER" | "BENCH" | "SHELF";

interface DayRow {
  id: string;
  at: string;
  station: Station;
  title: string;
  detail: string;
  value?: string;
  to: string;
}

const ZONE = "Asia/Kolkata";

const SHELF_MOVES = new Set(["IN", "RETURN", "OPENING", "ADJUSTMENT"]);

function firstName(fullName?: string): string {
  return (fullName ?? "").trim().split(/\s+/).filter(Boolean)[0] || "there";
}

function greeting(): string {
  const hour = new Date().getHours();
  if (hour < 12) {
    return "Good morning";
  }
  if (hour < 17) {
    return "Good afternoon";
  }
  return "Good evening";
}

function todayLabel(): string {
  return new Date().toLocaleDateString("en-IN", {
    weekday: "long",
    day: "numeric",
    month: "long",
    timeZone: ZONE,
  });
}

function dayKey(iso?: string): string {
  if (!iso) {
    return "";
  }
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(new Date(iso));
}

function clock(iso: string): string {
  return new Date(iso).toLocaleTimeString("en-IN", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
    timeZone: ZONE,
  });
}

function todayAt(hours: number, minutes: number): string {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(new Date());
  const year = parts.find((part) => part.type === "year")?.value;
  const month = parts.find((part) => part.type === "month")?.value;
  const day = parts.find((part) => part.type === "day")?.value;
  return new Date(
    `${year}-${month}-${day}T${String(hours).padStart(2, "0")}:${String(minutes).padStart(2, "0")}:00+05:30`,
  ).toISOString();
}

function ledgerBenchValue(status: string): string {
  if (status === "READY") {
    return "READY";
  }
  if (status === "WAITING_FOR_PART") {
    return "PART";
  }
  if (status === "IN_REPAIR") {
    return "REPAIR";
  }
  if (status === "DIAGNOSING") {
    return "DIAG";
  }
  if (status === "RECEIVED") {
    return "IN";
  }
  return humanLabel(status).toUpperCase();
}

function walkIn(name?: string): string {
  return name?.trim() || "Walk-in";
}

function saleTitle(sale: SaleRow): string {
  return sale.items?.map((item) => item.variantName).find(Boolean) || walkIn(sale.customerName) || sale.invoiceNumber;
}

function situation(data: DashboardResponse): string {
  const ready = data.repairs.ready;
  const tickets = data.sales.todayTransactions;
  const out = data.inventory.outOfStockCount;
  const low = data.inventory.lowStockCount;
  const pending = data.repairs.pending;
  if (tickets === 0 && pending === 0 && out === 0 && low === 0 && data.alerts.length === 0) {
    return "Your counter is quiet today.";
  }
  if (ready > 0) {
    return ready === 1 ? "One repair is ready for pickup." : `${qty.format(ready)} repairs are ready for pickup.`;
  }
  if (out > 0) {
    return out === 1 ? "One product is sold out." : `${qty.format(out)} products are sold out.`;
  }
  if (tickets > 0) {
    return tickets === 1 ? "One sale so far today." : `${qty.format(tickets)} sales so far today.`;
  }
  if (pending > 0) {
    return pending === 1 ? "One job is open on the bench." : `${qty.format(pending)} jobs are open on the bench.`;
  }
  if (low > 0) {
    return low === 1 ? "One item is running low." : `${qty.format(low)} items are running low.`;
  }
  return "Here is where the shop stands.";
}

function worstSku(alerts: StockAlert[]): StockAlert | null {
  return (
    alerts.find((alert) => alert.severity === "RED") ??
    alerts.find((alert) => alert.severity === "ORANGE") ??
    alerts[0] ??
    null
  );
}

function compactInr(value: number): string {
  const abs = Math.abs(value);
  const sign = value < 0 ? "−" : "";
  if (abs >= 10_000_000) {
    const cr = abs / 10_000_000;
    return `${sign}₹${cr.toFixed(cr >= 10 ? 1 : 2).replace(/\.0$/, "")}Cr`;
  }
  if (abs >= 100_000) {
    const lakh = abs / 100_000;
    return `${sign}₹${lakh.toFixed(1).replace(/\.0$/, "")}L`;
  }
  return money.format(value);
}

function remaining(alert: StockAlert): string | null {
  if (alert.observedValue == null) {
    return null;
  }
  return alert.observedValue === 1 ? "1 remaining" : `${qty.format(alert.observedValue)} remaining`;
}

const PREVIEW_ENV = ((import.meta.env.VITE_ENVIRONMENT as string | undefined) ?? "").toUpperCase();

/** Non-production layout stress test: `?floor=busy`. Never invents shop data in production. */
function busyPreview(data: DashboardResponse): DashboardResponse {
  return {
    ...data,
    sales: { todaySales: 124999, todayProfit: 24580, todayTransactions: 37 },
    repairs: { received: 2, diagnosing: 3, inRepair: 3, ready: 4, delivered: 12, pending: 12 },
    inventory: {
      ...data.inventory,
      totalProducts: 128,
      stockUnits: 640,
      stockValueAtCost: 1864000,
      lowStockCount: 9,
      outOfStockCount: 2,
    },
    alerts:
      data.alerts.length > 0
        ? data.alerts
        : [
            {
              id: "preview-low",
              productVariantId: "preview",
              productName: "Samsung Galaxy A55 5G Screen Protector",
              variantName: "A55",
              alertType: "LOW_STOCK",
              severity: "ORANGE",
              status: "OPEN",
              observedValue: 1,
              message: "1 remaining",
              createdAt: new Date().toISOString(),
            },
          ],
  };
}

export function DashboardPage() {
  const { user } = useAuth();
  const access = useAccess();
  const [data, setData] = useState<DashboardResponse | null>(null);
  const [sales, setSales] = useState<SaleRow[]>([]);
  const [repairs, setRepairs] = useState<RepairRow[]>([]);
  const [moves, setMoves] = useState<MovementRow[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [nonce, setNonce] = useState(0);
  const [params] = useSearchParams();
  const previewBusy = PREVIEW_ENV !== "PRODUCTION" && params.get("floor") === "busy";

  const shop = user?.workspaceName ?? user?.shopName ?? "This shop";
  const name = firstName(user?.fullName);
  const canSales = access.has("SALES_READ");
  const canRepairs = access.has("REPAIR_READ");
  const canStock = access.has("INVENTORY_READ");

  useEffect(() => {
    let live = true;
    setError(null);
    api<DashboardResponse>("/api/v1/mobistack/dashboard")
      .then((payload) => {
        if (live) {
          setData(payload);
        }
      })
      .catch((err: Error) => {
        if (live) {
          setError(err.message);
        }
      });

    if (canSales) {
      api<PageResponse<SaleRow>>("/api/v1/mobistack/sales?size=12")
        .then((page) => {
          if (live) {
            setSales(page.content.filter((row) => row.status !== "VOIDED"));
          }
        })
        .catch(() => undefined);
    }
    if (canRepairs) {
      api<PageResponse<RepairRow>>("/api/v1/mobistack/repairs?size=20")
        .then((page) => {
          if (live) {
            setRepairs(page.content);
          }
        })
        .catch(() => undefined);
    }
    if (canStock) {
      api<PageResponse<MovementRow>>("/api/v1/mobistack/inventory/transactions?size=12")
        .then((page) => {
          if (live) {
            setMoves(page.content);
          }
        })
        .catch(() => undefined);
    }
    return () => {
      live = false;
    };
  }, [nonce, canSales, canRepairs, canStock]);

  const daybook = useMemo<DayRow[]>(() => {
    const rows: DayRow[] = [];
    const today = dayKey(new Date().toISOString());

    sales.forEach((sale) => {
      rows.push({
        id: `sale-${sale.id}`,
        at: sale.occurredAt,
        station: "COUNTER",
        title: saleTitle(sale),
        detail: `Sale · ${walkIn(sale.customerName)}`,
        value: money.format(sale.total),
        to: "/sales",
      });
    });

    repairs.forEach((job) => {
      rows.push({
        id: `repair-${job.id}`,
        at: job.createdAt ?? "",
        station: "BENCH",
        title: job.deviceName || job.jobNumber,
        detail: `${humanLabel(job.status)} · ${walkIn(job.customerName)}`,
        value: ledgerBenchValue(job.status),
        to: "/repairs",
      });
    });

    moves
      .filter((move) => SHELF_MOVES.has(move.type))
      .forEach((move) => {
        const units = move.onHandDelta || move.quantity;
        rows.push({
          id: `move-${move.id}`,
          at: move.occurredAt,
          station: "SHELF",
          title: move.referenceLabel || move.reason || humanLabel(move.type),
          detail: humanLabel(move.type),
          value: `${units > 0 ? "+" : ""}${qty.format(units)}`,
          to: "/inventory",
        });
      });

    rows.sort((a, b) => new Date(b.at).getTime() - new Date(a.at).getTime());
    const todays = rows.filter((row) => row.at && dayKey(row.at) === today);
    return (todays.length > 0 ? todays : rows).slice(0, 12);
  }, [sales, repairs, moves]);

  const feedIsToday = useMemo(() => {
    const today = dayKey(new Date().toISOString());
    return daybook.some((row) => row.at && dayKey(row.at) === today);
  }, [daybook]);

  if (user && !user.features?.includes("DASHBOARD") && !user.systemAdmin) {
    return <Navigate to="/commons" replace />;
  }

  if (error) {
    return (
      <div className="page page--floor">
        <div className="floor-pad">
          <ErrorState message={error} onRetry={() => setNonce((value) => value + 1)} />
        </div>
      </div>
    );
  }

  if (!data) {
    return (
      <div className="page page--floor" aria-busy="true">
        <header className="floor-mast">
          <div className="skeleton" style={{ width: 180, height: 12 }} />
          <div className="skeleton skeleton--title" style={{ width: 280, height: 28, marginTop: 16 }} />
        </header>
        <div className="floor-pad">
          <div className="skeleton skeleton--value" style={{ width: 220, height: 56 }} />
        </div>
      </div>
    );
  }

  const view = previewBusy ? busyPreview(data) : data;
  const inv = view.inventory;
  const tickets = view.sales.todayTransactions;
  const previewSale: SaleRow | null =
    previewBusy && sales.length === 0
      ? {
          id: "preview-sale",
          invoiceNumber: "MS-2401",
          status: "COMPLETED",
          customerName: "Priya Sharma",
          total: 28999,
          occurredAt: todayAt(10, 42),
          items: [{ variantName: "Samsung Galaxy A55 5G Super AMOLED Display Assembly", quantity: 1 }],
        }
      : null;
  const lastSale = sales[0] ?? previewSale;
  const previewJob: RepairRow | null =
    previewBusy && repairs.length === 0
      ? {
          id: "preview-job",
          jobNumber: "R-8841",
          status: "READY",
          customerName: "Amit Kumar",
          deviceName: "iPhone 13",
          createdAt: todayAt(9, 56),
        }
      : null;
  const nextPickup = repairs.find((job) => job.status === "READY") ?? previewJob;
  const urgentRepair =
    repairs.find((job) => job.status === "WAITING_FOR_PART") ??
    repairs.find(
      (job) =>
        job.expectedAt &&
        new Date(job.expectedAt).getTime() < Date.now() &&
        job.status !== "DELIVERED" &&
        job.status !== "CANCELLED",
    ) ??
    nextPickup;
  const sku = worstSku(view.alerts);
  const benchClear = view.repairs.pending === 0;
  const shelfEmpty = inv.totalProducts === 0 && inv.stockUnits === 0;
  const shelfRisk = inv.lowStockCount + inv.outOfStockCount;
  const benchState = benchClear
    ? "Bench is clear."
    : `${qty.format(view.repairs.ready)} ready · ${qty.format(view.repairs.pending)} open`;
  const shelfState = shelfEmpty
    ? "Shelf is ready."
    : `${qty.format(inv.stockUnits)} units · ${compactInr(inv.stockValueAtCost)} cost`;
  const previewLedger: DayRow[] =
    previewBusy && daybook.length === 0
      ? [
          {
            id: "p1",
            at: todayAt(10, 42),
            station: "COUNTER",
            title: "Samsung Galaxy A55 5G Super AMOLED Display Assembly",
            detail: "Sale · Priya Sharma",
            value: money.format(28999),
            to: "/sales",
          },
          {
            id: "p2",
            at: todayAt(10, 21),
            station: "SHELF",
            title: "Redmi Note 14 5G charging dock",
            detail: "Stock received",
            value: "+12",
            to: "/inventory",
          },
          {
            id: "p3",
            at: todayAt(9, 56),
            station: "BENCH",
            title: "iPhone 13",
            detail: "Ready for pickup · Amit Kumar",
            value: "READY",
            to: "/repairs",
          },
        ]
      : [];
  const ledger = [...daybook, ...previewLedger].slice(0, 10);
  const ghosts = ledger.length === 0 ? 2 : 0;

  return (
    <div className="page page--floor">
      <header className="floor-mast">
        <div className="floor-mast__top">
          <div className="floor-id">
            <p className="floor-shop">{shop}</p>
            <p className="floor-when">{todayLabel()}</p>
          </div>
          <div className="floor-mast__cmd">
            <button className="btn ghost" type="button" onClick={() => openCommandPalette()}>
              Search / Scan
            </button>
            <Link className="btn" to="/sales">
              + New sale
            </Link>
          </div>
        </div>
        <div className="floor-mast__copy">
          <p className="floor-greet">
            {greeting()}, {name}.
          </p>
          <p className="floor-sit">{situation(view)}</p>
        </div>
        <nav className="floor-links" aria-label="More shop actions">
          <button className="floor-chip" type="button" onClick={() => openCommandPalette()}>
            Scan barcode
          </button>
          <Link className="floor-chip" to="/inventory">
            Add stock
          </Link>
          <Link className="floor-chip" to="/repairs">
            Book repair
          </Link>
          <Link className="floor-chip" to="/commons">
            Find compatibility
          </Link>
          <Link className="floor-chip" to="/customers">
            Add customer
          </Link>
        </nav>
      </header>

      <div className="floor-pad">
        <div className="floor-stations">
          <section className="station station--counter" aria-labelledby="station-counter">
            <header className="station__head">
              <h2 id="station-counter">Counter</h2>
              <span className="station-mark">{tickets === 0 ? "Quiet" : `${qty.format(tickets)} today`}</span>
            </header>
            <div className="station__money">
              <p className="station__kicker">Today’s business</p>
              <p className="station__figure" aria-label={money.format(view.sales.todaySales)}>
                <span className="station__ccy" aria-hidden>
                  ₹
                </span>
                <span className="station__amt">{qty.format(Math.round(view.sales.todaySales))}</span>
              </p>
              <p className="station__caption">{tickets === 0 && !previewBusy ? "No sales yet." : "Sales"}</p>
            </div>
            <p className="station__facts">
              <span>
                <b>{qty.format(tickets)}</b> {tickets === 1 ? "transaction" : "transactions"}
              </span>
              <span className={view.sales.todayProfit < 0 ? "is-neg" : undefined}>
                <b>{money.format(view.sales.todayProfit)}</b> profit
              </span>
            </p>
            <div className="station__item">
              <span>Last sale</span>
              {lastSale ? (
                <>
                  <strong>{saleTitle(lastSale)}</strong>
                  <b>{money.format(lastSale.total)}</b>
                </>
              ) : null}
              <Link className="station__act" to="/sales">
                View sales →
              </Link>
            </div>
          </section>

          <section className="station station--side station--bench" aria-labelledby="station-bench">
            <header className="station__head">
              <h2 id="station-bench">Bench</h2>
              <span
                className={`station-mark${view.repairs.ready > 0 ? " station-mark--info" : benchClear ? "" : " station-mark--warn"}`}
              >
                {view.repairs.ready > 0
                  ? `${qty.format(view.repairs.ready)} ready`
                  : benchClear
                    ? "Clear"
                    : `${qty.format(view.repairs.pending)} open`}
              </span>
            </header>
            <p className="station__state">{benchState}</p>
            <div className="station__item">
              <span>{urgentRepair ? "Next up" : "Queue"}</span>
              {urgentRepair ? (
                <>
                  <strong>{urgentRepair.deviceName || urgentRepair.jobNumber}</strong>
                  <b>{humanLabel(urgentRepair.status)}</b>
                </>
              ) : (
                <strong>No phones on the bench.</strong>
              )}
              <Link className="station__act" to="/repairs">
                {benchClear ? "Book repair →" : "Open board →"}
              </Link>
            </div>
          </section>

          <section className="station station--side station--shelf" aria-labelledby="station-shelf">
            <header className="station__head">
              <h2 id="station-shelf">Shelf</h2>
              <span
                className={`station-mark${shelfRisk > 0 ? (inv.outOfStockCount > 0 ? " station-mark--crit" : " station-mark--warn") : ""}`}
              >
                {shelfEmpty ? "Ready" : shelfRisk > 0 ? (inv.outOfStockCount > 0 ? `${qty.format(inv.outOfStockCount)} out` : `${qty.format(inv.lowStockCount)} low`) : "Healthy"}
              </span>
            </header>
            <p className="station__state">{shelfState}</p>
            <div className="station__item">
              <span>Watch</span>
              {sku ? (
                <>
                  <strong>{sku.productName ?? sku.variantName}</strong>
                  <b>{remaining(sku) ?? humanLabel(sku.alertType)}</b>
                </>
              ) : (
                <strong>{shelfEmpty ? "No stock yet." : "Levels are steady."}</strong>
              )}
              <Link className="station__act" to="/inventory">
                {shelfEmpty ? "Add stock →" : sku ? "Restock →" : "Inventory →"}
              </Link>
            </div>
          </section>
        </div>

        <section className="floor-daybook" aria-label="Today’s activity">
          <header className="daybook__head">
            <h2>{feedIsToday || ledger.length === 0 ? "Today’s activity" : "Recent activity"}</h2>
            {ledger.length > 0 ? (
              <Link className="station__act" to="/sales">
                View sales →
              </Link>
            ) : null}
          </header>
          {ledger.length === 0 ? (
            <div className="daybook-empty">
              <p className="daybook__ready">The counter is ready.</p>
              <p className="daybook__hint">
                Make your first sale, add inventory, or book a repair to start today’s record.
              </p>
              <div className="daybook-empty__act">
                <Link className="btn sm" to="/sales">
                  New sale
                </Link>
                <Link className="btn ghost sm" to="/inventory">
                  Add stock
                </Link>
              </div>
            </div>
          ) : null}
          <div className="daybook-sheet">
            {ledger.length > 0 ? (
              <div className="daybook__cols" aria-hidden>
                <span>Time</span>
                <span>Station</span>
                <span>Event</span>
                <span>Value</span>
              </div>
            ) : null}
            <ol className="daybook">
              {ledger.map((row) => (
                <li key={row.id}>
                  <Link className="daybook__row" to={row.to}>
                    <time dateTime={row.at}>{row.at ? clock(row.at) : "—"}</time>
                    <em className={`stn stn--${row.station.toLowerCase()}`}>{row.station}</em>
                    <span>
                      <strong>{row.title}</strong>
                      <i>{row.detail}</i>
                    </span>
                    <b>{row.value ?? ""}</b>
                  </Link>
                </li>
              ))}
              {Array.from({ length: ghosts }).map((_, index) => (
                <li key={`ghost-${index}`} className="daybook__ghost" aria-hidden>
                  <div className="daybook__row">
                    <span />
                    <span />
                    <span />
                    <span />
                  </div>
                </li>
              ))}
            </ol>
          </div>
        </section>
      </div>
    </div>
  );
}
