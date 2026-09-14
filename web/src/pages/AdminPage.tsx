import { useEffect, useState } from "react";
import { api, money } from "../lib/api";
import { EmptyState } from "../ui/EmptyState";
import { TextField } from "../ui/Field";
import { PageHeader } from "../ui/PageHeader";

interface Workspace {
  id: string;
  name: string;
  city?: string;
  active: boolean;
  members: number;
  extraScreens?: number;
  screenSeats?: number;
}

interface Flag {
  code: string;
  enabled: boolean;
}

interface LiveUser {
  userId: string;
  fullName: string;
  email: string;
  shopName?: string;
  deviceId: string;
  platform: string;
  appVersion?: string;
  ipAddress?: string;
  seenAt: string;
}

interface Conversation {
  id: string;
  subject: string;
  status: string;
  userName?: string;
  lastMessageAt: string;
  messages: { id: string; authorType: string; body: string }[];
}

interface Release {
  platform: string;
  minNativeBuild: number;
  latestNativeBuild: number;
  forceNativeUpdate: boolean;
  otaChannel: string;
  storeUrl?: string;
  notes?: string;
}

interface Plan {
  id: string;
  code: string;
  name: string;
  description?: string;
  amount: number;
  currency: string;
  interval: string;
  sortOrder: number;
  active: boolean;
  features: string[];
}

interface FeatureDef {
  code: string;
  label: string;
  help: string;
}

interface PaymentRow {
  id: string;
  shopName: string;
  priceCode: string;
  amount: number;
  currency: string;
  status: string;
  createdAt: string;
  paidAt?: string;
}

type AdminTab = "shops" | "plans" | "payments" | "live" | "support" | "releases" | "reviewers";

const emptyPlan = {
  code: "",
  name: "",
  description: "",
  amount: 50,
  interval: "MONTHLY",
  features: [] as string[],
};

