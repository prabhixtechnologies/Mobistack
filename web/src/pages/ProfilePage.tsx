import { useState } from "react";
import { api } from "../lib/api";
import { useAuth } from "../lib/auth";
import { useAccess } from "../lib/access";
import { useResource } from "../lib/useResource";
import { permissionLabel, PERMISSION_GROUPS } from "../lib/permissions";
import type { DeviceSession } from "../lib/types";
import { PageHeader } from "../ui/PageHeader";
import { TextField } from "../ui/Field";
import { ConfirmDialog } from "../ui/Modal";
import { EmptyState, ErrorState } from "../ui/EmptyState";
import { useToast } from "../ui/Toast";
import { Icon } from "../ui/navIcons";

function describeDevice(session: DeviceSession): string {
  const agent = session.userAgent ?? "";
  const browser = /Edg/.test(agent)
    ? "Edge"
    : /Chrome/.test(agent)
      ? "Chrome"
      : /Firefox/.test(agent)
        ? "Firefox"
        : /Safari/.test(agent)
          ? "Safari"
          : null;
  const platform = /Android/.test(agent)
    ? "Android"
    : /iPhone|iPad/.test(agent)
      ? "iOS"
      : /Windows/.test(agent)
        ? "Windows"
        : /Mac OS/.test(agent)
          ? "macOS"
          : /Linux/.test(agent)
            ? "Linux"
            : null;
  if (browser && platform) {
    return `${browser} on ${platform}`;
  }
  return browser ?? platform ?? "Unrecognised device";
}

/**
 * Self-service account screen: identity, password and signed-in devices.
 *
 * The change-password and session endpoints shipped on the backend without a web
 * surface, so users could neither rotate their own password nor see where their
 * account was signed in.
 */
