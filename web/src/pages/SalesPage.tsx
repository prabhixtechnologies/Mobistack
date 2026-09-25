import { FormEvent, useCallback, useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { api, apiText, money } from "../lib/api";
import { openHtmlDocument } from "../lib/printHtml";
import { useAccess } from "../lib/access";
import { useAction } from "../lib/useAction";
import { useDebounced } from "../lib/useDebounced";
import { usePagedList } from "../lib/usePagedList";
import { DataTable, type Column } from "../ui/DataTable";
import { ConfirmDialog } from "../ui/Modal";
import { PageHeader } from "../ui/PageHeader";
import { humanLabel } from "../lib/labels";
import type { PartSearchHit, GlobalSearchResponse } from "../lib/types";

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
  const access = useAccess();
  const canSell = access.has("SALES_WRITE");
  const canVoid = access.has("SALES_VOID");
  const sales = usePagedList<Sale>("/api/v1/mobistack/sales", { size: 25 });
  const [params] = useSearchParams();
  const [query, setQuery] = useState(params.get("q") ?? "");
  const settled = useDebounced(query);
  const [hits, setHits] = useState<PartSearchHit[]>([]);
  const [lines, setLines] = useState<Line[]>([]);
  const [method, setMethod] = useState("CASH");
  const [searchError, setSearchError] = useState<string | null>(null);
  const [voidTarget, setVoidTarget] = useState<Sale | null>(null);

  useEffect(() => {
    const fromUrl = params.get("q");
    if (fromUrl) {
      setQuery(fromUrl);
    }
  }, [params]);

  useEffect(() => {
    const term = settled.trim();
    if (term.length < 2) {
      setHits([]);
      return;
    }
    let live = true;
    api<GlobalSearchResponse>(`/api/v1/mobistack/search?q=${encodeURIComponent(term)}`)
      .then((result) => {
        if (!live) {
          return;
        }
        setHits(result.parts);
        setSearchError(null);
      })
      .catch((err: unknown) => {
        if (!live) {
          return;
        }
        setHits([]);
        setSearchError(err instanceof Error ? err.message : "Search is unavailable.");
      });
    return () => {
      live = false;
    };
  }, [settled]);

  const total = lines.reduce((sum, line) => sum + line.unitPrice * line.quantity, 0);

  const openInvoice = useCallback(async (id: string) => {
    const html = await apiText(`/api/v1/mobistack/sales/invoice?id=${id}`);
    openHtmlDocument(html);
  }, []);

  const checkout = useAction(
    async () => {
      const sale = await api<Sale>("/api/v1/mobistack/sales", {
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
      sales.reload();
      await openInvoice(sale.id);
    },
    { fallbackError: "Could not complete sale." },
  );

  const voidSale = useAction(
    async (id: string) => {
      await api(`/api/v1/mobistack/sales/void?id=${id}`, { method: "POST", body: JSON.stringify({ reason: "Counter void" }) });
      sales.reload();
    },
    { fallbackError: "Could not void that invoice.", onDone: () => setVoidTarget(null) },
  );

  function submit(event: FormEvent) {
    event.preventDefault();
    if (lines.length > 0) {
      void checkout.run();
    }
  }

  const columns: Column<Sale>[] = [
    {
      key: "invoice",
      header: "Invoice",
      render: (sale) => (
        <div className="cell-identity">
          <strong>{sale.invoiceNumber}</strong>
          <span className="faint">{humanLabel(sale.status)}</span>
        </div>
      ),
    },
    { key: "customer", header: "Customer", render: (sale) => sale.customerName ?? "Walk-in" },
    { key: "total", header: "Total", align: "right", render: (sale) => money.format(sale.total) },
    {
      key: "profit",
      header: "Profit",
      align: "right",
      need: "REPORT_READ",
      render: (sale) => money.format(sale.profit),
    },
    {
      key: "actions",
      header: "",
      align: "right",
      render: (sale) => (
        <div className="row" style={{ justifyContent: "flex-end" }}>
          <button className="btn ghost" type="button" onClick={() => void openInvoice(sale.id)}>
            Invoice
          </button>
          {canVoid && sale.status === "COMPLETED" && (
            <button
              className="btn ghost"
              type="button"
              disabled={voidSale.busy}
              onClick={() => setVoidTarget(sale)}
            >
              Void
            </button>
          )}
        </div>
      ),
    },
  ];

  return (
    <div className="page">
      <PageHeader
        kicker="Shop"
        title="Sales"
        subtitle="Find a part, add it to the ticket, take payment. Stock leaves the ledger when the sale completes."
      />
      {checkout.error && <div className="error">{checkout.error}</div>}
      {voidSale.error && <div className="error">{voidSale.error}</div>}
      {searchError && <div className="error">{searchError}</div>}

      <div className="pos">
      {canSell ? (
        <form className="pos__ticket" onSubmit={submit}>
          <h2>This ticket</h2>
          <input
            className="field"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Part, SKU, barcode…"
            aria-label="Find a part to sell"
            autoComplete="off"
          />
          {hits.length > 0 && (
            <div className="pos__hits">
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
                      return [
                        ...current,
                        {
                          variantId: hit.variantId,
                          name: `${hit.productName} · ${hit.variantName}`,
                          quantity: 1,
                          unitPrice: hit.price,
                        },
                      ];
                    });
                    setQuery("");
                    setHits([]);
                  }}
                >
                  <div>
                    <div style={{ fontWeight: 650 }}>{hit.productName}</div>
                    <div className="faint">
                      {hit.sku} · {hit.availableQty} in stock
                    </div>
                  </div>
                  <span>{money.format(hit.price)}</span>
                </button>
              ))}
            </div>
          )}
          {lines.length === 0 && hits.length === 0 && (
            <p className="faint">Search a phone part or scan a barcode to start.</p>
          )}
          {lines.map((line, index) => (
            <div className="pos__line" key={line.variantId}>
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
                <button
                  className="btn ghost"
                  type="button"
                  onClick={() => setLines((current) => current.filter((_, i) => i !== index))}
                >
                  Remove
                </button>
              </div>
            </div>
          ))}
          <div className="pos__total">
            <select className="select" value={method} onChange={(e) => setMethod(e.target.value)} style={{ width: 140, maxWidth: "100%" }} aria-label="Payment method">
              <option value="CASH">Cash</option>
              <option value="UPI">UPI</option>
              <option value="CARD">Card</option>
              <option value="CREDIT">Credit</option>
            </select>
            <strong>{money.format(total)}</strong>
          </div>
          <button className="btn" disabled={checkout.busy || lines.length === 0}>
            {checkout.busy ? "Saving…" : "Complete sale"}
          </button>
        </form>
      ) : (
        <div className="pos__ticket faint">
          Your role can read invoices but not raise them. Ask an owner for the “Take sales” permission.
        </div>
      )}

      <div>
        <DataTable
          columns={columns}
          rows={sales.loading && sales.rows.length === 0 ? undefined : sales.rows}
          rowKey={(sale) => sale.id}
          loading={sales.loading}
          error={sales.error}
          onRetry={sales.reload}
          skeletonRows={8}
          empty={{
            icon: "cart",
            title: "No sales yet",
            hint: "Scan a part above and take the payment. Completed invoices appear here.",
          }}
          paging={{
            total: sales.total,
            hasMore: sales.hasMore,
            loadingMore: sales.loadingMore,
            onLoadMore: sales.loadMore,
            noun: "invoices",
          }}
        />
      </div>
      </div>

      <ConfirmDialog
        open={Boolean(voidTarget)}
        title="Void this invoice?"
        description={
          voidTarget
            ? `${voidTarget.invoiceNumber} will be voided and the stock returned to the ledger. This cannot be undone.`
            : undefined
        }
        confirmLabel="Void invoice"
        destructive
        busy={voidSale.busy}
        error={voidSale.error}
        onConfirm={() => {
          if (voidTarget) {
            void voidSale.run(voidTarget.id);
          }
        }}
        onClose={() => {
          if (!voidSale.busy) {
            setVoidTarget(null);
            voidSale.clearError();
          }
        }}
      />
    </div>
  );
}
