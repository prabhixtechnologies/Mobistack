import { useMemo, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { api } from "../lib/api";
import { useAccess } from "../lib/access";
import { useAuth } from "../lib/auth";
import { brandMark, brandTone } from "../lib/brandTone";
import { hasFeature } from "../lib/plan";
import { useFitmentGroups } from "../lib/groups";
import { useResource } from "../lib/useResource";
import { EmptyState, ErrorState } from "../ui/EmptyState";
import { Modal } from "../ui/Modal";
import { PageHeader } from "../ui/PageHeader";
import { FitmentGroupBar } from "../ui/FitmentGroupBar";
import { TextField } from "../ui/Field";
import { phoneCaption } from "../ui/CatalogEditor";
import type { CatalogStockRow, CommonsDevice, CommonsFamily, CommonsFit } from "../lib/types";

export function CommonsDevicePage() {
  const { id } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();
  const categoryFilter = searchParams.get("category");

  const { user } = useAuth();
  const access = useAccess();
  const canOpenStock = hasFeature(user, "INVENTORY") && access.has("INVENTORY_READ");
  const canSell = hasFeature(user, "SALES") && access.has("SALES_READ");
  const fitment = useFitmentGroups();

  const device = useResource<CommonsDevice>(id ? `/api/v1/mobistack/commons/devices?deviceId=${id}` : null);
  const families = useResource<CommonsFamily[]>(
    id && fitment.ready
      ? `/api/v1/mobistack/commons/devices/family?deviceId=${id}${
          categoryFilter ? `&categoryCode=${encodeURIComponent(categoryFilter)}` : ""
        }`
      : null,
  );
  const fits = useResource<CommonsFit[]>(
    id && fitment.ready ? `/api/v1/mobistack/commons/devices/fits?deviceId=${id}` : null,
  );
  const stock = useResource<CatalogStockRow[]>(
    id && canOpenStock ? `/api/v1/mobistack/inventory/catalog-links/devices/stock?catalogDeviceId=${id}` : null,
  );

  const [disputeFor, setDisputeFor] = useState<CommonsFit | null>(null);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const allFamilies = families.data ?? [];

  // Extract distinct categories available for this phone
  const availableCategories = useMemo(() => {
    const map = new Map<string, { code: string; name: string; count: number }>();
    for (const f of allFamilies) {
      const code = f.categoryCode.toUpperCase();
      const existing = map.get(code);
      if (existing) {
        existing.count++;
      } else {
        map.set(code, { code: f.categoryCode, name: f.categoryName, count: 1 });
      }
    }
    return Array.from(map.values());
  }, [allFamilies]);

  // Active category object, if filter is set
  const activeCategoryObj = useMemo(() => {
    if (!categoryFilter) return null;
    return availableCategories.find(
      (c) => c.code.toUpperCase() === categoryFilter.toUpperCase(),
    );
  }, [availableCategories, categoryFilter]);

  // Filtered families
  const displayedFamilies = useMemo(() => {
    if (!categoryFilter) return [];
    return allFamilies.filter(
      (family) => family.categoryCode.toUpperCase() === categoryFilter.toUpperCase(),
    );
  }, [allFamilies, categoryFilter]);
  const displayedFits = useMemo(() => {
    const rows = fits.data ?? [];
    if (!categoryFilter) return rows;
    return rows.filter(
      (fit) => (fit.categoryCode ?? "").toUpperCase() === categoryFilter.toUpperCase(),
    );
  }, [fits.data, categoryFilter]);

  async function contribute(kind: "CONFIRM_FITMENT" | "DISPUTE_FITMENT", fitmentId: string, note?: string) {
    setBusy(true);
    setError(null);
    try {
      await api("/api/v1/mobistack/commons/contributions", {
        method: "POST",
        body: JSON.stringify({ kind, targetId: fitmentId, reason: note || undefined }),
      });
      fits.reload();
      families.reload();
      setDisputeFor(null);
      setReason("");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not record that.");
    } finally {
      setBusy(false);
    }
  }

  if (device.error) {
    return (
      <div className="page">
        <ErrorState message={device.error} onRetry={device.reload} />
      </div>
    );
  }

  if (!device.data) {
    return (
      <div className="page">
        <div className="skeleton skeleton--title" />
      </div>
    );
  }

  const title = [device.data.name, device.data.variant].filter(Boolean).join(" ");

  return (
    <div className="page">
      <FitmentGroupBar
        groups={fitment.groups}
        selected={fitment.selected}
        choose={fitment.choose}
        create={fitment.create}
        current={fitment.current}
      />

      <PageHeader
        avatar={
          <span className="device-hero__mark" style={brandTone(device.data.brandName)}>
            {brandMark(device.data.brandName)}
          </span>
        }
        kicker={
          <div className="catalog-kicker-nav">
            <Link to="/commons">Fitment Catalog</Link>
            <span className="catalog-kicker-sep">/</span>
            <Link
              to={`/commons/brands/${device.data.brandId}${
                categoryFilter ? `?category=${encodeURIComponent(categoryFilter)}` : ""
              }`}
            >
              {device.data.brandName}
            </Link>
            {categoryFilter && (
              <>
                <span className="catalog-kicker-sep">/</span>
                <Link to={`/commons/categories/${encodeURIComponent(categoryFilter)}`}>
                  {activeCategoryObj?.name ?? categoryFilter}
                </Link>
              </>
            )}
          </div>
        }
        title={title}
        subtitle={
          categoryFilter
            ? `${activeCategoryObj?.name ?? categoryFilter} for this phone, and the other models that take the same spare.`
            : "Choose the spare. This phone is not a list of every component."
        }
        meta={
          <div className="device-hero__facts">
            {device.data.modelCode ? <code>{device.data.modelCode}</code> : null}
            {device.data.releaseYear ? <span className="page-stat">{device.data.releaseYear}</span> : null}
          </div>
        }
      />

      {error && <div className="error">{error}</div>}

      {!categoryFilter && availableCategories.length > 0 && (
        <div className="catalog-filter-bar">
          {availableCategories.map((cat) => (
            <button
              type="button"
              key={cat.code}
              className="catalog-filter-chip"
              onClick={() => setSearchParams({ category: cat.code })}
            >
              {cat.name} ({cat.count})
            </button>
          ))}
        </div>
      )}

      {/* Spares and compatible models */}
      <div className="stack" style={{ gap: 16 }}>
        {displayedFamilies.map((family) => {
          const others = family.members.filter((member) => member.id !== id);
          const stockRows = (stock.data ?? []).filter((row) => row.componentId === family.id);
          return (
            <section className="catalog-family-card" key={family.id}>
              <div className="catalog-family-card__head">
                <div>
                  <span className="catalog-family-card__category">{family.categoryName}</span>
                  <h3 className="catalog-family-card__title">{family.name}</h3>
                </div>
                <Link className="btn ghost compact" to={`/commons/components/${family.id}`}>
                  View spare details →
                </Link>
              </div>

              <div className="catalog-family-card__compat">
                <div className="catalog-section-label">
                  Compatible Phone Models {others.length > 0 ? `(${others.length})` : ""}
                </div>
                {others.length > 0 ? (
                  <div className="catalog-companion-grid">
                    {others.map((member) => (
                      <Link
                        className="catalog-companion-chip"
                        key={member.id}
                        to={`/commons/devices/${member.id}${categoryFilter ? `?category=${encodeURIComponent(categoryFilter)}` : ""}`}
                        title={`Open ${phoneCaption(member)}`}
                      >
                        <span className="catalog-companion-chip__mark" style={brandTone(member.brandName)}>
                          {brandMark(member.brandName)}
                        </span>
                        <span>{phoneCaption(member)}</span>
                        <span className="catalog-companion-chip__arrow">→</span>
                      </Link>
                    ))}
                  </div>
                ) : (
                  <div className="catalog-unique-callout">
                    <span>Unique to {title} — no other models share this spare yet in this group.</span>
                  </div>
                )}
              </div>

              {canOpenStock && (
                <div className="catalog-stock-box">
                  <div className="catalog-section-label">Shop Inventory & Pricing</div>
                  {stockRows.length > 0 ? (
                    stockRows.map((row) => (
                      <div className="catalog-stock-row" key={row.variantId}>
                        <div>
                          <div style={{ fontWeight: 650 }}>{row.name}</div>
                          <div className="faint">{row.sku}</div>
                        </div>
                        <div className="row" style={{ alignItems: "center", gap: 12 }}>
                          <span className="badge GREEN">{row.available} on hand</span>
                          {canSell && (
                            <Link className="btn compact" to={`/sales?q=${encodeURIComponent(row.sku)}`}>
                              Sell
                            </Link>
                          )}
                        </div>
                      </div>
                    ))
                  ) : (
                    <div className="catalog-no-stock">
                      <span>No shop SKU linked to this spare yet.</span>
                      <Link className="linkish" to="/inventory/catalog-links">
                        Link inventory SKU →
                      </Link>
                    </div>
                  )}
                </div>
              )}
            </section>
          );
        })}
      </div>

      {!families.loading && (categoryFilter || availableCategories.length === 0) && displayedFamilies.length === 0 && (
        <EmptyState
          compact
          icon="link"
          title={
            categoryFilter
              ? `No ${activeCategoryObj?.name ?? categoryFilter} on this phone`
              : availableCategories.length > 0
                ? "Pick a spare"
                : "Nothing linked yet"
          }
          hint={
            categoryFilter
              ? "This model has no family for the spare you already chose."
              : availableCategories.length > 0
                ? "The part type comes first. The list below is that spare, not every component on the phone."
                : "Confirm a part from the bench, or add a family."
          }
        />
      )}

      {/* Technical fitment edges (collapsible for clean presentation) */}
      <details className="catalog-edges-card card">
        <summary className="catalog-edges-summary">
          <span>Technical fitment data ({displayedFits.length} edges)</span>
          <span className="muted" style={{ fontSize: 13, fontWeight: 400 }}>
            Community confirmations & disputes ▾
          </span>
        </summary>
        <div style={{ padding: "0 18px 16px" }}>
          {displayedFits.map((fit) => (
            <div className="category-row" key={fit.fitmentId}>
              <div>
                <Link to={`/commons/components/${fit.componentId}`} style={{ fontWeight: 650 }}>
                  {fit.componentName ?? "Part"}
                </Link>
                <div className="faint">
                  {fit.fit}
                  {fit.verified ? " · Verified" : ""}
                  {fit.disputed ? " · Disputed" : ""}
                  {` · ${fit.confirmations} confirm · ${fit.disputes} dispute`}
                </div>
              </div>
              <div className="row">
                <button
                  className="btn ghost compact"
                  type="button"
                  disabled={busy}
                  onClick={() => void contribute("CONFIRM_FITMENT", fit.fitmentId)}
                >
                  Confirm
                </button>
                <button
                  className="btn ghost compact"
                  type="button"
                  disabled={busy}
                  onClick={() => setDisputeFor(fit)}
                >
                  Dispute
                </button>
              </div>
            </div>
          ))}
          {displayedFits.length === 0 && (
            <p className="faint" style={{ margin: "12px 0 0" }}>
              No technical fitment edges recorded.
            </p>
          )}
        </div>
      </details>

      <Modal
        open={disputeFor != null}
        title="Dispute this fit"
        description="Disputes wait for review. Say what you saw on the bench."
        onClose={() => !busy && setDisputeFor(null)}
        footer={
          <>
            <button className="btn ghost" type="button" onClick={() => setDisputeFor(null)} disabled={busy}>
              Cancel
            </button>
            <button
              className="btn"
              type="button"
              disabled={busy || !reason.trim()}
              onClick={() => disputeFor && void contribute("DISPUTE_FITMENT", disputeFor.fitmentId, reason.trim())}
            >
              {busy ? "Sending…" : "Submit dispute"}
            </button>
          </>
        }
      >
        <TextField label="What doesn’t fit" value={reason} onChange={(event) => setReason(event.target.value)} />
      </Modal>
    </div>
  );
}
