import type { ApiError, AuthResponse, AuthenticatedUser, WorkspaceCard } from "./types";
import { getDeviceId } from "./device";

const API_ORIGIN = (import.meta.env.VITE_API_ORIGIN as string | undefined) ?? "";

const ACCESS = "fixflow.access";
const REFRESH = "fixflow.refresh";
const USER = "fixflow.user";
const WORKSPACES = "fixflow.workspaces";

export function getAccessToken(): string | null {
  return localStorage.getItem(ACCESS);
}

export function getStoredUser(): AuthenticatedUser | null {
  const raw = localStorage.getItem(USER);
  return raw ? (JSON.parse(raw) as AuthenticatedUser) : null;
}

export function getStoredWorkspaces(): WorkspaceCard[] {
  const raw = localStorage.getItem(WORKSPACES);
  return raw ? (JSON.parse(raw) as WorkspaceCard[]) : [];
}

export function persistSession(auth: AuthResponse): void {
  localStorage.setItem(ACCESS, auth.accessToken);
  localStorage.setItem(REFRESH, auth.refreshToken);
  localStorage.setItem(USER, JSON.stringify(auth.user));
  if (auth.workspaces) {
    localStorage.setItem(WORKSPACES, JSON.stringify(auth.workspaces));
  }
}

export function persistWorkspaces(workspaces: WorkspaceCard[]): void {
  localStorage.setItem(WORKSPACES, JSON.stringify(workspaces));
}

export function clearSession(): void {
  localStorage.removeItem(ACCESS);
  localStorage.removeItem(REFRESH);
  localStorage.removeItem(USER);
  localStorage.removeItem(WORKSPACES);
}

async function parseError(response: Response): Promise<never> {
  let payload: ApiError = { code: "HTTP_" + response.status, message: response.statusText };
  try {
    payload = (await response.json()) as ApiError;
  } catch {
    // keep the status text
  }
  throw Object.assign(new Error(payload.message), payload);
}

function withDevice(headers: Headers): Headers {
  headers.set("X-FixFlow-Device", getDeviceId());
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

  if (response.status === 401 && localStorage.getItem(REFRESH) && path !== "/api/v1/auth/refresh") {
    const refreshed = await fetch(`${API_ORIGIN}/api/v1/auth/refresh`, {
      method: "POST",
      headers: withDevice(new Headers({ "Content-Type": "application/json" })),
      body: JSON.stringify({ refreshToken: localStorage.getItem(REFRESH), deviceId: getDeviceId() }),
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
