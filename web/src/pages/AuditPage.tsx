import { useEffect, useState } from "react";
import { api } from "../lib/api";
import type { PageResponse } from "../lib/types";

interface AuditRow {
  id: string;
  action: string;
  summary?: string;
  actorName?: string;
  createdAt: string;
}

export function AuditPage() {
  const [rows, setRows] = useState<AuditRow[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api<PageResponse<AuditRow>>("/api/v1/audit?size=40")
      .then((page) => setRows(page.content))
      .catch((err: Error) => setError(err.message));
  }, []);

  return (
    <div className="page">
      <div className="page-title">
        <div>
          <h1>Audit</h1>
          <p>Who changed stock, people, or prices. The ledger of decisions.</p>
        </div>
      </div>
      {error && <div className="error">{error}</div>}
      <div className="card tight">
        {rows.map((row) => (
          <div className="category-row" key={row.id}>
            <div>
              <div style={{ fontWeight: 650 }}>{row.action.replaceAll("_", " ")}</div>
              <div className="faint">{row.summary}</div>
            </div>
            <span className="faint">{row.actorName}</span>
          </div>
        ))}
        {rows.length === 0 && <div className="empty">Nothing recorded yet.</div>}
      </div>
    </div>
  );
}
