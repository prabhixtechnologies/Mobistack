import { useRef, type ReactNode } from "react";
import { useAccess } from "../lib/access";
import type { Permission } from "../lib/permissions";

export interface TabDef {
  id: string;
  label: string;
  /** Tab is hidden unless the viewer holds this capability. */
  need?: Permission;
  /** Small count/status pill rendered after the label. */
  badge?: ReactNode;
}

/**
 * Keyboard-navigable tab strip with permission filtering.
 *
 * Arrow keys move between tabs and Home/End jump to the ends, matching the WAI
 * tabs pattern. Tabs the viewer can't use are removed rather than disabled, so
 * the strip never advertises screens they cannot reach.
 */
export function Tabs({
  tabs,
  active,
  onChange,
  label = "Sections",
}: {
  tabs: TabDef[];
  active: string;
  onChange: (id: string) => void;
  label?: string;
}) {
  const { permissions } = useAccess();
  const visible = tabs.filter((tab) => !tab.need || permissions.has(tab.need));
  const refs = useRef<Record<string, HTMLButtonElement | null>>({});

  const move = (delta: number) => {
    const index = visible.findIndex((tab) => tab.id === active);
    const next = visible[(index + delta + visible.length) % visible.length];
    if (next) {
      onChange(next.id);
      refs.current[next.id]?.focus();
    }
  };

  return (
    <div className="tabs" role="tablist" aria-label={label}>
      {visible.map((tab) => (
        <button
          key={tab.id}
          ref={(node) => {
            refs.current[tab.id] = node;
          }}
          role="tab"
          type="button"
          id={`tab-${tab.id}`}
          aria-selected={tab.id === active}
          aria-controls={`panel-${tab.id}`}
          tabIndex={tab.id === active ? 0 : -1}
          className={`tab${tab.id === active ? " tab--active" : ""}`}
          onClick={() => onChange(tab.id)}
          onKeyDown={(event) => {
            if (event.key === "ArrowRight") {
              event.preventDefault();
              move(1);
            } else if (event.key === "ArrowLeft") {
              event.preventDefault();
              move(-1);
            } else if (event.key === "Home") {
              event.preventDefault();
              onChange(visible[0].id);
            } else if (event.key === "End") {
              event.preventDefault();
              onChange(visible[visible.length - 1].id);
            }
          }}
        >
          {tab.label}
          {tab.badge !== undefined && <span className="tab__badge">{tab.badge}</span>}
        </button>
      ))}
    </div>
  );
}

export function TabPanel({ id, active, children }: { id: string; active: string; children: ReactNode }) {
  if (id !== active) {
    return null;
  }
  return (
    <div role="tabpanel" id={`panel-${id}`} aria-labelledby={`tab-${id}`} className="tab-panel">
      {children}
    </div>
  );
}
