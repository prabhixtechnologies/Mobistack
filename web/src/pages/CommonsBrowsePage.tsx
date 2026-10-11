import { FormEvent, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../lib/api";
import { brandMark, brandTone } from "../lib/brandTone";
import { canManageGroup, useFitmentGroups } from "../lib/groups";
import { useDebounced } from "../lib/useDebounced";
import { usePagedList } from "../lib/usePagedList";
import { useResource } from "../lib/useResource";
import { EmptyState, ErrorState } from "../ui/EmptyState";
import { AddFamilyModal, AddPhoneModal } from "../ui/CatalogEditor";
import { FitmentGroupBar } from "../ui/FitmentGroupBar";
import { PageHeader } from "../ui/PageHeader";
import { TextField } from "../ui/Field";
import type { CommonsCategory, CommonsDevice, CommonsStats } from "../lib/types";

export function CommonsBrowsePage() {
  const [query, setQuery] = useState("");
  const [addPhone, setAddPhone] = useState(false);
  const [addFamily, setAddFamily] = useState(false);
  const settled = useDebounced(query);
  const fitment = useFitmentGroups();
  const stats = useResource<CommonsStats>(
    fitment.ready ? `/api/v1/mobistack/commons/stats?groupId=${fitment.selected ?? ""}` : null,
  );
  const categories = useResource<CommonsCategory[]>(
    fitment.ready ? "/api/v1/mobistack/commons/categories" : null,
  );
  const searching = settled.trim().length >= 2;
  const devices = usePagedList<CommonsDevice>(
    searching ? `/api/v1/mobistack/commons/devices?q=${encodeURIComponent(settled.trim())}` : null,
    { size: 24 },
  );

  function reload() {
    categories.reload();
    stats.reload();
    devices.reload();
  }

  return (
    <div className="page catalog-home">
      <PageHeader
        icon="globe"
        kicker="Fitment Catalog"
        title={stats.data?.groupName ?? "Catalog"}
        subtitle="Pick the spare first, then a brand, then a phone. The phone shows that spare only."
        actions={
          canManageGroup(fitment.current) ? (
            <div className="row">
              <button className="btn ghost" type="button" onClick={() => setAddPhone(true)}>
                Add phone
              </button>
              <button className="btn" type="button" onClick={() => setAddFamily(true)}>
                Add family
              </button>
            </div>
          ) : null
        }
        meta={
          stats.data ? (
            <>
              <span className="page-stat">
                <strong>{stats.data.deviceCount}</strong> phones
              </span>
              <span className="page-stat">
                <strong>{stats.data.componentCount}</strong> families
              </span>
            </>
          ) : null
        }
      />

      <FitmentGroupBar
        groups={fitment.groups}
        selected={fitment.selected}
        choose={fitment.choose}
        create={fitment.create}
        current={fitment.current}
      />

      <TextField
        label="Search"
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        placeholder="iPhone 11, RMX2001, Galaxy A10…"
        autoComplete="off"
      />

      {searching ? (
        <section className="card tight">
          {devices.rows.map((device) => (
            <Link key={device.id} className="catalog-row" to={`/commons/devices/${device.id}`}>
              <div className="catalog-row__device">
                <span className="catalog-row__mark" style={brandTone(device.brandName)}>
                  {brandMark(device.brandName)}
                </span>
                <div>
                  <div className="catalog-row__name">{[device.name, device.variant].filter(Boolean).join(" ")}</div>
                  <div className="catalog-row__brand">{device.brandName}</div>
                </div>
              </div>
              <span className="catalog-row__sku">{device.modelCode ?? "—"}</span>
              <span className="catalog-row__go">Open</span>
            </Link>
          ))}
          {!devices.loading && devices.rows.length === 0 && (
            <EmptyState compact icon="search" title="No phones match that" hint="Try a factory code or a shorter name." />
          )}
        </section>
      ) : (
        <>
          {categories.error && <ErrorState message={categories.error} onRetry={categories.reload} />}
          {!categories.error && (
            <div className="catalog-grid">
              {(categories.data ?? []).map((category, index) => (
                <Link
                  key={category.code}
                  className="catalog-tile"
                  to={`/commons/categories/${encodeURIComponent(category.code)}`}
                >
                  <span className="catalog-tile__n">{String(index + 1).padStart(2, "0")}</span>
                  <strong>{category.name}</strong>
                  <p className="faint">
                    {category.familyCount} {category.familyCount === 1 ? "family" : "families"} · {category.deviceCount} phones
                  </p>
                </Link>
              ))}
            </div>
          )}
        </>
      )}

      <ContributeStrip />

      <AddPhoneModal open={addPhone} onClose={() => setAddPhone(false)} onCreated={reload} />
      <AddFamilyModal open={addFamily} onClose={() => setAddFamily(false)} onCreated={reload} />
    </div>
  );
}

function ContributeStrip() {
  const [kind, setKind] = useState<"ADD_DEVICE" | "ADD_COMPONENT">("ADD_DEVICE");
  const [name, setName] = useState("");
  const [brand, setBrand] = useState("");
  const [categoryCode, setCategoryCode] = useState("DISPLAY_FOLDER");
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [open, setOpen] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const payload =
        kind === "ADD_DEVICE" ? { brand, name } : { categoryCode, name };
      await api("/api/v1/mobistack/commons/contributions", {
        method: "POST",
        body: JSON.stringify({ kind, payload }),
      });
      setMessage("Submitted for the shared catalog.");
      setName("");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not submit that.");
    } finally {
      setBusy(false);
    }
  }

  if (!open) {
    return (
      <p className="faint catalog-home__contribute">
        Missing a phone?{" "}
        <button className="linkish" type="button" onClick={() => setOpen(true)}>
          Propose it
        </button>
      </p>
    );
  }

  return (
    <form className="card catalog-contribute" onSubmit={submit}>
      <strong>Propose to the shared catalog</strong>
      <div className="stack">
        <label className="form-field">
          <span className="form-field__label">Kind</span>
          <select className="select" value={kind} onChange={(event) => setKind(event.target.value as typeof kind)}>
            <option value="ADD_DEVICE">Phone</option>
            <option value="ADD_COMPONENT">Part family</option>
          </select>
        </label>
        {kind === "ADD_DEVICE" ? (
          <TextField label="Brand" value={brand} onChange={(event) => setBrand(event.target.value)} required />
        ) : (
          <TextField label="Part type" value={categoryCode} onChange={(event) => setCategoryCode(event.target.value)} required />
        )}
        <TextField label="Name" value={name} onChange={(event) => setName(event.target.value)} required />
        {error && <div className="error">{error}</div>}
        {message && <p className="faint">{message}</p>}
        <div className="row">
          <button className="btn ghost" type="button" onClick={() => setOpen(false)}>
            Close
          </button>
          <button className="btn" type="submit" disabled={busy}>
            {busy ? "Sending…" : "Submit"}
          </button>
        </div>
      </div>
    </form>
  );
}
