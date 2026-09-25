import { FormEvent, useEffect, useState, type ReactNode } from "react";
import { api } from "../lib/api";
import { useAuth } from "../lib/auth";
import { collectJoinPayment, type CheckoutOrder } from "../lib/payOrder";
import type { FitmentGroup } from "../lib/groups";
import { AuthGate } from "./LoginPage";
import { selectedWorkspaceId } from "../lib/types";

type Step = "home" | "create" | "join" | "invite";
type GateKind = "loading" | "start" | "joinUnion" | "waitingShop" | "waitingUnion" | "outside" | "catalog";

interface JoinCheckout extends CheckoutOrder {
  shopName?: string;
  alreadyPaid?: boolean;
}

interface JoinState {
  status: string;
  groupName?: string | null;
}

export function useShopGate(): GateKind {
  const { user, workspaces } = useAuth();
  const [groups, setGroups] = useState<FitmentGroup[] | null>(null);
  const [pendingGroup, setPendingGroup] = useState<string | null | undefined>(undefined);
  const active = workspaces.filter((row) => row.status === "ACTIVE");
  const shopId = selectedWorkspaceId(user);
  const open = active.find((row) => row.id === shopId) ?? active.find((row) => row.selected) ?? active[0];

  useEffect(() => {
    if (!open) {
      setGroups(null);
      setPendingGroup(undefined);
      return;
    }
    let cancelled = false;
    api<FitmentGroup[]>("/api/v1/mobistack/groups")
      .then((list) => {
        if (!cancelled) setGroups(list);
      })
      .catch(() => {
        if (!cancelled) setGroups([]);
      });
    return () => {
      cancelled = true;
    };
  }, [open?.id]);

  useEffect(() => {
    if (!open || !groups || groups.length > 0 || open.role !== "OWNER") {
      setPendingGroup(undefined);
      return;
    }
    let cancelled = false;
    api<JoinState>("/api/v1/mobistack/groups/join")
      .then((row) => {
        if (!cancelled) setPendingGroup(row.status === "PENDING" ? row.groupName ?? "the union" : null);
      })
      .catch(() => {
        if (!cancelled) setPendingGroup(null);
      });
    return () => {
      cancelled = true;
    };
  }, [open?.id, open?.role, groups]);

  if (!user) return "loading";
  if (active.length === 0) {
    const waiting = workspaces.some((row) => row.status === "PENDING" || row.status === "INVITED");
    return waiting ? "waitingShop" : "start";
  }
  if (!groups || (groups.length === 0 && open?.role === "OWNER" && pendingGroup === undefined)) {
    return "loading";
  }
  if (groups.length > 0) return "catalog";
  if (open?.role !== "OWNER") return "outside";
  return pendingGroup ? "waitingUnion" : "joinUnion";
}

export function ShopJourneyPage({ gate }: { gate: Exclude<GateKind, "loading" | "catalog"> }) {
  const [step, setStep] = useState<Step>("home");
  if (gate === "waitingShop" || gate === "waitingUnion") {
    return <Waiting union={gate === "waitingUnion"} />;
  }
  if (gate === "outside") return <Outside />;
  if (gate === "joinUnion") return <CodeJoin group onBack={null} />;
  if (step === "create") return <CreateShop onBack={() => setStep("home")} />;
  if (step === "join") return <CodeJoin group={false} onBack={() => setStep("home")} />;
  if (step === "invite") return <Invite onBack={() => setStep("home")} />;
  return <Start onCreate={() => setStep("create")} onJoin={() => setStep("join")} onInvite={() => setStep("invite")} />;
}

function Start({ onCreate, onJoin, onInvite }: { onCreate: () => void; onJoin: () => void; onInvite: () => void }) {
  const { logout } = useAuth();
  return (
    <Journey title="Your shop" body="Create your own counter, or join a shop that already exists.">
      <button className="auth-submit" type="button" onClick={onCreate}>
        Create shop
      </button>
      <button className="auth-submit auth-submit--secondary" type="button" onClick={onJoin}>
        Join a shop
      </button>
      <button className="auth-text" type="button" onClick={onInvite}>
        I have an invite
      </button>
      <button className="auth-text" type="button" onClick={() => void logout()}>
        Sign out
      </button>
    </Journey>
  );
}

function CreateShop({ onBack }: { onBack: () => void }) {
  const { createWorkspace } = useAuth();
  const [name, setName] = useState("");
  const [city, setCity] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (name.trim() === "") {
      setError("A shop needs a name.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await createWorkspace(name.trim(), city.trim() || undefined);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not create the shop");
      setBusy(false);
    }
  }

  return (
    <Journey title="Create shop" body="This does not ask for payment. You join the union on the next screen.">
      <form className="journey-form" onSubmit={(event) => void onSubmit(event)}>
        <label className="auth-label">
          Shop name
          <input className="field" value={name} onChange={(event) => setName(event.target.value)} required />
        </label>
        <label className="auth-label">
          City
          <input className="field" value={city} onChange={(event) => setCity(event.target.value)} />
        </label>
        {error && <p className="auth-error" role="alert">{error}</p>}
        <button className="auth-submit" type="submit" disabled={busy || name.trim() === ""}>
          {busy ? "Creating…" : "Create shop"}
        </button>
        <button className="auth-text" type="button" onClick={onBack}>
          Back
        </button>
      </form>
    </Journey>
  );
}

