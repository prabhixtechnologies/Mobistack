import { useMemo, useState } from "react";
import { api } from "../lib/api";
import { useAccess } from "../lib/access";
import { useResource } from "../lib/useResource";
import { PERMISSION_GROUPS, permissionLabel, roleMeta } from "../lib/permissions";
import type { PageResponse, WorkspaceRole, WorkspaceUser } from "../lib/types";
import { PageHeader } from "../ui/PageHeader";
import { DataTable, type Column } from "../ui/DataTable";
import { EmptyState, ErrorState } from "../ui/EmptyState";
import { Modal } from "../ui/Modal";
import { TextField } from "../ui/Field";
import { Tabs, TabPanel } from "../ui/Tabs";
import { PermissionGate } from "../ui/PermissionGate";
import { useToast } from "../ui/Toast";
import { Icon } from "../ui/navIcons";

interface UserDraft {
  id?: string;
  fullName: string;
  email: string;
  phone: string;
  password: string;
  roles: string[];
}

const BLANK: UserDraft = { fullName: "", email: "", phone: "", password: "", roles: [] };

function relativeDate(iso: string | undefined): string {
  if (!iso) {
    return "Never";
  }
  const days = Math.floor((Date.now() - new Date(iso).getTime()) / 86_400_000);
  if (days === 0) {
    return "Today";
  }
  if (days === 1) {
    return "Yesterday";
  }
  return days < 30 ? `${days} days ago` : new Date(iso).toLocaleDateString("en-IN");
}

/**
 * Workspace access administration: the accounts that exist, the roles they hold,
 * and what each role can do.
 *
 * The `/api/v1/users` and `/api/v1/roles` endpoints already existed with no UI in
 * front of them, so owners had no way to add an account or change a role after
 * the initial invite.
 */
