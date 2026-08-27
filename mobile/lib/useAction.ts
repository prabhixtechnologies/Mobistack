import { useCallback, useRef, useState } from "react";

export interface Action<Args extends unknown[]> {
  run: (...args: Args) => Promise<void>;
  /** Bind to `disabled` so the button cannot fire twice. */
  busy: boolean;
  error: string | null;
  clearError: () => void;
}

/**
 * Stops a tap from being counted twice.
 *
 * A slow save invites a second tap, and on this app that meant two invoices or
 * two repair jobs for one customer. The flag lives in a ref as well as state
 * because two taps in the same frame both read the stale state value.
 */
export function useAction<Args extends unknown[]>(
  work: (...args: Args) => Promise<unknown>,
  options: { fallbackError?: string } = {},
): Action<Args> {
  const { fallbackError = "That did not go through." } = options;
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const inFlight = useRef(false);

  const run = useCallback(
    async (...args: Args) => {
      if (inFlight.current) {
        return;
      }
      inFlight.current = true;
      setBusy(true);
      setError(null);
      try {
        await work(...args);
      } catch (cause) {
        setError(cause instanceof Error ? cause.message : fallbackError);
      } finally {
        inFlight.current = false;
        setBusy(false);
      }
    },
    [work, fallbackError],
  );

  return { run, busy, error, clearError: useCallback(() => setError(null), []) };
}
