/**
 * Opens server-rendered HTML (invoices) off the app origin.
 *
 * `window.open("")` + `document.write` executes that HTML as this origin, so a
 * script in customer/shop fields would inherit the console's cookies and tokens.
 * A blob URL runs in an opaque origin instead.
 */
export function openHtmlDocument(html: string): void {
  const blob = new Blob([html], { type: "text/html;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const popup = window.open(url, "_blank", "noopener,noreferrer");
  if (popup) {
    window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
    return;
  }
  const frame = document.createElement("iframe");
  frame.src = url;
  frame.title = "Printable document";
  frame.style.position = "fixed";
  frame.style.right = "0";
  frame.style.bottom = "0";
  frame.style.width = "0";
  frame.style.height = "0";
  frame.style.border = "0";
  frame.addEventListener(
    "load",
    () => {
      try {
        frame.contentWindow?.focus();
        frame.contentWindow?.print();
      } finally {
        window.setTimeout(() => {
          URL.revokeObjectURL(url);
          frame.remove();
        }, 1_000);
      }
    },
    { once: true },
  );
  document.body.appendChild(frame);
}
