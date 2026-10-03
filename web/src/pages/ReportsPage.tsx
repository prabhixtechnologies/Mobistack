import { useEffect, useState } from "react";
import { api, getAccessToken, money, qty } from "../lib/api";
import { getDeviceId } from "../lib/device";
import { useAccess } from "../lib/access";
import { useAction } from "../lib/useAction";
import { EmptyState } from "../ui/EmptyState";
import { PageHeader } from "../ui/PageHeader";
import { Kpi, Panel } from "../ui/Panel";

interface ReportBundle {
  sales: { sales: number; profit: number; transactions: number };
  purchases: { sales: number };
  repairRevenue: number;
  repairs: { received: number; diagnosing: number; inRepair: number; ready: number; delivered: number; pending: number };
  deadStock: { variantId: string; name: string; stock: number; stockValue: number; daysInactive: number; suggestion: string }[];
}

export function ReportsPage() {
  const access = useAccess();
  const [range, setRange] = useState("today");
  const [data, setData] = useState<ReportBundle | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let live = true;
    setLoading(true);
    api<ReportBundle>(`/api/v1/mobistack/reports?range=${range}`)
      .then((bundle) => {
        if (live) {
          setData(bundle);
          setError(null);
        }
      })
      .catch((err: Error) => {
        if (live) {
          setError(err.message);
        }
      })
      .finally(() => {
        if (live) {
          setLoading(false);
        }
      });
    return () => {
      live = false;
    };
  }, [range]);

  const exportCsv = useAction(
    async () => {
      const token = getAccessToken();
      const response = await fetch(`/api/v1/mobistack/reports/export?range=${range}`, {
        headers: {
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
          "X-MobiStack-Device": getDeviceId(),
        },
      });
      // Without this check a rejected export saves the error page as a .csv,
      // which then opens as a spreadsheet of HTML.
      if (!response.ok) {
        throw new Error(`The export was refused (${response.status}). Nothing was downloaded.`);
      }
      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = `mobistack-report-${range}.csv`;
      link.click();
      URL.revokeObjectURL(url);
    },
    { fallbackError: "That export did not download." },
  );

  const period = RANGES.find((option) => option.value === range)?.label ?? "";

  return (
    <div className="page">
      <PageHeader
        icon="chart"
        kicker="Insights"
        title="Reports"
        subtitle="Profit is sale price minus ledger cost. Dead stock is inventory that has not moved."
        actions={
          access.has("REPORT_EXPORT") ? (
            <button className="btn ghost" type="button" disabled={exportCsv.busy} onClick={() => void exportCsv.run()}>
              {exportCsv.busy ? "Exporting…" : "Export CSV"}
            </button>
          ) : undefined
        }
      />

      <div className="segmented" role="radiogroup" aria-label="Report period">
        {RANGES.map((option) => (
          <button
            key={option.value}
            type="button"
            role="radio"
            aria-checked={range === option.value}
            className={range === option.value ? "segmented__item segmented__item--on" : "segmented__item"}
            onClick={() => setRange(option.value)}
          >
            {option.label}
          </button>
        ))}
      </div>

      {error && <div className="error">{error}</div>}
      {exportCsv.error && <div className="error">{exportCsv.error}</div>}
      {loading && !data && (
        <div className="kpi-grid" aria-busy="true" aria-label="Loading report">
          {Array.from({ length: 4 }, (_, index) => (
            <div className="kpi" key={index}>
              <div className="skeleton" style={{ width: "50%" }} />
              <div className="skeleton skeleton--value" style={{ marginTop: 14 }} />
            </div>
          ))}
        </div>
      )}
      {data && (
        <div className={loading ? "report report--refreshing" : "report"}>
          <div className="kpi-grid">
            <Kpi
              icon="cart"
              tone="ochre"
              label="Sales"
              value={money.format(data.sales.sales)}
              foot={`${qty.format(data.sales.transactions)} ${data.sales.transactions === 1 ? "invoice" : "invoices"} · ${period.toLowerCase()}`}
            />
            <Kpi
              icon="pulse"
              tone={data.sales.profit < 0 ? "rose" : "green"}
              label="Profit"
              value={money.format(data.sales.profit)}
              foot={data.sales.sales > 0 ? `${Math.round((data.sales.profit / data.sales.sales) * 100)}% margin` : "No sales in this period"}
            />
            <Kpi
              icon="truck"
              tone="indigo"
              label="Purchases"
              value={money.format(data.purchases.sales)}
              foot="Stock bought in"
            />
            <Kpi
              icon="wrench"
              tone="teal"
              label="Repair revenue"
              value={money.format(data.repairRevenue)}
              foot={`${qty.format(data.repairs.delivered)} collected`}
            />
          </div>

          <div className="report__split">
            <Panel icon="wrench" title="Repair pipeline" hint={period}>
              <ul className="pipeline">
                {PIPELINE.map((stage) => {
                  const value = data.repairs[stage.key];
                  const max = Math.max(1, ...PIPELINE.map((item) => data.repairs[item.key]));
                  return (
                    <li key={stage.key} className={`pipeline__row pipeline__row--${stage.tone}`}>
                      <span>{stage.label}</span>
                      <span className="pipeline__bar" aria-hidden>
                        <span style={{ width: `${(value / max) * 100}%` }} />
                      </span>
                      <b>{qty.format(value)}</b>
                    </li>
                  );
                })}
              </ul>
            </Panel>

            <Panel icon="chart" title="Counter" hint={period}>
              <dl className="facts">
                <div>
                  <dt>Invoices</dt>
                  <dd>{qty.format(data.sales.transactions)}</dd>
                </div>
                <div>
                  <dt>Average ticket</dt>
                  <dd>{data.sales.transactions > 0 ? money.format(data.sales.sales / data.sales.transactions) : "—"}</dd>
                </div>
                <div>
                  <dt>Open repairs</dt>
                  <dd>{qty.format(data.repairs.pending)}</dd>
                </div>
                <div>
                  <dt>Ready for pickup</dt>
                  <dd>{qty.format(data.repairs.ready)}</dd>
                </div>
              </dl>
            </Panel>
          </div>

          <Panel
            icon="box"
            title="Dead stock"
            hint="Parts that have not moved. Money sitting on the shelf."
            count={data.deadStock.length > 0 ? data.deadStock.length : undefined}
            flush
          >
            {data.deadStock.length === 0 ? (
              <EmptyState compact icon="check" title="Nothing is sitting idle" hint="Parts show here when they stop selling." />
            ) : (
              <div className="table-scroll">
                <table className="table">
                  <thead>
                    <tr>
                      <th>Part</th>
                      <th style={{ textAlign: "right" }}>Idle</th>
                      <th style={{ textAlign: "right" }}>Units</th>
                      <th style={{ textAlign: "right" }}>Value at cost</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.deadStock.map((row) => (
                      <tr key={row.variantId}>
                        <td data-label="Part">
                          <div className="cell-identity">
                            <strong>{row.name}</strong>
                            <span className="faint">{row.suggestion}</span>
                          </div>
                        </td>
                        <td data-label="Idle" style={{ textAlign: "right" }}>
                          {qty.format(row.daysInactive)} days
                        </td>
                        <td data-label="Units" style={{ textAlign: "right" }}>
                          {qty.format(row.stock)}
                        </td>
                        <td data-label="Value at cost" style={{ textAlign: "right" }}>
                          {money.format(row.stockValue)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Panel>
        </div>
      )}
    </div>
  );
}

const RANGES = [
  { value: "today", label: "Today" },
  { value: "yesterday", label: "Yesterday" },
  { value: "7d", label: "7 days" },
  { value: "this_month", label: "This month" },
  { value: "last_month", label: "Last month" },
];

const PIPELINE: { key: "received" | "diagnosing" | "inRepair" | "ready" | "delivered"; label: string; tone: string }[] = [
  { key: "received", label: "Received", tone: "indigo" },
  { key: "diagnosing", label: "Diagnosing", tone: "indigo" },
  { key: "inRepair", label: "In repair", tone: "ochre" },
  { key: "ready", label: "Ready", tone: "teal" },
  { key: "delivered", label: "Collected", tone: "green" },
];