export function ProfilePage() {
  const { user, refreshUser } = useAuth();
  const access = useAccess();
  const toast = useToast();

  const sessions = useResource<DeviceSession[]>("/api/v1/auth/sessions");

  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [passwordError, setPasswordError] = useState<string | null>(null);
  const [changing, setChanging] = useState(false);
  const [revoking, setRevoking] = useState<DeviceSession | null>(null);
  const [revokeBusy, setRevokeBusy] = useState(false);

  const changePassword = async (event: React.FormEvent) => {
    event.preventDefault();
    if (newPassword !== confirmPassword) {
      setPasswordError("The two new passwords don't match.");
      return;
    }
    if (newPassword.length < 8) {
      setPasswordError("Use at least 8 characters.");
      return;
    }
    setChanging(true);
    setPasswordError(null);
    try {
      await api("/api/v1/auth/change-password", {
        method: "POST",
        body: JSON.stringify({ currentPassword, newPassword }),
      });
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
      // The backend ends every other session on success, so the list is now stale.
      sessions.reload();
      await refreshUser();
      toast.success("Password changed. Your other devices have been signed out.");
    } catch (cause) {
      setPasswordError(cause instanceof Error ? cause.message : "That didn't work.");
    } finally {
      setChanging(false);
    }
  };

  const revoke = async () => {
    if (!revoking) {
      return;
    }
    setRevokeBusy(true);
    try {
      await api(`/api/v1/auth/sessions/${revoking.id}`, { method: "DELETE" });
      toast.success("That device has been signed out.");
      setRevoking(null);
      sessions.reload();
    } catch (cause) {
      toast.fromError(cause);
    } finally {
      setRevokeBusy(false);
    }
  };

  const heldByGroup = PERMISSION_GROUPS.map((group) => ({
    label: group.label,
    held: group.permissions.filter((permission) => access.permissions.has(permission)),
  })).filter((group) => group.held.length > 0);

  return (
    <div className="page">
      <PageHeader
        kicker="My account"
        title={user?.fullName ?? "My profile"}
        subtitle="Your identity in this workspace, your password, and the devices you're signed in on."
      />

      <div className="grid-2 profile-grid">
        <section className="card">
          <h2 className="section-title">Identity</h2>
          <dl className="detail-list">
            <div>
              <dt>Name</dt>
              <dd>{user?.fullName}</dd>
            </div>
            <div>
              <dt>Email</dt>
              <dd>
                {user?.email}
                {user?.emailVerified ? (
                  <span className="badge GREEN">Verified</span>
                ) : (
                  <span className="badge ORANGE">Unverified</span>
                )}
              </dd>
            </div>
            <div>
              <dt>Phone</dt>
              <dd>
                {user?.phone ?? <span className="faint">Not set</span>}
                {user?.phone &&
                  (user.phoneVerified ? (
                    <span className="badge GREEN">Verified</span>
                  ) : (
                    <span className="badge ORANGE">Unverified</span>
                  ))}
              </dd>
            </div>
            <div>
              <dt>Workspace</dt>
              <dd>{user?.workspaceName ?? user?.shopName}</dd>
            </div>
            <div>
              <dt>Role</dt>
              <dd>
                <span className={`badge role-badge tint-${access.roleTint}`}>{access.roleLabel}</span>
                {access.isPlatformAdmin && <span className="badge neutral">Platform staff</span>}
              </dd>
            </div>
          </dl>
          <p className="faint">
            Name, email and phone are managed in Settings and by your workspace owner.
          </p>
        </section>

        <section className="card">
          <h2 className="section-title">Change password</h2>
          <form className="stack" onSubmit={changePassword}>
            {passwordError && <p className="error">{passwordError}</p>}
            <TextField
              label="Current password"
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
            <p className="faint">Changing your password signs out every other device.</p>
            <button className="btn" type="submit" disabled={changing}>
              {changing ? "Updating…" : "Update password"}
            </button>
          </form>
        </section>
      </div>

      <section className="card">
        <div className="spread section-head">
          <h2 className="section-title">Signed-in devices</h2>
          <button className="btn ghost btn--sm" type="button" onClick={sessions.reload}>
            <Icon name="refresh" />
            Refresh
          </button>
        </div>

        {sessions.error ? (
          <ErrorState message={sessions.error} onRetry={sessions.reload} />
        ) : sessions.loading && !sessions.data ? (
          <div className="stack">
            <div className="skeleton" />
            <div className="skeleton" />
          </div>
        ) : !sessions.data?.length ? (
          <EmptyState
            compact
            icon="shield"
            title="No other devices"
            hint="Only this browser is signed in to your account."
          />
        ) : (
          <ul className="session-list">
            {sessions.data.map((session) => (
              <li key={session.id} className="session">
                <span className="session__icon">
                  <Icon name="server" />
                </span>
                <div className="session__detail">
                  <strong>
                    {describeDevice(session)}
                    {session.current && <span className="badge GREEN">This device</span>}
                  </strong>
                  <span className="faint">
                    {session.ipAddress ?? "Unknown IP"} · signed in{" "}
                    {new Date(session.createdAt).toLocaleString("en-IN")}
                  </span>
                </div>
                {!session.current && (
                  <button className="btn ghost btn--sm" type="button" onClick={() => setRevoking(session)}>
                    Sign out
                  </button>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>

      <section className="card">
        <h2 className="section-title">What your role lets you do</h2>
        <p className="muted">
          These are the {access.permissions.size} permissions attached to your account. Anything outside this
          list is hidden from your navigation.
        </p>
        <div className="perm-matrix">
          {heldByGroup.map((group) => (
            <div className="perm-group" key={group.label}>
              <span className="perm-group__label">{group.label}</span>
              <div className="chips">
                {group.held.map((permission) => (
                  <span className="chip chip--granted" key={permission}>
                    <Icon name="check" />
                    {permissionLabel(permission)}
                  </span>
                ))}
              </div>
            </div>
          ))}
        </div>
      </section>

      <ConfirmDialog
        open={revoking !== null}
        title="Sign out this device?"
        description={
          revoking
            ? `${describeDevice(revoking)} will need to sign in again. Use this if you don't recognise it.`
            : undefined
        }
        confirmLabel="Sign it out"
        destructive
        busy={revokeBusy}
        onConfirm={() => void revoke()}
        onClose={() => setRevoking(null)}
      />
    </div>
  );
}
