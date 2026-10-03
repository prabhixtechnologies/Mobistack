import { FormEvent, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api, money } from "../lib/api";
import { humanLabel } from "../lib/labels";
import { useAccess } from "../lib/access";
import { useAction } from "../lib/useAction";
import { usePagedList } from "../lib/usePagedList";
import { DataTable, type Column } from "../ui/DataTable";
import { SelectField, TextField } from "../ui/Field";
import { PageHeader } from "../ui/PageHeader";
import { Panel } from "../ui/Panel";
import type { RowAction } from "../ui/RowActions";
import { useRowVerbs, verbs } from "../ui/rowVerbs";
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
  const rowVerbs = useRowVerbs();
  const canWrite = access.has("PURCHASE_WRITE");
  const purchases = usePagedList<Purchase>("/api/v1/mobistack/purchases", { size: 25 });
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [variants, setVariants] = useState<ProductVariant[]>([]);
  const [supplierId, setSupplierId] = useState("");
  const [variantId, setVariantId] = useState("");
  const [quantity, setQuantity] = useState(1);
  const [unitCost, setUnitCost] = useState(0);
  const [formError, setFormError] = useState<string | null>(null);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    if (!canWrite) {
      return;
    }
    Promise.all([
      api<PageResponse<Supplier>>("/api/v1/mobistack/suppliers?size=100"),
      api<PageResponse<ProductVariant>>("/api/v1/mobistack/variants?size=100"),
    ])
      .then(([supplierPage, variantPage]) => {
        setSuppliers(supplierPage.content);
        setVariants(variantPage.content);
        setSupplierId((current) => current || (supplierPage.content[0]?.id ?? ""));
        setVariantId((current) => current || (variantPage.content[0]?.id ?? ""));
        setFormError(null);
        setLoaded(true);
      })
      .catch((cause: unknown) => {
        setFormError(cause instanceof Error ? cause.message : "Suppliers and parts could not be loaded.");
      });
  }, [canWrite]);

  const receive = useAction(
    async () => {
      await api("/api/v1/mobistack/purchases", {
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

  // A purchase row is a receipt: the two things anyone wants off it are who it was from and
  // the reference to quote when the amount is queried.
  const purchaseActions = (purchase: Purchase): RowAction[] =>
    verbs(
      rowVerbs.copy("supplier", "Copy supplier name", purchase.supplierName),
      rowVerbs.copy("ref", "Copy reference", purchase.id),
    );

  const columns: Column<Purchase>[] = [
    {
      key: "supplier",
      header: "Supplier",
      render: (row) => (
        <div className="cell-identity">
          <strong>{row.supplierName}</strong>
          <span className="faint">{new Date(row.receivedAt).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" })}</span>
        </div>
      ),
    },
    {
      key: "status",
      header: "Status",
      render: (row) => <span className={`badge ${row.outstanding > 0 ? "tint-amber" : "tint-green"}`}>{humanLabel(row.status)}</span>,
    },
    { key: "total", header: "Total", align: "right", render: (row) => money.format(row.total) },
    { key: "outstanding", header: "Outstanding", align: "right", render: (row) => money.format(row.outstanding) },
  ];

  const ready = Boolean(supplierId && variantId);
  const setupNeeded = loaded && (suppliers.length === 0 || variants.length === 0);

  return (
    <div className="page">
      <PageHeader
        icon="truck"
        kicker="Inventory"
        title="Purchases"
        subtitle="A purchase is stock in plus a supplier ledger row. Not a spreadsheet paste."
      />
      {receive.error && <div className="error">{receive.error}</div>}
      {formError && <div className="error">{formError}</div>}

      {canWrite && (
        <Panel
          icon="plus"
          title="Receive a carton"
          hint={setupNeeded ? "Two things first, so the carton has somewhere to land." : "Stock goes on the shelf and the supplier's ledger updates in one step."}
        >
          {!loaded ? (
            formError ? (
              <p className="muted">Reload the page to try again.</p>
            ) : (
              <div className="skeleton" style={{ height: 44 }} />
            )
          ) : setupNeeded ? (
            <ol className="setup-steps">
              <li className={suppliers.length > 0 ? "is-done" : undefined}>
                <span className="setup-steps__mark" aria-hidden>
                  {suppliers.length > 0 ? "✓" : "1"}
                </span>
                <div>
                  <strong>Add a supplier</strong>
                  <p>{suppliers.length > 0 ? `${suppliers.length} on file.` : "The wholesaler you buy from."}</p>
                </div>
                {suppliers.length === 0 && (
                  <Link className="btn ghost sm" to="/suppliers">
                    Add supplier
                  </Link>
                )}
              </li>
              <li className={variants.length > 0 ? "is-done" : undefined}>
                <span className="setup-steps__mark" aria-hidden>
                  {variants.length > 0 ? "✓" : "2"}
                </span>
                <div>
                  <strong>Add a part</strong>
                  <p>{variants.length > 0 ? `${variants.length} on the shelf.` : "What the carton holds, with its selling price."}</p>
                </div>
                {variants.length === 0 && (
                  <Link className="btn ghost sm" to="/inventory">
                    Add part
                  </Link>
                )}
              </li>
            </ol>
          ) : (
            <form className="composer composer--purchase" onSubmit={submit}>
              <SelectField label="Supplier" value={supplierId} onChange={(e) => setSupplierId(e.target.value)}>
                {suppliers.map((supplier) => (
                  <option key={supplier.id} value={supplier.id}>
                    {supplier.name}
                  </option>
                ))}
              </SelectField>
              <SelectField label="Part" value={variantId} onChange={(e) => setVariantId(e.target.value)}>
                {variants.map((variant) => (
                  <option key={variant.id} value={variant.id}>
                    {variant.productName} · {variant.variantName}
                  </option>
                ))}
              </SelectField>
              <TextField
                label="Quantity"
                type="number"
                min={1}
                inputMode="numeric"
                value={quantity}
                onChange={(e) => setQuantity(Math.max(1, Number.parseInt(e.target.value, 10) || 1))}
              />
              <TextField
                label="Unit cost (₹)"
                type="number"
                min={0}
                inputMode="decimal"
                value={unitCost}
                onChange={(e) => setUnitCost(Math.max(0, Number(e.target.value) || 0))}
              />
              <div className="composer__total">
                <span>Paid now</span>
                <strong>{money.format(unitCost * quantity)}</strong>
              </div>
              <button className="btn" disabled={!ready || receive.busy}>
                {receive.busy ? "Receiving…" : "Receive and stock"}
              </button>
            </form>
          )}
        </Panel>
      )}

      <Panel icon="truck" title="Received" count={purchases.total > 0 ? purchases.total : undefined} flush>
        <DataTable
          columns={columns}
          rows={purchases.loading && purchases.rows.length === 0 ? undefined : purchases.rows}
          rowKey={(row) => row.id}
          loading={purchases.loading}
          error={purchases.error}
          onRetry={purchases.reload}
          rowActions={purchaseActions}
          rowLabel={(purchase) => `Purchase from ${purchase.supplierName}`}
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
      </Panel>
    </div>
  );
}
