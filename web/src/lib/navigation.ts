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
 * The fitment catalog is the ₹50 plan. Billing stays open so an unpaid shop can
 * pay. Breadcrumbs and route guards read the same `need` codes.
 */
export const NAV_SECTIONS: NavSection[] = [
  {
    label: "Catalog",
    hint: "shared across shops",
    shared: "commons",
    items: [
      { to: "/commons", label: "Fitment catalog", icon: "globe", tint: "cyan", end: true, feature: "COMPATIBILITY" },
    ],
  },
  {
    label: "Workspace",
    items: [
      { to: "/billing", label: "Billing", icon: "card", tint: "green", need: "WORKSPACE_BILLING", allowUnpaid: true },
    ],
  },
];

const UNPAID_ALLOWED = new Set([
  ...NAV_SECTIONS.flatMap((section) => section.items.filter((item) => item.allowUnpaid).map((item) => item.to)),
  "/profile",
]);

export function allowedWhileUnpaid(pathname: string): boolean {
  return UNPAID_ALLOWED.has(pathname);
}

const EXTRA_LABELS: Record<string, string> = {
  "/devices": "Devices",
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

export interface Crumb {
  label: string;
  to?: string;
}

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
    crumbs.push({ label, to: isLast ? undefined : accumulated });
  });

  return crumbs;
}
