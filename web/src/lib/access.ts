import { useMemo } from "react";
import { useAuth } from "./auth";
import { primaryRole, roleMeta, routePermission, type Permission } from "./permissions";

export interface Access {
  /** Capability codes granted by the JWT for the selected workspace. */
  permissions: Set<string>;
  /** Most senior role held, e.g. `OWNER`. */
  role: string | undefined;
  roleLabel: string;
  roleTint: string;
  /** Platform staff flag — orthogonal to workspace permissions. */
  isPlatformAdmin: boolean;
  has: (permission: Permission) => boolean;
  hasAny: (...permissions: Permission[]) => boolean;
  hasAll: (...permissions: Permission[]) => boolean;
  /** Whether the current principal may open a route path. */
  canOpen: (pathname: string) => boolean;
}

/**
 * Reads the capability set the backend put in the JWT.
 *
 * OWNER is granted every permission by Flyway `V1`, so there is deliberately no
 * client-side super-user bypass: if a code is absent from the token the UI hides
 * the control, which keeps this hook honest about what the API will actually
 * allow.
 */
export function useAccess(): Access {
  const { user } = useAuth();

  return useMemo(() => {
    const permissions = new Set(user?.permissions ?? []);
    const role = primaryRole(user?.roles);
    const meta = roleMeta(role);
    const has = (permission: Permission) => permissions.has(permission);

    return {
      permissions,
      role,
      roleLabel: meta.label,
      roleTint: meta.tint,
      isPlatformAdmin: Boolean(user?.systemAdmin),
      has,
      hasAny: (...codes: Permission[]) => codes.some(has),
      hasAll: (...codes: Permission[]) => codes.every(has),
      canOpen: (pathname: string) => {
        const required = routePermission(pathname);
        return required === null || permissions.has(required);
      },
    };
  }, [user]);
}
