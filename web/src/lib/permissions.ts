/**
 * Frontend mirror of the backend capability model.
 *
 * `PERMISSIONS` mirrors `com.fixflow.security.Permission` and the rows seeded
 * into the `permissions` table by Flyway `V1`/`V6`. `ROLES` mirrors the `roles`
 * table. Keeping the codes in one place means a nav entry, a route guard and a
 * button gate all agree on the same string.
 *
 * The backend remains the enforcement point — everything here is presentation.
 * Hiding a control the API would reject is a courtesy to the user, never a
 * security boundary.
 */

export const PERMISSIONS = [
  "INVENTORY_READ",
  "INVENTORY_WRITE",
  "INVENTORY_ADJUST",
  "CATALOG_READ",
  "CATALOG_WRITE",
  "SALES_READ",
  "SALES_WRITE",
  "SALES_VOID",
  "PURCHASE_READ",
  "PURCHASE_WRITE",
  "REPAIR_READ",
  "REPAIR_WRITE",
  "CUSTOMER_READ",
  "CUSTOMER_WRITE",
  "SUPPLIER_READ",
  "SUPPLIER_WRITE",
  "PRICING_READ",
  "PRICING_WRITE",
  "REPORT_READ",
  "REPORT_EXPORT",
  "USER_READ",
  "USER_WRITE",
  "USER_INVITE",
  "SETTINGS_READ",
  "SETTINGS_WRITE",
  "AUDIT_READ",
  "WORKSPACE_BILLING",
  "COMPATIBILITY_APPROVE",
] as const;

export type Permission = (typeof PERMISSIONS)[number];

/** Lower seniority wins. Mirrors `roles.seniority`. */
export const ROLES = {
  OWNER: { label: "Owner", seniority: 10, tint: "violet" },
  ADMIN: { label: "Admin", seniority: 15, tint: "rose" },
  MANAGER: { label: "Manager", seniority: 20, tint: "blue" },
  TECHNICIAN: { label: "Technician", seniority: 30, tint: "amber" },
  STAFF: { label: "Staff", seniority: 40, tint: "green" },
  VIEWER: { label: "Viewer", seniority: 50, tint: "slate" },
} as const satisfies Record<string, { label: string; seniority: number; tint: string }>;

export type RoleCode = keyof typeof ROLES;

export function roleMeta(code: string | undefined): { label: string; seniority: number; tint: string } {
  return ROLES[code as RoleCode] ?? { label: code ?? "Member", seniority: 99, tint: "slate" };
}

/** The most senior role held, used for the header badge and hierarchy checks. */
export function primaryRole(roles: string[] | undefined): string | undefined {
  if (!roles?.length) {
    return undefined;
  }
  return [...roles].sort((a, b) => roleMeta(a).seniority - roleMeta(b).seniority)[0];
}

/**
 * Route path → permission required to open it.
 *
 * Paths are matched longest-prefix-first, so `/devices/:id` inherits the
 * `/devices` entry. A route absent from this map is treated as requiring only a
 * session — add an entry rather than relying on that default for anything that
 * exposes workspace data.
 */
export const ROUTE_PERMISSIONS: Record<string, Permission> = {
  "/sales": "SALES_READ",
  "/repairs": "REPAIR_READ",
  "/inventory": "INVENTORY_READ",
  "/purchases": "PURCHASE_READ",
  "/customers": "CUSTOMER_READ",
  "/suppliers": "SUPPLIER_READ",
  "/devices": "CATALOG_READ",
  "/compatibility": "CATALOG_READ",
  "/import": "CATALOG_WRITE",
  "/reports": "REPORT_READ",
  "/movements": "INVENTORY_READ",
  "/audit": "AUDIT_READ",
  "/members": "USER_READ",
  "/users": "USER_READ",
  "/billing": "WORKSPACE_BILLING",
  "/health": "SETTINGS_READ",
  "/settings": "SETTINGS_READ",
};

/**
 * Routes reachable by any signed-in member of a workspace. Everything else
 * under a workspace is expected to declare a permission in `ROUTE_PERMISSIONS`;
 * see `routePermission`.
 */
const OPEN_ROUTES = new Set(["/", "/profile", "/notifications", "/support", "/workspaces", "/search"]);

/** `/admin` is platform-staff only and is gated on the `systemAdmin` flag, not a permission. */
export const PLATFORM_ADMIN_ROUTES = new Set(["/admin"]);

export function routePermission(pathname: string): Permission | null {
  if (OPEN_ROUTES.has(pathname)) {
    return null;
  }
  const match = Object.keys(ROUTE_PERMISSIONS)
    .filter((route) => pathname === route || pathname.startsWith(`${route}/`))
    .sort((a, b) => b.length - a.length)[0];
  return match ? ROUTE_PERMISSIONS[match] : null;
}

/** Human-readable grouping for the permission matrix in the access-control UI. */
export const PERMISSION_GROUPS: { label: string; permissions: Permission[] }[] = [
  { label: "Inventory", permissions: ["INVENTORY_READ", "INVENTORY_WRITE", "INVENTORY_ADJUST"] },
  { label: "Catalog", permissions: ["CATALOG_READ", "CATALOG_WRITE", "COMPATIBILITY_APPROVE"] },
  { label: "Sales", permissions: ["SALES_READ", "SALES_WRITE", "SALES_VOID"] },
  { label: "Purchasing", permissions: ["PURCHASE_READ", "PURCHASE_WRITE"] },
  { label: "Repairs", permissions: ["REPAIR_READ", "REPAIR_WRITE"] },
  { label: "Contacts", permissions: ["CUSTOMER_READ", "CUSTOMER_WRITE", "SUPPLIER_READ", "SUPPLIER_WRITE"] },
  { label: "Pricing", permissions: ["PRICING_READ", "PRICING_WRITE"] },
  { label: "Reporting", permissions: ["REPORT_READ", "REPORT_EXPORT"] },
  { label: "People", permissions: ["USER_READ", "USER_WRITE", "USER_INVITE"] },
  { label: "Workspace", permissions: ["SETTINGS_READ", "SETTINGS_WRITE", "AUDIT_READ", "WORKSPACE_BILLING"] },
];

const PERMISSION_LABELS: Partial<Record<Permission, string>> = {
  INVENTORY_ADJUST: "Adjust stock levels",
  SALES_VOID: "Void a sale",
  COMPATIBILITY_APPROVE: "Approve compatibility changes",
  WORKSPACE_BILLING: "Manage billing",
  USER_INVITE: "Invite people",
  USER_WRITE: "Add, edit, and approve people",
  REPORT_EXPORT: "Export reports",
};

export function permissionLabel(permission: Permission | string): string {
  const known = PERMISSION_LABELS[permission as Permission];
  if (known) {
    return known;
  }
  const [area, verb] = permission.split("_");
  const noun = area.charAt(0) + area.slice(1).toLowerCase();
  return verb === "READ" ? `View ${noun.toLowerCase()}` : `Edit ${noun.toLowerCase()}`;
}
