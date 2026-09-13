import { FormEvent, useMemo, useState } from "react";
import { api, money } from "../lib/api";
import { useAccess } from "../lib/access";
import { useAction } from "../lib/useAction";
import { useDebounced } from "../lib/useDebounced";
import { usePagedList } from "../lib/usePagedList";
import { DataTable, type Column } from "../ui/DataTable";
import { SelectField, TextField } from "../ui/Field";
import { PageHeader } from "../ui/PageHeader";

interface Customer {
  id: string;
  name: string;
  phone?: string;
  email?: string;
  city?: string;
  customerType: string;
  outstandingAmount: number;
  totalPurchases: number;
}

export function CustomersPage() {
  const access = useAccess();
  const canWrite = access.has("CUSTOMER_WRITE");
  const [search, setSearch] = useState("");
  const settled = useDebounced(search);
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [type, setType] = useState("RETAIL");

  const path = useMemo(() => {
    const term = settled.trim();
    return term ? `/api/v1/customers?q=${encodeURIComponent(term)}` : "/api/v1/customers";
  }, [settled]);
  const customers = usePagedList<Customer>(path, { size: 50 });

  const create = useAction(
    async () => {
      await api("/api/v1/customers", {
        method: "POST",
        body: JSON.stringify({ name, phone, customerType: type }),
      });
      setName("");
      setPhone("");
      customers.reload();
    },
    { fallbackError: "Could not save that customer." },
  );

  function submit(event: FormEvent) {
    event.preventDefault();
    void create.run();
  }

  const columns: Column<Customer>[] = [
    {
      key: "name",
      header: "Name",
      render: (row) => (
        <div className="cell-identity">
          <strong>{row.name}</strong>
          {row.city && <span className="faint">{row.city}</span>}
        </div>
      ),
    },
    { key: "phone", header: "Phone", render: (row) => row.phone ?? "—" },
    { key: "type", header: "Type", render: (row) => row.customerType },
    { key: "purchases", header: "Purchases", align: "right", render: (row) => money.format(row.totalPurchases) },
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
        title="Customers"
        subtitle="Walk-ins stay unnamed. Regulars keep a phone and an outstanding balance."
      />
      {create.error && <div className="error">{create.error}</div>}

      {canWrite && (
        <form className="card row" onSubmit={submit}>
          <TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} required autoComplete="name" />
          <TextField label="Phone" value={phone} onChange={(e) => setPhone(e.target.value)} autoComplete="tel" />
          <SelectField label="Type" value={type} onChange={(e) => setType(e.target.value)}>
            <option value="RETAIL">Retail</option>
            <option value="WHOLESALE">Wholesale</option>
            <option value="VIP">VIP</option>
          </SelectField>
          <button className="btn" disabled={create.busy}>
            {create.busy ? "Saving…" : "Add"}
          </button>
        </form>
      )}

      <div className="card row">
        <TextField
          label="Search"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Name or phone…"
          autoComplete="off"
        />
      </div>

      <div className="card tight">
        <DataTable
          columns={columns}
          rows={customers.loading && customers.rows.length === 0 ? undefined : customers.rows}
          rowKey={(row) => row.id}
          loading={customers.loading}
          error={customers.error}
          onRetry={customers.reload}
          skeletonRows={8}
          empty={{
            icon: "users",
            title: settled.trim() ? "No customer matches that" : "No customers yet",
            hint: settled.trim()
              ? "Try part of the name or the last few digits of the phone number."
              : "Add a regular above so their balance and history follow them.",
          }}
          paging={{
            total: customers.total,
            hasMore: customers.hasMore,
            loadingMore: customers.loadingMore,
            onLoadMore: customers.loadMore,
            noun: "customers",
          }}
        />
      </div>
    </div>
  );
}
