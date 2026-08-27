import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "./api";
import type { PageResponse } from "./types";

export interface PagedList<T> {
  /** Every row loaded so far, across pages. */
  rows: T[];
  /** True only while the first page of a query is in flight. */
  loading: boolean;
  /** True while a further page is being appended. */
  loadingMore: boolean;
  error: string | null;
  /** How many rows the server holds in total, for "showing 25 of 340". */
  total: number;
  /** Whether another page exists. */
  hasMore: boolean;
  loadMore: () => void;
  /** Refetch from the first page, e.g. after a write. */
  reload: () => void;
}

interface Options {
  /** Rows per request. */
  size?: number;
  /** Skip fetching entirely — for a filter that isn't ready. */
  enabled?: boolean;
}

/**
 * A list that can actually reach its own end.
 *
 * Every list screen used to fetch one fixed page and silently drop the rest, so
 * a shop with four hundred invoices could only ever see twenty-five of them and
 * had no way to tell. This keeps the loaded rows, reports the true total, and
 * hands back a `loadMore` for the next page.
 *
 * `path` should be the endpoint without paging parameters; they are appended
 * here. Changing `path` (a new search term, a new filter) resets to page zero.
 */
export function usePagedList<T>(path: string | null, options: Options = {}): PagedList<T> {
  const { size = 25, enabled = true } = options;
  const [rows, setRows] = useState<T[]>([]);
  const [page, setPage] = useState(0);
  const [total, setTotal] = useState(0);
  const [last, setLast] = useState(true);
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [nonce, setNonce] = useState(0);

  // A page request that lands after the query changed must not be applied.
  const request = useRef(0);
  const active = enabled && path !== null;

  useEffect(() => {
    setRows([]);
    setPage(0);
    setTotal(0);
    setLast(true);
  }, [path, size, nonce]);

  useEffect(() => {
    if (!active) {
      setLoading(false);
      return;
    }
    const ticket = ++request.current;
    const first = page === 0;
    if (first) {
      setLoading(true);
    } else {
      setLoadingMore(true);
    }
    api<PageResponse<T>>(withPaging(path, page, size))
      .then((result) => {
        if (ticket !== request.current) {
          return;
        }
        setRows((current) => (first ? result.content : [...current, ...result.content]));
        setTotal(result.totalElements);
        setLast(result.last);
        setError(null);
      })
      .catch((cause: unknown) => {
        if (ticket !== request.current) {
          return;
        }
        setError(cause instanceof Error ? cause.message : "That list could not be loaded.");
      })
      .finally(() => {
        if (ticket !== request.current) {
          return;
        }
        setLoading(false);
        setLoadingMore(false);
      });
  }, [active, path, page, size, nonce]);

  return {
    rows,
    loading,
    loadingMore,
    error,
    total,
    hasMore: !last,
    loadMore: useCallback(() => {
      setPage((current) => current + 1);
    }, []),
    reload: useCallback(() => {
      setNonce((current) => current + 1);
    }, []),
  };
}

function withPaging(path: string | null, page: number, size: number): string {
  const base = path ?? "";
  const separator = base.includes("?") ? "&" : "?";
  return `${base}${separator}page=${page}&size=${size}`;
}
