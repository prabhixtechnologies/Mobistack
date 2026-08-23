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
  good: string;
  goodSoft: string;
  warn: string;
  warnSoft: string;
  bad: string;
  badSoft: string;
}

const KEY = "mobistack.theme";
const LEGACY_KEY = "fixflow.theme";

export const light: Palette = {
  bg: "#F4F1EA",
  card: "#FFFDF8",
  ink: "#14130F",
  soft: "#5C574C",
  faint: "#8A8476",
  line: "#E6E1D6",
  accent: "#C9841D",
  good: "#2F7D4A",
  goodSoft: "#D8F0E0",
  warn: "#B86B12",
  warnSoft: "#F8E4C4",
  bad: "#B42318",
  badSoft: "#F8D5D2",
};

export const dark: Palette = {
  bg: "#10110F",
  card: "#181916",
  ink: "#F4F1EA",
  soft: "#B7B2A4",
  faint: "#7D786C",
  line: "#2C2D28",
  accent: "#E2A43A",
  good: "#6FCD8D",
  goodSoft: "#1D3A27",
  warn: "#E3B05A",
  warnSoft: "#3A2C14",
  bad: "#F0A39C",
  badSoft: "#3D1C19",
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

function fromScheme(scheme: ColorSchemeName): ThemeMode {
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
