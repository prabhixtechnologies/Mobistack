import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../lib/api";
import { highlightText, groupLine, phoneLabel } from "../lib/compatibility";
import { useAccess } from "../lib/access";
import { PageHeader } from "../ui/PageHeader";
import type {
  CompatibilityGroup,
  CompatibilityOverview,
  DeviceSearchHit,
  GlobalSearchResponse,
  PageResponse,
} from "../lib/types";

interface ChangeRequest {
  id: string;
  action: string;
  status: string;
  reason?: string;
}

export function CompatibilityPage() {
  const access = useAccess();
  const canApprove = access.has("COMPATIBILITY_APPROVE");
  const canWrite = access.has("CATALOG_WRITE");
  const [query, setQuery] = useState("");
  const [overview, setOverview] = useState<CompatibilityOverview | null>(null);
  const [groups, setGroups] = useState<CompatibilityGroup[]>([]);
  const [hits, setHits] = useState<DeviceSearchHit[]>([]);
  const [requests, setRequests] = useState<ChangeRequest[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [searching, setSearching] = useState(false);

  useEffect(() => {
    api<CompatibilityOverview>("/api/v1/compatibility-groups/overview")
      .then(setOverview)
      .catch((err: Error) => setError(err.message));
  }, []);

  useEffect(() => {
    if (!canApprove) {
      return;
    }
    api<ChangeRequest[]>("/api/v1/compatibility-requests")
      .then(setRequests)
      .catch(() => undefined);
  }, [canApprove]);

  useEffect(() => {
    if (query.trim().length < 2) {
      setHits([]);
      setGroups([]);
      setSearching(false);
      return;
    }
    let live = true;
    const handle = window.setTimeout(async () => {
      setSearching(true);
      try {
        const [search, page] = await Promise.all([
          api<GlobalSearchResponse>(`/api/v1/search?q=${encodeURIComponent(query)}`),
          api<PageResponse<CompatibilityGroup>>(
            `/api/v1/compatibility-groups?q=${encodeURIComponent(query)}&size=40`,
          ),
        ]);
        if (!live) {
          return;
        }
        setHits(search.devices ?? []);
        setGroups(page.content ?? []);
        setError(null);
      } catch (err) {
        if (!live) {
          return;
        }
        setError(err instanceof Error ? err.message : "Search failed");
      } finally {
        if (live) {
          setSearching(false);
        }
      }
    }, 140);
    return () => {
      live = false;
      window.clearTimeout(handle);
    };
  }, [query]);

  async function decide(id: string, action: "approve" | "reject") {
    try {
      await api(`/api/v1/compatibility-requests/${id}/${action}`, { method: "POST" });
      setRequests((current) => current.filter((row) => row.id !== id));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not update request");
    }
  }

  const searchingNow = query.trim().length >= 2;
  const categories = overview?.categories ?? [];
  const matchLabel = useMemo(() => {
    if (!searchingNow) {
      return null;
    }
    return `Match: ${groups.length}`;
  }, [groups.length, searchingNow]);

  return (
    <div className="page">
      <PageHeader
        kicker="Catalog"
        title="Compatibility"
        subtitle="Open a part list, then add the phones that share that part. Search still finds a phone on the counter."
        actions={
          canWrite ? (
            <Link className="btn ghost" to="/import">
              Import list
            </Link>
          ) : null
        }
      />
      {error && <div className="error">{error}</div>}
      <input
        className="field"
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        placeholder="Search 9A, Realme 6, iPhone 11…"
        autoFocus
      />
      {matchLabel && <p className="compat-match">{searching ? "Searching…" : matchLabel}</p>}

      {searchingNow ? (
        <>
          {groups.length > 0 && (
            <section className="card tight">
              {groups.map((group, index) => (
                <Link
                  key={group.id}
                  className={`universal-row${groupLine(group.devices).toLowerCase().includes(query.trim().toLowerCase()) ? " is-hit" : ""}`}
                  to={group.categoryId ? `/compatibility/${group.categoryId}` : "/compatibility"}
                >
                  <span className="universal-row__n">{index + 1}</span>
                  <div>
                    <div className="universal-row__meta">
                      {group.categoryName ?? "Unfiled"}
                      {group.verified ? " · Verified" : ""}
                    </div>
                    <div className="universal-row__line">{highlightText(groupLine(group.devices) || group.name, query)}</div>
                  </div>
                </Link>
              ))}
            </section>
          )}
          <section className="card tight">
            {hits.map((device) => (
              <Link key={device.id} to={`/devices/${device.id}`} className="category-row">
                <div>
                  <div style={{ fontWeight: 650 }}>{phoneLabel(device)}</div>
                  <div className="faint">
                    {device.modelCode ?? "No factory code"}
                    {device.matchedAliases.length > 0 ? ` · ${device.matchedAliases.join(", ")}` : ""}
                  </div>
                </div>
                <span className="badge neutral">Stock &amp; price</span>
              </Link>
            ))}
            {hits.length === 0 && groups.length === 0 && (
              <div className="empty">
                {searching ? "Searching…" : "No matching group or phone in this shop yet."}
              </div>
            )}
          </section>
        </>
      ) : (
        <section className="universal-hub">
          {categories.map((category, index) => (
            <Link
              key={category.id}
              to={`/compatibility/${category.id}`}
              className="universal-card"
              style={{ ["--card-accent" as string]: category.color || "#8B5CF6" }}
            >
              <span className="universal-card__n">{index + 1}</span>
              <strong>{category.name}</strong>
              <span>
                {category.groupCount} {category.groupCount === 1 ? "group" : "groups"}
              </span>
            </Link>
          ))}
          {categories.length === 0 && !error && (
            <div className="empty card">Loading the part lists…</div>
          )}
        </section>
      )}

      {canApprove && requests.length > 0 && (
        <section className="card tight">
          <div className="spread" style={{ padding: "16px 18px" }}>
            <strong>Pending changes</strong>
          </div>
          {requests.map((request) => (
            <div className="category-row" key={request.id}>
              <div>
                {request.action.replaceAll("_", " ")} · {request.reason ?? "No reason"}
              </div>
              <div className="row">
                <button className="btn" type="button" onClick={() => void decide(request.id, "approve")}>
                  Approve
                </button>
                <button className="btn ghost" type="button" onClick={() => void decide(request.id, "reject")}>
                  Reject
                </button>
              </div>
            </div>
          ))}
        </section>
      )}
    </div>
  );
}
