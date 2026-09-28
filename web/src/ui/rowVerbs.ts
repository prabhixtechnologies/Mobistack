import { useCallback, useMemo } from "react";
import type { RowAction } from "./RowActions";
import { useToast } from "./Toast";

/*
  The row verbs that are the same on every list, so each page does not write them again.

  These are deliberately modest. The audit asked for a context menu on every row, and the
  temptation is to fill it with edit and delete — but most of these lists have only a create
  endpoint behind them, and a menu item that calls an endpoint which does not exist is worse
  than no menu item. What is here is real:

    copy    reads a value out of the row and onto the clipboard. The audit counted exactly one
            copy affordance in the entire portfolio, against a job where people read a phone
            number or an IMEI off the screen and type it into something else all day.
    filter  puts the row's value into the page's own search box, which is how you answer "what
            else is there for this supplier" without leaving the list.
    open    a link the page already has, moved into the menu so the row has one place to look.

  Page-specific verbs live on the page. This is the shared floor, not the whole list.
*/

/** Drops the absent entries, so a page can write a verb list with holes in it. */
export function verbs(...items: (RowAction | null | undefined | false)[]): RowAction[] {
  return items.filter((item): item is RowAction => Boolean(item));
}

export function useRowVerbs() {
  const toast = useToast();

  /**
   * Copies one field of a row.
   *
   * Returns null for an empty value rather than a disabled item, because a menu is a list of
   * what can be done and a permanently greyed row is only noise. A supplier with no phone
   * number should have no "Copy phone".
   *
   * The confirmation is a toast rather than nothing. A clipboard write is silent and
   * instantaneous, so without it there is no way to tell a success from a click that missed.
   */
  const copy = useCallback(
    (id: string, label: string, value: string | null | undefined): RowAction | null => {
      const text = value?.toString().trim();
      if (!text) return null;
      return {
        id: `copy-${id}`,
        label,
        group: "copy",
        onSelect: async () => {
          try {
            // Available only over HTTPS and localhost. The shop tablets run over HTTPS, but a
            // failure here is silent otherwise, which is the one outcome worth ruling out.
            await navigator.clipboard.writeText(text);
            toast.success(`Copied ${text.length > 40 ? "to clipboard" : text}`);
          } catch {
            toast.error("Could not reach the clipboard. Copy it by hand.");
          }
        },
      };
    },
    [toast],
  );

  /**
   * Puts a value into the page's search box.
   *
   * Takes the setter rather than doing the navigation itself, because each list owns its own
   * filter state and some of them keep it in the URL. Narrowing a list to one supplier's rows
   * is the commonest thing anyone wants from a row, and before this it meant selecting the text
   * and retyping it above.
   */
  const filterBy = useCallback(
    (id: string, label: string, value: string | null | undefined, apply: (value: string) => void): RowAction | null => {
      const text = value?.toString().trim();
      if (!text) return null;
      return { id: `filter-${id}`, label, group: "filter", onSelect: () => apply(text) };
    },
    [],
  );

  return useMemo(() => ({ copy, filterBy }), [copy, filterBy]);
}
