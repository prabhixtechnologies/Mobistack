import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { brandMark, brandSwatch } from "../lib/brandTone";
import { commonsBrowsePath } from "../lib/commonsQuery";
import { useFitmentGroups } from "../lib/groups";
import { useDebounced } from "../lib/useDebounced";
import { usePagedList } from "../lib/usePagedList";
import { useResource } from "../lib/useResource";
import { EmptyState, ErrorState } from "../ui/EmptyState";
import { SearchField } from "../ui/Field";
import { FitmentGroupBar } from "../ui/FitmentGroupBar";
import { Icon } from "../ui/navIcons";
import type { CommonsBrand, CommonsCategory, CommonsDevice } from "../lib/types";

export function CommonsBrandPage({ brandId, category }: { brandId: string; category?: string | null }) {
  const fitment = useFitmentGroups();
  const brands = useResource<CommonsBrand[]>(
    fitment.ready
      ? `/api/v1/mobistack/commons/brands${category ? `?categoryCode=${encodeURIComponent(category)}` : ""}`
      : null,
  );
  const categories = useResource<CommonsCategory[]>(
    fitment.ready && category ? "/api/v1/mobistack/commons/categories" : null,
  );
  const brand = (brands.data ?? []).find((row) => row.id === brandId);
  const partName = (categories.data ?? []).find((row) => row.code.toUpperCase() === category?.toUpperCase())?.name ?? category;
  const [query, setQuery] = useState("");
  const term = useDebounced(query).trim();
  const devices = usePagedList<CommonsDevice>(
    fitment.ready && brandId
      ? `/api/v1/mobistack/commons/devices?brandId=${brandId}${category ? `&categoryCode=${encodeURIComponent(category)}` : ""}${term ? `&q=${encodeURIComponent(term)}` : ""}`
      : null,
    { size: 200 },
  );

  const sentinel = useRef<HTMLDivElement>(null);
  const { hasMore, loading, loadingMore, loadMore } = devices;
  useEffect(() => {
    const node = sentinel.current;
    if (!node || !hasMore || loading || loadingMore) return;
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) loadMore();
      },
      { rootMargin: "600px 0px" },
    );
    observer.observe(node);
    return () => observer.disconnect();
  }, [hasMore, loading, loadingMore, loadMore]);

  if (devices.error && !term) {
    return (
      <div className="page">
        <ErrorState message={devices.error} onRetry={devices.reload} />
      </div>
    );
  }

  const name = brand?.name ?? devices.rows[0]?.brandName ?? "Brand";
  const total = brand?.deviceCount ?? devices.total;
  const mapped = brand?.mappedDeviceCount ?? 0;
  const coverage = category && total > 0 ? Math.round((mapped / total) * 100) : null;

  return (
    <div className="page fx-home">
      <section className="fx-hero" style={brandSwatch(name)}>
        <div className="fx-hero__top">
          <span className="fx-hero__icon fx-hero__icon--part fx-tile__mark" aria-hidden>
            {brandMark(name)}
          </span>
          <div className="fx-hero__copy">
            <p className="fx-hero__kicker">
              <Link to="/commons">Fitment Catalog</Link>
              {category && (
                <>
                  <span className="fx-crumb-sep" aria-hidden>
                    /
                  </span>
                  <Link to={commonsBrowsePath({ category })}>{partName}</Link>
                </>
              )}
            </p>
            <h1>{name}</h1>
            <p className="fx-hero__sub">
              {category ? "Pick a phone to see its compatible spares." : "Pick a phone to see what fits it."}
            </p>
          </div>
          <div className="fx-hero__stats">
            <span className="fx-stat">
              <strong>{formatCount(total)}</strong> phones
            </span>
            {coverage !== null && (
              <>
                <span className="fx-stat">
                  <strong>{formatCount(mapped)}</strong> shared
                </span>
                <span className="fx-stat">
                  <strong>{coverage}%</strong> mapped
                </span>
              </>
            )}
          </div>
        </div>
        <div className="fx-hero__tools">
          <SearchField
            className="fx-search"
            label={`Search ${name} phones`}
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder={`Search ${name}: model name or factory code`}
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

      <div className="fx-section">
        <h2>{term ? `Matches for “${term}”` : "Phones"}</h2>
        <span>
          {devices.loading
            ? "Loading…"
            : devices.rows.length < devices.total
              ? `Showing ${formatCount(devices.rows.length)} of ${formatCount(devices.total)}`
              : `${formatCount(devices.total)} ${devices.total === 1 ? "phone" : "phones"}`}
          {category && !term ? " · phones without a shared family are model-specific" : ""}
        </span>
      </div>
      {devices.error && term && <ErrorState message={devices.error} onRetry={devices.reload} />}

      <div className="fx-phones">
        {devices.rows.map((device) => {
          const detail = device.modelCode ?? (device.releaseYear ? String(device.releaseYear) : null);
          return (
            <Link
              key={device.id}
              className="fx-phone"
              to={`/commons/devices/${device.id}${category ? `?category=${encodeURIComponent(category)}` : ""}`}
            >
              <span className="fx-phone__copy">
                <span className="fx-phone__name">{[device.name, device.variant].filter(Boolean).join(" ")}</span>
                {detail && (
                  <span className={device.modelCode ? "fx-phone__code" : "fx-phone__year"}>{detail}</span>
                )}
              </span>
              <span className="fx-phone__go" aria-hidden>
                <Icon name="chevronRight" />
              </span>
            </Link>
          );
        })}
        {devices.loading &&
          devices.rows.length === 0 &&
          Array.from({ length: 12 }, (_, index) => <span key={index} className="fx-phone fx-phone--ghost" />)}
      </div>
      {!devices.loading && !devices.error && devices.rows.length === 0 && (
        term ? (
          <EmptyState
            compact
            icon="search"
            title={`No ${name} phone matches “${term}”`}
            hint="Try fewer letters, or the factory code printed on the back."
          />
        ) : (
          <EmptyState compact icon="search" title="No phones for this brand" />
        )
      )}
      {devices.hasMore && (
        <>
          <div ref={sentinel} aria-hidden />
          <button className="btn ghost fx-more" type="button" onClick={devices.loadMore} disabled={devices.loadingMore}>
            {devices.loadingMore ? "Loading…" : `Show more (${formatCount(devices.total - devices.rows.length)} left)`}
          </button>
        </>
      )}
    </div>
  );
}

function formatCount(value: number): string {
  return value.toLocaleString("en-IN");
}
