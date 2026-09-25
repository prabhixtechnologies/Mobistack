import { Link } from "react-router-dom";
import { usePagedList } from "../lib/usePagedList";
import { useResource } from "../lib/useResource";
import { EmptyState } from "../ui/EmptyState";
import { PageHeader } from "../ui/PageHeader";
import type { CommonsContribution, CommonsStanding } from "../lib/types";

export function CommonsStandingPage() {
  const standing = useResource<CommonsStanding>("/api/v1/mobistack/commons/standing");
  const mine = usePagedList<CommonsContribution>("/api/v1/mobistack/commons/contributions/mine", { size: 25 });
  const row = standing.data;

  return (
    <div className="page">
      <PageHeader
        kicker={<Link to="/commons">Fitment Catalog</Link>}
        title="Contributor standing"
        subtitle="Accepted contributions raise trust. Disputes and rejections count the other way."
      />
      <div className="grid-4">
        <div className="card metric-card">
          <div className="metric-label">Accepted</div>
          <div className="metric-value">{row?.accepted ?? "—"}</div>
        </div>
        <div className="card metric-card">
          <div className="metric-label">Rejected</div>
          <div className="metric-value">{row?.rejected ?? "—"}</div>
        </div>
        <div className="card metric-card">
          <div className="metric-label">Trusted</div>
          <div className="metric-value">{row ? (row.trusted ? "Yes" : "No") : "—"}</div>
        </div>
        <div className="card metric-card">
          <div className="metric-label">Banned</div>
          <div className="metric-value">{row ? (row.banned ? "Yes" : "No") : "—"}</div>
        </div>
      </div>
      {row?.banned && row.bannedReason && <div className="banner banner-warn">{row.bannedReason}</div>}

      <section className="card tight">
        <div className="spread" style={{ padding: "16px 18px" }}>
          <strong>Your contributions</strong>
        </div>
        {mine.error && <div className="error">{mine.error}</div>}
        {mine.rows.map((item) => (
          <div className="category-row" key={item.id}>
            <div>
              <div style={{ fontWeight: 650 }}>{item.kind.replaceAll("_", " ")}</div>
              <div className="faint">{item.reason ?? "No reason given"}</div>
            </div>
            <span className="badge neutral">{item.status}</span>
          </div>
        ))}
        {!mine.loading && mine.rows.length === 0 && (
          <EmptyState compact icon="upload" title="Nothing submitted yet" hint="Confirm a fit, or add a phone from Browse catalog." />
        )}
      </section>
    </div>
  );
}
