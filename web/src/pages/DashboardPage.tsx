import { useEffect, useState } from "react";
import { Link, Navigate } from "react-router-dom";
import { useAuth } from "../lib/auth";
import { api, money, qty } from "../lib/api";
import { ErrorState } from "../ui/EmptyState";
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
  if (user && !user.features?.includes("DASHBOARD") && !user.systemAdmin) {
    return <Navigate to={user.features?.includes("COMPATIBILITY") ? "/compatibility" : "/billing"} replace />;
  }
  const [data, setData] = useState<DashboardResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [nonce, setNonce] = useState(0);

  useEffect(() => {
    let live = true;
    setError(null);
    api<DashboardResponse>("/api/v1/dashboard")
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
    return () => {
      live = false;
    };
  }, [nonce]);

  if (error) {
    return (
      <div className="page">
        <ErrorState message={error} onRetry={() => setNonce((value) => value + 1)} />
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
              <div className="skeleton skeleton--value" />
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
        <Link className="btn" to="/compatibility">
          Search a phone
        </Link>
      </div>

      <div className="grid-4">
        <Metric label="Today’s sales" value={money.format(data.sales.todaySales)} tint="violet" to="/sales" />
        <Metric label="Today’s profit" value={money.format(data.sales.todayProfit)} tint="green" to="/reports" />
        <Metric label="Transactions" value={qty.format(data.sales.todayTransactions)} tint="blue" to="/sales" />
        <Metric label="Pending repairs" value={qty.format(data.repairs.pending)} tint="amber" to="/repairs" />
      </div>

      <div className="grid-4">
        <Metric label="Products" value={qty.format(inv.totalProducts)} tint="cyan" to="/inventory" />
        <Metric label="Stock units" value={qty.format(inv.stockUnits)} tint="slate" to="/inventory" />
        <Metric label="Stock value" value={money.format(inv.stockValueAtCost)} tint="orange" to="/inventory" />
        <Metric
          label="Low / out"
          value={`${inv.lowStockCount} / ${inv.outOfStockCount}`}
          tint="rose"
          to="/inventory"
        />
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
            <Link
              className="category-row"
              key={alert.id}
              to={`/inventory?q=${encodeURIComponent(alert.productName ?? alert.variantName ?? "")}`}
            >
              <div>
                <div style={{ fontWeight: 600 }}>{alert.productName}</div>
                <div className="faint">{alert.message}</div>
              </div>
              <span className={`badge ${alert.severity}`}>{alert.severity}</span>
              <span className="faint">{alert.alertType.replaceAll("_", " ")}</span>
            </Link>
          ))
        )}
      </section>
    </div>
  );
}

function Metric({
  label,
  value,
  tint,
  to,
}: {
  label: string;
  value: string;
  tint: string;
  to: string;
}) {
  return (
    <Link className={`card metric-card lift tint-${tint}`} to={to}>
      <div className="metric-label">{label}</div>
      <div className="metric-value">{value}</div>
    </Link>
  );
}
