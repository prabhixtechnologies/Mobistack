import { useCallback, useEffect, useState } from "react";
import { api } from "./api";

export interface Resource<T> {
  data: T | undefined;
  error: string | null;
  loading: boolean;
  /** Refetch without clearing `data`, so the table stays visible while updating. */
  reload: () => void;
  /** Apply a local change optimistically, e.g. after a successful mutation. */
  patch: (update: (current: T) => T) => void;
}

/**
 * Read-only GET with the four states pages actually need.
 *
 * Deliberately not a cache: MobiStack's counter screens want fresh numbers on
 * every visit, and a stale-while-revalidate layer would need invalidation rules
 * at every mutation site. `reload()` after a write is enough here, and keeps the
 * web bundle free of another dependency.
 *
 * Pass `path = null` to skip the request — useful when it depends on a value that
 * isn't ready yet.
 */
export function useResource<T>(path: string | null): Resource<T> {
  const [data, setData] = useState<T>();
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(Boolean(path));
  const [nonce, setNonce] = useState(0);

  useEffect(() => {
    if (!path) {
      setLoading(false);
      return;
    }
    let live = true;
    setLoading(true);
    api<T>(path)
      .then((result) => {
        if (live) {
          setData(result);
          setError(null);
        }
      })
      .catch((cause: unknown) => {
        if (live) {
          setError(cause instanceof Error ? cause.message : "That request failed.");
        }
      })
      .finally(() => {
        if (live) {
          setLoading(false);
        }
      });
    return () => {
      live = false;
    };
  }, [path, nonce]);

  return {
    data,
    error,
    loading,
    reload: useCallback(() => setNonce((value) => value + 1), []),
    patch: useCallback((update: (current: T) => T) => {
      setData((current) => (current === undefined ? current : update(current)));
    }, []),
  };
}
