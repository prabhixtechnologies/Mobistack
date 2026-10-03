import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { api, money } from "../lib/api";
import { useAccess } from "../lib/access";
import { useAction } from "../lib/useAction";
import { usePagedList } from "../lib/usePagedList";
import { EmptyState, ErrorState } from "../ui/EmptyState";
import { LoadMore } from "../ui/DataTable";
import { Field, SelectField, TextField } from "../ui/Field";
import { Modal } from "../ui/Modal";
import { PageHeader } from "../ui/PageHeader";
import { Panel } from "../ui/Panel";
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

const REPAIR_LANES = [
  { id: "intake", label: "Intake", statuses: ["RECEIVED", "DIAGNOSING"] },
  { id: "bench", label: "On the bench", statuses: ["WAITING_FOR_PART", "IN_REPAIR"] },
  { id: "ready", label: "Ready & closed", statuses: ["READY", "DELIVERED", "CANCELLED"] },
] as const;

function statusTone(status: string): string {
  if (status === "READY" || status === "DELIVERED") return " status-chip--ready";
  if (status === "WAITING_FOR_PART") return " status-chip--warn";
  if (status === "CANCELLED") return "";
  return " status-chip--open";
}

export function RepairsPage() {
  const access = useAccess();
  const canWrite = access.has("REPAIR_WRITE");
  const [filter, setFilter] = useState("");
  const problemRef = useRef<HTMLInputElement>(null);
  const [problem, setProblem] = useState("");
  const [imei, setImei] = useState("");
  const [labor, setLabor] = useState(500);
  const [partFor, setPartFor] = useState<Repair | null>(null);
  const [variants, setVariants] = useState<ProductVariant[]>([]);

  const path = useMemo(
    () => (filter ? `/api/v1/mobistack/repairs?status=${encodeURIComponent(filter)}` : "/api/v1/mobistack/repairs"),
    [filter],
  );
  const jobs = usePagedList<Repair>(path, { size: 20 });

  useEffect(() => {
    if (!partFor) {
      return;
    }
    api<PageResponse<ProductVariant>>("/api/v1/mobistack/variants?size=100")
      .then((page) => setVariants(page.content))
      .catch(() => setVariants([]));
  }, [partFor]);

  const create = useAction(
    async () => {
      await api("/api/v1/mobistack/repairs", {
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
      await api(`/api/v1/mobistack/repairs?id=${job.id}`, { method: "PUT", body: JSON.stringify({ status }) });
      jobs.reload();
    },
    { fallbackError: "Could not update that job." },
  );

  const collect = useAction(
    async (job: Repair) => {
      await api(`/api/v1/mobistack/repairs/payments?id=${job.id}`, {
        method: "POST",
        body: JSON.stringify({ payments: [{ method: "CASH", amount: job.outstanding }] }),
      });
      jobs.reload();
    },
    { fallbackError: "Could not take that payment." },
  );

  const addPart = useAction(
    async (repairId: string, variantId: string, quantity: number) => {
      await api(`/api/v1/mobistack/repairs/parts?id=${repairId}`, {
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
        icon="wrench"
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
        <Panel icon="plus" title="Book a repair" hint="What is wrong, and what you will charge for the work. Parts are added on the bench.">
          <form className="composer composer--repair" onSubmit={submitJob}>
            <Field label="Problem" required>
              {({ id, describedBy }) => (
                <input
                  ref={problemRef}
                  id={id}
                  aria-describedby={describedBy}
                  className="field"
                  value={problem}
                  onChange={(e) => setProblem(e.target.value)}
                  placeholder="Cracked display, not charging…"
                  required
                />
              )}
            </Field>
            <TextField
              label="IMEI"
              value={imei}
              onChange={(e) => setImei(e.target.value)}
              placeholder="Optional"
              inputMode="numeric"
            />
            <TextField
              label="Labour charge (₹)"
              type="number"
              min={0}
              inputMode="decimal"
              value={labor}
              onChange={(e) => setLabor(Math.max(0, Number(e.target.value) || 0))}
            />
            <button className="btn" disabled={create.busy || !problem.trim()}>
              {create.busy ? "Opening…" : "Book repair"}
            </button>
          </form>
        </Panel>
      )}

      <div className="board-bar">
        <div className="board-bar__title">
          <h2>Bench board</h2>
          <span className="faint">
            {OPEN_STATUSES.includes(filter) || !filter ? "Newest first." : "Closed jobs stay for the record."}
          </span>
        </div>
        <select className="select" value={filter} onChange={(e) => setFilter(e.target.value)} aria-label="Job status">
          <option value="">All jobs</option>
          {STATUSES.map((status) => (
            <option key={status} value={status}>
              {humanLabel(status)}
            </option>
          ))}
        </select>
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
          /*
            The hint named the way out and then made the reader go and find it. A filtered
            list clears; an empty one puts the cursor in the form that fills it. The form is
            only rendered for someone who can write, so the button follows it.
          */
          action={
            filter ? (
              <button className="btn ghost" type="button" onClick={() => setFilter("")}>
                Clear the filter
              </button>
            ) : canWrite ? (
              <button
                className="btn ghost"
                type="button"
                onClick={() => {
                  problemRef.current?.scrollIntoView({ block: "center", behavior: "smooth" });
                  problemRef.current?.focus();
                }}
              >
                Open the first job
              </button>
            ) : undefined
          }
        />
      ) : (
        <>
          <div className="repair-board">
            {REPAIR_LANES.map((lane) => {
              const laneJobs = jobs.rows.filter((job) => lane.statuses.some((status) => status === job.status));
              return (
                <section className={`repair-lane repair-lane--${lane.id}`} key={lane.id} aria-labelledby={`repair-lane-${lane.id}`}>
                  <header className="repair-lane__header">
                    <h2 id={`repair-lane-${lane.id}`}>{lane.label}</h2>
                    <span className="repair-lane__count">{laneJobs.length}</span>
                  </header>
                  <div className="repair-lane__list">
                    {laneJobs.map((job) => (
                      <article className={`job-card${job.status === "CANCELLED" ? " job-card--muted" : ""}`} key={job.id}>
                        <div className="job-card__top">
                          <strong>{job.jobNumber}</strong>
                          <span className={`status-chip${statusTone(job.status)}`}>{humanLabel(job.status)}</span>
                        </div>
                        <p className="job-card__problem">{job.problem}</p>
                        <div className="job-card__meta">
                          {job.customerName ?? "Walk-in"} · {job.deviceName ?? "Device unknown"}
                        </div>
                        <dl className="job-card__money">
                          <div>
                            <dt>Total</dt>
                            <dd>{money.format(job.total)}</dd>
                          </div>
                          <div>
                            <dt>Paid</dt>
                            <dd>{money.format(job.paid)}</dd>
                          </div>
                          {access.has("REPORT_READ") && (
                            <div>
                              <dt>Profit</dt>
                              <dd>{money.format(job.profit)}</dd>
                            </div>
                          )}
                        </dl>
                        {canWrite && (
                          <div className="job-card__actions">
                            <select
                              className="select"
                              value={job.status}
                              disabled={changeStatus.busy}
                              onChange={(e) => void changeStatus.run(job, e.target.value)}
                              aria-label={`Status for ${job.jobNumber}`}
                            >
                              {STATUSES.map((status) => (
                                <option key={status} value={status}>
                                  {humanLabel(status)}
                                </option>
                              ))}
                            </select>
                            {OPEN_STATUSES.includes(job.status) && (
                              <button className="btn ghost sm" type="button" onClick={() => setPartFor(job)}>
                                Add part
                              </button>
                            )}
                            {job.outstanding > 0 && (
                              <button className="btn sm" type="button" disabled={collect.busy} onClick={() => void collect.run(job)}>
                                {collect.busy ? "Taking…" : `Collect ${money.format(job.outstanding)}`}
                              </button>
                            )}
                          </div>
                        )}
                      </article>
                    ))}
                    {laneJobs.length === 0 && <p className="repair-lane__empty">Nothing here right now.</p>}
                  </div>
                </section>
              );
            })}
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
