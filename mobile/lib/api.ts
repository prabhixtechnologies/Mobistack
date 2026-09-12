import * as SecureStore from "expo-secure-store";
import { Platform } from "react-native";
import Constants from "expo-constants";
import { getDeviceId } from "./device";
import { setActiveScope } from "./db";
import { isOidcEnabled } from "./config";
import { refreshOidcTokens } from "./oidc";

const PRODUCTION_ORIGIN = "https://mobistack.prabhixtechnologies.com";

const ACCESS = "mobistack.access";
const REFRESH = "mobistack.refresh";
const USER = "mobistack.user";
const WORKSPACES = "mobistack.workspaces";
const LEGACY_ACCESS = "fixflow.access";
const LEGACY_REFRESH = "fixflow.refresh";
const LEGACY_USER = "fixflow.user";
const LEGACY_WORKSPACES = "fixflow.workspaces";

function hostFromExpo(): string {
  const host = Constants.expoConfig?.hostUri?.split(":")[0];
  if (host) {
    return `http://${host}:8080`;
  }
  return Platform.OS === "android" ? "http://10.0.2.2:8080" : "http://localhost:8080";
}

export const API_BASE = process.env.EXPO_PUBLIC_API_URL
  ?? (__DEV__ ? hostFromExpo() : PRODUCTION_ORIGIN);

export type MembershipStatus = "INVITED" | "PENDING" | "ACTIVE" | "SUSPENDED" | "REMOVED" | "REJECTED";

export interface WorkspaceCard {
  id: string;
  name: string;
  city?: string;
  joinCode?: string | null;
  role: string;
  status: MembershipStatus;
  memberCount: number;
  productCount: number;
  selected: boolean;
}

export interface AuthUser {
  id: string;
  shopId?: string | null;
  shopName?: string | null;
  workspaceId?: string | null;
  workspaceName?: string | null;
  fullName: string;
  email: string;
  roles: string[];
  permissions: string[];
  paymentRequired?: boolean;
  catalogOnly?: boolean;
  features?: string[];
  planCode?: string | null;
  planName?: string | null;
  periodEnd?: string | null;
  emailVerified?: boolean;
  phoneVerified?: boolean;
  phone?: string | null;
  systemAdmin?: boolean;
  mustChangePassword?: boolean;
}

export interface AuthResponse {
  accessToken: string;
  /** Absent on the Identity path: the OIDC refresh token is stored separately via persistOidcTokens. */
  refreshToken?: string;
  user: AuthUser;
  workspaces?: WorkspaceCard[];
  deviceId?: string;
}

export function selectedWorkspaceId(user: AuthUser | null | undefined): string | null {
  return user?.workspaceId ?? user?.shopId ?? null;
}

/**
 * A reply the server actually sent, as opposed to a request that never arrived.
 *
 * Callers used to see one undistinguished `Error` for both, so a shop whose plan
 * had lapsed was told it had no internet connection — and kept retrying a call
 * that would never succeed. Anything holding a status came back from the server.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly code?: string;

  constructor(message: string, status: number, code?: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
  }

  /** True when retrying cannot help: the server understood and said no. */
  get permanent(): boolean {
    return this.status >= 400 && this.status < 500 && this.status !== 408 && this.status !== 429;
  }
}

async function readStore(current: string, legacy: string): Promise<string | null> {
  return (await SecureStore.getItemAsync(current)) ?? (await SecureStore.getItemAsync(legacy));
}

export async function getAccessToken(): Promise<string | null> {
  return readStore(ACCESS, LEGACY_ACCESS);
}

export async function getStoredUser(): Promise<AuthUser | null> {
  const raw = await readStore(USER, LEGACY_USER);
  return raw ? (JSON.parse(raw) as AuthUser) : null;
}

export async function getStoredWorkspaces(): Promise<WorkspaceCard[]> {
  const raw = await readStore(WORKSPACES, LEGACY_WORKSPACES);
  return raw ? (JSON.parse(raw) as WorkspaceCard[]) : [];
}

export async function persistSession(auth: AuthResponse): Promise<void> {
  await SecureStore.setItemAsync(ACCESS, auth.accessToken);
  if (auth.refreshToken) {
    await SecureStore.setItemAsync(REFRESH, auth.refreshToken);
  }
  await SecureStore.setItemAsync(USER, JSON.stringify(auth.user));
  if (auth.workspaces) {
    await SecureStore.setItemAsync(WORKSPACES, JSON.stringify(auth.workspaces));
  }
  setActiveScope(auth.user?.id, selectedWorkspaceId(auth.user));
}

/** Identity access + refresh after OIDC; user/workspaces filled by a follow-up /me call. */
export async function persistOidcTokens(accessToken: string, refreshToken: string): Promise<void> {
  await SecureStore.setItemAsync(ACCESS, accessToken);
  await SecureStore.setItemAsync(REFRESH, refreshToken);
}

