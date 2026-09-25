import { FormEvent, useEffect, useState } from "react";
import { api } from "../lib/api";
import {
  canManageGroup,
  type FitmentGroup,
  type FitmentGroupDetail,
  type FitmentMember,
} from "../lib/groups";
import { TextField } from "./Field";
import { Modal } from "./Modal";

export function FitmentGroupBar({
  groups,
  selected,
  choose,
  create,
  current,
}: {
  groups: FitmentGroup[];
  selected: string | null;
  choose: (id: string) => void;
  create: (name: string) => Promise<void>;
  current: FitmentGroup | null;
}) {
  const [open, setOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [name, setName] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onCreate(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await create(name.trim());
      setName("");
      setCreating(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not create the group");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="spread" style={{ marginBottom: 16, gap: 12, flexWrap: "wrap" }}>
      {creating && (
        <form className="spread" onSubmit={(event) => void onCreate(event)}>
          <TextField label="Group name" value={name} onChange={(event) => setName(event.target.value)} />
          <button className="btn" type="submit" disabled={busy || name.trim() === ""}>
            {busy ? "Creating…" : "Create"}
          </button>
          <button className="btn ghost" type="button" onClick={() => setCreating(false)}>
            Cancel
          </button>
          {error && <div className="error">{error}</div>}
        </form>
      )}
      {groups.length > 1 ? (
        <div className="method-tabs" role="tablist" aria-label="Fitment groups">
          {groups.map((group) => (
            <button
              key={group.id}
              className={`method-tab ${group.id === selected ? "on" : ""}`}
              type="button"
              role="tab"
              aria-selected={group.id === selected}
              onClick={() => choose(group.id)}
            >
              {group.name}
            </button>
          ))}
        </div>
      ) : (
        <strong>{groups[0]?.name}</strong>
      )}
      {canManageGroup(current) && (
        <button className="btn ghost" type="button" onClick={() => setOpen(true)}>
          Members
        </button>
      )}
      {!creating && (
        <button className="btn ghost" type="button" onClick={() => setCreating(true)}>
          New group
        </button>
      )}
      {current && (
        <MembersDialog
          open={open}
          groupId={current.id}
          owner={current.callerRole === "OWNER"}
          onClose={() => setOpen(false)}
        />
      )}
    </div>
  );
}

function MembersDialog({
  open,
  groupId,
  owner,
  onClose,
}: {
  open: boolean;
  groupId: string;
  owner: boolean;
  onClose: () => void;
}) {
  const [detail, setDetail] = useState<FitmentGroupDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [email, setEmail] = useState("");
  const [joinCode, setJoinCode] = useState("");
  const [requests, setRequests] = useState<{ id: string; shopName: string; askedBy: string }[]>([]);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!open) {
      return;
    }
    let cancelled = false;
    api<FitmentGroupDetail>(`/api/v1/groups/${groupId}`)
      .then((loaded) => {
        if (!cancelled) {
          setDetail(loaded);
        }
      })
      .catch((err: Error) => {
        if (!cancelled) {
          setError(err.message);
        }
      });
    api<{ id: string; shopName: string; askedBy: string }[]>(`/api/v1/groups/${groupId}/requests`)
      .then((loaded) => {
        if (!cancelled) setRequests(loaded);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [open, groupId]);

  async function run(task: () => Promise<void>) {
    setBusy(true);
    setError(null);
    try {
      await task();
      setDetail(await api<FitmentGroupDetail>(`/api/v1/groups/${groupId}`));
      setRequests(await api(`/api/v1/groups/${groupId}/requests`));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not update the group");
    } finally {
      setBusy(false);
    }
  }

  async function onPerson(event: FormEvent, role: "MEMBER" | "ADMIN") {
    event.preventDefault();
    await run(async () => {
      await api(`/api/v1/groups/${groupId}/people`, {
        method: "POST",
        body: JSON.stringify({ email, role }),
      });
      setEmail("");
    });
  }

  async function onShop(event: FormEvent) {
    event.preventDefault();
    await run(async () => {
      await api(`/api/v1/groups/${groupId}/shops`, {
        method: "POST",
        body: JSON.stringify({ joinCode }),
      });
      setJoinCode("");
    });
  }

  return (
    <Modal open={open} title="Members" onClose={onClose}>
      {error && <div className="error">{error}</div>}
      {detail?.joinCode && (
        <p className="faint">Union code {detail.joinCode}. A new shop enters this on Join the union.</p>
      )}
      <div className="stack">
        {requests.map((request) => (
          <div className="spread" key={request.id}>
            <div>
              <strong>{request.shopName}</strong>
              <div className="faint">{request.askedBy} paid and is waiting</div>
            </div>
            <button
              className="btn"
              type="button"
              disabled={busy}
              onClick={() =>
                void run(async () => {
                  await api(`/api/v1/groups/${groupId}/requests/${request.id}/approve`, { method: "POST" });
                })
              }
            >
              Admit
            </button>
            <button
              className="btn ghost"
              type="button"
              disabled={busy}
              onClick={() =>
                void run(async () => {
                  await api(`/api/v1/groups/${groupId}/requests/${request.id}/reject`, { method: "POST" });
                })
              }
            >
              Refuse
            </button>
          </div>
        ))}
        {(detail?.members ?? []).map((member) => (
          <MemberRow
            key={`${member.kind}-${member.subjectId}`}
            member={member}
            owner={owner}
            busy={busy}
            onRemove={() =>
              void run(async () => {
                const path =
                  member.kind === "SHOP"
                    ? `/api/v1/groups/${groupId}/shops/${member.subjectId}`
                    : `/api/v1/groups/${groupId}/people/${member.subjectId}`;
                await api(path, { method: "DELETE" });
              })
            }
            onDismiss={() =>
              void run(() =>
                api(`/api/v1/groups/${groupId}/people/${member.subjectId}`, {
                  method: "PUT",
                  body: JSON.stringify({ role: "MEMBER" }),
                }),
              )
            }
          />
        ))}
        <form className="stack" onSubmit={(event) => void onPerson(event, "MEMBER")}>
          <TextField label="Person email" value={email} onChange={(event) => setEmail(event.target.value)} />
          <div className="spread">
            <button className="btn" type="submit" disabled={busy || email.trim() === ""}>
              Add person
            </button>
            <button
              className="btn ghost"
              type="button"
              disabled={busy || email.trim() === ""}
              onClick={(event) => void onPerson(event, "ADMIN")}
            >
              Add as admin
            </button>
          </div>
        </form>
        <form className="stack" onSubmit={(event) => void onShop(event)}>
          <TextField label="Shop join code" value={joinCode} onChange={(event) => setJoinCode(event.target.value)} />
          <button className="btn" type="submit" disabled={busy || joinCode.trim() === ""}>
            Add shop
          </button>
        </form>
      </div>
    </Modal>
  );
}

function MemberRow({
  member,
  owner,
  busy,
  onRemove,
  onDismiss,
}: {
  member: FitmentMember;
  owner: boolean;
  busy: boolean;
  onRemove: () => void;
  onDismiss: () => void;
}) {
  const locked = member.role === "OWNER" || (member.role === "ADMIN" && !owner);
  return (
    <div className="spread">
      <div>
        <strong>{member.label}</strong>
        <div className="faint">
          {member.kind === "SHOP" ? "Shop" : "Person"} · {member.role.toLowerCase()}
        </div>
      </div>
      {member.role === "ADMIN" && owner && (
        <button className="btn ghost" type="button" disabled={busy} onClick={onDismiss}>
          Dismiss admin
        </button>
      )}
      {!locked && (
        <button className="btn ghost" type="button" disabled={busy} onClick={onRemove}>
          Remove
        </button>
      )}
    </div>
  );
}
