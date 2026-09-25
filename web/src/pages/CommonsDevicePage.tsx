import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api } from "../lib/api";
import { useAccess } from "../lib/access";
import { useResource } from "../lib/useResource";
import { EmptyState, ErrorState } from "../ui/EmptyState";
import { Modal } from "../ui/Modal";
import { PageHeader } from "../ui/PageHeader";
import { FitmentGroupBar } from "../ui/FitmentGroupBar";
import { useFitmentGroups } from "../lib/groups";
import { TextField } from "../ui/Field";
import { brandMark, brandTone } from "../lib/brandTone";
import type { CatalogStockRow, CommonsDevice, CommonsFit } from "../lib/types";

export function CommonsDevicePage() {
  const { id } = useParams();
  const access = useAccess();
  const fitment = useFitmentGroups();
  const device = useResource<CommonsDevice>(id ? `/api/v1/mobistack/commons/devices?deviceId=${id}` : null);
  const fits = useResource<CommonsFit[]>(
    id && fitment.ready ? `/api/v1/mobistack/commons/devices/fits?deviceId=${id}&groupId=${fitment.selected ?? ""}` : null,
  );
  const stock = useResource<CatalogStockRow[]>(
    id && access.has("INVENTORY_READ") ? `/api/v1/mobistack/inventory/catalog-links/devices/stock?catalogDeviceId=${id}` : null,
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
        <div className="card">
          <div className="skeleton" />
        </div>
      </div>
    );
  }

  const title = [device.data.name, device.data.variant].filter(Boolean).join(" ");
  const tone = brandTone(device.data.brandName);

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
        <span className="device-hero__mark" style={{ background: tone.bg, color: tone.fg }}>
          {brandMark(device.data.brandName)}
        </span>
        <PageHeader
          kicker={<Link to="/commons">Fitment Catalog · {device.data.brandName}</Link>}
          title={title}
          subtitle="Shared catalog phone. Confirm a fit from the bench, or open linked stock for this shop."
          meta={
            <div className="device-hero__facts">
              {device.data.modelCode ? <code>{device.data.modelCode}</code> : null}
              {device.data.releaseYear ? <span className="page-stat">{device.data.releaseYear}</span> : null}
              <span className="page-stat">{device.data.brandName}</span>
            </div>
          }
        />
      </div>
      {error && <div className="error">{error}</div>}

      {access.has("INVENTORY_READ") && (
        <section className="card tight">
          <div className="spread" style={{ padding: "16px 18px" }}>
            <strong>This shop’s stock</strong>
            <Link className="btn ghost" to="/inventory/catalog-links">
              Link a part
            </Link>
          </div>
          {(stock.data ?? []).map((row) => (
            <Link key={row.variantId} className="category-row" to={`/inventory?q=${encodeURIComponent(row.sku)}`}>
              <div>
                <div style={{ fontWeight: 650 }}>{row.name}</div>
                <div className="faint">{row.sku}</div>
              </div>
              <span className="badge GREEN">{row.available} on hand</span>
            </Link>
          ))}
          {!stock.loading && (stock.data ?? []).length === 0 && (
            <EmptyState
              compact
              icon="box"
              title="No linked stock for this phone"
              hint="Point a variant at a catalog part, then it appears here."
            />
          )}
        </section>
      )}

      <section className="card tight">
        <div className="spread" style={{ padding: "16px 18px" }}>
          <strong>What fits</strong>
        </div>
        {fits.error && <div className="error">{fits.error}</div>}
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
        {!fits.loading && (fits.data ?? []).length === 0 && (
          <EmptyState compact icon="link" title="Nothing linked yet" hint="Confirm a part from the bench, or contribute a fitment." />
        )}
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
