import { useEffect, useState } from "react";
import api from "../services/api";

const money = value => value == null ? "—" : Number(value).toLocaleString("en-MY", {
  style: "currency", currency: "MYR",
});
const errorText = error => error.response?.data?.message || error.message || "Request failed.";
const inputStyle = "mt-1 block w-full rounded-xl border border-slate-200 px-3 py-2";
const panelStyle = "space-y-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm";
const actionStyle = "rounded-xl bg-teal-700 px-4 py-2 font-semibold text-white disabled:opacity-50";

function Notice({ error, success }) {
  return <>
    {error && <p role="alert" className="text-sm text-rose-700">{error}</p>}
    {success && <p role="status" className="text-sm text-emerald-700">{success}</p>}
  </>;
}

export function TripPricingSettings() {
  const path = "/admin/trip-pricing-settings";
  const [form, setForm] = useState(null);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    const request = new AbortController();
    api.get(path, { signal: request.signal }).then(({ data }) => setForm(data))
      .catch(e => { if (!request.signal.aborted) setError(errorText(e)); });
    return () => request.abort();
  }, []);
  const change = (field, value) => setForm(current => ({ ...current, [field]: value }));
  async function save(event) {
    event.preventDefault();
    setBusy(true); setError(""); setMessage("");
    try {
      const { data } = await api.put(path, form);
      setForm(data);
      setMessage("Pricing policy saved. Existing booking snapshots were preserved.");
    } catch (e) { setError(errorText(e)); }
    finally { setBusy(false); }
  }
  return <section className={panelStyle}>
    <h2 className="text-lg font-semibold">Trip pricing policy</h2>
    <p className="text-sm text-slate-600">Hourly charges apply only to hours approved for a booking. The partial-hour policy determines how approved hours are charged.</p>
    <Notice error={error} success={message} />
    {!form && !error && <p role="status">Loading pricing policy…</p>}
    {form && <form onSubmit={save}>
      <fieldset disabled={busy} className="grid gap-4 sm:grid-cols-2">
        {[["ratePerKm", "Default rate per km (RM)", "0.0001"], ["hourlyRate", "Approved extra hours rate (RM)", "0.01"]].map(([field, label, step]) =>
          <label key={field}>{label}<input className={inputStyle} type="number" min="0" step={step}
            required value={form[field]} onChange={e => change(field, e.target.value)} /></label>)}
        <label>Partial-hour policy<select className={inputStyle} value={form.partialHourPolicy}
          onChange={e => change("partialHourPolicy", e.target.value)}>
          <option value="PRORATE">Prorate</option><option value="ROUND_UP">Round up to the next hour</option>
        </select></label>
        <div className="flex flex-col gap-2">
          <label><input type="checkbox" checked={form.active} onChange={e => change("active", e.target.checked)} /> Pricing active</label>
          <label><input type="checkbox" checked={form.useCategoryRates} onChange={e => change("useCategoryRates", e.target.checked)} /> Use configured category rates</label>
        </div>
        <button className={actionStyle}>{busy ? "Saving…" : "Save pricing policy"}</button>
      </fieldset>
    </form>}
  </section>;
}

