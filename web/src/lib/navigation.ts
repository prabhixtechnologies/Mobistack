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
  /** Granted by Prabhix on the user, not by a shop role. */
  commonsReviewer?: boolean;
  /** Match the path exactly — needed for `/` so it isn't active everywhere. */
  end?: boolean;
  /** Still reachable while the workspace subscription is unpaid. */
  allowUnpaid?: boolean;
  /** Product feature that must be on the shop's paid plan. */
  feature?: string;
}

export interface NavSection {
  label: string;
  /** Static fallback when a live hint is not loaded yet. */
  hint?: string;
  /** Marks the two product halves: shared catalog vs private shop. */
  shared?: "commons" | "shop";
  items: NavItem[];
}

/**
 * The single description of primary navigation.
 *
 * Catalog facts are shared; shop operations are private to this workspace and gated by plan
 * features. Breadcrumbs and route guards read the same `need` codes.
 */
export const NAV_SECTIONS: NavSection[] = [
  {
    label: "Home",
    items: [
      { to: "/", label: "Home", icon: "home", tint: "rose", end: true },
    ],
  },
  {
    label: "Counter",
    hint: "sell and serve",
    shared: "shop",
    items: [
      { to: "/sales", label: "Sales", icon: "cart", tint: "green", need: "SALES_READ", feature: "SALES" },
      { to: "/customers", label: "Customers", icon: "users", tint: "blue", need: "CUSTOMER_READ", feature: "CUSTOMERS" },
    ],
  },
  {
    label: "Bench",
    hint: "repair queue",
    shared: "shop",
    items: [
      { to: "/repairs", label: "Repairs", icon: "wrench", tint: "amber", need: "REPAIR_READ", feature: "REPAIRS" },
    ],
  },
  {
    label: "Stock",
    hint: "this shop",
    shared: "shop",
    items: [
      { to: "/inventory", label: "Inventory", icon: "box", tint: "blue", need: "INVENTORY_READ", feature: "INVENTORY" },
      { to: "/purchases", label: "Purchases", icon: "truck", tint: "orange", need: "PURCHASE_READ", feature: "PURCHASES" },
      { to: "/suppliers", label: "Suppliers", icon: "store", tint: "cyan", need: "SUPPLIER_READ", feature: "SUPPLIERS" },
    ],
  },
  {
    label: "Catalog",
    hint: "shared across shops",
    shared: "commons",
    items: [
      { to: "/commons", label: "Shared fitment", icon: "globe", tint: "cyan", end: true, feature: "COMPATIBILITY" },
      { to: "/compatibility", label: "Shop notes", icon: "lock", tint: "slate", need: "CATALOG_READ" },
      { to: "/commons/standing", label: "Contributor standing", icon: "pulse", tint: "blue", allowUnpaid: true },
      {
        to: "/commons/review",
        label: "Catalog review",
        icon: "shield",
        tint: "amber",
        commonsReviewer: true,
        allowUnpaid: true,
      },
    ],
  },
  {
    label: "Insights",
    items: [
      { to: "/reports", label: "Reports", icon: "chart", tint: "green", need: "REPORT_READ", feature: "REPORTS" },
      { to: "/movements", label: "Stock activity", icon: "move", tint: "amber", need: "INVENTORY_READ", feature: "MOVEMENTS" },
      { to: "/audit", label: "Audit", icon: "shield", tint: "slate", need: "AUDIT_READ", feature: "AUDIT" },
    ],
  },
  {
    label: "Manage",
    items: [
      { to: "/members", label: "Team", icon: "people", tint: "blue", need: "USER_READ", feature: "MEMBERS" },
      { to: "/users", label: "Roles & access", icon: "key", tint: "rose", need: "USER_READ", feature: "MEMBERS" },
      { to: "/import", label: "Import data", icon: "upload", tint: "slate", need: "CATALOG_WRITE", feature: "IMPORT" },
      { to: "/workspaces", label: "Workspaces", icon: "grid", tint: "blue", allowUnpaid: true },
      { to: "/billing", label: "Billing", icon: "card", tint: "green", need: "WORKSPACE_BILLING", allowUnpaid: true },
      { to: "/settings", label: "Settings", icon: "settings", tint: "slate", need: "SETTINGS_READ", allowUnpaid: true },
      { to: "/health", label: "System health", icon: "pulse", tint: "cyan", need: "SETTINGS_READ", feature: "AUDIT" },
    ],
  },
  {
    label: "Support",
    items: [
      { to: "/notifications", label: "Notifications", icon: "bell", tint: "rose", need: "NOTIFICATION_READ", allowUnpaid: true },
      { to: "/support", label: "Support", icon: "chat", tint: "blue", need: "SUPPORT_READ", allowUnpaid: true },
    ],
  },
  {
    label: "Platform",
    items: [{ to: "/admin", label: "Platform console", icon: "crown", tint: "amber", platformAdmin: true }],
  },
];

