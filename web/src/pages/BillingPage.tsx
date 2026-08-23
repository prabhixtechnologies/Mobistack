import { useEffect, useState } from "react";
import { api, money } from "../lib/api";
import { useAuth } from "../lib/auth";
import { BRAND } from "../lib/brand";
import { loadRazorpayCheckout, openRazorpayCheckout } from "../lib/razorpay";
import { PageHeader } from "../ui/PageHeader";

interface Overview {
  prices: { code: string; amount: number; currency: string; interval: string; entitlement: string }[];
  entitlements: string[];
  orders: { id: string; priceCode: string; amount: number; status: string; createdAt: string }[];
  razorpayKeyId?: string;
  razorpayEnabled?: boolean;
}

interface CheckoutOrder {
  id: string;
  order_id: string;
  amount: number;
  currency: string;
  keyId?: string;
  priceCode: string;
  gateway: string;
}

export function BillingPage() {
  const { user } = useAuth();
  const [data, setData] = useState<Overview | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [paying, setPaying] = useState<string | null>(null);

  async function load() {
    setData(await api<Overview>("/api/v1/billing"));
  }

  useEffect(() => {
    load().catch((err: Error) => setError(err.message));
  }, []);

  const publishableKey =
    (import.meta.env.VITE_RAZORPAY_KEY_ID as string | undefined)?.trim()
    || data?.razorpayKeyId
    || "";

  async function buy(priceCode: string) {
    setError(null);
    setNotice(null);
    setPaying(priceCode);
    try {
      const order = await api<CheckoutOrder>("/api/v1/billing/orders", {
        method: "POST",
        body: JSON.stringify({ priceCode }),
      });
      if (order.gateway === "DEV" || !order.order_id?.startsWith("order_")) {
        await api(`/api/v1/billing/orders/${order.id}/confirm`, { method: "POST" });
        await load();
        setNotice("Local gateway captured the order.");
        return;
      }
      const key = publishableKey || order.keyId;
      if (!key) {
        throw new Error("Razorpay key is missing. Set VITE_RAZORPAY_KEY_ID or configure the server.");
      }
      await loadRazorpayCheckout();
      await new Promise<void>((resolve, reject) => {
        let settled = false;
        const checkout = openRazorpayCheckout({
          key,
          amount: order.amount,
          currency: order.currency,
          name: BRAND.product,
          description: priceCode.replaceAll("_", " "),
          order_id: order.order_id,
          prefill: {
            name: user?.fullName,
            email: user?.email,
            contact: user?.phone,
          },
          theme: { color: "#6d28d9" },
          handler: (response) => {
            void api("/api/v1/billing/verify", {
              method: "POST",
              body: JSON.stringify({
                razorpay_order_id: response.razorpay_order_id,
                razorpay_payment_id: response.razorpay_payment_id,
                razorpay_signature: response.razorpay_signature,
              }),
            })
              .then(async () => {
                settled = true;
                await load();
                setNotice("Payment received.");
                resolve();
              })
              .catch((err: Error) => {
                settled = true;
                reject(err);
              });
          },
          modal: {
            ondismiss: () => {
              if (!settled) {
                reject(new Error("Payment cancelled."));
              }
            },
          },
        });
        checkout.on("payment.failed", (response) => {
          settled = true;
          reject(new Error(response.error?.description || "Payment failed."));
        });
        checkout.open();
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Payment failed");
    } finally {
      setPaying(null);
    }
  }

  return (
    <div className="page">
      <PageHeader
        kicker="Workspace"
        title="Billing"
        subtitle="Pay with Razorpay Checkout. The server creates the order and verifies the payment signature before anything is marked paid."
      />
      {error && <div className="error">{error}</div>}
      {notice && <div className="muted">{notice}</div>}
      {data && (
        <>
          <div className="muted">Active entitlements: {data.entitlements.join(", ") || "none"}</div>
          <div className="grid-2">
            {data.prices.map((price) => (
              <article className="card stack" key={price.code}>
                <strong>{price.code.replaceAll("_", " ")}</strong>
                <div className="metric-value">{money.format(price.amount)}</div>
                <div className="faint">{price.interval} · grants {price.entitlement}</div>
                <button
                  className="btn"
                  type="button"
                  disabled={paying !== null}
                  onClick={() => void buy(price.code)}
                >
                  {paying === price.code ? "Opening checkout…" : `Pay ${money.format(price.amount)}`}
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
