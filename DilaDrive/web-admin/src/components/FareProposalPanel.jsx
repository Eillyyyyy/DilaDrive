import { useEffect, useState } from "react";
import api from "../services/api";

export default function FareProposalPanel({ bookingId, category, disabled }) {
  const [history, setHistory] = useState([]);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  async function load() {
    try { setHistory((await api.get(`/bookings/${bookingId}/fare-proposals`)).data); setError(""); }
    catch (e) { setError(e.response?.data?.message || e.message); }
  }
  useEffect(() => { load(); }, [bookingId]);
  async function propose() {
    if (!window.confirm(`Propose the configured ${category} fare? The customer must accept before this category can be assigned. The accepted booking fare remains unchanged until acceptance.`)) return;
    setBusy(true); setError(""); setMessage("");
    try {
      const { data } = await api.post(`/bookings/${bookingId}/fare-proposals`, { vehicleCategory: category });
      setMessage(`MYR ${Number(data.proposedFare).toFixed(2)} proposed for ${data.vehicleCategory}. Awaiting customer acceptance.`);
      await load();
    } catch (e) { setError(e.response?.data?.message || e.message); }
    finally { setBusy(false); }
  }
  return <div className="space-y-2 border-t pt-3 text-sm">
    <p className="font-semibold">Accepted fare and category changes</p>
    <p className="text-slate-600">The same category keeps the accepted fare. For a different category, send its configured fare to the customer for review before confirming assignment.</p>
    {category && <button type="button" disabled={disabled || busy} onClick={propose} className="rounded-lg border border-blue-300 bg-white px-3 py-2 text-blue-800">{busy ? "Sending..." : `Propose ${category} fare`}</button>}
    <button type="button" onClick={load} disabled={busy} className="ml-2 text-blue-700">Refresh acceptance</button>
    {message && <p role="status" className="text-emerald-700">{message}</p>}
    {error && <p role="alert" className="text-red-700">{error}</p>}
    {history.map(h => <p key={h.id} className="text-slate-600">{h.vehicle_type}: MYR {Number(h.original_fare).toFixed(2)} to MYR {Number(h.proposed_fare).toFixed(2)} / {h.status}</p>)}
  </div>;
}
