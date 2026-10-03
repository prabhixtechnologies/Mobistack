import { useEffect, useState, type ReactNode } from "react";
import { useAccess } from "../lib/access";
import type { Permission } from "../lib/permissions";
import { EmptyState, ErrorState } from "./EmptyState";
import type { NavIconName } from "./navIcons";
import { useRowMenu, type RowAction } from "./RowActions";

export interface Column<T> {
  key: string;
  header: ReactNode;
  /** Short text used as the field label when a table becomes cards on a phone. */
  mobileLabel?: string;
  render: (row: T) => ReactNode;
  align?: "left" | "right" | "center";
  width?: string;
  /** Column is dropped entirely unless the viewer holds this capability. */
  need?: Permission;
}

export interface BulkAction {
  id: string;
  label: string;
  onSelect: (keys: string[]) => unknown;
  danger?: boolean;
  disabled?: boolean;
}

interface DataTableProps<T> {
  columns: Column<T>[];
  rows: T[] | undefined;
  rowKey: (row: T) => string;
  loading?: boolean;
  error?: string | null;
  onRetry?: () => void;
  onRowClick?: (row: T) => void;
  /**
   * The verbs for a single row, opened by right-click, touch long-press, Shift+F10 or the
   * Menu key. Returning an empty array leaves the row without a menu, which is correct for
   * read-only audit rows.
   */
  rowActions?: (row: T) => RowAction[];
  /** Names the record for the menu heading and screen readers: "Invoice INV-1043". */
  rowLabel?: (row: T) => string;
  /** Copy for the zero-row state. */
  empty?: { icon?: NavIconName; title: string; hint?: string; action?: ReactNode };
  /** Skeleton row count while loading. Match the page size to avoid layout jump. */
  skeletonRows?: number;
  /** Adds keyboard/touch selection and a bulk-action bar. */
  selectable?: boolean;
  bulkActions?: BulkAction[];
  /**
   * Paging footer. Supplying this shows how much of the list is on screen and a
   * button for the rest, so a long history stops looking like a short one.
   */
  paging?: { total: number; hasMore: boolean; loadingMore: boolean; onLoadMore: () => void; noun?: string };
}

function columnLabel<T>(column: Column<T>): string {
  return column.mobileLabel ?? (typeof column.header === "string" ? column.header : "");
}

/**
 * One row. Split out only because `useRowMenu` is a hook and cannot be called inside the
 * `rows.map` of the parent.
 */
