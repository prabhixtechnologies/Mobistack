import type { NavIconName } from "../ui/navIcons";
import type { Permission } from "./permissions";

export type Tint = "rose" | "green" | "amber" | "blue" | "violet" | "cyan" | "slate" | "orange";

/** One page inside a destination. Gates are evaluated per tab, so a role sees only its own tabs. */
export interface NavTab {
  to: string;
  label: string;
  /** Page and breadcrumb title when the tab label alone is too short, e.g. "Stock" → "Inventory". */
  title?: string;
  /** Tab is hidden unless the viewer holds this capability. */
  need?: Permission;
  /** Granted by Prabhix on the user, not by a shop role. */
  commonsReviewer?: boolean;
  /** Still reachable while the workspace subscription is unpaid. */
  allowUnpaid?: boolean;
  /** Product feature that must be on the shop's paid plan. */
  feature?: string;
}

/**
 * A primary destination. The sidebar shows one link per destination, pointing at its first tab
 * the viewer can open; the shell renders the remaining tabs above the page.
 */
export interface NavDestination {
  id: string;
  label: string;
  icon: NavIconName;
  tint: Tint;
  tabs: NavTab[];
  /** Detail routes that belong here without being a tab, e.g. `/devices/:id` under Catalog. */
  owns?: string[];
}

/**
 * The single description of primary navigation, ordered by how often a shop opens each one.
 *
 * Records that support a workflow (customers, suppliers, purchases, stock activity) live as tabs
 * of that workflow instead of as sidebar rows. Breadcrumbs and route guards read the same `need`
 * codes.
 */
export const NAV_DESTINATIONS: NavDestination[] = [
  { id: "home", label: "Home", icon: "home", tint: "rose", tabs: [{ to: "/", label: "Home" }] },
  {
    id: "sell",
    label: "Sell",
    icon: "cart",
    tint: "green",
    tabs: [
      { to: "/sales", label: "Sales", need: "SALES_READ", feature: "SALES" },
      { to: "/customers", label: "Customers", need: "CUSTOMER_READ", feature: "CUSTOMERS" },
    ],
  },
  {
    id: "repairs",
    label: "Repairs",
    icon: "wrench",
    tint: "amber",
    tabs: [{ to: "/repairs", label: "Repairs", need: "REPAIR_READ", feature: "REPAIRS" }],
  },
  {
    id: "inventory",
    label: "Inventory",
    icon: "box",
    tint: "blue",
    tabs: [
      { to: "/inventory", label: "Stock", title: "Inventory", need: "INVENTORY_READ", feature: "INVENTORY" },
      { to: "/purchases", label: "Purchases", need: "PURCHASE_READ", feature: "PURCHASES" },
      { to: "/suppliers", label: "Suppliers", need: "SUPPLIER_READ", feature: "SUPPLIERS" },
      { to: "/movements", label: "Stock activity", need: "INVENTORY_READ", feature: "MOVEMENTS" },
    ],
  },
  {
    id: "catalog",
    label: "Catalog",
    icon: "globe",
    tint: "cyan",
    owns: ["/devices", "/compatibility"],
    tabs: [
      { to: "/commons", label: "Catalog", feature: "COMPATIBILITY" },
      { to: "/commons/standing", label: "Contributions", title: "Contributor standing", allowUnpaid: true },
      { to: "/commons/review", label: "Review queue", title: "Catalog review", commonsReviewer: true, allowUnpaid: true },
    ],
  },
  {
    id: "reports",
    label: "Reports",
    icon: "chart",
    tint: "green",
    tabs: [{ to: "/reports", label: "Reports", need: "REPORT_READ", feature: "REPORTS" }],
  },
];

