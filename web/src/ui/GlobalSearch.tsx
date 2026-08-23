import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../lib/api";
import type { GlobalSearchResponse } from "../lib/types";

export function GlobalSearch() {
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);
  const [result, setResult] = useState<GlobalSearchResponse | null>(null);
  const box = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();

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
    document.addEventListener("mousedown", onClick);
    return () => document.removeEventListener("mousedown", onClick);
  }, []);

  return (
    <div className="search" ref={box} style={{ position: "relative" }}>
      <span className="faint">⌘K</span>
      <input
        value={query}
        placeholder="Search Realme 6, SKU, barcode…"
        onChange={(event) => setQuery(event.target.value)}
        onFocus={() => result && setOpen(true)}
      />
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
