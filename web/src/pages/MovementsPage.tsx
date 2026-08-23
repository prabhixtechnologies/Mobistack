import { useEffect, useState } from "react";
import { api, qty } from "../lib/api";
import type { InventoryTransaction, PageResponse } from "../lib/types";

export function MovementsPage() {
  const [page, setPage] = useState<PageResponse<InventoryTransaction> | null>(null);

  useEffect(() => {
    api<PageResponse<InventoryTransaction>>("/api/v1/inventory/transactions?size=40").then(setPage);
  }, []);

  return (
    <div className="page">
      <div className="page-title">
        <div>
          <h1>Stock movements</h1>
          <p>Every change is a row. Nothing is overwritten.</p>
        </div>
      </div>
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
      </div>
    </div>
  );
}
