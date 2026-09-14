import { Link, useParams } from "react-router-dom";
import { useResource } from "../lib/useResource";
import { EmptyState, ErrorState } from "../ui/EmptyState";
import { PageHeader } from "../ui/PageHeader";
import type { CommonsComponent, CommonsDevice } from "../lib/types";

function deviceLabel(device: CommonsDevice): string {
  return [device.brandName, device.name, device.variant].filter(Boolean).join(" ");
}

export function CommonsComponentPage() {
  const { id } = useParams();
  const component = useResource<CommonsComponent>(id ? `/api/v1/commons/components/${id}` : null);
  const devices = useResource<CommonsDevice[]>(id ? `/api/v1/commons/components/${id}/devices` : null);

  if (component.error) {
    return (
      <div className="page">
        <ErrorState message={component.error} onRetry={component.reload} />
      </div>
    );
  }

  if (!component.data) {
    return (
      <div className="page">
        <div className="skeleton skeleton--title" />
      </div>
    );
  }

  return (
    <div className="page">
      <PageHeader
        kicker={<Link to="/commons">Fitment Catalog</Link>}
        title={component.data.name}
        subtitle={component.data.description || component.data.categoryCode.replaceAll("_", " ")}
      />
      <section className="card tight">
        <div className="spread" style={{ padding: "16px 18px" }}>
          <strong>Phones this part fits</strong>
        </div>
        {devices.error && <div className="error">{devices.error}</div>}
        {(devices.data ?? []).map((device) => (
          <Link key={device.id} className="category-row" to={`/commons/devices/${device.id}`}>
            <div>
              <div style={{ fontWeight: 650 }}>{deviceLabel(device)}</div>
              <div className="faint">{device.modelCode ?? "No factory code"}</div>
            </div>
            <span className="badge neutral">Fits</span>
          </Link>
        ))}
        {!devices.loading && (devices.data ?? []).length === 0 && (
          <EmptyState compact icon="search" title="No phones linked yet" hint="Contribute a fitment from a device page." />
        )}
      </section>
    </div>
  );
}
