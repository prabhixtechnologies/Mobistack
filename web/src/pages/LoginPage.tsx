import { FormEvent, useEffect, useRef, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../lib/auth";
import { api } from "../lib/api";
import { afterAuthPath } from "../lib/plan";
import type { AuthResponse } from "../lib/types";
import { BRAND, copyrightLine } from "../lib/brand";
import { getDeviceId } from "../lib/device";
import { storeGet, storeRemove, storeSet } from "../lib/storage";
import { ThemeToggle } from "../ui/ThemeToggle";
import { LogoMark } from "../ui/LogoMark";

type Method = "password" | "magic" | "email-otp" | "phone" | "whatsapp" | "register";

const REMEMBER_KEY = "remember-email";

const HIGHLIGHTS = [
  {
    title: "Secure authentication",
    body: "Signed sessions, device limits, and HTTPS in production.",
    icon: "shield",
  },
  {
    title: "Counter-fast",
    body: "Search a phone, see what fits, see stock, then sell or repair.",
    icon: "bolt",
  },
  {
    title: "Works offline",
    body: "The shop stays in your pocket when the network does not.",
    icon: "grid",
  },
] as const;

export function LoginPage() {
  const { login, acceptSession } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [method, setMethod] = useState<Method>("password");
  const [email, setEmail] = useState(() => storeGet(REMEMBER_KEY) ?? "");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [remember, setRemember] = useState(() => Boolean(storeGet(REMEMBER_KEY)));
  const [token, setToken] = useState("");
  const [code, setCode] = useState("");
  const [phone, setPhone] = useState("");
  const [fullName, setFullName] = useState("");
  const [acceptedTerms, setAcceptedTerms] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [recover, setRecover] = useState<"signin" | "forgot" | "reset">("signin");

  const acceptRef = useRef(acceptSession);
  acceptRef.current = acceptSession;

  useEffect(() => {
    const magic = params.get("magic");
    const googleCode = params.get("code");
    const sso = params.get("sso");
    if (magic) {
      setBusy(true);
      api<AuthResponse>("/api/v1/auth/magic-link/consume", {
        method: "POST",
        body: JSON.stringify({ token: magic, deviceId: getDeviceId() }),
      })
        .then((auth) => {
          acceptRef.current(auth);
          navigate(afterAuthPath(auth.user));
        })
        .catch((err: unknown) => {
          setError(err instanceof Error ? err.message : "That magic link is no longer valid.");
          setMethod("magic");
        })
        .finally(() => setBusy(false));
      return;
    }
    if (params.get("reason") === "session") {
      setNotice("This account signed in on another screen. Sign in here to continue — the other screen will be signed out.");
    }
    const reset = params.get("reset");
    if (reset) {
      setRecover("reset");
      setToken(reset);
      setMethod("password");
    }
    if (sso === "google" && googleCode) {
      setBusy(true);
      api<AuthResponse>("/api/v1/auth/sso/google", {
        method: "POST",
        body: JSON.stringify({
          code: googleCode,
          redirectUri: `${window.location.origin}/login?sso=google`,
          deviceId: getDeviceId(),
        }),
      })
        .then((auth) => {
          acceptRef.current(auth);
          navigate(afterAuthPath(auth.user));
        })
        .catch((err: unknown) => {
          setError(err instanceof Error ? err.message : "Google sign-in failed.");
        })
        .finally(() => setBusy(false));
    }
  }, [navigate, params]);

  function afterAuth(auth: AuthResponse) {
    acceptSession(auth);
    navigate(afterAuthPath(auth.user));
  }

  function finish(auth?: AuthResponse) {
    if (auth) {
      afterAuth(auth);
      return;
    }
    navigate("/");
  }

  function choose(next: Method) {
    setMethod(next);
    setError(null);
    setNotice(null);
    setCode("");
    setRecover("signin");
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      if (method === "password") {
        if (recover === "forgot") {
          await api("/api/v1/auth/forgot-password", { method: "POST", body: JSON.stringify({ email }) });
          setNotice("If an account exists for that email, reset instructions were sent.");
          setRecover("reset");
        } else if (recover === "reset") {
          await api("/api/v1/auth/reset-password", {
            method: "POST",
            body: JSON.stringify({ token, newPassword: password }),
          });
          setNotice("Password updated. Sign in.");
          setRecover("signin");
        } else {
          if (remember) {
            storeSet(REMEMBER_KEY, email);
          } else {
            storeRemove(REMEMBER_KEY);
          }
          const auth = await login(email, password);
          navigate(afterAuthPath(auth.user));
        }
      } else if (method === "magic") {
        await api("/api/v1/auth/magic-link", {
          method: "POST",
          body: JSON.stringify({ email }),
        });
        setNotice("If that email is registered, a sign-in link was sent.");
      } else if (method === "email-otp") {
        if (!code) {
          await api("/api/v1/auth/email-otp", {
            method: "POST",
            body: JSON.stringify({ email }),
          });
          setNotice("If that email is registered, a one-time code was sent.");
        } else {
          finish(
            await api<AuthResponse>("/api/v1/auth/email-otp/verify", {
              method: "POST",
              body: JSON.stringify({ email, code }),
            }),
          );
        }
      } else if (method === "phone" || method === "whatsapp") {
        const channel = method === "whatsapp" ? "whatsapp" : "phone";
        if (!code) {
          await api(`/api/v1/auth/${channel}/start`, {
            method: "POST",
            body: JSON.stringify({ phone, channel: method === "whatsapp" ? "WHATSAPP" : "SMS" }),
          });
          setNotice("A one-time code was sent to that number.");
        } else {
          finish(
            await api<AuthResponse>(`/api/v1/auth/${channel}/verify`, {
              method: "POST",
              body: JSON.stringify({ phone, code }),
            }),
          );
        }
      } else if (method === "register") {
        if (!acceptedTerms) {
          throw new Error("Accept the terms to create an account.");
        }
        finish(
          await api<AuthResponse>("/api/v1/auth/register", {
            method: "POST",
            body: JSON.stringify({ fullName, email, password, phone }),
          }),
        );
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not continue");
    } finally {
      setBusy(false);
    }
  }

  async function startGoogle() {
    setError(null);
    setNotice(null);
    try {
      const start = await api<{ status: string; authorizationUrl?: string }>(
        "/api/v1/auth/sso/google/start",
      );
      if (start.status === "READY" && start.authorizationUrl) {
        window.location.assign(start.authorizationUrl);
        return;
      }
      setNotice("Google sign-in is not configured for this environment.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not start Google sign-in");
    }
  }

  const heading =
    method === "register"
      ? "Create your account"
      : recover === "forgot"
        ? "Forgot password"
        : recover === "reset"
          ? "Set a new password"
          : method === "magic"
            ? "Sign in with a link"
            : method === "email-otp"
              ? "Sign in with email code"
              : method === "phone"
                ? "Sign in with SMS"
                : method === "whatsapp"
                  ? "Sign in with WhatsApp"
                  : "Welcome back";

  const subtitle =
    method === "register"
      ? "Join an existing shop after an owner approves you."
      : recover !== "signin"
        ? "We will email reset instructions if that account exists."
        : "Please sign in to continue.";

  const submitLabel = busy
    ? "Working…"
    : method === "magic"
      ? "Send magic link"
      : method === "register"
        ? "Create account"
        : recover === "forgot"
          ? "Send reset"
          : recover === "reset"
            ? "Set password"
            : code
              ? "Verify and sign in"
              : method === "email-otp" || method === "phone" || method === "whatsapp"
                ? "Send code"
                : "Sign in →";

  const showEmail =
    method === "password" ||
    method === "magic" ||
    method === "email-otp" ||
    method === "register";
  const showPasswordField =
    (method === "password" && recover !== "forgot") || method === "register";
  const showPhone = method === "phone" || method === "whatsapp" || method === "register";
  const showCode = method === "email-otp" || method === "phone" || method === "whatsapp";
  const showAlts = method !== "register" && recover === "signin";
  const showCreateUser = method !== "register" && recover === "signin";

  return (
    <div className="auth-screen">
      <div className="auth-screen__glow" aria-hidden />
      <header className="auth-top">
        <ThemeToggle icon />
        <span className="auth-secure">
          <IconShield />
          TLS
        </span>
      </header>

      <div className="auth-layout">
        <form className="auth-panel" onSubmit={onSubmit}>
          <div className="auth-brand">
            <span className="auth-brand__mark">
              <LogoMark size={36} />
            </span>
            <div className="auth-brand__name">{BRAND.product}</div>
          </div>

          <div className="auth-intro">
            <h1 className="auth-heading">{heading}</h1>
            <p className="auth-brand__welcome">{subtitle}</p>
          </div>

          {method === "register" && (
            <label className="auth-label">
              Full name
              <span className="auth-field">
                <IconUser />
                <input
                  className="auth-input"
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                  placeholder="Your name"
                  autoComplete="name"
                />
              </span>
            </label>
          )}

          {showEmail && (
            <label className="auth-label">
              Email
              <span className="auth-field">
                <IconUser />
                <input
                  className="auth-input"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  autoComplete="username"
                  type="email"
                  placeholder="you@prabhixtechnologies.com"
                />
              </span>
            </label>
          )}

          {showPasswordField && (
            <label className="auth-label">
              {recover === "reset" ? "New password" : "Password"}
              <span className="auth-field">
                <IconLock />
                <input
                  className="auth-input"
                  type={showPassword ? "text" : "password"}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  autoComplete={recover === "reset" || method === "register" ? "new-password" : "current-password"}
                  placeholder="Enter your password"
                />
                <button
                  className="auth-eye"
                  type="button"
                  onClick={() => setShowPassword((open) => !open)}
                  aria-label={showPassword ? "Hide password" : "Show password"}
                >
                  {showPassword ? <IconEyeOff /> : <IconEye />}
                </button>
              </span>
            </label>
          )}

          {method === "password" && recover === "reset" && (
            <label className="auth-label">
              Reset token from your email
              <span className="auth-field">
                <IconLock />
                <input className="auth-input" value={token} onChange={(e) => setToken(e.target.value)} />
              </span>
            </label>
          )}

          {showPhone && (
            <label className="auth-label">
              {method === "whatsapp" ? "WhatsApp number" : "Mobile number"}
              <span className="auth-field">
                <IconPhone />
                <input
                  className="auth-input"
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                  placeholder="+91 98XXXXXXXX"
                  autoComplete="tel"
                />
              </span>
            </label>
          )}

          {showCode && (
            <label className="auth-label">
              One-time code
              <span className="auth-field">
                <IconLock />
                <input
                  className="auth-input"
                  value={code}
                  onChange={(e) => setCode(e.target.value)}
                  placeholder="Leave blank to request a code"
                  inputMode="numeric"
                  autoComplete="one-time-code"
                />
              </span>
            </label>
          )}

          {method === "password" && recover === "signin" && (
            <div className="auth-row">
              <label className="auth-remember">
                <input
                  type="checkbox"
                  checked={remember}
                  onChange={(e) => setRemember(e.target.checked)}
                />
                Remember me
              </label>
              <button className="auth-link" type="button" onClick={() => setRecover("forgot")}>
                Forgot password?
              </button>
            </div>
          )}

          {method === "password" && recover !== "signin" && (
            <button className="auth-link" type="button" onClick={() => setRecover("signin")}>
              Back to sign in
            </button>
          )}

          {method === "register" && (
            <label className="auth-remember">
              <input
                type="checkbox"
                checked={acceptedTerms}
                onChange={(e) => setAcceptedTerms(e.target.checked)}
                required
              />
              <span>
                I agree to the <Link to="/terms">Terms</Link>, <Link to="/privacy">Privacy Policy</Link>,
                and <Link to="/refunds">Refunds</Link>. An owner must approve you before you can work in a shop.
              </span>
            </label>
          )}

          {notice && <div className="auth-notice">{notice}</div>}
          {error && <div className="auth-error">{error}</div>}

          <div className="auth-actions">
            <button className="auth-submit" type="submit" disabled={busy}>
              {submitLabel}
            </button>
            {showCreateUser && (
              <button className="auth-submit auth-submit--secondary" type="button" onClick={() => choose("register")}>
                Create user
              </button>
            )}
            {method === "register" && (
              <button className="auth-submit auth-submit--secondary" type="button" onClick={() => choose("password")}>
                Sign in
              </button>
            )}
          </div>

          {showAlts && (
            <>
              <div className="auth-rule">
                <span>Or continue with</span>
              </div>
              <div className="auth-alts">
                <button className="auth-alt" type="button" onClick={() => void startGoogle()}>
                  <IconGoogle />
                  Google
                </button>
                <button
                  className={method === "magic" ? "auth-alt on" : "auth-alt"}
                  type="button"
                  onClick={() => choose("magic")}
                >
                  <IconLink />
                  Magic link
                </button>
                <button
                  className={method === "email-otp" ? "auth-alt on" : "auth-alt"}
                  type="button"
                  onClick={() => choose("email-otp")}
                >
                  <IconMail />
                  Email code
                </button>
                <button
                  className={method === "phone" || method === "whatsapp" ? "auth-alt on" : "auth-alt"}
                  type="button"
                  onClick={() => choose(method === "whatsapp" ? "whatsapp" : "phone")}
                >
                  <IconPhone />
                  Phone
                </button>
              </div>
              {(method === "phone" || method === "whatsapp") && (
                <div className="auth-channel">
                  <button
                    className={method === "phone" ? "on" : ""}
                    type="button"
                    onClick={() => choose("phone")}
                  >
                    SMS
                  </button>
                  <button
                    className={method === "whatsapp" ? "on" : ""}
                    type="button"
                    onClick={() => choose("whatsapp")}
                  >
                    WhatsApp
                  </button>
                </div>
              )}
              {(method === "magic" || method === "email-otp" || method === "phone" || method === "whatsapp") && (
                <button className="auth-link auth-link--center" type="button" onClick={() => choose("password")}>
                  Use email and password
                </button>
              )}
            </>
          )}

          <div className="auth-legal-block">
            <p className="auth-legal">{copyrightLine()}</p>
            <nav className="auth-legal-links" aria-label="App and legal">
              <Link to="/app">Get the app</Link>
              <a href="/download/android">Android</a>
              <a href="/download/ios">iOS</a>
              <Link to="/privacy">Privacy</Link>
              <Link to="/terms">Terms</Link>
              <Link to="/refunds">Refunds</Link>
            </nav>
          </div>
        </form>

        <aside className="auth-showcase">
          <p className="auth-kicker">{BRAND.organization}</p>
          <div className="auth-showcase__brand">
            <LogoMark size={56} />
            <h2>{BRAND.product}</h2>
          </div>
          <p className="auth-tagline">{BRAND.tagline}</p>
          <ul className="auth-highlights">
            {HIGHLIGHTS.map((item) => (
              <li key={item.title} className="auth-highlight">
                <span className="auth-highlight__icon">
                  {item.icon === "shield" ? <IconShield /> : item.icon === "bolt" ? <IconBolt /> : <IconGrid />}
                </span>
                <div>
                  <strong>{item.title}</strong>
                  <p>{item.body}</p>
                </div>
              </li>
            ))}
          </ul>
        </aside>
      </div>
    </div>
  );
}

function IconUser() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden>
      <path
        fill="currentColor"
        d="M12 12a4 4 0 1 0-4-4 4 4 0 0 0 4 4Zm0 2c-4.42 0-8 2.24-8 5v1h16v-1c0-2.76-3.58-5-8-5Z"
      />
    </svg>
  );
}

