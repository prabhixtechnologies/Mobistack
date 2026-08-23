import { FormEvent, useEffect, useState } from "react";
import { api } from "../lib/api";
import { BRAND } from "../lib/brand";

interface Message {
  id: string;
  authorType: string;
  body: string;
  createdAt: string;
}

interface Conversation {
  id: string;
  subject: string;
  status: string;
  messages: Message[];
}

export function SupportPage() {
  const [conversation, setConversation] = useState<Conversation | null>(null);
  const [message, setMessage] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api<Conversation[]>("/api/v1/support/conversations")
      .then((rows) => setConversation(rows[0] ?? null))
      .catch((err: Error) => setError(err.message));
  }, []);

  async function send(event: FormEvent) {
    event.preventDefault();
    if (!message.trim()) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const next = conversation
        ? await api<Conversation>(`/api/v1/support/conversations/${conversation.id}/messages`, {
            method: "POST",
            body: JSON.stringify({ message, channel: "WEB" }),
          })
        : await api<Conversation>("/api/v1/support/chat", {
            method: "POST",
            body: JSON.stringify({ message, channel: "WEB" }),
          });
      setConversation(next);
      setMessage("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not send");
    } finally {
      setBusy(false);
    }
  }

  async function ticket() {
    setBusy(true);
    try {
      setConversation(
        await api<Conversation>("/api/v1/support/conversations", {
          method: "POST",
          body: JSON.stringify({
            subject: "Help from the console",
            message: message || "Please contact me.",
            channel: "WEB",
          }),
        }),
      );
      setMessage("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not open a ticket");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="page">
      <div className="page-title">
        <div>
          <h1>Support</h1>
          <p>
            Ask the FixFlow assistant, or reach Prabhix at{" "}
            <a href={`mailto:${BRAND.supportEmail}`}>{BRAND.supportEmail}</a>.
          </p>
        </div>
        <button className="btn ghost" type="button" onClick={() => void ticket()}>
          Contact support
        </button>
      </div>
      {error && <div className="error">{error}</div>}
      <div className="card chat-log">
        {(conversation?.messages ?? []).map((row) => (
          <div key={row.id} className={`chat-bubble ${row.authorType.toLowerCase()}`}>
            <div className="faint">{row.authorType}</div>
            <div>{row.body}</div>
          </div>
        ))}
        {!conversation && <div className="muted">Ask how to sell a part, find stock, or talk to a person.</div>}
      </div>
      <form className="row" onSubmit={(event) => void send(event)}>
        <input
          className="field"
          style={{ flex: 1 }}
          value={message}
          onChange={(event) => setMessage(event.target.value)}
          placeholder="How do I search for a Realme 6 display?"
        />
        <button className="btn" type="submit" disabled={busy}>
          Send
        </button>
      </form>
    </div>
  );
}
