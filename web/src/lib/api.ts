import type { ApiError, AuthResponse, AuthenticatedUser, WorkspaceCard } from "./types";
import { getDeviceId } from "./device";
import { storeGet, storeRemove, storeSet } from "./storage";
import { IDENTITY_ISSUER } from "./config";
import { isOidcEnabled } from "./oidc";

const API_ORIGIN = (import.meta.env.VITE_API_ORIGIN as string | undefined) ?? "";

const ANONYMOUS_API = new Set([
  "/api/v1/auth/login",
  "/api/v1/auth/refresh",
  "/api/v1/auth/register",
  "/api/v1/auth/register-shop",
  "/api/v1/auth/forgot-password",
  "/api/v1/auth/reset-password",
  "/api/v1/auth/request-otp",
  "/api/v1/auth/verify-otp",
  "/api/v1/auth/methods",
  "/api/v1/auth/magic-link",
  "/api/v1/auth/magic-link/consume",
  "/api/v1/auth/email-otp",
  "/api/v1/auth/email-otp/verify",
  "/api/v1/auth/phone/start",
  "/api/v1/auth/phone/verify",
  "/api/v1/auth/whatsapp/start",
  "/api/v1/auth/whatsapp/verify",
  "/api/v1/auth/sso/google/start",
  "/api/v1/auth/sso/google",
]);

function pathOnly(path: string): string {
  const cut = path.indexOf("?");
  return cut === -1 ? path : path.slice(0, cut);
}

export function getAccessToken(): string | null {
  return storeGet("access");
}

export function getRefreshToken(): string | null {
  return storeGet("refresh");
}

export function getStoredUser(): AuthenticatedUser | null {
  const raw = storeGet("user");
  return raw ? (JSON.parse(raw) as AuthenticatedUser) : null;
}

export function getStoredWorkspaces(): WorkspaceCard[] {
  const raw = storeGet("workspaces");
  return raw ? (JSON.parse(raw) as WorkspaceCard[]) : [];
}

export function persistSession(auth: AuthResponse): void {
  storeSet("access", auth.accessToken);
  if (auth.refreshToken) {
    storeSet("refresh", auth.refreshToken);
  } else {
    storeRemove("refresh");
  }
  storeSet("user", JSON.stringify(auth.user));
  if (auth.workspaces) {
    storeSet("workspaces", JSON.stringify(auth.workspaces));
  }
}

/** Identity path: store an access token without a MobiStack refresh token. */
export function persistAccessToken(accessToken: string): void {
  storeSet("access", accessToken);
  storeRemove("refresh");
}

export function persistUser(user: AuthenticatedUser): void {
  storeSet("user", JSON.stringify(user));
}

export function persistWorkspaces(workspaces: WorkspaceCard[]): void {
  storeSet("workspaces", JSON.stringify(workspaces));
}

export function clearSession(): void {
  storeRemove("access", "refresh", "user", "workspaces");
}

const API_HINT = API_ORIGIN || "http://localhost:8082";

const OFFLINE_API: ApiError = {
  code: "UNAVAILABLE",
  message: `The API is not running. Start the backend on ${API_HINT}, then try again.`,
};

async function parseError(response: Response): Promise<never> {
  let payload: ApiError = { code: "HTTP_" + response.status, message: response.statusText };
  try {
    payload = (await response.json()) as ApiError;
  } catch {
    if (
      response.status === 500 ||
      response.status === 502 ||
      response.status === 503 ||
      response.status === 504
    ) {
      payload = OFFLINE_API;
    }
  }
  if (!payload.message || payload.message === "Internal Server Error") {
    payload = OFFLINE_API;
  }
  throw Object.assign(new Error(payload.message), payload);
}

function withDevice(headers: Headers): Headers {
  headers.set("X-MobiStack-Device", getDeviceId());
  return headers;
}

function endSession(reason: "session" | "expired"): void {
  clearSession();
  if (!window.location.pathname.startsWith("/login")) {
    window.location.assign(`/login?reason=${reason}`);
  }
}

/**
 * Shared refresh, so a page whose requests all expire together performs one
 * rotation instead of one per request.
 */
let refreshInFlight: Promise<boolean> | null = null;

function refreshSession(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      if (isOidcEnabled()) {
        try {
          const response = await fetch(`${IDENTITY_ISSUER}/api/v1/auth/session/token`, {
            method: "POST",
            credentials: "include",
            headers: { "Content-Type": "application/json" },
          });
          if (!response.ok) {
            return false;
          }
          const payload = (await response.json()) as {
            accessToken?: string;
            access_token?: string;
          };
          const access = payload.accessToken ?? payload.access_token;
          if (!access) {
            return false;
          }
          persistAccessToken(access);
          return true;
        } catch {
          return false;
        }
      }

      const refreshToken = getRefreshToken();
      if (!refreshToken) {
        return false;
      }
      try {
        const response = await fetch(`${API_ORIGIN}/api/v1/auth/refresh`, {
          method: "POST",
          headers: withDevice(new Headers({ "Content-Type": "application/json" })),
          body: JSON.stringify({ refreshToken, deviceId: getDeviceId() }),
        });
        if (!response.ok) {
          return false;
        }
        persistSession((await response.json()) as AuthResponse);
        return true;
      } catch {
        return false;
      }
    })().finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = withDevice(new Headers(init.headers));
  if (init.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  const anonymous = ANONYMOUS_API.has(pathOnly(path));
  const token = anonymous ? null : getAccessToken();
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  let response: Response;
  try {
    response = await fetch(`${API_ORIGIN}${path}`, { ...init, headers });
  } catch {
    throw Object.assign(new Error(OFFLINE_API.message), OFFLINE_API);
  }

  const canRefresh = isOidcEnabled() || Boolean(getRefreshToken());
  if (response.status === 401 && !anonymous && canRefresh && path !== "/api/v1/auth/refresh") {
    let sessionExpired = true;
    try {
      const payload = (await response.clone().json()) as ApiError;
      if (payload.code === "SESSION_REPLACED") {
        endSession("session");
        await parseError(response);
      }
      sessionExpired = !payload.code || payload.code === "UNAUTHENTICATED"
        || payload.code === "TOKEN_EXPIRED" || payload.code === "TOKEN_INVALID";
    } catch (error) {
      if (error instanceof Error && "code" in error) {
        throw error;
      }
      sessionExpired = true;
    }
    if (!sessionExpired) {
      await parseError(response);
    }
    const current = getAccessToken();
    const refreshed = current && current !== token ? true : await refreshSession();
    if (refreshed) {
      headers.set("Authorization", `Bearer ${getAccessToken()}`);
      response = await fetch(`${API_ORIGIN}${path}`, { ...init, headers });
    } else {
      endSession("expired");
      await parseError(response);
    }
  }

  if (!response.ok) {
    await parseError(response);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

export async function apiText(path: string, init: RequestInit = {}): Promise<string> {
  const headers = new Headers(init.headers);
  const token = getAccessToken();
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }
  const response = await fetch(`${API_ORIGIN}${path}`, { ...init, headers: withDevice(headers) });
  if (!response.ok) {
    await parseError(response);
  }
  return response.text();
}

/**
 * POST whose retry the server will collapse into the original.
 */
export async function apiOnce<T>(path: string, body: unknown, key = crypto.randomUUID()): Promise<T> {
  return api<T>(path, {
    method: "POST",
    headers: { "Idempotency-Key": key },
    body: JSON.stringify(body),
  });
}

export const money = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  maximumFractionDigits: 0,
});

export const qty = new Intl.NumberFormat("en-IN");
