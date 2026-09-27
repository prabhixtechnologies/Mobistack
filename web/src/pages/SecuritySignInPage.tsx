import { beginLogin } from "@prabhix/oidc-client";
import { useAuth } from "../lib/auth";
import { AuthGate } from "./LoginPage";

/**
 * Shown when the API rejects an older Identity token after a security cutover.
 */
export function SecuritySignInPage() {
  const { logout } = useAuth();

  return (
    <AuthGate>
      <h1 className="auth-heading">Sign in again</h1>
      <p className="auth-brand__welcome">
        Your session was issued before a security update. Sign in through Prabhix Identity to continue
        without losing shop data.
      </p>
      <div className="auth-actions auth-actions--stack">
        <button
          className="auth-submit"
          type="button"
          onClick={() => void beginLogin("/").catch(() => undefined)}
        >
          Continue to Identity
        </button>
        <button className="auth-text" type="button" onClick={() => void logout()}>
          Sign out on this device
        </button>
      </div>
    </AuthGate>
  );
}
