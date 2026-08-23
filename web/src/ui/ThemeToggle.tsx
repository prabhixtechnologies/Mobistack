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
  if (icon) {
    return (
      <button className="icon-btn header-icon" type="button" onClick={toggle} aria-label="Toggle color theme">
        <Icon name={theme === "light" ? "moon" : "sun"} />
      </button>
    );
  }
  return (
    <button className="btn ghost" type="button" onClick={toggle} aria-label="Toggle color theme">
      {compact ? (theme === "light" ? "Dark" : "Light") : theme === "light" ? "Dark mode" : "Light mode"}
    </button>
  );
}
