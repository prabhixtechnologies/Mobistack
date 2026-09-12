import { Link } from "react-router-dom";
import type { ReactNode } from "react";
import { beginLogin, beginSignup, isOidcEnabled } from "../lib/oidc";
import { LogoMark } from "../ui/LogoMark";
import { ThemeToggle } from "../ui/ThemeToggle";
import { BRAND, copyrightLine } from "../lib/brand";

/**
 * Product gateway only — credentials are never collected here.
 *
 * <p>Every Prabhix product uses the same Identity hosted login (OIDC). This page starts that
 * flow; the white panel is the interaction surface, not a second password form.
 */
export function LoginPage() {
  if (!isOidcEnabled()) return <MissingIssuer />;

  return (
    <AuthGate>
      <div className="auth-brand">
        <span className="auth-brand__mark">
          <LogoMark size={36} />
        </span>
        <span className="auth-brand__name">{BRAND.product}</span>
      </div>
      <div className="auth-intro">
        <h1 className="auth-heading">Welcome</h1>
        <p className="auth-brand__welcome">
          One Prabhix account. Sign in or create one — then pick a shop workspace.
        </p>
      </div>
      <div className="auth-actions" style={{ gridTemplateColumns: "1fr" }}>
        <button
          type="button"
          className="auth-submit"
          onClick={() => void beginLogin(window.location.pathname === "/login" ? "/" : undefined)}
        >
          Sign in
        </button>
        <button
          type="button"
          className="auth-submit auth-submit--secondary"
          onClick={() => void beginSignup("/")}
        >
          Create an account
        </button>
      </div>
      <p className="auth-legal">{copyrightLine()}</p>
    </AuthGate>
  );
}

export function MissingIssuer() {
  return (
    <AuthGate>
      <div className="auth-brand">
        <span className="auth-brand__mark">
          <LogoMark size={36} />
        </span>
        <span className="auth-brand__name">{BRAND.product}</span>
      </div>
      <div className="auth-intro">
        <h1 className="auth-heading">Sign-in is not configured</h1>
        <p className="auth-brand__welcome">
          Set <code>VITE_IDENTITY_ISSUER</code> to the Identity URL and rebuild this app.
        </p>
      </div>
      <p className="auth-legal">{copyrightLine()}</p>
    </AuthGate>
  );
}

/** Shared OIDC gateway chrome — panel + showcase. Used by login and callback. */
export function AuthGate({ children }: { children: ReactNode }) {
  return (
    <div className="auth-screen">
      <div className="auth-screen__glow" aria-hidden />
      <header className="auth-top">
        <ThemeToggle icon />
      </header>
      <div className="auth-layout">
        <section className="auth-panel">{children}</section>
        <aside className="auth-showcase" aria-hidden>
          <p className="auth-kicker">Prabhix Identity</p>
          <div className="auth-showcase__brand">
            <LogoMark size={48} />
            <h2>{BRAND.product}</h2>
          </div>
          <p className="muted" style={{ color: "rgba(232,238,244,0.78)", marginTop: 12, lineHeight: 1.5 }}>
            {BRAND.tagline} Sign in once with your Prabhix account — the same one used by OneOps and
            Mailroom.
          </p>
        </aside>
      </div>
    </div>
  );
}

/** Kept for callback errors that need a link home without nesting AuthGate twice. */
export function AuthGateLink({ to, children }: { to: string; children: ReactNode }) {
  return (
    <Link className="auth-submit" to={to} style={{ textAlign: "center", textDecoration: "none" }}>
      {children}
    </Link>
  );
}