function IconLock() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden>
      <path
        fill="currentColor"
        d="M17 9h-1V7a4 4 0 0 0-8 0v2H7a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-8a2 2 0 0 0-2-2Zm-7-2a2 2 0 0 1 4 0v2h-4Zm7 12H7v-8h10Z"
      />
    </svg>
  );
}

function IconEye() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden>
      <path
        fill="currentColor"
        d="M12 5c-7 0-10 7-10 7s3 7 10 7 10-7 10-7-3-7-10-7Zm0 12a5 5 0 1 1 5-5 5 5 0 0 1-5 5Zm0-8a3 3 0 1 0 3 3 3 3 0 0 0-3-3Z"
      />
    </svg>
  );
}

function IconEyeOff() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden>
      <path
        fill="currentColor"
        d="M2 5.27 3.28 4 20 20.72 18.73 22l-2.4-2.4A12.5 12.5 0 0 1 12 21C5 21 2 14 2 14a14.8 14.8 0 0 1 5.17-5.64Zm8.6 8.6 1.53 1.53A3 3 0 0 1 9.13 12.4ZM12 7c7 0 10 7 10 7a14.9 14.9 0 0 1-3.53 4.31l-1.45-1.45A12.6 12.6 0 0 0 20.8 14S18.2 9 12 9a8.6 8.6 0 0 0-2.16.28L8.18 7.62A10.8 10.8 0 0 1 12 7Zm-.9 2.12L14.88 13A3 3 0 0 0 11.1 9.12Z"
      />
    </svg>
  );
}

