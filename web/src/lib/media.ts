import { useEffect, useState } from "react";

/**
 * Live match for a CSS media query. Used by the shell to switch between a
 * docked sidebar, a collapsed rail, and an off-canvas drawer.
 */
export function useMediaQuery(query: string): boolean {
  const [matches, setMatches] = useState(() =>
    typeof window !== "undefined" ? window.matchMedia(query).matches : false,
  );

  useEffect(() => {
    const media = window.matchMedia(query);
    const onChange = () => setMatches(media.matches);
    onChange();
    media.addEventListener("change", onChange);
    return () => media.removeEventListener("change", onChange);
  }, [query]);

  return matches;
}

/** Phones, phablets, and short landscape (rotated phone / split view). */
export const DRAWER_QUERY =
  "(max-width: 767.98px), (max-height: 540px) and (orientation: landscape)";

/** Tablet-sized docked rail — wide enough for content beside icons. */
export const TABLET_QUERY = "(min-width: 768px) and (max-width: 1099.98px)";