export function AccessControlPage() {
  const access = useAccess();
  const toast = useToast();
  const [tab, setTab] = useState("users");

  const users = useResource<PageResponse<WorkspaceUser>>("/api/v1/users?size=100");
  const roles = useResource<WorkspaceRole[]>("/api/v1/roles");

  const [draft, setDraft] = useState<UserDraft | null>(null);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const roleOptions = useMemo(
    () => [...(roles.data ?? [])].sort((a, b) => a.seniority - b.seniority),
    [roles.data],
  );

  const openCreate = () => {
    setFormError(null);
    setDraft({ ...BLANK });
  };

  const openEdit = (user: WorkspaceUser) => {
    setFormError(null);
    setDraft({
      id: user.id,
      fullName: user.fullName,
      email: user.email,
      phone: user.phone ?? "",
      password: "",
      roles: [...user.roles],
    });
  };

  const save = async () => {
    if (!draft) {
      return;
    }
    if (draft.roles.length === 0) {
      setFormError("Pick at least one role — an account with no role can't sign in usefully.");
      return;
    }
    setSaving(true);
    setFormError(null);
    try {
      if (draft.id) {
        await api(`/api/v1/users/${draft.id}`, {
          method: "PUT",
          body: JSON.stringify({
            fullName: draft.fullName,
            phone: draft.phone || undefined,
            roles: draft.roles,
            active: true,
          }),
        });
        toast.success(`${draft.fullName} updated.`);
      } else {
        await api("/api/v1/users", {
          method: "POST",
          body: JSON.stringify({
            fullName: draft.fullName,
            email: draft.email,
            phone: draft.phone || undefined,
            password: draft.password,
            roles: draft.roles,
            mustChangePassword: true,
          }),
        });
        toast.success(`${draft.fullName} can now sign in and will set their own password.`);
      }
      setDraft(null);
      users.reload();
    } catch (cause) {
      setFormError(cause instanceof Error ? cause.message : "Saving failed.");
    } finally {
      setSaving(false);
    }
  };

  const setActive = async (user: WorkspaceUser, active: boolean) => {
    try {
      await api(`/api/v1/users/${user.id}`, {
        method: "PUT",
        body: JSON.stringify({
          fullName: user.fullName,
          phone: user.phone ?? undefined,
          roles: [...user.roles],
          active,
        }),
      });
      toast.success(active ? `${user.fullName} reactivated.` : `${user.fullName} deactivated.`);
      users.reload();
    } catch (cause) {
      toast.fromError(cause);
    }
  };

  const columns: Column<WorkspaceUser>[] = [
    {
      key: "name",
      header: "Person",
      render: (user) => (
        <div className="cell-identity">
          <strong>{user.fullName}</strong>
          <span className="faint">{user.email}</span>
        </div>
      ),
    },
    {
      key: "roles",
      header: "Roles",
      render: (user) => (
        <div className="chips">
          {user.roles.map((code) => (
            <span key={code} className={`badge role-badge tint-${roleMeta(code).tint}`}>
              {roleMeta(code).label}
            </span>
          ))}
        </div>
      ),
    },
    { key: "phone", header: "Phone", render: (user) => user.phone ?? <span className="faint">—</span> },
    { key: "last", header: "Last signed in", render: (user) => relativeDate(user.lastLoginAt) },
    {
      key: "status",
      header: "Status",
      render: (user) => (
        <span className={`badge ${user.active ? "GREEN" : "neutral"}`}>{user.active ? "Active" : "Disabled"}</span>
      ),
    },
    {
      key: "actions",
      header: "",
      align: "right",
      need: "USER_WRITE",
      render: (user) => (
        <div className="row row--end">
          <button className="btn ghost btn--sm" type="button" onClick={() => openEdit(user)}>
            <Icon name="edit" />
            Edit
          </button>
          <button
            className={`btn btn--sm ${user.active ? "danger-ghost" : "ghost"}`}
            type="button"
            onClick={() => void setActive(user, !user.active)}
          >
            {user.active ? "Disable" : "Enable"}
          </button>
        </div>
      ),
    },
  ];

  return (
    <div className="page">
      <PageHeader
        kicker="Access control"
        title="Who can do what"
        subtitle="Accounts in this workspace, the roles they hold, and the permissions each role carries."
        actions={
          <PermissionGate need="USER_WRITE">
            <button className="btn" type="button" onClick={openCreate}>
              <Icon name="plus" />
              Add person
            </button>
          </PermissionGate>
        }
      />

      <Tabs
        tabs={[
          { id: "users", label: "People", badge: users.data?.totalElements ?? undefined },
          { id: "roles", label: "Roles & permissions", badge: roleOptions.length || undefined },
        ]}
        active={tab}
        onChange={setTab}
      />

      <TabPanel id="users" active={tab}>
        <div className="card tight">
          <DataTable
            columns={columns}
            rows={users.data?.content}
            rowKey={(user) => user.id}
            loading={users.loading}
            error={users.error}
            onRetry={users.reload}
            empty={{
              icon: "people",
              title: "No accounts yet",
              hint: "Add the people who work this counter so each one signs in as themselves.",
              action: access.has("USER_WRITE") ? (
                <button className="btn" type="button" onClick={openCreate}>
                  Add the first person
                </button>
              ) : undefined,
            }}
          />
        </div>
      </TabPanel>

      <TabPanel id="roles" active={tab}>
        {roles.error ? (
          <ErrorState message={roles.error} onRetry={roles.reload} />
        ) : roleOptions.length === 0 ? (
          <EmptyState icon="key" title="No roles returned" hint="The workspace has no assignable roles." />
        ) : (
          <div className="role-grid">
            {roleOptions.map((role) => (
              <RoleCard key={role.id} role={role} mine={access.role === role.code} />
            ))}
          </div>
        )}
      </TabPanel>

      <Modal
        open={draft !== null}
        title={draft?.id ? `Edit ${draft.fullName}` : "Add a person"}
        description={
          draft?.id
            ? "Change their name, phone or roles. Email is the sign-in identity and can't be edited here."
            : "They'll sign in with this email and be asked to choose a new password on first use."
        }
        onClose={() => setDraft(null)}
        footer={
          <>
            <button className="btn ghost" type="button" onClick={() => setDraft(null)} disabled={saving}>
              Cancel
            </button>
            <button className="btn" type="button" onClick={() => void save()} disabled={saving}>
              {saving ? "Saving…" : draft?.id ? "Save changes" : "Create account"}
            </button>
          </>
        }
      >
        {draft && (
          <div className="stack">
            {formError && <p className="error">{formError}</p>}

            <TextField
              label="Full name"
              required
              value={draft.fullName}
              autoComplete="name"
              onChange={(event) => setDraft({ ...draft, fullName: event.target.value })}
            />

            {!draft.id && (
              <>
                <TextField
                  label="Email"
                  type="email"
                  required
                  value={draft.email}
                  autoComplete="email"
                  hint="Used as the sign-in identity."
                  onChange={(event) => setDraft({ ...draft, email: event.target.value })}
                />
                <TextField
                  label="Temporary password"
                  type="password"
                  required
                  minLength={8}
                  value={draft.password}
                  autoComplete="new-password"
                  hint="At least 8 characters. They'll be forced to replace it at first sign-in."
                  onChange={(event) => setDraft({ ...draft, password: event.target.value })}
                />
              </>
            )}

            <TextField
              label="Phone"
              type="tel"
              value={draft.phone}
              autoComplete="tel"
              onChange={(event) => setDraft({ ...draft, phone: event.target.value })}
            />

            <fieldset className="role-picker">
              <legend>Roles</legend>
              {roleOptions.map((role) => {
                const checked = draft.roles.includes(role.code);
                return (
                  <label key={role.id} className={`role-option${checked ? " role-option--on" : ""}`}>
                    <input
                      type="checkbox"
                      checked={checked}
                      onChange={(event) =>
                        setDraft({
                          ...draft,
                          roles: event.target.checked
                            ? [...draft.roles, role.code]
                            : draft.roles.filter((code) => code !== role.code),
                        })
                      }
                    />
                    <span>
                      <strong>{role.name}</strong>
                      <small className="faint">
                        {role.description ?? `${role.permissions.length} permissions`}
                      </small>
                    </span>
                  </label>
                );
              })}
            </fieldset>
          </div>
        )}
      </Modal>
    </div>
  );
}

