import { render } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { useScanner } from "./useScanner";

/*
  The behaviour under test is a money defect, not a nicety.

  The sales counter's search box sits inside the checkout form, so a barcode scanner's closing
  Enter submitted that form. With items already on the ticket, it completed the sale and printed
  the invoice - scanning a second part while bagging the first took payment for the first.

  The hook has to get both halves right: recognise a scan, and let hand typing through
  untouched, because the form's Enter is still how a person submits it.
*/

function Probe({ onScan, ...options }: { onScan: (code: string) => void; minLength?: number }) {
  useScanner(onScan, options);
  return <input aria-label="Search" />;
}

/**
 * Keystrokes at a chosen speed.
 *
 * <p>Dispatched directly rather than through userEvent because the thing being measured is
 * `event.timeStamp`, and userEvent supplies its own.
 */
function type(text: string, gapMs: number, { enter = true, enterGapMs = gapMs } = {}) {
  let clock = 1000;
  const events: KeyboardEvent[] = [];
  for (const char of text) {
    clock += gapMs;
    const event = new KeyboardEvent("keydown", { key: char, bubbles: true, cancelable: true });
    // jsdom leaves timeStamp at 0, and the hook falls back to performance.now() then - which
    // would make every gap in a test look like zero. Setting it is what makes speed testable.
    Object.defineProperty(event, "timeStamp", { value: clock });
    events.push(event);
  }
  if (enter) {
    clock += enterGapMs;
    const event = new KeyboardEvent("keydown", { key: "Enter", bubbles: true, cancelable: true });
    Object.defineProperty(event, "timeStamp", { value: clock });
    events.push(event);
  }
  for (const event of events) document.dispatchEvent(event);
  return events[events.length - 1];
}

afterEach(() => document.body.replaceChildren());

describe("useScanner", () => {
  it("reports a code typed at machine speed", () => {
    const onScan = vi.fn();
    render(<Probe onScan={onScan} />);
    type("8901234567890", 15);
    expect(onScan).toHaveBeenCalledWith("8901234567890");
  });

  it("swallows the scanner's Enter, so the checkout form cannot submit on it", () => {
    render(<Probe onScan={vi.fn()} />);
    const enter = type("8901234567890", 15);
    // This is the defect in one assertion: an unprevented Enter is a completed sale.
    expect(enter.defaultPrevented).toBe(true);
  });

  it("ignores a person typing, and leaves their Enter alone", () => {
    const onScan = vi.fn();
    render(<Probe onScan={onScan} />);
    const enter = type("SCREEN-13", 180);
    expect(onScan).not.toHaveBeenCalled();
    expect(enter.defaultPrevented).toBe(false);
  });

  it("ignores a code pasted fast but confirmed slowly", () => {
    const onScan = vi.fn();
    render(<Probe onScan={onScan} />);
    // Someone who types quickly and then pauses before Enter is still a person deciding.
    type("8901234567890", 15, { enterGapMs: 900 });
    expect(onScan).not.toHaveBeenCalled();
  });

  it("ignores a stray keypress that is too short to be a barcode", () => {
    const onScan = vi.fn();
    render(<Probe onScan={onScan} />);
    type("7", 15);
    expect(onScan).not.toHaveBeenCalled();
  });

  it("keeps a run going across Shift, because scanners send it for upper-case codes", () => {
    const onScan = vi.fn();
    render(<Probe onScan={onScan} />);
    let clock = 1000;
    const press = (key: string) => {
      clock += 15;
      const event = new KeyboardEvent("keydown", { key, bubbles: true, cancelable: true });
      Object.defineProperty(event, "timeStamp", { value: clock });
      document.dispatchEvent(event);
    };
    for (const key of ["Shift", "A", "B", "Shift", "C", "1", "2", "3"]) press(key);
    press("Enter");
    expect(onScan).toHaveBeenCalledWith("ABC123");
  });

  it("does not fire while a shortcut is being pressed", () => {
    const onScan = vi.fn();
    render(<Probe onScan={onScan} />);
    let clock = 1000;
    for (const key of ["8", "9", "0", "1", "2", "3", "4", "5"]) {
      clock += 15;
      const event = new KeyboardEvent("keydown", { key, bubbles: true, ctrlKey: true });
      Object.defineProperty(event, "timeStamp", { value: clock });
      document.dispatchEvent(event);
    }
    clock += 15;
    const enter = new KeyboardEvent("keydown", { key: "Enter", bubbles: true, cancelable: true });
    Object.defineProperty(enter, "timeStamp", { value: clock });
    document.dispatchEvent(enter);
    expect(onScan).not.toHaveBeenCalled();
  });

  it("stops listening when disabled", () => {
    const onScan = vi.fn();
    function Disabled() {
      useScanner(onScan, { enabled: false });
      return null;
    }
    render(<Disabled />);
    type("8901234567890", 15);
    expect(onScan).not.toHaveBeenCalled();
  });
});
