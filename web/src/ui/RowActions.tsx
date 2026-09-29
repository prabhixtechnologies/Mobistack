import { Fragment, useCallback, useEffect, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { MenuItem, MenuSeparator } from "./Menu";
import { Modal } from "./Modal";

/*
  The row interaction contract, in MobiStack's own vocabulary.

  Deliberately not the `RowActions` from @prabhixtechnologies/ui: that one is built entirely from
  Tailwind utility classes and this app has no Tailwind, so importing it would put an
  unstyled button and a transparent panel on every row — something that builds and
  typechecks perfectly and looks broken. This version reuses this app's own `MenuItem`,
  `MenuSeparator` and `Modal`, so a row menu matches the header menu it sits under.

  Exposed as a hook rather than only a wrapper component because the rows that need it most
  are `<tr>`s, and a wrapping `<div>` is not valid inside `<tbody>`. The hook hands back
  props to spread onto whatever the row actually is, plus the panel to render.

  One action list feeds every route to the verbs: right-click on a pointer, long-press on
  touch, Shift+F10 and the Menu key from the keyboard.

  Contract: Infra/docs/UX-STANDARD.md § 3.2.
*/

export interface RowAction {
  id: string;
  label: string;
  icon?: ReactNode;
  onSelect: () => unknown;
  disabled?: boolean;
  /** Draws the item in the danger colour and separates it from the ordinary verbs. */
  danger?: boolean;
  /**
   * Groups render in order, separated by a rule; ungrouped verbs come first and anything
   * marked `danger` comes last regardless. Matches the `group` on `@prabhixtechnologies/ui`'s `RowAction`,
   * so the two menus read the same way even though this app cannot use that implementation.
   */
  group?: string;
  /** Required for anything that cannot be undone. */
  confirm?: {
    title: string;
    /** What is lost, and whether it can be reversed. Never just "Are you sure?". */
    message: string;
    confirmLabel: string;
  };
}

interface Point {
  x: number;
  y: number;
}

const LONG_PRESS_MS = 500;
const PANEL_WIDTH = 268;

export function useRowMenu(actions: RowAction[], label: string) {
  const [at, setAt] = useState<Point | null>(null);
  const [pending, setPending] = useState<RowAction | null>(null);
  const [busy, setBusy] = useState(false);
  const panel = useRef<HTMLDivElement>(null);
  const timer = useRef<number | null>(null);
  const usable = actions.some((action) => !action.disabled);

  const open = useCallback(
    (point: Point) => {
      if (usable) setAt(point);
    },
    [usable],
  );

  const cancelLongPress = useCallback(() => {
    if (timer.current !== null) {
      window.clearTimeout(timer.current);
      timer.current = null;
    }
  }, []);

  useEffect(() => cancelLongPress, [cancelLongPress]);

  useEffect(() => {
    if (!at) return;
    panel.current?.querySelector<HTMLElement>('[role="menuitem"]')?.focus();

    const onPointerDown = (event: PointerEvent) => {
      if (!panel.current?.contains(event.target as Node)) setAt(null);
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        setAt(null);
        return;
      }
      const nodes = Array.from(panel.current?.querySelectorAll<HTMLElement>('[role="menuitem"]') ?? []);
      if (nodes.length === 0) return;
      if (event.key === "Home" || event.key === "End") {
        event.preventDefault();
        (event.key === "Home" ? nodes[0] : nodes[nodes.length - 1]).focus();
        return;
      }
      if (event.key !== "ArrowDown" && event.key !== "ArrowUp") return;
      event.preventDefault();
      const index = nodes.indexOf(document.activeElement as HTMLElement);
      const delta = event.key === "ArrowDown" ? 1 : -1;
      nodes[(index + delta + nodes.length) % nodes.length].focus();
    };
    // Capture, so a scroll in any ancestor dismisses rather than leaving the panel stranded
    // away from the row it belongs to.
    const dismiss = () => setAt(null);
    document.addEventListener("pointerdown", onPointerDown);
    document.addEventListener("keydown", onKeyDown);
    window.addEventListener("scroll", dismiss, true);
    window.addEventListener("resize", dismiss);
    return () => {
      document.removeEventListener("pointerdown", onPointerDown);
      document.removeEventListener("keydown", onKeyDown);
      window.removeEventListener("scroll", dismiss, true);
      window.removeEventListener("resize", dismiss);
    };
  }, [at]);

  const run = (action: RowAction) => {
    setAt(null);
    if (action.confirm) {
      setPending(action);
      return;
    }
    void action.onSelect();
  };

  const confirmPending = async () => {
    if (!pending) return;
    setBusy(true);
    try {
      await pending.onSelect();
      setPending(null);
    } finally {
      setBusy(false);
    }
  };

  /** Spread onto the row element, whatever it is. */
  const rowProps = {
    onContextMenu: (event: React.MouseEvent) => {
      if (!usable) return;
      event.preventDefault();
      open({ x: event.clientX, y: event.clientY });
    },
    onKeyDown: (event: React.KeyboardEvent) => {
      if (event.key !== "ContextMenu" && !(event.key === "F10" && event.shiftKey)) return;
      event.preventDefault();
      const box = event.currentTarget.getBoundingClientRect();
      // Anchored to the row, because a keyboard has no pointer to anchor to.
      open({ x: box.left + 24, y: box.bottom - 8 });
    },
    onPointerDown: (event: React.PointerEvent) => {
      if (event.pointerType !== "touch" || !usable) return;
      const { clientX, clientY } = event;
      timer.current = window.setTimeout(() => {
        // Touch has no right-click, so the long press is its only route to these verbs. The
        // haptic is what tells a finger the press registered before the panel has painted.
        navigator.vibrate?.(10);
        open({ x: clientX, y: clientY });
      }, LONG_PRESS_MS);
    },
    onPointerUp: cancelLongPress,
    onPointerCancel: cancelLongPress,
    onPointerMove: cancelLongPress,
  };

  const dangerous = actions.filter((action) => action.danger);
  // Ungrouped verbs lead, so a row's primary actions stay where the pointer lands first, then
  // each named group in the order it was first mentioned. Danger is handled separately below
  // and always sits at the bottom, whatever group it was given.
  const groups = (() => {
    const order: string[] = [];
    const byGroup = new Map<string, RowAction[]>();
    for (const action of actions) {
      if (action.danger) continue;
      const key = action.group ?? "";
      if (!byGroup.has(key)) {
        byGroup.set(key, []);
        order.push(key);
      }
      byGroup.get(key)!.push(action);
    }
    order.sort((a, b) => (a === "" ? -1 : b === "" ? 1 : 0));
    return order.map((key) => byGroup.get(key)!);
  })();
  const ordinary = groups.flat();

  // Portalled, because a `position: fixed` panel inside a `.table-scroll` with its own
  // transform or overflow would be clipped by it.
  const menu =
    at && typeof document !== "undefined"
      ? createPortal(
          <div
            ref={panel}
            role="menu"
            aria-label={label}
            className="row-menu"
            // Clamped so a right-click near an edge does not open a panel off-screen.
            style={{
              left: Math.max(8, Math.min(at.x, window.innerWidth - PANEL_WIDTH)),
              top: Math.max(8, Math.min(at.y, window.innerHeight - (actions.length * 40 + 64))),
            }}
          >
            <p className="row-menu__label">{label}</p>
            {groups.map((items, index) => (
              <Fragment key={items[0]?.group ?? index}>
                {index > 0 && <MenuSeparator />}
                {items.map((action) => (
                  <MenuItem key={action.id} icon={action.icon} onClick={() => run(action)}>
                    {action.label}
                  </MenuItem>
                ))}
              </Fragment>
            ))}
            {dangerous.length > 0 && ordinary.length > 0 && <MenuSeparator />}
            {dangerous.map((action) => (
              <MenuItem key={action.id} icon={action.icon} danger onClick={() => run(action)}>
                {action.label}
              </MenuItem>
            ))}
          </div>,
          document.body,
        )
      : null;

  const confirmDialog = pending ? (
    <Modal
      open
      size="sm"
      title={pending.confirm?.title ?? pending.label}
      description={pending.confirm?.message}
      onClose={() => setPending(null)}
      footer={
        <>
          <button type="button" className="btn ghost" onClick={() => setPending(null)} disabled={busy}>
            Cancel
          </button>
          <button type="button" className="btn danger" onClick={() => void confirmPending()} disabled={busy}>
            {busy ? "Working…" : (pending.confirm?.confirmLabel ?? "Confirm")}
          </button>
        </>
      }
    />
  ) : null;

  return { rowProps, menu, confirmDialog, usable };
}

/**
 * The wrapper form, for card and list layouts where an extra `<div>` is harmless. Table rows
 * should use `useRowMenu` directly and spread `rowProps` onto the `<tr>`.
 */
export function RowActions({
  actions,
  label,
  children,
  className,
}: {
  actions: RowAction[];
  /** Names the record, not the menu: "Invoice INV-1043". Becomes the panel's heading. */
  label: string;
  children: ReactNode;
  className?: string;
}) {
  const { rowProps, menu, confirmDialog } = useRowMenu(actions, label);
  return (
    <>
      <div className={className ? `row-actions-host ${className}` : "row-actions-host"} {...rowProps}>
        {children}
      </div>
      {menu}
      {confirmDialog}
    </>
  );
}
