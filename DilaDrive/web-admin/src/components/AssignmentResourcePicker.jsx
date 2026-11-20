import { useEffect, useState } from "react";
import api from "../services/api";
import FareProposalPanel from "./FareProposalPanel";

// Both admin pages preview the same server rules; saving rechecks under locks.
export default function AssignmentResourcePicker({ bookingId, scheduledEndAt, excludeDriverId, onChange, disabled }) {
  const [options, setOptions] = useState([]);
  const [vehicleId, setVehicleId] = useState("");
  const [driverId, setDriverId] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [refresh, setRefresh] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setVehicleId(""); setDriverId(""); setOptions([]); onChange(null); setError(""); setLoading(false);
    if (!bookingId || !scheduledEndAt) return () => controller.abort();
    setLoading(true);
    api.get(`/bookings/${bookingId}/assignment-options`, {
      params: { scheduledEndAt }, signal: controller.signal,
    }).then(({ data }) => {
      if (!controller.signal.aborted) setOptions(data.filter(o => String(o.driverId) !== String(excludeDriverId)));
    }).catch(e => {
      if (!controller.signal.aborted) setError(e.response?.data?.message || e.message);
    }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [bookingId, scheduledEndAt, excludeDriverId, refresh, onChange]);
  const selected = options.find(o => String(o.vehicleId) === vehicleId);
  return <div className="space-y-3 rounded-xl border border-slate-200 bg-slate-50 p-4">
    <div className="flex items-center justify-between gap-2">
      <p className="text-sm font-semibold">Suitable vehicles and eligible drivers</p>
      <button type="button" disabled={disabled || loading} className="text-sm font-semibold text-blue-700"
        onClick={() => setRefresh(v => v + 1)}>Recheck availability</button>
    </div>
    {loading && <p role="status" className="text-sm">Checking the selected time...</p>}
    {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
    {!loading && !error && options.length === 0 && <p className="text-sm">No eligible vehicle and driver pair for this time. Change the schedule or requirements, or keep the request pending.</p>}
    <label className="block text-sm">Available vehicle
      <select name="vehicleId" required disabled={disabled || loading} value={vehicleId}
        className="mt-1 w-full rounded-xl border border-slate-300 p-3"
        onChange={e => { setVehicleId(e.target.value); setDriverId(""); onChange(null); }}>
        <option value="">Select a suitable vehicle</option>
        {options.map(o => <option key={o.vehicleId} value={o.vehicleId}>{o.vehiclePlate} - {o.vehicleModel || o.vehicleType} ({o.passengerCapacity} seats, {o.luggagePointsCapacity ?? "unknown"} luggage points)</option>)}
      </select>
    </label>
    <label className="block text-sm">Eligible driver
      <select name="driverId" required disabled={disabled || loading || !selected} value={driverId}
        className="mt-1 w-full rounded-xl border border-slate-300 p-3"
        onChange={e => { setDriverId(e.target.value); onChange(e.target.value ? selected : null); }}>
        <option value="">Select a driver</option>
        {selected && <option value={selected.driverId}>{selected.driverName}</option>}
      </select>
    </label>
    <p className="text-xs text-slate-600">Option A is preserved: the eligible driver operates their registered vehicle. This selection does not reserve resources. Confirmation checks availability again.</p>
    {bookingId && <FareProposalPanel key={bookingId} bookingId={bookingId} category={selected?.vehicleType} disabled={disabled} />}
  </div>;
}
