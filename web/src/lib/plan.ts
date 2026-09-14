import type { AuthenticatedUser } from "./types";

export const PLAN_FEATURES = [
  { code: "COMPATIBILITY", label: "Compatibility" },
  { code: "DASHBOARD", label: "Dashboard" },
  { code: "SALES", label: "Sales" },
  { code: "REPAIRS", label: "Repairs" },
  { code: "INVENTORY", label: "Inventory" },
  { code: "PURCHASES", label: "Purchases" },
  { code: "CUSTOMERS", label: "Customers" },
  { code: "SUPPLIERS", label: "Suppliers" },
  { code: "MEMBERS", label: "People" },
  { code: "IMPORT", label: "Import" },
  { code: "REPORTS", label: "Reports" },
  { code: "MOVEMENTS", label: "Movements" },
  { code: "AUDIT", label: "Audit" },
] as const;

export type PlanFeatureCode = (typeof PLAN_FEATURES)[number]["code"];

export function userFeatures(user: Pick<AuthenticatedUser, "features" | "systemAdmin"> | null | undefined): string[] {
  return user?.features ?? [];
}

export function hasFeature(
  user: Pick<AuthenticatedUser, "features" | "systemAdmin"> | null | undefined,
  feature: string,
): boolean {
  return Boolean(user?.systemAdmin || user?.features?.includes(feature));
}

export function afterAuthPath(
  user: Pick<AuthenticatedUser, "paymentRequired" | "features" | "catalogOnly">,
): string {
  if (user.paymentRequired) {
    return "/billing?activate=1";
  }
  if (user.features?.includes("DASHBOARD")) {
    return "/";
  }
  return "/commons";
}

export function routeFeature(pathname: string): string | null {
  if (pathname === "/") {
    return "DASHBOARD";
  }
  if (
    pathname.startsWith("/commons") ||
    pathname.startsWith("/search") ||
    pathname.startsWith("/compatibility")
  ) {
    return null;
  }
  if (pathname.startsWith("/devices")) {
    return "COMPATIBILITY";
  }
  if (pathname.startsWith("/sales")) return "SALES";
  if (pathname.startsWith("/repairs")) return "REPAIRS";
  if (pathname.startsWith("/inventory")) return "INVENTORY";
  if (pathname.startsWith("/purchases")) return "PURCHASES";
  if (pathname.startsWith("/customers")) return "CUSTOMERS";
  if (pathname.startsWith("/suppliers")) return "SUPPLIERS";
  if (pathname.startsWith("/members") || pathname.startsWith("/users")) return "MEMBERS";
  if (pathname.startsWith("/import")) return "IMPORT";
  if (pathname.startsWith("/reports")) return "REPORTS";
  if (pathname.startsWith("/movements")) return "MOVEMENTS";
  if (pathname.startsWith("/audit") || pathname.startsWith("/health")) return "AUDIT";
  return null;
}

export function allowedOnPlan(pathname: string, features: string[] | undefined): boolean {
  const feature = routeFeature(pathname);
  if (!feature) {
    return true;
  }
  return Boolean(features?.includes(feature));
}
