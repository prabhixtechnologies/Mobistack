import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { brandMark, brandTone } from "../lib/brandTone";
import { canManageGroup, useFitmentGroups } from "../lib/groups";
import { usePagedList } from "../lib/usePagedList";
import { useResource } from "../lib/useResource";
import { EmptyState, ErrorState } from "../ui/EmptyState";
import { AddFamilyModal, phoneCaption } from "../ui/CatalogEditor";
import { FitmentGroupBar } from "../ui/FitmentGroupBar";
import { LoadMore } from "../ui/DataTable";
import { PageHeader } from "../ui/PageHeader";
import type { CommonsBrand, CommonsCategory, CommonsFamily } from "../lib/types";

export function CommonsCategoryPage() {
  const { code } = useParams();
  const fitment = useFitmentGroups();
  const [addFamily, setAddFamily] = useState(false);
  const categories = useResource<CommonsCategory[]>(
    fitment.ready ? "/api/v1/mobistack/commons/categories" : null,
  );
  const families = usePagedList<CommonsFamily>(
    fitment.ready && code ? `/api/v1/mobistack/commons/families?categoryCode=${encodeURIComponent(code)}` : null,
    { size: 24 },
  );
  const brands = useResource<CommonsBrand[]>(
    fitment.ready && code ? `/api/v1/mobistack/commons/brands?categoryCode=${encodeURIComponent(code)}` : null,
  );
  const category = (categories.data ?? []).find((row) => row.code === code);
  const partName = category?.name ?? code ?? "Part type";

  if (families.error) {
    return (
      <div className="page">
        <ErrorState message={families.error} onRetry={families.reload} />
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
        kicker={<Link to="/commons">Fitment Catalog</Link>}
        title={partName}
        subtitle="Pick a brand, then a phone. The phone stays on this spare."
        actions={
          canManageGroup(fitment.current) ? (
            <button className="btn" type="button" onClick={() => setAddFamily(true)}>
              Add family
            </button>
          ) : null
        }
      />
      {brands.error && <ErrorState message={brands.error} onRetry={brands.reload} />}
      {!brands.error && (
        <div className="catalog-grid">
          {(brands.data ?? []).map((brand) => (
            <Link
              key={brand.id}
              className="catalog-tile catalog-tile--brand"
              to={`/commons/brands/${brand.id}?category=${encodeURIComponent(code ?? "")}`}
            >
              <span className="catalog-tile__mark" style={brandTone(brand.name)}>
                {brandMark(brand.name)}
              </span>
              <strong>{brand.name}</strong>
              <p className="faint">
                {brand.deviceCount ?? 0} {(brand.deviceCount ?? 0) === 1 ? "phone" : "phones"}
              </p>
            </Link>
          ))}
        </div>
      )}
      {!brands.loading && (brands.data ?? []).length === 0 && !brands.error && (
        <EmptyState compact icon="search" title={`No phones take ${partName} yet`} />
      )}

      {families.loading && <p className="muted">Loading families…</p>}
      {families.rows.length > 0 && <h2 className="catalog-section-label">Spares in this part type</h2>}
      <div className="catalog-family-list">
        {families.rows.map((family) => (
          <article className="catalog-family" key={family.id}>
            <div className="spread">
              <div>
                <strong>{family.name}</strong>
                <p className="faint">
                  {family.members.length} {family.members.length === 1 ? "phone" : "phones"}
                </p>
              </div>
              <Link className="btn ghost" to={`/commons/components/${family.id}`}>
                Open
              </Link>
            </div>
            <div className="chips">
              {family.members.slice(0, 8).map((member) => (
                <Link
                  className="chip"
                  key={member.id}
                  to={`/commons/devices/${member.id}${code ? `?category=${encodeURIComponent(code)}` : ""}`}
                >
                  {phoneCaption(member)}
                </Link>
              ))}
              {family.members.length > 8 && <span className="chip">+{family.members.length - 8}</span>}
            </div>
          </article>
        ))}
      </div>
      {!families.loading && families.rows.length === 0 && (
        <EmptyState
          icon="link"
          title="No families yet"
          hint="Add phones that take the same spare, then save them as one family."
        />
      )}
      <LoadMore
        loaded={families.rows.length}
        total={families.total}
        hasMore={families.hasMore}
        loadingMore={families.loadingMore}
        onLoadMore={families.loadMore}
        noun="families"
      />
      <AddFamilyModal
        open={addFamily}
        categoryCode={code}
        onClose={() => setAddFamily(false)}
        onCreated={() => {
          families.reload();
          categories.reload();
        }}
      />
    </div>
  );
}
