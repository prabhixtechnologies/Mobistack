import { useEffect, useState } from "react";
import { api, money } from "../lib/api";
import { PageHeader } from "../ui/PageHeader";

interface Overview {
  prices: { code: string; amount: number; currency: string; interval: string; entitlement: string }[];
  entitlements: string[];
  orders: { id: string; priceCode: string; amount: number; status: string; createdAt: string }[];
}

export function BillingPage() {
  const [data, setData] = useState<Overview | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    setData(await api<Overview>("/api/v1/billing"));
  }

  useEffect(() => {
    load().catch((err: Error) => setError(err.message));
  }, []);

  async function buy(priceCode: string) {
    try {
      const order = await api<{ id: string }>("/api/v1/billing/orders", {
        method: "POST",
        body: JSON.stringify({ priceCode }),
      });
      await api(`/api/v1/billing/orders/${order.id}/confirm`, { method: "POST" });
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Payment failed");
    }
  }

  return (
    <div className="page">
      <PageHeader
        kicker="Workspace"
        title="Billing"
        subtitle="Local Docker can confirm a pilot plan. Production refuses self-confirm until a payment gateway is wired."
      />
      {error && <div className="error">{error}</div>}
      {data && (
        <>
          <div className="muted">Active entitlements: {data.entitlements.join(", ") || "none"}</div>
          <div className="grid-2">
            {data.prices.map((price) => (
              <article className="card stack" key={price.code}>
                <strong>{price.code.replaceAll("_", " ")}</strong>
                <div className="metric-value">{money.format(price.amount)}</div>
                <div className="faint">{price.interval} · grants {price.entitlement}</div>
                <button className="btn" type="button" onClick={() => void buy(price.code)}>
                  Pay in dev
                </button>
              </article>
            ))}
          </div>
          <div className="card tight">
            {data.orders.map((order) => (
              <div className="category-row" key={order.id}>
                <div>{order.priceCode}</div>
                <span>{order.status}</span>
                <span>{money.format(order.amount)}</span>
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  );
}
