import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import {
  api,
  clearSession,
  getStoredUser,
  getStoredWorkspaces,
  onSessionEnded,
  persistSession,
  persistWorkspaces,
  selectedWorkspaceId,
  type AuthResponse,
  type AuthUser,
  type WorkspaceCard,
} from "./api";
import { setActiveScope } from "./db";
import { getDeviceId } from "./device";
import { drainBeforeWorkspaceChange } from "./offline";
import { forgetPushRegistration } from "./push";

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
  completeJoin: (
    joinCode: string,
    payment?: {
      orderId?: string;
      razorpay_order_id?: string;
      razorpay_payment_id?: string;
      razorpay_signature?: string;
    },
  ) => Promise<WorkspaceCard>;
  registerShop: (input: {
    shopName: string;
    ownerName: string;
    email: string;
    password: string;
    phone?: string;
    city?: string;
  }) => Promise<AuthUser>;
  refreshWorkspaces: () => Promise<void>;
}

const AuthContext = createContext<AuthValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [workspaces, setWorkspaces] = useState<WorkspaceCard[]>([]);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    Promise.all([getStoredUser(), getStoredWorkspaces()]).then(([stored, storedWorkspaces]) => {
      // Point local reads at this person's workspace before any screen mounts.
      setActiveScope(stored?.id, selectedWorkspaceId(stored));
      setUser(stored);
      setWorkspaces(storedWorkspaces);
      setReady(true);
    });
  }, []);

  // The server can end a session mid-request (expired or device limit). Drop
  // back to the sign-in screen instead of leaving the tabs on screen.
  useEffect(() => onSessionEnded(() => {
    setUser(null);
    setWorkspaces([]);
    forgetPushRegistration();
  }), []);

  return (
    <AuthContext.Provider
      value={{
        user,
        workspaces,
        ready,
        async login(email, password) {
          await clearSession();
          setUser(null);
          setWorkspaces([]);
          const auth = await api<AuthResponse>("/api/v1/auth/login", {
            method: "POST",
            body: JSON.stringify({ email: email.trim(), password, deviceId: await getDeviceId() }),
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
          // A push token belongs to whoever registered it, so the next person
          // on this phone must register again rather than inherit the alerts.
          forgetPushRegistration();
          setUser(null);
          setWorkspaces([]);
        },
        async switchWorkspace(workspaceId) {
          // Queued work belongs to the shop it was recorded in, so it has to
          // reach the server before the active shop changes.
          await drainBeforeWorkspaceChange();
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
          const card = await api<WorkspaceCard>("/api/v1/workspaces/join/complete", {
            method: "POST",
            body: JSON.stringify({ joinCode }),
          });
          const mine = await api<MyWorkspacesResponse>("/api/v1/workspaces");
          await persistWorkspaces(mine.workspaces);
          setWorkspaces(mine.workspaces);
          return card;
        },
        async completeJoin(joinCode, payment) {
          const card = await api<WorkspaceCard>("/api/v1/workspaces/join/complete", {
            method: "POST",
            body: JSON.stringify({ joinCode, ...payment }),
          });
          const mine = await api<MyWorkspacesResponse>("/api/v1/workspaces");
          await persistWorkspaces(mine.workspaces);
          setWorkspaces(mine.workspaces);
          return card;
        },
        async registerShop(input) {
          await clearSession();
          setUser(null);
          setWorkspaces([]);
          const auth = await api<AuthResponse>("/api/v1/auth/register-shop", {
            method: "POST",
            body: JSON.stringify(input),
          });
          await persistSession(auth);
          setUser(auth.user);
          setWorkspaces(auth.workspaces ?? []);
          return auth.user;
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
