/**
 * Paths remembered across the Identity redirect must stay inside this app.
 *
 * sessionStorage is same-origin, but a crafted value still must not become
 * `//evil.example` or `https://…` when we `navigate(returnTo)`.
 */
export function safeAppPath(path: string | null | undefined, fallback = "/"): string {
  if (!path) {
    return fallback;
  }
  const trimmed = path.trim();
  if (
    !trimmed.startsWith("/") ||
    trimmed.startsWith("//") ||
    trimmed.includes("://") ||
    trimmed.includes("\\") ||
    trimmed.includes("\0")
  ) {
    return fallback;
  }
  if (trimmed.startsWith("/login") || trimmed.startsWith("/auth/callback") || trimmed.startsWith("/security-sign-in")) {
    return fallback;
  }
  return trimmed;
}

/** Sign-in URL that comes back to the page that was open, rather than the dashboard. */
export function loginPathFor(pathname: string, search = "", extra?: Record<string, string>): string {
  const params = new URLSearchParams();
  if (extra) {
    for (const [key, value] of Object.entries(extra)) {
      if (value) params.set(key, value);
    }
  }
  const back = safeAppPath(`${pathname}${search}`);
  if (back !== "/") params.set("return", back);
  const query = params.toString();
  return query ? `/login?${query}` : "/login";
}

/** Page to open after Identity, taken from a `return` query. Defaults to the dashboard. */
export function returnPathFrom(search: string | URLSearchParams | null | undefined): string {
  const params = typeof search === "string"
    ? new URLSearchParams(search.startsWith("?") ? search.slice(1) : search)
    : search ?? new URLSearchParams();
  return safeAppPath(params.get("return"));
}
