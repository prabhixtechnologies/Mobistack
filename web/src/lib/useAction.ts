import { useCallback, useRef, useState } from "react";

export interface Action<Args extends unknown[]> {
  /** Runs the work unless a run is already in flight. */
  run: (...args: Args) => Promise<void>;
  /** Bind to `disabled` so the button cannot be pressed twice. */
  busy: boolean;
  /** Message from the last failure, or null. */
  error: string | null;
  clearError: () => void;
}

/**
 * Stops a button from firing its work twice.
 *
 * On a shop counter, a slow network invites a second press, and the second press
 * used to open a second repair job or send a second invitation. The in-flight
 * flag is held in a ref as well as state because two clicks in the same frame
 * both read the old state value.
 */
export function useAction<Args extends unknown[]>(
  work: (...args: Args) => Promise<unknown>,
  options: { onDone?: () => void; fallbackError?: string } = {},
): Action<Args> {
  const { onDone, fallbackError = "That did not go through." } = options;
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
        onDone?.();
      } catch (cause) {
        setError(cause instanceof Error ? cause.message : fallbackError);
      } finally {
        inFlight.current = false;
        setBusy(false);
      }
    },
    [work, onDone, fallbackError],
  );

  return { run, busy, error, clearError: useCallback(() => setError(null), []) };
}