/**
 * One role and the capabilities it grants, grouped by area.
 *
 * Reads from the role's live permission set rather than a hardcoded table, so a
 * change to `role_permissions` in the database shows up here without a code edit.
 */
function RoleCard({ role, mine }: { role: WorkspaceRole; mine: boolean }) {
  const granted = new Set(role.permissions);

  return (
    <section className={`card role-card${mine ? " role-card--mine" : ""}`}>
      <header className="role-card__head">
        <div>
          <h3>{role.name}</h3>
          {role.description && <p className="muted">{role.description}</p>}
        </div>
        <div className="role-card__tags">
          {mine && <span className="badge GREEN">Your role</span>}
          {role.systemRole && <span className="badge neutral">Built in</span>}
        </div>
      </header>

      <p className="faint role-card__count">
        {granted.size} of {PERMISSION_GROUPS.reduce((total, group) => total + group.permissions.length, 0)}{" "}
        permissions
      </p>

      <div className="perm-matrix">
        {PERMISSION_GROUPS.map((group) => {
          const held = group.permissions.filter((permission) => granted.has(permission));
          if (held.length === 0) {
            return null;
          }
          return (
            <div className="perm-group" key={group.label}>
              <span className="perm-group__label">{group.label}</span>
              <div className="chips">
                {held.map((permission) => (
                  <span className="chip chip--granted" key={permission}>
                    <Icon name="check" />
                    {permissionLabel(permission)}
                  </span>
                ))}
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
}
