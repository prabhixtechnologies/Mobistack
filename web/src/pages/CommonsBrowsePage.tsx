import { FormEvent, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../lib/api";
import { useDebounced } from "../lib/useDebounced";
import { usePagedList } from "../lib/usePagedList";
import { useResource } from "../lib/useResource";
import { EmptyState } from "../ui/EmptyState";
import { TextField } from "../ui/Field";
import { PageHeader } from "../ui/PageHeader";
import { FitmentGroupBar } from "../ui/FitmentGroupBar";
import { useFitmentGroups } from "../lib/groups";
import { Icon } from "../ui/navIcons";
import { brandMark, brandTone } from "../lib/brandTone";
import type { CommonsBrand, CommonsComponent, CommonsDevice, CommonsStats } from "../lib/types";

type Tab = "devices" | "components" | "contribute";

function deviceLabel(device: CommonsDevice): string {
  return [device.name, device.variant].filter(Boolean).join(" ");
}

export function CommonsBrowsePage() {
  const [tab, setTab] = useState<Tab>("devices");
  const [query, setQuery] = useState("");
  const [brandId, setBrandId] = useState("");
  const settled = useDebounced(query);
  const fitment = useFitmentGroups();
  const stats = useResource<CommonsStats>(
    fitment.ready ? `/api/v1/commons/stats?groupId=${fitment.selected ?? ""}` : null,
  );
  const brands = useResource<CommonsBrand[]>("/api/v1/commons/brands");

  const devicePath = useMemo(() => {
    const params = new URLSearchParams();
    if (settled.trim().length >= 2) {
      params.set("q", settled.trim());
    } else if (brandId) {
      params.set("brandId", brandId);
    }
    const suffix = params.toString();
    return suffix ? `/api/v1/commons/devices?${suffix}` : "/api/v1/commons/devices";
  }, [settled, brandId]);

  const componentPath = useMemo(() => {
    const params = new URLSearchParams();
    if (settled.trim().length >= 2) {
      params.set("q", settled.trim());
    }
    const suffix = params.toString();
    return suffix ? `/api/v1/commons/components?${suffix}` : "/api/v1/commons/components";
  }, [settled]);

  const devices = usePagedList<CommonsDevice>(tab === "devices" ? devicePath : null, { size: 30 });
  const components = usePagedList<CommonsComponent>(tab === "components" ? componentPath : null, { size: 30 });

  return (
    <div className="page">
      <PageHeader
        kicker="Fitment Catalog"
        title="Browse catalog"
        subtitle={
          stats.data?.groupName
            ? `Shared inside ${stats.data.groupName}. Look up a phone, then open the parts that fit it.`
            : "Look up a phone, then open the parts that fit it."
        }
        actions={
          <Link className="btn ghost" to="/commons/standing">
            Your standing
          </Link>
        }
        meta={
          stats.data ? (
            <>
              <span className="page-stat">
                <strong>{stats.data.deviceCount}</strong> phones
              </span>
              <span className="page-stat">
                <strong>{stats.data.componentCount}</strong> parts
              </span>
              <span className="page-stat">
                Shared with <strong>{stats.data.shopCount}</strong> shops
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

      <div className="method-tabs" role="tablist" aria-label="Catalog sections">
        {(["devices", "components", "contribute"] as const).map((id) => (
          <button
            key={id}
            className={`method-tab ${tab === id ? "on" : ""}`}
            type="button"
            role="tab"
            aria-selected={tab === id}
            onClick={() => setTab(id)}
          >
            {id === "devices" ? "Phones" : id === "components" ? "Parts" : "Contribute"}
          </button>
        ))}
      </div>

      {tab !== "contribute" && (
        <div className={tab === "devices" ? "catalog-command" : "catalog-command catalog-command--solo"}>
          <label className="catalog-command__field">
            <Icon name="search" />
            <input
              className="catalog-command__input"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder={tab === "devices" ? "Search phones, model codes…" : "Search parts…"}
              autoFocus
              autoComplete="off"
              aria-label={tab === "devices" ? "Search phones" : "Search parts"}
            />
          </label>
          {tab === "devices" ? (
            <label className="catalog-command__brand">
              <span className="visually-hidden">Brand</span>
              <select
                className="select"
                value={brandId}
                aria-label="Brand"
                onChange={(event) => setBrandId(event.target.value)}
              >
                <option value="">All brands</option>
                {(brands.data ?? []).map((brand) => (
                  <option key={brand.id} value={brand.id}>
                    {brand.name}
                  </option>
                ))}
              </select>
            </label>
          ) : null}
        </div>
      )}

      {tab === "devices" && (
        <section>
          {devices.error && (
            <div className="error" role="alert">
              Could not load the catalog. {devices.error}
            </div>
          )}
          {devices.rows.length > 0 && (
            <div className="catalog-list">
              <div className="catalog-list__head">
                <span>Device</span>
                <span>Factory code</span>
                <span>Year</span>
                <span />
              </div>
              {devices.rows.map((device) => {
                const tone = brandTone(device.brandName);
                return (
                  <Link key={device.id} className="catalog-row" to={`/commons/devices/${device.id}`}>
                    <div className="catalog-row__device">
                      <span className="catalog-row__mark" style={{ background: tone.bg, color: tone.fg }}>
                        {brandMark(device.brandName)}
                      </span>
                      <div>
                        <div className="catalog-row__name">{deviceLabel(device)}</div>
                        <span className="catalog-row__brand">{device.brandName}</span>
                      </div>
                    </div>
                    <span className="catalog-row__sku">{device.modelCode ?? "—"}</span>
                    <span className="catalog-row__year">{device.releaseYear ?? "—"}</span>
                    <span className="catalog-row__go">Fitments</span>
                  </Link>
                );
              })}
            </div>
          )}
          {devices.loading && devices.rows.length === 0 && <EmptyState compact icon="search" title="Loading phones…" />}
          {!devices.loading && !devices.error && devices.rows.length === 0 && (
            <EmptyState
              compact
              icon="search"
              title={settled.trim() ? "No matching phone" : "No phones in the catalog yet"}
              hint="Try a shorter model name, or contribute one."
            />
          )}
          {devices.hasMore && (
            <div className="spread" style={{ paddingTop: 8 }}>
              <button className="btn ghost" type="button" onClick={devices.loadMore} disabled={devices.loadingMore}>
                {devices.loadingMore ? "Loading…" : `Show more of ${devices.total}`}
              </button>
            </div>
          )}
        </section>
      )}

      {tab === "components" && (
        <section>
          {components.error && <div className="error">{components.error}</div>}
          {components.rows.length > 0 && (
            <div className="catalog-list">
              <div className="catalog-list__head">
                <span>Part</span>
                <span>Category</span>
                <span />
                <span />
              </div>
              {components.rows.map((component) => {
                const tone = brandTone(component.categoryCode);
                return (
                  <Link key={component.id} className="catalog-row" to={`/commons/components/${component.id}`}>
                    <div className="catalog-row__device">
                      <span className="catalog-row__mark" style={{ background: tone.bg, color: tone.fg }}>
                        {brandMark(component.name)}
                      </span>
                      <div>
                        <div className="catalog-row__name">{component.name}</div>
                        <span className="catalog-row__brand">{component.categoryCode.replaceAll("_", " ")}</span>
                      </div>
                    </div>
                    <span className="catalog-row__sku">{component.categoryCode.replaceAll("_", " ")}</span>
                    <span className="catalog-row__year" />
                    <span className="catalog-row__go">Fits phones</span>
                  </Link>
                );
              })}
            </div>
          )}
          {components.loading && components.rows.length === 0 && <EmptyState compact icon="search" title="Loading parts…" />}
          {!components.loading && components.rows.length === 0 && (
            <EmptyState compact icon="search" title={settled.trim() ? "No matching part" : "No parts yet"} />
          )}
          {components.hasMore && (
            <div className="spread" style={{ paddingTop: 8 }}>
              <button className="btn ghost" type="button" onClick={components.loadMore} disabled={components.loadingMore}>
                {components.loadingMore ? "Loading…" : `Show more of ${components.total}`}
              </button>
            </div>
          )}
        </section>
      )}

      {tab === "contribute" && <ContributeForm brands={brands.data ?? []} />}
    </div>
  );
}

function ContributeForm({ brands }: { brands: CommonsBrand[] }) {
  const [kind, setKind] = useState<"ADD_DEVICE" | "ADD_COMPONENT">("ADD_DEVICE");
  const [brand, setBrand] = useState(brands[0]?.name ?? "");
  const [name, setName] = useState("");
  const [variant, setVariant] = useState("");
  const [modelCode, setModelCode] = useState("");
  const [categoryCode, setCategoryCode] = useState("DISPLAY_FOLDER");
  const [description, setDescription] = useState("");
  const [reason, setReason] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const payload =
        kind === "ADD_DEVICE"
          ? { brand, name, variant: variant || undefined, modelCode: modelCode || undefined }
          : { categoryCode, name, description: description || undefined };
      await api("/api/v1/commons/contributions", {
        method: "POST",
        body: JSON.stringify({ kind, payload, reason: reason || undefined }),
      });
      setMessage("Submitted. Trusted contributors apply immediately; everyone else waits in the review queue.");
      setName("");
      setVariant("");
      setModelCode("");
      setDescription("");
      setReason("");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not submit that.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="card catalog-contribute" onSubmit={submit}>
      <div className="catalog-contribute__intro">
        <p className="page-kicker">Shared catalog</p>
        <strong>Add a phone or part</strong>
        <p className="faint">
          This is not your stock. It becomes a fact every shop can look up. Trusted contributors apply
          immediately; everyone else waits in review.
        </p>
      </div>
      <div className="stack">
        <label className="form-field">
          <span className="form-field__label">Kind</span>
          <select className="select" value={kind} onChange={(event) => setKind(event.target.value as typeof kind)}>
            <option value="ADD_DEVICE">Phone</option>
            <option value="ADD_COMPONENT">Part</option>
          </select>
        </label>
        {kind === "ADD_DEVICE" ? (
          <>
            <TextField label="Brand" value={brand} onChange={(event) => setBrand(event.target.value)} required />
            <TextField label="Name" value={name} onChange={(event) => setName(event.target.value)} placeholder="Realme 6" required />
            <TextField label="Variant" value={variant} onChange={(event) => setVariant(event.target.value)} />
            <TextField label="Factory code" value={modelCode} onChange={(event) => setModelCode(event.target.value)} placeholder="RMX2001" />
          </>
        ) : (
          <>
            <TextField
              label="Category code"
              value={categoryCode}
              onChange={(event) => setCategoryCode(event.target.value)}
              placeholder="DISPLAY_FOLDER"
              required
            />
            <TextField label="Part name" value={name} onChange={(event) => setName(event.target.value)} required />
            <TextField label="Description" value={description} onChange={(event) => setDescription(event.target.value)} />
          </>
        )}
        <TextField label="Why this belongs" value={reason} onChange={(event) => setReason(event.target.value)} />
        {error && <div className="error">{error}</div>}
        {message && <p className="faint">{message}</p>}
        <button className="btn" type="submit" disabled={busy}>
          {busy ? "Sending…" : "Submit"}
        </button>
      </div>
    </form>
  );
}
