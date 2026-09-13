import { createContext, createElement, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { Appearance, type ColorSchemeName } from "react-native";
import * as SecureStore from "expo-secure-store";

export type ThemeMode = "light" | "dark";

export interface Palette {
  bg: string;
  card: string;
  ink: string;
  soft: string;
  faint: string;
  line: string;
  accent: string;
  accentInk: string;
  good: string;
  goodSoft: string;
  warn: string;
  warnSoft: string;
  bad: string;
  badSoft: string;
}

const KEY = "mobistack.theme";
const LEGACY_KEY = "fixflow.theme";

/** Aligned with Infra/design/prabhix-tokens.css (teal/cyan). */
export const light: Palette = {
  bg: "#f3f6fb",
  card: "#ffffff",
  ink: "#0c1524",
  soft: "#5b6b7c",
  faint: "#8fa3b5",
  line: "#d5dee8",
  accent: "#0e7490",
  accentInk: "#f0fdfa",
  good: "#067647",
  goodSoft: "#d1fadf",
  warn: "#b45309",
  warnSoft: "#fef0c7",
  bad: "#b42318",
  badSoft: "#fee4e2",
};

export const dark: Palette = {
  bg: "#071018",
  card: "#0d1a24",
  ink: "#e8eef4",
  soft: "#8fa3b5",
  faint: "#5b6b7c",
  line: "#1c3342",
  accent: "#22d3ee",
  accentInk: "#042f2e",
  good: "#32d583",
  goodSoft: "#054f31",
  warn: "#fdb022",
  warnSoft: "#3b2507",
  bad: "#f97066",
  badSoft: "#55160c",
};

/** Default palette for modules that cannot hook into the provider yet. */
export const colors = light;

export const stockColor = (status: string, palette: Palette = colors) => {
  if (status === "GREEN") return { bg: palette.goodSoft, fg: palette.good };
  if (status === "ORANGE") return { bg: palette.warnSoft, fg: palette.warn };
  return { bg: palette.badSoft, fg: palette.bad };
};

export const money = (value: number) =>
  new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 }).format(value);

interface ThemeValue {
  mode: ThemeMode;
  colors: Palette;
  toggle: () => void;
}

const ThemeContext = createContext<ThemeValue | null>(null);

function fromScheme(scheme: ColorSchemeName | null | undefined): ThemeMode {
  return scheme === "dark" ? "dark" : "light";
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [mode, setMode] = useState<ThemeMode>(fromScheme(Appearance.getColorScheme()));

  useEffect(() => {
    Promise.all([SecureStore.getItemAsync(KEY), SecureStore.getItemAsync(LEGACY_KEY)]).then(([stored, legacy]) => {
      const value = stored ?? legacy;
      if (value === "light" || value === "dark") {
        setMode(value);
      }
    });
    const sub = Appearance.addChangeListener(({ colorScheme }) => {
      Promise.all([SecureStore.getItemAsync(KEY), SecureStore.getItemAsync(LEGACY_KEY)]).then(([stored, legacy]) => {
        const value = stored ?? legacy;
        if (value !== "light" && value !== "dark") {
          setMode(fromScheme(colorScheme));
        }
      });
    });
    return () => sub.remove();
  }, []);

  const value = useMemo<ThemeValue>(
    () => ({
      mode,
      colors: mode === "dark" ? dark : light,
      toggle: () => {
        setMode((current) => {
          const next = current === "light" ? "dark" : "light";
          void SecureStore.setItemAsync(KEY, next);
          return next;
        });
      },
    }),
    [mode],
  );

  return createElement(ThemeContext.Provider, { value }, children);
}

export function useTheme(): ThemeValue {
  const ctx = useContext(ThemeContext);
  if (!ctx) {
    return {
      mode: "light",
      colors: light,
      toggle: () => undefined,
    };
  }
  return ctx;
}
