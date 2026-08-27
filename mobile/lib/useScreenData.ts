import { useCallback, useEffect, useRef, useState } from "react";

export interface ScreenData<T> {
  data: T | undefined;
  /** True only while the very first load of this query is in flight. */
  loading: boolean;
  /** True while a pull-to-refresh is running, so the spinner sits in the list. */
  refreshing: boolean;
  /** Set when neither the network nor the cache produced anything. */
  error: string | null;
  /** True when the rows on screen came from the on-device copy. */
  offline: boolean;
  refresh: () => void;
}

interface Options<T> {
  /** On-device copy, used when the request fails. */
  fallback?: () => Promise<T>;
  /** Skip loading entirely, e.g. while a filter is not ready. */
  enabled?: boolean;
}

/**
 * One place for the four states a phone screen actually has: loading, failed,
 * showing cached rows, and showing live rows.
 *
 * Every screen used to write its own `try { api() } catch { cache() }` block, and
 * most of them forgot at least one state — no spinner on a cold start, or a
 * silent blank list when the request failed and there was no cache to fall back
 * on. Passing a `fallback` keeps the shop working in a basement with no signal.
 *
 * `key` should change whenever the query changes; that resets to the loading
 * state and discards any reply from the previous query.
 */
export function useScreenData<T>(
  key: string,
  load: () => Promise<T>,
  options: Options<T> = {},
): ScreenData<T> {
  const { fallback, enabled = true } = options;
  const [data, setData] = useState<T>();
  const [loading, setLoading] = useState(enabled);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [offline, setOffline] = useState(false);
  const [nonce, setNonce] = useState(0);

  // Latest-wins: a slow reply for an old search must not overwrite a new one.
  const ticket = useRef(0);
  // Held in refs so changing the closures does not retrigger the effect.
  const loader = useRef(load);
  const cache = useRef(fallback);
  loader.current = load;
  cache.current = fallback;

  useEffect(() => {
    if (!enabled) {
      setLoading(false);
      return;
    }
    const mine = ++ticket.current;
    setLoading((current) => current || data === undefined);
    (async () => {
      try {
        const fresh = await loader.current();
        if (mine !== ticket.current) {
          return;
        }
        setData(fresh);
        setOffline(false);
        setError(null);
      } catch (cause) {
        if (mine !== ticket.current) {
          return;
        }
        if (cache.current) {
          try {
            const stale = await cache.current();
            if (mine !== ticket.current) {
              return;
            }
            setData(stale);
            setOffline(true);
            setError(null);
            return;
          } catch {
            // Fall through to the network error below; the cache is no better.
          }
        }
        setError(cause instanceof Error ? cause.message : "That did not load.");
      } finally {
        if (mine === ticket.current) {
          setLoading(false);
          setRefreshing(false);
        }
      }
    })();
    // `data` is deliberately absent: including it would reload on every result.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, enabled, nonce]);

  return {
    data,
    loading,
    refreshing,
    error,
    offline,
    refresh: useCallback(() => {
      setRefreshing(true);
      setNonce((current) => current + 1);
    }, []),
  };
}
