import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";

export type Theme = "light" | "dark";

const KEY = "fixflow.theme";

function readTheme(): Theme {
  const stored = localStorage.getItem(KEY);
  if (stored === "light" || stored === "dark") {
    return stored;
  }
  return window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

function applyTheme(theme: Theme): void {
  document.documentElement.dataset.theme = theme;
  document.documentElement.style.colorScheme = theme;
  localStorage.setItem(KEY, theme);
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
