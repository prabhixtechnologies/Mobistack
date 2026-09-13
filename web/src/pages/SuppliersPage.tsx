import { FormEvent, useMemo, useState } from "react";
import { api, money } from "../lib/api";
import { useAccess } from "../lib/access";
import { useAction } from "../lib/useAction";
import { useDebounced } from "../lib/useDebounced";
import { usePagedList } from "../lib/usePagedList";
import { DataTable, type Column } from "../ui/DataTable";
import { PageHeader } from "../ui/PageHeader";

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
  const canWrite = access.has("SUPPLIER_WRITE");
  const [search, setSearch] = useState("");
  const settled = useDebounced(search);
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");

  const path = useMemo(() => {
    const term = settled.trim();
    return term ? `/api/v1/suppliers?q=${encodeURIComponent(term)}` : "/api/v1/suppliers";
  }, [settled]);
  const suppliers = usePagedList<Supplier>(path, { size: 50 });

  const create = useAction(
    async () => {
      await api("/api/v1/suppliers", { method: "POST", body: JSON.stringify({ name, phone }) });
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

  return (
    <div className="page">
      <PageHeader
        kicker="People"
        title="Suppliers"
        subtitle="Who you buy glass and boards from. Purchases post against these names."
      />
      {create.error && <div className="error">{create.error}</div>}

      {canWrite && (
        <form className="card row" onSubmit={submit}>
          <input
            className="field"
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="Supplier"
            aria-label="Supplier name"
            required
          />
          <input className="field" value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="Phone" aria-label="Supplier phone" />
          <button className="btn" disabled={create.busy}>
            {create.busy ? "Saving…" : "Add"}
          </button>
        </form>
      )}

      <div className="card row">
        <input
          className="field"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search by name or phone…"
          aria-label="Search suppliers"
        />
      </div>

      <div className="card tight">
        <DataTable
          columns={columns}
          rows={suppliers.loading && suppliers.rows.length === 0 ? undefined : suppliers.rows}
          rowKey={(row) => row.id}
          loading={suppliers.loading}
          error={suppliers.error}
          onRetry={suppliers.reload}
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
      </div>
    </div>
  );
}
