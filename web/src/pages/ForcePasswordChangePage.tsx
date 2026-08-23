import { useState } from "react";
import { api } from "../lib/api";
import { useAuth } from "../lib/auth";
import { BRAND } from "../lib/brand";
import { TextField } from "../ui/Field";
import { ThemeToggle } from "../ui/ThemeToggle";
import { Icon } from "../ui/navIcons";

/**
 * Blocking screen for accounts carrying `mustChangePassword`.
 *
 * An admin who creates an account sets a temporary password; until it's replaced
 * the app refuses to route anywhere else (see `App`), so a shared starter
 * credential can't linger in use. There is deliberately no skip — only sign out.
 */
export function ForcePasswordChangePage() {
  const { user, logout, refreshUser } = useAuth();
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (newPassword !== confirmPassword) {
      setError("The two new passwords don't match.");
      return;
    }
    if (newPassword.length < 8) {
      setError("Use at least 8 characters.");
      return;
    }
    if (newPassword === currentPassword) {
      setError("Choose a password different from the temporary one.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await api("/api/v1/auth/change-password", {
        method: "POST",
        body: JSON.stringify({ currentPassword, newPassword }),
      });
      // Clears `mustChangePassword`, which releases the routing block in `App`.
      await refreshUser();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "That didn't work.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="login-wrap">
      <div className="login-card stack">
        <div className="auth-top">
          <ThemeToggle compact />
        </div>

        <div className="forced-pw__mark">
          <Icon name="key" />
        </div>

        <h1>Choose your own password</h1>
        <p className="muted">
          You're signed in as <strong>{user?.email}</strong> with a temporary password set by your workspace
          admin. Pick a new one to continue into {BRAND.product}.
        </p>

        <form className="stack" onSubmit={submit}>
          {error && <p className="error">{error}</p>}
          <TextField
            label="Temporary password"
            type="password"
            required
            autoComplete="current-password"
            value={currentPassword}
            onChange={(event) => setCurrentPassword(event.target.value)}
          />
          <TextField
            label="New password"
            type="password"
            required
            minLength={8}
            autoComplete="new-password"
            hint="At least 8 characters."
            value={newPassword}
            onChange={(event) => setNewPassword(event.target.value)}
          />
          <TextField
            label="Confirm new password"
            type="password"
            required
            autoComplete="new-password"
            value={confirmPassword}
            onChange={(event) => setConfirmPassword(event.target.value)}
          />
          <button className="auth-submit" type="submit" disabled={busy}>
            {busy ? "Saving…" : "Set password and continue"}
          </button>
        </form>

        <button className="auth-link auth-link--center" type="button" onClick={() => void logout()}>
          Sign out instead
        </button>
      </div>
    </div>
  );
}
