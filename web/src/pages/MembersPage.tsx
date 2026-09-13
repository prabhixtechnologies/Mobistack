import { FormEvent, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { useAuth } from "../lib/auth";
import { useAccess } from "../lib/access";
import { api } from "../lib/api";
import { EmptyState } from "../ui/EmptyState";
import { PageHeader } from "../ui/PageHeader";
import { ConfirmDialog } from "../ui/Modal";
import { selectedWorkspaceId } from "../lib/types";
import type { PageResponse } from "../lib/types";

interface Member {
  membershipId: string;
  userId: string;
  fullName: string;
  email: string;
  role: string;
  status: string;
}

interface Invitation {
  id: string;
  email?: string;
  role: string;
  status: string;
  token?: string;
}

export function MembersPage() {
  const { user } = useAuth();
  const access = useAccess();
  const workspaceId = selectedWorkspaceId(user);
  const canApprove = access.has("USER_WRITE");
  const canInvite = access.has("USER_INVITE");
  const [members, setMembers] = useState<Member[]>([]);
  const [invites, setInvites] = useState<Invitation[]>([]);
  const [email, setEmail] = useState("");
  const [role, setRole] = useState("STAFF");
  const [error, setError] = useState<string | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [purgeTarget, setPurgeTarget] = useState<Member | null>(null);
  const [purgeBusy, setPurgeBusy] = useState(false);
  const [purgeError, setPurgeError] = useState<string | null>(null);

  async function load() {
    if (!workspaceId) return;
    const [memberPage, invitationRows] = await Promise.all([
      api<PageResponse<Member>>(`/api/v1/workspaces/${workspaceId}/members?size=50`),
      api<Invitation[]>(`/api/v1/workspaces/${workspaceId}/invitations`).catch(() => []),
    ]);
    setMembers(memberPage.content);
    setInvites(invitationRows);
  }

  useEffect(() => {
    load().catch((err: Error) => setError(err.message));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [workspaceId]);

  async function decide(membershipId: string, action: "approve" | "reject" | "suspend" | "remove") {
    if (!workspaceId) return;
    try {
      await api(`/api/v1/workspaces/${workspaceId}/members/${membershipId}/${action}`, {
        method: "POST",
        body: action === "approve" ? JSON.stringify({ role: "STAFF" }) : undefined,
      });
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not update member");
    }
  }

  async function purgeMember() {
    if (!workspaceId || !purgeTarget) return;
    setPurgeBusy(true);
    setPurgeError(null);
    try {
      await api(`/api/v1/workspaces/${workspaceId}/members/${purgeTarget.membershipId}/purge`, {
        method: "POST",
      });
      setPurgeTarget(null);
      await load();
    } catch (err) {
      setPurgeError(err instanceof Error ? err.message : "Could not delete");
    } finally {
      setPurgeBusy(false);
    }
  }

  async function invite(event: FormEvent) {
    event.preventDefault();
    if (!workspaceId) return;
    try {
      const created = await api<Invitation>(`/api/v1/workspaces/${workspaceId}/invitations`, {
        method: "POST",
        body: JSON.stringify({ email, role }),
      });
      setToken(created.token ?? null);
      setEmail("");
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not invite");
    }
  }

  const pendingCount = members.filter((member) => member.status === "PENDING").length;
  const orderedMembers = useMemo(
    () =>
      [...members].sort((a, b) => {
        if (a.status === "PENDING" && b.status !== "PENDING") return -1;
        if (a.status !== "PENDING" && b.status === "PENDING") return 1;
        return a.fullName.localeCompare(b.fullName);
      }),
    [members],
  );

  return (
    <div className="page">
      <PageHeader
        kicker="People"
        title="People"
        subtitle="Paid join requests land here. Owners, admins, and anyone with People edit permission can approve."
      />
      {error && <div className="error">{error}</div>}
      {token && <div className="card">Give them this invite token: <strong>{token}</strong></div>}
      {pendingCount > 0 && (
        <div className="banner">
          {pendingCount} join {pendingCount === 1 ? "request" : "requests"} waiting.
          {canApprove ? " Approve to add them as staff." : " Ask an owner or admin to approve."}
        </div>
      )}
      <div className="card">
        Approvers are the shop's owners, admins, and anyone given People edit permission.
        To let someone else approve, open{" "}
        <Link to="/users">Access control</Link>, edit them, and assign the Admin role. They need
        to reopen this workspace so the new permission applies.
      </div>

      {canInvite && (
      <form className="card row" onSubmit={invite}>
        <input className="field" type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="email@shop" aria-label="Invitee email" required />
        <select className="select" value={role} onChange={(e) => setRole(e.target.value)} style={{ width: 160, maxWidth: "100%" }} aria-label="Invitee role">
          <option>STAFF</option>
          <option>TECHNICIAN</option>
          <option>MANAGER</option>
          <option>ADMIN</option>
        </select>
        <button className="btn">Invite</button>
      </form>
      )}

      <div className="card tight">
        {orderedMembers.length === 0 ? (
          <EmptyState
            compact
            icon="people"
            title="No people yet"
            hint="Invite someone above, or wait for a paid join request to appear."
          />
        ) : (
          orderedMembers.map((member) => (
          <div className="category-row" key={member.membershipId}>
            <div>
              <div style={{ fontWeight: 650 }}>{member.fullName}</div>
              <div className="faint">{member.email} · {member.role}</div>
            </div>
            <span className={`badge ${member.status === "ACTIVE" ? "GREEN" : member.status === "REMOVED" || member.status === "REJECTED" ? "RED" : "neutral"}`}>{member.status}</span>
            <div className="row">
              {member.status === "PENDING" && canApprove && (
                <>
                  <button className="btn" type="button" onClick={() => void decide(member.membershipId, "approve")}>Approve</button>
                  <button className="btn ghost" type="button" onClick={() => void decide(member.membershipId, "reject")}>Reject</button>
                </>
              )}
              {member.status === "ACTIVE" && member.role !== "OWNER" && canApprove && (
                <>
                  <button className="btn ghost" type="button" onClick={() => void decide(member.membershipId, "suspend")}>Suspend</button>
                  <button className="btn ghost" type="button" onClick={() => void decide(member.membershipId, "remove")}>Remove</button>
                </>
              )}
              {(member.status === "REMOVED" || member.status === "REJECTED") && canApprove && (
                <button
                  className="btn danger-ghost btn--sm"
                  type="button"
                  onClick={() => {
                    setPurgeError(null);
                    setPurgeTarget(member);
                  }}
                >
                  Delete permanently
                </button>
              )}
            </div>
          </div>
          ))
        )}
      </div>

      <ConfirmDialog
        open={purgeTarget !== null}
        title="Delete this person from the list?"
        description={
          purgeTarget
            ? `Remove ${purgeTarget.fullName} from this shop. If they have no other shops, their login is deleted so this email can register again.`
            : undefined
        }
        confirmLabel="Delete permanently"
        destructive
        busy={purgeBusy}
        error={purgeError}
        onConfirm={() => void purgeMember()}
        onClose={() => {
          if (!purgeBusy) {
            setPurgeTarget(null);
            setPurgeError(null);
          }
        }}
      />

      {invites.length > 0 && (
        <div className="card">
          <strong>Open invitations</strong>
          {invites.map((inviteRow) => (
            <div className="muted" key={inviteRow.id}>{inviteRow.email} · {inviteRow.role} · {inviteRow.status}</div>
          ))}
        </div>
      )}
    </div>
  );
}
