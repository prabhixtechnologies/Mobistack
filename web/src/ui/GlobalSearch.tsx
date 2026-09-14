import { useEffect, useId, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useNavigate } from "react-router-dom";
import { isAbortError } from "../lib/abort";
import { api } from "../lib/api";
import { storeGet, storeSet } from "../lib/storage";
import { useMediaQuery } from "../lib/media";
import type { CommonsSearchHit, DeviceSearchHit, GlobalSearchResponse, PartSearchHit } from "../lib/types";
import { Icon } from "./navIcons";

type Hit =
  | { kind: "action"; to: string; title: string; hint: string }
  | { kind: "recent"; query: string }
  | { kind: "commons-device"; hit: CommonsSearchHit }
  | { kind: "commons-component"; hit: CommonsSearchHit }
  | { kind: "device"; device: DeviceSearchHit }
  | { kind: "part"; part: PartSearchHit };

const RECENT_KEY = "search.recent";
const OPEN_EVENT = "mobistack:command";
const SEARCH_KINDS = ["Phone", "SKU", "Barcode", "Customer", "Repair", "Sale"];
const ACTIONS: Extract<Hit, { kind: "action" }>[] = [
  { kind: "action", to: "/sales", title: "New sale", hint: "Take a payment" },
  { kind: "action", to: "/repairs", title: "Book a repair", hint: "Open a job card" },
  { kind: "action", to: "/commons", title: "Look up a phone", hint: "Shared catalog" },
  { kind: "action", to: "/inventory", title: "Open inventory", hint: "Stock and SKUs" },
  { kind: "action", to: "/customers", title: "Find a customer", hint: "People and numbers" },
];

export function openCommandPalette(): void {
  window.dispatchEvent(new Event(OPEN_EVENT));
}

function readRecent(): string[] {
  try {
    const raw = storeGet(RECENT_KEY);
    const parsed = raw ? (JSON.parse(raw) as unknown) : [];
    return Array.isArray(parsed) ? parsed.filter((row): row is string => typeof row === "string").slice(0, 6) : [];
  } catch {
    return [];
  }
}

function remember(query: string): void {
  const next = [query, ...readRecent().filter((row) => row !== query)].slice(0, 6);
  storeSet(RECENT_KEY, JSON.stringify(next));
}

