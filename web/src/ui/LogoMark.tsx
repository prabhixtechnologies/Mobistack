import { useId } from "react";

/** Geometric stacked-M used across the console, login, and footer. */
export const LOGO_M_PATH = "M20.15 47.1 L20.15 19.15 L32 35.35 L43.85 19.15 L43.85 47.1";

/**
 * The tile is filled with the product's accent pair rather than a fixed colour, so the mark
 * is ochre-to-teal here and picks up whatever `data-brand` is set on `<html>` elsewhere. It
 * used to hardcode the old house cyan, which is why the mark stayed teal after the rest of
 * the console moved to warm ochre.
 *
 * <p>`onDark` forces the light-mode pairing, for the places the mark sits on the always-dark
 * sign-in stage rather than on a themed surface.
 */
export function LogoMark({
  size = 34,
  rounded = true,
  className,
  onDark = false,
}: {
  size?: number;
  rounded?: boolean;
  className?: string;
  onDark?: boolean;
}) {
  // Gradient ids are document-global, so two marks on one page would otherwise share one.
  const gradientId = useId();

  return (
    <svg
      className={className}
      width={size}
      height={size}
      viewBox="0 0 64 64"
      aria-hidden="true"
      focusable="false"
    >
      <defs>
        <linearGradient id={gradientId} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor={onDark ? "var(--px-ochre-500)" : "var(--px-accent)"} />
          <stop offset="1" stopColor={onDark ? "var(--px-teal-600)" : "var(--px-accent-2)"} />
        </linearGradient>
      </defs>
      <rect width="64" height="64" rx={rounded ? 16 : 0} fill={`url(#${gradientId})`} />
      <path
        d={LOGO_M_PATH}
        fill="none"
        stroke="var(--px-accent-ink)"
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="6.7"
        transform="translate(-1.1 -1.2)"
      />
    </svg>
  );
}
