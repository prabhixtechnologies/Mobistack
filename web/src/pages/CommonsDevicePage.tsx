import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api } from "../lib/api";
import { useAccess } from "../lib/access";
import { useAuth } from "../lib/auth";
import { brandMark, brandTone } from "../lib/brandTone";
import { hasFeature } from "../lib/plan";
import { useFitmentGroups } from "../lib/groups";
import { useResource } from "../lib/useResource";
import { EmptyState, ErrorState } from "../ui/EmptyState";
import { Modal } from "../ui/Modal";
import { PageHeader } from "../ui/PageHeader";
import { FitmentGroupBar } from "../ui/FitmentGroupBar";
import { TextField } from "../ui/Field";
import { phoneCaption } from "../ui/CatalogEditor";
import type { CatalogStockRow, CommonsDevice, CommonsFamily, CommonsFit } from "../lib/types";

export function CommonsDevicePage() {
  const { id } = useParams();
  const { user } = useAuth();
  const access = useAccess();
  const canOpenStock = hasFeature(user, "INVENTORY") && access.has("INVENTORY_READ");
  const canSell = hasFeature(user, "SALES") && access.has("SALES_READ");
  const fitment = useFitmentGroups();
  const device = useResource<CommonsDevice>(id ? `/api/v1/mobistack/commons/devices?deviceId=${id}` : null);
  const families = useResource<CommonsFamily[]>(
    id && fitment.ready ? `/api/v1/mobistack/commons/devices/family?deviceId=${id}` : null,
  );
  const fits = useResource<CommonsFit[]>(
    id && fitment.ready ? `/api/v1/mobistack/commons/devices/fits?deviceId=${id}` : null,
  );
  const stock = useResource<CatalogStockRow[]>(
    id && canOpenStock ? `/api/v1/mobistack/inventory/catalog-links/devices/stock?catalogDeviceId=${id}` : null,
  );
  const [disputeFor, setDisputeFor] = useState<CommonsFit | null>(null);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function contribute(kind: "CONFIRM_FITMENT" | "DISPUTE_FITMENT", fitmentId: string, note?: string) {
    setBusy(true);
    setError(null);
    try {
      await api("/api/v1/mobistack/commons/contributions", {
        method: "POST",
        body: JSON.stringify({ kind, targetId: fitmentId, reason: note || undefined }),
      });
      fits.reload();
      families.reload();
      setDisputeFor(null);
      setReason("");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not record that.");
    } finally {
      setBusy(false);
    }
  }

  if (device.error) {
    return (
      <div className="page">
        <ErrorState message={device.error} onRetry={device.reload} />
      </div>
    );
  }

  if (!device.data) {
    return (
      <div className="page">
        <div className="skeleton skeleton--title" />
      </div>
    );
  }

  const title = [device.data.name, device.data.variant].filter(Boolean).join(" ");

  return (
    <div className="page">
      <FitmentGroupBar
        groups={fitment.groups}
        selected={fitment.selected}
        choose={fitment.choose}
        create={fitment.create}
        current={fitment.current}
      />
      <div className="device-hero">
        <span className="device-hero__mark" style={brandTone(device.data.brandName)}>
          {brandMark(device.data.brandName)}
        </span>
        <PageHeader
          kicker={
            <Link to={`/commons/brands/${device.data.brandId}`}>
              Fitment Catalog · {device.data.brandName}
            </Link>
          }
          title={title}
          subtitle="Compatible models sit with the spare they share. Stock is this shop’s, live."
          meta={
            <div className="device-hero__facts">
              {device.data.modelCode ? <code>{device.data.modelCode}</code> : null}
              {device.data.releaseYear ? <span className="page-stat">{device.data.releaseYear}</span> : null}
            </div>
          }
        />
      </div>
      {error && <div className="error">{error}</div>}

      {(families.data ?? []).map((family) => {
        const others = family.members.filter((member) => member.id !== id);
        const stockRows = (stock.data ?? []).filter((row) => row.componentId === family.id);
        return (
          <section className="card catalog-family" key={family.id}>
            <div className="spread">
              <div>
                <p className="page-kicker">{family.categoryName}</p>
                <strong>{family.name}</strong>
              </div>
              <Link className="btn ghost" to={`/commons/components/${family.id}`}>
                Family
              </Link>
            </div>
            <div className="chips">
              <span className="chip">{title}</span>
              {others.map((member) => (
                <Link className="chip" key={member.id} to={`/commons/devices/${member.id}`}>
                  {phoneCaption(member)}
                </Link>
              ))}
              {others.length === 0 && <span className="faint">No other models on this spare yet.</span>}
            </div>
            {canOpenStock && (
              <div className="catalog-stock">
                {stockRows.map((row) => (
                  <div className="category-row" key={row.variantId}>
                    <div>
                      <div style={{ fontWeight: 650 }}>{row.name}</div>
                      <div className="faint">{row.sku}</div>
                    </div>
                    <span className="badge GREEN">{row.available} on hand</span>
                    {canSell && (
                      <Link className="btn" to={`/sales?q=${encodeURIComponent(row.sku)}`}>
                        Sell
                      </Link>
                    )}
                  </div>
                ))}
                {stockRows.length === 0 && (
                  <p className="faint">
                    No linked stock. <Link to="/inventory/catalog-links">Link a SKU</Link>
                  </p>
                )}
              </div>
            )}
          </section>
        );
      })}

      {!families.loading && (families.data ?? []).length === 0 && (
        <EmptyState compact icon="link" title="Nothing linked yet" hint="Confirm a part from the bench, or add a family." />
      )}

      <section className="card tight">
        <div className="spread" style={{ padding: "16px 18px" }}>
          <strong>Fitment edges</strong>
        </div>
        {(fits.data ?? []).map((fit) => (
          <div className="category-row" key={fit.fitmentId}>
            <div>
              <Link to={`/commons/components/${fit.componentId}`} style={{ fontWeight: 650 }}>
                {fit.componentName ?? "Part"}
              </Link>
              <div className="faint">
                {fit.fit}
                {fit.verified ? " · Verified" : ""}
                {fit.disputed ? " · Disputed" : ""}
                {` · ${fit.confirmations} confirm · ${fit.disputes} dispute`}
              </div>
            </div>
            <div className="row">
              <button
                className="btn ghost"
                type="button"
                disabled={busy}
                onClick={() => void contribute("CONFIRM_FITMENT", fit.fitmentId)}
              >
                Confirm
              </button>
              <button className="btn ghost" type="button" disabled={busy} onClick={() => setDisputeFor(fit)}>
                Dispute
              </button>
            </div>
          </div>
        ))}
      </section>

      <Modal
        open={disputeFor != null}
        title="Dispute this fit"
        description="Disputes wait for review. Say what you saw on the bench."
        onClose={() => !busy && setDisputeFor(null)}
        footer={
          <>
            <button className="btn ghost" type="button" onClick={() => setDisputeFor(null)} disabled={busy}>
              Cancel
            </button>
            <button
              className="btn"
              type="button"
              disabled={busy || !reason.trim()}
              onClick={() => disputeFor && void contribute("DISPUTE_FITMENT", disputeFor.fitmentId, reason.trim())}
            >
              {busy ? "Sending…" : "Submit dispute"}
            </button>
          </>
        }
      >
        <TextField label="What doesn’t fit" value={reason} onChange={(event) => setReason(event.target.value)} />
      </Modal>
    </div>
  );
}
