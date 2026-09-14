import { Link, useSearchParams } from "react-router-dom";
import { useEffect, useState, type ReactNode } from "react";
import { beginLogin, beginSignup, beginSilentLogin, isOidcEnabled } from "@prabhix/oidc-client";
import "../lib/oidc-config";
import { LogoMark } from "../ui/LogoMark";
import { ThemeToggle } from "../ui/ThemeToggle";
import { SkipLink } from "../ui/SkipLink";
import { BRAND, copyrightLine } from "../lib/brand";

/**
 * Product gateway only — credentials are never collected here.
 *
 * Every Prabhix product uses the same Identity hosted login (OIDC). This page starts that
 * flow; the white panel is the interaction surface, not a second password form.
 *
 * If Identity already has a session, we never show the Welcome card: a silent
 * `prompt=none` authorize recovers it. `?sso=0` is the bounce from a failed
 * silent check so we do not loop.
 */
export function LoginPage() {
  if (!isOidcEnabled()) return <MissingIssuer />;

  return <LoginGateway />;
}

function LoginGateway() {
  const [searchParams] = useSearchParams();
  if (searchParams.get("sso") === "0") {
    return (
      <AuthGate>
        <LoginActions />
      </AuthGate>
    );
  }
  return <SilentSso />;
}

function SilentSso() {
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void beginSilentLogin("/").catch((cause) => {
      setError(cause instanceof Error ? cause.message : "Could not continue sign-in.");
    });
  }, []);

  if (error) {
    return (
      <AuthGate>
        <AuthBrand />
        <div className="auth-intro">
          <h1 className="auth-heading">Could not continue</h1>
          <p className="auth-error" role="alert">
            {error}
          </p>
        </div>
        <div className="auth-actions auth-actions--stack">
          <Link className="auth-submit" to="/login?sso=0">
            Back to sign in
          </Link>
        </div>
        <AuthLegal />
      </AuthGate>
    );
  }

  return <SessionRestore />;
}

function LoginActions() {
  const [busy, setBusy] = useState<"login" | "signup" | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function start(kind: "login" | "signup"): Promise<void> {
    setBusy(kind);
    setError(null);
    try {
      if (kind === "login") {
        await beginLogin(window.location.pathname === "/login" ? "/" : undefined);
      } else {
        await beginSignup("/");
      }
    } catch (cause) {
      setBusy(null);
      setError(cause instanceof Error ? cause.message : "Could not start sign-in. Try again.");
    }
  }

  return (
    <>
      <AuthBrand />
      <div className="auth-intro">
        <h1 className="auth-heading">Welcome</h1>
        <p className="auth-brand__welcome">
          One Prabhix account. Sign in or create one — then pick a shop workspace.
        </p>
      </div>
      {error ? (
        <p className="auth-error" role="alert">
          {error}
        </p>
      ) : null}
      <div className="auth-actions auth-actions--stack">
        <button
          type="button"
          className="auth-submit"
          disabled={busy !== null}
          aria-busy={busy === "login"}
          onClick={() => void start("login")}
        >
          {busy === "login" ? "Redirecting…" : "Sign in"}
        </button>
        <button
          type="button"
          className="auth-submit auth-submit--secondary"
          disabled={busy !== null}
          aria-busy={busy === "signup"}
          onClick={() => void start("signup")}
        >
          {busy === "signup" ? "Redirecting…" : "Create an account"}
        </button>
      </div>
      <AuthLegal />
    </>
  );
}

export function MissingIssuer() {
  return (
    <AuthGate>
      <AuthBrand />
      <div className="auth-intro">
        <h1 className="auth-heading">Sign-in is not configured</h1>
        <p className="auth-brand__welcome">
          Set <code>VITE_IDENTITY_ISSUER</code> to the Identity URL and rebuild this app.
        </p>
      </div>
      <AuthLegal />
    </AuthGate>
  );
}

