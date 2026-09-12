import { useId } from "react";

/** Geometric stacked-M used across the console, login, and footer. */
export const LOGO_M_PATH = "M20.15 47.1 L20.15 19.15 L32 35.35 L43.85 19.15 L43.85 47.1";

export function LogoMark({
  size = 34,
  rounded = true,
  className,
}: {
  size?: number;
  rounded?: boolean;
  className?: string;
}) {
  const uid = useId().replace(/:/g, "");
  const bg = `${uid}-bg`;
  const glow = `${uid}-glow`;

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
        <linearGradient id={bg} x1="6" y1="0" x2="58" y2="64" gradientUnits="userSpaceOnUse">
          <stop stopColor="#67E8F9" />
          <stop offset="0.38" stopColor="#0E7490" />
          <stop offset="1" stopColor="#042F2E" />
        </linearGradient>
        <radialGradient id={glow} cx="28%" cy="22%" r="62%">
          <stop stopColor="#fff" stopOpacity="0.28" />
          <stop offset="1" stopColor="#fff" stopOpacity="0" />
        </radialGradient>
      </defs>
      <rect width="64" height="64" rx={rounded ? 16 : 0} fill={`url(#${bg})`} />
      <rect width="64" height="64" rx={rounded ? 16 : 0} fill={`url(#${glow})`} />
      <g
        fill="none"
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="6.7"
        transform="translate(-1.1 -1.2)"
      >
        <path d={LOGO_M_PATH} stroke="#F5C542" transform="translate(2.6 2.85)" />
        <path d={LOGO_M_PATH} stroke="#A5F3FC" transform="translate(1.3 1.4)" />
        <path d={LOGO_M_PATH} stroke="#FFFFFF" />
      </g>
    </svg>
  );
}
