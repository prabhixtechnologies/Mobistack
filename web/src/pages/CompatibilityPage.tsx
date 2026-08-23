import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../lib/api";
import { useAccess } from "../lib/access";
import { PageHeader } from "../ui/PageHeader";
import type { DeviceSearchHit, GlobalSearchResponse, PageResponse, PartSearchHit } from "../lib/types";

interface Group {
  id: string;
  name: string;
  categoryName?: string;
  devices?: { brandName?: string; deviceName: string }[];
}

interface ChangeRequest {
  id: string;
  action: string;
  status: string;
  reason?: string;
}

export function CompatibilityPage() {
  const access = useAccess();
  const canApprove = access.has("COMPATIBILITY_APPROVE");
  const [query, setQuery] = useState("");
  const [hits, setHits] = useState<DeviceSearchHit[]>([]);
  const [parts, setParts] = useState<PartSearchHit[]>([]);
  const [groups, setGroups] = useState<Group[]>([]);
  const [requests, setRequests] = useState<ChangeRequest[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [searching, setSearching] = useState(false);

  useEffect(() => {
    if (query.trim().length < 2) {
      setHits([]);
      setParts([]);
      setSearching(false);
      return;
    }
    const handle = window.setTimeout(async () => {
      setSearching(true);
      try {
        const result = await api<GlobalSearchResponse>(`/api/v1/search?q=${encodeURIComponent(query)}`);
        setHits(result.devices ?? []);
        setParts(result.parts ?? []);
        setError(null);
      } catch (err) {
        setError(err instanceof Error ? err.message : "Search failed");
      } finally {
        setSearching(false);
      }
    }, 140);
    return () => window.clearTimeout(handle);
  }, [query]);

  useEffect(() => {
    api<PageResponse<Group>>("/api/v1/compatibility-groups?size=40")
      .then((page) => setGroups(page.content ?? []))
      .catch(() => undefined);
    if (!canApprove) {
      return;
    }
    api<ChangeRequest[]>("/api/v1/compatibility-requests")
      .then(setRequests)
      .catch(() => undefined);
  }, [canApprove]);

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
      <PageHeader
        kicker="Catalog"
        title="Compatibility"
        subtitle="Type the phone on the counter. Open it to see every part that fits, with stock and price."
      />
      {error && <div className="error">{error}</div>}
      <input
        className="field"
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        placeholder="Realme 6, iPhone 11, RMX2002…"
        autoFocus
      />
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
                {device.partsInStock > 0 ? ` · ${device.partsInStock} in stock` : ""}
              </div>
            </div>
            <span className="badge neutral">Open</span>
          </Link>
        ))}
        {parts.map((part) => (
          <div key={part.variantId} className="category-row">
            <div>
              <div style={{ fontWeight: 650 }}>{part.productName}</div>
              <div className="faint">
                {[part.variantName, part.sku, part.categoryName].filter(Boolean).join(" · ")}
              </div>
            </div>
            <span className="badge neutral">{part.availableQty} in stock</span>
          </div>
        ))}
        {hits.length === 0 && parts.length === 0 && (
          <div className="empty">
            {searching
              ? "Searching…"
              : query.trim().length < 2
                ? "Keep typing. Two characters is enough."
                : "No matching phone or part in this shop yet."}
          </div>
        )}
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
      {groups.length > 0 && (
        <section className="card tight">
          {groups.map((group) => (
            <div className="category-row" key={group.id}>
              <div>
                <div style={{ fontWeight: 650 }}>{group.name}</div>
                <div className="faint">
                  {[group.categoryName, (group.devices ?? [])
                    .map((device) => [device.brandName, device.deviceName].filter(Boolean).join(" "))
                    .filter(Boolean)
                    .join(", ")].filter(Boolean).join(" · ")}
                </div>
              </div>
            </div>
          ))}
        </section>
      )}
    </div>
  );
}