/** Shown while the Identity session cookie is exchanged for an access token. */
export function SessionRestore() {
  return (
    <AuthGate>
      <AuthBrand />
      <div className="auth-intro">
        <h1 className="auth-heading">Restoring your session</h1>
        <p className="auth-brand__welcome">Checking your Prabhix Identity sign-in…</p>
      </div>
      <AuthLegal />
    </AuthGate>
  );
}

function AuthBrand() {
  return (
    <div className="auth-brand">
      <span className="auth-brand__mark">
        <LogoMark size={36} />
      </span>
      <span className="auth-brand__name">{BRAND.product}</span>
    </div>
  );
}

function AuthLegal() {
  return (
    <div className="auth-legal-block">
      <p className="auth-legal">{copyrightLine()}</p>
      <nav className="auth-legal-links" aria-label="Legal">
        <Link to="/privacy">Privacy</Link>
        <Link to="/terms">Terms</Link>
        <Link to="/refunds">Refunds</Link>
      </nav>
    </div>
  );
}

/** Shared OIDC gateway chrome — panel + showcase. Used by login and callback. */
export function AuthGate({ children }: { children: ReactNode }) {
  return (
    <div className="auth-screen">
      <SkipLink href="#auth-panel" label="Skip to sign in" />
      <div className="auth-screen__glow" aria-hidden />
      <header className="auth-top">
        <span className="auth-secure">
          <LockIcon />
          Secured by Prabhix Identity
        </span>
        <ThemeToggle icon />
      </header>
      <div className="auth-layout">
        <section className="auth-panel" id="auth-panel">
          {children}
        </section>
        <aside className="auth-showcase">
          <p className="auth-kicker">Prabhix Identity</p>
          <div className="auth-showcase__brand">
            <LogoMark size={48} />
            <h2>{BRAND.product}</h2>
          </div>
          <p className="auth-tagline">
            {BRAND.tagline} Sign in once with your Prabhix account — the same one used by OneOps and
            Mailroom.
          </p>
          <ul className="auth-highlights">
            <li className="auth-highlight">
              <span className="auth-highlight__icon" aria-hidden>
                <SearchIcon />
              </span>
              <div>
                <strong>See what fits</strong>
                <p>Search a phone and get the parts that actually fit it, not a generic list.</p>
              </div>
            </li>
            <li className="auth-highlight">
              <span className="auth-highlight__icon" aria-hidden>
                <BoxIcon />
              </span>
              <div>
                <strong>See stock and price</strong>
                <p>Live quantities and counter prices before you commit the sale or the job.</p>
              </div>
            </li>
            <li className="auth-highlight">
              <span className="auth-highlight__icon" aria-hidden>
                <CartIcon />
              </span>
              <div>
                <strong>Sell from the ledger</strong>
                <p>Every sale, repair and receive appends a row. Offline tickets stay idempotent.</p>
              </div>
            </li>
          </ul>
        </aside>
      </div>
    </div>
  );
}

/** Kept for callback errors that need a link home without nesting AuthGate twice. */
export function AuthGateLink({ to, children }: { to: string; children: ReactNode }) {
  return (
    <Link className="auth-submit" to={to}>
      {children}
    </Link>
  );
}

function LockIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden>
      <rect x="5" y="11" width="14" height="10" rx="2" />
      <path d="M8 11V8a4 4 0 0 1 8 0v3" />
    </svg>
  );
}

function SearchIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <circle cx="11" cy="11" r="7" />
      <path d="M20 20l-3-3" />
    </svg>
  );
}

function BoxIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <path d="M21 8l-9-5-9 5v8l9 5 9-5V8z" />
      <path d="M3 8l9 5 9-5" />
    </svg>
  );
}

function CartIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
      <circle cx="9" cy="20" r="1" />
      <circle cx="18" cy="20" r="1" />
      <path d="M3 4h2l2.4 12h11.2L21 8H7" />
    </svg>
  );
}
