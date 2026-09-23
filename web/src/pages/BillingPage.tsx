import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { api, money } from "../lib/api";
import { useAuth } from "../lib/auth";
import { captureCheckoutOrder, type CheckoutOrder } from "../lib/payOrder";
import { EmptyState } from "../ui/EmptyState";
import { PageHeader } from "../ui/PageHeader";

interface PlanCard {
  id: string;
  code: string;
  name: string;
  description?: string;
  amount: number;
  currency: string;
  interval: string;
  features: string[];
  priceCode: string;
}

interface Overview {
  plans: PlanCard[];
  subscription?: {
    planCode: string;
    planName: string;
    status: string;
    periodEnd?: string | null;
    features: string[];
  } | null;
  recentPayments: {
    id: string;
    planName: string;
    priceCode: string;
    amount: number;
    currency: string;
    status: string;
    paidAt: string;
  }[];
  razorpayKeyId?: string;
  razorpayEnabled?: boolean;
  paymentRequired?: boolean;
  localActivationAvailable?: boolean;
  screens?: {
    included: number;
    extra: number;
    subscribed: number;
    seats: number;
    inUse: number;
    live: boolean;
    periodEnd?: string | null;
    amount: number;
    renewAmount: number;
    currency: string;
    priceCode: string;
    interval: string;
  };
}

function when(value?: string | null): string {
  if (!value) {
    return "No end date";
  }
  return new Date(value).toLocaleDateString("en-IN", {
    day: "numeric",
    month: "short",
    year: "numeric",
  });
}

export function BillingPage() {
  const { user, refreshUser } = useAuth();
  const [params] = useSearchParams();
  const activating = params.get("activate") === "1" || Boolean(user?.paymentRequired);
  const [data, setData] = useState<Overview | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [paying, setPaying] = useState<string | null>(null);
  const [unlocking, setUnlocking] = useState(false);

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

  async function activateLocal() {
    setError(null);
    setNotice(null);
    setUnlocking(true);
    try {
      await api("/api/v1/billing/dev/activate", { method: "POST" });
      await load();
      await refreshUser();
      setNotice("Local shop is on. Dashboard, stock, sales, and private fitment notes are unlocked.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not activate the local shop.");
    } finally {
      setUnlocking(false);
    }
  }

  async function buy(plan: PlanCard) {
    setError(null);
    setNotice(null);
    setPaying(plan.code);
    try {
      const order = await api<CheckoutOrder>("/api/v1/billing/orders", {
        method: "POST",
        body: JSON.stringify({ planCode: plan.priceCode || plan.code }),
      });
      await captureCheckoutOrder(order, user ?? undefined, plan.name, publishableKey);
      await load();
      await refreshUser();
      setNotice("Payment received. This month's plan is on.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Payment failed");
    } finally {
      document.body.classList.remove("rzp-checkout-open");
      setPaying(null);
    }
  }

  const current = data?.subscription;

  return (
    <div className="page">
      <PageHeader
        kicker="Workspace"
        title="Billing"
        subtitle="Pick a plan. Pay each month. If a month is missed, the shop locks until you pay again."
      />
      {activating && (
        <div className="banner banner-warn">
          This shop has no live plan. Choose one below and pay to turn the features on.
        </div>
      )}
      {data?.localActivationAvailable && activating && (
        <article className="card stack">
          <strong>Local testing</strong>
          <p className="faint">
            Razorpay is not required on this machine. Turn the full shop on, then open Private
            fitment notes under My Shop — or click Pay on a plan; the DEV gateway confirms instantly.
          </p>
          <button className="btn" type="button" disabled={unlocking || paying !== null} onClick={() => void activateLocal()}>
            {unlocking ? "Turning on…" : "Turn on local shop"}
          </button>
        </article>
      )}
      {current && current.status === "ACTIVE" && (
        <div className="banner">
          {current.planName} is on
          {current.periodEnd
            ? ` until ${when(current.periodEnd)}. Pay again before that date or the shop stops.`
            : " with no end date."}
        </div>
      )}
      {current && current.status === "PAST_DUE" && (
        <div className="banner banner-warn">
          {current.planName} lapsed. Pay this month to restore the features on that plan.
        </div>
      )}
      {(publishableKey.startsWith("rzp_test_") || data?.razorpayKeyId?.startsWith("rzp_test_")) && (
        <div className="banner">
          Razorpay Test Mode: use card <strong>4111 1111 1111 1111</strong>, any future expiry, CVV
          123, OTP <strong>1234</strong>.
        </div>
      )}
      {error && <div className="error">{error}</div>}
      {notice && <div className="muted">{notice}</div>}
      {data && (
        <>
          <div className="grid-2">
            {data.plans.filter((plan) => plan.code !== "FULL_SHOP").map((plan) => (
              <article className="card stack" key={plan.id}>
                <strong>{plan.name}</strong>
                <div className="metric-value">{money.format(plan.amount)}</div>
                <div className="faint">{plan.interval.toLowerCase()} · {plan.description}</div>
                <div className="chips">
                  {plan.features.map((feature) => (
                    <span className="chip" key={feature}>{feature.replaceAll("_", " ").toLowerCase()}</span>
                  ))}
                </div>
                <button
                  className="btn"
                  type="button"
                  disabled={paying !== null}
                  onClick={() => void buy(plan)}
                >
                  {paying === plan.code ? "Opening checkout…" : `Pay ${money.format(plan.amount)}`}
                </button>
              </article>
            ))}
          </div>
          <section className="card tight">
            <div className="spread" style={{ padding: "16px 18px" }}>
              <strong>Your last 3 payments</strong>
            </div>
            {data.recentPayments.length === 0 ? (
              <EmptyState compact icon="card" title="No payments yet" hint="Pay a plan above and the last three show here." />
            ) : (
              data.recentPayments.map((payment) => (
              <div className="category-row" key={payment.id}>
                <div>
                  <div style={{ fontWeight: 650 }}>{payment.planName}</div>
                  <div className="faint">{when(payment.paidAt)}</div>
                </div>
                <span>{money.format(payment.amount)}</span>
                <span className="badge GREEN">{payment.status}</span>
              </div>
            ))
            )}
          </section>
        </>
      )}
    </div>
  );
}
