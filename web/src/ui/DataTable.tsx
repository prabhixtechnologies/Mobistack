import type { ReactNode } from "react";
import { useAccess } from "../lib/access";
import type { Permission } from "../lib/permissions";
import { EmptyState, ErrorState } from "./EmptyState";
import type { NavIconName } from "./navIcons";

export interface Column<T> {
  key: string;
  header: ReactNode;
  render: (row: T) => ReactNode;
  align?: "left" | "right" | "center";
  width?: string;
  /** Column is dropped entirely unless the viewer holds this capability. */
  need?: Permission;
}

interface DataTableProps<T> {
  columns: Column<T>[];
  rows: T[] | undefined;
  rowKey: (row: T) => string;
  loading?: boolean;
  error?: string | null;
  onRetry?: () => void;
  onRowClick?: (row: T) => void;
  /** Copy for the zero-row state. */
  empty?: { icon?: NavIconName; title: string; hint?: string; action?: ReactNode };
  /** Skeleton row count while loading. Match the page size to avoid layout jump. */
  skeletonRows?: number;
  /**
   * Paging footer. Supplying this shows how much of the list is on screen and a
   * button for the rest, so a long history stops looking like a short one.
   */
  paging?: { total: number; hasMore: boolean; loadingMore: boolean; onLoadMore: () => void; noun?: string };
}

/**
 * One table that owns all four states — loading, error, empty and populated — so
 * pages stop hand-rolling `{loading ? "Loading…" : ...}` chains that each look
 * slightly different.
 *
 * Columns carrying sensitive data (cost price, margin) can declare `need` and
 * disappear for viewers without that capability.
 */
export function DataTable<T>({
  columns,
  rows,
  rowKey,
  loading = false,
  error = null,
  onRetry,
  onRowClick,
  empty,
  skeletonRows = 6,
  paging,
}: DataTableProps<T>) {
  const access = useAccess();
  const visible = columns.filter((column) => !column.need || access.has(column.need));

  if (error) {
    return <ErrorState message={error} onRetry={onRetry} />;
  }

  if (loading && !rows) {
    return (
      <div className="table-scroll">
        <table className="table">
          <thead>
            <tr>
              {visible.map((column) => (
                <th key={column.key} style={{ width: column.width, textAlign: column.align }}>
                  {column.header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {Array.from({ length: skeletonRows }, (_, index) => (
              <tr key={index}>
                {visible.map((column) => (
                  <td key={column.key}>
                    <div className="skeleton" />
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    );
  }

  if (!rows?.length) {
    return <EmptyState icon={empty?.icon} title={empty?.title ?? "Nothing here yet"} hint={empty?.hint} action={empty?.action} />;
  }

  return (
    <>
      <div className="table-scroll">
        <table className={`table${loading ? " table--refreshing" : ""}`}>
          <thead>
            <tr>
              {visible.map((column) => (
                <th key={column.key} style={{ width: column.width, textAlign: column.align }}>
                  {column.header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr
                key={rowKey(row)}
                className={onRowClick ? "table__row--clickable" : undefined}
                onClick={onRowClick ? () => onRowClick(row) : undefined}
              >
                {visible.map((column) => (
                  <td key={column.key} style={{ textAlign: column.align }}>
                    {column.render(row)}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {paging && <LoadMore loaded={rows.length} {...paging} />}
    </>
  );
}

/**
 * Says how much of the list is on screen. Without the count, a page that stops
 * at twenty-five rows is indistinguishable from a shop with twenty-five rows.
 */
export function LoadMore({
  loaded,
  total,
  hasMore,
  loadingMore,
  onLoadMore,
  noun = "rows",
}: {
  loaded: number;
  total: number;
  hasMore: boolean;
  loadingMore: boolean;
  onLoadMore: () => void;
  noun?: string;
}) {
  if (!hasMore && loaded >= total) {
    return total > 0 ? <div className="load-more"><span className="faint">{total} {noun}</span></div> : null;
  }
  return (
    <div className="load-more">
      <span className="faint">
        Showing {loaded} of {total} {noun}
      </span>
      {hasMore && (
        <button className="btn ghost" type="button" onClick={onLoadMore} disabled={loadingMore}>
          {loadingMore ? "Loading…" : "Load more"}
        </button>
      )}
    </div>
  );
}
