import { useEffect, useState } from "react";
import { RefreshCw, Save } from "lucide-react";
import api from "../services/api";
import { TripPricingSettings } from "./DriverEarnings";

export default function Settings() {
  const [data, setData] = useState({ categories: [], pricing: [] });
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [category, setCategory] = useState("");
  const [baseFare, setBaseFare] = useState("");
  const [ratePerKm, setRatePerKm] = useState("");
  const [active, setActive] = useState(true);
  const current = data.pricing.find(p => p.vehicleCategory.toLowerCase() === category.toLowerCase());
  const explain = e => e.response?.data?.message || e.message || "Pricing could not be saved.";
  async function load() {
    setLoading(true); setError("");
    try { setData((await api.get("/admin/pricing")).data); }
    catch (e) { setError(explain(e)); }
    finally { setLoading(false); }
  }
  useEffect(() => { load(); }, []);
  function select(value) {
    const pricing = data.pricing.find(p => p.vehicleCategory.toLowerCase() === value.toLowerCase());
    setCategory(value); setBaseFare(pricing ? String(pricing.baseFare) : "");
    setRatePerKm(pricing ? String(pricing.ratePerKm) : ""); setActive(pricing?.active ?? true);
    setError(""); setMessage("");
  }
  async function save(e) {
    e.preventDefault(); setError(""); setMessage("");
    if (!category || !/^\d+(\.\d{1,2})?$/.test(baseFare) || !/^\d+(\.\d{1,4})?$/.test(ratePerKm)) {
      setError("Choose a category and enter nonnegative base fare (2 decimals) and rate/km (4 decimals)."); return;
    }
    if (current?.active && !active && !window.confirm("Deactivate pricing? Customers will be unable to submit new bookings for this category until pricing is active again.")) return;
    setSaving(true);
    try {
      const payload = { vehicleCategory: category, baseFare, ratePerKm, active };
      if (current) await api.put(`/admin/pricing/${encodeURIComponent(category)}`, payload);
      else await api.post("/admin/pricing", payload);
      await load(); setMessage("Pricing saved. Existing accepted booking fares are preserved.");
    } catch (e) { setError(explain(e)); }
    finally { setSaving(false); }
  }
  return <div className="space-y-6">
    <div className="flex items-center justify-between"><div><h1 className="text-2xl font-bold text-slate-900">Settings</h1><p className="text-slate-500">Company pricing in MYR</p></div>
      <button onClick={load} disabled={loading || saving} className="flex items-center gap-2 rounded-xl border bg-white px-4 py-2"><RefreshCw size={16} />Refresh</button></div>
    <TripPricingSettings />
    <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
      <h2 className="mb-2 text-lg font-semibold">Category rates</h2><p className="mb-5 text-sm text-slate-600">New estimates use road distance multiplied by the active per-kilometre rate. Legacy fare fields remain available for reference.</p>
      {error && <p role="alert" className="mb-4 rounded-xl bg-red-50 p-3 text-red-800">{error}</p>}
      {message && <p role="status" className="mb-4 rounded-xl bg-emerald-50 p-3 text-emerald-800">{message}</p>}
      {loading ? <p role="status">Loading pricing…</p> : <>
        {!data.categories.length && <p className="rounded-xl bg-slate-50 p-4">No vehicle categories are recorded in the fleet. Add a vehicle before configuring pricing.</p>}
        <div className="mb-6 overflow-x-auto"><table className="w-full text-left text-sm"><thead className="border-b text-slate-500"><tr>{["Vehicle category", "Base fare (RM)", "Rate (RM/km)", "Additional charge (RM)", "Status", "Last updated", ""].map(h => <th key={h} className="p-3">{h}</th>)}</tr></thead>
          <tbody>{data.categories.map(c => { const p = data.pricing.find(r => r.vehicleCategory.toLowerCase() === c.toLowerCase()); return <tr key={c} className="border-b"><td className="p-3 font-medium">{c}</td><td className="p-3">{p ? Number(p.baseFare).toFixed(2) : "Not configured"}</td><td className="p-3">{p ? Number(p.ratePerKm).toFixed(4) : "—"}</td><td className="p-3">{p ? Number(p.additionalCharge).toFixed(2) : "—"}</td><td className="p-3">{p ? (p.active ? "Active" : "Inactive") : "Missing"}</td><td className="p-3">{p?.updatedAt ? new Date(p.updatedAt).toLocaleString() : "—"}</td><td className="p-3"><button disabled={saving} onClick={() => select(c)} className="font-semibold text-blue-700">{p ? "Edit" : "Configure"}</button></td></tr>; })}</tbody></table></div>
        <form onSubmit={save} className="grid max-w-3xl gap-4 sm:grid-cols-2">
          <label className="text-sm">Vehicle category<select required value={category} onChange={e => select(e.target.value)} disabled={saving} className="mt-1 w-full rounded-xl border p-3"><option value="">Select category</option>{data.categories.map(c => <option key={c}>{c}</option>)}</select></label>
          <label className="text-sm">Base fare (RM)<input required type="number" min="0" max="9999999999.99" step="0.01" value={baseFare} disabled={saving} onChange={e => setBaseFare(e.target.value)} className="mt-1 w-full rounded-xl border p-3" /></label>
          <label className="text-sm">Rate per kilometre (RM/km)<input required type="number" min="0" max="99999999.9999" step="0.0001" value={ratePerKm} disabled={saving} onChange={e => setRatePerKm(e.target.value)} className="mt-1 w-full rounded-xl border p-3" /></label>
          <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={active} disabled={saving} onChange={e => setActive(e.target.checked)} />Pricing active</label>
          <button disabled={saving || !category} className="flex items-center justify-center gap-2 rounded-xl bg-slate-900 px-5 py-3 font-semibold text-white disabled:opacity-50"><Save size={16} />{saving ? "Saving…" : current ? "Update pricing" : "Create pricing"}</button>
        </form>
      </>}
    </section>
  </div>;
}
