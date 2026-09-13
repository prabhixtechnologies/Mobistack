import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import {
  api,
  clearSession,
  getAccessToken,
  getRefreshToken,
  getStoredUser,
  getStoredWorkspaces,
  persistAccessToken,
  persistSession,
  persistUser,
  persistWorkspaces,
  refreshSession,
} from "./api";
import { getDeviceId } from "./device";
import { afterAuthPath } from "./plan";
import { beginLogout, isOidcEnabled } from "./oidc";
import type { AuthResponse, AuthenticatedUser, MyWorkspacesResponse, WorkspaceCard } from "./types";

interface AuthContextValue {
  user: AuthenticatedUser | null;
  workspaces: WorkspaceCard[];
  /** False while Identity cookie restore is in flight — do not flash the login page. */
  ready: boolean;
  login: (email: string, password: string) => Promise<AuthResponse>;
  loginWithTokens: (accessToken: string) => Promise<void>;
  acceptSession: (auth: AuthResponse) => void;
  logout: () => Promise<void>;
  switchWorkspace: (workspaceId: string) => Promise<void>;
  createWorkspace: (name: string, city?: string) => Promise<void>;
  joinWorkspace: (joinCode: string) => Promise<WorkspaceCard>;
  refreshWorkspaces: () => Promise<MyWorkspacesResponse>;
  refreshUser: () => Promise<AuthenticatedUser>;
  has: (permission: string) => boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function atOidcCallback(): boolean {
  return typeof window !== "undefined" && window.location.pathname === "/auth/callback";
}

function applySession(
  auth: AuthResponse,
  setUser: (user: AuthenticatedUser) => void,
  setWorkspaces: (workspaces: WorkspaceCard[]) => void,
): void {
  persistSession(auth);
  setUser(auth.user);
  if (auth.workspaces) {
    setWorkspaces(auth.workspaces);
  }
}

/**
 * After a workspace select/create while signed in through Identity.
 *
 * <p>The server still returns a freshly minted HS256 pair for legacy clients. Keeping those would
 * throw away the Identity access token and with it the shared session cookie refresh path. When
 * there is no product refresh token, we are on that path: keep the access token we already have and
 * only take the updated user / workspace list.
 */
function applyWorkspaceChange(
  auth: AuthResponse,
  setUser: (user: AuthenticatedUser) => void,
  setWorkspaces: (workspaces: WorkspaceCard[]) => void,
): void {
  if (isOidcEnabled() && !getRefreshToken()) {
    persistUser(auth.user);
    setUser(auth.user);
    if (auth.workspaces) {
      persistWorkspaces(auth.workspaces);
      setWorkspaces(auth.workspaces);
    }
    return;
  }
  applySession(auth, setUser, setWorkspaces);
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthenticatedUser | null>(getStoredUser);
  const [workspaces, setWorkspaces] = useState<WorkspaceCard[]>(getStoredWorkspaces);
  const [ready, setReady] = useState(() => atOidcCallback() || Boolean(getAccessToken()) || !isOidcEnabled());

  useEffect(() => {
    let cancelled = false;

    async function boot(): Promise<void> {
      if (atOidcCallback()) {
        return;
      }

      if (isOidcEnabled() && !getAccessToken()) {
        const restored = await refreshSession();
        if (!restored) {
          clearSession();
          if (!cancelled) {
            setUser(null);
            setWorkspaces([]);
            setReady(true);
          }
          return;
        }
      }

      if (!getAccessToken()) {
        if (!cancelled) {
          setReady(true);
        }
        return;
      }

      try {
        const me = await api<AuthenticatedUser>("/api/v1/auth/me");
        if (cancelled) {
          return;
        }
        persistUser(me);
        setUser(me);
      } catch {
        /* keep the cached session until the next API call */
      }

      try {
        const mine = await api<MyWorkspacesResponse>("/api/v1/workspaces");
        if (cancelled) {
          return;
        }
        persistWorkspaces(mine.workspaces);
        setWorkspaces(mine.workspaces);
      } catch {
        /* listing is optional until the user opens My Workspaces */
      }

      if (!cancelled) {
        setReady(true);
      }
    }

    void boot();
    return () => {
      cancelled = true;
    };
  }, []);

  const loginWithTokens = useCallback(async (accessToken: string) => {
    persistAccessToken(accessToken);
    const me = await api<AuthenticatedUser>("/api/v1/auth/me");
    persistUser(me);
    setUser(me);
    try {
      const mine = await api<MyWorkspacesResponse>("/api/v1/workspaces");
      persistWorkspaces(mine.workspaces);
      setWorkspaces(mine.workspaces);
    } catch {
      setWorkspaces([]);
    }
    setReady(true);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      workspaces,
      ready,
      async login(email, password) {
        clearSession();
        setUser(null);
        setWorkspaces([]);
        const auth = await api<AuthResponse>("/api/v1/auth/login", {
          method: "POST",
          body: JSON.stringify({
            email: email.trim(),
            password,
            deviceId: getDeviceId(),
          }),
        });
        applySession(auth, setUser, setWorkspaces);
        setReady(true);
        return auth;
      },
      loginWithTokens,
      acceptSession(auth) {
        applySession(auth, setUser, setWorkspaces);
        setReady(true);
      },
      async logout() {
        const refreshToken = getRefreshToken();
        try {
          if (refreshToken) {
            await api("/api/v1/auth/logout", {
              method: "POST",
              body: JSON.stringify({ refreshToken }),
            });
          }
        } finally {
          clearSession();
          setUser(null);
          setWorkspaces([]);
          if (isOidcEnabled()) {
            beginLogout();
          }
        }
      },
      async switchWorkspace(workspaceId) {
        const auth = await api<AuthResponse>(`/api/v1/workspaces/${workspaceId}/select`, {
          method: "POST",
        });
        // Under Identity the access token carries who you are, not which shop. The select call
        // updates the preferred workspace in the database; applying the response's tokens would
        // replace the Identity JWT with a product HS256 and break shared sign-out / refresh.
        applyWorkspaceChange(auth, setUser, setWorkspaces);
        window.location.assign(afterAuthPath(auth.user));
      },
      async createWorkspace(name, city) {
        const auth = await api<AuthResponse>("/api/v1/workspaces", {
          method: "POST",
          body: JSON.stringify({ name, city }),
        });
        applyWorkspaceChange(auth, setUser, setWorkspaces);
        window.location.assign(afterAuthPath(auth.user));
      },
      async joinWorkspace(joinCode) {
        const card = await api<WorkspaceCard>("/api/v1/workspaces/join", {
          method: "POST",
          body: JSON.stringify({ joinCode }),
        });
        const mine = await api<MyWorkspacesResponse>("/api/v1/workspaces");
        persistWorkspaces(mine.workspaces);
        setWorkspaces(mine.workspaces);
        return card;
      },
      async refreshWorkspaces() {
        const mine = await api<MyWorkspacesResponse>("/api/v1/workspaces");
        persistWorkspaces(mine.workspaces);
        setWorkspaces(mine.workspaces);
        return mine;
      },
      async refreshUser() {
        const me = await api<AuthenticatedUser>("/api/v1/auth/me");
        persistUser(me);
        setUser(me);
        return me;
      },
      has: (permission) => Boolean(user?.permissions.includes(permission)),
    }),
    [user, workspaces, ready, loginWithTokens],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used inside AuthProvider");
  }
  return ctx;
}
