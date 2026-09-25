import { FormEvent, useEffect, useState } from "react";
import { useAuth } from "../lib/auth";
import { api, money } from "../lib/api";
import { collectJoinPayment, type CheckoutOrder } from "../lib/payOrder";
import { TextField } from "../ui/Field";
import { ConfirmDialog } from "../ui/Modal";
import { useToast } from "../ui/Toast";
import { Icon, type NavIconName } from "../ui/navIcons";
import type { MembershipStatus, WorkspaceCard } from "../lib/types";

interface JoinCheckout extends CheckoutOrder {
  shopName?: string;
  alreadyPaid?: boolean;
}

const PATHS: { id: "join" | "create" | "invite"; label: string; hint: string; icon: NavIconName }[] = [
  { id: "join", label: "Join a shop", hint: `Pay ${money.format(50)} with a code`, icon: "link" },
  { id: "create", label: "Create shop", hint: "Open your own counter", icon: "store" },
  { id: "invite", label: "Use invite", hint: "Free token from an owner", icon: "key" },
];

function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) {
    return "SH";
  }
  return parts
    .slice(0, 2)
    .map((part) => part[0] ?? "")
    .join("")
    .toUpperCase();
}

function statusTone(status: MembershipStatus): string {
  if (status === "ACTIVE") return "GREEN";
  if (status === "PENDING" || status === "INVITED") return "ORANGE";
  if (status === "SUSPENDED" || status === "REJECTED") return "RED";
  return "neutral";
}

function statusLabel(status: MembershipStatus): string {
  if (status === "PENDING") return "Waiting";
  if (status === "INVITED") return "Invited";
  if (status === "ACTIVE") return "Active";
  if (status === "SUSPENDED") return "Suspended";
  return status;
}

export function WorkspacesPage() {
  const { user, workspaces, switchWorkspace, createWorkspace, refreshWorkspaces } = useAuth();
  const toast = useToast();
  const [name, setName] = useState("");
  const [city, setCity] = useState("");
  const [joinCode, setJoinCode] = useState("");
  const [inviteToken, setInviteToken] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [addTab, setAddTab] = useState<"join" | "create" | "invite">("join");
  const [cancelTarget, setCancelTarget] = useState<WorkspaceCard | null>(null);
  const [cancelBusy, setCancelBusy] = useState(false);
  const [cancelError, setCancelError] = useState<string | null>(null);

  const empty = workspaces.length === 0;
  const pendingCount = workspaces.filter((workspace) => workspace.status === "PENDING").length;
  const firstName = user?.fullName?.split(/\s+/)[0] ?? "there";

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
    try {
      const checkout = await api<JoinCheckout>("/api/v1/mobistack/workspaces/join/checkout", {
        method: "POST",
        body: JSON.stringify({ joinCode: joinCode.trim() }),
      });
      const payment = await collectJoinPayment(
        checkout,
        joinCode.trim(),
        user ?? undefined,
        checkout.shopName,
        checkout.keyId,
      );
      const card = await api<WorkspaceCard>("/api/v1/mobistack/workspaces/join/complete", {
        method: "POST",
        body: JSON.stringify({
          joinCode: joinCode.trim(),
          razorpay_order_id: payment.razorpay_order_id,
          razorpay_payment_id: payment.razorpay_payment_id,
          razorpay_signature: payment.razorpay_signature,
          orderId: payment.orderId,
        }),
      });
      setJoinCode("");
      await refreshWorkspaces();
      toast.success(
        card.status === "PENDING"
          ? `Paid ₹50 and asked to join ${card.name}. An owner still has to approve.`
          : `Joined ${card.name}.`,
      );
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not join");
    } finally {
      setBusy(false);
    }
  }

  async function onInvite(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api("/api/v1/mobistack/invitations/accept", { method: "POST", body: JSON.stringify({ token: inviteToken }) });
      setInviteToken("");
      await refreshWorkspaces();
      toast.success("Invitation accepted. Open the workspace from the list.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not accept invite");
    } finally {
      setBusy(false);
    }
  }

  async function confirmCancel() {
    if (!cancelTarget) return;
    setCancelBusy(true);
    setCancelError(null);
    try {
      await api(`/api/v1/mobistack/workspaces/join/cancel?id=${cancelTarget.id}`, { method: "POST" });
      await refreshWorkspaces();
      toast.success(`Cancelled the request to join ${cancelTarget.name}.`);
      setCancelTarget(null);
    } catch (err) {
      setCancelError(err instanceof Error ? err.message : "Could not cancel");
    } finally {
      setCancelBusy(false);
    }
  }

  return (
    <div className={`page workspaces-page${empty ? " workspaces-page--empty" : ""}`}>
      {!empty && (
        <header className="workspaces-head">
          <div>
            <p className="page-kicker">Workspace</p>
            <h1>Your shops</h1>
            <p className="muted">Open a shop, or add another on this same screen.</p>
          </div>
          <div className="workspace-counts">
            <span className="workspace-count">
              {workspaces.length} {workspaces.length === 1 ? "shop" : "shops"}
            </span>
            {pendingCount > 0 && (
              <span className="workspace-count workspace-count--wait">{pendingCount} waiting</span>
            )}
          </div>
        </header>
      )}

      {error && <div className="error">{error}</div>}

      <section className="workspace-board">
        <aside className="workspace-board__intro">
          {empty ? (
            <>
              <p className="page-kicker">Get started</p>
              <h1>Hi {firstName}. Set up your first shop.</h1>
              <p className="workspace-board__lead">
                One account can hold many counters. Join with a code, create your own, or paste an invite — all here.
              </p>
            </>
          ) : (
            <>
              <p className="page-kicker">Add a shop</p>
              <h2>Bring another counter onto this account</h2>
              <p className="workspace-board__lead">
                Join, create, or accept an invite without leaving this page.
              </p>
            </>
          )}
          <ul className="workspace-points">
            <li>
              <Icon name="link" />
              <span>Join is {money.format(50)} per shop. The owner still approves.</span>
            </li>
            <li>
              <Icon name="store" />
              <span>Create opens your own workspace. Activation may ask for payment.</span>
            </li>
            <li>
              <Icon name="key" />
              <span>Owner invites are free. Waiting requests can be cancelled here.</span>
            </li>
          </ul>
        </aside>

        <div className="workspace-board__panel">
          {!empty && (
            <div className="workspace-gallery">
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
                  onCancel={() => {
                    setCancelError(null);
                    setCancelTarget(workspace);
                  }}
                />
              ))}
            </div>
          )}

          <div className="workspace-paths" role="tablist" aria-label="How to add a shop">
            {PATHS.map((path) => (
              <button
                key={path.id}
                type="button"
                role="tab"
                aria-selected={addTab === path.id}
                className={`workspace-path${addTab === path.id ? " workspace-path--on" : ""}`}
                onClick={() => setAddTab(path.id)}
              >
                <span className="workspace-path__icon">
                  <Icon name={path.icon} />
                </span>
                <span>
                  <strong>{path.label}</strong>
                  <small>{path.hint}</small>
                </span>
              </button>
            ))}
          </div>

          {addTab === "join" && (
            <form className="workspace-add__form" onSubmit={onJoin}>
              <div className="workspace-code-wrap">
                <TextField
                  label="Join code"
                  value={joinCode}
                  onChange={(e) => setJoinCode(e.target.value.toUpperCase())}
                  placeholder="HUB-7K2P"
                  autoComplete="off"
                  required
                />
              </div>
              <button className="btn" type="submit" disabled={busy || !joinCode.trim()}>
                {busy ? "Working…" : `Pay ${money.format(50)} and request access`}
              </button>
            </form>
          )}
          {addTab === "create" && (
            <form className="workspace-add__form" onSubmit={onCreate}>
              <TextField
                label="Shop name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="ABC Mobile Repair"
                required
              />
              <TextField
                label="City"
                value={city}
                onChange={(e) => setCity(e.target.value)}
                placeholder="Mumbai"
              />
              <button className="btn" type="submit" disabled={busy || !name.trim()}>
                Create and open
              </button>
            </form>
          )}
          {addTab === "invite" && (
            <form className="workspace-add__form" onSubmit={onInvite}>
              <TextField
                label="Invite token"
                value={inviteToken}
                onChange={(e) => setInviteToken(e.target.value)}
                placeholder="Paste token"
                required
              />
              <button className="btn" type="submit" disabled={busy || !inviteToken.trim()}>
                Accept invite
              </button>
            </form>
          )}
        </div>
      </section>

      <ConfirmDialog
        open={cancelTarget !== null}
        title={cancelTarget?.status === "INVITED" ? "Decline this invite?" : "Cancel this request?"}
        description={
          cancelTarget
            ? `Withdraw your request to join ${cancelTarget.name}. The shop will be told you cancelled. The ₹50 you paid stays on this shop, so you can request again without paying again.`
            : undefined
        }
        confirmLabel="Cancel request"
        destructive
        busy={cancelBusy}
        error={cancelError}
        onConfirm={() => void confirmCancel()}
        onClose={() => {
          if (!cancelBusy) {
            setCancelTarget(null);
            setCancelError(null);
          }
        }}
      />
    </div>
  );
}

