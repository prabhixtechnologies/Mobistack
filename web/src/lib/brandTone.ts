const BRAND_TONES = [
  { bg: "#ecfeff", fg: "#0e7490" },
  { bg: "#eff6ff", fg: "#1d4ed8" },
  { bg: "#f5f3ff", fg: "#6d28d9" },
  { bg: "#fff1f2", fg: "#be123c" },
  { bg: "#fff7ed", fg: "#c2410c" },
  { bg: "#ecfdf5", fg: "#047857" },
  { bg: "#fefce8", fg: "#a16207" },
  { bg: "#f1f5f9", fg: "#334155" },
];

export function brandTone(name?: string | null): { bg: string; fg: string } {
  const key = (name ?? "?").toUpperCase();
  let hash = 0;
  for (let i = 0; i < key.length; i += 1) {
    hash = (hash * 31 + key.charCodeAt(i)) >>> 0;
  }
  return BRAND_TONES[hash % BRAND_TONES.length];
}

export function brandMark(name?: string | null): string {
  const parts = (name ?? "?").trim().split(/\s+/).filter(Boolean);
  if (parts.length >= 2) {
    return `${parts[0][0] ?? ""}${parts[1][0] ?? ""}`.toUpperCase();
  }
  return (name ?? "?").slice(0, 2).toUpperCase();
}
