import type { CSSProperties } from "react";

type Tone =
  | "neutral"
  | "red"
  | "rose"
  | "pink"
  | "fuchsia"
  | "violet"
  | "indigo"
  | "blue"
  | "cyan"
  | "teal"
  | "green"
  | "lime"
  | "yellow"
  | "ochre"
  | "clay";

const PARTS: Record<string, { tone: Tone; path: string }> = {
  TEMPERED_GLASS: { tone: "cyan", path: "M12 3 5 6v6c0 4.5 3 8.5 7 9.5 4-1 7-5 7-9.5V6Z" },
  TOUCH_OCA: {
    tone: "violet",
    path: "M9 11V5.5a1.5 1.5 0 0 1 3 0V11m0-1.5a1.5 1.5 0 0 1 3 0V11m0-.5a1.5 1.5 0 0 1 3 0V15a6 6 0 0 1-6 6h-.5a6 6 0 0 1-4.6-2.2L4.5 15.5a1.5 1.5 0 0 1 2.3-1.9L9 16",
  },
  DISPLAY_FOLDER: { tone: "pink", path: "M7 2h10a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2Zm4 17h2" },
  DISPLAY_CONNECTOR: { tone: "clay", path: "M9 2v5m6-5v5M6 7h12v4a6 6 0 0 1-12 0Zm6 10v5" },
  FRAME: { tone: "indigo", path: "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Zm2 4v10h10V7Z" },
  BACK_COVER: { tone: "teal", path: "M12 3 2 8l10 5 10-5Zm-10 9 10 5 10-5M2 16l10 5 10-5" },
  BATTERY: { tone: "green", path: "M3 8a2 2 0 0 1 2-2h12a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Zm19 3v2M7 10v4m4-4v4" },
  POWER_VOLUME_FLEX: { tone: "yellow", path: "M8 6h8a6 6 0 0 1 0 12H8A6 6 0 0 1 8 6Zm0 9a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z" },
  CHARGING_BOARD: { tone: "ochre", path: "M13 2 4 14h7l-1 8 9-12h-7Z" },
  CAMERA: {
    tone: "blue",
    path: "M3 8a2 2 0 0 1 2-2h2l2-2h6l2 2h2a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Zm9 9a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z",
  },
  SPEAKER: { tone: "fuchsia", path: "M4 9h4l5-4v14l-5-4H4Zm12 0a4 4 0 0 1 0 6m2.5-9a8 8 0 0 1 0 12" },
  MICROPHONE: { tone: "lime", path: "M12 2a3 3 0 0 1 3 3v6a3 3 0 0 1-6 0V5a3 3 0 0 1 3-3Zm7 9a7 7 0 0 1-14 0m7 7v4" },
  IC_CHIP: { tone: "red", path: "M7 7h10v10H7Zm3 3h4v4h-4ZM9 2v3m6-3v3M9 19v3m6-3v3M2 9h3m-3 6h3m14-6h3m-3 6h3" },
  OTHER: { tone: "neutral", path: "M3 7.5 12 3l9 4.5v9L12 21 3 16.5Zm0 0 9 4.5 9-4.5M12 12v9" },
};

const FALLBACK = { tone: "rose" as Tone, path: PARTS.OTHER.path };

/** Swatch variables for a part category; the tile reads `--part-bg`, `--part-ink` and `--part-line`. */
export function partTone(code: string): CSSProperties {
  const { tone } = PARTS[code] ?? FALLBACK;
  return {
    "--part-bg": `var(--px-tag-${tone}-bg)`,
    "--part-ink": `var(--px-tag-${tone}-ink)`,
    "--part-line": `var(--px-tag-${tone}-border)`,
  } as CSSProperties;
}

export function PartIcon({ code }: { code: string }) {
  const { path } = PARTS[code] ?? FALLBACK;
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <path d={path} />
    </svg>
  );
}
