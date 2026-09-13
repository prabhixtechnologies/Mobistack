import { useEffect, useRef, useState, type ReactNode } from "react";

/**
 * Popover anchored to a trigger, closing on outside click, Escape or blur out of
 * the subtree.
 *
 * Deliberately unstyled beyond layout so callers can host anything — the header
 * user card, a row's action list — without a variant explosion.
 */
export function Menu({
  trigger,
  align = "end",
  label,
  children,
}: {
  /** Receives the open state so the trigger can reflect it (chevron, active tint). */
  trigger: (state: { open: boolean }) => ReactNode;
  align?: "start" | "end";
  label: string;
  children: (close: () => void) => ReactNode;
}) {
  const [open, setOpen] = useState(false);
  const root = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) {
      return;
    }
    const items = () => Array.from(root.current?.querySelectorAll<HTMLElement>('[role="menuitem"]') ?? []);
    items()[0]?.focus();

    const onPointerDown = (event: PointerEvent) => {
      if (!root.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        setOpen(false);
        root.current?.querySelector<HTMLElement>("[aria-haspopup]")?.focus();
        return;
      }
      if (event.key !== "ArrowDown" && event.key !== "ArrowUp" && event.key !== "Home" && event.key !== "End") {
        return;
      }
      const nodes = items();
      if (nodes.length === 0) {
        return;
      }
      event.preventDefault();
      const index = nodes.indexOf(document.activeElement as HTMLElement);
      if (event.key === "Home") {
        nodes[0].focus();
        return;
      }
      if (event.key === "End") {
        nodes[nodes.length - 1].focus();
        return;
      }
      const delta = event.key === "ArrowDown" ? 1 : -1;
      const next = (index + delta + nodes.length) % nodes.length;
      nodes[next].focus();
    };
    document.addEventListener("pointerdown", onPointerDown);
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("pointerdown", onPointerDown);
      document.removeEventListener("keydown", onKeyDown);
    };
  }, [open]);

  return (
    <div className="menu" ref={root}>
      <button
        type="button"
        className="menu__trigger"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={label}
        onClick={() => setOpen((value) => !value)}
      >
        {trigger({ open })}
      </button>
      {open && (
        <div className={`menu__panel menu__panel--${align}`} role="menu">
          {children(() => setOpen(false))}
        </div>
      )}
    </div>
  );
}

export function MenuItem({
  icon,
  onClick,
  danger = false,
  children,
}: {
  icon?: ReactNode;
  onClick: () => void;
  danger?: boolean;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      role="menuitem"
      className={`menu__item${danger ? " menu__item--danger" : ""}`}
      onClick={onClick}
    >
      {icon && <span className="menu__icon">{icon}</span>}
      <span>{children}</span>
    </button>
  );
}

export function MenuSeparator() {
  return <div className="menu__sep" role="separator" />;
}
