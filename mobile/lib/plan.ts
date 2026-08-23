import type { AuthUser } from "./api";

export function hasFeature(user: AuthUser | null | undefined, feature: string): boolean {
  return Boolean(user?.systemAdmin || user?.features?.includes(feature));
}

export function hasPermission(user: AuthUser | null | undefined, permission: string): boolean {
  return Boolean(user?.systemAdmin || user?.permissions?.includes(permission));
}
