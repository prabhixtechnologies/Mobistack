import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../lib/api";
import { useMediaQuery } from "../lib/media";
import type { GlobalSearchResponse } from "../lib/types";
import { Icon } from "./navIcons";

export function GlobalSearch() {
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);
  const [result, setResult] = useState<GlobalSearchResponse | null>(null);
  const box = useRef<HTMLDivElement>(null);
  const input = useRef<HTMLInputElement>(null);
  const navigate = useNavigate();
  const compact = useMediaQuery("(max-width: 720px)");

  useEffect(() => {
    if (query.trim().length < 2) {
      setResult(null);
      return;
    }
    const handle = window.setTimeout(async () => {
      const data = await api<GlobalSearchResponse>(`/api/v1/search?q=${encodeURIComponent(query)}`);
      setResult(data);
      setOpen(true);
    }, 160);
    return () => window.clearTimeout(handle);
  }, [query]);

  useEffect(() => {
    const onClick = (event: MouseEvent) => {
      if (!box.current?.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    const onKey = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        input.current?.focus();
      }
    };
    document.addEventListener("mousedown", onClick);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", onClick);
      document.removeEventListener("keydown", onKey);
    };
  }, []);

  return (
    <div className="search" ref={box}>
      <Icon name="search" className="search-ico" />
      <input
        ref={input}
        value={query}
        placeholder={compact ? "Search…" : "Search a phone, SKU, barcode…"}
        onChange={(event) => setQuery(event.target.value)}
        onFocus={() => result && setOpen(true)}
      />
      <kbd className="search-kbd">Ctrl K</kbd>
      {open && result && (
        <div className="search-panel">
          {result.devices.map((device) => (
            <button
              key={device.id}
              className="search-hit"
              type="button"
              onClick={() => {
                navigate(`/devices/${device.id}`);
                setOpen(false);
                setQuery("");
              }}
            >
              <strong>
                {device.brandName} {device.name}
              </strong>
              <div className="faint">
                Device
                {device.matchedAliases.length > 0 ? ` · also ${device.matchedAliases.join(", ")}` : ""}
              </div>
            </button>
          ))}
          {result.parts.map((part) => (
            <button
              key={part.variantId}
              className="search-hit"
              type="button"
              onClick={() => {
                navigate(`/inventory?q=${encodeURIComponent(part.sku)}`);
                setOpen(false);
                setQuery("");
              }}
            >
              <strong>{part.productName}</strong>
              <div className="faint">
                {part.variantName} · {part.availableQty} in stock
              </div>
            </button>
          ))}
          {result.totalResults === 0 && <div className="empty">Nothing matches “{result.query}”.</div>}
        </div>
      )}
    </div>
  );
}
