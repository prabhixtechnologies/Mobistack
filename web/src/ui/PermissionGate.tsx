import type { ReactNode } from "react";
import { Navigate, Outlet, useLocation } from "react-router-dom";
import { useAccess } from "../lib/access";
import { useAuth } from "../lib/auth";
import { allowedWhileUnpaid } from "../lib/navigation";
import type { Permission } from "../lib/permissions";
import { routePermission } from "../lib/permissions";
import { ForbiddenPage } from "../pages/ForbiddenPage";

interface GateProps {
  /** Single capability the caller must hold. */
  need?: Permission;
  /** Caller must hold at least one of these. */
  anyOf?: Permission[];
  /** Caller must hold every one of these. */
  allOf?: Permission[];
  /** Rendered instead of `children` when the check fails. Defaults to nothing. */
  fallback?: ReactNode;
  children: ReactNode;
}

/**
 * Hides UI the API would reject.
 *
 * Use for action controls — a "Void sale" button, a destructive menu item, a
 * column holding cost prices. For whole routes prefer `RequirePermission`, which
 * explains the denial instead of rendering a blank page.
 */
export function PermissionGate({ need, anyOf, allOf, fallback = null, children }: GateProps) {
  const access = useAccess();
  const checks = [
    need ? access.has(need) : true,
    anyOf?.length ? access.hasAny(...anyOf) : true,
    allOf?.length ? access.hasAll(...allOf) : true,
  ];
  return <>{checks.every(Boolean) ? children : fallback}</>;
}

/**
 * Route-level guard for everything inside a workspace.
 *
 * Runs the subscription check before the capability check: an unpaid workspace is
 * redirected to Billing rather than shown a permission error, which would be a
 * misleading reason for the block. The capability itself is resolved from
 * `ROUTE_PERMISSIONS` against the live pathname, so each route declares its
 * requirement once in the catalog instead of at every `<Route>`.
 */
export function RequirePermission({ need }: { need?: Permission }) {
  const access = useAccess();
  const { user } = useAuth();
  const { pathname } = useLocation();

  if (user?.paymentRequired && !allowedWhileUnpaid(pathname)) {
    // Only someone who can actually pay is sent to Billing; everyone else would
    // just bounce into a second denial there, so tell them who to ask instead.
    return access.has("WORKSPACE_BILLING") ? (
      <Navigate to="/billing?activate=1" replace />
    ) : (
      <ForbiddenPage reason="unpaid" />
    );
  }

  const required = need ?? routePermission(pathname);
  if (required && !access.has(required)) {
    return <ForbiddenPage required={required} />;
  }
  return <Outlet />;
}

/** Guards `/admin`, which turns on the platform-staff flag rather than a capability. */
export function RequirePlatformAdmin() {
  const access = useAccess();
  return access.isPlatformAdmin ? <Outlet /> : <ForbiddenPage reason="platform" />;
}
