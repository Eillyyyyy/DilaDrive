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
    <p className="text-sm text-slate-600">Hourly charges apply only to hours approved for a booking. Commission and partial-hour settings are provisional pending owner confirmation.</p>
    <Notice error={error} success={message} />
    {!form && !error && <p role="status">Loading pricing policy…</p>}
    {form && <form onSubmit={save}>
      <fieldset disabled={busy} className="grid gap-4 sm:grid-cols-2">
        {[["ratePerKm", "Default rate per km (RM)", "0.0001"], ["hourlyRate", "Approved extra hours rate (RM)", "0.01"],
          ["commissionPercent", "Driver commission (%)", "0.01", 100]].map(([field, label, step, max]) =>
          <label key={field}>{label}<input className={inputStyle} type="number" min="0" max={max} step={step}
            required value={form[field]} onChange={e => change(field, e.target.value)} /></label>)}
        <label>Commission basis<select className={inputStyle} value={form.commissionBasis}
          onChange={e => change("commissionBasis", e.target.value)}>
          <option value="BASE_AND_HOURS">Base fare and approved hours</option>
          <option value="BASE_ONLY">Base fare only</option><option value="FINAL_FARE">Final fare including toll and parking</option>
        </select></label>
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

export default function DriverEarnings() {
  const path = "/admin/driver-earnings";
  const [filters, setFilters] = useState({ month: new Intl.DateTimeFormat("sv-SE", {
    timeZone: "Asia/Kuala_Lumpur", year: "numeric", month: "2-digit",
  }).format(), driverId: "", status: "" });
  const [drivers, setDrivers] = useState([]);
  const [result, setResult] = useState(null);
  const [history, setHistory] = useState(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  async function load(parameters = filters) {
    setBusy(true); setError(""); setResult(null); setHistory(null);
    try {
      setResult((await api.get(path, { params: Object.fromEntries(Object.entries(parameters).filter(([, value]) => value)) })).data);
    } catch (e) { setError(errorText(e)); }
    finally { setBusy(false); }
  }
  useEffect(() => {
    api.get("/drivers").then(({ data }) => setDrivers(data))
      .catch(e => setError(errorText(e)));
    load();
  }, []);
  async function payment(trip) {
    const next = trip.payment_status === "PAID" ? "UNPAID" : "PAID";
    const reason = window.prompt(next === "PAID" ? "Confirm payment was made and enter a reference." : "Reason for reopening this commission.");
    if (!reason?.trim()) return;
    setBusy(true); setError(""); setMessage("");
    try {
      await api.patch(`${path}/${trip.assignment_id}/payment-status`, { status: next, reason: reason.trim() });
      await load();
      setMessage("Commission payment status updated.");
    } catch (e) { setError(errorText(e)); }
    finally { setBusy(false); }
  }
  async function showHistory(trip) {
    setError("");
    try { setHistory((await api.get(`${path}/${trip.assignment_id}/history`)).data); }
    catch (e) { setError(errorText(e)); }
  }
  return <div className="space-y-5">
    <h1 className="text-2xl font-bold">Driver earnings</h1>
    <section className={panelStyle}>
      <form onSubmit={e => { e.preventDefault(); setMessage(""); load(); }} className="flex flex-wrap items-end gap-3">
        <label>Month<input required type="month" className={inputStyle} value={filters.month}
          onChange={e => setFilters({ ...filters, month: e.target.value })} /></label>
        <label>Driver<select className={inputStyle} value={filters.driverId} onChange={e => setFilters({ ...filters, driverId: e.target.value })}>
          <option value="">All drivers</option>{drivers.map(driver =>
            <option key={driver.driverId} value={driver.driverId}>{driver.driverName}</option>)}
        </select></label>
        <label>Payment status<select className={inputStyle} value={filters.status} onChange={e => setFilters({ ...filters, status: e.target.value })}>
          <option value="">All statuses</option><option value="UNPAID">Unpaid</option><option value="PAID">Paid</option>
        </select></label>
        <button className={actionStyle} disabled={busy}>Apply filters</button>
      </form>
      <Notice error={error} success={message} />
      {busy && <p role="status">Loading earnings…</p>}
      {result && <><h2 className="font-semibold">Monthly totals · {result.month}</h2>
        {result.drivers.length === 0 && <p>No recorded commissions match these filters.</p>}
        <ul className="grid gap-3 sm:grid-cols-2">{result.drivers.map(driver => <li key={driver.driver_id} className="rounded-xl border p-4 text-sm">
          <h3 className="font-semibold">{driver.driver_name} · {driver.payment_status}</h3>
          <p>{driver.completed_trips} completed trips · {money(driver.commissionable_fare)} commissionable</p>
          <p>Earned {money(driver.earned)} · Paid {money(driver.paid)} · Outstanding {money(driver.outstanding)}</p>
        </li>)}</ul>
        {result.trips.length > 0 && <h2 className="font-semibold">Trip commissions</h2>}
        <ul className="space-y-2">{result.trips.map(trip => <li key={trip.assignment_id} className="flex flex-wrap items-center gap-3 rounded-xl border p-3 text-sm">
          <span className="mr-auto">{trip.driver_name} · Booking #{trip.booking_id} · {money(trip.commission_amount)} ({trip.commission_rate}%) · {trip.payment_status}</span>
          <button disabled={busy} onClick={() => payment(trip)} className="font-semibold text-teal-700">{trip.payment_status === "PAID" ? "Reopen" : "Mark paid"}</button>
          <button disabled={busy} onClick={() => showHistory(trip)} className="font-semibold text-teal-700">History</button>
        </li>)}</ul></>}
      {history && <div><h3 className="font-semibold">Commission history</h3>
        {history.length === 0 && <p>No payment or amount changes recorded.</p>}
        <ul>{history.map(row => <li key={row.id}>{row.action} · {money(row.previous_amount)} → {money(row.new_amount)} · Admin #{row.admin_id} · {new Date(row.changed_at).toLocaleString("en-MY")} · {row.reason}</li>)}</ul>
      </div>}
    </section>
  </div>;
}
