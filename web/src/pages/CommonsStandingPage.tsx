import { Link } from "react-router-dom";
import { humanLabel } from "../lib/labels";
import { usePagedList } from "../lib/usePagedList";
import { useResource } from "../lib/useResource";
import { EmptyState } from "../ui/EmptyState";
import { PageHeader } from "../ui/PageHeader";
import { Kpi, Panel } from "../ui/Panel";
import type { CommonsContribution, CommonsStanding } from "../lib/types";

const STATUS_TONE: Record<string, string> = {
  APPLIED: "tint-green",
  ACCEPTED: "tint-green",
  APPROVED: "tint-green",
  PENDING: "tint-amber",
  REJECTED: "tint-red",
  DISPUTED: "tint-rose",
};

function when(iso?: string | null): string {
  return iso ? new Date(iso).toLocaleDateString("en-IN", { day: "numeric", month: "short", year: "numeric" }) : "";
}

export function CommonsStandingPage() {
  const standing = useResource<CommonsStanding>("/api/v1/mobistack/commons/standing");
  const mine = usePagedList<CommonsContribution>("/api/v1/mobistack/commons/contributions/mine", { size: 25 });
  const row = standing.data;
  const reviewed = row ? row.accepted + row.rejected : 0;

  return (
    <div className="page">
      <PageHeader
        icon="pulse"
        kicker={<Link to="/commons">Fitment Catalog</Link>}
        title="Contributor standing"
        subtitle="Accepted contributions raise trust. Disputes and rejections count the other way."
        actions={
          <Link className="btn ghost" to="/commons">
            Browse catalog
          </Link>
        }
      />
      <div className="kpi-grid">
        <Kpi
          icon="check"
          tone="green"
          label="Accepted"
          value={row?.accepted ?? "—"}
          foot={reviewed > 0 ? `${Math.round(((row?.accepted ?? 0) / reviewed) * 100)}% of reviewed` : "Nothing reviewed yet"}
        />
        <Kpi icon="close" tone="rose" label="Rejected" value={row?.rejected ?? "—"} foot="Counts against trust" />
        <Kpi
          icon="shield"
          tone="teal"
          label="Trusted"
          value={row ? (row.trusted ? "Yes" : "Not yet") : "—"}
          foot={row?.trusted ? "Your changes apply straight away" : "Keep contributing accurate fits"}
        />
        <Kpi
          icon="lock"
          tone={row?.banned ? "rose" : "slate"}
          label="Account"
          value={row ? (row.banned ? "Banned" : "In good standing") : "—"}
          foot={row?.banned ? "Contact support to appeal" : "No restrictions"}
        />
      </div>
      {row?.banned && row.bannedReason && <div className="banner banner-warn">{row.bannedReason}</div>}

      <Panel icon="upload" title="Your contributions" count={mine.total > 0 ? mine.total : undefined} flush>
        {mine.error && <div className="error">{mine.error}</div>}
        {mine.rows.length > 0 && (
          <ul className="contrib-list">
            {mine.rows.map((item) => (
              <li key={item.id}>
                <span className="contrib-list__icon" aria-hidden>
                  {item.kind.startsWith("CONFIRM") ? "✓" : "+"}
                </span>
                <div className="contrib-list__body">
                  <strong>{humanLabel(item.kind)}</strong>
                  <span>{item.summary || item.reason || item.reviewNote || "No details recorded"}</span>
                </div>
                <time dateTime={item.createdAt}>{when(item.createdAt)}</time>
                <span className={`badge ${STATUS_TONE[item.status] ?? "neutral"}`}>{humanLabel(item.status)}</span>
              </li>
            ))}
          </ul>
        )}
        {!mine.loading && mine.rows.length === 0 && (
          <EmptyState compact icon="upload" title="Nothing submitted yet" hint="Confirm a fit, or add a phone from Browse catalog." />
        )}
      </Panel>
    </div>
  );
}
