import { FormEvent, useCallback, useEffect, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { api, apiText, money } from "../lib/api";
import { openHtmlDocument } from "../lib/printHtml";
import { useAccess } from "../lib/access";
import { useAction } from "../lib/useAction";
import { useDebounced } from "../lib/useDebounced";
import { usePagedList } from "../lib/usePagedList";
import { useScanner } from "../lib/useScanner";
import { DataTable, type Column } from "../ui/DataTable";
import type { RowAction } from "../ui/RowActions";
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
  const searchRef = useRef<HTMLInputElement>(null);
  // Announced rather than shown as an error, because a scan that misses is a normal event at a
  // counter - an unlabelled part, a damaged code - and the till should say so without stopping.
  const [scanNote, setScanNote] = useState<string | null>(null);

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

  /** One more of this part on the ticket, or a new line if it is not on it yet. */
  const addHit = useCallback((hit: PartSearchHit) => {
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
  }, []);

  /**
   * A scanned code goes straight onto the ticket.
   *
   * <p>Only on an exact barcode or SKU match. The search endpoint is fuzzy, and a scan that
   * quietly adds the nearest-looking part is worse than one that adds nothing - nobody
   * re-reads a line they did not type.
   */
  const addByCode = useCallback(
    async (code: string) => {
      setScanNote(null);
      try {
        const result = await api<GlobalSearchResponse>(
          `/api/v1/mobistack/search?q=${encodeURIComponent(code)}`,
        );
        const wanted = code.trim().toLowerCase();
        const exact = result.parts.find(
          (part) =>
            part.barcode?.trim().toLowerCase() === wanted ||
            part.sku.trim().toLowerCase() === wanted,
        );
        if (!exact) {
          setQuery(code);
          setHits(result.parts);
          setScanNote(
            result.parts.length > 0
              ? `Nothing matches ${code} exactly. Pick from the list below.`
              : `Nothing matches ${code}.`,
          );
          return;
        }
        if (exact.availableQty <= 0) {
          // Added anyway: a counter sells the part in its hand, and refusing here means the
          // sale goes through untracked. Saying so is the useful part.
          setScanNote(`${exact.productName} shows no stock. Added — count it when you can.`);
        } else {
          setScanNote(`Added ${exact.productName}.`);
        }
        addHit(exact);
      } catch (err) {
        setScanNote(err instanceof Error ? err.message : "Could not look that code up.");
      }
    },
    [addHit],
  );

  // A USB scanner is a keyboard, and its Enter used to submit this form - which, with items on
  // the ticket, completed the sale. It is recognised by typing speed now and never reaches the
  // form. See lib/useScanner.ts.
  useScanner((code) => void addByCode(code), { enabled: canSell });

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
    // Only the Complete sale button may take money. Enter from anywhere inside this form used
    // to land here, so a hand-typed SKU followed by Enter charged the customer for whatever
    // was already on the ticket.
    const submitter = (event.nativeEvent as SubmitEvent).submitter;
    if (submitter?.getAttribute("data-checkout") !== "true") return;
    if (lines.length > 0) {
      void checkout.run();
    }
  }

  /*
    The counter keyboard.

    F2 to the search box and F9 to take payment are what till software has used for thirty
    years, and the people on this counter have used till software. Escape clears a
    half-finished ticket, which is the other thing that happens constantly: wrong customer,
    start again.

    Function keys rather than letters because the search box holds focus almost all the time
    and a letter shortcut would either be swallowed or eat a keystroke.
  */
  useEffect(() => {
    if (!canSell) return;
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === "F2") {
        event.preventDefault();
        searchRef.current?.focus();
        searchRef.current?.select();
        return;
      }
      if (event.key === "F9") {
        event.preventDefault();
        if (lines.length > 0 && !checkout.busy) void checkout.run();
        return;
      }
      if (event.key === "Escape" && document.activeElement === searchRef.current) {
        // Clears the search first and the ticket only on a second press, so one stray Escape
        // cannot throw away a ticket someone spent a minute building.
        if (query) {
          setQuery("");
          setHits([]);
        } else if (lines.length > 0) {
          setLines([]);
          setScanNote("Ticket cleared.");
        }
      }
    }
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [canSell, lines.length, checkout, query]);

  // The same verbs the actions column renders as buttons, reachable by right-click on the
  // counter PC, by long-press on the tablet, and by Shift+F10 without a mouse. Defined here
  // so the two routes cannot drift apart.
  const saleActions = (sale: Sale): RowAction[] => [
    {
      id: "invoice",
      label: "Open invoice",
      onSelect: () => openInvoice(sale.id),
    },
    ...(canVoid && sale.status === "COMPLETED"
      ? [
          {
            id: "void",
            label: "Void invoice",
            danger: true,
            disabled: voidSale.busy,
            // Routed through the page's existing confirm dialog rather than the menu's own,
            // because that one is already wired to the mutation's busy and error state.
            onSelect: () => setVoidTarget(sale),
          } satisfies RowAction,
        ]
      : []),
  ];

  const columns: Column<Sale>[] = [
    {
      key: "invoice",
      header: "Invoice",
      mobileLabel: "Invoice",
      render: (sale) => (
        <div className="cell-identity">
          <strong>{sale.invoiceNumber}</strong>
          <span className="faint">{humanLabel(sale.status)}</span>
        </div>
      ),
    },
    { key: "customer", header: "Customer", mobileLabel: "Customer", render: (sale) => sale.customerName ?? "Walk-in" },
    { key: "total", header: "Total", mobileLabel: "Total", align: "right", render: (sale) => money.format(sale.total) },
    {
      key: "profit",
      header: "Profit",
      mobileLabel: "Profit",
      align: "right",
      need: "REPORT_READ",
      render: (sale) => money.format(sale.profit),
    },
    {
      key: "actions",
      header: "",
      mobileLabel: "",
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
    <div className="page page--wide">
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
        <form className="pos__ticket pos__workspace" onSubmit={submit}>
          <h2>This ticket</h2>
          <input
            ref={searchRef}
            className="field"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={(e) => {
              // Enter takes the only hit. With several it does nothing, because guessing which
              // part someone meant is how the wrong one gets sold.
              if (e.key === "Enter") {
                e.preventDefault();
                if (hits.length === 1) addHit(hits[0]);
              }
            }}
            placeholder="Part, SKU, barcode…"
            aria-label="Find a part to sell"
            aria-describedby="pos-keys"
            autoComplete="off"
          />
          {/* Announced, not just shown: at a counter nobody is watching this corner of the
              screen while holding a part and a scanner. */}
          <p className="scan-feedback" role="status" aria-live="polite">
            {scanNote}
          </p>
          <p className="faint" id="pos-keys">
            Scan a barcode to add it. <kbd>F2</kbd> search · <kbd>F9</kbd> take payment ·{" "}
            <kbd>Esc</kbd> clear
          </p>
          {hits.length > 0 && (
            <div className="pos__hits">
              {hits.map((hit) => (
                <button
                  key={hit.variantId}
                  className="category-row"
                  type="button"
                  onClick={() => addHit(hit)}
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
                  aria-label={`Quantity of ${line.name}`}
                  onChange={(e) => {
                    // Clearing the box gives an empty string, and Number("") is 0 - which sold
                    // the part for nothing and left a zero-quantity line on the invoice.
                    const parsed = Number.parseInt(e.target.value, 10);
                    const quantity = Number.isFinite(parsed) && parsed > 0 ? parsed : 1;
                    setLines((current) =>
                      current.map((row, i) => (i === index ? { ...row, quantity } : row)),
                    );
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
          {/* The marker the submit handler checks. Nothing else in this form may take money. */}
          <button className="btn" data-checkout="true" disabled={checkout.busy || lines.length === 0}>
            {checkout.busy ? "Saving…" : "Complete sale"}
          </button>
        </form>
      ) : (
        <div className="pos__ticket faint">
          Your role can read invoices but not raise them. Ask an owner for the “Take sales” permission.
        </div>
      )}

      <div className="pos__history">
        <DataTable
          columns={columns}
          rows={sales.loading && sales.rows.length === 0 ? undefined : sales.rows}
          rowKey={(sale) => sale.id}
          loading={sales.loading}
          error={sales.error}
          onRetry={sales.reload}
          rowActions={saleActions}
          rowLabel={(sale) => `Invoice ${sale.invoiceNumber}`}
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
