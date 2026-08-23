import { useEffect, useState } from "react";
import { api } from "../lib/api";
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

function Field({ label, value, onChange }: { label: string; value: string; onChange: (value: string) => void }) {
  return (
    <label className="stack">
      <span className="faint">{label}</span>
      <input className="field" value={value} onChange={(e) => onChange(e.target.value)} />
    </label>
  );
}
