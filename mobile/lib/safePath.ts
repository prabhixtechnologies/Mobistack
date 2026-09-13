/**
 * Paths from inbox items and push payloads must stay inside this app.
 *
 * A crafted `link` still must not become `//evil.example` or `https://…`
 * when we `router.push(link)`.
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
  if (trimmed.startsWith("/login") || trimmed.startsWith("/auth/callback")) {
    return fallback;
  }
  return trimmed;
}
