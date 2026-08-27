import { qty } from "../lib/api";
import { usePagedList } from "../lib/usePagedList";
import { DataTable, type Column } from "../ui/DataTable";
import { PageHeader } from "../ui/PageHeader";
import type { InventoryTransaction } from "../lib/types";

export function MovementsPage() {
  const movements = usePagedList<InventoryTransaction>("/api/v1/inventory/transactions", { size: 40 });

  const columns: Column<InventoryTransaction>[] = [
    {
      key: "when",
      header: "When",
      render: (row) => new Date(row.occurredAt).toLocaleString("en-IN"),
    },
    { key: "type", header: "Type", render: (row) => row.type },
    {
      key: "delta",
      header: "Qty",
      align: "right",
      render: (row) => (row.onHandDelta > 0 ? `+${row.onHandDelta}` : row.onHandDelta),
    },
    { key: "balance", header: "Balance", align: "right", render: (row) => qty.format(row.balanceAfter) },
    { key: "by", header: "By", render: (row) => row.createdByName ?? "—" },
    { key: "reason", header: "Reason", render: (row) => <span className="faint">{row.reason ?? "—"}</span> },
  ];

  return (
    <div className="page">
      <PageHeader
        kicker="Insights"
        title="Stock movements"
        subtitle="Every change is a row. Nothing is overwritten."
      />
      <div className="card tight">
        <DataTable
          columns={columns}
          rows={movements.loading && movements.rows.length === 0 ? undefined : movements.rows}
          rowKey={(row) => row.id}
          loading={movements.loading}
          error={movements.error}
          onRetry={movements.reload}
          skeletonRows={10}
          empty={{
            icon: "move",
            title: "No movements yet",
            hint: "Receiving stock, selling a part or fitting one to a repair all land here.",
          }}
          paging={{
            total: movements.total,
            hasMore: movements.hasMore,
            loadingMore: movements.loadingMore,
            onLoadMore: movements.loadMore,
            noun: "movements",
          }}
        />
      </div>
    </div>
  );
}
