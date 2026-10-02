import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { api, money } from "../lib/api";
import { useAuth } from "../lib/auth";
import { captureCheckoutOrder, type CheckoutOrder } from "../lib/payOrder";
import { EmptyState } from "../ui/EmptyState";
import { Icon } from "../ui/navIcons";

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

const FEATURE_LABELS: Record<string, string> = {
  COMPATIBILITY: "Shared phone and part compatibility",
  DASHBOARD: "Live shop dashboard",
  SALES: "Sales and payment records",
  REPAIRS: "Repair jobs and status tracking",
  INVENTORY: "Inventory and stock control",
  PURCHASES: "Purchase records",
  CUSTOMERS: "Customer directory",
  SUPPLIERS: "Supplier directory",
  REPORTS: "Business reports",
  MOVEMENTS: "Stock movement history",
  MEMBERS: "Team access",
  IMPORT: "Bulk imports",
  AUDIT: "Audit trail",
};

export function featureLabel(feature: string): string {
  return FEATURE_LABELS[feature] ?? feature.replaceAll("_", " ").toLowerCase();
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
    setData(await api<Overview>("/api/v1/mobistack/billing"));
  }

  useEffect(() => {
    load().catch((err: Error) => setError(err.message));
  }, []);

  // The backend owns the active Razorpay environment. Prefer its current key so a web image
  // built before a Test -> Live switch cannot keep presenting the stale checkout mode.
  const publishableKey =
    data?.razorpayKeyId?.trim()
    || (import.meta.env.VITE_RAZORPAY_KEY_ID as string | undefined)?.trim()
    || "";
  const liveCheckout = Boolean(data?.razorpayEnabled && publishableKey.startsWith("rzp_live_"));

  async function activateLocal() {
    setError(null);
    setNotice(null);
    setUnlocking(true);
    try {
      await api("/api/v1/mobistack/billing/dev/activate", { method: "POST" });
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
      const order = await api<CheckoutOrder>("/api/v1/mobistack/billing/orders", {
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
    <div className="page billing-page">
      <section className="billing-hero">
        <div className="billing-hero__copy">
          <div className="billing-hero__meta">
            <p className="page-kicker">MobiStack plans</p>
            <span className={`billing-live${liveCheckout ? " billing-live--on" : ""}`}>
              <i aria-hidden />
              {liveCheckout ? "Live payments" : "Secure checkout"}
            </span>
          </div>
          <h1>Choose what your shop needs now.</h1>
          <p>
            Start with reliable fitment or run the entire counter. Switch plans without losing your
            catalog, private notes, or shop history.
          </p>
          <div className="billing-hero__trust" aria-label="Plan assurances">
            <span>Razorpay protected</span>
            <span>No joining fee</span>
            <span>Monthly access</span>
          </div>
        </div>
        <div className="billing-hero__visual" aria-hidden>
          <span className="billing-shield"><Icon name="shield" /></span>
          <strong>Safe checkout</strong>
          <p>MobiStack does not prefill your stored email or mobile number. Razorpay handles the payment details.</p>
          <span className="billing-hero__provider">Powered by Razorpay</span>
        </div>
      </section>
      {activating && (
        <div className="banner billing-status">
          Your shop is ready. Choose one plan below to switch on the features you need.
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
      {publishableKey.startsWith("rzp_test_") && (
        <div className="banner">
          Razorpay Test Mode: use card <strong>4111 1111 1111 1111</strong>, any future expiry, CVV
          123, OTP <strong>1234</strong>.
        </div>
      )}
      {error && <div className="error">{error}</div>}
      {notice && <div className="muted">{notice}</div>}
      {data && (
        <>
          <div className="billing-plans" aria-label="MobiStack plans">
            {data.plans.map((plan, index) => {
              const recommended = plan.code === "FULL_SHOP";
              const selected = current?.status === "ACTIVE" && current.planCode === plan.code;
              return (
              <article className={`plan-card${recommended ? " plan-card--recommended" : ""}${selected ? " plan-card--current" : ""}`} key={plan.id}>
                <div className="plan-card__top">
                  <div>
                    <p className="plan-card__eyebrow">0{index + 1} · {recommended ? "Complete shop" : "Essential lookup"}</p>
                    <h2>{plan.name}</h2>
                  </div>
                  {selected ? (
                    <span className="plan-card__recommended plan-card__recommended--current">Current plan</span>
                  ) : recommended ? (
                    <span className="plan-card__recommended">Recommended</span>
                  ) : null}
                </div>
                <div className="plan-card__price">
                  <strong>{money.format(plan.amount)}</strong>
                  <span>/ month</span>
                </div>
                <p className="plan-card__description">{plan.description}</p>
                <div className="plan-card__rule" />
                <p className="plan-card__includes">{recommended ? "Everything to run your counter" : "Made for fitment checks"}</p>
                <ul className="plan-card__features">
                  {plan.features.map((feature) => (
                    <li key={feature}>
                      <span aria-hidden>✓</span>
                      {featureLabel(feature)}
                    </li>
                  ))}
                  {!recommended ? (
                    <li className="plan-card__locked">
                      <span aria-hidden>—</span>
                      Inventory, sales and repairs stay locked
                    </li>
                  ) : null}
                </ul>
                <button
                  className={`btn plan-card__action${recommended ? "" : " secondary"}`}
                  type="button"
                  disabled={paying !== null || selected}
                  onClick={() => void buy(plan)}
                >
                  {selected
                    ? `Active until ${when(current?.periodEnd)}`
                    : paying === plan.code
                    ? "Opening secure checkout…"
                    : recommended
                      ? `Choose full shop · ${money.format(plan.amount)}`
                      : `Choose compatibility · ${money.format(plan.amount)}`}
                </button>
              </article>
              );
            })}
          </div>
          <section className="card tight billing-history">
            <div className="billing-history__head">
              <div>
                <p className="page-kicker">Receipts</p>
                <h2>Recent payments</h2>
              </div>
              <span className="faint">Last 3</span>
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
