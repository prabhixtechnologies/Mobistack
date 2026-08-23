import { FormEvent, useEffect, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../lib/auth";
import { api } from "../lib/api";
import type { AuthResponse } from "../lib/types";
import { BRAND } from "../lib/brand";
import { getDeviceId } from "../lib/device";
import { BrandFooter, BrandMark } from "../ui/BrandMark";
import { ThemeToggle } from "../ui/ThemeToggle";

type Method = "password" | "magic" | "email-otp" | "phone" | "whatsapp" | "sso" | "register" | "shop";

const METHODS: { id: Method; label: string }[] = [
  { id: "password", label: "Password" },
  { id: "magic", label: "Magic link" },
  { id: "email-otp", label: "Email code" },
  { id: "phone", label: "Phone" },
  { id: "whatsapp", label: "WhatsApp" },
  { id: "sso", label: "SSO" },
  { id: "register", label: "Create user" },
  { id: "shop", label: "New shop" },
];

export function LoginPage() {
  const { login, acceptSession } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [method, setMethod] = useState<Method>("password");
  const [email, setEmail] = useState("owner@fixflow.app");
  const [password, setPassword] = useState("Owner@123");
  const [token, setToken] = useState("");
  const [code, setCode] = useState("");
  const [phone, setPhone] = useState("");
  const [fullName, setFullName] = useState("");
  const [shopName, setShopName] = useState("");
  const [ownerName, setOwnerName] = useState("");
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
          navigate("/");
        })
        .catch((err: unknown) => {
          setError(err instanceof Error ? err.message : "That magic link is no longer valid.");
          setMethod("magic");
        })
        .finally(() => setBusy(false));
      return;
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
          navigate("/");
        })
        .catch((err: unknown) => {
          setError(err instanceof Error ? err.message : "Google sign-in failed.");
          setMethod("sso");
        })
        .finally(() => setBusy(false));
    }
  }, [navigate, params]);

  function finish(auth?: AuthResponse) {
    if (auth) {
      acceptSession(auth);
    }
    navigate("/");
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
          setNotice("If that account exists, a reset token was written to the notification log.");
          setRecover("reset");
        } else if (recover === "reset") {
          await api("/api/v1/auth/reset-password", {
            method: "POST",
            body: JSON.stringify({ token, newPassword: password }),
          });
          setNotice("Password updated. Sign in.");
          setRecover("signin");
        } else {
          await login(email, password);
          navigate("/");
        }
      } else if (method === "magic") {
        const sent = await api<{ hint?: string }>("/api/v1/auth/magic-link", {
          method: "POST",
          body: JSON.stringify({ email }),
        });
        setNotice(sent.hint ?? "A sign-in link was sent.");
      } else if (method === "email-otp") {
        if (!code) {
          const sent = await api<{ hint?: string }>("/api/v1/auth/email-otp", {
            method: "POST",
            body: JSON.stringify({ email }),
          });
          setNotice(sent.hint ?? "A code was sent to that email.");
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
          const sent = await api<{ hint?: string }>(`/api/v1/auth/${channel}/start`, {
            method: "POST",
            body: JSON.stringify({ phone, channel: method === "whatsapp" ? "WHATSAPP" : "SMS" }),
          });
          setNotice(sent.hint ?? "A one-time code was sent.");
        } else {
          finish(
            await api<AuthResponse>(`/api/v1/auth/${channel}/verify`, {
              method: "POST",
              body: JSON.stringify({ phone, code }),
            }),
          );
        }
      } else if (method === "sso") {
        finish(
          await api<AuthResponse>("/api/v1/auth/sso/dev", {
            method: "POST",
            body: JSON.stringify({ email, fullName: fullName || email, provider: "DEV", deviceId: "fixflow-web" }),
          }),
        );
      } else if (method === "register") {
        finish(
          await api<AuthResponse>("/api/v1/auth/register", {
            method: "POST",
            body: JSON.stringify({ fullName, email, password, phone }),
          }),
        );
      } else {
        finish(
          await api<AuthResponse>("/api/v1/auth/register-shop", {
            method: "POST",
            body: JSON.stringify({ shopName, ownerName, email, password, phone, city: "Pune" }),
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
    try {
      const start = await api<{ status: string; authorizationUrl?: string; hint?: string }>(
        "/api/v1/auth/sso/google/start",
      );
      if (start.status === "READY" && start.authorizationUrl) {
        window.location.assign(start.authorizationUrl);
        return;
      }
      setNotice(start.hint ?? "Google SSO is not configured. Use Dev SSO below.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not start Google SSO");
    }
  }

  const heading =
    method === "shop"
      ? "Open a shop."
      : method === "register"
        ? "Create your account."
        : recover !== "signin" && method === "password"
          ? "Recover access."
          : "Open the counter.";

  return (
    <div className="login-shell">
      <aside className="login-hero">
        <div className="spread">
          <BrandMark />
          <ThemeToggle compact />
        </div>
        <h1>{BRAND.product}</h1>
        <p className="login-tagline">{BRAND.tagline}</p>
        <p className="muted">
          Search a phone, see what fits, sell it. The shop stays in your pocket — even when the
          network does not.
        </p>
        <BrandFooter />
      </aside>
      <form className="login-card stack" onSubmit={onSubmit}>
        <div className="method-tabs" role="tablist">
          {METHODS.map((item) => (
            <button
              key={item.id}
              type="button"
              role="tab"
              aria-selected={method === item.id}
              className={method === item.id ? "method-tab on" : "method-tab"}
              onClick={() => {
                setMethod(item.id);
                setError(null);
                setNotice(null);
                setCode("");
                if (item.id !== "password") {
                  setRecover("signin");
                }
              }}
            >
              {item.label}
            </button>
          ))}
        </div>
        <h2>{heading}</h2>
        {(method === "password" ||
          method === "magic" ||
          method === "email-otp" ||
          method === "sso" ||
          method === "register" ||
          method === "shop") && (
          <label className="stack">
            <span className="faint">Email</span>
            <input
              className="field"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              autoComplete="username"
              type="email"
            />
          </label>
        )}
        {(method === "password" && recover !== "forgot") || method === "register" || method === "shop" ? (
          <label className="stack">
            <span className="faint">{recover === "reset" ? "New password" : "Password"}</span>
            <input
              className="field"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete={recover === "reset" ? "new-password" : "current-password"}
            />
          </label>
        ) : null}
        {method === "password" && recover === "reset" && (
          <label className="stack">
            <span className="faint">Reset token from the log</span>
            <input className="field" value={token} onChange={(e) => setToken(e.target.value)} />
          </label>
        )}
        {(method === "email-otp" || method === "phone" || method === "whatsapp") && (
          <label className="stack">
            <span className="faint">One-time code</span>
            <input
              className="field"
              value={code}
              onChange={(e) => setCode(e.target.value)}
              placeholder="Leave blank to request a code"
              inputMode="numeric"
            />
          </label>
        )}
        {(method === "phone" || method === "whatsapp" || method === "register" || method === "shop") && (
          <label className="stack">
            <span className="faint">{method === "whatsapp" ? "WhatsApp number" : "Mobile number"}</span>
            <input className="field" value={phone} onChange={(e) => setPhone(e.target.value)} />
          </label>
        )}
        {(method === "sso" || method === "register") && (
          <label className="stack">
            <span className="faint">Full name</span>
            <input className="field" value={fullName} onChange={(e) => setFullName(e.target.value)} />
          </label>
        )}
        {method === "shop" && (
          <>
            <input
              className="field"
              value={shopName}
              onChange={(e) => setShopName(e.target.value)}
              placeholder="Shop name"
              required
            />
            <input
              className="field"
              value={ownerName}
              onChange={(e) => setOwnerName(e.target.value)}
              placeholder="Owner name"
              required
            />
          </>
        )}
        {method === "sso" && (
          <button className="btn ghost" type="button" onClick={() => void startGoogle()}>
            Continue with Google
          </button>
        )}
        {notice && <div className="muted">{notice}</div>}
        {error && <div className="error">{error}</div>}
        <button className="btn" type="submit" disabled={busy}>
          {busy
            ? "Working…"
            : method === "magic"
              ? "Send magic link"
              : method === "shop"
                ? "Create shop"
                : method === "register"
                  ? "Create account"
                  : method === "sso"
                    ? "Continue with Dev SSO"
                    : recover === "forgot"
                      ? "Send reset"
                      : recover === "reset"
                        ? "Set password"
                        : code
                          ? "Verify and sign in"
                          : method === "email-otp" || method === "phone" || method === "whatsapp"
                            ? "Send code"
                            : "Sign in"}
        </button>
        {method === "password" && (
          <div className="row" style={{ flexWrap: "wrap" }}>
            <button className="btn ghost" type="button" onClick={() => setRecover("signin")}>
              Sign in
            </button>
            <button className="btn ghost" type="button" onClick={() => setRecover("forgot")}>
              Forgot password
            </button>
          </div>
        )}
        <p className="faint">Demo password login: owner@fixflow.app / Owner@123. Dev codes are 123456.</p>
      </form>
    </div>
  );
}
