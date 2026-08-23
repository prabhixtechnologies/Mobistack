import { FormEvent, useEffect, useState } from "react";
import { api, money } from "../lib/api";
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

export function RepairsPage() {
  const [jobs, setJobs] = useState<Repair[]>([]);
  const [problem, setProblem] = useState("");
  const [imei, setImei] = useState("");
  const [labor, setLabor] = useState(500);
  const [error, setError] = useState<string | null>(null);
  const [partFor, setPartFor] = useState<Repair | null>(null);
  const [variants, setVariants] = useState<ProductVariant[]>([]);

  async function load() {
    const page = await api<PageResponse<Repair>>("/api/v1/repairs?size=40");
    setJobs(page.content);
  }

  useEffect(() => {
    load().catch((err: Error) => setError(err.message));
    api<PageResponse<ProductVariant>>("/api/v1/variants?size=40")
      .then((page) => setVariants(page.content))
      .catch(() => undefined);
  }, []);

  async function create(event: FormEvent) {
    event.preventDefault();
    setError(null);
    try {
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
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not open job");
    }
  }

  async function setStatus(job: Repair, status: string) {
    try {
      await api(`/api/v1/repairs/${job.id}`, { method: "PUT", body: JSON.stringify({ status }) });
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not update job");
    }
  }

  async function collect(job: Repair) {
    if (job.outstanding <= 0) return;
    try {
      await api(`/api/v1/repairs/${job.id}/payments`, {
        method: "POST",
        body: JSON.stringify({ payments: [{ method: "CASH", amount: job.outstanding }] }),
      });
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not collect");
    }
  }

  return (
    <div className="page">
      <div className="page-title">
        <div>
          <h1>Repairs</h1>
          <p>Open a job, fit a part from stock, collect when it is ready.</p>
        </div>
      </div>
      {error && <div className="error">{error}</div>}

      <form className="card stack" onSubmit={create}>
        <strong>New job</strong>
        <input className="field" value={problem} onChange={(e) => setProblem(e.target.value)} placeholder="Cracked display" required />
        <div className="grid-2">
          <input className="field" value={imei} onChange={(e) => setImei(e.target.value)} placeholder="IMEI (optional)" />
          <input className="field" type="number" min={0} value={labor} onChange={(e) => setLabor(Number(e.target.value))} />
        </div>
        <button className="btn">Open job</button>
      </form>

      {jobs.map((job) => (
        <article className="card stack" key={job.id}>
          <div className="spread">
            <div>
              <div style={{ fontWeight: 700 }}>{job.jobNumber}</div>
              <div className="faint">{job.customerName ?? "Walk-in"} · {job.deviceName ?? "Device unknown"}</div>
            </div>
            <span className="badge neutral">{job.status}</span>
          </div>
          <p style={{ margin: 0 }}>{job.problem}</p>
          <div className="muted">
            {money.format(job.total)} · paid {money.format(job.paid)} · profit {money.format(job.profit)}
          </div>
          <div className="row">
            <select className="select" value={job.status} onChange={(e) => void setStatus(job, e.target.value)} style={{ width: 200 }}>
              {STATUSES.map((status) => (
                <option key={status} value={status}>{status.replaceAll("_", " ")}</option>
              ))}
            </select>
            <button className="btn ghost" type="button" onClick={() => setPartFor(job)}>Add part</button>
            {job.outstanding > 0 && (
              <button className="btn" type="button" onClick={() => void collect(job)}>Collect {money.format(job.outstanding)}</button>
            )}
          </div>
        </article>
      ))}

      {partFor && (
        <div className="login-wrap" style={{ position: "fixed", inset: 0, background: "rgba(20,19,15,0.35)", zIndex: 20 }}>
          <form
            className="login-card stack"
            onSubmit={async (event) => {
              event.preventDefault();
              const form = new FormData(event.currentTarget);
              try {
                await api(`/api/v1/repairs/${partFor.id}/parts`, {
                  method: "POST",
                  body: JSON.stringify({
                    variantId: form.get("variantId"),
                    quantity: Number(form.get("quantity")),
                  }),
                });
                setPartFor(null);
                await load();
              } catch (err) {
                setError(err instanceof Error ? err.message : "Could not add part");
              }
            }}
          >
            <div className="spread">
              <h1 style={{ fontSize: 28, margin: 0 }}>Add part</h1>
              <button className="btn ghost" type="button" onClick={() => setPartFor(null)}>Close</button>
            </div>
            <select className="select" name="variantId" required>
              {variants.map((variant) => (
                <option key={variant.id} value={variant.id}>
                  {variant.productName} · {variant.variantName} ({variant.availableQty})
                </option>
              ))}
            </select>
            <input className="field" name="quantity" type="number" min={1} defaultValue={1} />
            <button className="btn">Use from stock</button>
          </form>
        </div>
      )}
    </div>
  );
}
