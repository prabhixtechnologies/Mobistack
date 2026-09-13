import { useTheme } from "../lib/theme";
import { Icon } from "./navIcons";

export function ThemeToggle({
  compact = false,
  icon = false,
}: {
  compact?: boolean;
  icon?: boolean;
}) {
  const { theme, toggle } = useTheme();
  const next = theme === "light" ? "dark" : "light";
  const label = `Switch to ${next} mode`;
  if (icon) {
    return (
      <button
        className="icon-btn header-icon"
        type="button"
        onClick={toggle}
        aria-label={label}
        title={label}
      >
        <Icon name={theme === "light" ? "moon" : "sun"} />
      </button>
    );
  }
  return (
    <button className="btn ghost" type="button" onClick={toggle} aria-label={label}>
      {compact ? (theme === "light" ? "Dark" : "Light") : theme === "light" ? "Dark mode" : "Light mode"}
    </button>
  );
}
