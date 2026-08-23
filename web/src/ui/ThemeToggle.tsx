import { useTheme } from "../lib/theme";

export function ThemeToggle({ compact = false }: { compact?: boolean }) {
  const { theme, toggle } = useTheme();
  return (
    <button className="btn ghost" type="button" onClick={toggle} aria-label="Toggle color theme">
      {compact ? (theme === "light" ? "Dark" : "Light") : theme === "light" ? "Dark mode" : "Light mode"}
    </button>
  );
}
