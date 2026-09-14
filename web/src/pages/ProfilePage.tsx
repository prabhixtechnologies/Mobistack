import { useAuth } from "../lib/auth";
import { useAccess } from "../lib/access";
import { permissionLabel, PERMISSION_GROUPS } from "../lib/permissions";
import { ACCOUNT_URL } from "../lib/config";
import { PageHeader } from "../ui/PageHeader";
import { Icon } from "../ui/navIcons";

/**
 * Self-service account screen: who you are here, and a deep-link to Identity for
 * password, passkeys and signed-in devices.
 */
export function ProfilePage() {
  const { user } = useAuth();
  const access = useAccess();

  const heldByGroup = PERMISSION_GROUPS.map((group) => ({
    label: group.label,
    held: group.permissions.filter((permission) => access.permissions.has(permission)),
  })).filter((group) => group.held.length > 0);

  return (
    <div className="page">
      <PageHeader
        kicker="My account"
        title={user?.fullName ?? "My profile"}
        subtitle="Your identity in this workspace. Password and sessions are managed on Prabhix Identity."
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
          <h2 className="section-title">Password and sessions</h2>
          <p className="muted">
            Change your password, manage passkeys, and see signed-in devices on your Prabhix Identity
            account.
          </p>
          {ACCOUNT_URL ? (
            <a className="btn" href={ACCOUNT_URL}>
              Manage account on Identity
            </a>
          ) : (
            <p className="faint">Identity is not configured in this build.</p>
          )}
        </section>
      </div>

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
    </div>
  );
}
