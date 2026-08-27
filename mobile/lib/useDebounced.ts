import { useEffect, useState } from "react";

/**
 * The settled value of a field somebody is still typing into.
 *
 * Screens were firing a request per keystroke, so a five-letter part name meant
 * five round trips on a counter connection, and whichever reply landed last won
 * regardless of what was typed.
 */
export function useDebounced<T>(value: T, delay = 220): T {
  const [settled, setSettled] = useState(value);

  useEffect(() => {
    const handle = setTimeout(() => setSettled(value), delay);
    return () => clearTimeout(handle);
  }, [value, delay]);

  return settled;
}
