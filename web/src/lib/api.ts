import type { ApiError, AuthResponse, AuthenticatedUser, WorkspaceCard } from "./types";
import { getDeviceId } from "./device";
import { storeGet, storeRemove, storeSet } from "./storage";

const API_ORIGIN = (import.meta.env.VITE_API_ORIGIN as string | undefined) ?? "";

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
  storeSet("refresh", auth.refreshToken);
  storeSet("user", JSON.stringify(auth.user));
  if (auth.workspaces) {
    storeSet("workspaces", JSON.stringify(auth.workspaces));
  }
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

async function parseError(response: Response): Promise<never> {
  let payload: ApiError = { code: "HTTP_" + response.status, message: response.statusText };
  try {
    payload = (await response.json()) as ApiError;
  } catch {
    if (response.status === 502 || response.status === 503 || response.status === 504) {
      payload = {
        code: "UNAVAILABLE",
        message: "MobiStack is temporarily unavailable. Try again shortly.",
      };
    }
  }
  throw Object.assign(new Error(payload.message), payload);
}

function withDevice(headers: Headers): Headers {
  headers.set("X-MobiStack-Device", getDeviceId());
  return headers;
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = withDevice(new Headers(init.headers));
  if (init.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  const token = getAccessToken();
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }

  let response = await fetch(`${API_ORIGIN}${path}`, { ...init, headers });

  if (response.status === 401 && getRefreshToken() && path !== "/api/v1/auth/refresh") {
    const refreshed = await fetch(`${API_ORIGIN}/api/v1/auth/refresh`, {
      method: "POST",
      headers: withDevice(new Headers({ "Content-Type": "application/json" })),
      body: JSON.stringify({ refreshToken: getRefreshToken(), deviceId: getDeviceId() }),
    });
    if (refreshed.ok) {
      persistSession((await refreshed.json()) as AuthResponse);
      headers.set("Authorization", `Bearer ${getAccessToken()}`);
      response = await fetch(`${API_ORIGIN}${path}`, { ...init, headers });
    } else {
      clearSession();
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

export const money = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  maximumFractionDigits: 0,
});

export const qty = new Intl.NumberFormat("en-IN");
