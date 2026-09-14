import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../lib/api";
import { useAccess } from "../lib/access";
import { useDebounced } from "../lib/useDebounced";
import { usePagedList } from "../lib/usePagedList";
import { EmptyState } from "../ui/EmptyState";
import { TextField } from "../ui/Field";
import { PageHeader } from "../ui/PageHeader";
import type { CommonsComponent, ProductVariant } from "../lib/types";

export function CatalogLinksPage() {
  const access = useAccess();
  const canWrite = access.has("INVENTORY_WRITE");
  const [query, setQuery] = useState("");
  const [partQuery, setPartQuery] = useState("");
  const [linking, setLinking] = useState<ProductVariant | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const settled = useDebounced(query);
  const settledPart = useDebounced(partQuery);

  const variantPath = useMemo(() => {
    const params = new URLSearchParams();
    if (settled.trim()) {
      params.set("q", settled.trim());
    }
    const suffix = params.toString();
    return suffix ? `/api/v1/variants?${suffix}` : "/api/v1/variants";
  }, [settled]);

  const variants = usePagedList<ProductVariant>(variantPath, { size: 25 });
  const components = usePagedList<CommonsComponent>(
    linking && settledPart.trim().length >= 2
      ? `/api/v1/commons/components?q=${encodeURIComponent(settledPart.trim())}`
      : linking
        ? "/api/v1/commons/components"
        : null,
    { size: 20 },
  );

  async function link(variantId: string, componentId: string) {
    setBusy(true);
    setError(null);
    try {
      await api(`/api/v1/inventory/catalog-links/${variantId}`, {
        method: "PUT",
        body: JSON.stringify({ componentId }),
      });
      setLinking(null);
      setPartQuery("");
      variants.reload();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not link that part.");
    } finally {
      setBusy(false);
    }
  }

  async function unlink(variantId: string) {
    setBusy(true);
    setError(null);
    try {
      await api(`/api/v1/inventory/catalog-links/${variantId}`, { method: "DELETE" });
      variants.reload();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not unlink that part.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="page">
      <PageHeader
        kicker={<Link to="/inventory">Inventory</Link>}
        title="Link to catalog part"
        subtitle="Point a shelf SKU at the shared Fitment Catalog. Stock then shows on the phone it fits."
      />
      {error && <div className="error">{error}</div>}
      <TextField
        label="Your variants"
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        placeholder="SKU, part name…"
        autoComplete="off"
      />
      <section className="card tight">
        {variants.rows.map((variant) => (
          <div className="category-row" key={variant.id}>
            <div>
              <div style={{ fontWeight: 650 }}>
                {variant.productName} · {variant.variantName}
              </div>
              <div className="faint">
                {variant.sku}
                {variant.catalogComponentId ? " · linked" : " · not linked"}
              </div>
            </div>
            {canWrite && (
              <div className="row">
                <button className="btn ghost" type="button" disabled={busy} onClick={() => setLinking(variant)}>
                  {variant.catalogComponentId ? "Relink" : "Link"}
                </button>
                {variant.catalogComponentId && (
                  <button className="btn ghost" type="button" disabled={busy} onClick={() => void unlink(variant.id)}>
                    Unlink
                  </button>
                )}
              </div>
            )}
          </div>
        ))}
        {!variants.loading && variants.rows.length === 0 && (
          <EmptyState compact icon="box" title="No variants" hint="Receive stock, then link it to a catalog part." />
        )}
      </section>

      {linking && (
        <section className="card stack">
          <div className="spread">
            <strong>
              Catalog part for {linking.productName} · {linking.variantName}
            </strong>
            <button className="btn ghost" type="button" onClick={() => setLinking(null)}>
              Close
            </button>
          </div>
          <TextField
            label="Search shared parts"
            value={partQuery}
            onChange={(event) => setPartQuery(event.target.value)}
            placeholder="Realme 6 Display Folder"
            autoFocus
          />
          {components.rows.map((component) => (
            <div className="category-row" key={component.id}>
              <div>
                <div style={{ fontWeight: 650 }}>{component.name}</div>
                <div className="faint">{component.categoryCode.replaceAll("_", " ")}</div>
              </div>
              <button className="btn" type="button" disabled={busy} onClick={() => void link(linking.id, component.id)}>
                Use this
              </button>
            </div>
          ))}
          {!components.loading && components.rows.length === 0 && (
            <EmptyState compact icon="search" title="No catalog parts match" hint="Type at least two characters, or contribute the part first." />
          )}
        </section>
      )}
    </div>
  );
}
