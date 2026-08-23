import { FormEvent, useEffect, useState } from "react";
import { api, money } from "../lib/api";
import type { PageResponse } from "../lib/types";

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
  const [rows, setRows] = useState<Customer[]>([]);
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [type, setType] = useState("RETAIL");
  const [error, setError] = useState<string | null>(null);

  async function load() {
    const page = await api<PageResponse<Customer>>("/api/v1/customers?size=50");
    setRows(page.content);
  }

  useEffect(() => {
    load().catch((err: Error) => setError(err.message));
  }, []);

  async function create(event: FormEvent) {
    event.preventDefault();
    try {
      await api("/api/v1/customers", { method: "POST", body: JSON.stringify({ name, phone, customerType: type }) });
      setName("");
      setPhone("");
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save customer");
    }
  }

  return (
    <div className="page">
      <div className="page-title">
        <div>
          <h1>Customers</h1>
          <p>Walk-ins stay unnamed. Regulars keep a phone and an outstanding balance.</p>
        </div>
      </div>
      {error && <div className="error">{error}</div>}
      <form className="card row" onSubmit={create}>
        <input className="field" value={name} onChange={(e) => setName(e.target.value)} placeholder="Name" required />
        <input className="field" value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="Phone" />
        <select className="select" value={type} onChange={(e) => setType(e.target.value)} style={{ width: 160 }}>
          <option value="RETAIL">Retail</option>
          <option value="WHOLESALE">Wholesale</option>
          <option value="VIP">VIP</option>
        </select>
        <button className="btn">Add</button>
      </form>
      <div className="card tight">
        <table className="table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Phone</th>
              <th>Type</th>
              <th>Purchases</th>
              <th>Outstanding</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.id}>
                <td>{row.name}</td>
                <td>{row.phone ?? "—"}</td>
                <td>{row.customerType}</td>
                <td>{money.format(row.totalPurchases)}</td>
                <td>{money.format(row.outstandingAmount)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {rows.length === 0 && <div className="empty">No customers yet.</div>}
      </div>
    </div>
  );
}
