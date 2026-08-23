import { FormEvent, useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { api, money, qty } from "../lib/api";
import { PageHeader } from "../ui/PageHeader";
import type { PageResponse, ProductVariant } from "../lib/types";

export function InventoryPage() {
  const [params, setParams] = useSearchParams();
  const q = params.get("q") ?? "";
  const [query, setQuery] = useState(q);
  const [lowOnly, setLowOnly] = useState(false);
  const [page, setPage] = useState<PageResponse<ProductVariant> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [receiveFor, setReceiveFor] = useState<ProductVariant | null>(null);

  async function load(nextQuery = query) {
    const search = new URLSearchParams();
    if (nextQuery) search.set("q", nextQuery);
    if (lowOnly) search.set("lowStockOnly", "true");
    search.set("size", "25");
    try {
      setPage(await api<PageResponse<ProductVariant>>(`/api/v1/variants?${search}`));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load inventory");
    }
  }

  useEffect(() => {
    void load(q);
    // Intentionally only on first paint and explicit filters.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [q, lowOnly]);

  return (
    <div className="page">
      <PageHeader
        kicker="Counter"
        title="Inventory"
        subtitle="Stock is the ledger. The number on the row is a cache of every movement."
      />

      <form
        className="row"
        onSubmit={(event) => {
          event.preventDefault();
          setParams(query ? { q: query } : {});
          void load(query);
        }}
      >
        <input className="field" value={query} placeholder="Search part, SKU, barcode" onChange={(e) => setQuery(e.target.value)} />
        <button className="btn" type="submit">
          Search
        </button>
        <button className={lowOnly ? "btn soft" : "btn ghost"} type="button" onClick={() => setLowOnly(!lowOnly)}>
          Low stock
        </button>
      </form>

      {error && <div className="error">{error}</div>}

      <div className="card tight">
        <table className="table">
          <thead>
            <tr>
              <th>Part</th>
              <th>SKU</th>
              <th>Stock</th>
              <th>Retail</th>
              <th>Cost</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {page?.content.map((variant) => (
              <tr key={variant.id}>
                <td>
                  <div style={{ fontWeight: 650 }}>{variant.productName}</div>
                  <div className="faint">
                    {variant.variantName}
                    {variant.grade ? ` · ${variant.grade}` : ""}
                  </div>
                </td>
                <td>{variant.sku}</td>
                <td>
                  <span className={`badge ${variant.stockStatus}`}>{qty.format(variant.availableQty)}</span>
                </td>
                <td>{money.format(variant.retailPrice)}</td>
                <td>{money.format(variant.costPrice)}</td>
                <td>
                  <button className="btn ghost" type="button" onClick={() => setReceiveFor(variant)}>
                    Add stock
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {page && page.content.length === 0 && <div className="empty">No parts match.</div>}
      </div>

      {receiveFor && <ReceiveSheet variant={receiveFor} onClose={() => setReceiveFor(null)} onSaved={() => void load()} />}
    </div>
  );
}

function ReceiveSheet({
  variant,
  onClose,
  onSaved,
}: {
  variant: ProductVariant;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [quantity, setQuantity] = useState(1);
  const [unitCost, setUnitCost] = useState(variant.costPrice);
  const [reason, setReason] = useState("Counter receipt");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api("/api/v1/inventory/receive", {
        method: "POST",
        body: JSON.stringify({ variantId: variant.id, quantity, unitCost, reason }),
      });
      onSaved();
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not receive stock");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="login-wrap" style={{ position: "fixed", inset: 0, background: "rgba(20,19,15,0.35)", zIndex: 20 }}>
      <form className="login-card stack" onSubmit={submit}>
        <div className="spread">
          <h1 style={{ fontSize: 28, margin: 0 }}>Add stock</h1>
          <button className="btn ghost" type="button" onClick={onClose}>
            Close
          </button>
        </div>
        <p className="muted">
          {variant.productName} · {variant.variantName}
        </p>
        <label className="stack">
          <span className="faint">Quantity</span>
          <input className="field" type="number" min={1} value={quantity} onChange={(e) => setQuantity(Number(e.target.value))} />
        </label>
        <label className="stack">
          <span className="faint">Unit cost</span>
          <input className="field" type="number" min={0} value={unitCost} onChange={(e) => setUnitCost(Number(e.target.value))} />
        </label>
        <label className="stack">
          <span className="faint">Reason</span>
          <input className="field" value={reason} onChange={(e) => setReason(e.target.value)} />
        </label>
        {error && <div className="error">{error}</div>}
        <button className="btn" disabled={busy}>
          {busy ? "Saving…" : "Receive"}
        </button>
      </form>
    </div>
  );
}
