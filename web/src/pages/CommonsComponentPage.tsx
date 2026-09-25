import { Link, useParams } from "react-router-dom";
import { useResource } from "../lib/useResource";
import { EmptyState, ErrorState } from "../ui/EmptyState";
import { PageHeader } from "../ui/PageHeader";
import { FitmentGroupBar } from "../ui/FitmentGroupBar";
import { useFitmentGroups } from "../lib/groups";
import type { CommonsComponent, CommonsDevice } from "../lib/types";

function deviceLabel(device: CommonsDevice): string {
  return [device.brandName, device.name, device.variant].filter(Boolean).join(" ");
}

export function CommonsComponentPage() {
  const { id } = useParams();
  const fitment = useFitmentGroups();
  const component = useResource<CommonsComponent>(id ? `/api/v1/mobistack/commons/components?componentId=${id}` : null);
  const devices = useResource<CommonsDevice[]>(
    id && fitment.ready ? `/api/v1/mobistack/commons/components/devices?componentId=${id}&groupId=${fitment.selected ?? ""}` : null,
  );

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
      <FitmentGroupBar
        groups={fitment.groups}
        selected={fitment.selected}
        choose={fitment.choose}
        create={fitment.create}
        current={fitment.current}
      />
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
