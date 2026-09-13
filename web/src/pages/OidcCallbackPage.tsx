import { useEffect, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { completeLogin, rememberIdToken } from "../lib/oidc";
import { useAuth } from "../lib/auth";
import { AuthGate, AuthGateLink } from "./LoginPage";
import { BRAND, copyrightLine } from "../lib/brand";
import { LogoMark } from "../ui/LogoMark";

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
    <AuthGate>
      <div className="auth-brand">
        <span className="auth-brand__mark">
          <LogoMark size={36} />
        </span>
        <span className="auth-brand__name">{BRAND.product}</span>
      </div>
      <div className="auth-intro">
        <h1 className="auth-heading">{message ? "Sign-in did not finish" : "Signing you in"}</h1>
        {message ? (
          <p className="auth-error" role="alert">
            {message}
          </p>
        ) : (
          <p className="auth-brand__welcome">Finishing your Prabhix Identity session…</p>
        )}
      </div>
      {message ? (
        <div className="auth-actions auth-actions--stack">
          <AuthGateLink to="/login">Back to sign in</AuthGateLink>
        </div>
      ) : null}
      <p className="auth-legal">{copyrightLine()}</p>
    </AuthGate>
  );
}
