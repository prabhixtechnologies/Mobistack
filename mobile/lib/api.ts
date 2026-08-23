import * as SecureStore from "expo-secure-store";
import { Platform } from "react-native";
import Constants from "expo-constants";
import { getDeviceId } from "./device";

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
  planName?: string | null;
  periodEnd?: string | null;
  emailVerified?: boolean;
  phoneVerified?: boolean;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  user: AuthUser;
  workspaces?: WorkspaceCard[];
  deviceId?: string;
}

export function selectedWorkspaceId(user: AuthUser | null | undefined): string | null {
  return user?.workspaceId ?? user?.shopId ?? null;
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
  await SecureStore.setItemAsync(REFRESH, auth.refreshToken);
  await SecureStore.setItemAsync(USER, JSON.stringify(auth.user));
  if (auth.workspaces) {
    await SecureStore.setItemAsync(WORKSPACES, JSON.stringify(auth.workspaces));
  }
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
    || path.startsWith("/api/v1/auth/reset-password");
  const token = anonymous ? null : await getAccessToken();
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }
  headers.set("X-MobiStack-Device", await getDeviceId());

  let response = await fetch(`${API_BASE}${path}`, { ...init, headers });
  if (response.status === 401 && !anonymous && path !== "/api/v1/auth/refresh") {
    try {
      const payload = (await response.clone().json()) as { code?: string };
      if (payload.code === "SESSION_REPLACED") {
        await clearSession();
        throw new Error(payload.code === "SESSION_REPLACED"
          ? "This account signed in on another screen."
          : "Session ended");
      }
    } catch (error) {
      if (error instanceof Error && error.message.includes("another screen")) {
        throw error;
      }
    }
    const refreshToken = await readStore(REFRESH, LEGACY_REFRESH);
    if (refreshToken) {
      const refreshed = await fetch(`${API_BASE}/api/v1/auth/refresh`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "X-MobiStack-Device": await getDeviceId(),
        },
        body: JSON.stringify({ refreshToken, deviceId: await getDeviceId() }),
      });
      if (refreshed.ok) {
        await persistSession((await refreshed.json()) as AuthResponse);
        headers.set("Authorization", `Bearer ${await getAccessToken()}`);
        response = await fetch(`${API_BASE}${path}`, { ...init, headers });
      } else {
        await clearSession();
      }
    }
  }
  if (!response.ok) {
    let message = response.statusText;
    try {
      const body = (await response.json()) as { message?: string };
      if (body.message) message = body.message;
    } catch {
      // keep status text
    }
    throw new Error(message);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}
