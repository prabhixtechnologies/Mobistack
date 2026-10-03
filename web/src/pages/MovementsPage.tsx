import { qty } from "../lib/api";
import { usePagedList } from "../lib/usePagedList";
import { DataTable, type Column } from "../ui/DataTable";
import { PageHeader } from "../ui/PageHeader";
import { Panel } from "../ui/Panel";
import { humanLabel } from "../lib/labels";
import type { RowAction } from "../ui/RowActions";
import { useRowVerbs, verbs } from "../ui/rowVerbs";
import type { InventoryTransaction } from "../lib/types";

export function MovementsPage() {
  const rowVerbs = useRowVerbs();
  const movements = usePagedList<InventoryTransaction>("/api/v1/mobistack/inventory/transactions", { size: 40 });

  // This is the ledger people come to when a count disagrees with the shelf, so the verb that
  // matters is getting one line out of it and into the message where the question is being
  // asked. The whole line rather than a field, because a movement only means something with
  // its date, its quantity and its reason together.
  const movementActions = (movement: InventoryTransaction): RowAction[] =>
    verbs(
      rowVerbs.copy(
        "line",
        "Copy this movement",
        [
          new Date(movement.occurredAt).toLocaleString("en-IN"),
          movement.type,
          movement.onHandDelta > 0 ? `+${movement.onHandDelta}` : String(movement.onHandDelta),
          movement.createdByName,
          movement.reason,
        ]
          .filter(Boolean)
          .join(" \u00b7 "),
      ),
      rowVerbs.copy("reason", "Copy reason", movement.reason),
      rowVerbs.copy("ref", "Copy reference", movement.id),
    );

  const columns: Column<InventoryTransaction>[] = [
    {
      key: "when",
      header: "When",
      render: (row) =>
        new Date(row.occurredAt).toLocaleString("en-IN", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" }),
    },
    {
      key: "type",
      header: "Type",
      render: (row) => <span className="badge neutral">{humanLabel(row.type)}</span>,
    },
    {
      key: "delta",
      header: "Qty",
      align: "right",
      render: (row) => (
        <span className={row.onHandDelta > 0 ? "delta delta--in" : row.onHandDelta < 0 ? "delta delta--out" : "delta"}>
          {row.onHandDelta > 0 ? `+${row.onHandDelta}` : row.onHandDelta}
        </span>
      ),
    },
    { key: "balance", header: "Balance", align: "right", render: (row) => qty.format(row.balanceAfter) },
    { key: "by", header: "By", render: (row) => row.createdByName ?? "—" },
    { key: "reason", header: "Reason", render: (row) => <span className="faint">{row.reason ?? "—"}</span> },
  ];

  return (
    <div className="page">
      <PageHeader
        icon="move"
        kicker="Inventory"
        title="Stock activity"
        subtitle="Every change is a row. Nothing is overwritten."
      />
      <Panel icon="move" title="Ledger" hint="Newest first" count={movements.total > 0 ? movements.total : undefined} flush>
        <DataTable
          columns={columns}
          rows={movements.loading && movements.rows.length === 0 ? undefined : movements.rows}
          rowKey={(row) => row.id}
          loading={movements.loading}
          error={movements.error}
            onRetry={movements.reload}
            rowActions={movementActions}
            rowLabel={(movement) => `${movement.type} on ${new Date(movement.occurredAt).toLocaleDateString("en-IN")}`}
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
      </Panel>
    </div>
  );
}