function WorkspaceTile({
  workspace,
  busy,
  onSelect,
  onCancel,
}: {
  workspace: WorkspaceCard;
  busy: boolean;
  onSelect: () => void;
  onCancel: () => void;
}) {
  const canOpen = workspace.status === "ACTIVE" && !workspace.selected;
  const waiting = workspace.status === "PENDING" || workspace.status === "INVITED";
  const tone = workspace.selected ? "selected" : waiting ? "pending" : workspace.status === "ACTIVE" ? "active" : "idle";

  return (
    <article className={`workspace-tile workspace-tile--${tone}`}>
      <div className="workspace-tile__top">
        <div className="workspace-tile__mark" aria-hidden>
          {initials(workspace.name)}
        </div>
        <div className="workspace-tile__identity">
          <div className="workspace-tile__name">{workspace.name}</div>
          <div className="faint">{[workspace.city, workspace.role].filter(Boolean).join(" · ")}</div>
        </div>
        <span className={`badge ${statusTone(workspace.status)}`}>{statusLabel(workspace.status)}</span>
      </div>
      <div className="workspace-tile__meta">
        <span className="workspace-chip">{workspace.memberCount} members</span>
        <span className="workspace-chip">{workspace.productCount} products</span>
        {workspace.joinCode && <span className="workspace-chip workspace-chip--code">Code {workspace.joinCode}</span>}
      </div>
      {waiting && (
        <p className="workspace-tile__wait">Waiting for an owner or admin of this shop to approve you.</p>
      )}
      <div className="workspace-tile__foot">
        {canOpen ? (
          <button className="btn btn--sm" type="button" disabled={busy} onClick={onSelect}>
            Open shop
          </button>
        ) : workspace.selected ? (
          <span className="workspace-tile__current">
            <Icon name="check" />
            Current shop
          </span>
        ) : waiting ? (
          <button className="btn danger-ghost btn--sm" type="button" disabled={busy} onClick={onCancel}>
            Cancel request
          </button>
        ) : (
          <span className="muted">You cannot open this shop yet.</span>
        )}
      </div>
    </article>
  );
}
