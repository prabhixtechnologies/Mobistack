import { usePagedList } from "../lib/usePagedList";
import { DataTable, type Column } from "../ui/DataTable";
import { PageHeader } from "../ui/PageHeader";

interface AuditRow {
  id: string;
  action: string;
  summary?: string;
  actorName?: string;
  createdAt: string;
}

export function AuditPage() {
  const trail = usePagedList<AuditRow>("/api/v1/mobistack/audit", { size: 40 });

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
        kicker="Insights"
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
