import { FormEvent, useEffect, useState } from "react";
import { api, apiText, money } from "../lib/api";
import type { PageResponse, PartSearchHit, GlobalSearchResponse } from "../lib/types";

interface Sale {
  id: string;
  invoiceNumber: string;
  status: string;
  customerName?: string;
  total: number;
  paid: number;
  outstanding: number;
  profit: number;
  occurredAt: string;
  items?: { variantName?: string; quantity: number; lineTotal: number }[];
}

interface Line {
  variantId: string;
  name: string;
  quantity: number;
  unitPrice: number;
}

export function SalesPage() {
  const [sales, setSales] = useState<Sale[]>([]);
  const [query, setQuery] = useState("");
  const [hits, setHits] = useState<PartSearchHit[]>([]);
  const [lines, setLines] = useState<Line[]>([]);
  const [method, setMethod] = useState("CASH");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function load() {
    const page = await api<PageResponse<Sale>>("/api/v1/sales?size=25");
    setSales(page.content);
  }

  useEffect(() => {
    load().catch((err: Error) => setError(err.message));
  }, []);

  useEffect(() => {
    if (query.trim().length < 2) {
      setHits([]);
      return;
    }
    const handle = window.setTimeout(async () => {
      const result = await api<GlobalSearchResponse>(`/api/v1/search?q=${encodeURIComponent(query)}`);
      setHits(result.parts);
    }, 140);
    return () => window.clearTimeout(handle);
  }, [query]);

  const total = lines.reduce((sum, line) => sum + line.unitPrice * line.quantity, 0);

  async function checkout(event: FormEvent) {
    event.preventDefault();
    if (lines.length === 0) return;
    setBusy(true);
    setError(null);
    try {
      const sale = await api<Sale>("/api/v1/sales", {
        method: "POST",
        body: JSON.stringify({
          pricingFlag: "NORMAL",
          idempotencyKey: crypto.randomUUID(),
          items: lines.map((line) => ({
            variantId: line.variantId,
            quantity: line.quantity,
            unitPrice: line.unitPrice,
          })),
          payments: [{ method, amount: total }],
        }),
      });
      setLines([]);
      setQuery("");
      await load();
      await openInvoice(sale.id);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not complete sale");
    } finally {
      setBusy(false);
    }
  }

  async function openInvoice(id: string) {
    const html = await apiText(`/api/v1/sales/${id}/invoice`);
    const popup = window.open("", "_blank");
    if (popup) {
      popup.document.write(html);
      popup.document.close();
    }
  }

  async function voidSale(id: string) {
    if (!window.confirm("Void this invoice and return the stock?")) return;
    try {
      await api(`/api/v1/sales/${id}/void`, { method: "POST", body: JSON.stringify({ reason: "Counter void" }) });
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not void");
    }
  }

  return (
    <div className="page">
      <div className="page-title">
        <div>
          <h1>Sales</h1>
          <p>Scan or type a SKU, add the part, take the money. Stock leaves the ledger on complete.</p>
        </div>
      </div>
      {error && <div className="error">{error}</div>}

      <form className="card stack" onSubmit={checkout}>
        <strong>New invoice</strong>
        <input className="field" value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Part, SKU, barcode…" />
        {hits.length > 0 && (
          <div className="card tight">
            {hits.map((hit) => (
              <button
                key={hit.variantId}
                className="category-row"
                type="button"
                onClick={() => {
                  setLines((current) => {
                    const existing = current.find((line) => line.variantId === hit.variantId);
                    if (existing) {
                      return current.map((line) =>
                        line.variantId === hit.variantId ? { ...line, quantity: line.quantity + 1 } : line,
                      );
                    }
                    return [...current, { variantId: hit.variantId, name: `${hit.productName} · ${hit.variantName}`, quantity: 1, unitPrice: hit.price }];
                  });
                  setQuery("");
                  setHits([]);
                }}
              >
                <div>
                  <div style={{ fontWeight: 650 }}>{hit.productName}</div>
                  <div className="faint">{hit.sku} · {hit.availableQty} in stock</div>
                </div>
                <span>{money.format(hit.price)}</span>
              </button>
            ))}
          </div>
        )}
        {lines.map((line, index) => (
          <div className="spread" key={line.variantId}>
            <div>
              {line.name}
              <div className="faint">{money.format(line.unitPrice)}</div>
            </div>
            <div className="row">
              <input
                className="field"
                style={{ width: 72 }}
                type="number"
                min={1}
                value={line.quantity}
                onChange={(e) => {
                  const quantity = Number(e.target.value);
                  setLines((current) => current.map((row, i) => (i === index ? { ...row, quantity } : row)));
                }}
              />
              <button className="btn ghost" type="button" onClick={() => setLines((current) => current.filter((_, i) => i !== index))}>
                Remove
              </button>
            </div>
          </div>
        ))}
        <div className="spread">
          <select className="select" value={method} onChange={(e) => setMethod(e.target.value)} style={{ width: 160 }}>
            <option value="CASH">Cash</option>
            <option value="UPI">UPI</option>
            <option value="CARD">Card</option>
            <option value="CREDIT">Credit</option>
          </select>
          <strong>{money.format(total)}</strong>
        </div>
        <button className="btn" disabled={busy || lines.length === 0}>
          {busy ? "Saving…" : "Complete sale"}
        </button>
      </form>

      <div className="card tight">
        <table className="table">
          <thead>
            <tr>
              <th>Invoice</th>
              <th>Customer</th>
              <th>Total</th>
              <th>Profit</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {sales.map((sale) => (
              <tr key={sale.id}>
                <td>
                  <div style={{ fontWeight: 650 }}>{sale.invoiceNumber}</div>
                  <div className="faint">{sale.status}</div>
                </td>
                <td>{sale.customerName ?? "Walk-in"}</td>
                <td>{money.format(sale.total)}</td>
                <td>{money.format(sale.profit)}</td>
                <td className="row">
                  <button className="btn ghost" type="button" onClick={() => void openInvoice(sale.id)}>
                    Invoice
                  </button>
                  {sale.status === "COMPLETED" && (
                    <button className="btn ghost" type="button" onClick={() => void voidSale(sale.id)}>
                      Void
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {sales.length === 0 && <div className="empty">No sales yet today.</div>}
      </div>
    </div>
  );
}
