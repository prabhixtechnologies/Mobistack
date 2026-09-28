import { toneFor } from "@prabhix/brand";
import type { CSSProperties } from "react";

/**
 * The swatch for a catalog mark — a phone brand, a component category.
 *
 * This used to be eight hand-picked light-mode hex pairs and a hash of its own. Two problems
 * with that: none of the pairs had ever been through the contrast gate, and there was only one
 * set, so in dark mode a pale `#ecfeff` chip sat glaring on a dark surface. It was also a second
 * implementation of something `@prabhix/brand` already does, which is how the same brand ended
 * up a different colour here than anywhere else in the portfolio.
 *
 * Now it returns the shared swatch variables. Those are generated from tokens.json, have a light
 * and a dark value, and every one is asserted AA against its own background by the contrast gate.
 * Samsung is the same colour here as it would be on a chip in any other product.
 */
export function brandTone(name?: string | null): CSSProperties {
  const tone = toneFor(name ?? "?");
  return {
    background: `var(--px-tag-${tone}-bg)`,
    color: `var(--px-tag-${tone}-ink)`,
  };
}

export function brandMark(name?: string | null): string {
  const parts = (name ?? "?").trim().split(/\s+/).filter(Boolean);
  if (parts.length >= 2) {
    return `${parts[0][0] ?? ""}${parts[1][0] ?? ""}`.toUpperCase();
  }
  return (name ?? "?").slice(0, 2).toUpperCase();
}
