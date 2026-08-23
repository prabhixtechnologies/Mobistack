import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import {
  api,
  clearSession,
  getStoredUser,
  getStoredWorkspaces,
  persistSession,
  persistWorkspaces,
  type AuthResponse,
  type AuthUser,
  type WorkspaceCard,
} from "./api";
import { getDeviceId } from "./device";

interface MyWorkspacesResponse {
  selectedWorkspaceId?: string | null;
  workspaces: WorkspaceCard[];
}

interface AuthValue {
  user: AuthUser | null;
  workspaces: WorkspaceCard[];
  ready: boolean;
  login: (email: string, password: string) => Promise<AuthUser>;
  acceptSession: (auth: AuthResponse) => Promise<AuthUser>;
  logout: () => Promise<void>;
  switchWorkspace: (workspaceId: string) => Promise<void>;
  createWorkspace: (name: string, city?: string) => Promise<void>;
  joinWorkspace: (joinCode: string) => Promise<WorkspaceCard>;
  refreshWorkspaces: () => Promise<void>;
}

const AuthContext = createContext<AuthValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [workspaces, setWorkspaces] = useState<WorkspaceCard[]>([]);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    Promise.all([getStoredUser(), getStoredWorkspaces()]).then(([stored, storedWorkspaces]) => {
      setUser(stored);
      setWorkspaces(storedWorkspaces);
      setReady(true);
    });
  }, []);

  return (
    <AuthContext.Provider
      value={{
        user,
        workspaces,
        ready,
        async login(email, password) {
          const auth = await api<AuthResponse>("/api/v1/auth/login", {
            method: "POST",
            body: JSON.stringify({ email, password, deviceId: await getDeviceId() }),
          });
          await persistSession(auth);
          setUser(auth.user);
          setWorkspaces(auth.workspaces ?? []);
          return auth.user;
        },
        async acceptSession(auth) {
          await persistSession(auth);
          setUser(auth.user);
          setWorkspaces(auth.workspaces ?? []);
          return auth.user;
        },
        async logout() {
          await clearSession();
          setUser(null);
          setWorkspaces([]);
        },
        async switchWorkspace(workspaceId) {
          const auth = await api<AuthResponse>(`/api/v1/workspaces/${workspaceId}/select`, {
            method: "POST",
          });
          await persistSession(auth);
          setUser(auth.user);
          setWorkspaces(auth.workspaces ?? []);
        },
        async createWorkspace(name, city) {
          const auth = await api<AuthResponse>("/api/v1/workspaces", {
            method: "POST",
            body: JSON.stringify({ name, city }),
          });
          await persistSession(auth);
          setUser(auth.user);
          setWorkspaces(auth.workspaces ?? []);
        },
        async joinWorkspace(joinCode) {
          const card = await api<WorkspaceCard>("/api/v1/workspaces/join", {
            method: "POST",
            body: JSON.stringify({ joinCode }),
          });
          const mine = await api<MyWorkspacesResponse>("/api/v1/workspaces");
          await persistWorkspaces(mine.workspaces);
          setWorkspaces(mine.workspaces);
          return card;
        },
        async refreshWorkspaces() {
          const mine = await api<MyWorkspacesResponse>("/api/v1/workspaces");
          await persistWorkspaces(mine.workspaces);
          setWorkspaces(mine.workspaces);
        },
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("AuthProvider missing");
  return ctx;
}