export function GlobalSearch() {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [result, setResult] = useState<GlobalSearchResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [active, setActive] = useState(0);
  const [recent, setRecent] = useState<string[]>(readRecent);
  const input = useRef<HTMLInputElement>(null);
  const navigate = useNavigate();
  const compact = useMediaQuery("(max-width: 720px)");
  const listId = useId();

  const hits: Hit[] =
    query.trim().length < 2
      ? [...recent.map((row) => ({ kind: "recent" as const, query: row })), ...ACTIONS]
      : result
        ? [
            ...(result.commonsDevices ?? []).map((hit) => ({ kind: "commons-device" as const, hit })),
            ...(result.commonsComponents ?? []).map((hit) => ({ kind: "commons-component" as const, hit })),
            ...result.devices.map((device) => ({ kind: "device" as const, device })),
            ...result.parts.map((part) => ({ kind: "part" as const, part })),
          ]
        : [];

  useEffect(() => {
    if (!open || query.trim().length < 2) {
      setResult(null);
      setError(null);
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
        setActive(0);
      } catch (cause) {
        if (isAbortError(cause)) {
          return;
        }
        setResult(null);
        setError(cause instanceof Error ? cause.message : "Search is unavailable.");
      }
    }, 140);
    return () => {
      window.clearTimeout(handle);
      controller.abort();
    };
  }, [open, query]);

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "k") {
        event.preventDefault();
        setOpen(true);
      }
    };
    const onOpen = () => setOpen(true);
    document.addEventListener("keydown", onKey);
    window.addEventListener(OPEN_EVENT, onOpen);
    return () => {
      document.removeEventListener("keydown", onKey);
      window.removeEventListener(OPEN_EVENT, onOpen);
    };
  }, []);

  useEffect(() => {
    if (open) {
      setRecent(readRecent());
      setActive(0);
      window.setTimeout(() => input.current?.focus(), 0);
    } else {
      setQuery("");
      setResult(null);
      setError(null);
    }
  }, [open]);

  function go(hit: Hit): void {
    if (hit.kind === "action") {
      navigate(hit.to);
    } else if (hit.kind === "recent") {
      setQuery(hit.query);
      return;
    } else if (hit.kind === "commons-device") {
      remember(query.trim());
      navigate(`/commons/devices/${hit.hit.id}`);
    } else if (hit.kind === "commons-component") {
      remember(query.trim());
      navigate(`/commons/components/${hit.hit.id}`);
    } else if (hit.kind === "device") {
      remember(query.trim());
      navigate(`/devices/${hit.device.id}`);
    } else {
      remember(query.trim());
      navigate(`/inventory?q=${encodeURIComponent(hit.part.sku)}`);
    }
    setOpen(false);
  }

  function onKeyDown(event: React.KeyboardEvent<HTMLInputElement>): void {
    if (event.key === "Escape") {
      setOpen(false);
      return;
    }
    if (hits.length === 0) {
      return;
    }
    if (event.key === "ArrowDown") {
      event.preventDefault();
      setActive((current) => (current + 1) % hits.length);
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      setActive((current) => (current <= 0 ? hits.length - 1 : current - 1));
    } else if (event.key === "Enter" && hits[active]) {
      event.preventDefault();
      go(hits[active]);
    }
  }

  return (
    <>
      <button className="cmd-trigger" type="button" onClick={() => setOpen(true)} aria-label="Search phone, SKU, barcode, customer, or repair">
        <Icon name="search" className="search-ico" />
        <span>
          {compact ? "Search…" : (
            <>
              <em>Command</em>
              phone · SKU · barcode · customer · repair · sale
            </>
          )}
        </span>
        <kbd className="search-kbd">Ctrl K</kbd>
      </button>
      {open
        ? createPortal(
            <div
              className="cmd-scrim"
              onMouseDown={(event) => event.target === event.currentTarget && setOpen(false)}
            >
              <div className="cmd-panel" role="dialog" aria-modal="true" aria-label="Search">
                <label className="cmd-panel__field">
                  <Icon name="search" />
                  <input
                    ref={input}
                    value={query}
                    placeholder="Phone, SKU, barcode, customer, repair, sale…"
                    aria-autocomplete="list"
                    aria-controls={listId}
                    role="combobox"
                    aria-expanded
                    onChange={(event) => setQuery(event.target.value)}
                    onKeyDown={onKeyDown}
                  />
                  <kbd className="search-kbd">Esc</kbd>
                </label>
                <div id={listId} role="listbox">
                  {error && <div className="cmd-empty">{error}</div>}
                  {!error && query.trim().length < 2 && (
                    <>
                      <div className="cmd-hint" aria-hidden>
                        {SEARCH_KINDS.map((kind) => (
                          <b key={kind}>{kind}</b>
                        ))}
                      </div>
                      {recent.length > 0 && (
                        <div className="cmd-group">
                          <div className="cmd-group__label">Recent</div>
                          {hits
                            .filter((hit) => hit.kind === "recent")
                            .map((hit, index) => (
                              <button
                                key={`r-${hit.query}`}
                                className={`cmd-hit${index === active ? " cmd-hit--active" : ""}`}
                                type="button"
                                role="option"
                                aria-selected={index === active}
                                onMouseEnter={() => setActive(index)}
                                onClick={() => go(hit)}
                              >
                                <strong>{hit.query}</strong>
                                <small>Recent</small>
                              </button>
                            ))}
                        </div>
                      )}
                      <div className="cmd-group">
                        <div className="cmd-group__label">Actions</div>
                        {hits
                          .filter((hit) => hit.kind === "action")
                          .map((hit, index) => {
                            const offset = recent.length;
                            return (
                              <button
                                key={hit.to}
                                className={`cmd-hit${offset + index === active ? " cmd-hit--active" : ""}`}
                                type="button"
                                role="option"
                                aria-selected={offset + index === active}
                                onMouseEnter={() => setActive(offset + index)}
                                onClick={() => go(hit)}
                              >
                                <strong>{hit.title}</strong>
                                <small>{hit.hint}</small>
                              </button>
                            );
                          })}
                      </div>
                    </>
                  )}
                  {!error && query.trim().length >= 2 && hits.length === 0 && result && (
                    <div className="cmd-empty">Nothing matches “{result.query}”.</div>
                  )}
                  {!error &&
                    hits.map((hit, index) => {
                      if (hit.kind === "action" || hit.kind === "recent") {
                        return null;
                      }
                      const title =
                        hit.kind === "commons-device"
                          ? [hit.hit.brandName, hit.hit.name].filter(Boolean).join(" ")
                          : hit.kind === "commons-component"
                            ? hit.hit.name
                            : hit.kind === "device"
                              ? `${hit.device.brandName} ${hit.device.name}`
                              : hit.part.productName;
                      const hint =
                        hit.kind === "commons-device"
                          ? "Shared catalog · phone"
                          : hit.kind === "commons-component"
                            ? "Shared catalog · part"
                            : hit.kind === "device"
                              ? "Shop device"
                              : `${hit.part.sku} · ${hit.part.availableQty} in stock`;
                      return (
                        <button
                          key={`${hit.kind}-${index}`}
                          className={`cmd-hit${index === active ? " cmd-hit--active" : ""}`}
                          type="button"
                          role="option"
                          aria-selected={index === active}
                          onMouseEnter={() => setActive(index)}
                          onClick={() => go(hit)}
                        >
                          <strong>{title}</strong>
                          <small>{hint}</small>
                        </button>
                      );
                    })}
                </div>
              </div>
            </div>,
            document.body,
          )
        : null}
    </>
  );
}
