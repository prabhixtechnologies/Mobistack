import { useState } from "react";
import { Link, Navigate } from "react-router-dom";
import { api } from "../lib/api";
import { useAuth } from "../lib/auth";
import { usePagedList } from "../lib/usePagedList";
import { EmptyState } from "../ui/EmptyState";
import { PageHeader } from "../ui/PageHeader";
import { TextField } from "../ui/Field";
import type { CommonsContribution } from "../lib/types";

export function CommonsReviewPage() {
  const { user } = useAuth();
  const queue = usePagedList<CommonsContribution>("/api/v1/commons/review/queue", {
    size: 25,
    enabled: Boolean(user?.commonsReviewer),
  });
  const [note, setNote] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (user && !user.commonsReviewer) {
    return <Navigate to="/commons" replace />;
  }

  async function decide(id: string, action: "accept" | "reject") {
    setBusy(true);
    setError(null);
    try {
      await api(`/api/v1/commons/review/${id}/${action}`, {
        method: "POST",
        body: JSON.stringify({ note: note.trim() || undefined }),
      });
      queue.reload();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not review that.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="page">
      <PageHeader
        kicker={<Link to="/commons">Fitment Catalog</Link>}
        title="Catalog review"
        subtitle="Granted by Prabhix. This queue changes what every shop reads."
      />
      {error && <div className="error">{error}</div>}
      <TextField label="Review note (optional)" value={note} onChange={(event) => setNote(event.target.value)} />
      <section className="card tight">
        {queue.error && <div className="error">{queue.error}</div>}
        {queue.rows.map((item) => (
          <div className="category-row" key={item.id}>
            <div>
              <div style={{ fontWeight: 650 }}>{item.kind.replaceAll("_", " ")}</div>
              {item.summary && <div>{item.summary}</div>}
              <div className="faint">{item.reason ?? "No reason"}</div>
            </div>
            <div className="row">
              <button className="btn" type="button" disabled={busy} onClick={() => void decide(item.id, "accept")}>
                Accept
              </button>
              <button className="btn ghost" type="button" disabled={busy} onClick={() => void decide(item.id, "reject")}>
                Reject
              </button>
            </div>
          </div>
        ))}
        {!queue.loading && queue.rows.length === 0 && (
          <EmptyState compact icon="check" title="Queue is empty" hint="New contributions land here when they need a person." />
        )}
      </section>
    </div>
  );
}
