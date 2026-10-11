import { FormEvent, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { api } from "../lib/api";
import { brandMark, brandTone } from "../lib/brandTone";
import { commonsBrowsePath } from "../lib/commonsQuery";
import { canManageGroup, useFitmentGroups } from "../lib/groups";
import { useDebounced } from "../lib/useDebounced";
import { usePagedList } from "../lib/usePagedList";
import { useResource } from "../lib/useResource";
import { EmptyState, ErrorState } from "../ui/EmptyState";
import { AddFamilyModal, AddPhoneModal } from "../ui/CatalogEditor";
import { FitmentGroupBar } from "../ui/FitmentGroupBar";
import { SearchField, TextField } from "../ui/Field";
import { Icon } from "../ui/navIcons";
import { PartIcon, partTone } from "../ui/PartCategory";
import type { CommonsCategory, CommonsDevice, CommonsStats } from "../lib/types";
import { CommonsBrandPage } from "./CommonsBrandPage";
import { CommonsCategoryPage } from "./CommonsCategoryPage";

export function CommonsBrowsePage() {
  const [params] = useSearchParams();
  const brands = params.get("brands")?.trim() ?? "";
  const category = params.get("category")?.trim() ?? "";
  if (brands) return <CommonsBrandPage brandId={brands} category={category || null} />;
  if (category) return <CommonsCategoryPage code={category} />;
  return <CatalogHome />;
}

function CatalogHome() {
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

  const parts = categories.data ?? [];
  const measured = parts.filter((part) => part.totalDeviceCount > 0);
  const coverage = measured.length
    ? Math.round(
        (measured.reduce((sum, part) => sum + part.mappedDeviceCount / part.totalDeviceCount, 0) / measured.length) * 100,
      )
    : null;

  return (
    <div className="page fx-home">
      <section className="fx-hero">
        <div className="fx-hero__top">
          <span className="fx-hero__icon" aria-hidden>
            <Icon name="globe" />
          </span>
          <div className="fx-hero__copy">
            <p className="fx-hero__kicker">Fitment Catalog</p>
            <h1>{stats.data?.groupName ?? "Catalog"}</h1>
            <p className="fx-hero__sub">Pick a spare, then a brand, then a phone.</p>
          </div>
          <div className="fx-hero__stats">
            {stats.data && (
              <>
                <span className="fx-stat fx-stat--a">
                  <strong>{formatCount(stats.data.deviceCount)}</strong> phones
                </span>
                <span className="fx-stat fx-stat--b">
                  <strong>{formatCount(stats.data.componentCount)}</strong> families
                </span>
              </>
            )}
            {coverage !== null && (
              <span className="fx-stat fx-stat--c">
                <strong>{coverage}%</strong> mapped
              </span>
            )}
          </div>
          {canManageGroup(fitment.current) && (
            <div className="fx-hero__actions">
              <button className="btn ghost sm" type="button" onClick={() => setAddPhone(true)}>
                Add phone
              </button>
              <button className="btn sm" type="button" onClick={() => setAddFamily(true)}>
                Add family
              </button>
            </div>
          )}
        </div>
        <div className="fx-hero__tools">
          <SearchField
            className="fx-search"
            label="Search phones"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search a phone: iPhone 11, RMX2001, Galaxy A10…"
            autoComplete="off"
          />
          <FitmentGroupBar
            compact
            groups={fitment.groups}
            selected={fitment.selected}
            choose={fitment.choose}
            create={fitment.create}
            current={fitment.current}
          />
        </div>
      </section>

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
            <>
              <div className="fx-section">
                <h2>Spare parts</h2>
                <span>{parts.length} types · tap one to pick a brand</span>
              </div>
              <div className="fx-grid">
                {parts.map((category) => {
                  const share = category.totalDeviceCount
                    ? Math.round((category.mappedDeviceCount / category.totalDeviceCount) * 100)
                    : 0;
                  const empty = category.mappedDeviceCount === 0;
                  return (
                    <Link
                      key={category.code}
                      className={`fx-tile${empty ? " fx-tile--empty" : ""}`}
                      style={partTone(category.code)}
                      to={commonsBrowsePath({ category: category.code })}
                    >
                      <span className="fx-tile__head">
                        <span className="fx-tile__icon">
                          <PartIcon code={category.code} />
                        </span>
                        <span className="fx-tile__title">
                          <span className="fx-tile__name">{category.name}</span>
                          <span className="fx-tile__sub">
                            {formatCount(category.familyCount)} {category.familyCount === 1 ? "family" : "families"}
                          </span>
                        </span>
                      </span>
                      <span className="fx-tile__bar" aria-hidden>
                        <span style={{ width: `${share}%` }} />
                      </span>
                      <span className="fx-tile__meta">
                        {empty ? (
                          <span>Not mapped yet</span>
                        ) : (
                          <span>
                            <strong>{formatCount(category.mappedDeviceCount)}</strong> phones mapped
                          </span>
                        )}
                        <span className="fx-tile__pct">{share}%</span>
                      </span>
                    </Link>
                  );
                })}
                {categories.loading &&
                  parts.length === 0 &&
                  Array.from({ length: 10 }, (_, index) => <span key={index} className="fx-tile fx-tile--ghost" />)}
              </div>
            </>
          )}
        </>
      )}

      <ContributeStrip />

      <AddPhoneModal open={addPhone} onClose={() => setAddPhone(false)} onCreated={reload} />
      <AddFamilyModal open={addFamily} onClose={() => setAddFamily(false)} onCreated={reload} />
    </div>
  );
}

function formatCount(value: number): string {
  return value.toLocaleString("en-IN");
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
