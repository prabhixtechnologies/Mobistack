import { useAuth } from "../lib/auth";
import { ACCOUNT_URL } from "../lib/config";
import { BRAND } from "../lib/brand";
import { ThemeToggle } from "../ui/ThemeToggle";
import { SkipLink } from "../ui/SkipLink";
import { Icon } from "../ui/navIcons";

/**
 * Blocking screen when Identity still reports that this account must pick its own password.
 *
 * The form used to post to this API. Passwords live at Identity now, so this page only deep-links
 * there and offers sign-out.
 */
export function ForcePasswordChangePage() {
  const { user, logout } = useAuth();

  return (
    <div className="login-wrap">
      <SkipLink href="#main-content" label="Skip to account link" />
      <div className="login-card stack" id="main-content" tabIndex={-1}>
        <div className="auth-top">
          <ThemeToggle compact />
        </div>

        <div className="forced-pw__mark">
          <Icon name="key" />
        </div>

        <h1>Choose your own password</h1>
        <p className="muted">
          You're signed in as <strong>{user?.email}</strong> with a temporary password. Set a new one on
          Prabhix Identity to continue into {BRAND.product}.
        </p>

        {ACCOUNT_URL ? (
          <a className="auth-submit" href={ACCOUNT_URL}>
            Open Identity account
          </a>
        ) : (
          <p className="error">Identity is not configured in this build, so this password cannot be changed here.</p>
        )}

        <button className="auth-link auth-link--center" type="button" onClick={() => void logout()}>
          Sign out instead
        </button>
      </div>
    </div>
  );
}
