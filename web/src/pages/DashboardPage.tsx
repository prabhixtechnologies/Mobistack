import { useEffect, useState } from "react";
import { api, money, qty } from "../lib/api";
import type { DashboardResponse } from "../lib/types";

export function DashboardPage() {
  const [data, setData] = useState<DashboardResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api<DashboardResponse>("/api/v1/dashboard")
      .then(setData)
      .catch((err: Error) => setError(err.message));
  }, []);

  if (error) {
    return (
      <div className="page">
        <div className="error">{error}</div>
      </div>
    );
  }

  if (!data) {
    return (
      <div className="page">
        <div className="grid-4">
          {Array.from({ length: 4 }).map((_, i) => (
            <div className="card" key={i}>
              <div className="skeleton" />
            </div>
          ))}
        </div>
      </div>
    );
  }

  const inv = data.inventory;

  return (
    <div className="page">
      <div className="page-title">
        <div>
          <h1>Today at the counter</h1>
          <p>Stock, sales and open jobs from this workspace. Numbers are live, not placeholders.</p>
        </div>
      </div>

      <div className="grid-4">
        <Metric label="Today’s sales" value={money.format(data.sales.todaySales)} />
        <Metric label="Today’s profit" value={money.format(data.sales.todayProfit)} />
        <Metric label="Transactions" value={qty.format(data.sales.todayTransactions)} />
        <Metric label="Pending repairs" value={qty.format(data.repairs.pending)} />
      </div>

      <div className="grid-4">
        <Metric label="Products" value={qty.format(inv.totalProducts)} />
        <Metric label="Stock units" value={qty.format(inv.stockUnits)} />
        <Metric label="Stock value" value={money.format(inv.stockValueAtCost)} />
        <Metric label="Low / out" value={`${inv.lowStockCount} / ${inv.outOfStockCount}`} />
      </div>

      <section className="card tight">
        <div className="spread" style={{ padding: "16px 18px" }}>
          <strong>Critical inventory</strong>
          <span className="faint">{inv.openAlertCount} open alerts</span>
        </div>
        {data.alerts.length === 0 ? (
          <div className="empty">Nothing needs attention. That is a good morning.</div>
        ) : (
          data.alerts.map((alert) => (
            <div className="category-row" key={alert.id}>
              <div>
                <div style={{ fontWeight: 600 }}>{alert.productName}</div>
                <div className="faint">{alert.message}</div>
              </div>
              <span className={`badge ${alert.severity}`}>{alert.severity}</span>
              <span className="faint">{alert.alertType.replaceAll("_", " ")}</span>
            </div>
          ))
        )}
      </section>
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
