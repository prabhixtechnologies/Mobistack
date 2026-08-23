import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../lib/api";
import { Icon, type NavIconName } from "../ui/navIcons";

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

const CHANNELS = [
  { key: "email", label: "Email" },
  { key: "push", label: "Push" },
  { key: "whatsapp", label: "WhatsApp" },
  { key: "sms", label: "SMS" },
] as const;

type ChannelKey = (typeof CHANNELS)[number]["key"];

const EVENT_COPY: Record<string, { title: string; help: string; group: "shop" | "signin"; icon: NavIconName }> = {
  JOIN_REQUEST: { title: "Join request", help: "Someone asked to join this shop", group: "shop", icon: "people" },
  JOIN_REQUEST_APPROVED: { title: "Join approved", help: "You were let into a shop", group: "shop", icon: "check" },
  JOIN_REQUEST_CANCELLED: { title: "Join cancelled", help: "A pending request was withdrawn", group: "shop", icon: "close" },
  USER_INVITED: { title: "Invitation", help: "A person was invited to the shop", group: "shop", icon: "users" },
  SALE_COMPLETED: { title: "Sale completed", help: "A counter sale was recorded", group: "shop", icon: "cart" },
  REPAIR_READY: { title: "Repair ready", help: "A job is ready for pickup", group: "shop", icon: "wrench" },
  LOW_STOCK: { title: "Low stock", help: "A part is running out", group: "shop", icon: "box" },
  SUPPORT_REPLY: { title: "Support reply", help: "Prabhix replied to a ticket", group: "shop", icon: "chat" },
  SUPPORT_TICKET: { title: "Support ticket", help: "A new support message arrived", group: "shop", icon: "chat" },
  DEVICE_REVOKED: { title: "Device signed out", help: "A session was revoked", group: "shop", icon: "shield" },
  PAYMENT_PENDING: { title: "Payment pending", help: "The shop plan needs a payment", group: "shop", icon: "card" },
  PAYMENT_RECEIVED: { title: "Payment received", help: "A plan payment was captured", group: "shop", icon: "card" },
  BILLING_REMINDER: { title: "Billing reminder", help: "The current period is ending soon", group: "shop", icon: "card" },
  PASSWORD_RESET: { title: "Password reset", help: "Reset link — never shown in the inbox", group: "signin", icon: "lock" },
  EMAIL_OTP: { title: "Email code", help: "One-time sign-in code by email", group: "signin", icon: "lock" },
  PHONE_OTP: { title: "SMS code", help: "One-time sign-in code by SMS", group: "signin", icon: "lock" },
  WHATSAPP_OTP: { title: "WhatsApp code", help: "One-time sign-in code on WhatsApp", group: "signin", icon: "lock" },
  MAGIC_LINK: { title: "Magic link", help: "Sign-in link — never shown in the inbox", group: "signin", icon: "lock" },
};

function eventCopy(type: string) {
  return EVENT_COPY[type] ?? {
    title: type.replaceAll("_", " ").toLowerCase(),
    help: "Shop or account notice",
    group: "shop" as const,
    icon: "bell" as const,
  };
}

function relativeTime(value: string): string {
  const then = new Date(value).getTime();
  if (Number.isNaN(then)) {
    return "";
  }
  const delta = Date.now() - then;
  const minutes = Math.round(delta / 60_000);
  if (minutes < 1) return "Just now";
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.round(hours / 24);
  if (days < 7) return `${days}d ago`;
  return new Date(value).toLocaleDateString("en-IN", { day: "numeric", month: "short" });
}

function channelLabel(channel: string): string {
  const known: Record<string, string> = {
    EMAIL: "Email",
    PUSH: "Push",
    WHATSAPP: "WhatsApp",
    SMS: "SMS",
    INBOX: "Inbox",
    LOG: "Log",
  };
  return known[channel] ?? channel;
}

