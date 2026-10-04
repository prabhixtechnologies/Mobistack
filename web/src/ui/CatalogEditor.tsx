import { FormEvent, useMemo, useState } from "react";
import { api } from "../lib/api";
import { usePagedList } from "../lib/usePagedList";
import { TextField } from "./Field";
import { Modal } from "./Modal";
import type { CommonsDevice } from "../lib/types";

export function AddPhoneModal({
  open,
  onClose,
  onCreated,
}: {
  open: boolean;
  onClose: () => void;
  onCreated: () => void;
}) {
  const [brand, setBrand] = useState("");
  const [name, setName] = useState("");
  const [modelCode, setModelCode] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await api("/api/v1/mobistack/commons/devices", {
        method: "POST",
        body: JSON.stringify({ brand, name, modelCode: modelCode || undefined }),
      });
      setBrand("");
      setName("");
      setModelCode("");
      onCreated();
      onClose();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not add that phone.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal
      open={open}
      title="Add a phone"
      description="Stays in this fitment group. It is not your stock."
      onClose={() => !busy && onClose()}
      footer={
        <>
          <button className="btn ghost" type="button" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button className="btn" type="submit" form="add-phone" disabled={busy}>
            {busy ? "Saving…" : "Add phone"}
          </button>
        </>
      }
    >
      <form id="add-phone" className="stack" onSubmit={submit}>
        <TextField label="Brand" value={brand} onChange={(e) => setBrand(e.target.value)} required />
        <TextField label="Model" value={name} onChange={(e) => setName(e.target.value)} required />
        <TextField label="Factory code" value={modelCode} onChange={(e) => setModelCode(e.target.value)} placeholder="RMX2001" />
        {error && <div className="error">{error}</div>}
      </form>
    </Modal>
  );
}

export function AddFamilyModal({
  open,
  categoryCode,
  onClose,
  onCreated,
}: {
  open: boolean;
  categoryCode?: string;
  onClose: () => void;
  onCreated: () => void;
}) {
  const [name, setName] = useState("");
  const [code, setCode] = useState(categoryCode ?? "TEMPERED_GLASS");
  const [picked, setPicked] = useState<string[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const models = usePagedList<CommonsDevice>(open ? "/api/v1/mobistack/commons/devices" : null, { size: 80 });
  const options = useMemo(
    () =>
      models.rows.map((row) => ({
        id: row.id,
        label: [row.brandName, row.name, row.variant].filter(Boolean).join(" "),
      })),
    [models.rows],
  );

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (picked.length < 2) {
      setError("Pick at least two phones that take the same part.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await api("/api/v1/mobistack/commons/families", {
        method: "POST",
        body: JSON.stringify({ categoryCode: code, name, deviceIds: picked }),
      });
      setName("");
      setPicked([]);
      onCreated();
      onClose();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not save that family.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal
      open={open}
      title="Add a family"
      description="Phones that take the same spare. One part, many models."
      onClose={() => !busy && onClose()}
      footer={
        <>
          <button className="btn ghost" type="button" onClick={onClose} disabled={busy}>
            Cancel
          </button>
          <button className="btn" type="submit" form="add-family" disabled={busy || picked.length < 2}>
            {busy ? "Saving…" : "Save family"}
          </button>
        </>
      }
    >
      <form id="add-family" className="stack" onSubmit={submit}>
        <TextField label="Family name" value={name} onChange={(e) => setName(e.target.value)} required placeholder="A32 / M32 glass" />
        <TextField label="Part type" value={code} onChange={(e) => setCode(e.target.value)} required />
        <label className="form-field">
          <span className="form-field__label">Phones</span>
          <select
            className="select"
            multiple
            size={10}
            value={picked}
            onChange={(event) => setPicked(Array.from(event.target.selectedOptions, (option) => option.value))}
          >
            {options.map((row) => (
              <option key={row.id} value={row.id}>
                {row.label}
              </option>
            ))}
          </select>
        </label>
        {models.hasMore && (
          <button className="btn ghost" type="button" onClick={models.loadMore}>
            Load more phones
          </button>
        )}
        {error && <div className="error">{error}</div>}
      </form>
    </Modal>
  );
}

export function phoneCaption(member: { brandName?: string | null; name: string; variant?: string | null }) {
  return [member.brandName, member.name, member.variant].filter(Boolean).join(" ");
}