export function AdminPage() {
  const [tab, setTab] = useState<AdminTab>("shops");
  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [flags, setFlags] = useState<Flag[]>([]);
  const [live, setLive] = useState<LiveUser[]>([]);
  const [tickets, setTickets] = useState<Conversation[]>([]);
  const [releases, setReleases] = useState<Release[]>([]);
  const [plans, setPlans] = useState<Plan[]>([]);
  const [featureDefs, setFeatureDefs] = useState<FeatureDef[]>([]);
  const [payments, setPayments] = useState<PaymentRow[]>([]);
  const [reviewers, setReviewers] = useState<
    { userId: string; email?: string; fullName?: string; reason?: string; grantedAt?: string }[]
  >([]);
  const [reviewerId, setReviewerId] = useState("");
  const [reviewerReason, setReviewerReason] = useState("");
  const [draft, setDraft] = useState(emptyPlan);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [reply, setReply] = useState("");
  const [error, setError] = useState<string | null>(null);

  async function load() {
    const [shopRows, flagRows, liveRows, ticketRows, releaseRows, planRows, featureRows, paymentRows] = await Promise.all([
      api<Workspace[]>("/api/v1/admin/workspaces"),
      api<Flag[]>("/api/v1/admin/feature-flags"),
      api<LiveUser[]>("/api/v1/admin/live"),
      api<Conversation[]>("/api/v1/admin/support"),
      api<Release[]>("/api/v1/admin/app-releases"),
      api<Plan[]>("/api/v1/admin/plans"),
      api<FeatureDef[]>("/api/v1/admin/plan-features"),
      api<PaymentRow[]>("/api/v1/admin/billing/orders"),
    ]);
    setWorkspaces(shopRows);
    setFlags(flagRows);
    setLive(liveRows);
    setTickets(ticketRows);
    setReleases(releaseRows);
    setPlans(planRows);
    setFeatureDefs(featureRows);
    setPayments(paymentRows);
  }

  async function loadReviewers() {
    const rows = await api<
      { userId: string; email?: string; fullName?: string; reason?: string; grantedAt?: string }[]
    >("/api/v1/admin/commons-reviewers");
    setReviewers(rows);
  }

  useEffect(() => {
    load().catch((err: Error) => setError(err.message));
    const tick = () => {
      if (document.hidden) return;
      void api<LiveUser[]>("/api/v1/admin/live").then(setLive).catch(() => undefined);
    };
    const timer = window.setInterval(tick, 10000);
    document.addEventListener("visibilitychange", tick);
    return () => {
      window.clearInterval(timer);
      document.removeEventListener("visibilitychange", tick);
    };
  }, []);

  useEffect(() => {
    if (tab !== "reviewers") {
      return;
    }
    loadReviewers().catch((err: Error) => setError(err.message));
  }, [tab]);

  async function toggleShop(workspace: Workspace) {
    await api(`/api/v1/admin/workspaces/${workspace.id}/${workspace.active ? "suspend" : "activate"}`, { method: "POST" });
    await load();
  }

  async function toggleFlag(flag: Flag) {
    await api("/api/v1/admin/feature-flags", {
      method: "PUT",
      body: JSON.stringify({ code: flag.code, enabled: !flag.enabled }),
    });
    await load();
  }

  return (
    <div className="page">
      <PageHeader
        kicker="Platform"
        title="Platform"
        subtitle="Plans, payments, live users, support, and the native/OTA release gate."
        actions={
        <div className="method-tabs">
          {(["shops", "plans", "payments", "live", "support", "releases", "reviewers"] as const).map((id) => (
            <button key={id} className={`method-tab ${tab === id ? "on" : ""}`} type="button" onClick={() => setTab(id)}>
              {id}
            </button>
          ))}
        </div>
        }
      />
      {error && <div className="error">{error}</div>}

      {tab === "shops" && (
        <>
          <div className="card tight">
            {workspaces.length === 0 ? (
              <EmptyState compact icon="store" title="No shops yet" hint="Workspaces appear here once they are created." />
            ) : (
              workspaces.map((workspace) => (
              <div className="category-row" key={workspace.id}>
                <div>
                  <div style={{ fontWeight: 650 }}>{workspace.name}</div>
                  <div className="faint">
                    {workspace.city} · {workspace.members} members
                  </div>
                </div>
                <label className="row">
                  Extra screens
                  <input
                    className="field"
                    style={{ width: 72 }}
                    type="number"
                    min={0}
                    max={49}
                    defaultValue={workspace.extraScreens ?? 0}
                    onBlur={(event) => {
                      void api(`/api/v1/admin/workspaces/${workspace.id}/screens`, {
                        method: "POST",
                        body: JSON.stringify({ extraScreens: Number(event.target.value) }),
                      });
                    }}
                  />
                </label>
                <span className={`badge ${workspace.active ? "GREEN" : "RED"}`}>
                  {workspace.active ? "ACTIVE" : "SUSPENDED"}
                </span>
                <button className="btn ghost" type="button" onClick={() => void toggleShop(workspace)}>
                  {workspace.active ? "Suspend" : "Activate"}
                </button>
              </div>
              ))
            )}
          </div>
          <div className="card tight">
            {flags.length === 0 ? (
              <EmptyState compact icon="grid" title="No feature flags" hint="Flags will show here when the platform defines them." />
            ) : (
              flags.map((flag) => (
              <div className="category-row" key={flag.code}>
                <div style={{ fontWeight: 650 }}>{flag.code}</div>
                <span>{flag.enabled ? "On" : "Off"}</span>
                <button className="btn ghost" type="button" onClick={() => void toggleFlag(flag)}>
                  Toggle
                </button>
              </div>
              ))
            )}
          </div>
        </>
      )}

      {tab === "plans" && (
        <div className="stack">
          <form
            className="card stack"
            onSubmit={(event) => {
              event.preventDefault();
              const body = {
                code: draft.code,
                name: draft.name,
                description: draft.description,
                amount: Number(draft.amount),
                interval: draft.interval,
                features: draft.features,
                active: true,
              };
              const path = editingId ? `/api/v1/admin/plans/${editingId}` : "/api/v1/admin/plans";
              void api(path, {
                method: editingId ? "PUT" : "POST",
                body: JSON.stringify(body),
              })
                .then(() => {
                  setDraft(emptyPlan);
                  setEditingId(null);
                  return load();
                })
                .catch((err: Error) => setError(err.message));
            }}
          >
            <strong>{editingId ? "Edit plan" : "Create a plan"}</strong>
            <div className="grid-2">
              <label className="stack">
                <span className="faint">Name</span>
                <input className="field" value={draft.name} onChange={(event) => setDraft({ ...draft, name: event.target.value })} required />
              </label>
              <label className="stack">
                <span className="faint">Code</span>
                <input className="field" value={draft.code} disabled={Boolean(editingId)} onChange={(event) => setDraft({ ...draft, code: event.target.value })} placeholder="FULL_SHOP" />
              </label>
              <label className="stack">
                <span className="faint">Amount (₹)</span>
                <input className="field" type="number" min={0} value={draft.amount} onChange={(event) => setDraft({ ...draft, amount: Number(event.target.value) })} />
              </label>
              <label className="stack">
                <span className="faint">Interval</span>
                <select className="select" value={draft.interval} onChange={(event) => setDraft({ ...draft, interval: event.target.value })}>
                  <option value="MONTHLY">Monthly</option>
                  <option value="ANNUAL">Annual</option>
                  <option value="ONE_TIME">One time (31 days)</option>
                </select>
              </label>
            </div>
            <label className="stack">
              <span className="faint">What the shop sees on this plan</span>
              <p className="faint">Tick every screen this plan should unlock. Unticked features stay hidden.</p>
            </label>
            <div className="chips">
              {featureDefs.map((feature) => {
                const on = draft.features.includes(feature.code);
                return (
                  <button
                    key={feature.code}
                    className={`chip ${on ? "on" : ""}`}
                    type="button"
                    title={feature.help}
                    onClick={() =>
                      setDraft({
                        ...draft,
                        features: on
                          ? draft.features.filter((code) => code !== feature.code)
                          : [...draft.features, feature.code],
                      })
                    }
                  >
                    {feature.label}
                  </button>
                );
              })}
            </div>
            <div className="row">
              <button className="btn" type="submit">{editingId ? "Save plan" : "Create plan"}</button>
              {editingId && (
                <button className="btn ghost" type="button" onClick={() => { setEditingId(null); setDraft(emptyPlan); }}>
                  Cancel
                </button>
              )}
            </div>
          </form>
          <div className="card tight">
            {plans.filter((plan) => plan.features.length > 0).length === 0 ? (
              <EmptyState compact icon="card" title="No plans yet" hint="Create a plan above to sell it to shops." />
            ) : (
              plans.filter((plan) => plan.features.length > 0).map((plan) => (
              <div className="category-row" key={plan.id}>
                <div>
                  <div style={{ fontWeight: 650 }}>{plan.name} · {money.format(plan.amount)}</div>
                  <div className="faint">{plan.code} · {plan.interval} · {plan.features.join(", ").toLowerCase()}</div>
                </div>
                <span className={`badge ${plan.active ? "GREEN" : "RED"}`}>{plan.active ? "ON" : "OFF"}</span>
                <button
                  className="btn ghost"
                  type="button"
                  onClick={() => {
                    setEditingId(plan.id);
                    setDraft({
                      code: plan.code,
                      name: plan.name,
                      description: plan.description ?? "",
                      amount: plan.amount,
                      interval: plan.interval,
                      features: plan.features,
                    });
                    setTab("plans");
                  }}
                >
                  Edit
                </button>
              </div>
              ))
            )}
          </div>
        </div>
      )}

      {tab === "payments" && (
        <div className="card tight">
          {payments.length === 0 ? (
            <EmptyState compact icon="card" title="No payments yet" hint="Captured shop payments across the platform land here." />
          ) : (
            payments.map((payment) => (
            <div className="category-row" key={payment.id}>
              <div>
                <div style={{ fontWeight: 650 }}>{payment.shopName}</div>
                <div className="faint">
                  {payment.priceCode.replaceAll("_", " ")} · {new Date(payment.paidAt ?? payment.createdAt).toLocaleString("en-IN")}
                </div>
              </div>
              <span>{money.format(payment.amount)}</span>
              <span className={`badge ${payment.status === "CAPTURED" ? "GREEN" : "ORANGE"}`}>{payment.status}</span>
            </div>
            ))
          )}
        </div>
      )}

      {tab === "live" && (
        <div className="card tight">
          {live.length === 0 ? (
            <EmptyState compact icon="pulse" title="Nobody is live" hint="Signed-in devices will show here as they check in." />
          ) : (
            live.map((row) => (
            <div className="category-row" key={`${row.userId}-${row.deviceId}`}>
              <div>
                <div style={{ fontWeight: 650 }}>{row.fullName}</div>
                <div className="faint">
                  {row.shopName ?? "No workspace"} · {row.platform} · {row.deviceId} · {row.ipAddress}
                </div>
              </div>
              <button
                className="btn ghost"
                type="button"
                onClick={() =>
                  void api(`/api/v1/admin/sessions/${row.userId}/revoke-device`, {
                    method: "POST",
                    body: JSON.stringify({ deviceId: row.deviceId }),
                  }).then(load)
                }
              >
                Kick device
              </button>
            </div>
            ))
          )}
        </div>
      )}

      {tab === "support" && (
        <div className="stack">
          {tickets.length === 0 ? (
            <EmptyState compact icon="chat" title="No tickets" hint="Shopkeepers' support conversations appear here." />
          ) : (
            tickets.map((ticket) => (
            <div className="card" key={ticket.id}>
              <div className="spread">
                <div>
                  <div style={{ fontWeight: 650 }}>{ticket.subject}</div>
                  <div className="faint">
                    {ticket.userName} · {ticket.status}
                  </div>
                </div>
                <button className="btn ghost" type="button" onClick={() => void api(`/api/v1/admin/support/${ticket.id}/resolve`, { method: "POST" }).then(load)}>
                  Resolve
                </button>
              </div>
              <div className="chat-log">
                {ticket.messages.map((message) => (
                  <div key={message.id} className={`chat-bubble ${message.authorType.toLowerCase()}`}>
                    <div className="faint">{message.authorType}</div>
                    <div>{message.body}</div>
                  </div>
                ))}
              </div>
              <div className="row">
                <TextField
                  label="Reply"
                  value={reply}
                  onChange={(event) => setReply(event.target.value)}
                  placeholder="Reply as Prabhix"
                  autoComplete="off"
                />
                <button
                  className="btn"
                  type="button"
                  onClick={() => {
                    void api(`/api/v1/admin/support/${ticket.id}/messages`, {
                      method: "POST",
                      body: JSON.stringify({ message: reply }),
                    }).then(() => {
                      setReply("");
                      return load();
                    });
                  }}
                >
                  Reply
                </button>
              </div>
            </div>
            ))
          )}
        </div>
      )}

      {tab === "releases" && (
        <div className="card tight">
          {releases.length === 0 ? (
            <EmptyState compact icon="upload" title="No release gates" hint="Native min-build settings appear here per platform." />
          ) : (
            releases.map((release, index) => (
            <div className="category-row" key={release.platform}>
              <div style={{ minWidth: 90, fontWeight: 650 }}>{release.platform}</div>
              <label className="stack">
                <span className="faint">Min build</span>
                <input
                  className="field"
                  type="number"
                  value={release.minNativeBuild}
                  onChange={(event) => {
                    const next = [...releases];
                    next[index] = { ...release, minNativeBuild: Number(event.target.value) };
                    setReleases(next);
                  }}
                />
              </label>
              <label className="row">
                <input
                  type="checkbox"
                  checked={release.forceNativeUpdate}
                  onChange={(event) => {
                    const next = [...releases];
                    next[index] = { ...release, forceNativeUpdate: event.target.checked };
                    setReleases(next);
                  }}
                />
                Force
              </label>
              <button
                className="btn ghost"
                type="button"
                onClick={() =>
                  void api(`/api/v1/admin/app-releases/${release.platform}`, {
                    method: "PUT",
                    body: JSON.stringify(release),
                  }).then(load)
                }
              >
                Save
              </button>
            </div>
            ))
          )}
        </div>
      )}

      {tab === "reviewers" && (
        <div className="stack">
          <form
            className="card stack"
            onSubmit={(event) => {
              event.preventDefault();
              void api("/api/v1/admin/commons-reviewers", {
                method: "POST",
                body: JSON.stringify({ userId: reviewerId.trim(), reason: reviewerReason.trim() || undefined }),
              })
                .then(() => {
                  setReviewerId("");
                  setReviewerReason("");
                  return loadReviewers();
                })
                .catch((err: Error) => setError(err.message));
            }}
          >
            <strong>Grant catalog review</strong>
            <p className="faint">User-level, granted by Prabhix. Not a shop role.</p>
            <TextField label="User id" value={reviewerId} onChange={(event) => setReviewerId(event.target.value)} required />
            <TextField label="Reason" value={reviewerReason} onChange={(event) => setReviewerReason(event.target.value)} />
            <button className="btn" type="submit">
              Grant
            </button>
          </form>
          <div className="card tight">
            {reviewers.length === 0 ? (
              <EmptyState compact icon="shield" title="No reviewers yet" hint="Grant a user id to open the shared catalog queue." />
            ) : (
              reviewers.map((row) => (
                <div className="category-row" key={row.userId}>
                  <div>
                    <div style={{ fontWeight: 650 }}>{row.fullName ?? row.email ?? row.userId}</div>
                    <div className="faint">{row.reason ?? row.userId}</div>
                  </div>
                  <button
                    className="btn ghost"
                    type="button"
                    onClick={() =>
                      void api(`/api/v1/admin/commons-reviewers/${row.userId}/revoke`, { method: "POST" })
                        .then(loadReviewers)
                        .catch((err: Error) => setError(err.message))
                    }
                  >
                    Revoke
                  </button>
                </div>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  );
}
