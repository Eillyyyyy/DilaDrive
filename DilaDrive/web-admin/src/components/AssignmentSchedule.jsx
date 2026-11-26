import { useEffect, useState } from "react";
import api from "../services/api";

const malaysiaCalendar = new Intl.DateTimeFormat("en", {
  timeZone: "Asia/Kuala_Lumpur", year: "numeric", month: "2-digit", day: "2-digit",
});
function malaysiaDate() {
  const parts = Object.fromEntries(malaysiaCalendar.formatToParts(new Date()).map(({ type, value }) => [type, value]));
  return [parts.year, parts.month, parts.day].join("-");
}

function localTime(value) {
  return value?.replace("T", " ").slice(0, 16);
}

export default function AssignmentSchedule({ driverId, vehicleId }) {
  const [date, setDate] = useState(malaysiaDate);
  const [result, setResult] = useState(null);

  useEffect(() => {
    if (!date || (!driverId && !vehicleId)) return undefined;
    const controller = new AbortController();
    api.get("/assignments/schedule", {
      signal: controller.signal, params: { date, driverId, vehicleId },
    }).then(({ data }) => {
      if (!controller.signal.aborted) setResult({ date, entries: data });
    }).catch(() => {
      if (!controller.signal.aborted) setResult({ date, error: "Could not load the schedule." });
    });
    return () => controller.abort();
  }, [date, driverId, vehicleId]);

  if (!driverId && !vehicleId) return null;
  const current = result?.date === date ? result : null;
  return <section className="space-y-2 rounded-xl border border-slate-200 p-4">
    <label className="flex flex-wrap items-center justify-between gap-2 text-sm font-semibold">
      Schedule (Malaysia)
      <input type="date" value={date} onChange={event => { setResult(null); setDate(event.target.value); }}
        className="rounded-lg border border-slate-300 px-2 py-1" />
    </label>
    {date && !current && <p role="status" className="text-sm">Loading schedule…</p>}
    {current?.error && <p role="alert" className="text-sm text-red-700">{current.error}</p>}
    {current?.entries?.length === 0 && <p className="text-sm text-slate-600">No trips, holds, or unavailable periods on this day.</p>}
    {current?.entries?.length > 0 && <ul className="divide-y text-sm">
      {current.entries.map((entry, index) => <li key={index} className="flex flex-wrap justify-between gap-2 py-2">
        <span>{localTime(entry.start)} to {localTime(entry.end) || "end pending"}</span>
        <span>{entry.bookingId && `BK-${String(entry.bookingId).padStart(6, "0")} · `}{entry.status}</span>
      </li>)}
    </ul>}
  </section>;
}
