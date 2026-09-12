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
} from "./api";
import { getDeviceId } from "./device";
import { afterAuthPath } from "./plan";
import { beginLogout, isOidcEnabled } from "./oidc";
import type { AuthResponse, AuthenticatedUser, MyWorkspacesResponse, WorkspaceCard } from "./types";

interface AuthContextValue {
  user: AuthenticatedUser | null;
  workspaces: WorkspaceCard[];
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

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthenticatedUser | null>(getStoredUser);
  const [workspaces, setWorkspaces] = useState<WorkspaceCard[]>(getStoredWorkspaces);

  useEffect(() => {
    if (!user && !getAccessToken()) {
      return;
    }
    api<AuthenticatedUser>("/api/v1/auth/me")
      .then((me) => {
        persistUser(me);
        setUser(me);
      })
      .catch(() => {
        /* keep the cached session until the next API call */
      });
  }, []);

  useEffect(() => {
    if (!user || workspaces.length > 0) {
      return;
    }
    api<MyWorkspacesResponse>("/api/v1/workspaces")
      .then((mine) => {
        persistWorkspaces(mine.workspaces);
        setWorkspaces(mine.workspaces);
      })
      .catch(() => {
        /* listing is optional until the user opens My Workspaces */
      });
  }, [user, workspaces.length]);

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
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      workspaces,
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
        return auth;
      },
      loginWithTokens,
      acceptSession(auth) {
        applySession(auth, setUser, setWorkspaces);
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
        applySession(auth, setUser, setWorkspaces);
        window.location.assign(afterAuthPath(auth.user));
      },
      async createWorkspace(name, city) {
        const auth = await api<AuthResponse>("/api/v1/workspaces", {
          method: "POST",
          body: JSON.stringify({ name, city }),
        });
        applySession(auth, setUser, setWorkspaces);
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
    [user, workspaces, loginWithTokens],
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
