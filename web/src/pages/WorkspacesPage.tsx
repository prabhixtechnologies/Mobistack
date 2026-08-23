import { FormEvent, useEffect, useState } from "react";
import { useAuth } from "../lib/auth";
import { api } from "../lib/api";
import { PageHeader } from "../ui/PageHeader";
import type { WorkspaceCard } from "../lib/types";

export function WorkspacesPage() {
  const { workspaces, switchWorkspace, createWorkspace, joinWorkspace, refreshWorkspaces } = useAuth();
  const [name, setName] = useState("");
  const [city, setCity] = useState("");
  const [joinCode, setJoinCode] = useState("");
  const [inviteToken, setInviteToken] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    refreshWorkspaces().catch((err: Error) => setError(err.message));
    // Load once when the page opens; later mutations refresh themselves.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function onCreate(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await createWorkspace(name, city || undefined);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not create workspace");
      setBusy(false);
    }
  }

  async function onJoin(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const card = await joinWorkspace(joinCode);
      setJoinCode("");
      setNotice(
        card.status === "PENDING"
          ? `Asked to join ${card.name}. An owner still has to approve.`
          : `Joined ${card.name}.`,
      );
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not join");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="page">
      <PageHeader kicker="Workspace" title="My workspaces" subtitle="One account. Many shops. Switch here — never by editing a request." />
      {error && <div className="error">{error}</div>}
      {notice && <div className="muted">{notice}</div>}

      {workspaces.length === 0 ? (
        <div className="card empty">No workspaces yet. Create one or join with a code.</div>
      ) : (
        <div className="grid-3">
          {workspaces.map((workspace) => (
            <WorkspaceTile
              key={workspace.id}
              workspace={workspace}
              busy={busy}
              onSelect={() => {
                setBusy(true);
                void switchWorkspace(workspace.id).catch((err: Error) => {
                  setError(err.message);
                  setBusy(false);
                });
              }}
            />
          ))}
        </div>
      )}

      <div className="grid-2">
        <form className="card stack" onSubmit={onCreate}>
          <strong>Create a workspace</strong>
          <label className="stack">
            <span className="faint">Name</span>
            <input className="field" value={name} onChange={(e) => setName(e.target.value)} required />
          </label>
          <label className="stack">
            <span className="faint">City</span>
            <input className="field" value={city} onChange={(e) => setCity(e.target.value)} />
          </label>
          <button className="btn" type="submit" disabled={busy || !name.trim()}>
            Create and open
          </button>
        </form>
        <form className="card stack" onSubmit={onJoin}>
          <strong>Join with a code</strong>
          <p className="muted" style={{ margin: 0 }}>
            This only files a request. An owner still has to approve.
          </p>
          <label className="stack">
            <span className="faint">Join code</span>
            <input
              className="field"
              value={joinCode}
              onChange={(e) => setJoinCode(e.target.value.toUpperCase())}
              placeholder="HUB-7K2P"
              required
            />
          </label>
          <button className="btn ghost" type="submit" disabled={busy || !joinCode.trim()}>
            Request access
          </button>
        </form>
        <form
          className="card stack"
          onSubmit={async (event) => {
            event.preventDefault();
            setBusy(true);
            setError(null);
            try {
              await api("/api/v1/invitations/accept", { method: "POST", body: JSON.stringify({ token: inviteToken }) });
              setInviteToken("");
              setNotice("Invitation accepted. Open the workspace from the list.");
              await refreshWorkspaces();
            } catch (err) {
              setError(err instanceof Error ? err.message : "Could not accept invite");
            } finally {
              setBusy(false);
            }
          }}
        >
          <strong>Accept an invite</strong>
          <input className="field" value={inviteToken} onChange={(e) => setInviteToken(e.target.value)} placeholder="Invite token" />
          <button className="btn ghost" type="submit" disabled={busy || !inviteToken.trim()}>
            Accept
          </button>
        </form>
      </div>
    </div>
  );
}

function WorkspaceTile({
  workspace,
  busy,
  onSelect,
}: {
  workspace: WorkspaceCard;
  busy: boolean;
  onSelect: () => void;
}) {
  const canOpen = workspace.status === "ACTIVE" && !workspace.selected;
  return (
    <article className={workspace.selected ? "card workspace-card selected" : "card workspace-card"}>
      <div className="spread">
        <div>
          <div style={{ fontWeight: 700 }}>{workspace.name}</div>
          <div className="faint" style={{ fontSize: 13 }}>
            {[workspace.city, workspace.role].filter(Boolean).join(" · ")}
          </div>
        </div>
        <span className={`badge ${workspace.status === "ACTIVE" ? "GREEN" : "neutral"}`}>{workspace.status}</span>
      </div>
      <div className="muted" style={{ fontSize: 13 }}>
        {workspace.memberCount} members · {workspace.productCount} products
      </div>
      {workspace.joinCode && (
        <div className="faint" style={{ fontSize: 13 }}>
          Join code <strong>{workspace.joinCode}</strong>
        </div>
      )}
      {canOpen ? (
        <button className="btn" type="button" disabled={busy} onClick={onSelect}>
          Open
        </button>
      ) : workspace.selected ? (
        <div className="muted">Selected</div>
      ) : (
        <div className="muted">Waiting for approval</div>
      )}
    </article>
  );
}