export async function persistUser(user: AuthUser): Promise<void> {
  await SecureStore.setItemAsync(USER, JSON.stringify(user));
  setActiveScope(user.id, selectedWorkspaceId(user));
}

export async function persistWorkspaces(workspaces: WorkspaceCard[]): Promise<void> {
  await SecureStore.setItemAsync(WORKSPACES, JSON.stringify(workspaces));
}

export async function clearSession(): Promise<void> {
  await SecureStore.deleteItemAsync(ACCESS);
  await SecureStore.deleteItemAsync(REFRESH);
  await SecureStore.deleteItemAsync(USER);
  await SecureStore.deleteItemAsync(WORKSPACES);
  await SecureStore.deleteItemAsync(LEGACY_ACCESS);
  await SecureStore.deleteItemAsync(LEGACY_REFRESH);
  await SecureStore.deleteItemAsync(LEGACY_USER);
  await SecureStore.deleteItemAsync(LEGACY_WORKSPACES);
  setActiveScope(null, null);
}

/**
 * Lets the auth provider react when the server ends a session mid-request.
 * Clearing storage alone left the app rendering the signed-in tabs while every
 * call failed.
 */
type SessionEndedListener = (reason: string) => void;
const sessionEndedListeners = new Set<SessionEndedListener>();

export function onSessionEnded(listener: SessionEndedListener): () => void {
  sessionEndedListeners.add(listener);
  return () => {
    sessionEndedListeners.delete(listener);
  };
}

async function endSession(reason: string): Promise<void> {
  await clearSession();
  for (const listener of sessionEndedListeners) {
    listener(reason);
  }
}

/**
 * Shared refresh. Refresh tokens are single-use, so several screens hitting a
 * expired token at once must rotate it once between them rather than racing.
 */
let refreshInFlight: Promise<boolean> | null = null;

function refreshSession(): Promise<boolean> {
  if (!refreshInFlight) {
    refreshInFlight = (async () => {
      const refreshToken = await readStore(REFRESH, LEGACY_REFRESH);
      if (!refreshToken) {
        return false;
      }
      try {
        if (isOidcEnabled()) {
          const tokens = await refreshOidcTokens(refreshToken);
          await persistOidcTokens(tokens.accessToken, tokens.refreshToken);
          return true;
        }
        const deviceId = await getDeviceId();
        const refreshed = await fetch(`${API_BASE}/api/v1/auth/refresh`, {
          method: "POST",
          headers: { "Content-Type": "application/json", "X-MobiStack-Device": deviceId },
          body: JSON.stringify({ refreshToken, deviceId }),
        });
        if (!refreshed.ok) {
          return false;
        }
        await persistSession((await refreshed.json()) as AuthResponse);
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
  const headers = new Headers(init.headers);
  if (init.body && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  const anonymous = path.startsWith("/api/v1/auth/login")
    || path.startsWith("/api/v1/auth/refresh")
    || path.startsWith("/api/v1/auth/register")
    || path.startsWith("/api/v1/auth/magic-link")
    || path.startsWith("/api/v1/auth/email-otp")
    || path.startsWith("/api/v1/auth/phone/")
    || path.startsWith("/api/v1/auth/whatsapp/")
    || path.startsWith("/api/v1/auth/forgot-password")
    || path.startsWith("/api/v1/auth/reset-password")
    || path.startsWith("/api/v1/auth/sso/");
  const token = anonymous ? null : await getAccessToken();
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }
  headers.set("X-MobiStack-Device", await getDeviceId());

  let response = await fetch(`${API_BASE}${path}`, { ...init, headers });
  if (response.status === 401 && !anonymous && path !== "/api/v1/auth/refresh") {
    let replaced = false;
    try {
      const payload = (await response.clone().json()) as { code?: string };
      replaced = payload.code === "SESSION_REPLACED";
    } catch {
      // No JSON body: treat as an ordinary expiry and try to refresh.
    }
    if (replaced) {
      await endSession("replaced");
      throw new Error("This phone was signed out, usually because the account is signed in on more devices than the shop allows. Please sign in again.");
    }
    // Another screen may have refreshed while this call was in flight.
    const current = await getAccessToken();
    const refreshed = current && current !== token ? true : await refreshSession();
    if (refreshed) {
      headers.set("Authorization", `Bearer ${await getAccessToken()}`);
      response = await fetch(`${API_BASE}${path}`, { ...init, headers });
    } else {
      await endSession("expired");
      throw new Error("Your session has expired. Please sign in again.");
    }
  }
  if (!response.ok) {
    let message = response.statusText;
    let code: string | undefined;
    try {
      const body = (await response.json()) as { message?: string; code?: string };
      if (body.message) message = body.message;
      code = body.code;
    } catch {
      // keep status text
    }
    throw new ApiError(message, response.status, code);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}
