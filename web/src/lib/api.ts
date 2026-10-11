import type { ApiError, AuthResponse, AuthenticatedUser, WorkspaceCard } from "./types";
import { isAbortError } from "./abort";
import { getDeviceId } from "./device";
import { storeGet, storeRemove, storeSet } from "./storage";
import { IDENTITY_ISSUER } from "./config";
import { loginPathFor, safeAppPath } from "./safePath";
import { beginStepUp, isOidcEnabled } from "@prabhixtechnologies/oidc-client";
import "./oidc-config";

/**
 * Identity access tokens stay in memory. localStorage is readable to any XSS on
 * this origin; the session cookie on Identity is what survives a refresh.
 */
let accessTokenMemory: string | null = null;

function onAuthCallback(): boolean {
  return typeof window !== "undefined" && window.location.pathname.startsWith("/auth/callback");
}

const API_ORIGIN = (import.meta.env.VITE_API_ORIGIN as string | undefined) ?? "";

const ANONYMOUS_API = new Set<string>();

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
    : "MobiStack is temporarily unavailable. Try again shortly.",
};

const SIGN_IN_UNREACHABLE: ApiError = {
  code: "UNAVAILABLE",
  message: "You are still signed in, but MobiStack could not reach the network. Check the connection and try again.",
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
    // A proxy outage is plain text written for an operator (image tags, compose
    // profiles). Shop staff get the same sentence nginx already returns.
    if (response.status === 502 || response.status === 503 || response.status === 504) {
      payload = OFFLINE_API;
    } else if (raw.trim()) {
      payload = { code: "HTTP_" + response.status, message: raw.trim() };
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
  if (payload.code === "STEP_UP_REQUIRED") {
    askForFreshProof();
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

function endSession(reason: "session" | "expired", message?: string): void {
  const here = window.location.pathname;
  const search = window.location.search;
  clearSession();
  const security = message?.toLowerCase().includes("security update");
  if (security && !here.startsWith("/security-sign-in")) {
    const back = safeAppPath(`${here}${search}`);
    window.location.assign(back === "/" ? "/security-sign-in" : `/security-sign-in?return=${encodeURIComponent(back)}`);
    return;
  }
  if (!here.startsWith("/login")) {
    window.location.assign(loginPathFor(here, search, { reason }));
  }
}

/**
 * `signed-out` is the only answer that ends a session: the server looked at the credential and
 * refused it. `step-up` means the cookie is still valid and Identity wants a passkey or a
 * one-time code; the browser is sent there and the local session stays. A network that is still
 * waking up, a deploy, or a rate limit is `unavailable`, and the sign-in survives it.
 */
export type RenewResult = "ok" | "signed-out" | "step-up" | "unavailable";

let stepUpStarted = false;

function askForFreshProof(): void {
  if (stepUpStarted || !isOidcEnabled() || onAuthCallback()) {
    return;
  }
  stepUpStarted = true;
  void beginStepUp(`${window.location.pathname}${window.location.search}`);
}

async function responseCode(response: Response): Promise<string | null> {
  try {
    const payload = (await response.clone().json()) as { code?: unknown };
    return typeof payload.code === "string" ? payload.code : null;
  } catch {
    return null;
  }
}

const RETRY_DELAYS_MS = [0, 1500, 4000, 8000];

function refusedCredential(status: number): boolean {
  return status === 400 || status === 401 || status === 403;
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, ms));
}

function waitForNetwork(limitMs: number): Promise<void> {
  if (typeof navigator === "undefined" || navigator.onLine) {
    return Promise.resolve();
  }
  return new Promise((resolve) => {
    const done = () => {
      window.removeEventListener("online", done);
      window.clearTimeout(timer);
      resolve();
    };
    const timer = window.setTimeout(done, limitMs);
    window.addEventListener("online", done);
  });
}

async function renewOnce(): Promise<RenewResult> {
  let response: Response;
  try {
    response = isOidcEnabled()
      ? await fetch(`${IDENTITY_ISSUER}/api/v1/identity/auth/session/token`, {
          method: "POST",
          credentials: "include",
          headers: { "Content-Type": "application/json" },
        })
      : await (async () => {
          const refreshToken = getRefreshToken();
          if (!refreshToken) {
            return new Response(null, { status: 401 });
          }
          return fetch(`${API_ORIGIN}/api/v1/mobistack/auth/refresh`, {
            method: "POST",
            headers: withDevice(new Headers({ "Content-Type": "application/json" })),
            body: JSON.stringify({ refreshToken, deviceId: getDeviceId() }),
          });
        })();
  } catch {
    return "unavailable";
  }
  if (response.status === 403 && (await responseCode(response)) === "STEP_UP_REQUIRED") {
    askForFreshProof();
    return "step-up";
  }
  if (refusedCredential(response.status)) {
    return "signed-out";
  }
  if (!response.ok) {
    return "unavailable";
  }
  try {
    if (isOidcEnabled()) {
      const payload = (await response.json()) as { accessToken?: string; access_token?: string };
      const access = payload.accessToken ?? payload.access_token;
      if (!access) {
        return "unavailable";
      }
      persistAccessToken(access);
    } else {
      persistSession((await response.json()) as AuthResponse);
    }
    return "ok";
  } catch {
    return "unavailable";
  }
}

/**
 * Shared renewal, so a page whose requests all expire together performs one
 * exchange instead of one per request.
 */
let renewInFlight: Promise<RenewResult> | null = null;

export function renewSession(): Promise<RenewResult> {
  if (!renewInFlight) {
    renewInFlight = (async () => {
      let result: RenewResult = "unavailable";
      for (const delay of RETRY_DELAYS_MS) {
        if (delay > 0) {
          await sleep(delay);
        }
        await waitForNetwork(15000);
        result = await renewOnce();
        if (result !== "unavailable") {
          return result;
        }
      }
      return result;
    })().finally(() => {
      renewInFlight = null;
    });
  }
  return renewInFlight;
}

function tokenExpiresAt(token: string | null): number | null {
  if (!token) {
    return null;
  }
  try {
    const part = token.split(".")[1];
    const json = JSON.parse(atob(part.replace(/-/g, "+").replace(/_/g, "/"))) as { exp?: number };
    return typeof json.exp === "number" ? json.exp * 1000 : null;
  } catch {
    return null;
  }
}

/**
 * Renews the short access token before it lapses and as soon as a sleeping tab wakes, so coming
 * back to the laptop never starts with a refused request.
 */
export function startSessionKeepAlive(): () => void {
  const check = () => {
    if (document.hidden) {
      return;
    }
    const token = getAccessToken();
    if (!token) {
      return;
    }
    const expires = tokenExpiresAt(token);
    if (expires !== null && expires - Date.now() > 120_000) {
      return;
    }
    void renewSession().then((result) => {
      if (result === "signed-out") {
        endSession("expired");
      }
    });
  };
  const timer = window.setInterval(check, 60_000);
  document.addEventListener("visibilitychange", check);
  window.addEventListener("online", check);
  window.addEventListener("focus", check);
  return () => {
    window.clearInterval(timer);
    document.removeEventListener("visibilitychange", check);
    window.removeEventListener("online", check);
    window.removeEventListener("focus", check);
  };
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
    let expiryMessage: string | undefined;
    try {
      const payload = (await response.clone().json()) as ApiError;
      expiryMessage = payload.message;
      if (payload.code === "SESSION_REPLACED") {
        endSession("session", expiryMessage);
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
    const renewed: RenewResult = current && current !== token ? "ok" : await renewSession();
    if (renewed === "ok") {
      headers.set("Authorization", `Bearer ${getAccessToken()}`);
      try {
        response = await fetch(`${API_ORIGIN}${path}`, { ...init, headers });
      } catch (error) {
        if (isAbortError(error)) {
          throw error;
        }
        throw Object.assign(new Error(OFFLINE_API.message), OFFLINE_API);
      }
    } else if (renewed === "signed-out") {
      endSession("expired", expiryMessage);
      await parseError(response);
    } else if (renewed === "step-up") {
      throw Object.assign(new Error("Confirm it is you, then try this again."), {
        code: "STEP_UP_REQUIRED",
      });
    } else {
      throw Object.assign(new Error(SIGN_IN_UNREACHABLE.message), SIGN_IN_UNREACHABLE);
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
