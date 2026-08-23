import { useEffect, useState } from "react";
import { api } from "../lib/api";

interface Pref {
  eventType: string;
  email: boolean;
  whatsapp: boolean;
  push: boolean;
  sms: boolean;
}

interface InboxItem {
  id: string;
  eventType: string;
  title: string;
  body?: string;
  createdAt: string;
  readAt?: string | null;
}

interface InboxSummary {
  unread: number;
  items: InboxItem[];
}

interface Outbox {
  id: string;
  eventType: string;
  channel: string;
  subject?: string;
  recipient?: string;
  status: string;
}

export function NotificationsPage() {
  const [prefs, setPrefs] = useState<Pref[]>([]);
  const [inbox, setInbox] = useState<InboxSummary>({ unread: 0, items: [] });
  const [rows, setRows] = useState<Outbox[]>([]);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    const [nextPrefs, nextInbox, nextRows] = await Promise.all([
      api<Pref[]>("/api/v1/notifications/preferences"),
      api<InboxSummary>("/api/v1/inbox"),
      api<Outbox[]>("/api/v1/notifications"),
    ]);
    setPrefs(nextPrefs);
    setInbox(nextInbox);
    setRows(nextRows);
  }

  useEffect(() => {
    load().catch((err: Error) => setError(err.message));
  }, []);

  async function save() {
    try {
      setPrefs(await api<Pref[]>("/api/v1/notifications/preferences", { method: "PUT", body: JSON.stringify(prefs) }));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save");
    }
  }

  return (
    <div className="page">
      <div className="page-title">
        <div>
          <h1>Notifications</h1>
          <p>
            {inbox.unread} unread in your inbox. Push uses Expo when the counter app has registered a token.
          </p>
        </div>
        <div className="row">
          <button className="btn ghost" type="button" onClick={() => void api("/api/v1/inbox/read-all", { method: "POST" }).then(load)}>
            Mark all read
          </button>
          <button className="btn" type="button" onClick={() => void save()}>
            Save
          </button>
        </div>
      </div>
      {error && <div className="error">{error}</div>}
      <div className="card tight">
        {inbox.items.map((item) => (
          <div className="category-row" key={item.id}>
            <div>
              <div style={{ fontWeight: 650 }}>{item.title}</div>
              <div className="faint">{item.body}</div>
            </div>
            <span className={`badge ${item.readAt ? "neutral" : "GREEN"}`}>{item.readAt ? "READ" : "NEW"}</span>
          </div>
        ))}
        {inbox.items.length === 0 && <div className="muted">Nothing in the inbox yet.</div>}
      </div>
      <div className="card tight">
        {prefs.map((pref, index) => (
          <div className="category-row" key={pref.eventType}>
            <div style={{ fontWeight: 650 }}>{pref.eventType}</div>
            <label className="row">
              <input
                type="checkbox"
                checked={pref.email}
                onChange={(e) => {
                  const next = [...prefs];
                  next[index] = { ...pref, email: e.target.checked };
                  setPrefs(next);
                }}
              />{" "}
              Email
            </label>
            <label className="row">
              <input
                type="checkbox"
                checked={pref.push}
                onChange={(e) => {
                  const next = [...prefs];
                  next[index] = { ...pref, push: e.target.checked };
                  setPrefs(next);
                }}
              />{" "}
              Push
            </label>
            <label className="row">
              <input
                type="checkbox"
                checked={pref.whatsapp}
                onChange={(e) => {
                  const next = [...prefs];
                  next[index] = { ...pref, whatsapp: e.target.checked };
                  setPrefs(next);
                }}
              />{" "}
              WhatsApp
            </label>
          </div>
        ))}
      </div>
      <div className="card tight">
        {rows.map((row) => (
          <div className="category-row" key={row.id}>
            <div>
              <div style={{ fontWeight: 650 }}>{row.subject ?? row.eventType}</div>
              <div className="faint">
                {row.channel} · {row.recipient}
              </div>
            </div>
            <span className="badge neutral">{row.status}</span>
          </div>
        ))}
      </div>
    </div>
  );
}
