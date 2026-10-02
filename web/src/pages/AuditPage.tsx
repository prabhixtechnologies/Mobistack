import { usePagedList } from "../lib/usePagedList";
import { DataTable, type Column } from "../ui/DataTable";
import { PageHeader } from "../ui/PageHeader";
import type { RowAction } from "../ui/RowActions";
import { useRowVerbs, verbs } from "../ui/rowVerbs";

interface AuditRow {
  id: string;
  action: string;
  summary?: string;
  actorName?: string;
  createdAt: string;
}

export function AuditPage() {
  const rowVerbs = useRowVerbs();
  const trail = usePagedList<AuditRow>("/api/v1/mobistack/audit", { size: 40 });

  // An audit line is evidence, and evidence gets pasted into a message to somebody. Copying the
  // whole entry keeps the timestamp and the actor attached to it, which is the part that gets
  // dropped when it is retyped.
  const entryActions = (entry: AuditRow): RowAction[] =>
    verbs(
      rowVerbs.copy(
        "entry",
        "Copy this entry",
        [
          new Date(entry.createdAt).toLocaleString("en-IN"),
          entry.action.replaceAll("_", " "),
          entry.actorName,
          entry.summary,
        ]
          .filter(Boolean)
          .join(" \u00b7 "),
      ),
      rowVerbs.copy("actor", "Copy who did it", entry.actorName),
      rowVerbs.copy("ref", "Copy reference", entry.id),
    );

  const columns: Column<AuditRow>[] = [
    {
      key: "action",
      header: "What happened",
      render: (row) => (
        <div className="cell-identity">
          <strong>{row.action.replaceAll("_", " ")}</strong>
          {row.summary && <span className="faint">{row.summary}</span>}
        </div>
      ),
    },
    { key: "actor", header: "Who", render: (row) => row.actorName ?? "—" },
    {
      key: "when",
      header: "When",
      align: "right",
      render: (row) => <span className="faint">{new Date(row.createdAt).toLocaleString("en-IN")}</span>,
    },
  ];

  return (
    <div className="page">
      <PageHeader
        kicker="Settings"
        title="Audit"
        subtitle="Who changed stock, people, or prices. The ledger of decisions."
      />
      <div className="card tight">
        <DataTable
          columns={columns}
          rows={trail.loading && trail.rows.length === 0 ? undefined : trail.rows}
          rowKey={(row) => row.id}
          loading={trail.loading}
          error={trail.error}
            onRetry={trail.reload}
            rowActions={entryActions}
            rowLabel={(entry) => entry.action.replaceAll("_", " ")}
            skeletonRows={10}
          empty={{
            icon: "shield",
            title: "Nothing recorded yet",
            hint: "Stock adjustments, price changes and staff changes are logged here as they happen.",
          }}
          paging={{
            total: trail.total,
            hasMore: trail.hasMore,
            loadingMore: trail.loadingMore,
            onLoadMore: trail.loadMore,
            noun: "entries",
          }}
        />
      </div>
    </div>
  );
}
