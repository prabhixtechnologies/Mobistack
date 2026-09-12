import { useEffect } from "react";
import { beginLogin, isOidcEnabled } from "../lib/oidc";
import { LogoMark } from "../ui/LogoMark";
import { ThemeToggle } from "../ui/ThemeToggle";
import { BRAND, copyrightLine } from "../lib/brand";

/**
 * Signing in is a redirect when Identity is configured.
 *
 * <p>The password / OTP / Google forms that used to live here are gone on the OIDC path: credentials
 * belong on the hosted page. When {@code VITE_IDENTITY_ISSUER} is blank (local/dev without Identity),
 * {@link MissingIssuer} explains what is missing rather than silently offering a second login path.
 */
export function LoginPage() {
  useEffect(() => {
    if (!isOidcEnabled()) return;
    void beginLogin(window.location.pathname === "/login" ? "/" : undefined);
  }, []);

  if (!isOidcEnabled()) return <MissingIssuer />;

  return (
    <div className="login-page">
      <header className="login-page__top">
        <LogoMark />
        <ThemeToggle icon />
      </header>
      <main className="login-page__body">
        <p className="muted">Taking you to sign in…</p>
      </main>
      <footer className="login-page__footer">
        <p className="muted">{copyrightLine()}</p>
      </footer>
    </div>
  );
}

/**
 * For a build with no {@code VITE_IDENTITY_ISSUER}, which has no way to sign anybody in via OIDC.
 */
export function MissingIssuer() {
  return (
    <div className="login-page">
      <header className="login-page__top">
        <LogoMark />
        <ThemeToggle icon />
      </header>
      <main className="login-page__body">
        <h1>{BRAND.product}</h1>
        <p>Sign-in is not configured for this build.</p>
        <p className="muted">
          Set <code>VITE_IDENTITY_ISSUER</code> to the identity service URL and rebuild.
        </p>
      </main>
      <footer className="login-page__footer">
        <p className="muted">{copyrightLine()}</p>
      </footer>
    </div>
  );
}
