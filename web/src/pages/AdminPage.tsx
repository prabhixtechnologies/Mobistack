import { useEffect, useState } from "react";
import { api } from "../lib/api";
import { PageHeader } from "../ui/PageHeader";

interface Workspace {
  id: string;
  name: string;
  city?: string;
  active: boolean;
  members: number;
  maxDevicesPerUser?: number;
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

export function AdminPage() {
  const [tab, setTab] = useState<"shops" | "live" | "support" | "releases">("shops");
  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [flags, setFlags] = useState<Flag[]>([]);
  const [live, setLive] = useState<LiveUser[]>([]);
  const [tickets, setTickets] = useState<Conversation[]>([]);
  const [releases, setReleases] = useState<Release[]>([]);
  const [reply, setReply] = useState("");
  const [error, setError] = useState<string | null>(null);

  async function load() {
    const [shopRows, flagRows, liveRows, ticketRows, releaseRows] = await Promise.all([
      api<Workspace[]>("/api/v1/admin/workspaces"),
      api<Flag[]>("/api/v1/admin/feature-flags"),
      api<LiveUser[]>("/api/v1/admin/live"),
      api<Conversation[]>("/api/v1/admin/support"),
      api<Release[]>("/api/v1/admin/app-releases"),
    ]);
    setWorkspaces(shopRows);
    setFlags(flagRows);
    setLive(liveRows);
    setTickets(ticketRows);
    setReleases(releaseRows);
  }

  useEffect(() => {
    load().catch((err: Error) => setError(err.message));
    const timer = window.setInterval(() => {
      void api<LiveUser[]>("/api/v1/admin/live").then(setLive).catch(() => undefined);
    }, 10000);
    return () => window.clearInterval(timer);
  }, []);

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
        subtitle="Live users, device caps, support, and the native/OTA release gate."
        actions={
        <div className="method-tabs">
          {(["shops", "live", "support", "releases"] as const).map((id) => (
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
            {workspaces.map((workspace) => (
              <div className="category-row" key={workspace.id}>
                <div>
                  <div style={{ fontWeight: 650 }}>{workspace.name}</div>
                  <div className="faint">
                    {workspace.city} · {workspace.members} members
                  </div>
                </div>
                <label className="row">
                  Devices
                  <input
                    className="field"
                    style={{ width: 72 }}
                    type="number"
                    min={1}
                    max={20}
                    defaultValue={workspace.maxDevicesPerUser ?? 3}
                    onBlur={(event) => {
                      void api(`/api/v1/admin/workspaces/${workspace.id}/device-limit`, {
                        method: "POST",
                        body: JSON.stringify({ maxDevicesPerUser: Number(event.target.value) }),
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
            ))}
          </div>
          <div className="card tight">
            {flags.map((flag) => (
              <div className="category-row" key={flag.code}>
                <div style={{ fontWeight: 650 }}>{flag.code}</div>
                <span>{flag.enabled ? "On" : "Off"}</span>
                <button className="btn ghost" type="button" onClick={() => void toggleFlag(flag)}>
                  Toggle
                </button>
              </div>
            ))}
          </div>
        </>
      )}

      {tab === "live" && (
        <div className="card tight">
          {live.map((row) => (
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
          ))}
          {live.length === 0 && <div className="muted">Nobody is live right now.</div>}
        </div>
      )}

      {tab === "support" && (
        <div className="stack">
          {tickets.map((ticket) => (
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
                <input className="field" style={{ flex: 1 }} value={reply} onChange={(event) => setReply(event.target.value)} placeholder="Reply as Prabhix" />
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
          ))}
        </div>
      )}

      {tab === "releases" && (
        <div className="card tight">
          {releases.map((release, index) => (
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
          ))}
        </div>
      )}
    </div>
  );
}
