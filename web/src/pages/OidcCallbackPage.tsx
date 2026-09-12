import { useEffect, useRef, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { completeLogin, rememberIdToken } from "../lib/oidc";
import { useAuth } from "../lib/auth";
import { LogoMark } from "../ui/LogoMark";
import { ThemeToggle } from "../ui/ThemeToggle";
import { copyrightLine } from "../lib/brand";

/**
 * Where Prabhix Identity sends the browser back after a successful sign-in.
 *
 * <p>Registered as an unguarded route so the authorization code can be redeemed before a session
 * exists in this app.
 */
export function OidcCallbackPage() {
  const [searchParams] = useSearchParams();
  const { loginWithTokens } = useAuth();
  const navigate = useNavigate();
  const [message, setMessage] = useState<string | null>(null);
  const started = useRef(false);

  useEffect(() => {
    if (started.current) return;
    started.current = true;

    void (async () => {
      try {
        const { tokens, returnTo } = await completeLogin(searchParams);
        rememberIdToken(tokens.idToken);
        await loginWithTokens(tokens.accessToken);
        navigate(returnTo, { replace: true });
      } catch (err) {
        setMessage(err instanceof Error ? err.message : "Sign-in did not complete.");
      }
    })();
  }, [searchParams, loginWithTokens, navigate]);

  return (
    <div className="login-page">
      <header className="login-page__top">
        <LogoMark />
        <ThemeToggle icon />
      </header>
      <main className="login-page__body">
        {message ? (
          <>
            <p className="error">{message}</p>
            <Link className="btn" to="/login">
              Back to sign in
            </Link>
          </>
        ) : (
          <p className="muted">Signing you in…</p>
        )}
      </main>
      <footer className="login-page__footer">
        <p className="muted">{copyrightLine()}</p>
      </footer>
    </div>
  );
}