function CodeJoin({ group, onBack }: { group: boolean; onBack: (() => void) | null }) {
  const { user, refreshUser, refreshWorkspaces } = useAuth();
  const [code, setCode] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    const joinCode = code.trim();
    if (joinCode === "") return;
    setBusy(true);
    setError(null);
    try {
      const checkout = await api<JoinCheckout>(group ? "/api/v1/mobistack/groups/join/checkout" : "/api/v1/mobistack/workspaces/join/checkout", {
        method: "POST",
        body: JSON.stringify({ joinCode }),
      });
      const payment = await collectJoinPayment(checkout, joinCode, user ?? undefined, checkout.shopName);
      await api(group ? "/api/v1/mobistack/groups/join/complete" : "/api/v1/mobistack/workspaces/join/complete", {
        method: "POST",
        body: JSON.stringify({
          joinCode,
          razorpay_order_id: payment.razorpay_order_id,
          razorpay_payment_id: payment.razorpay_payment_id,
          razorpay_signature: payment.razorpay_signature,
          orderId: payment.orderId,
        }),
      });
      await refreshUser();
      await refreshWorkspaces();
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not join";
      setError(message);
      if (message.toLowerCase().includes("already")) {
        await refreshUser();
        await refreshWorkspaces();
      }
      setBusy(false);
    }
  }

  return (
    <Journey
      title={group ? "Join the union" : "Join a shop"}
      body={
        group
          ? "Enter the code for Bihar mobile union. A matching code opens payment of ₹50."
          : "Enter the code from that shop. A real code opens payment of ₹50."
      }
    >
      <form className="journey-form" onSubmit={(event) => void onSubmit(event)}>
        <label className="auth-label">
          Code
          <input
            className="field"
            value={code}
            autoCapitalize="characters"
            onChange={(event) => setCode(event.target.value.toUpperCase())}
          />
        </label>
        {error && <p className="auth-error" role="alert">{error}</p>}
        <button className="auth-submit" type="submit" disabled={busy || code.trim() === ""}>
          {busy ? "Checking…" : "Continue"}
        </button>
        {onBack && (
          <button className="auth-text" type="button" onClick={onBack}>
            Back
          </button>
        )}
      </form>
    </Journey>
  );
}

function Invite({ onBack }: { onBack: () => void }) {
  const { refreshUser, refreshWorkspaces } = useAuth();
  const [token, setToken] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api("/api/v1/mobistack/invitations/accept", { method: "POST", body: JSON.stringify({ token: token.trim() }) });
      await refreshUser();
      await refreshWorkspaces();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not accept the invite");
      setBusy(false);
    }
  }

  return (
    <Journey title="Invite" body="An owner invite is free. Paste the token from the email.">
      <form className="journey-form" onSubmit={(event) => void onSubmit(event)}>
        <label className="auth-label">
          Invite token
          <input className="field" value={token} onChange={(event) => setToken(event.target.value)} />
        </label>
        {error && <p className="auth-error" role="alert">{error}</p>}
        <button className="auth-submit" type="submit" disabled={busy || token.trim() === ""}>
          {busy ? "Checking…" : "Accept invite"}
        </button>
        <button className="auth-text" type="button" onClick={onBack}>
          Back
        </button>
      </form>
    </Journey>
  );
}

function Waiting({ union }: { union: boolean }) {
  const { workspaces, refreshUser, refreshWorkspaces } = useAuth();
  const waiting = workspaces.find((row) => row.status === "PENDING" || row.status === "INVITED");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const who = union ? "a union admin" : "the shop owner";
  const name = union ? "the union" : waiting?.name ?? "the shop";

  async function cancel() {
    setBusy(true);
    setError(null);
    try {
      if (union) {
        await api("/api/v1/mobistack/groups/join/cancel", { method: "POST" });
      } else if (waiting) {
        await api(`/api/v1/mobistack/workspaces/join/cancel?id=${waiting.id}`, { method: "POST" });
      }
      await refreshUser();
      await refreshWorkspaces();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not cancel");
      setBusy(false);
    }
  }

  return (
    <Journey title="Waiting" body={`Payment is done for ${name}. ${who} still has to approve.`}>
      {error && <p className="auth-error" role="alert">{error}</p>}
      <button className="auth-submit auth-submit--secondary" type="button" disabled={busy} onClick={() => void cancel()}>
        {busy ? "Cancelling…" : "Cancel request"}
      </button>
    </Journey>
  );
}

function Outside() {
  const { user, logout } = useAuth();
  const name = user?.shopName ?? user?.workspaceName ?? "This shop";
  return (
    <Journey
      title={name}
      body="You are in this shop. It is not in Bihar mobile union yet, so compatibility stays closed until a union admin adds it."
    >
      <button className="auth-text" type="button" onClick={() => void logout()}>
        Sign out
      </button>
    </Journey>
  );
}

function Journey({ title, body, children }: { title: string; body: string; children: ReactNode }) {
  return (
    <AuthGate>
      <h1 className="auth-heading">{title}</h1>
      <p className="auth-brand__welcome">{body}</p>
      <div className="journey-form">{children}</div>
    </AuthGate>
  );
}

