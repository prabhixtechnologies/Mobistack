import { FormEvent, useEffect, useState } from "react";
import { api } from "../lib/api";
import type { PageResponse } from "../lib/types";

interface ImportJob {
  id: string;
  kind: string;
  status: string;
  sourceName?: string;
  resultJson?: { warnings?: string[] };
}

export function ImportPage() {
  const [brand, setBrand] = useState("Realme");
  const [text, setText] = useState("Realme 6 = Realme 6i = Realme 7 = Narzo 20");
  const [result, setResult] = useState<string | null>(null);
  const [jobs, setJobs] = useState<ImportJob[]>([]);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    const page = await api<PageResponse<ImportJob>>("/api/v1/imports?size=20");
    setJobs(page.content);
  }

  useEffect(() => {
    load().catch((err: Error) => setError(err.message));
  }, []);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    try {
      const body = await api<{ groups: number; devices: number; aliases: number; warnings: string[] }>(
        "/api/v1/imports/compatibility",
        { method: "POST", body: JSON.stringify({ brand, text, sourceName: "paste" }) },
      );
      setResult(`Imported ${body.groups} groups, ${body.devices} devices, ${body.aliases} aliases.`);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Import failed");
    }
  }

  return (
    <div className="page">
      <div className="page-title">
        <div>
          <h1>Import</h1>
          <p>Paste the old universal list. <code>A = B = C</code>, CSV, or JSON all become aliases on the first model.</p>
        </div>
      </div>
      {error && <div className="error">{error}</div>}
      {result && <div className="muted">{result}</div>}
      <form className="card stack" onSubmit={submit}>
        <input className="field" value={brand} onChange={(e) => setBrand(e.target.value)} />
        <textarea className="field" rows={8} value={text} onChange={(e) => setText(e.target.value)} />
        <button className="btn">Import compatibility</button>
      </form>
      <div className="card tight">
        {jobs.map((job) => (
          <div className="category-row" key={job.id}>
            <div>
              <div style={{ fontWeight: 650 }}>{job.kind}</div>
              <div className="faint">{job.sourceName} · {job.status}</div>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