function IconPhone() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden>
      <path
        fill="currentColor"
        d="M17 2H7a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V4a2 2 0 0 0-2-2Zm0 18H7V4h10Z"
      />
    </svg>
  );
}

function IconMail() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden>
      <path
        fill="currentColor"
        d="M20 4H4a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V6a2 2 0 0 0-2-2Zm0 4-8 5L4 8V6l8 5 8-5Z"
      />
    </svg>
  );
}

function IconLink() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden>
      <path
        fill="currentColor"
        d="M10.6 13.4a4 4 0 0 1 0-5.66l2.12-2.12a4 4 0 0 1 5.66 5.66l-1.06 1.06-1.41-1.41 1.06-1.06a2 2 0 1 0-2.83-2.83l-2.12 2.12a2 2 0 0 0 0 2.83Zm2.8-2.8a4 4 0 0 1 0 5.66l-2.12 2.12a4 4 0 1 1-5.66-5.66l1.06-1.06 1.41 1.41-1.06 1.06a2 2 0 1 0 2.83 2.83l2.12-2.12a2 2 0 0 0 0-2.83Z"
      />
    </svg>
  );
}

function IconGoogle() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden>
      <path fill="#4285F4" d="M21.6 12.23c0-.74-.06-1.45-.18-2.14H12v4.05h5.38a4.6 4.6 0 0 1-2 3.02v2.5h3.24c1.9-1.75 3-4.32 3-7.43Z" />
      <path fill="#34A853" d="M12 22c2.7 0 4.96-.9 6.62-2.34l-3.24-2.5c-.9.6-2.05.96-3.38.96-2.6 0-4.8-1.76-5.59-4.12H3.07v2.58A10 10 0 0 0 12 22Z" />
      <path fill="#FBBC05" d="M6.41 13.99A6 6 0 0 1 6.1 12c0-.69.12-1.36.31-1.99V7.43H3.07A10 10 0 0 0 2 12c0 1.61.39 3.14 1.07 4.57Z" />
      <path fill="#EA4335" d="M12 5.88c1.47 0 2.79.5 3.82 1.5l2.87-2.87C16.95 2.86 14.7 2 12 2A10 10 0 0 0 3.07 7.43l3.34 2.58C7.2 7.64 9.4 5.88 12 5.88Z" />
    </svg>
  );
}

function IconShield() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden>
      <path fill="currentColor" d="M12 2 4 5v6c0 5.25 3.44 10.15 8 11.4 4.56-1.25 8-6.15 8-11.4V5Z" />
    </svg>
  );
}

function IconBolt() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden>
      <path fill="currentColor" d="M13 2 4 14h7l-1 8 10-14h-7l0-6Z" />
    </svg>
  );
}

function IconGrid() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden>
      <path fill="currentColor" d="M3 3h8v8H3Zm10 0h8v8h-8ZM3 13h8v8H3Zm10 0h8v8h-8Z" />
    </svg>
  );
}
