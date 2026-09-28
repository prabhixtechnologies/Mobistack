import { useEffect, useRef } from "react";

/*
  Telling a barcode scanner apart from a person typing.

  A USB scanner is a keyboard. It types the code and presses Enter, and nothing in the browser
  says which one it was. That matters here more than it sounds: on the sales counter the search
  box sits inside the checkout form, so the scanner's Enter submitted the form. With an empty
  ticket that did nothing; with items on it, it completed the sale and printed the invoice. A
  second scan while bagging the first item took payment.

  The tell is speed. A scanner emits a whole code in tens of milliseconds and a person cannot;
  the fastest human typing is around 120ms between keys and a scanner is under 30. Measuring the
  gap between keystrokes separates them reliably without asking anyone to press a mode button.

  Listening on the document rather than on an input, because the counter's scanner fires
  wherever focus happens to be - often nowhere, after the last sale reset the page.
*/

export interface ScannerOptions {
  /**
   * Longest gap between two keystrokes that still counts as machine-fast, in milliseconds.
   * 50 leaves headroom over a typical scanner's ~20ms without reaching human speed.
   */
  maxGapMs?: number;
  /** Shorter than this is a stray keypress, not a code. Most retail barcodes are 8 or more. */
  minLength?: number;
  /** Suspend without unbinding - a modal is open, the till is locked. */
  enabled?: boolean;
}

/**
 * Calls `onScan` when a barcode scanner finishes a code.
 *
 * <p>Hand typing never reaches it, so a page can safely act on a scan without confirmation:
 * add the part, jump to the record, count it in.
 *
 * ```ts
 * useScanner((code) => void addByCode(code));
 * ```
 */
export function useScanner(
  onScan: (code: string) => void,
  { maxGapMs = 50, minLength = 4, enabled = true }: ScannerOptions = {},
) {
  // Held in a ref so an inline arrow at the call site does not rebind the listener each render.
  const onScanRef = useRef(onScan);
  onScanRef.current = onScan;

  useEffect(() => {
    if (!enabled) return;
    let buffer = "";
    let lastKeyAt = 0;

    function onKeyDown(event: KeyboardEvent) {
      // A modifier means somebody is reaching for a shortcut, and a scanner never sends one.
      if (event.metaKey || event.ctrlKey || event.altKey) {
        buffer = "";
        return;
      }

      const now = event.timeStamp || performance.now();
      const gap = now - lastKeyAt;
      lastKeyAt = now;

      if (event.key === "Enter") {
        const code = buffer;
        buffer = "";
        // The terminating Enter has to be fast too. Otherwise a person who types a SKU by hand
        // and presses Enter a second later would look like a scan.
        if (code.length >= minLength && gap <= maxGapMs) {
          // Swallowed, so the form this input sits in does not also submit. That is the whole
          // defect: the scanner's Enter used to complete the sale.
          event.preventDefault();
          event.stopPropagation();
          onScanRef.current(code);
        }
        return;
      }

      // Printable characters only. Shift arrives as its own keydown and must not reset the run,
      // because scanners do send it for upper-case codes.
      if (event.key.length !== 1) {
        if (event.key !== "Shift") buffer = "";
        return;
      }

      // Too slow to be a machine: this is a person, so start counting again from this key.
      buffer = gap > maxGapMs ? event.key : buffer + event.key;
    }

    // Capture phase, so the code is recognised and the Enter swallowed before it reaches the
    // input, the form, or anything else that would act on it.
    document.addEventListener("keydown", onKeyDown, true);
    return () => document.removeEventListener("keydown", onKeyDown, true);
  }, [maxGapMs, minLength, enabled]);
}
