import { FormEvent, useMemo, useState } from "react";
import { api, money } from "../lib/api";
import { useAccess } from "../lib/access";
import { useAction } from "../lib/useAction";
import { useDebounced } from "../lib/useDebounced";
import { usePagedList } from "../lib/usePagedList";
import { DataTable, type Column } from "../ui/DataTable";
import { SearchField, TextField } from "../ui/Field";
import { PageHeader } from "../ui/PageHeader";
import { Panel } from "../ui/Panel";
import type { RowAction } from "../ui/RowActions";
import { useRowVerbs, verbs } from "../ui/rowVerbs";

interface Supplier {
  id: string;
  name: string;
  contactPerson?: string;
  phone?: string;
  city?: string;
  outstandingAmount: number;
}

export function SuppliersPage() {
  const access = useAccess();
  const rowVerbs = useRowVerbs();
  const canWrite = access.has("SUPPLIER_WRITE");
  const [search, setSearch] = useState("");
  const settled = useDebounced(search);
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");

  const path = useMemo(() => {
    const term = settled.trim();
    return term ? `/api/v1/mobistack/suppliers?q=${encodeURIComponent(term)}` : "/api/v1/mobistack/suppliers";
  }, [settled]);
  const suppliers = usePagedList<Supplier>(path, { size: 50 });

  const create = useAction(
    async () => {
      await api("/api/v1/mobistack/suppliers", { method: "POST", body: JSON.stringify({ name, phone }) });
      setName("");
      setPhone("");
      suppliers.reload();
    },
    { fallbackError: "Could not save that supplier." },
  );

  function submit(event: FormEvent) {
    event.preventDefault();
    void create.run();
  }

  const columns: Column<Supplier>[] = [
    {
      key: "name",
      header: "Name",
      render: (row) => (
        <div className="cell-identity">
          <strong>{row.name}</strong>
          {row.contactPerson && <span className="faint">{row.contactPerson}</span>}
        </div>
      ),
    },
    { key: "phone", header: "Phone", render: (row) => row.phone ?? "—" },
    { key: "city", header: "City", render: (row) => row.city ?? "—" },
    {
      key: "outstanding",
      header: "Outstanding",
      align: "right",
      render: (row) => money.format(row.outstandingAmount),
    },
  ];

  // A phone number on this screen exists to be dialled or pasted somewhere else, which until
  // now meant selecting it by hand off a table cell.
  const supplierActions = (supplier: Supplier): RowAction[] =>
    verbs(
      rowVerbs.filterBy("name", "Show only this supplier", supplier.name, setSearch),
      rowVerbs.copy("name", "Copy name", supplier.name),
      rowVerbs.copy("phone", "Copy phone", supplier.phone),
      rowVerbs.copy("contact", "Copy contact person", supplier.contactPerson),
    );

  return (
    <div className="page">
      <PageHeader
        icon="truck"
        kicker="Inventory"
        title="Suppliers"
        subtitle="Who you buy glass and boards from. Purchases post against these names."
      />
      {create.error && <div className="error">{create.error}</div>}

      {canWrite && (
        <Panel icon="plus" title="Add a supplier" hint="The wholesaler or market stall you buy cartons from.">
          <form className="composer" onSubmit={submit}>
            <TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} required autoComplete="organization" placeholder="Nehru Place Mobiles" />
            <TextField label="Phone" value={phone} onChange={(e) => setPhone(e.target.value)} autoComplete="tel" inputMode="tel" />
            <button className="btn" disabled={create.busy || !name.trim()}>
              {create.busy ? "Saving…" : "Add supplier"}
            </button>
          </form>
        </Panel>
      )}

      <Panel
        icon="truck"
        title="All suppliers"
        count={suppliers.total > 0 ? suppliers.total : undefined}
        flush
        tools={
          <SearchField
            label="Search suppliers"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Name or phone"
            autoComplete="off"
          />
        }
      >
        <DataTable
          columns={columns}
          rows={suppliers.loading && suppliers.rows.length === 0 ? undefined : suppliers.rows}
          rowKey={(row) => row.id}
          loading={suppliers.loading}
          error={suppliers.error}
          onRetry={suppliers.reload}
          rowActions={supplierActions}
          rowLabel={(supplier) => supplier.name}
          skeletonRows={8}
          empty={{
            icon: "truck",
            title: settled.trim() ? "No supplier matches that" : "No suppliers yet",
            hint: settled.trim()
              ? "Try part of the name or the phone number."
              : "Add the people you buy from so purchases can post against them.",
          }}
          paging={{
            total: suppliers.total,
            hasMore: suppliers.hasMore,
            loadingMore: suppliers.loadingMore,
            onLoadMore: suppliers.loadMore,
            noun: "suppliers",
          }}
        />
      </Panel>
    </div>
  );
}
