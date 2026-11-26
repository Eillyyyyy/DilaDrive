import { useEffect, useState } from "react";
import api from "../services/api";
import FareProposalPanel from "./FareProposalPanel";
import { assignmentDuration, assignmentPreviewError, preferredAssignment } from "../services/bookingAssignments";

// The preview is advisory. Confirming or reassigning repeats the checks under locks.
export default function AssignmentResourcePicker({ bookingId, scheduledEndAt, replacementOf, onChange, onSuggestedEnd, disabled, onReserve }) {
  const [preview, setPreview] = useState(null);
  const [selected, setSelected] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [refresh, setRefresh] = useState(0);
  const [changing, setChanging] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    setSelected(null);
    setChanging(false);
    setPreview(null);
    onChange(null);
    setError("");
    if (!bookingId) {
      setLoading(false);
      return () => controller.abort();
    }
    setLoading(true);
    api.get(`/bookings/${bookingId}/assignment-preview`, {
      params: { scheduledEndAt: scheduledEndAt || undefined, replacementOf }, signal: controller.signal,
    }).then(({ data }) => {
      if (controller.signal.aborted) return;
      setPreview(data);
      if (!scheduledEndAt && data.scheduledEndAt) onSuggestedEnd?.(data.scheduledEndAt.slice(0, 19));
      const preferred = replacementOf || data.reservationStatus === "EXPIRED" ? null : preferredAssignment(data);
      setSelected(preferred);
      onChange(preferred);
    }).catch(e => {
      if (!controller.signal.aborted) setError(assignmentPreviewError(e));
    }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [bookingId, scheduledEndAt, replacementOf, refresh, onChange, onSuggestedEnd]);

  const preferred = replacementOf || preview?.reservationStatus === "EXPIRED" ? null : preferredAssignment(preview);
  const alternatives = preview?.alternatives ?? [];
  return <div className="space-y-3 rounded-xl border border-slate-200 bg-slate-50 p-4">
    <div className="flex flex-wrap items-center justify-between gap-2">
      <p className="text-sm font-semibold">{replacementOf ? "Eligible replacement pairs" : "Suggested driver and vehicle"}</p>
      <button type="button" disabled={disabled || loading} className="text-sm font-semibold text-blue-700"
        onClick={() => { onChange(null); setRefresh(v => v + 1); }}>{error ? "Retry" : "Recheck availability"}</button>
    </div>
    {loading && <p role="status" className="text-sm">Checking assignment availability...</p>}
    {error && <p role="alert" className="text-sm text-red-700">{error}</p>}
    {preview && preview.estimatedTripEnd && <dl className="grid gap-2 text-sm text-slate-700 sm:grid-cols-3">
      <div><dt className="font-medium">Estimated drive</dt><dd>{assignmentDuration(preview.driveDurationSeconds)}</dd></div>
      <div><dt className="font-medium">Operational buffer</dt><dd>{assignmentDuration(preview.operationalBufferSeconds)}</dd></div>
      <div><dt className="font-medium">Earliest end (Malaysia)</dt><dd>{preview.estimatedTripEnd.replace("T", " ")}</dd></div>
    </dl>}
    {preview && !replacementOf && <div className="space-y-1 text-sm">
      {preview.reservationStatus === "ACTIVE" && <p>Reserved until {preview.reservationExpiresAt?.replace("T", " ").slice(0, 16)} (Malaysia)</p>}
      {preview.reservationStatus === "EXPIRED" && <p className="font-semibold text-amber-800">Reservation expired. Recheck and reserve an eligible pair.</p>}
      {preview.reservationStatus === "NONE" && <p>No temporary reservation</p>}
      <p>{preview.preferred
        ? `${preview.preferred.vehicleModel || preview.preferred.vehicleType} (${preview.preferred.vehiclePlate})`
        : preview.preferredCategory || "No vehicle preference saved"}</p>
      {preview.preferred?.driverName && <p>Associated driver: {preview.preferred.driverName}</p>}
      {preferred ? <p role="status" className="font-medium text-emerald-700">Vehicle and driver are eligible for this trip.</p>
        : <p role="status" className="text-amber-800">{preview.unavailableReason ||
          "This vehicle category requires customer acceptance before assignment."}</p>}
      {preview.preferenceChecks?.reasons?.length > 1 &&
        <ul className="list-disc pl-5 text-amber-800">
          {preview.preferenceChecks.reasons.slice(1).map(reason => <li key={reason}>{reason}</li>)}
        </ul>}
    </div>}
    {onReserve && preferred && !changing && <button type="button" disabled={disabled}
      className="text-sm font-semibold text-blue-700" onClick={() => { setChanging(true); onChange(null); setSelected(null); }}>
      Change Assignment
    </button>}
    {preview && (!preferred || changing) && (alternatives.length > 0 ? <label className="block text-sm">
      {replacementOf ? "Replacement vehicle and driver" : "Alternative vehicle and driver"}
      <select required disabled={disabled || loading} value={selected?.vehicleId ?? ""}
        className="mt-1 w-full rounded-xl border border-slate-300 p-3"
        onChange={e => {
          const next = alternatives.find(option => String(option.vehicleId) === e.target.value) || null;
          setSelected(next);
          const mustReserve = onReserve && preview.reservationStatus !== "NONE";
          onChange(mustReserve || next?.customerAcceptanceRequired ? null : next);
        }}>
        <option value="">Select an eligible pair</option>
        {alternatives.map(option => <option key={option.vehicleId} value={option.vehicleId}>
          {option.vehicleModel || option.vehicleType} ({option.vehiclePlate}) / {option.driverName}
          {option.customerAcceptanceRequired ? " (customer acceptance required)" : ""}
        </option>)}
      </select>
    </label> : <p className="text-sm">No eligible pair for this time. Check the schedule or try again later.</p>)}
    {selected && <p className="text-sm text-slate-600">
      {selected.passengerCapacity} passenger seats, boot capacity {selected.luggagePointsCapacity ?? "unknown"} luggage units
    </p>}
    {onReserve && selected && (changing || !preferred) && !selected.customerAcceptanceRequired &&
      preview?.reservationStatus !== "NONE" && <button type="button" disabled={disabled || loading}
        className="rounded-lg border border-blue-300 px-3 py-2 text-sm font-semibold text-blue-700"
        onClick={async () => { if (await onReserve(selected)) setRefresh(value => value + 1); }}>
        Reserve selected pair
      </button>}
    {replacementOf && preview?.emergencyRequired && <label className="flex items-center gap-2 text-sm">
      <input type="checkbox" name="emergency" required /> Emergency reassignment after the normal cutoff
    </label>}
    {selected && !selected.customerAcceptanceRequired && <>
      <input type="hidden" name="vehicleId" value={selected.vehicleId} />
      <input type="hidden" name="driverId" value={selected.driverId} />
    </>}
    {selected?.customerAcceptanceRequired && <FareProposalPanel
      bookingId={bookingId} category={selected.vehicleType} disabled={disabled} />}
  </div>;
}
