import { useMemo, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { api } from "../lib/api";
import { brandMark, brandTone } from "../lib/brandTone";
import { commonsBrowsePath } from "../lib/commonsQuery";
import { useFitmentGroups } from "../lib/groups";
import { useResource } from "../lib/useResource";
import { EmptyState, ErrorState } from "../ui/EmptyState";
import { Modal } from "../ui/Modal";
import { FitmentGroupBar } from "../ui/FitmentGroupBar";
import { TextField } from "../ui/Field";
import { phoneCaption } from "../ui/CatalogEditor";
import { Icon } from "../ui/navIcons";
import type { CommonsDevice, CommonsFamily, CommonsFit } from "../lib/types";

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

function categoryLabel(code: string | null): string | null {
  if (!code) return null;
  return code
    .toLowerCase()
    .replaceAll("_", " ")
    .replace(/\b\w/g, (letter) => letter.toUpperCase());
}

export function CommonsDevicePage() {
  const { id } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();
  const categoryFilter = searchParams.get("category");

  const fitment = useFitmentGroups();

  const device = useResource<CommonsDevice>(id ? `/api/v1/mobistack/commons/devices?deviceId=${id}` : null);
  const families = useResource<CommonsFamily[]>(
    id && fitment.ready ? `/api/v1/mobistack/commons/devices/family?deviceId=${id}` : null,
  );
  const fits = useResource<CommonsFit[]>(
    id && fitment.ready ? `/api/v1/mobistack/commons/devices/fits?deviceId=${id}` : null,
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
  const activeCategoryName = activeCategoryObj?.name ?? categoryLabel(categoryFilter);

  // Filtered families
  const displayedFamilies = useMemo(() => {
    if (!categoryFilter) return [];
    return allFamilies.filter(
      (family) => family.categoryCode.toUpperCase() === categoryFilter.toUpperCase(),
    );
  }, [allFamilies, categoryFilter]);
  const supportingFamilies = useMemo(
    () => displayedFamilies.filter((family) => family.members.some((member) => member.id !== id)),
    [displayedFamilies, id],
  );
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
  const compatiblePhones = useMemo(() => {
    const phones = new Map<
      string,
      { member: CommonsFamily["members"][number]; sharedFamilies: number }
    >();
    for (const family of supportingFamilies) {
      for (const member of family.members) {
        if (member.id === id) continue;
        const existing = phones.get(member.id);
        if (existing) {
          existing.sharedFamilies += 1;
        } else {
          phones.set(member.id, { member, sharedFamilies: 1 });
        }
      }
    }
    return Array.from(phones.values()).sort(
      (left, right) =>
        right.sharedFamilies - left.sharedFamilies ||
        phoneCaption(left.member).localeCompare(phoneCaption(right.member)),
    );
  }, [supportingFamilies, id]);
  const confirmationCount = useMemo(() => {
    const familyIds = new Set((categoryFilter ? supportingFamilies : allFamilies).map((family) => family.id));
    return (fits.data ?? [])
      .filter((fit) => familyIds.has(fit.componentId))
      .reduce((total, fit) => total + fit.confirmations, 0);
  }, [fits.data, categoryFilter, supportingFamilies, allFamilies]);

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

      <header className="dv-hero">
        <nav className="dv-crumbs" aria-label="Catalog path">
          <Link to="/commons">Fitment catalog</Link>
          <Icon name="chevronRight" />
          <Link to={commonsBrowsePath({ brands: device.data.brandId, category: categoryFilter })}>
            {device.data.brandName}
          </Link>
          {categoryFilter && (
            <>
              <Icon name="chevronRight" />
              <Link to={commonsBrowsePath({ category: categoryFilter })}>{activeCategoryName}</Link>
            </>
          )}
        </nav>

        <div className="dv-hero__body">
          <div className="dv-hero__identity">
            <span className="dv-hero__mark" style={brandTone(device.data.brandName)} aria-hidden>
              {brandMark(device.data.brandName)}
            </span>
            <div className="dv-hero__copy">
              <h1>{title}</h1>
              <p>
                {categoryFilter
                  ? `${activeCategoryName} parts that fit this phone, and the other models that take the same part.`
                  : "Verified spare families for this phone, and every model that shares them."}
              </p>
              <div className="dv-hero__tags">
                <span className="dv-tag">{device.data.brandName}</span>
                {device.data.modelCode ? <code className="dv-tag dv-tag--mono">{device.data.modelCode}</code> : null}
                {device.data.releaseYear ? <span className="dv-tag">Released {device.data.releaseYear}</span> : null}
              </div>
            </div>
          </div>

          <dl className="dv-stats">
            <div className="dv-stat dv-stat--models">
              <span className="dv-stat__icon"><Icon name="people" /></span>
              <div>
                <dt>{compatibleModelCount === 1 ? "Compatible phone" : "Compatible phones"}</dt>
                <dd>{compatibleModelCount}</dd>
              </div>
            </div>
            <div className="dv-stat dv-stat--proof">
              <span className="dv-stat__icon"><Icon name="shield" /></span>
              <div>
                <dt>{confirmationCount === 1 ? "Community confirmation" : "Community confirmations"}</dt>
                <dd>{confirmationCount}</dd>
              </div>
            </div>
          </dl>
        </div>
      </header>

      {error && <div className="error">{error}</div>}

      {!categoryFilter && availableCategories.length > 0 && (
        <div className="dv-tabs" role="tablist" aria-label="Part type">
          {availableCategories.map((cat) => {
            const active = categoryFilter?.toUpperCase() === cat.code.toUpperCase();
            return (
              <button
                type="button"
                role="tab"
                aria-selected={active}
                key={cat.code}
                className={`dv-tab${active ? " is-active" : ""}`}
                onClick={() => setSearchParams({ category: cat.code })}
              >
                <span>{cat.name}</span>
                <span className="dv-tab__count">{cat.count}</span>
              </button>
            );
          })}
        </div>
      )}

      {categoryFilter && compatiblePhones.length > 0 && (
        <section className="dv-compatible" aria-labelledby="compatible-phone-heading">
          <div className="dv-compatible__intro">
            <span className="dv-compatible__eyebrow">
              <Icon name="link" />
              Quick compatibility
            </span>
            <h2 id="compatible-phone-heading">
              Phones that share this {activeCategoryName}
            </h2>
            <p>
              Pick a phone to open its fitment. These models appear because they share at least one
              catalog compatibility record with {title}.
            </p>
          </div>
          <div className="dv-compatible__grid">
            {compatiblePhones.map(({ member, sharedFamilies }) => (
              <Link
                className="dv-compatible-phone"
                key={member.id}
                to={`/commons/devices/${member.id}?category=${encodeURIComponent(categoryFilter)}`}
              >
                <span className="dv-compatible-phone__mark" style={brandTone(member.brandName)} aria-hidden>
                  {brandMark(member.brandName)}
                </span>
                <span className="dv-compatible-phone__copy">
                  <strong>
                    {member.brandName === device.data?.brandName
                      ? [member.name, member.variant].filter(Boolean).join(" ")
                      : phoneCaption(member)}
                  </strong>
                  <span>
                    {member.modelCode ? <code>{member.modelCode}</code> : null}
                    {sharedFamilies > 1 ? <small>{sharedFamilies} independent matches</small> : null}
                  </span>
                </span>
                <span className="dv-compatible-phone__open">
                  Open
                  <Icon name="chevronRight" />
                </span>
              </Link>
            ))}
          </div>
        </section>
      )}

      {supportingFamilies.length > 0 && (
        <details className="dv-evidence">
          <summary>
            <span className="dv-evidence__icon"><Icon name="shield" /></span>
            <span className="dv-evidence__summary">
              <strong>Community evidence</strong>
              <small>
                {supportingFamilies.length} {supportingFamilies.length === 1 ? "record supports" : "records support"}
                {" "}the phone list
              </small>
            </span>
            <Icon name="chevronDown" className="dv-evidence__chevron" />
          </summary>
          <div className="dv-evidence__body">
            {supportingFamilies.map((family) => {
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
              const disputed = fit?.disputed || ownMember?.disputed;
              return (
                <div className="dv-evidence-row" key={family.id}>
                  <span className={`dv-evidence-row__status${disputed ? " is-disputed" : ""}`}>
                    <Icon name={disputed ? "alert" : "check"} />
                  </span>
                  <div className="dv-evidence-row__copy">
                    <strong>{family.name}</strong>
                    <span>
                      {categoryLabel(fit?.fit ?? ownMember?.fit ?? "COMPATIBLE")} · connects{" "}
                      {others.length + 1} phones
                      {fit ? ` · ${fit.confirmations} confirmed · ${fit.disputes} disputed` : ""}
                    </span>
                  </div>
                  <div className="dv-evidence-row__actions">
                    {fit?.confirmedByMe ? (
                      <span className="dv-evidence-row__mine"><Icon name="check" /> Confirmed by you</span>
                    ) : fit?.disputedByMe ? (
                      <span className="dv-evidence-row__mine is-disputed"><Icon name="alert" /> Disputed by you</span>
                    ) : fitmentId ? (
                      <>
                        <button
                          type="button"
                          disabled={busy}
                          onClick={() => void contribute("CONFIRM_FITMENT", fitmentId)}
                        >
                          Confirm
                        </button>
                        <button
                          className="is-dispute"
                          type="button"
                          disabled={busy || !disputeFit}
                          onClick={() => disputeFit && setDisputeFor(disputeFit)}
                        >
                          Dispute
                        </button>
                      </>
                    ) : null}
                    <Link to={`/commons/components/${family.id}`} aria-label={`Open ${family.name} details`}>
                      Details <Icon name="chevronRight" />
                    </Link>
                  </div>
                </div>
              );
            })}
          </div>
        </details>
      )}

      {!families.loading &&
        ((categoryFilter && compatiblePhones.length === 0) || availableCategories.length === 0) && (
        <EmptyState
          compact
          icon="link"
          title={
            categoryFilter
              ? `No compatible phones for ${activeCategoryName} yet`
              : "No compatibility data yet"
          }
          hint={
            categoryFilter
              ? "This phone is listed, but no other phone is confirmed to use the same part."
              : "The phone is listed, but no compatible phone models have been confirmed yet."
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