export function NotificationsPage() {
  const [prefs, setPrefs] = useState<Pref[]>([]);
  const [inbox, setInbox] = useState<InboxSummary>({ unread: 0, items: [] });
  const [rows, setRows] = useState<Outbox[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [dirty, setDirty] = useState(false);

  async function load() {
    const [nextPrefs, nextInbox, nextRows] = await Promise.all([
      api<Pref[]>("/api/v1/notifications/preferences"),
      api<InboxSummary>("/api/v1/inbox"),
      api<Outbox[]>("/api/v1/notifications"),
    ]);
    setPrefs(nextPrefs);
    setInbox(nextInbox);
    setRows(nextRows);
    setDirty(false);
  }

  useEffect(() => {
    load().catch((err: Error) => setError(err.message));
  }, []);

  const shopPrefs = useMemo(() => prefs.filter((pref) => eventCopy(pref.eventType).group === "shop"), [prefs]);
  const signinPrefs = useMemo(() => prefs.filter((pref) => eventCopy(pref.eventType).group === "signin"), [prefs]);
  const sent = useMemo(
    () => rows.filter((row) => row.channel !== "LOG" && row.channel !== "INBOX").slice(0, 8),
    [rows],
  );

  function toggle(eventType: string, channel: ChannelKey, value: boolean) {
    setPrefs((current) =>
      current.map((pref) => (pref.eventType === eventType ? { ...pref, [channel]: value } : pref)),
    );
    setDirty(true);
    setNotice(null);
  }

  async function save() {
    setSaving(true);
    setError(null);
    try {
      setPrefs(await api<Pref[]>("/api/v1/notifications/preferences", { method: "PUT", body: JSON.stringify(prefs) }));
      setDirty(false);
      setNotice("Channel preferences saved.");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save");
    } finally {
      setSaving(false);
    }
  }

  async function markAll() {
    try {
      setInbox(await api<InboxSummary>("/api/v1/inbox/read-all", { method: "POST" }));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not mark read");
    }
  }

  async function markOne(id: string, alreadyRead: boolean) {
    if (alreadyRead) {
      return;
    }
    try {
      const updated = await api<InboxItem>(`/api/v1/inbox/${id}/read`, { method: "POST" });
      setInbox((current) => ({
        unread: Math.max(0, current.unread - 1),
        items: current.items.map((item) => (item.id === id ? { ...item, readAt: updated.readAt } : item)),
      }));
    } catch {
      /* the inbox list is still usable */
    }
  }

  return (
    <div className="page notify-page">
      <header className="notify-hero">
        <div>
          <p className="page-kicker">Support</p>
          <h1>Inbox</h1>
          <p>
            {inbox.unread === 0
              ? "You are caught up. Shop notices land here; sign-in codes stay private."
              : `${inbox.unread} unread ${inbox.unread === 1 ? "notice" : "notices"} waiting.`}
          </p>
        </div>
        <div className="notify-hero__meta">
          <span className={`notify-count ${inbox.unread ? "notify-count--hot" : ""}`}>
            {inbox.unread} unread
          </span>
          <button className="btn ghost" type="button" disabled={inbox.unread === 0} onClick={() => void markAll()}>
            Mark all read
          </button>
        </div>
      </header>

      {error && <div className="error">{error}</div>}
      {notice && <div className="banner">{notice}</div>}

      <section className="notify-panel" aria-label="Inbox">
        {inbox.items.length === 0 ? (
          <div className="notify-empty">
            <span className="notify-empty__mark" aria-hidden>
              <Icon name="bell" />
            </span>
            <strong>Nothing in the inbox yet</strong>
            <p>Join approvals, billing, and shop alerts will show up here.</p>
          </div>
        ) : (
          <ol className="notify-feed">
            {inbox.items.map((item) => {
              const copy = eventCopy(item.eventType);
              const unread = !item.readAt;
              return (
                <li key={item.id}>
                  <button
                    className={`notify-item${unread ? " notify-item--new" : ""}`}
                    type="button"
                    onClick={() => void markOne(item.id, Boolean(item.readAt))}
                  >
                    <span className="notify-item__icon" aria-hidden>
                      <Icon name={copy.icon} />
                    </span>
                    <span className="notify-item__body">
                      <span className="notify-item__title">{item.title}</span>
                      {item.body && <span className="notify-item__text">{item.body}</span>}
                      {item.eventType === "JOIN_REQUEST" && (
                        <Link to="/members" className="notify-item__link" onClick={(event) => event.stopPropagation()}>
                          Open People to approve
                        </Link>
                      )}
                    </span>
                    <span className="notify-item__aside">
                      <time dateTime={item.createdAt}>{relativeTime(item.createdAt)}</time>
                      <span className={`notify-pill ${unread ? "notify-pill--new" : ""}`}>
                        {unread ? "New" : "Read"}
                      </span>
                    </span>
                  </button>
                </li>
              );
            })}
          </ol>
        )}
      </section>

      <section className="notify-panel notify-channels" aria-label="Channels">
        <div className="notify-panel__head">
          <div>
            <h2>Where we reach you</h2>
            <p>Tick the channels for each kind of notice. Sign-in codes never appear in the inbox.</p>
          </div>
          <button className="btn" type="button" disabled={!dirty || saving} onClick={() => void save()}>
            {saving ? "Saving…" : dirty ? "Save channels" : "Saved"}
          </button>
        </div>

        <PreferenceTable title="Shop" prefs={shopPrefs} onToggle={toggle} />
        <PreferenceTable title="Sign-in codes" prefs={signinPrefs} onToggle={toggle} />
      </section>

      {sent.length > 0 && (
        <section className="notify-panel" aria-label="Recently sent">
          <div className="notify-panel__head">
            <div>
              <h2>Recently sent</h2>
              <p>The last few deliveries on email, WhatsApp, SMS, or push.</p>
            </div>
          </div>
          <ol className="notify-sent">
            {sent.map((row) => (
              <li className="notify-sent__row" key={row.id}>
                <div>
                  <strong>{row.subject ?? eventCopy(row.eventType).title}</strong>
                  <span className="faint">
                    {channelLabel(row.channel)}
                    {row.recipient ? ` · ${row.recipient}` : ""}
                  </span>
                </div>
                <span className="notify-pill">{row.status}</span>
              </li>
            ))}
          </ol>
        </section>
      )}
    </div>
  );
}

function PreferenceTable({
  title,
  prefs,
  onToggle,
}: {
  title: string;
  prefs: Pref[];
  onToggle: (eventType: string, channel: ChannelKey, value: boolean) => void;
}) {
  if (prefs.length === 0) {
    return null;
  }
  return (
    <div className="notify-table-wrap">
      <div className="notify-table__label">{title}</div>
      <div className="notify-table" role="table">
        <div className="notify-table__row notify-table__row--head" role="row">
          <span role="columnheader">Notice</span>
          {CHANNELS.map((channel) => (
            <span key={channel.key} role="columnheader">
              {channel.label}
            </span>
          ))}
        </div>
        {prefs.map((pref) => {
          const copy = eventCopy(pref.eventType);
          return (
            <div className="notify-table__row" role="row" key={pref.eventType}>
              <div className="notify-table__event" role="cell">
                <strong>{copy.title}</strong>
                <span>{copy.help}</span>
              </div>
              {CHANNELS.map((channel) => (
                <label className="notify-check" role="cell" key={channel.key}>
                  <input
                    type="checkbox"
                    checked={pref[channel.key]}
                    onChange={(event) => onToggle(pref.eventType, channel.key, event.target.checked)}
                    aria-label={`${copy.title} ${channel.label}`}
                  />
                  <span className="notify-check__box" aria-hidden />
                  <span className="notify-check__mobile">{channel.label}</span>
                </label>
              ))}
            </div>
          );
        })}
      </div>
    </div>
  );
}
