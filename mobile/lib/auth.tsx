import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import {
  api,
  clearSession,
  getStoredUser,
  getStoredWorkspaces,
  onSessionEnded,
  persistOidcTokens,
  persistSession,
  persistUser,
  persistWorkspaces,
  selectedWorkspaceId,
  type AuthResponse,
  type AuthUser,
  type WorkspaceCard,
} from "./api";
import { setActiveScope } from "./db";
import { drainBeforeWorkspaceChange } from "./offline";
import { forgetPushRegistration } from "./push";
import { beginLogin as beginOidcLogin, beginLogout, isOidcEnabled, rememberIdToken } from "./oidc";

interface MyWorkspacesResponse {
  selectedWorkspaceId?: string | null;
  workspaces: WorkspaceCard[];
}

interface AuthValue {
  user: AuthUser | null;
  workspaces: WorkspaceCard[];
  ready: boolean;
  /** Hosted Identity login (Custom Tab / system browser). */
  loginWithIdentity: (options?: { prompt?: "create" | "login" | "select_account" }) => Promise<AuthUser>;
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
  refreshWorkspaces: () => Promise<void>;
}

const AuthContext = createContext<AuthValue | null>(null);

/**
 * After workspace select/create under Identity: keep the Identity access token.
 * The API still returns an HS256 pair for legacy clients; applying it would drop the shared session.
 */
async function applyWorkspaceChange(
  auth: AuthResponse,
  setUser: (user: AuthUser) => void,
  setWorkspaces: (workspaces: WorkspaceCard[]) => void,
): Promise<void> {
  if (isOidcEnabled()) {
    await persistUser(auth.user);
    setUser(auth.user);
    if (auth.workspaces) {
      await persistWorkspaces(auth.workspaces);
      setWorkspaces(auth.workspaces);
    }
    return;
  }
  await persistSession(auth);
  setUser(auth.user);
  setWorkspaces(auth.workspaces ?? []);
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [workspaces, setWorkspaces] = useState<WorkspaceCard[]>([]);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    Promise.all([getStoredUser(), getStoredWorkspaces()]).then(([stored, storedWorkspaces]) => {
      setActiveScope(stored?.id, selectedWorkspaceId(stored));
      setUser(stored);
      setWorkspaces(storedWorkspaces);
      setReady(true);
    });
  }, []);

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
        async loginWithIdentity(options) {
          await clearSession();
          setUser(null);
          setWorkspaces([]);
          const tokens = await beginOidcLogin(options);
          await rememberIdToken(tokens.idToken);
          await persistOidcTokens(tokens.accessToken, tokens.refreshToken);
          const me = await api<AuthUser>("/api/v1/auth/me");
          await persistUser(me);
          setUser(me);
          try {
            const mine = await api<MyWorkspacesResponse>("/api/v1/workspaces");
            await persistWorkspaces(mine.workspaces);
            setWorkspaces(mine.workspaces);
          } catch {
            setWorkspaces([]);
          }
          return me;
        },
        async logout() {
          await clearSession();
          forgetPushRegistration();
          setUser(null);
          setWorkspaces([]);
          if (isOidcEnabled()) {
            try {
              await beginLogout();
            } catch {
              /* local session is already gone */
            }
          }
        },
        async switchWorkspace(workspaceId) {
          await drainBeforeWorkspaceChange();
          const auth = await api<AuthResponse>(`/api/v1/workspaces/${workspaceId}/select`, {
            method: "POST",
          });
          await applyWorkspaceChange(auth, setUser, setWorkspaces);
        },
        async createWorkspace(name, city) {
          const auth = await api<AuthResponse>("/api/v1/workspaces", {
            method: "POST",
            body: JSON.stringify({ name, city }),
          });
          await applyWorkspaceChange(auth, setUser, setWorkspaces);
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
