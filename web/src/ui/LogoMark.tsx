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
  return (
    <svg
      className={className}
      width={size}
      height={size}
      viewBox="0 0 64 64"
      aria-hidden="true"
      focusable="false"
    >
      <rect width="64" height="64" rx={rounded ? 16 : 0} fill="#0E7490" />
      <path
        d={LOGO_M_PATH}
        fill="none"
        stroke="#FFFFFF"
        strokeLinecap="round"
        strokeLinejoin="round"
        strokeWidth="6.7"
        transform="translate(-1.1 -1.2)"
      />
    </svg>
  );
}
