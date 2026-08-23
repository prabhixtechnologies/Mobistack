import { FormEvent, useEffect, useState } from "react";
import { api, money } from "../lib/api";
import type { PageResponse, ProductVariant } from "../lib/types";

interface Supplier {
  id: string;
  name: string;
}

interface Purchase {
  id: string;
  supplierName: string;
  status: string;
  total: number;
  paid: number;
  outstanding: number;
  receivedAt: string;
}

export function PurchasesPage() {
  const [rows, setRows] = useState<Purchase[]>([]);
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [variants, setVariants] = useState<ProductVariant[]>([]);
  const [supplierId, setSupplierId] = useState("");
  const [variantId, setVariantId] = useState("");
  const [quantity, setQuantity] = useState(1);
  const [unitCost, setUnitCost] = useState(0);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    const [purchases, supplierPage, variantPage] = await Promise.all([
      api<PageResponse<Purchase>>("/api/v1/purchases?size=40"),
      api<PageResponse<Supplier>>("/api/v1/suppliers?size=40"),
      api<PageResponse<ProductVariant>>("/api/v1/variants?size=40"),
    ]);
    setRows(purchases.content);
    setSuppliers(supplierPage.content);
    setVariants(variantPage.content);
    if (!supplierId && supplierPage.content[0]) setSupplierId(supplierPage.content[0].id);
    if (!variantId && variantPage.content[0]) setVariantId(variantPage.content[0].id);
  }

  useEffect(() => {
    load().catch((err: Error) => setError(err.message));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function receive(event: FormEvent) {
    event.preventDefault();
    try {
      await api("/api/v1/purchases", {
        method: "POST",
        body: JSON.stringify({
          supplierId,
          idempotencyKey: crypto.randomUUID(),
          items: [{ variantId, quantity, unitCost }],
          payments: [{ method: "CASH", amount: unitCost * quantity }],
        }),
      });
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not receive purchase");
    }
  }

  return (
    <div className="page">
      <div className="page-title">
        <div>
          <h1>Purchases</h1>
          <p>A purchase is stock in plus a supplier ledger row. Not a spreadsheet paste.</p>
        </div>
      </div>
      {error && <div className="error">{error}</div>}
      <form className="card stack" onSubmit={receive}>
        <strong>Receive a carton</strong>
        <select className="select" value={supplierId} onChange={(e) => setSupplierId(e.target.value)}>
          {suppliers.map((supplier) => (
            <option key={supplier.id} value={supplier.id}>{supplier.name}</option>
          ))}
        </select>
        <select className="select" value={variantId} onChange={(e) => setVariantId(e.target.value)}>
          {variants.map((variant) => (
            <option key={variant.id} value={variant.id}>{variant.productName} · {variant.variantName}</option>
          ))}
        </select>
        <div className="grid-2">
          <input className="field" type="number" min={1} value={quantity} onChange={(e) => setQuantity(Number(e.target.value))} />
          <input className="field" type="number" min={0} value={unitCost} onChange={(e) => setUnitCost(Number(e.target.value))} />
        </div>
        <button className="btn" disabled={!supplierId || !variantId}>Receive and stock</button>
      </form>
      <div className="card tight">
        <table className="table">
          <thead>
            <tr>
              <th>Supplier</th>
              <th>Status</th>
              <th>Total</th>
              <th>Outstanding</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.id}>
                <td>{row.supplierName}</td>
                <td>{row.status}</td>
                <td>{money.format(row.total)}</td>
                <td>{money.format(row.outstanding)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
