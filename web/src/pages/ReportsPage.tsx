import { useEffect, useState } from "react";
import { api, getAccessToken, money, qty } from "../lib/api";
import { PageHeader } from "../ui/PageHeader";

interface ReportBundle {
  sales: { sales: number; profit: number; transactions: number };
  purchases: { sales: number };
  repairRevenue: number;
  repairs: { received: number; diagnosing: number; inRepair: number; ready: number; delivered: number; pending: number };
  deadStock: { variantId: string; name: string; stock: number; stockValue: number; daysInactive: number; suggestion: string }[];
}

export function ReportsPage() {
  const [range, setRange] = useState("today");
  const [data, setData] = useState<ReportBundle | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api<ReportBundle>(`/api/v1/reports?range=${range}`)
      .then(setData)
      .catch((err: Error) => setError(err.message));
  }, [range]);

  return (
    <div className="page">
      <PageHeader
        kicker="Insights"
        title="Reports"
        subtitle="Profit is sale price minus ledger cost. Dead stock is inventory that has not moved."
        actions={
        <div className="row">
          <select className="select" value={range} onChange={(e) => setRange(e.target.value)} style={{ width: 180 }}>
            <option value="today">Today</option>
            <option value="yesterday">Yesterday</option>
            <option value="7d">7 days</option>
            <option value="this_month">This month</option>
            <option value="last_month">Last month</option>
          </select>
          <a className="btn ghost" href={`/api/v1/reports/export?range=${range}`} onClick={(event) => {
            event.preventDefault();
            const token = getAccessToken();
            void fetch(`/api/v1/reports/export?range=${range}`, {
              headers: token ? { Authorization: `Bearer ${token}` } : {},
            }).then(async (response) => {
              const blob = await response.blob();
              const url = URL.createObjectURL(blob);
              const link = document.createElement("a");
              link.href = url;
              link.download = "mobistack-report.csv";
              link.click();
              URL.revokeObjectURL(url);
            });
          }}>
            Export CSV
          </a>
        </div>
        }
      />
      {error && <div className="error">{error}</div>}
      {data && (
        <>
          <div className="grid-4">
            <Metric label="Sales" value={money.format(data.sales.sales)} />
            <Metric label="Profit" value={money.format(data.sales.profit)} />
            <Metric label="Purchases" value={money.format(data.purchases.sales)} />
            <Metric label="Repair revenue" value={money.format(data.repairRevenue)} />
          </div>
          <div className="grid-4">
            <Metric label="Transactions" value={qty.format(data.sales.transactions)} />
            <Metric label="Pending repairs" value={qty.format(data.repairs.pending)} />
            <Metric label="Ready" value={qty.format(data.repairs.ready)} />
            <Metric label="Delivered" value={qty.format(data.repairs.delivered)} />
          </div>
          <section className="card tight">
            <div className="spread" style={{ padding: "16px 18px" }}>
              <strong>Dead stock</strong>
            </div>
            {data.deadStock.length === 0 ? (
              <div className="empty">Nothing is sitting idle.</div>
            ) : (
              data.deadStock.map((row) => (
                <div className="category-row" key={row.variantId}>
                  <div>
                    <div style={{ fontWeight: 650 }}>{row.name}</div>
                    <div className="faint">{row.daysInactive} days · {row.suggestion}</div>
                  </div>
                  <span>{row.stock}</span>
                  <span>{money.format(row.stockValue)}</span>
                </div>
              ))
            )}
          </section>
        </>
      )}
    </div>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="card">
      <div className="metric-label">{label}</div>
      <div className="metric-value">{value}</div>
    </div>
  );
}
