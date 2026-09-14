import { FormEvent, useEffect, useMemo, useState } from "react";
import { api, money } from "../lib/api";
import { useAccess } from "../lib/access";
import { useAction } from "../lib/useAction";
import { usePagedList } from "../lib/usePagedList";
import { EmptyState, ErrorState } from "../ui/EmptyState";
import { LoadMore } from "../ui/DataTable";
import { SelectField, TextField } from "../ui/Field";
import { Modal } from "../ui/Modal";
import { PageHeader } from "../ui/PageHeader";
import { humanLabel } from "../lib/labels";
import type { PageResponse, ProductVariant } from "../lib/types";

interface Repair {
  id: string;
  jobNumber: string;
  status: string;
  customerName?: string;
  deviceName?: string;
  problem: string;
  total: number;
  paid: number;
  outstanding: number;
  profit: number;
  parts?: { variantName?: string; quantity: number; lineTotal: number }[];
}

const STATUSES = ["RECEIVED", "DIAGNOSING", "WAITING_FOR_PART", "IN_REPAIR", "READY", "DELIVERED", "CANCELLED"];

const OPEN_STATUSES = STATUSES.filter((status) => status !== "DELIVERED" && status !== "CANCELLED");

export function RepairsPage() {
  const access = useAccess();
  const canWrite = access.has("REPAIR_WRITE");
  const [filter, setFilter] = useState("");
  const [problem, setProblem] = useState("");
  const [imei, setImei] = useState("");
  const [labor, setLabor] = useState(500);
  const [partFor, setPartFor] = useState<Repair | null>(null);
  const [variants, setVariants] = useState<ProductVariant[]>([]);

  const path = useMemo(
    () => (filter ? `/api/v1/repairs?status=${encodeURIComponent(filter)}` : "/api/v1/repairs"),
    [filter],
  );
  const jobs = usePagedList<Repair>(path, { size: 20 });

  useEffect(() => {
    if (!partFor) {
      return;
    }
    api<PageResponse<ProductVariant>>("/api/v1/variants?size=100")
      .then((page) => setVariants(page.content))
      .catch(() => setVariants([]));
  }, [partFor]);

  const create = useAction(
    async () => {
      await api("/api/v1/repairs", {
        method: "POST",
        body: JSON.stringify({
          problem,
          imei,
          laborCharge: labor,
          laborCost: 0,
          estimatedCost: labor,
          idempotencyKey: crypto.randomUUID(),
        }),
      });
      setProblem("");
      setImei("");
      jobs.reload();
    },
    { fallbackError: "Could not open that job." },
  );

  const changeStatus = useAction(
    async (job: Repair, status: string) => {
      await api(`/api/v1/repairs/${job.id}`, { method: "PUT", body: JSON.stringify({ status }) });
      jobs.reload();
    },
    { fallbackError: "Could not update that job." },
  );

  const collect = useAction(
    async (job: Repair) => {
      await api(`/api/v1/repairs/${job.id}/payments`, {
        method: "POST",
        body: JSON.stringify({ payments: [{ method: "CASH", amount: job.outstanding }] }),
      });
      jobs.reload();
    },
    { fallbackError: "Could not take that payment." },
  );

  const addPart = useAction(
    async (repairId: string, variantId: string, quantity: number) => {
      await api(`/api/v1/repairs/${repairId}/parts`, {
        method: "POST",
        body: JSON.stringify({ variantId, quantity }),
      });
      setPartFor(null);
      jobs.reload();
    },
    { fallbackError: "Could not fit that part." },
  );

  const problems = [create.error, changeStatus.error, collect.error, addPart.error].filter(Boolean);

  function submitJob(event: FormEvent) {
    event.preventDefault();
    void create.run();
  }

  function submitPart(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!partFor) {
      return;
    }
    const form = new FormData(event.currentTarget);
    void addPart.run(partFor.id, String(form.get("variantId")), Number(form.get("quantity")));
  }

  return (
    <div className="page">
      <PageHeader
        kicker="Shop"
        title="Repairs"
        subtitle="Take the phone in, move it through the bench, collect when it is ready for pickup."
      />
      {problems.map((message) => (
        <div className="error" key={message}>
          {message}
        </div>
      ))}

      {canWrite && (
        <form className="toolbar" onSubmit={submitJob}>
          <strong className="visually-hidden">New job</strong>
          <input
            className="field"
            value={problem}
            onChange={(e) => setProblem(e.target.value)}
            placeholder="Cracked display"
            required
          />
          <div className="grid-2">
            <input
              className="field"
              value={imei}
              onChange={(e) => setImei(e.target.value)}
              placeholder="IMEI (optional)"
            />
            <input
              className="field"
              type="number"
              min={0}
              value={labor}
              onChange={(e) => setLabor(Number(e.target.value))}
              aria-label="Labour charge"
            />
          </div>
          <button className="btn" disabled={create.busy}>
            {create.busy ? "Opening…" : "Book repair"}
          </button>
        </form>
      )}

      <div className="toolbar">
        <select className="select" value={filter} onChange={(e) => setFilter(e.target.value)} style={{ width: 220, maxWidth: "100%" }} aria-label="Job status">
          <option value="">All jobs</option>
          {STATUSES.map((status) => (
            <option key={status} value={status}>
              {humanLabel(status)}
            </option>
          ))}
        </select>
        <span className="faint">
          {OPEN_STATUSES.includes(filter) || !filter ? "Newest first." : "Closed jobs stay for the record."}
        </span>
      </div>

      {jobs.error ? (
        <ErrorState message={jobs.error} onRetry={jobs.reload} />
      ) : jobs.loading && jobs.rows.length === 0 ? (
        <div className="card stack">
          {Array.from({ length: 3 }, (_, index) => (
            <div className="skeleton" style={{ height: 72 }} key={index} />
          ))}
        </div>
      ) : jobs.rows.length === 0 ? (
        <EmptyState
          icon="wrench"
          title={filter ? "No jobs in that state" : "No repair jobs yet"}
          hint={
            filter
              ? "Clear the filter to see everything on the bench."
              : "Open a job above and it will show here until it is delivered."
          }
        />
      ) : (
        <>
          <div className="job-list">
          {jobs.rows.map((job) => (
            <article className="job-row" key={job.id}>
              <div>
                <strong>{job.jobNumber}</strong>
                <div className="job-row__meta">
                  {job.customerName ?? "Walk-in"} · {job.deviceName ?? "Device unknown"}
                </div>
                <p style={{ margin: "8px 0 0" }}>{job.problem}</p>
                <div className="muted" style={{ marginTop: 6 }}>
                  {money.format(job.total)} · paid {money.format(job.paid)}
                  {access.has("REPORT_READ") && ` · profit ${money.format(job.profit)}`}
                </div>
              </div>
              <div>
                <span className={`status-chip${job.status === "READY" ? " status-chip--ready" : job.status === "WAITING_FOR_PART" ? " status-chip--warn" : " status-chip--open"}`}>
                  {humanLabel(job.status)}
                </span>
              </div>
              {canWrite && (
                <div className="row">
                  <select
                    className="select"
                    value={job.status}
                    disabled={changeStatus.busy}
                    onChange={(e) => void changeStatus.run(job, e.target.value)}
                    style={{ width: 200, maxWidth: "100%" }}
                    aria-label={`Status for ${job.jobNumber}`}
                  >
                    {STATUSES.map((status) => (
                      <option key={status} value={status}>
                        {humanLabel(status)}
                      </option>
                    ))}
                  </select>
                  <button className="btn ghost" type="button" onClick={() => setPartFor(job)}>
                    Add part
                  </button>
                  {job.outstanding > 0 && (
                    <button className="btn" type="button" disabled={collect.busy} onClick={() => void collect.run(job)}>
                      {collect.busy ? "Taking…" : `Collect ${money.format(job.outstanding)}`}
                    </button>
                  )}
                </div>
              )}
            </article>
          ))}
          </div>
          <div>
            <LoadMore
              loaded={jobs.rows.length}
              total={jobs.total}
              hasMore={jobs.hasMore}
              loadingMore={jobs.loadingMore}
              onLoadMore={jobs.loadMore}
              noun="jobs"
            />
          </div>
        </>
      )}

      <Modal
        open={partFor !== null}
        title="Add part"
        description={partFor ? `Fit stock to ${partFor.jobNumber}` : undefined}
        onClose={() => {
          if (!addPart.busy) {
            setPartFor(null);
          }
        }}
        footer={
          <>
            <button className="btn ghost" type="button" onClick={() => setPartFor(null)} disabled={addPart.busy}>
              Cancel
            </button>
            {variants.length > 0 && (
              <button className="btn" type="submit" form="add-part-form" disabled={addPart.busy}>
                {addPart.busy ? "Fitting…" : "Use from stock"}
              </button>
            )}
          </>
        }
      >
        {variants.length === 0 ? (
          <p className="muted">No stock is available to fit. Receive parts first.</p>
        ) : (
          <form id="add-part-form" className="stack" onSubmit={submitPart}>
            <SelectField label="Part" name="variantId" required>
              {variants.map((variant) => (
                <option key={variant.id} value={variant.id}>
                  {variant.productName} · {variant.variantName} ({variant.availableQty})
                </option>
              ))}
            </SelectField>
            <TextField label="Quantity" name="quantity" type="number" min={1} defaultValue={1} />
          </form>
        )}
      </Modal>
    </div>
  );
}
