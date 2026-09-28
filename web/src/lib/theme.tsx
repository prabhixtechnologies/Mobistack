import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { storeGet, storeSet } from "./storage";

export type Theme = "light" | "dark";

function readTheme(): Theme {
  const stored = storeGet("theme");
  if (stored === "light" || stored === "dark") {
    return stored;
  }
  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

function applyTheme(theme: Theme): void {
  document.documentElement.dataset.theme = theme;
  document.documentElement.style.colorScheme = theme;
  const meta = document.querySelector('meta[name="theme-color"]');
  if (meta) {
    // px-allow-literal: the browser paints the address bar from this attribute before any
    // stylesheet is consulted, so it cannot be var(). These were the house cyan and its dark
    // navy, left behind when MobiStack moved to its own ochre theme — the one surface still
    // announcing the old brand. They now mirror --px-accent and --px-bg for mobistack.
    meta.setAttribute("content", theme === "dark" ? "#12100e" : "#b45309");
  }
  storeSet("theme", theme);
}

interface ThemeValue {
  theme: Theme;
  toggle: () => void;
  setTheme: (theme: Theme) => void;
}

const ThemeContext = createContext<ThemeValue | null>(null);

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<Theme>(() => (typeof document === "undefined" ? "light" : readTheme()));

  useEffect(() => {
    applyTheme(theme);
  }, [theme]);

  const value = useMemo<ThemeValue>(
    () => ({
      theme,
      setTheme: setThemeState,
      toggle: () => setThemeState((current) => (current === "light" ? "dark" : "light")),
    }),
    [theme],
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme(): ThemeValue {
  const ctx = useContext(ThemeContext);
  if (!ctx) {
    throw new Error("useTheme must be used inside ThemeProvider");
  }
  return ctx;
}
