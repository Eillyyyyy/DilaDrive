import { useEffect, useState } from "react";
import api from "../services/api";

export default function UnavailablePeriods({ resource, resourceId }) {
  const [open, setOpen] = useState(false);
  return <details onToggle={event => setOpen(event.currentTarget.open)} className="rounded-xl border border-slate-200 p-4">
    <summary className="cursor-pointer text-sm font-semibold text-slate-900">Unavailable periods</summary>
    {open && <PeriodControls resource={resource} resourceId={resourceId} />}
  </details>;
}

const input = "w-full rounded-lg border border-slate-300 px-2 py-1.5 text-sm";

function PeriodControls({ resource, resourceId }) {
  const [periods, setPeriods] = useState([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [revision, setRevision] = useState(0);
  const path = `/admin/unavailability/${resource}/${resourceId}`;
  useEffect(() => {
    const request = new AbortController();
    setLoading(true);
    api.get(path, { signal: request.signal }).then(({ data }) => {
      if (!request.signal.aborted) { setPeriods(data); setError(""); }
    }).catch(() => {
      if (!request.signal.aborted) setError("Unavailable periods could not be loaded.");
    }).finally(() => {
      if (!request.signal.aborted) setLoading(false);
    });
    return () => request.abort();
  }, [path, revision]);

  function showError(requestError) {
    const data = requestError.response?.data;
    const ids = Array.isArray(data?.bookingIds) ? data.bookingIds : [];
    setError(ids.length ? `This period overlaps ${ids.map(id => `BK-${String(id).padStart(6, "0")}`).join(", ")}.`
      : data?.message || "Could not save the change. Try again.");
  }

  async function create(event) {
    event.preventDefault();
    if (busy) return;
    const form = event.currentTarget;
    const data = new FormData(form);
    const startAt = data.get("startAt");
    const endAt = data.get("endAt");
    if (!startAt || !endAt || endAt <= startAt) {
      setError("Choose an end time after the start time.");
      return;
    }
    setBusy(true);
    setError("");
    try {
      await api.post(path, { startAt, endAt, reason: data.get("reason").trim() || null });
      form.reset();
      setRevision(value => value + 1);
    } catch (requestError) {
      showError(requestError);
    } finally {
      setBusy(false);
    }
  }

  async function remove(id) {
    if (busy) return;
    setBusy(true);
    setError("");
    try {
      await api.delete(`${path}/${id}`);
      setRevision(value => value + 1);
    } catch (requestError) {
      showError(requestError);
    } finally {
      setBusy(false);
    }
  }

  return <div className="mt-3 space-y-3">
    <p className="text-xs text-slate-600">Times are in Malaysia time. Each period can span up to 31 days.</p>
    <form onSubmit={create} className="space-y-2">
      <div className="grid gap-2 sm:grid-cols-2">
        <label className="text-xs font-medium">Start<input name="startAt" type="datetime-local" className={input} required /></label>
        <label className="text-xs font-medium">End<input name="endAt" type="datetime-local" className={input} required /></label>
      </div>
      <label className="block text-xs font-medium">Reason<input name="reason" className={input} maxLength={500} /></label>
      <button type="submit" disabled={busy} className="rounded-lg bg-teal-700 px-3 py-1.5 text-sm font-semibold text-white disabled:opacity-50">Add period</button>
    </form>
    {error && <p role="alert" className="text-sm text-rose-800">{error}</p>}
    {loading ? <p role="status" className="text-sm text-slate-600">Loading periods…</p>
      : periods.length === 0 ? <p className="text-sm text-slate-600">No unavailable periods.</p>
      : <ul className="divide-y divide-slate-100">
        {periods.map(period => <li key={period.id} className="flex items-start justify-between gap-2 py-2 text-sm">
          <div><p>{period.startAt.replace("T", " ").slice(0, 16)} to {period.endAt.replace("T", " ").slice(0, 16)}</p>
            {period.reason && <p className="text-xs text-slate-600">{period.reason}</p>}</div>
          <button type="button" disabled={busy} onClick={() => remove(period.id)} className="text-xs font-semibold text-rose-800 hover:underline disabled:opacity-50">Remove</button>
        </li>)}
      </ul>}
  </div>;
}
