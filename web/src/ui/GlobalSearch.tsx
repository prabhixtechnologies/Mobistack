import { useEffect, useId, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { isAbortError } from "../lib/abort";
import { api } from "../lib/api";
import { useMediaQuery } from "../lib/media";
import type { DeviceSearchHit, GlobalSearchResponse, PartSearchHit } from "../lib/types";
import { Icon } from "./navIcons";

type Hit =
  | { kind: "device"; device: DeviceSearchHit }
  | { kind: "part"; part: PartSearchHit };

export function GlobalSearch() {
  const [query, setQuery] = useState("");
  const [open, setOpen] = useState(false);
  const [result, setResult] = useState<GlobalSearchResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [active, setActive] = useState(-1);
  const box = useRef<HTMLDivElement>(null);
  const input = useRef<HTMLInputElement>(null);
  const navigate = useNavigate();
  const compact = useMediaQuery("(max-width: 720px)");
  const listId = useId();

  const hits: Hit[] = result
    ? [
        ...result.devices.map((device) => ({ kind: "device" as const, device })),
        ...result.parts.map((part) => ({ kind: "part" as const, part })),
      ]
    : [];

  useEffect(() => {
    if (query.trim().length < 2) {
      setResult(null);
      setError(null);
      setActive(-1);
      return;
    }
    const controller = new AbortController();
    const handle = window.setTimeout(async () => {
      try {
        const data = await api<GlobalSearchResponse>(`/api/v1/search?q=${encodeURIComponent(query)}`, {
          signal: controller.signal,
        });
        setResult(data);
        setError(null);
        setOpen(true);
        setActive(-1);
      } catch (cause) {
        if (isAbortError(cause)) {
          return;
        }
        setResult(null);
        setError(cause instanceof Error ? cause.message : "Search is unavailable.");
        setOpen(true);
      }
    }, 160);
    return () => {
      window.clearTimeout(handle);
      controller.abort();
    };
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

  function go(hit: Hit): void {
    if (hit.kind === "device") {
      navigate(`/devices/${hit.device.id}`);
    } else {
      navigate(`/inventory?q=${encodeURIComponent(hit.part.sku)}`);
    }
    setOpen(false);
    setQuery("");
    setActive(-1);
  }

  function onKeyDown(event: React.KeyboardEvent<HTMLInputElement>): void {
    if (event.key === "Escape") {
      setOpen(false);
      return;
    }
    if (!open || hits.length === 0) {
      return;
    }
    if (event.key === "ArrowDown") {
      event.preventDefault();
      setActive((current) => (current + 1) % hits.length);
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      setActive((current) => (current <= 0 ? hits.length - 1 : current - 1));
    } else if (event.key === "Enter" && active >= 0 && hits[active]) {
      event.preventDefault();
      go(hits[active]);
    }
  }

  const activeId = active >= 0 ? `${listId}-hit-${active}` : undefined;

  return (
    <div className="search" ref={box} role="search">
      <Icon name="search" className="search-ico" />
      <input
        ref={input}
        value={query}
        placeholder={compact ? "Search…" : "Search a phone, SKU, barcode…"}
        aria-label="Search devices and parts"
        aria-autocomplete="list"
        aria-expanded={open}
        aria-controls={listId}
        aria-activedescendant={activeId}
        role="combobox"
        onChange={(event) => setQuery(event.target.value)}
        onFocus={() => (result || error) && setOpen(true)}
        onKeyDown={onKeyDown}
      />
      <kbd className="search-kbd">Ctrl K</kbd>
      {open && (result || error) && (
        <div className="search-panel" id={listId} role="listbox" aria-label="Search results">
          {error && <div className="empty">{error}</div>}
          {hits.map((hit, index) =>
            hit.kind === "device" ? (
              <button
                key={hit.device.id}
                id={`${listId}-hit-${index}`}
                className={`search-hit${index === active ? " search-hit--active" : ""}`}
                type="button"
                role="option"
                aria-selected={index === active}
                onMouseEnter={() => setActive(index)}
                onClick={() => go(hit)}
              >
                <strong>
                  {hit.device.brandName} {hit.device.name}
                </strong>
                <div className="faint">
                  Device
                  {hit.device.matchedAliases?.length
                    ? ` · also ${hit.device.matchedAliases.join(", ")}`
                    : ""}
                </div>
              </button>
            ) : (
              <button
                key={hit.part.variantId}
                id={`${listId}-hit-${index}`}
                className={`search-hit${index === active ? " search-hit--active" : ""}`}
                type="button"
                role="option"
                aria-selected={index === active}
                onMouseEnter={() => setActive(index)}
                onClick={() => go(hit)}
              >
                <strong>{hit.part.productName}</strong>
                <div className="faint">
                  {hit.part.variantName} · {hit.part.availableQty} in stock
                </div>
              </button>
            ),
          )}
          {result && result.totalResults === 0 && !error && (
            <div className="empty">Nothing matches “{result.query}”.</div>
          )}
        </div>
      )}
    </div>
  );
}
