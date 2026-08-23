import { useEffect, useState } from "react";
import { useAuth } from "../lib/auth";
import { api, money, qty } from "../lib/api";
import type { DashboardResponse } from "../lib/types";

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

export function DashboardPage() {
  const { user } = useAuth();
  const firstName = user?.fullName?.split(/\s+/)[0] ?? "there";
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
          <p className="page-kicker">{user?.workspaceName ?? user?.shopName}</p>
          <h1>
            {greeting()}, {firstName}.
          </h1>
          <p>Live sales, stock and jobs for this counter — not placeholders.</p>
        </div>
      </div>

      <div className="grid-4">
        <Metric label="Today’s sales" value={money.format(data.sales.todaySales)} tint="violet" />
        <Metric label="Today’s profit" value={money.format(data.sales.todayProfit)} tint="green" />
        <Metric label="Transactions" value={qty.format(data.sales.todayTransactions)} tint="blue" />
        <Metric label="Pending repairs" value={qty.format(data.repairs.pending)} tint="amber" />
      </div>

      <div className="grid-4">
        <Metric label="Products" value={qty.format(inv.totalProducts)} tint="cyan" />
        <Metric label="Stock units" value={qty.format(inv.stockUnits)} tint="slate" />
        <Metric label="Stock value" value={money.format(inv.stockValueAtCost)} tint="orange" />
        <Metric label="Low / out" value={`${inv.lowStockCount} / ${inv.outOfStockCount}`} tint="rose" />
      </div>

      <section className="card tight lift">
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

function Metric({ label, value, tint }: { label: string; value: string; tint: string }) {
  return (
    <div className={`card metric-card lift tint-${tint}`}>
      <div className="metric-label">{label}</div>
      <div className="metric-value">{value}</div>
    </div>
  );
}
