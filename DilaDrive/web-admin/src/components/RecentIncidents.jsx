import { useEffect, useState } from "react";
import api from "../services/api";

export default function RecentIncidents() {
  const [result, setResult] = useState(null);
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    let newest = 0;
    const fetchIncidents = () => {
      const attempt = ++newest;
      api.get("/assignments/incidents", { signal: controller.signal })
        .then(({data}) => { if (!controller.signal.aborted && attempt === newest) setResult({ rows: data }); })
        .catch(() => { if (!controller.signal.aborted && attempt === newest) setResult({ error: true }); });
    };
    fetchIncidents();
    const interval = window.setInterval(fetchIncidents, 30000);
    return () => { controller.abort(); window.clearInterval(interval); };
  }, [revision]);

  return <section className="space-y-3 rounded-xl border bg-white p-4">
    <div className="flex justify-between gap-2">
      <h2 className="font-semibold">Recent trip incidents</h2>
      <button type="button" className="text-sm text-blue-700" onClick={() => setRevision(r => r+1)}>Refresh</button>
    </div>
    {!result && <p role="status">Loading incidents…</p>}
    {result?.error && <p role="alert">Could not load incidents.</p>}
    {result?.rows?.length === 0 && <p>No incidents reported.</p>}
    <ul className="space-y-3">
      {result?.rows?.slice(0,5).map(row => <li key={row.incidentId} className="border-t pt-2 text-sm">
        <p className="font-semibold">BK-{String(row.bookingId).padStart(6, "0")} · {row.driverName}</p>
        <p className="whitespace-pre-wrap break-words">{row.description}</p>
        <p className="text-slate-600">{new Date(row.reportedAt).toLocaleString("en-MY", { timeZone: "Asia/Kuala_Lumpur", timeZoneName: "short" })}</p>
      </li>)}
    </ul>
  </section>;
}