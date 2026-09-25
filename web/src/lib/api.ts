import type { ApiError, AuthResponse, AuthenticatedUser, WorkspaceCard } from "./types";
import { isAbortError } from "./abort";
import { getDeviceId } from "./device";
import { storeGet, storeRemove, storeSet } from "./storage";
import { IDENTITY_ISSUER } from "./config";
import { isOidcEnabled } from "@prabhix/oidc-client";
import "./oidc-config";

/**
 * Identity access tokens stay in memory. localStorage is readable to any XSS on
 * this origin; the session cookie on Identity is what survives a refresh.
 */
let accessTokenMemory: string | null = null;

const API_ORIGIN = (import.meta.env.VITE_API_ORIGIN as string | undefined) ?? "";

const ANONYMOUS_API = new Set([
  "/api/v1/mobistack/auth/login",
  "/api/v1/mobistack/auth/refresh",
  "/api/v1/mobistack/auth/register",
  "/api/v1/mobistack/auth/register-shop",
  "/api/v1/mobistack/auth/forgot-password",
  "/api/v1/mobistack/auth/reset-password",
  "/api/v1/mobistack/auth/request-otp",
  "/api/v1/mobistack/auth/verify-otp",
  "/api/v1/mobistack/auth/methods",
  "/api/v1/mobistack/auth/magic-link",
  "/api/v1/mobistack/auth/magic-link/consume",
  "/api/v1/mobistack/auth/email-otp",
  "/api/v1/mobistack/auth/email-otp/verify",
  "/api/v1/mobistack/auth/phone/start",
  "/api/v1/mobistack/auth/phone/verify",
  "/api/v1/mobistack/auth/whatsapp/start",
  "/api/v1/mobistack/auth/whatsapp/verify",
  "/api/v1/mobistack/auth/sso/google/start",
  "/api/v1/mobistack/auth/sso/google",
]);

function pathOnly(path: string): string {
  const cut = path.indexOf("?");
  return cut === -1 ? path : path.slice(0, cut);
}

export function getAccessToken(): string | null {
  if (accessTokenMemory) {
    return accessTokenMemory;
  }
  const stored = storeGet("access");
  if (stored && isOidcEnabled()) {
    accessTokenMemory = stored;
    storeRemove("access", "refresh");
  }
  return stored;
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
  accessTokenMemory = auth.accessToken;
  if (isOidcEnabled() && !auth.refreshToken) {
    storeRemove("access", "refresh");
  } else {
    storeSet("access", auth.accessToken);
    if (auth.refreshToken) {
      storeSet("refresh", auth.refreshToken);
    } else {
      storeRemove("refresh");
    }
  }
  storeSet("user", JSON.stringify(auth.user));
  if (auth.workspaces) {
    storeSet("workspaces", JSON.stringify(auth.workspaces));
  }
}

/** Identity path: keep the access token in memory, not in localStorage. */
export function persistAccessToken(accessToken: string): void {
  accessTokenMemory = accessToken;
  storeRemove("access", "refresh");
}

export function persistUser(user: AuthenticatedUser): void {
  storeSet("user", JSON.stringify(user));
}

export function persistWorkspaces(workspaces: WorkspaceCard[]): void {
  storeSet("workspaces", JSON.stringify(workspaces));
}

export function clearSession(): void {
  accessTokenMemory = null;
  storeRemove("access", "refresh", "user", "workspaces");
}

const OFFLINE_API: ApiError = {
  code: "UNAVAILABLE",
  message: API_ORIGIN
    ? `The API is not running. Start the backend on ${API_ORIGIN}, then try again.`
    : "The API is not reachable. Check that mobistack-backend is healthy, then try again.",
};

async function parseError(response: Response): Promise<never> {
  const fallback = response.statusText?.trim() || `Request failed (${response.status})`;
  let payload: ApiError = { code: "HTTP_" + response.status, message: fallback };
  const raw = await response.text();
  try {
    if (raw.trim()) {
      payload = JSON.parse(raw) as ApiError;
    }
  } catch {
    if (raw.trim()) {
      // Spring CORS and a few other filters return plain text, not ApiError JSON.
      payload = { code: "HTTP_" + response.status, message: raw.trim() };
    } else if (response.status === 502 || response.status === 503 || response.status === 504) {
      payload = OFFLINE_API;
    }
  }
  if (!payload.message) {
    payload = {
      ...payload,
      message: response.status >= 500 ? OFFLINE_API.message : fallback,
    };
  } else if (payload.message === "Internal Server Error" && response.status >= 500) {
    payload = OFFLINE_API;
  }
  throw Object.assign(new Error(payload.message), payload);
}

const FITMENT_GROUP_KEY = "fitment.group";

export function getFitmentGroup(): string | null {
  const value = storeGet(FITMENT_GROUP_KEY);
  return value && value.length > 0 ? value : null;
}

export function setFitmentGroup(id: string): void {
  storeSet(FITMENT_GROUP_KEY, id);
}

function withDevice(headers: Headers): Headers {
  headers.set("X-MobiStack-Device", getDeviceId());
  const group = getFitmentGroup();
  if (group) {
    headers.set("X-Fitment-Group", group);
  }
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

export function refreshSession(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      if (isOidcEnabled()) {
        try {
          const response = await fetch(`${IDENTITY_ISSUER}/api/v1/identity/auth/session/token`, {
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
        const response = await fetch(`${API_ORIGIN}/api/v1/mobistack/auth/refresh`, {
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
  } catch (error) {
    if (isAbortError(error)) {
      throw error;
    }
    throw Object.assign(new Error(OFFLINE_API.message), OFFLINE_API);
  }

  const canRefresh = isOidcEnabled() || Boolean(getRefreshToken());
  if (response.status === 401 && !anonymous && canRefresh && path !== "/api/v1/mobistack/auth/refresh") {
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
