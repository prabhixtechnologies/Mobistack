import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api } from "../lib/api";
import { groupLine, highlightText, splitEqualsLine } from "../lib/compatibility";
import { useAccess } from "../lib/access";
import { EmptyState } from "../ui/EmptyState";
import { TextField } from "../ui/Field";
import { ConfirmDialog, Modal } from "../ui/Modal";
import { PageHeader } from "../ui/PageHeader";
import type { CategoryOverview, CompatibilityGroup, CompatibilityOverview, PageResponse } from "../lib/types";

interface EditorState {
  group: CompatibilityGroup | null;
  name: string;
  line: string;
  verified: boolean;
}

export function CompatibilityCategoryPage() {
  const { categoryId } = useParams();
  const access = useAccess();
  const canWrite = access.has("CATALOG_WRITE");
  const [category, setCategory] = useState<CategoryOverview | null>(null);
  const [groups, setGroups] = useState<CompatibilityGroup[]>([]);
  const [query, setQuery] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [editor, setEditor] = useState<EditorState | null>(null);
  const [pendingDelete, setPendingDelete] = useState<CompatibilityGroup | null>(null);
  const [proposing, setProposing] = useState<string | null>(null);

  async function load(nextQuery = query) {
    if (!categoryId) {
      return;
    }
    const params = new URLSearchParams({ categoryId, size: "200" });
    if (nextQuery.trim().length >= 2) {
      params.set("q", nextQuery.trim());
    }
    const [overview, page] = await Promise.all([
      api<CompatibilityOverview>("/api/v1/compatibility-groups/overview"),
      api<PageResponse<CompatibilityGroup>>(`/api/v1/compatibility-groups?${params}`),
    ]);
    setCategory(overview.categories.find((row) => row.id === categoryId) ?? null);
    setGroups(page.content ?? []);
  }

  useEffect(() => {
    const handle = window.setTimeout(() => {
      load(query).catch((err: Error) => setError(err.message));
    }, query.trim().length < 2 ? 0 : 140);
    return () => window.clearTimeout(handle);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [query, categoryId]);

  const visible = groups;
  const matchCount = query.trim().length >= 2 ? visible.length : null;

  function openCreate() {
    setEditor({ group: null, name: "", line: "", verified: false });
  }

  function openEdit(group: CompatibilityGroup) {
    setEditor({
      group,
      name: group.name,
      line: groupLine(group.devices),
      verified: group.verified,
    });
  }

  async function saveEditor() {
    if (!editor || !categoryId) {
      return;
    }
    const names = splitEqualsLine(editor.line);
    if (names.length === 0) {
      setError("Add at least one phone, like Samsung A32 4G = Samsung M32 4G.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      const name = editor.name.trim() || names[0];
      if (editor.group) {
        await api(`/api/v1/compatibility-groups/${editor.group.id}`, {
          method: "PUT",
          body: JSON.stringify({
            name,
            categoryId,
            verified: editor.verified,
            active: true,
          }),
        });
        await api(`/api/v1/compatibility-groups/${editor.group.id}/membership`, {
          method: "PUT",
          body: JSON.stringify({ deviceTexts: names }),
        });
      } else {
        await api("/api/v1/compatibility-groups", {
          method: "POST",
          body: JSON.stringify({
            name,
            categoryId,
            verified: editor.verified,
            active: true,
            deviceTexts: names,
          }),
        });
      }
      setEditor(null);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save the group");
    } finally {
      setBusy(false);
    }
  }

  async function propose(group: CompatibilityGroup) {
    setBusy(true);
    setError(null);
    setProposing(group.id);
    try {
      await api("/api/v1/commons/contributions", {
        method: "POST",
        body: JSON.stringify({
          kind: "ADD_COMPONENT",
          payload: {
            categoryCode: category?.code ?? "UNFILED",
            name: group.name,
            description: group.notes ?? "",
          },
          reason: `Proposed from private fitment notes. Devices: ${groupLine(group.devices) || group.name}`,
        }),
      });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not propose that note");
    } finally {
      setBusy(false);
      setProposing(null);
    }
  }

  async function copyGroup(group: CompatibilityGroup) {
    setBusy(true);
    setError(null);
    try {
      await api(`/api/v1/compatibility-groups/${group.id}/copy`, {
        method: "POST",
        body: JSON.stringify({}),
      });
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not copy the group");
    } finally {
      setBusy(false);
    }
  }

  async function confirmDelete() {
    if (!pendingDelete) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await api(`/api/v1/compatibility-groups/${pendingDelete.id}`, { method: "DELETE" });
      setPendingDelete(null);
      await load();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not delete the group");
    } finally {
      setBusy(false);
    }
  }

  const title = category?.name ?? "Private fitment notes";

  const subtitle = useMemo(() => {
    if (matchCount != null) {
      return `Match: ${matchCount}`;
    }
    const count = groups.length;
    return `${count} ${count === 1 ? "group" : "groups"}. Phones that take the same ${title.toLowerCase()} belong on one line.`;
  }, [groups.length, matchCount, title]);

  return (
    <div className="page">
      <PageHeader
        kicker={<Link to="/compatibility">Private fitment notes</Link>}
        title={title}
        subtitle={subtitle}
        actions={
          canWrite ? (
            <button className="btn" type="button" onClick={openCreate}>
              Add group
            </button>
          ) : null
        }
      />
      {error && <div className="error">{error}</div>}
      <TextField
        label="Find a model"
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        placeholder="Find a model in this list…"
        autoComplete="off"
      />
      <section className="card tight">
        {visible.map((group, index) => {
          const line = groupLine(group.devices) || group.name;
          const hit = query.trim().length >= 2 && line.toLowerCase().includes(query.trim().toLowerCase());
          return (
            <article className={`universal-row${hit ? " is-hit" : ""}`} key={group.id}>
              <span className="universal-row__n">{index + 1}</span>
              <div>
                <div className="universal-row__line">
                  {highlightText(line, query)}
                  {group.verified ? <span className="universal-ok" title="Verified">✓</span> : null}
                </div>
                <div className="universal-row__meta">
                  {group.name}
                  {group.linkedProductCount > 0 ? ` · ${group.linkedProductCount} parts linked` : ""}
                </div>
              </div>
              {canWrite && (
                <div className="row universal-row__actions">
                  <button className="btn ghost btn--sm" type="button" onClick={() => openEdit(group)} disabled={busy}>
                    Edit
                  </button>
                  <button
                    className="btn ghost btn--sm"
                    type="button"
                    onClick={() => void propose(group)}
                    disabled={busy || proposing === group.id}
                  >
                    {proposing === group.id ? "Proposing…" : "Propose to shared catalog"}
                  </button>
                  <button className="btn ghost btn--sm" type="button" onClick={() => void copyGroup(group)} disabled={busy}>
                    Copy
                  </button>
                  <button
                    className="btn danger-ghost btn--sm"
                    type="button"
                    onClick={() => setPendingDelete(group)}
                    disabled={busy}
                  >
                    Delete
                  </button>
                </div>
              )}
            </article>
          );
        })}
        {visible.length === 0 && (
          <EmptyState
            compact
            icon="search"
            title={
              query.trim().length >= 2
                ? "No group matches that search"
                : "No groups in this list yet"
            }
            hint={
              query.trim().length >= 2
                ? "Try a shorter model name."
                : canWrite
                  ? "Add the first line, or import a pasted list."
                  : "Ask someone with catalogue access to add a group."
            }
          />
        )}
      </section>

      <Modal
        open={editor != null}
        title={editor?.group ? "Edit group" : "Add group"}
        description="One line is one part shape. Separate phones with = . Brands and 4G/5G variants are stored on each model."
        size="md"
        onClose={() => !busy && setEditor(null)}
        footer={
          <>
            <button className="btn ghost" type="button" onClick={() => setEditor(null)} disabled={busy}>
              Cancel
            </button>
            <button className="btn" type="button" onClick={() => void saveEditor()} disabled={busy}>
              {busy ? "Saving…" : "Save group"}
            </button>
          </>
        }
      >
        {editor && (
          <div className="stack">
            <label className="stack" style={{ gap: 6 }}>
              <span className="faint">Name</span>
              <input
                className="field"
                value={editor.name}
                onChange={(event) => setEditor({ ...editor, name: event.target.value })}
                placeholder="Optional — defaults to the first phone"
              />
            </label>
            <label className="stack" style={{ gap: 6 }}>
              <span className="faint">Phones that share this part</span>
              <textarea
                className="field"
                rows={5}
                value={editor.line}
                onChange={(event) => setEditor({ ...editor, line: event.target.value })}
                placeholder="Samsung A32 4G = Samsung M32 4G = Samsung F22"
              />
            </label>
            <label className="row">
              <input
                type="checkbox"
                checked={editor.verified}
                onChange={(event) => setEditor({ ...editor, verified: event.target.checked })}
              />
              Verified on the bench
            </label>
          </div>
        )}
      </Modal>

      <ConfirmDialog
        open={pendingDelete != null}
        title="Delete this group?"
        description={
          pendingDelete && pendingDelete.linkedProductCount > 0
            ? "Parts are still linked, so the group will be hidden rather than removed."
            : "This line will be removed from the list."
        }
        confirmLabel="Delete"
        destructive
        busy={busy}
        error={null}
        onConfirm={() => void confirmDelete()}
        onClose={() => setPendingDelete(null)}
      />
    </div>
  );
}
