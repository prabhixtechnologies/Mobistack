import type { NavIconName } from "../ui/navIcons";
import type { Permission } from "./permissions";

export type Tint = "rose" | "green" | "amber" | "blue" | "violet" | "cyan" | "slate" | "orange";

export interface NavItem {
  to: string;
  label: string;
  icon: NavIconName;
  tint: Tint;
  /** Item is hidden unless the viewer holds this capability. */
  need?: Permission;
  /** Item requires the platform-staff flag rather than a workspace capability. */
  platformAdmin?: boolean;
  /** Match the path exactly — needed for `/` so it isn't active everywhere. */
  end?: boolean;
  /** Still reachable while the workspace subscription is unpaid. */
  allowUnpaid?: boolean;
}

export interface NavSection {
  label: string;
  items: NavItem[];
}

/**
 * The single description of primary navigation.
 *
 * The sidebar renders it, breadcrumbs take their labels from it, and the route
 * guards resolve capabilities from the same `need` codes — so adding a screen in
 * one place cannot leave the others inconsistent.
 */
export const NAV_SECTIONS: NavSection[] = [
  {
    label: "Counter",
    items: [
      { to: "/", label: "Dashboard", icon: "home", tint: "rose", end: true },
      { to: "/sales", label: "Sales", icon: "cart", tint: "green", need: "SALES_READ" },
      { to: "/repairs", label: "Repairs", icon: "wrench", tint: "amber", need: "REPAIR_READ" },
      { to: "/inventory", label: "Inventory", icon: "box", tint: "blue", need: "INVENTORY_READ" },
      { to: "/purchases", label: "Purchases", icon: "truck", tint: "orange", need: "PURCHASE_READ" },
    ],
  },
  {
    label: "People",
    items: [
      { to: "/customers", label: "Customers", icon: "users", tint: "violet", need: "CUSTOMER_READ" },
      { to: "/suppliers", label: "Suppliers", icon: "store", tint: "cyan", need: "SUPPLIER_READ" },
      { to: "/members", label: "Members", icon: "people", tint: "blue", need: "USER_READ" },
      { to: "/users", label: "Access control", icon: "key", tint: "rose", need: "USER_READ" },
    ],
  },
  {
    label: "Catalog",
    items: [
      { to: "/compatibility", label: "Compatibility", icon: "link", tint: "violet", need: "CATALOG_READ" },
      { to: "/import", label: "Import", icon: "upload", tint: "slate", need: "CATALOG_WRITE" },
    ],
  },
  {
    label: "Insights",
    items: [
      { to: "/reports", label: "Reports", icon: "chart", tint: "green", need: "REPORT_READ" },
      { to: "/movements", label: "Movements", icon: "move", tint: "amber", need: "INVENTORY_READ" },
      { to: "/audit", label: "Audit", icon: "shield", tint: "slate", need: "AUDIT_READ" },
    ],
  },
  {
    label: "Workspace",
    items: [
      { to: "/workspaces", label: "Workspaces", icon: "grid", tint: "violet", allowUnpaid: true },
      { to: "/billing", label: "Billing", icon: "card", tint: "green", need: "WORKSPACE_BILLING", allowUnpaid: true },
      { to: "/health", label: "System health", icon: "pulse", tint: "cyan", need: "SETTINGS_READ" },
      { to: "/settings", label: "Settings", icon: "settings", tint: "slate", allowUnpaid: true },
    ],
  },
  {
    label: "Support",
    items: [
      { to: "/notifications", label: "Notifications", icon: "bell", tint: "rose", allowUnpaid: true },
      { to: "/support", label: "Support", icon: "chat", tint: "violet", allowUnpaid: true },
    ],
  },
  {
    label: "Platform",
    items: [{ to: "/admin", label: "Platform console", icon: "crown", tint: "amber", platformAdmin: true }],
  },
];

/**
 * Paths still reachable while a workspace subscription is unpaid.
 *
 * Derived from the nav catalog so a section marked `allowUnpaid` is automatically
 * permitted by the route guard too, plus the account-level screens that exist
 * outside the nav tree.
 */
const UNPAID_ALLOWED = new Set([
  ...NAV_SECTIONS.flatMap((section) => section.items.filter((item) => item.allowUnpaid).map((item) => item.to)),
  "/profile",
]);

export function allowedWhileUnpaid(pathname: string): boolean {
  return UNPAID_ALLOWED.has(pathname);
}

/** Labels for crumb segments that aren't top-level nav destinations. */
const EXTRA_LABELS: Record<string, string> = {
  "/devices": "Devices",
  "/profile": "My profile",
  "/search": "Search",
};

const NAV_LABELS: Record<string, string> = Object.fromEntries(
  NAV_SECTIONS.flatMap((section) => section.items.map((item) => [item.to, item.label])),
);

export function routeLabel(pathname: string): string | undefined {
  return NAV_LABELS[pathname] ?? EXTRA_LABELS[pathname];
}

export interface Crumb {
  label: string;
  to?: string;
}

/**
 * Builds the crumb trail for a pathname.
 *
 * Segments that look like ids (uuid or numeric) collapse into a `detailLabel`
 * supplied by the page, so `/devices/9f3c…` reads "Dashboard / Devices / iPhone 13"
 * rather than leaking a raw key into the UI.
 */
export function breadcrumbsFor(pathname: string, detailLabel?: string): Crumb[] {
  if (pathname === "/") {
    return [{ label: "Dashboard" }];
  }

  const segments = pathname.split("/").filter(Boolean);
  const crumbs: Crumb[] = [{ label: "Dashboard", to: "/" }];
  let accumulated = "";

  segments.forEach((segment, index) => {
    accumulated += `/${segment}`;
    const isLast = index === segments.length - 1;
    const looksLikeId = /^[0-9a-f]{8}-|^\d+$/i.test(segment);
    const label = looksLikeId
      ? (detailLabel ?? "Details")
      : (routeLabel(accumulated) ?? segment.charAt(0).toUpperCase() + segment.slice(1));
    crumbs.push({ label, to: isLast ? undefined : accumulated });
  });

  return crumbs;
}
