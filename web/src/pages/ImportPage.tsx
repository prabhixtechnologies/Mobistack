import { FormEvent, useEffect, useState } from "react";
import { api } from "../lib/api";
import { useAction } from "../lib/useAction";
import { EmptyState } from "../ui/EmptyState";
import { PageHeader } from "../ui/PageHeader";
import type { CompatibilityOverview, PageResponse } from "../lib/types";

interface ImportJob {
  id: string;
  kind: string;
  status: string;
  sourceName?: string;
  resultJson?: { warnings?: string[]; groups?: number; devices?: number };
}

export function ImportPage() {
  const [categoryId, setCategoryId] = useState("");
  const [brand, setBrand] = useState("");
  const [text, setText] = useState("Samsung A32 4G = Samsung M32 4G\nRedmi 9 = Redmi 9A = Redmi 9C");
  const [categories, setCategories] = useState<{ id: string; name: string }[]>([]);
  const [result, setResult] = useState<string | null>(null);
  const [jobs, setJobs] = useState<ImportJob[]>([]);
  const [error, setError] = useState<string | null>(null);

  async function load() {
    const [overview, page] = await Promise.all([
      api<CompatibilityOverview>("/api/v1/mobistack/compatibility-groups/overview"),
      api<PageResponse<ImportJob>>("/api/v1/mobistack/imports?size=20"),
    ]);
    setCategories(overview.categories);
    if (!categoryId && overview.categories[0]) {
      setCategoryId(overview.categories[0].id);
    }
    setJobs(page.content);
  }

  useEffect(() => {
    load().catch((err: Error) => setError(err.message));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const runImport = useAction(
    async () => {
      const body = await api<{ groups: number; devices: number; aliases: number; warnings: string[] }>(
        "/api/v1/mobistack/imports/compatibility",
        {
          method: "POST",
          body: JSON.stringify({
            brand: brand.trim() || null,
            text,
            sourceName: "paste",
            categoryId,
          }),
        },
      );
      const warningText = body.warnings?.length ? ` ${body.warnings.length} skipped.` : "";
      setResult(`Imported ${body.groups} groups and ${body.devices} phones.${warningText}`);
      await load();
    },
    { fallbackError: "That list could not be imported. Nothing was created." },
  );

  function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setResult(null);
    void runImport.run();
  }

  return (
    <div className="page">
      <PageHeader
        kicker="Catalog"
        title="Import"
        subtitle="Paste the old universal list. Each A = B = C line becomes a real compatibility group in the category you pick."
      />
      {error && <div className="error">{error}</div>}
      {runImport.error && <div className="error">{runImport.error}</div>}
      {result && <div className="muted">{result}</div>}
      <form className="card stack" onSubmit={submit}>
        <label className="stack" style={{ gap: 6 }}>
          <span className="faint">Part category</span>
          <select className="field" value={categoryId} onChange={(event) => setCategoryId(event.target.value)} required>
            {categories.map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
              </option>
            ))}
          </select>
        </label>
        <label className="stack" style={{ gap: 6 }}>
          <span className="faint">Default brand when a line has no brand</span>
          <input
            className="field"
            value={brand}
            onChange={(event) => setBrand(event.target.value)}
            placeholder="Samsung (optional)"
          />
        </label>
        <label className="stack" style={{ gap: 6 }}>
          <span className="faint">List</span>
          <textarea className="field" rows={10} value={text} onChange={(event) => setText(event.target.value)} />
        </label>
        <button className="btn" disabled={runImport.busy || !categoryId || !text.trim()}>
          {runImport.busy ? "Importing…" : "Import compatibility"}
        </button>
      </form>
      <div className="card tight">
        {jobs.length === 0 ? (
          <EmptyState compact icon="upload" title="No imports yet" hint="Paste a list above and the jobs will show here." />
        ) : (
          jobs.map((job) => (
          <div className="category-row" key={job.id}>
            <div>
              <div style={{ fontWeight: 650 }}>{job.kind}</div>
              <div className="faint">
                {job.sourceName} · {job.status}
                {job.resultJson?.groups != null ? ` · ${job.resultJson.groups} groups` : ""}
              </div>
            </div>
          </div>
          ))
        )}
      </div>
    </div>
  );
}
