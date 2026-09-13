import { useEffect, useState } from "react";
import { api } from "../lib/api";
import { useAccess } from "../lib/access";
import { useAction } from "../lib/useAction";
import { useAuth } from "../lib/auth";
import { BRAND, copyrightLine } from "../lib/brand";
import { Link } from "react-router-dom";
import { PageHeader } from "../ui/PageHeader";

interface Shop {
  id: string;
  name: string;
  phone?: string;
  email?: string;
  city?: string;
  gstNumber?: string;
  invoicePrefix: string;
  currencyCode: string;
  timezone: string;
  joinCode?: string;
  requireCompatibilityApproval?: boolean;
  extraScreens?: number;
  screenSeats?: number;
  screensInUse?: number;
}

export function SettingsPage() {
  const { user, refreshUser } = useAuth();
  const access = useAccess();
  const canEdit = access.has("SETTINGS_WRITE");
  const [shop, setShop] = useState<Shop | null>(null);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api<Shop>("/api/v1/shop").then(setShop).catch((err: Error) => setError(err.message));
  }, []);

  const save = useAction(
    async (current: Shop) => {
      setSaved(false);
      setShop(await api<Shop>("/api/v1/shop", { method: "PUT", body: JSON.stringify(current) }));
      setSaved(true);
    },
    { fallbackError: "Could not save those settings." },
  );

  if (!shop) {
    return <div className="page">{error ?? "Loading…"}</div>;
  }
  const loaded = shop;

  return (
    <div className="page">
      <PageHeader
        kicker="Workspace"
        title="Shop settings"
        subtitle="Printed on every invoice. Keep it exact."
        actions={
          canEdit ? (
            <button className="btn" type="button" disabled={save.busy} onClick={() => void save.run(loaded)}>
              {save.busy ? "Saving…" : "Save"}
            </button>
          ) : null
        }
      />
      {error && <div className="error">{error}</div>}
      {save.error && <div className="error">{save.error}</div>}
      {saved && <div className="muted">Saved.</div>}
      {!canEdit && (
        <div className="card tight faint">
          These are the details printed on your invoices. Your role can see them but not change them.
        </div>
      )}
      <VerificationCard
        email={user?.email}
        phone={user?.phone}
        emailVerified={Boolean(user?.emailVerified)}
        phoneVerified={Boolean(user?.phoneVerified)}
        onVerified={() => void refreshUser()}
      />
      <div className="grid-2">
        <Field label="Shop name" value={shop.name} readOnly={!canEdit} onChange={(name) => setShop({ ...shop, name })} />
        <Field label="Phone" value={shop.phone ?? ""} readOnly={!canEdit} onChange={(phone) => setShop({ ...shop, phone })} />
        <Field label="Email" value={shop.email ?? ""} readOnly={!canEdit} onChange={(email) => setShop({ ...shop, email })} />
        <Field label="City" value={shop.city ?? ""} readOnly={!canEdit} onChange={(city) => setShop({ ...shop, city })} />
        <Field
          label="GST"
          value={shop.gstNumber ?? ""}
          readOnly={!canEdit}
          onChange={(gstNumber) => setShop({ ...shop, gstNumber })}
        />
        <Field
          label="Invoice prefix"
          value={shop.invoicePrefix}
          readOnly={!canEdit}
          onChange={(invoicePrefix) => setShop({ ...shop, invoicePrefix })}
        />
        <label className="stack">
          <span className="faint">Join code</span>
          <input className="field" value={shop.joinCode ?? ""} readOnly />
        </label>
        <div className="stack">
          <span className="faint">Screens</span>
          <div>
            {shop.screensInUse ?? 0} of {shop.screenSeats ?? 1} in use
            {(shop.extraScreens ?? 0) > 0
              ? (shop.screenSeats ?? 1) > 1
                ? ` · ${shop.extraScreens} extra this month`
                : ` · ${shop.extraScreens} extra lapsed this month`
              : ""}
          </div>
          <Link to="/billing" className="auth-link">Extra screens are ₹50 each per month</Link>
        </div>
        <label className="row">
          <input
            type="checkbox"
            checked={Boolean(shop.requireCompatibilityApproval)}
            disabled={!canEdit}
            onChange={(e) => setShop({ ...shop, requireCompatibilityApproval: e.target.checked })}
          />
          Require approval before compatibility changes
        </label>
      </div>
      <div className="card">
        <div className="metric-label">Product</div>
        <div className="metric-value">{BRAND.product}</div>
        <p className="muted" style={{ marginBottom: 0 }}>
          {BRAND.tagline}
          <br />
          {copyrightLine()}
        </p>
      </div>
    </div>
  );
}

function VerificationCard({
  email,
  phone,
  emailVerified,
  phoneVerified,
  onVerified,
}: {
  email?: string;
  phone?: string;
  emailVerified: boolean;
  phoneVerified: boolean;
  onVerified: () => void;
}) {
  const [code, setCode] = useState("");
  const [channel, setChannel] = useState<"email" | "phone" | "whatsapp">("email");
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function send() {
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      if (channel === "email") {
        await api("/api/v1/auth/email-otp", { method: "POST", body: JSON.stringify({ email }) });
        setNotice("A verification code was sent to your email.");
      } else {
        await api(`/api/v1/auth/${channel === "whatsapp" ? "whatsapp" : "phone"}/start`, {
          method: "POST",
          body: JSON.stringify({ phone, channel: channel === "whatsapp" ? "WHATSAPP" : "SMS" }),
        });
        setNotice("A verification code was sent to that number.");
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not send a code");
    } finally {
      setBusy(false);
    }
  }

  async function verify() {
    setBusy(true);
    setError(null);
    try {
      if (channel === "email") {
        await api("/api/v1/auth/email-otp/verify", {
          method: "POST",
          body: JSON.stringify({ email, code }),
        });
      } else {
        await api(`/api/v1/auth/${channel === "whatsapp" ? "whatsapp" : "phone"}/verify`, {
          method: "POST",
          body: JSON.stringify({ phone, code }),
        });
      }
      setNotice("Verified.");
      setCode("");
      onVerified();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not verify");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="card stack">
      <strong>Account verification</strong>
      <div className="muted">
        Email: {emailVerified ? "verified" : "not verified"} · Phone: {phoneVerified ? "verified" : "not verified"}
      </div>
      <div className="row">
        <button className={channel === "email" ? "btn" : "btn ghost"} type="button" onClick={() => setChannel("email")}>
          Email
        </button>
        <button className={channel === "phone" ? "btn" : "btn ghost"} type="button" onClick={() => setChannel("phone")}>
          SMS
        </button>
        <button className={channel === "whatsapp" ? "btn" : "btn ghost"} type="button" onClick={() => setChannel("whatsapp")}>
          WhatsApp
        </button>
      </div>
      <div className="row">
        <input className="field" value={code} onChange={(e) => setCode(e.target.value)} placeholder="One-time code" aria-label="One-time verification code" autoComplete="one-time-code" />
        <button className="btn ghost" type="button" disabled={busy} onClick={() => void send()}>
          Send code
        </button>
        <button className="btn" type="button" disabled={busy || !code} onClick={() => void verify()}>
          Verify
        </button>
      </div>
      {notice && <div className="muted">{notice}</div>}
      {error && <div className="error">{error}</div>}
    </div>
  );
}

function Field({
  label,
  value,
  onChange,
  readOnly = false,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  readOnly?: boolean;
}) {
  return (
    <label className="stack">
      <span className="faint">{label}</span>
      <input className="field" value={value} readOnly={readOnly} onChange={(e) => onChange(e.target.value)} />
    </label>
  );
}
