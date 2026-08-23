import { useEffect, useState } from "react";
import { api } from "../lib/api";
import { useAuth } from "../lib/auth";
import { BRAND, copyrightLine } from "../lib/brand";
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
  maxDevicesPerUser?: number;
}

export function SettingsPage() {
  const { user, refreshUser } = useAuth();
  const [shop, setShop] = useState<Shop | null>(null);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api<Shop>("/api/v1/shop").then(setShop).catch((err: Error) => setError(err.message));
  }, []);

  if (!shop) {
    return <div className="page">{error ?? "Loading…"}</div>;
  }

  async function save() {
    setError(null);
    setSaved(false);
    try {
      setShop(await api<Shop>("/api/v1/shop", { method: "PUT", body: JSON.stringify(shop) }));
      setSaved(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save");
    }
  }

  return (
    <div className="page">
      <PageHeader
        kicker="Workspace"
        title="Shop settings"
        subtitle="Printed on every invoice. Keep it exact."
        actions={
          <button className="btn" type="button" onClick={() => void save()}>
            Save
          </button>
        }
      />
      {error && <div className="error">{error}</div>}
      {saved && <div className="muted">Saved.</div>}
      <VerificationCard
        email={user?.email}
        phone={user?.phone}
        emailVerified={Boolean(user?.emailVerified)}
        phoneVerified={Boolean(user?.phoneVerified)}
        onVerified={() => void refreshUser()}
      />
      <div className="grid-2">
        <Field label="Shop name" value={shop.name} onChange={(name) => setShop({ ...shop, name })} />
        <Field label="Phone" value={shop.phone ?? ""} onChange={(phone) => setShop({ ...shop, phone })} />
        <Field label="Email" value={shop.email ?? ""} onChange={(email) => setShop({ ...shop, email })} />
        <Field label="City" value={shop.city ?? ""} onChange={(city) => setShop({ ...shop, city })} />
        <Field label="GST" value={shop.gstNumber ?? ""} onChange={(gstNumber) => setShop({ ...shop, gstNumber })} />
        <Field label="Invoice prefix" value={shop.invoicePrefix} onChange={(invoicePrefix) => setShop({ ...shop, invoicePrefix })} />
        <label className="stack">
          <span className="faint">Join code</span>
          <input className="field" value={shop.joinCode ?? ""} readOnly />
        </label>
        <label className="stack">
          <span className="faint">Max devices per user</span>
          <input
            className="field"
            type="number"
            min={1}
            max={20}
            value={shop.maxDevicesPerUser ?? 3}
            onChange={(e) => setShop({ ...shop, maxDevicesPerUser: Number(e.target.value) })}
          />
        </label>
        <label className="row">
          <input
            type="checkbox"
            checked={Boolean(shop.requireCompatibilityApproval)}
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
        <input className="field" value={code} onChange={(e) => setCode(e.target.value)} placeholder="One-time code" />
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

function Field({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  return (
    <label className="stack">
      <span className="faint">{label}</span>
      <input className="field" value={value} onChange={(e) => onChange(e.target.value)} />
    </label>
  );
}
