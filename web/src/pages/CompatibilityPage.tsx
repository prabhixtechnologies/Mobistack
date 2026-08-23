import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../lib/api";
import { PageHeader } from "../ui/PageHeader";
import type { DeviceSearchHit, GlobalSearchResponse, PageResponse } from "../lib/types";

interface Group {
  id: string;
  name: string;
  categoryName?: string;
  deviceCount?: number;
  devices?: { deviceName: string }[];
}

interface ChangeRequest {
  id: string;
  action: string;
  status: string;
  reason?: string;
}

export function CompatibilityPage() {
  const [query, setQuery] = useState("realme 6");
  const [hits, setHits] = useState<DeviceSearchHit[]>([]);
  const [groups, setGroups] = useState<Group[]>([]);
  const [requests, setRequests] = useState<ChangeRequest[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (query.trim().length < 2) {
      setHits([]);
      return;
    }
    const handle = window.setTimeout(async () => {
      try {
        const result = await api<GlobalSearchResponse>(`/api/v1/search?q=${encodeURIComponent(query)}`);
        setHits(result.devices);
        setError(null);
      } catch (err) {
        setError(err instanceof Error ? err.message : "Search failed");
      }
    }, 140);
    return () => window.clearTimeout(handle);
  }, [query]);

  useEffect(() => {
    api<PageResponse<Group>>("/api/v1/compatibility-groups?size=40")
      .then((page) => setGroups(page.content))
      .catch((err: Error) => setError(err.message));
    api<ChangeRequest[]>("/api/v1/compatibility-requests")
      .then(setRequests)
      .catch((err: Error) => setError(err.message));
  }, []);

  async function decide(id: string, action: "approve" | "reject") {
    try {
      await api(`/api/v1/compatibility-requests/${id}/${action}`, { method: "POST" });
      setRequests((current) => current.filter((row) => row.id !== id));
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not update request");
    }
  }

  return (
    <div className="page">
      <PageHeader kicker="Catalog" title="Compatibility" subtitle="Type the phone the customer put on the counter. Everything that fits comes back with it." />
      {error && <div className="error">{error}</div>}
      <input className="field" value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Realme 6, iPhone 11, RMX2002…" />
      <div className="card tight">
        {hits.map((device) => (
          <Link key={device.id} to={`/devices/${device.id}`} className="category-row">
            <div>
              <div style={{ fontWeight: 650 }}>
                {device.brandName} {device.name}
              </div>
              <div className="faint">
                {device.modelCode ?? "No factory code"}
                {device.matchedAliases.length > 0 ? ` · ${device.matchedAliases.join(", ")}` : ""}
              </div>
            </div>
            <span className="badge neutral">Open</span>
          </Link>
        ))}
        {hits.length === 0 && <div className="empty">Keep typing. Two characters is enough.</div>}
      </div>
      {requests.length > 0 && (
        <section className="card tight">
          <div className="spread" style={{ padding: "16px 18px" }}><strong>Pending changes</strong></div>
          {requests.map((request) => (
            <div className="category-row" key={request.id}>
              <div>{request.action.replaceAll("_", " ")} · {request.reason ?? "No reason"}</div>
              <div className="row">
                <button className="btn" type="button" onClick={() => void decide(request.id, "approve")}>Approve</button>
                <button className="btn ghost" type="button" onClick={() => void decide(request.id, "reject")}>Reject</button>
              </div>
            </div>
          ))}
        </section>
      )}
      <section className="card tight">
        {groups.map((group) => (
          <div className="category-row" key={group.id}>
            <div>
              <div style={{ fontWeight: 650 }}>{group.name}</div>
              <div className="faint">{group.categoryName} · {(group.devices ?? []).map((device) => device.deviceName).join(", ")}</div>
            </div>
          </div>
        ))}
      </section>
    </div>
  );
}
