import { FormEvent, useEffect, useState } from "react";
import { useAuth } from "../lib/auth";
import { api } from "../lib/api";
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
  const workspaceId = selectedWorkspaceId(user);
  const [members, setMembers] = useState<Member[]>([]);
  const [invites, setInvites] = useState<Invitation[]>([]);
  const [email, setEmail] = useState("");
  const [role, setRole] = useState("STAFF");
  const [error, setError] = useState<string | null>(null);
  const [token, setToken] = useState<string | null>(null);

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

  return (
    <div className="page">
      <div className="page-title">
        <div>
          <h1>People</h1>
          <p>Join requests stay pending until you approve. Invites carry a one-time token.</p>
        </div>
      </div>
      {error && <div className="error">{error}</div>}
      {token && <div className="card">Give them this invite token: <strong>{token}</strong></div>}

      <form className="card row" onSubmit={invite}>
        <input className="field" type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="email@shop" required />
        <select className="select" value={role} onChange={(e) => setRole(e.target.value)} style={{ width: 160 }}>
          <option>STAFF</option>
          <option>TECHNICIAN</option>
          <option>MANAGER</option>
          <option>ADMIN</option>
        </select>
        <button className="btn">Invite</button>
      </form>

      <div className="card tight">
        {members.map((member) => (
          <div className="category-row" key={member.membershipId}>
            <div>
              <div style={{ fontWeight: 650 }}>{member.fullName}</div>
              <div className="faint">{member.email} · {member.role}</div>
            </div>
            <span className={`badge ${member.status === "ACTIVE" ? "GREEN" : "neutral"}`}>{member.status}</span>
            <div className="row">
              {member.status === "PENDING" && (
                <>
                  <button className="btn" type="button" onClick={() => void decide(member.membershipId, "approve")}>Approve</button>
                  <button className="btn ghost" type="button" onClick={() => void decide(member.membershipId, "reject")}>Reject</button>
                </>
              )}
              {member.status === "ACTIVE" && member.role !== "OWNER" && (
                <>
                  <button className="btn ghost" type="button" onClick={() => void decide(member.membershipId, "suspend")}>Suspend</button>
                  <button className="btn ghost" type="button" onClick={() => void decide(member.membershipId, "remove")}>Remove</button>
                </>
              )}
            </div>
          </div>
        ))}
      </div>

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
