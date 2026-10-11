import { useState } from "react";
import { Link } from "react-router-dom";
import { brandMark, brandSwatch } from "../lib/brandTone";
import { commonsBrowsePath } from "../lib/commonsQuery";
import { canManageGroup, useFitmentGroups } from "../lib/groups";
import { usePagedList } from "../lib/usePagedList";
import { useResource } from "../lib/useResource";
import { EmptyState, ErrorState } from "../ui/EmptyState";
import { AddFamilyModal, phoneCaption } from "../ui/CatalogEditor";
import { FitmentGroupBar } from "../ui/FitmentGroupBar";
import { LoadMore } from "../ui/DataTable";
import { PartIcon, partTone } from "../ui/PartCategory";
import type { CommonsBrand, CommonsCategory, CommonsFamily } from "../lib/types";

export function CommonsCategoryPage({ code }: { code: string }) {
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

  const rows = brands.data ?? [];
  const phones = rows.reduce((sum, brand) => sum + (brand.deviceCount ?? 0), 0);
  const mapped = rows.reduce((sum, brand) => sum + (brand.mappedDeviceCount ?? 0), 0);
  const coverage = phones > 0 ? Math.round((mapped / phones) * 100) : null;

  return (
    <div className="page fx-home">
      <section className="fx-hero fx-hero--part" style={partTone(code)}>
        <div className="fx-hero__top">
          <span className="fx-hero__icon fx-hero__icon--part" aria-hidden>
            <PartIcon code={code} />
          </span>
          <div className="fx-hero__copy">
            <p className="fx-hero__kicker">
              <Link to="/commons">Fitment Catalog</Link>
            </p>
            <h1>{partName}</h1>
            <p className="fx-hero__sub">Pick a brand, then a phone.</p>
          </div>
          {rows.length > 0 && (
            <div className="fx-hero__stats">
              <span className="fx-stat fx-stat--a">
                <strong>{formatCount(phones)}</strong> phones
              </span>
              <span className="fx-stat fx-stat--b">
                <strong>{formatCount(rows.length)}</strong> {rows.length === 1 ? "brand" : "brands"}
              </span>
              {coverage !== null && (
                <span className="fx-stat fx-stat--c">
                  <strong>{coverage}%</strong> mapped
                </span>
              )}
            </div>
          )}
          {canManageGroup(fitment.current) && (
            <div className="fx-hero__actions">
              <button className="btn sm" type="button" onClick={() => setAddFamily(true)}>
                Add family
              </button>
            </div>
          )}
        </div>
        <div className="fx-hero__tools">
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
      {brands.error && <ErrorState message={brands.error} onRetry={brands.reload} />}
      {!brands.error && rows.length > 0 && (
        <>
          <div className="fx-section">
            <h2>Brands</h2>
            <span>{rows.length} brands · tap one to pick a phone</span>
          </div>
          <div className="fx-grid">
            {rows.map((brand) => {
              const count = brand.deviceCount ?? 0;
              const done = brand.mappedDeviceCount ?? 0;
              const share = count > 0 ? Math.round((done / count) * 100) : 0;
              const empty = done === 0;
              const shown = !empty && share === 0 ? "<1%" : `${share}%`;
              return (
                <Link
                  key={brand.id}
                  className={`fx-tile${empty ? " fx-tile--empty" : ""}`}
                  style={brandSwatch(brand.name)}
                  to={commonsBrowsePath({ brands: brand.id, category: code })}
                >
                  <span className="fx-tile__head">
                    <span className="fx-tile__icon fx-tile__mark">{brandMark(brand.name)}</span>
                    <span className="fx-tile__title">
                      <span className="fx-tile__name">{brand.name}</span>
                      <span className="fx-tile__sub">
                        {formatCount(count)} {count === 1 ? "phone" : "phones"}
                      </span>
                    </span>
                  </span>
                  <span className="fx-tile__bar" aria-hidden>
                    <span style={{ width: `${empty ? 0 : Math.max(share, 2)}%` }} />
                  </span>
                  <span className="fx-tile__meta">
                    {empty ? <span>Not mapped yet</span> : <span><strong>{formatCount(done)}</strong> mapped</span>}
                    <span className="fx-tile__pct">{shown}</span>
                  </span>
                </Link>
              );
            })}
          </div>
        </>
      )}
      {brands.loading && rows.length === 0 && !brands.error && (
        <div className="fx-grid">
          {Array.from({ length: 10 }, (_, index) => (
            <span key={index} className="fx-tile fx-tile--ghost" />
          ))}
        </div>
      )}
      {!brands.loading && rows.length === 0 && !brands.error && (
        <EmptyState compact icon="search" title="No phones in this catalog yet" />
      )}

      {families.rows.length > 0 && (
        <div className="fx-section">
          <h2>Shared families</h2>
          <span>{families.total} {families.total === 1 ? "family" : "families"} · phones that take the same spare</span>
        </div>
      )}
      <div className="fx-families">
        {families.rows.map((family) => (
          <article className="fx-family" key={family.id}>
            <div className="fx-family__name">
              <strong>{family.name}</strong>
              <span>
                {family.members.length} {family.members.length === 1 ? "phone" : "phones"}
              </span>
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
            <Link className="btn ghost sm" to={`/commons/components/${family.id}`}>
              Open
            </Link>
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

function formatCount(value: number): string {
  return value.toLocaleString("en-IN");
}
