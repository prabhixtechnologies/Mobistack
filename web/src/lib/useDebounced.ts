import { useEffect, useState } from "react";

/**
 * The settled value of a field the user is still typing into.
 *
 * Search boxes were firing a request per keystroke, which on a slow connection
 * meant results arriving out of order and the list flickering between them.
 */
export function useDebounced<T>(value: T, delay = 250): T {
  const [settled, setSettled] = useState(value);

  useEffect(() => {
    const handle = window.setTimeout(() => setSettled(value), delay);
    return () => window.clearTimeout(handle);
  }, [value, delay]);

  return settled;
}