export function TripCharges({ bookingId, allowDistance = false, canEditCharges = false, onDistanceConfirmed }) {
  const path = `/admin/bookings/${bookingId}/charges`;
  const [data, setData] = useState(null);
  const [form, setForm] = useState({ extraHours: "0", toll: "0", parking: "0", reason: "" });
  const [distance, setDistance] = useState("");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [preview, setPreview] = useState(null);
  const current = data?.history[0];
  function display(result) {
    setData(result);
    const last = result.history[0];
    setForm({ extraHours: last?.extra_hours ?? "0", toll: last?.toll ?? "0",
      parking: last?.parking ?? "0", reason: "" });
  }
  useEffect(() => {
    const request = new AbortController();
    api.get(path, { signal: request.signal }).then(({ data: result }) => {
      if (!request.signal.aborted) display(result);
    }).catch(e => { if (!request.signal.aborted) setError(errorText(e)); });
    return () => request.abort();
  }, [path]);
  async function save(e) {
    e.preventDefault();
    const previewOnly = e.nativeEvent.submitter?.value === "preview";
    if (!previewOnly && !window.confirm("Approve these actual charges for this booking?")) return;
    setBusy(true); setError(""); setMessage("");
    try {
      if (previewOnly) {
        setPreview((await api.post(`${path}/preview`, form)).data);
        return;
      }
      display((await api.put(path, form)).data);
      setPreview(null);
      setMessage("Charge revision saved.");
    } catch (err) { setError(errorText(err)); }
    finally { setBusy(false); }
  }
  async function saveDistance(e) {
    e.preventDefault();
    if (!window.confirm("Confirm this measured road distance?")) return;
    setBusy(true); setError(""); setMessage("");
    try {
      const { data: result } = await api.put(`/admin/bookings/${bookingId}/road-distance`, { distanceKm: distance });
      setData(previous => ({ ...previous, distanceKm: result.distanceKm,
        baseFare: result.estimatedFare, finalFare: result.estimatedFare }));
      setMessage(`Road distance confirmed at ${result.distanceKm} km.`);
      await onDistanceConfirmed?.();
    } catch (err) { setError(errorText(err)); }
    finally { setBusy(false); }
  }
  return <section className={panelStyle}>
    <h3 className="font-semibold">Trip fare and actual charges</h3>
    <Notice error={error} success={message} />
    {!data && !error && <p role="status">Loading fare details…</p>}
    {data && <>
      <dl className="grid gap-2 text-sm sm:grid-cols-2">
        <div><dt>Base fare{data.distanceKm > 0 ? ` · ${data.distanceKm} km` : ""}</dt><dd>{money(data.baseFare)}</dd></div>
        <div><dt>Approved hourly charges</dt><dd>{money(current?.hourly_amount ?? 0)}</dd></div>
        <div><dt>Toll / parking</dt><dd>{money(current?.toll ?? 0)} / {money(current?.parking ?? 0)}</dd></div>
        <div><dt className="font-semibold">{current ? "Current final fare" : "Estimated fare"}</dt><dd>{money(data.finalFare)}</dd></div>
      </dl>
      {data.issuedInvoiceAmount != null && <p className="text-sm text-slate-600">
        Issued invoice remains {money(data.issuedInvoiceAmount)}. Charge revisions are recorded separately.
        {current?.invoice_adjustment != null && <> Recorded adjustment: {money(current.invoice_adjustment)}.</>}
      </p>}
      {allowDistance && !(data.distanceKm > 0) && !(data.baseFare > 0) && <form onSubmit={saveDistance} className="flex flex-wrap items-end gap-3">
        <label>Measured road distance (km)<input required type="number" min="0.001" max="999999.999" step="0.001" className={inputStyle}
          value={distance} onChange={e => setDistance(e.target.value)} disabled={busy} /></label>
        <button className={actionStyle} disabled={busy || Boolean(message)}>Confirm road distance</button>
      </form>}
      {canEditCharges && <form onSubmit={save}>
        <fieldset disabled={busy} className="grid gap-3 sm:grid-cols-2">
          {[["extraHours", "Approved extra hours"], ["toll", "Actual toll (RM)"], ["parking", "Actual parking (RM)"]].map(([field, label]) =>
            <label key={field}>{label}<input required type="number" min="0" step="0.01" className={inputStyle}
              value={form[field]} onChange={e => { setPreview(null); setForm({ ...form, [field]: e.target.value }); }} /></label>)}
          <label>Reason<input required maxLength={500} className={inputStyle} value={form.reason}
            onChange={e => setForm({ ...form, reason: e.target.value })} /></label>
          <p className="text-sm text-slate-600">Hourly rate {money(data.hourlyRate)} · {data.partialHourPolicy === "ROUND_UP" ? "Round up" : "Prorate"} partial hours.</p>
          {preview && <p role="status" className="text-sm font-semibold">Preview: {money(preview.finalFare)} · Extra hours: {money(preview.hourlyAmount)}</p>}
          <div className="flex flex-wrap gap-2 sm:col-span-2">
            <button type="submit" value="preview" className="rounded-xl border px-4 py-2">Preview fare</button>
            <button type="submit" value="save" className={actionStyle}>{busy ? "Working…" : "Approve actual charges"}</button>
          </div>
        </fieldset>
      </form>}
      {data.history.length > 0 && <details><summary className="cursor-pointer font-medium">Charge history</summary>
        <ul className="mt-2 space-y-2 text-sm">{data.history.map(row => <li key={row.id}>
          {new Date(row.updated_at).toLocaleString("en-MY")} · Admin #{row.updated_by} · {money(row.final_amount)} · {row.reason}
        </li>)}</ul></details>}
    </>}
  </section>;
}
