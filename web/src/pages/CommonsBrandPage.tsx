import { Link, useParams, useSearchParams } from "react-router-dom";
import { brandMark, brandTone } from "../lib/brandTone";
import { useFitmentGroups } from "../lib/groups";
import { usePagedList } from "../lib/usePagedList";
import { useResource } from "../lib/useResource";
import { EmptyState, ErrorState } from "../ui/EmptyState";
import { FitmentGroupBar } from "../ui/FitmentGroupBar";
import { PageHeader } from "../ui/PageHeader";
import type { CommonsBrand, CommonsCategory, CommonsDevice } from "../lib/types";

export function CommonsBrandPage() {
  const { brandId } = useParams();
  const [searchParams] = useSearchParams();
  const category = searchParams.get("category");
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
  const devices = usePagedList<CommonsDevice>(
    fitment.ready && brandId
      ? `/api/v1/mobistack/commons/devices?brandId=${brandId}${category ? `&categoryCode=${encodeURIComponent(category)}` : ""}`
      : null,
    { size: 40 },
  );

  if (devices.error) {
    return (
      <div className="page">
        <ErrorState message={devices.error} onRetry={devices.reload} />
      </div>
    );
  }

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
        icon="globe"
        kicker={
          category ? (
            <span>
              <Link to="/commons">Fitment Catalog</Link>
              <span className="catalog-kicker-sep"> / </span>
              <Link to={`/commons/categories/${encodeURIComponent(category)}`}>{partName}</Link>
            </span>
          ) : (
            <Link to="/commons">Fitment Catalog</Link>
          )
        }
        title={brand?.name ?? "Brand"}
        subtitle={
          category
            ? `${partName} · ${brand?.deviceCount ?? devices.total} phones that take this spare.`
            : `${brand?.deviceCount ?? devices.total} phones in this group.`
        }
      />
      <section className="card tight">
        {devices.rows.map((device) => (
          <Link
            key={device.id}
            className="catalog-row"
            to={`/commons/devices/${device.id}${category ? `?category=${encodeURIComponent(category)}` : ""}`}
          >
            <div className="catalog-row__device">
              <span className="catalog-row__mark" style={brandTone(device.brandName)}>
                {brandMark(device.brandName)}
              </span>
              <div>
                <div className="catalog-row__name">{[device.name, device.variant].filter(Boolean).join(" ")}</div>
                <div className="catalog-row__brand">{device.modelCode ?? "No factory code"}</div>
              </div>
            </div>
            <span className="catalog-row__year">{device.releaseYear ?? "—"}</span>
            <span className="catalog-row__go">Open</span>
          </Link>
        ))}
        {!devices.loading && devices.rows.length === 0 && (
          <EmptyState compact icon="search" title="No phones for this brand" />
        )}
      </section>
      {devices.hasMore && (
        <button className="btn ghost" type="button" onClick={devices.loadMore}>
          Show more
        </button>
      )}
    </div>
  );
}
