import { useEffect, useState } from "react";
import { api, qty } from "../lib/api";
import { PageHeader } from "../ui/PageHeader";
import type { InventoryTransaction, PageResponse } from "../lib/types";

export function MovementsPage() {
  const [page, setPage] = useState<PageResponse<InventoryTransaction> | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api<PageResponse<InventoryTransaction>>("/api/v1/inventory/transactions?size=40")
      .then(setPage)
      .catch((err: Error) => setError(err.message));
  }, []);

  return (
    <div className="page">
      <PageHeader kicker="Insights" title="Stock movements" subtitle="Every change is a row. Nothing is overwritten." />
      {error && <div className="error">{error}</div>}
      <div className="card tight">
        <table className="table">
          <thead>
            <tr>
              <th>When</th>
              <th>Type</th>
              <th>Qty</th>
              <th>Balance</th>
              <th>By</th>
              <th>Reason</th>
            </tr>
          </thead>
          <tbody>
            {page?.content.map((row) => (
              <tr key={row.id}>
                <td>{new Date(row.occurredAt).toLocaleString("en-IN")}</td>
                <td>{row.type}</td>
                <td>{row.onHandDelta > 0 ? `+${row.onHandDelta}` : row.onHandDelta}</td>
                <td>{qty.format(row.balanceAfter)}</td>
                <td>{row.createdByName ?? "—"}</td>
                <td className="faint">{row.reason ?? "—"}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {page && page.content.length === 0 && <div className="empty">No movements yet.</div>}
      </div>
    </div>
  );
}
