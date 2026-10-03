import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { api, apiOnce, money, qty } from "../lib/api";
import { useAccess } from "../lib/access";
import { useAction } from "../lib/useAction";
import { useDebounced } from "../lib/useDebounced";
import { usePagedList } from "../lib/usePagedList";
import { DataTable, type Column } from "../ui/DataTable";
import { SearchField, SelectField, TextField } from "../ui/Field";
import { Modal } from "../ui/Modal";
import { PageHeader } from "../ui/PageHeader";
import { Panel } from "../ui/Panel";
import type { RowAction } from "../ui/RowActions";
import { useRowVerbs, verbs } from "../ui/rowVerbs";
import type { ProductVariant } from "../lib/types";

export function InventoryPage() {
  const access = useAccess();
  const rowVerbs = useRowVerbs();
  const canReceive = access.has("INVENTORY_WRITE");
  const [params, setParams] = useSearchParams();
  const [query, setQuery] = useState(params.get("q") ?? "");
  const [lowOnly, setLowOnly] = useState(params.get("stock") === "low");
  const [receiveFor, setReceiveFor] = useState<ProductVariant | null>(null);
  const [creating, setCreating] = useState(false);
  const settled = useDebounced(query);

  // Keep the address bar in step so a search can be shared or reloaded.
  useEffect(() => {
    const next = new URLSearchParams();
    if (settled.trim()) next.set("q", settled.trim());
    if (lowOnly) next.set("stock", "low");
    setParams(next, { replace: true });
  }, [settled, lowOnly, setParams]);

  const path = useMemo(() => {
    const search = new URLSearchParams();
    if (settled.trim()) {
      search.set("q", settled.trim());
    }
    if (lowOnly) {
      search.set("lowStockOnly", "true");
    }
    const suffix = search.toString();
    return suffix ? `/api/v1/mobistack/variants?${suffix}` : "/api/v1/mobistack/variants";
  }, [settled, lowOnly]);

  const parts = usePagedList<ProductVariant>(path, { size: 25 });

  const columns: Column<ProductVariant>[] = [
    {
      key: "part",
      header: "Part",
      mobileLabel: "Part",
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
    { key: "sku", header: "SKU", mobileLabel: "SKU", render: (variant) => variant.sku },
    {
      key: "stock",
      header: "Stock",
      mobileLabel: "Stock",
      align: "right",
      render: (variant) => (
        <span className={`badge ${variant.stockStatus}`}>{qty.format(variant.availableQty)}</span>
      ),
    },
    { key: "retail", header: "Retail", mobileLabel: "Retail", align: "right", render: (variant) => money.format(variant.retailPrice) },
    {
      key: "cost",
      header: "Cost",
      mobileLabel: "Cost",
      align: "right",
      need: "REPORT_READ",
      render: (variant) => money.format(variant.costPrice),
    },
    ...(canReceive
      ? [
          {
            key: "actions",
            header: "",
            mobileLabel: "",
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

  // The same "Add stock" the actions column renders, plus the two things a counter hand does
  // with a part that has no button anywhere: narrow the list to it, and get the SKU into
  // whatever they are typing it into.
  const partActions = (variant: ProductVariant): RowAction[] =>
    verbs(
      canReceive && {
        id: "receive",
        label: "Add stock",
        onSelect: () => setReceiveFor(variant),
      },
      rowVerbs.filterBy("sku", "Show only this part", variant.sku, setQuery),
      rowVerbs.copy("sku", "Copy SKU", variant.sku),
      rowVerbs.copy("name", "Copy part name", `${variant.productName} ${variant.variantName}`.trim()),
    );

  const filtered = Boolean(settled.trim() || lowOnly);

  return (
    <div className="page page--wide">
      <PageHeader
        icon="box"
        kicker="Shop"
        title="Inventory"
        subtitle="What is on the shelf, what it costs, and what you can sell it for."
        actions={
          <>
            <Link className="btn ghost" to="/inventory/catalog-links">
              Link a part
            </Link>
            {canReceive && (
              <button className="btn" type="button" onClick={() => setCreating(true)}>
                + New part
              </button>
            )}
          </>
        }
      />

      <Panel
        icon="box"
        title={lowOnly ? "Running low" : "Shelf"}
        count={parts.total > 0 ? parts.total : undefined}
        flush
        tools={
          <>
            <SearchField
              label="Search parts"
              value={query}
              placeholder="Part, SKU, barcode"
              onChange={(e) => setQuery(e.target.value)}
              autoComplete="off"
            />
            <button
              className={lowOnly ? "chip-toggle chip-toggle--on" : "chip-toggle"}
              type="button"
              aria-pressed={lowOnly}
              onClick={() => setLowOnly(!lowOnly)}
            >
              Low stock
            </button>
            {filtered && (
              <button
                className="btn ghost sm"
                type="button"
                onClick={() => {
                  setQuery("");
                  setLowOnly(false);
                }}
              >
                Clear
              </button>
            )}
          </>
        }
      >
        <DataTable
          columns={columns}
          rows={parts.loading && parts.rows.length === 0 ? undefined : parts.rows}
          rowKey={(variant) => variant.id}
          loading={parts.loading}
          error={parts.error}
          onRetry={parts.reload}
          rowActions={partActions}
          rowLabel={(variant) => `${variant.productName} ${variant.variantName}`.trim()}
          selectable
          bulkActions={[
            {
              id: "copy-skus",
              label: "Copy SKUs",
              onSelect: async (keys) => {
                const selected = parts.rows.filter((variant) => keys.includes(variant.id));
                await navigator.clipboard.writeText(selected.map((variant) => variant.sku).join("\n"));
              },
            },
          ]}
          skeletonRows={8}
          empty={{
            icon: "box",
            title: filtered ? "Nothing matches those filters" : "No parts yet",
            hint: filtered
              ? "Clear the search or the low-stock filter to see the whole shelf."
              : "Add the parts you keep on the shelf, with what they cost and what you sell them for. Opening stock goes into the ledger.",
            action:
              !filtered && canReceive ? (
                <button className="btn" type="button" onClick={() => setCreating(true)}>
                  + Add your first part
                </button>
              ) : undefined,
          }}
          paging={{
            total: parts.total,
            hasMore: parts.hasMore,
            loadingMore: parts.loadingMore,
            onLoadMore: parts.loadMore,
            noun: "parts",
          }}
        />
      </Panel>

      {receiveFor && (
        <ReceiveSheet variant={receiveFor} onClose={() => setReceiveFor(null)} onSaved={parts.reload} />
      )}
      {creating && <NewPartSheet onClose={() => setCreating(false)} onSaved={parts.reload} />}
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
      await apiOnce("/api/v1/mobistack/inventory/receive", { variantId: variant.id, quantity, unitCost, reason });
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

interface Category {
  id: string;
  name: string;
  sortOrder: number;
}

/** A shelf part is a product plus its first variant. Opening stock posts an OPENING ledger row. */
function NewPartSheet({ onClose, onSaved }: { onClose: () => void; onSaved: () => void }) {
  const [categories, setCategories] = useState<Category[] | null>(null);
  const [categoryId, setCategoryId] = useState("");
  const [name, setName] = useState("");
  const [variantName, setVariantName] = useState("Standard");
  const [sku, setSku] = useState("");
  const [costPrice, setCostPrice] = useState("");
  const [retailPrice, setRetailPrice] = useState("");
  const [openingStock, setOpeningStock] = useState("0");
  const [reorderLevel, setReorderLevel] = useState("2");
  const [loadError, setLoadError] = useState<string | null>(null);
  const createdProduct = useRef<{ id: string; key: string } | null>(null);

  useEffect(() => {
    api<Category[]>("/api/v1/mobistack/categories")
      .then((list) => {
        const sorted = [...list].sort((a, b) => a.sortOrder - b.sortOrder);
        setCategories(sorted);
        setCategoryId((current) => current || (sorted[0]?.id ?? ""));
      })
      .catch((err: unknown) => {
        setCategories([]);
        setLoadError(err instanceof Error ? err.message : "Categories could not be loaded.");
      });
  }, []);

  const whole = (value: string) => Math.max(0, Number.parseInt(value, 10) || 0);
  const price = (value: string) => Math.max(0, Number(value) || 0);
  const ready = Boolean(categoryId && name.trim() && variantName.trim() && retailPrice !== "");

  const save = useAction(
    async () => {
      // A retry after the variant step failed reuses the product, so it does not leave a
      // second empty product behind.
      if (!createdProduct.current || createdProduct.current.key !== `${categoryId}|${name.trim()}`) {
        const product = await api<{ id: string }>("/api/v1/mobistack/products", {
          method: "POST",
          body: JSON.stringify({ categoryId, name: name.trim(), active: true }),
        });
        createdProduct.current = { id: product.id, key: `${categoryId}|${name.trim()}` };
      }
      await api(`/api/v1/mobistack/products/variants?id=${createdProduct.current.id}`, {
        method: "POST",
        body: JSON.stringify({
          variantName: variantName.trim(),
          sku: sku.trim() || undefined,
          costPrice: price(costPrice),
          retailPrice: price(retailPrice),
          openingStock: whole(openingStock),
          reorderLevel: whole(reorderLevel),
          active: true,
        }),
      });
      onSaved();
      onClose();
    },
    { fallbackError: "Could not add that part." },
  );

  function submit(event: FormEvent) {
    event.preventDefault();
    if (ready) {
      void save.run();
    }
  }

  return (
    <Modal
      open
      title="New part"
      description="Leave the SKU empty and MobiStack makes one from the name."
      onClose={() => {
        if (!save.busy) {
          onClose();
        }
      }}
      footer={
        <>
          <button className="btn ghost" type="button" onClick={onClose} disabled={save.busy}>
            Cancel
          </button>
          <button className="btn" type="submit" form="new-part-form" disabled={save.busy || !ready}>
            {save.busy ? "Saving…" : "Add part"}
          </button>
        </>
      }
    >
      <form id="new-part-form" className="stack" onSubmit={submit}>
        {loadError && <div className="error">{loadError}</div>}
        <SelectField
          label="Category"
          value={categoryId}
          onChange={(e) => setCategoryId(e.target.value)}
          required
          disabled={!categories || categories.length === 0}
        >
          {!categories && <option value="">Loading…</option>}
          {categories?.map((category) => (
            <option key={category.id} value={category.id}>
              {category.name}
            </option>
          ))}
        </SelectField>
        <TextField
          label="Part name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Redmi Note 13 display"
          required
          autoFocus
        />
        <div className="grid-2">
          <TextField
            label="Variant"
            value={variantName}
            onChange={(e) => setVariantName(e.target.value)}
            hint="Quality, colour or grade"
            required
          />
          <TextField label="SKU" value={sku} onChange={(e) => setSku(e.target.value)} placeholder="Optional" />
        </div>
        <div className="grid-2">
          <TextField
            label="Cost price (₹)"
            type="number"
            min={0}
            step="0.01"
            inputMode="decimal"
            value={costPrice}
            onChange={(e) => setCostPrice(e.target.value)}
            placeholder="0"
          />
          <TextField
            label="Selling price (₹)"
            type="number"
            min={0}
            step="0.01"
            inputMode="decimal"
            value={retailPrice}
            onChange={(e) => setRetailPrice(e.target.value)}
            required
          />
        </div>
        <div className="grid-2">
          <TextField
            label="Opening stock"
            type="number"
            min={0}
            inputMode="numeric"
            value={openingStock}
            onChange={(e) => setOpeningStock(e.target.value)}
          />
          <TextField
            label="Warn me below"
            type="number"
            min={0}
            inputMode="numeric"
            value={reorderLevel}
            onChange={(e) => setReorderLevel(e.target.value)}
          />
        </div>
        {save.error && <div className="error">{save.error}</div>}
      </form>
    </Modal>
  );
}