function DataRow<T>({
  row,
  columns,
  onRowClick,
  actions,
  label,
  selected,
  selectable,
  onSelect,
}: {
  row: T;
  columns: Column<T>[];
  onRowClick?: (row: T) => void;
  actions: RowAction[];
  label: string;
  selected: boolean;
  selectable: boolean;
  onSelect: (selected: boolean) => void;
}) {
  const { rowProps, menu, confirmDialog } = useRowMenu(actions, label);
  const hasMenu = actions.length > 0;

  return (
    <>
      <tr
        className={`${onRowClick ? "table__row--clickable" : ""}${selected ? " table__row--selected" : ""}`.trim() || undefined}
        tabIndex={onRowClick || selectable ? 0 : undefined}
        aria-selected={selectable ? selected : undefined}
        onClick={onRowClick ? () => onRowClick(row) : undefined}
        {...(hasMenu ? rowProps : {})}
        onKeyDown={(event) => {
          if (onRowClick && (event.key === "Enter" || event.key === " ")) {
            event.preventDefault();
            onRowClick(row);
            return;
          }
          if (selectable && event.key.toLowerCase() === "x") {
            event.preventDefault();
            onSelect(!selected);
            return;
          }
          if (["ArrowDown", "ArrowUp", "j", "k", "Home", "End"].includes(event.key)) {
            const tableRows = Array.from(
              event.currentTarget.closest("tbody")?.querySelectorAll<HTMLElement>("tr[tabindex='0']") ?? [],
            );
            const index = tableRows.indexOf(event.currentTarget);
            if (index >= 0) {
              event.preventDefault();
              const next =
                event.key === "Home"
                  ? 0
                  : event.key === "End"
                    ? tableRows.length - 1
                    : Math.max(0, Math.min(tableRows.length - 1, index + (event.key === "ArrowDown" || event.key === "j" ? 1 : -1)));
              tableRows[next]?.focus();
              return;
            }
          }
          if (hasMenu) rowProps.onKeyDown(event);
        }}
      >
        {selectable && (
          <td className="table__select-cell" data-label="">
            <input
              className="table__select"
              type="checkbox"
              checked={selected}
              aria-label={`Select ${label}`}
              onClick={(event) => event.stopPropagation()}
              onChange={(event) => onSelect(event.target.checked)}
            />
          </td>
        )}
        {columns.map((column) => (
          <td key={column.key} data-label={columnLabel(column)} style={{ textAlign: column.align }}>
            {column.render(row)}
          </td>
        ))}
      </tr>
      {menu}
      {confirmDialog}
    </>
  );
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
  rowActions,
  rowLabel,
  empty,
  skeletonRows = 6,
  selectable = false,
  bulkActions = [],
  paging,
}: DataTableProps<T>) {
  const access = useAccess();
  const [selected, setSelected] = useState<Set<string>>(() => new Set());
  const visible = columns.filter((column) => !column.need || access.has(column.need));
  const rowKeys = rows?.map(rowKey) ?? [];
  const rowKeySignature = rowKeys.join("\u0000");
  const allSelected = rowKeys.length > 0 && rowKeys.every((key) => selected.has(key));

  useEffect(() => {
    const available = new Set(rowKeySignature ? rowKeySignature.split("\u0000") : []);
    setSelected((current) => {
      const next = new Set([...current].filter((key) => available.has(key)));
      return next.size === current.size ? current : next;
    });
  }, [rowKeySignature]);

  if (error) {
    return <ErrorState message={error} onRetry={onRetry} />;
  }

  if (loading && !rows) {
    return (
      <div className="table-scroll">
        <table className="table">
          <thead>
            <tr>
              {selectable && <th className="table__select-cell" aria-label="Selection" />}
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
                {selectable && (
                  <td className="table__select-cell">
                    <div className="skeleton" />
                  </td>
                )}
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
    <div className="table-shell">
      {selectable && selected.size > 0 && (
        <div className="table-selection" role="region" aria-label="Selected rows">
          <strong>{selected.size} selected</strong>
          <div className="table-selection__actions">
            {bulkActions.map((action) => (
              <button
                key={action.id}
                className={`btn ${action.danger ? "danger" : "ghost"}`}
                type="button"
                disabled={action.disabled}
                onClick={() => void action.onSelect([...selected])}
              >
                {action.label}
              </button>
            ))}
            <button className="btn ghost" type="button" onClick={() => setSelected(new Set())}>
              Clear
            </button>
          </div>
        </div>
      )}
      <div className="table-scroll">
        <table className={`table${loading ? " table--refreshing" : ""}`}>
          <thead>
            <tr>
              {selectable && (
                <th className="table__select-cell">
                  <input
                    className="table__select"
                    type="checkbox"
                    checked={allSelected}
                    aria-label={allSelected ? "Clear all rows" : "Select all loaded rows"}
                    onChange={(event) => setSelected(event.target.checked ? new Set(rowKeys) : new Set())}
                  />
                </th>
              )}
              {visible.map((column) => (
                <th key={column.key} style={{ width: column.width, textAlign: column.align }}>
                  {column.header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <DataRow
                key={rowKey(row)}
                row={row}
                columns={visible}
                onRowClick={onRowClick}
                actions={rowActions?.(row) ?? []}
                label={rowLabel?.(row) ?? ""}
                selectable={selectable}
                selected={selected.has(rowKey(row))}
                onSelect={(value) =>
                  setSelected((current) => {
                    const next = new Set(current);
                    if (value) next.add(rowKey(row));
                    else next.delete(rowKey(row));
                    return next;
                  })
                }
              />
            ))}
          </tbody>
        </table>
      </div>
      {paging && <LoadMore loaded={rows.length} {...paging} />}
    </div>
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
    return total > 0 ? (
      <div className="load-more">
        <span className="faint">
          {total} {total === 1 ? noun.replace(/ies$/, "y").replace(/s$/, "") : noun}
        </span>
      </div>
    ) : null;
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
