import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { canManageGroup, useFitmentGroups } from "../lib/groups";
import { useResource } from "../lib/useResource";
import { EmptyState, ErrorState } from "../ui/EmptyState";
import { AddFamilyModal, phoneCaption } from "../ui/CatalogEditor";
import { FitmentGroupBar } from "../ui/FitmentGroupBar";
import { PageHeader } from "../ui/PageHeader";
import type { CommonsCategory, CommonsFamily } from "../lib/types";

export function CommonsCategoryPage() {
  const { code } = useParams();
  const fitment = useFitmentGroups();
  const [addFamily, setAddFamily] = useState(false);
  const categories = useResource<CommonsCategory[]>(
    fitment.ready ? "/api/v1/mobistack/commons/categories" : null,
  );
  const families = useResource<CommonsFamily[]>(
    fitment.ready && code ? `/api/v1/mobistack/commons/families?categoryCode=${encodeURIComponent(code)}` : null,
  );
  const category = (categories.data ?? []).find((row) => row.code === code);

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
        title={category?.name ?? code ?? "Part type"}
        subtitle={
          category
            ? `${category.familyCount} families · ${category.deviceCount} phones that share this spare.`
            : "Phones that take the same part."
        }
        actions={
          canManageGroup(fitment.current) ? (
            <button className="btn" type="button" onClick={() => setAddFamily(true)}>
              Add family
            </button>
          ) : null
        }
      />
      <div className="catalog-family-list">
        {(families.data ?? []).map((family) => (
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
      {!families.loading && (families.data ?? []).length === 0 && (
        <EmptyState
          icon="link"
          title="No families yet"
          hint="Add phones that take the same spare, then save them as one family."
        />
      )}
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
