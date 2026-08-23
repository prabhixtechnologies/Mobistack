import { FormEvent, useEffect, useState } from "react";
import { api, money } from "../lib/api";
import type { PageResponse } from "../lib/types";

interface Supplier {
  id: string;
  name: string;
  contactPerson?: string;
  phone?: string;
  city?: string;
  outstandingAmount: number;
}

export function SuppliersPage() {
  const [rows, setRows] = useState<Supplier[]>([]);
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [error, setError] = useState<string | null>(null);

  async function load() {
    const page = await api<PageResponse<Supplier>>("/api/v1/suppliers?size=50");
    setRows(page.content);
  }

  useEffect(() => {
    load().catch((err: Error) => setError(err.message));
  }, []);

  async function create(event: FormEvent) {
    event.preventDefault();
    try {
      await api("/api/v1/suppliers", { method: "POST", body: JSON.stringify({ name, phone }) });
      setName("");
      setPhone("");
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save supplier");
    }
  }

  return (
    <div className="page">
      <div className="page-title">
        <div>
          <h1>Suppliers</h1>
          <p>Who you buy glass and boards from. Purchases post against these names.</p>
        </div>
      </div>
      {error && <div className="error">{error}</div>}
      <form className="card row" onSubmit={create}>
        <input className="field" value={name} onChange={(e) => setName(e.target.value)} placeholder="Supplier" required />
        <input className="field" value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="Phone" />
        <button className="btn">Add</button>
      </form>
      <div className="card tight">
        <table className="table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Phone</th>
              <th>City</th>
              <th>Outstanding</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.id}>
                <td>{row.name}</td>
                <td>{row.phone ?? "—"}</td>
                <td>{row.city ?? "—"}</td>
                <td>{money.format(row.outstandingAmount)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
