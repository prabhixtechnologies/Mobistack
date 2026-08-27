import { FormEvent, useEffect, useState } from "react";
import { api, money } from "../lib/api";
import { useAccess } from "../lib/access";
import { useAction } from "../lib/useAction";
import { usePagedList } from "../lib/usePagedList";
import { DataTable, type Column } from "../ui/DataTable";
import { PageHeader } from "../ui/PageHeader";
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
  const access = useAccess();
  const canWrite = access.has("PURCHASE_WRITE");
  const purchases = usePagedList<Purchase>("/api/v1/purchases", { size: 25 });
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [variants, setVariants] = useState<ProductVariant[]>([]);
  const [supplierId, setSupplierId] = useState("");
  const [variantId, setVariantId] = useState("");
  const [quantity, setQuantity] = useState(1);
  const [unitCost, setUnitCost] = useState(0);
  const [formError, setFormError] = useState<string | null>(null);

  useEffect(() => {
    if (!canWrite) {
      return;
    }
    Promise.all([
      api<PageResponse<Supplier>>("/api/v1/suppliers?size=100"),
      api<PageResponse<ProductVariant>>("/api/v1/variants?size=100"),
    ])
      .then(([supplierPage, variantPage]) => {
        setSuppliers(supplierPage.content);
        setVariants(variantPage.content);
        setSupplierId((current) => current || (supplierPage.content[0]?.id ?? ""));
        setVariantId((current) => current || (variantPage.content[0]?.id ?? ""));
        setFormError(null);
      })
      .catch((cause: unknown) => {
        setFormError(cause instanceof Error ? cause.message : "Suppliers and parts could not be loaded.");
      });
  }, [canWrite]);

  const receive = useAction(
    async () => {
      await api("/api/v1/purchases", {
        method: "POST",
        body: JSON.stringify({
          supplierId,
          idempotencyKey: crypto.randomUUID(),
          items: [{ variantId, quantity, unitCost }],
          payments: [{ method: "CASH", amount: unitCost * quantity }],
        }),
      });
      purchases.reload();
    },
    { fallbackError: "Could not receive that purchase." },
  );

  function submit(event: FormEvent) {
    event.preventDefault();
    void receive.run();
  }

  const columns: Column<Purchase>[] = [
    {
      key: "supplier",
      header: "Supplier",
      render: (row) => (
        <div className="cell-identity">
          <strong>{row.supplierName}</strong>
          <span className="faint">{new Date(row.receivedAt).toLocaleDateString("en-IN")}</span>
        </div>
      ),
    },
    { key: "status", header: "Status", render: (row) => row.status },
    { key: "total", header: "Total", align: "right", render: (row) => money.format(row.total) },
    { key: "outstanding", header: "Outstanding", align: "right", render: (row) => money.format(row.outstanding) },
  ];

  const ready = Boolean(supplierId && variantId);

  return (
    <div className="page">
      <PageHeader
        kicker="Counter"
        title="Purchases"
        subtitle="A purchase is stock in plus a supplier ledger row. Not a spreadsheet paste."
      />
      {receive.error && <div className="error">{receive.error}</div>}
      {formError && <div className="error">{formError}</div>}

      {canWrite && (
        <form className="card stack" onSubmit={submit}>
          <strong>Receive a carton</strong>
          {suppliers.length === 0 || variants.length === 0 ? (
            <p className="muted">
              Add at least one supplier and one part before receiving stock, so the carton has somewhere to land.
            </p>
          ) : (
            <>
              <select className="select" value={supplierId} onChange={(e) => setSupplierId(e.target.value)}>
                {suppliers.map((supplier) => (
                  <option key={supplier.id} value={supplier.id}>
                    {supplier.name}
                  </option>
                ))}
              </select>
              <select className="select" value={variantId} onChange={(e) => setVariantId(e.target.value)}>
                {variants.map((variant) => (
                  <option key={variant.id} value={variant.id}>
                    {variant.productName} · {variant.variantName}
                  </option>
                ))}
              </select>
              <div className="grid-2">
                <input
                  className="field"
                  type="number"
                  min={1}
                  value={quantity}
                  onChange={(e) => setQuantity(Number(e.target.value))}
                />
                <input
                  className="field"
                  type="number"
                  min={0}
                  value={unitCost}
                  onChange={(e) => setUnitCost(Number(e.target.value))}
                />
              </div>
              <button className="btn" disabled={!ready || receive.busy}>
                {receive.busy ? "Receiving…" : "Receive and stock"}
              </button>
            </>
          )}
        </form>
      )}

      <div className="card tight">
        <DataTable
          columns={columns}
          rows={purchases.loading && purchases.rows.length === 0 ? undefined : purchases.rows}
          rowKey={(row) => row.id}
          loading={purchases.loading}
          error={purchases.error}
          onRetry={purchases.reload}
          skeletonRows={8}
          empty={{
            icon: "truck",
            title: "No purchases yet",
            hint: "Receive a carton above and the supplier balance updates with it.",
          }}
          paging={{
            total: purchases.total,
            hasMore: purchases.hasMore,
            loadingMore: purchases.loadingMore,
            onLoadMore: purchases.loadMore,
            noun: "purchases",
          }}
        />
      </div>
    </div>
  );
}
