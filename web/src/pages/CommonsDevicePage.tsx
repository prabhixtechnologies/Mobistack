import { useMemo, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { api } from "../lib/api";
import { useAccess } from "../lib/access";
import { useAuth } from "../lib/auth";
import { brandMark, brandTone } from "../lib/brandTone";
import { commonsBrowsePath } from "../lib/commonsQuery";
import { hasFeature } from "../lib/plan";
import { useFitmentGroups } from "../lib/groups";
import { useResource } from "../lib/useResource";
import { EmptyState, ErrorState } from "../ui/EmptyState";
import { Modal } from "../ui/Modal";
import { PageHeader } from "../ui/PageHeader";
import { FitmentGroupBar } from "../ui/FitmentGroupBar";
import { TextField } from "../ui/Field";
import { phoneCaption } from "../ui/CatalogEditor";
import { Icon } from "../ui/navIcons";
import type { CatalogStockRow, CommonsDevice, CommonsFamily, CommonsFit } from "../lib/types";

/** Drops a generated one-phone placeholder after its imported Combo Support family supersedes it. */
function canonicalFamilies(rows: CommonsFamily[]): CommonsFamily[] {
  const drop = new Set<string>();
  const imported = rows.filter((row) => row.description?.toLowerCase().includes("combo support"));
  for (const family of rows) {
    if (family.members.length !== 1) continue;
    const member = family.members[0];
    const expectedName = [member.brandName, member.name, member.variant, family.categoryName]
      .filter(Boolean)
      .join(" ")
      .replace(/\s+/g, " ")
      .trim();
    if (family.name.toLowerCase() !== expectedName.toLowerCase()) continue;
    const covered = imported.some(
      (candidate) =>
        candidate.categoryCode.toUpperCase() === family.categoryCode.toUpperCase() &&
        candidate.members.some((candidateMember) => candidateMember.id === member.id),
    );
    if (covered) drop.add(family.id);
  }
  return rows.filter((row) => !drop.has(row.id));
}

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

  const allFamilies = useMemo(() => canonicalFamilies(families.data ?? []), [families.data]);

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
    const familyIds = new Set(displayedFamilies.map((family) => family.id));
    return (fits.data ?? []).filter((fit) => familyIds.has(fit.componentId));
  }, [fits.data, displayedFamilies]);
  const fitByComponent = useMemo(() => {
    const map = new Map<string, CommonsFit>();
    for (const fit of displayedFits) map.set(fit.componentId, fit);
    return map;
  }, [displayedFits]);
  const summaryFamilies = categoryFilter ? displayedFamilies : allFamilies;
  const compatibleModelCount = useMemo(() => {
    const deviceIds = new Set<string>();
    for (const family of summaryFamilies) {
      for (const member of family.members) {
        if (member.id !== id) deviceIds.add(member.id);
      }
    }
    return deviceIds.size;
  }, [summaryFamilies, id]);

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
    <div className="page commons-device-page">
      <FitmentGroupBar
        groups={fitment.groups}
        selected={fitment.selected}
        choose={fitment.choose}
        create={fitment.create}
        current={fitment.current}
      />

      <div className="device-catalog-hero">
        <div className="device-catalog-hero__glow" aria-hidden />
        <div className="device-catalog-hero__content">
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
                  to={commonsBrowsePath({
                    brands: device.data.brandId,
                    category: categoryFilter,
                  })}
                >
                  {device.data.brandName}
                </Link>
                {categoryFilter && (
                  <>
                    <span className="catalog-kicker-sep">/</span>
                    <Link to={commonsBrowsePath({ category: categoryFilter })}>
                      {activeCategoryObj?.name ?? categoryFilter}
                    </Link>
                  </>
                )}
              </div>
            }
            title={title}
            subtitle={
              categoryFilter
                ? `${activeCategoryObj?.name ?? categoryFilter} options linked to this phone and the models that share them.`
                : "Explore verified spare families and every phone model that shares them."
            }
            meta={
              <div className="device-hero__facts">
                {device.data.modelCode ? <code>{device.data.modelCode}</code> : null}
                {device.data.releaseYear ? <span className="page-stat">{device.data.releaseYear}</span> : null}
              </div>
            }
          />
        </div>
        <div className="device-catalog-hero__stats">
          <div className="device-catalog-stat">
            <span className="device-catalog-stat__icon"><Icon name="box" /></span>
            <span><strong>{summaryFamilies.length}</strong> spare {summaryFamilies.length === 1 ? "family" : "families"}</span>
          </div>
          <div className="device-catalog-stat">
            <span className="device-catalog-stat__icon"><Icon name="link" /></span>
            <span><strong>{compatibleModelCount}</strong> compatible {compatibleModelCount === 1 ? "model" : "models"}</span>
          </div>
          <div className="device-catalog-stat">
            <span className="device-catalog-stat__icon"><Icon name="shield" /></span>
            <span>Community fitment data</span>
          </div>
        </div>
      </div>

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
      {displayedFamilies.length > 0 && (
        <div className="catalog-results-heading">
          <div>
            <span className="catalog-results-heading__eyebrow">Compatibility results</span>
            <h2>{categoryFilter ? activeCategoryObj?.name ?? categoryFilter : "Available spares"}</h2>
          </div>
          <p>
            {displayedFamilies.length === 1
              ? "One spare family covers this phone. Compatible models are the other phones on that same spare."
              : `${displayedFamilies.length} spare families cover this phone. Each card is a part the others do not fully replace.`}
          </p>
        </div>
      )}
      <div className="catalog-family-list">
        {displayedFamilies.map((family, index) => {
          const others = family.members.filter((member) => member.id !== id);
          const ownMember = family.members.find((member) => member.id === id);
          const fit = fitByComponent.get(family.id);
          const fitmentId = fit?.fitmentId ?? ownMember?.fitmentId;
          const disputeFit: CommonsFit | null = fit ?? (fitmentId && id
            ? {
                fitmentId,
                componentId: family.id,
                componentName: family.name,
                categoryCode: family.categoryCode,
                deviceId: id,
                fit: ownMember?.fit ?? "COMPATIBLE",
                confirmations: 0,
                disputes: 0,
                disputed: ownMember?.disputed ?? false,
                verified: ownMember?.verified ?? false,
              }
            : null);
          const stockRows = (stock.data ?? []).filter((row) => row.componentId === family.id);
          return (
            <section className="catalog-family-card" key={family.id}>
              <div className="catalog-family-card__head">
                <div className="catalog-family-card__identity">
                  <span className="catalog-family-card__number" aria-hidden>
                    {String(index + 1).padStart(2, "0")}
                  </span>
                  <div>
                    <span className="catalog-family-card__category">{family.categoryName}</span>
                    <h3 className="catalog-family-card__title">{family.name}</h3>
                    <div className="catalog-family-card__summary">
                      <Icon name={others.length > 0 ? "people" : "user"} />
                      {others.length > 0
                        ? `Shared with ${others.length} other ${others.length === 1 ? "model" : "models"}`
                        : `Specific to ${title}`}
                    </div>
                  </div>
                </div>
                <Link className="catalog-family-card__action" to={`/commons/components/${family.id}`}>
                  <span>Spare details</span>
                  <Icon name="chevronRight" />
                </Link>
              </div>

              {fitmentId && (
                <div className="catalog-fit-bar">
                  <div className="catalog-fit-bar__status">
                    <span className={`catalog-fit-quality${fit?.disputed || ownMember?.disputed ? " is-disputed" : ""}`}>
                      {(fit?.fit ?? ownMember?.fit ?? "COMPATIBLE").replaceAll("_", " ")}
                    </span>
                    <span>
                      {fit ? `${fit.confirmations} confirmations · ${fit.disputes} disputes` : "Community fitment"}
                      {(fit?.verified || ownMember?.verified) && " · Verified"}
                      {(fit?.disputed || ownMember?.disputed) && " · Disputed"}
                    </span>
                  </div>
                  <div className="catalog-fit-bar__actions">
                    <button
                      className="btn ghost compact"
                      type="button"
                      disabled={busy}
                      onClick={() => void contribute("CONFIRM_FITMENT", fitmentId)}
                    >
                      Confirm
                    </button>
                    <button
                      className="btn ghost compact"
                      type="button"
                      disabled={busy || !disputeFit}
                      onClick={() => disputeFit && setDisputeFor(disputeFit)}
                    >
                      Dispute
                    </button>
                  </div>
                </div>
              )}

              <div className="catalog-family-card__compat">
                <div className="catalog-section-heading">
                  <div>
                    <span className="catalog-section-label">Compatible phone models</span>
                    <span className="catalog-section-hint">
                      {others.length > 0 ? "Select a model to explore its fitment" : "No shared models in this family"}
                    </span>
                  </div>
                  {others.length > 0 && <span className="catalog-count-pill">{others.length}</span>}
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
                        <span className="catalog-companion-chip__copy">
                          <strong>{phoneCaption(member)}</strong>
                          {member.modelCode ? <small>{member.modelCode}</small> : null}
                        </span>
                        <span className="catalog-companion-chip__arrow"><Icon name="chevronRight" /></span>
                      </Link>
                    ))}
                  </div>
                ) : (
                  <div className="catalog-unique-callout">
                    <span className="catalog-unique-callout__icon"><Icon name="shield" /></span>
                    <div>
                      <strong>Dedicated fitment</strong>
                      <span>Only {title} uses this spare in the current catalog group.</span>
                    </div>
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
                      <span><Icon name="box" /> No shop SKU linked to this spare yet.</span>
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
