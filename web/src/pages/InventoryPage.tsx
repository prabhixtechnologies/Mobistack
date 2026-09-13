import { FormEvent, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { apiOnce, money, qty } from "../lib/api";
import { useAccess } from "../lib/access";
import { useAction } from "../lib/useAction";
import { useDebounced } from "../lib/useDebounced";
import { usePagedList } from "../lib/usePagedList";
import { DataTable, type Column } from "../ui/DataTable";
import { TextField } from "../ui/Field";
import { Modal } from "../ui/Modal";
import { PageHeader } from "../ui/PageHeader";
import type { ProductVariant } from "../lib/types";

export function InventoryPage() {
  const access = useAccess();
  const canReceive = access.has("INVENTORY_WRITE");
  const [params, setParams] = useSearchParams();
  const [query, setQuery] = useState(params.get("q") ?? "");
  const [lowOnly, setLowOnly] = useState(false);
  const [receiveFor, setReceiveFor] = useState<ProductVariant | null>(null);
  const settled = useDebounced(query);

  // Keep the address bar in step so a search can be shared or reloaded.
  useEffect(() => {
    setParams(settled.trim() ? { q: settled.trim() } : {}, { replace: true });
  }, [settled, setParams]);

  const path = useMemo(() => {
    const search = new URLSearchParams();
    if (settled.trim()) {
      search.set("q", settled.trim());
    }
    if (lowOnly) {
      search.set("lowStockOnly", "true");
    }
    const suffix = search.toString();
    return suffix ? `/api/v1/variants?${suffix}` : "/api/v1/variants";
  }, [settled, lowOnly]);

  const parts = usePagedList<ProductVariant>(path, { size: 25 });

  const columns: Column<ProductVariant>[] = [
    {
      key: "part",
      header: "Part",
      render: (variant) => (
        <div className="cell-identity">
          <strong>{variant.productName}</strong>
          <span className="faint">
            {variant.variantName}
            {variant.grade ? ` · ${variant.grade}` : ""}
          </span>
        </div>
      ),
    },
    { key: "sku", header: "SKU", render: (variant) => variant.sku },
    {
      key: "stock",
      header: "Stock",
      align: "right",
      render: (variant) => (
        <span className={`badge ${variant.stockStatus}`}>{qty.format(variant.availableQty)}</span>
      ),
    },
    { key: "retail", header: "Retail", align: "right", render: (variant) => money.format(variant.retailPrice) },
    {
      key: "cost",
      header: "Cost",
      align: "right",
      need: "REPORT_READ",
      render: (variant) => money.format(variant.costPrice),
    },
    ...(canReceive
      ? [
          {
            key: "actions",
            header: "",
            align: "right" as const,
            render: (variant: ProductVariant) => (
              <button className="btn ghost" type="button" onClick={() => setReceiveFor(variant)}>
                Add stock
              </button>
            ),
          },
        ]
      : []),
  ];

  return (
    <div className="page">
      <PageHeader
        kicker="Counter"
        title="Inventory"
        subtitle="Stock is the ledger. The number on the row is a cache of every movement."
      />

      <div className="row">
        <TextField
          label="Search"
          value={query}
          placeholder="Part, SKU, barcode"
          onChange={(e) => setQuery(e.target.value)}
          autoComplete="off"
        />
        <button className={lowOnly ? "btn soft" : "btn ghost"} type="button" onClick={() => setLowOnly(!lowOnly)}>
          Low stock
        </button>
      </div>

      <div className="card tight">
        <DataTable
          columns={columns}
          rows={parts.loading && parts.rows.length === 0 ? undefined : parts.rows}
          rowKey={(variant) => variant.id}
          loading={parts.loading}
          error={parts.error}
          onRetry={parts.reload}
          skeletonRows={8}
          empty={{
            icon: "box",
            title: settled.trim() || lowOnly ? "Nothing matches those filters" : "No parts yet",
            hint:
              settled.trim() || lowOnly
                ? "Clear the search or the low-stock filter to see the whole shelf."
                : "Receive a purchase, or import your existing list, and parts appear here.",
          }}
          paging={{
            total: parts.total,
            hasMore: parts.hasMore,
            loadingMore: parts.loadingMore,
            onLoadMore: parts.loadMore,
            noun: "parts",
          }}
        />
      </div>

      {receiveFor && (
        <ReceiveSheet variant={receiveFor} onClose={() => setReceiveFor(null)} onSaved={parts.reload} />
      )}
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

  const receive = useAction(
    async () => {
      await apiOnce("/api/v1/inventory/receive", { variantId: variant.id, quantity, unitCost, reason });
      onSaved();
      onClose();
    },
    { fallbackError: "Could not receive that stock." },
  );

  function submit(event: FormEvent) {
    event.preventDefault();
    void receive.run();
  }

  return (
    <Modal
      open
      title="Add stock"
      description={`${variant.productName} · ${variant.variantName}`}
      onClose={() => {
        if (!receive.busy) {
          onClose();
        }
      }}
      footer={
        <>
          <button className="btn ghost" type="button" onClick={onClose} disabled={receive.busy}>
            Cancel
          </button>
          <button className="btn" type="submit" form="receive-stock-form" disabled={receive.busy}>
            {receive.busy ? "Saving…" : "Receive"}
          </button>
        </>
      }
    >
      <form id="receive-stock-form" className="stack" onSubmit={submit}>
        <TextField
          label="Quantity"
          type="number"
          min={1}
          value={quantity}
          onChange={(e) => setQuantity(Number(e.target.value))}
        />
        <TextField
          label="Unit cost"
          type="number"
          min={0}
          value={unitCost}
          onChange={(e) => setUnitCost(Number(e.target.value))}
        />
        <TextField label="Reason" value={reason} onChange={(e) => setReason(e.target.value)} />
        {receive.error && <div className="error">{receive.error}</div>}
      </form>
    </Modal>
  );
}
