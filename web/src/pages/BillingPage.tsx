import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
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
  paymentRequired?: boolean;
  currentPeriodEnd?: string | null;
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

function priceTitle(code: string): string {
  if (code === "WORKSPACE_ACTIVATION") {
    return "Shop activation";
  }
  if (code === "WORKSPACE_MONTHLY") {
    return "Monthly plan";
  }
  return code.replaceAll("_", " ");
}

function priceHelp(code: string): string {
  if (code === "WORKSPACE_ACTIVATION") {
    return "Unlocks sales, repairs, stock, and staff for 31 days.";
  }
  if (code === "WORKSPACE_MONTHLY") {
    return "Renews the shop for another 31 days. You will get a reminder before it ends.";
  }
  return "Recorded against this workspace after Razorpay confirms the payment.";
}

export function BillingPage() {
  const { user, refreshUser } = useAuth();
  const [params] = useSearchParams();
  const activating = params.get("activate") === "1" || Boolean(user?.paymentRequired);
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
        await refreshUser();
        setNotice("Payment recorded. The shop counter is unlocked.");
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
          description: priceTitle(priceCode),
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
                await refreshUser();
                setNotice("Payment received. The shop counter is unlocked.");
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

  const periodEnd = data?.currentPeriodEnd
    ? new Date(data.currentPeriodEnd).toLocaleDateString("en-IN", {
        day: "numeric",
        month: "short",
        year: "numeric",
      })
    : null;

  return (
    <div className="page">
      <PageHeader
        kicker="Workspace"
        title="Billing"
        subtitle="Pay with Razorpay Checkout. The server creates the order and verifies the payment signature before anything is marked paid."
      />
      {activating && (
        <div className="banner banner-warn">
          Payment is pending. Complete shop activation or the monthly plan to unlock sales, repairs,
          stock, and staff invites.
        </div>
      )}
      {(publishableKey.startsWith("rzp_test_") || data?.razorpayKeyId?.startsWith("rzp_test_")) && (
        <div className="banner">
          Razorpay is in test mode. Real UPI IDs and QR scans will show as invalid. Use UPI ID{" "}
          <strong>success@razorpay</strong> or card <strong>4111 1111 1111 1111</strong>.
        </div>
      )}
      {data && !data.paymentRequired && periodEnd && (
        <div className="banner">Current plan is active until {periodEnd}.</div>
      )}
      {error && <div className="error">{error}</div>}
      {notice && <div className="muted">{notice}</div>}
      {data && (
        <>
          <div className="muted">Active entitlements: {data.entitlements.join(", ") || "none"}</div>
          <div className="grid-2">
            {data.prices.map((price) => (
              <article className="card stack" key={price.code}>
                <strong>{priceTitle(price.code)}</strong>
                <div className="metric-value">{money.format(price.amount)}</div>
                <div className="faint">{price.interval} · {priceHelp(price.code)}</div>
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
            {data.orders.length === 0 && <div className="muted">No payments recorded yet.</div>}
            {data.orders.map((order) => (
              <div className="category-row" key={order.id}>
                <div>{priceTitle(order.priceCode)}</div>
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