/** Workspace administration: one sidebar link in the footer, its pages as tabs. */
export const SETTINGS_DESTINATION: NavDestination = {
  id: "settings",
  label: "Settings",
  icon: "settings",
  tint: "slate",
  tabs: [
    { to: "/settings", label: "Shop", title: "Settings", need: "SETTINGS_READ", allowUnpaid: true },
    { to: "/members", label: "Team", need: "USER_READ", feature: "MEMBERS" },
    { to: "/users", label: "Roles & access", need: "USER_READ", feature: "MEMBERS" },
    { to: "/billing", label: "Billing", need: "WORKSPACE_BILLING", allowUnpaid: true },
    { to: "/workspaces", label: "Workspaces", allowUnpaid: true },
    { to: "/import", label: "Import data", need: "CATALOG_WRITE", feature: "IMPORT" },
    { to: "/audit", label: "Audit", need: "AUDIT_READ", feature: "AUDIT" },
    { to: "/health", label: "System health", need: "SETTINGS_READ", feature: "AUDIT" },
  ],
};

const ALL_DESTINATIONS = [...NAV_DESTINATIONS, SETTINGS_DESTINATION];

/** Pages reached from the account menu rather than the sidebar. */
const ACCOUNT_LABELS: Record<string, string> = {
  "/notifications": "Notifications",
  "/support": "Support",
  "/admin": "Platform console",
  "/profile": "My profile",
};

function under(pathname: string, to: string): boolean {
  if (to === "/") return pathname === "/";
  return pathname === to || pathname.startsWith(`${to}/`);
}

/** The tab a path belongs to: the longest tab route that contains it. */
export function activeTab(destination: NavDestination, pathname: string): NavTab | undefined {
  return destination.tabs
    .filter((tab) => under(pathname, tab.to))
    .sort((a, b) => b.to.length - a.to.length)[0];
}

/** The destination a path belongs to, including detail routes it owns. */
export function destinationFor(pathname: string): NavDestination | undefined {
  return ALL_DESTINATIONS.find(
    (destination) =>
      activeTab(destination, pathname) !== undefined ||
      (destination.owns ?? []).some((prefix) => under(pathname, prefix)),
  );
}

const UNPAID_ALLOWED = new Set([
  ...ALL_DESTINATIONS.flatMap((destination) => destination.tabs.filter((tab) => tab.allowUnpaid).map((tab) => tab.to)),
  "/profile",
  "/notifications",
  "/support",
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
  ...ACCOUNT_LABELS,
  "/devices": "Devices",
  "/inventory/catalog-links": "Link catalog part",
  "/search": "Search",
  "/commons/devices": "Devices",
  "/commons/components": "Parts",
};

const NAV_LABELS: Record<string, string> = Object.fromEntries(
  ALL_DESTINATIONS.flatMap((destination) => destination.tabs.map((tab) => [tab.to, tab.title ?? tab.label])),
);

export function routeLabel(pathname: string): string | undefined {
  return NAV_LABELS[pathname] ?? EXTRA_LABELS[pathname];
}

/** Human context for the shell title and document title, including detail routes. */
export function routeContext(pathname: string): { title: string; section: string } {
  const direct = routeLabel(pathname);
  if (direct) {
    return { title: direct, section: destinationFor(pathname)?.label ?? "MobiStack" };
  }
  if (pathname.startsWith("/commons/devices/")) return { title: "Phone", section: "Catalog" };
  if (pathname.startsWith("/commons/components/")) return { title: "Family", section: "Catalog" };
  if (pathname.startsWith("/commons/categories/")) return { title: "Part type", section: "Catalog" };
  if (pathname.startsWith("/commons/brands/")) return { title: "Brand", section: "Catalog" };
  if (pathname.startsWith("/devices/")) return { title: "Shop phone", section: "Catalog" };
  if (pathname.startsWith("/compatibility")) return { title: "Catalog", section: "Catalog" };
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
};

export function breadcrumbsFor(
  pathname: string,
  detailLabel?: string,
  options?: { homeTo?: string; homeLabel?: string; deviceParent?: string },
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
    const target =
      accumulated === "/devices"
        ? (options?.deviceParent ?? "/commons")
        : (CRUMB_TARGETS[accumulated] ?? accumulated);
    crumbs.push({ label, to: isLast ? undefined : target });
  });

  return crumbs;
}