const UNPAID_ALLOWED = new Set([
  ...NAV_SECTIONS.flatMap((section) => section.items.filter((item) => item.allowUnpaid).map((item) => item.to)),
  "/profile",
  "/commons/devices",
  "/commons/components",
]);

export function allowedWhileUnpaid(pathname: string): boolean {
  if (UNPAID_ALLOWED.has(pathname)) {
    return true;
  }
  return pathname.startsWith("/commons/");
}

const EXTRA_LABELS: Record<string, string> = {
  "/devices": "Devices",
  "/inventory/catalog-links": "Link catalog part",
  "/profile": "My profile",
  "/search": "Search",
  "/commons/devices": "Devices",
  "/commons/components": "Parts",
};

const NAV_LABELS: Record<string, string> = Object.fromEntries(
  NAV_SECTIONS.flatMap((section) => section.items.map((item) => [item.to, item.label])),
);

export function routeLabel(pathname: string): string | undefined {
  return NAV_LABELS[pathname] ?? EXTRA_LABELS[pathname];
}

/** Human context for the shell title and document title, including detail routes. */
export function routeContext(pathname: string): { title: string; section: string } {
  const direct = routeLabel(pathname);
  if (direct) {
    const section = NAV_SECTIONS.find((group) => group.items.some((item) => item.to === pathname))?.label ?? "MobiStack";
    return { title: direct, section };
  }
  if (pathname.startsWith("/commons/devices/")) return { title: "Shared device", section: "Catalog" };
  if (pathname.startsWith("/commons/components/")) return { title: "Shared part", section: "Catalog" };
  if (pathname.startsWith("/devices/")) return { title: "Shop device", section: "Stock" };
  if (pathname.startsWith("/compatibility/")) return { title: "Fitment category", section: "Catalog" };
  return { title: "MobiStack", section: "Shop operations" };
}

export interface Crumb {
  label: string;
  to?: string;
}

/** Intermediate URL segments that have no page of their own, and the page that lists them. */
const CRUMB_TARGETS: Record<string, string> = {
  "/commons/devices": "/commons",
  "/commons/components": "/commons",
  "/devices": "/inventory",
};

export function breadcrumbsFor(
  pathname: string,
  detailLabel?: string,
  options?: { homeTo?: string; homeLabel?: string },
): Crumb[] {
  const home = {
    label: options?.homeLabel ?? "Dashboard",
    to: options?.homeTo ?? "/",
  };

  if (pathname === home.to) {
    return [{ label: home.label }];
  }

  const segments = pathname.split("/").filter(Boolean);
  const crumbs: Crumb[] = [{ label: home.label, to: home.to }];
  let accumulated = "";

  segments.forEach((segment, index) => {
    accumulated += `/${segment}`;
    const isLast = index === segments.length - 1;
    const looksLikeId = /^[0-9a-f]{8}-|^\d+$/i.test(segment);
    const label = looksLikeId
      ? (detailLabel ?? "Details")
      : (routeLabel(accumulated) ?? segment.charAt(0).toUpperCase() + segment.slice(1));
    crumbs.push({ label, to: isLast ? undefined : (CRUMB_TARGETS[accumulated] ?? accumulated) });
  });

  return crumbs;
}
