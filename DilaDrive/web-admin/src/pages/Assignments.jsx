import useModalScrollLock from "../components/useModalScrollLock";
import useRecordFocus from "../services/useRecordFocus";
import AssignmentTimeline from "../components/AssignmentTimeline";
import { useEffect, useMemo, useState } from "react";
import axios from "axios";
import api from "../services/api";
import AssignmentResourcePicker from "../components/AssignmentResourcePicker";
import {
  AlertCircle,
  CalendarDays,
  CheckCircle2,
  ClipboardList,
  Clock3,
  Eye,
  LoaderCircle,
  RefreshCw,
  Search,
  Truck,
  UserRound,
  Users,
  X,
} from "lucide-react";

const TERMINAL_STATUSES = new Set(["COMPLETED", "CANCELLED", "SUPERSEDED"]);
const ASSIGNMENT_STATUSES = [
  "ASSIGNED",
  "OTW",
  "WAITING",
  "PICKUP",
  "DROP",
  "COMPLETED",
  "CANCELLED",
  "SUPERSEDED",
];

const normalizeStatus = (status) => String(status || "").trim().toUpperCase();

const getApiError = (error) => {
  const body = error.response?.data;
  return (
    (typeof body === "string" ? body : body?.message || body?.detail) ||
    error.message ||
    "The request could not be completed."
  );
};

const formatDate = (value) => {
  if (!value) return "—";
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return "—";
  return parsed.toLocaleDateString("en-MY", {
    day: "2-digit",
    month: "short",
    year: "numeric",
  });
};

const formatDateTime = (value) => {
  if (!value) return "—";
  const [datePart, timePart = "00:00:00"] = String(value).split("T");
  const [year, month, day] = datePart.split("-").map(Number);
  const [hour, minute, second = 0] = timePart.split(":").map(Number);
  if (![year, month, day, hour, minute, second].every(Number.isFinite)) {
    return "—";
  }

  // LocalDateTime from the API has no timezone; preserve its database wall-clock time.
  const parsed = new Date(Date.UTC(year, month - 1, day, hour, minute, second));
  const date = parsed.toLocaleDateString("en-GB", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    timeZone: "UTC",
  });
  const time = parsed.toLocaleTimeString("en-US", {
    hour: "2-digit",
    minute: "2-digit",
    hour12: true,
    timeZone: "UTC",
  });
  return `${date}, ${time}`;
};

const getAssignmentDate = (assignment) =>
  assignment.assignmentDate ?? assignment.assignedAt ?? assignment.assignment_date;

const statusClass = (status) => {
  switch (normalizeStatus(status)) {
    case "ASSIGNED":
      return "bg-blue-50 text-blue-700 ring-blue-200";
    case "OTW":
    case "WAITING":
      return "bg-amber-50 text-amber-700 ring-amber-200";
    case "PICKUP":
    case "DROP":
      return "bg-violet-50 text-violet-700 ring-violet-200";
    case "COMPLETED":
      return "bg-emerald-50 text-emerald-700 ring-emerald-200";
    case "CANCELLED":
      return "bg-slate-100 text-slate-600 ring-slate-200";
    default:
      return "bg-slate-100 text-slate-600 ring-slate-200";
  }
};

export default function Assignments({ onNavigate, recordFocus, onRecordOpened }) {
  const [assignments, setAssignments] = useState([]);
  const [drivers, setDrivers] = useState([]);
  const [vehicles, setVehicles] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshKey, setRefreshKey] = useState(0);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [dateFilter, setDateFilter] = useState("");
  const [driverFilter, setDriverFilter] = useState("");
  const [vehicleFilter, setVehicleFilter] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [modal, setModal] = useState(null);
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState("");

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError("");

    Promise.all([
      api.get("/assignments", { signal: controller.signal }),
      api.get("/drivers/available", { signal: controller.signal }),
      api.get("/vehicles/available", { signal: controller.signal }),
    ])
      .then(([assignmentResponse, driverResponse, vehicleResponse]) => {
        setAssignments(assignmentResponse.data);
        setDrivers(driverResponse.data);
        setVehicles(vehicleResponse.data);
      })
      .catch((requestError) => {
        if (!axios.isCancel(requestError)) setError(getApiError(requestError));
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [refreshKey]);

  useEffect(() => {
    const refresh = () => setRefreshKey(key => key + 1);
    window.addEventListener("focus", refresh);
    window.addEventListener("diladrive:assignments-changed", refresh);
    return () => {
      window.removeEventListener("focus", refresh);
      window.removeEventListener("diladrive:assignments-changed", refresh);
    };
  }, []);

  const activeAssignments = assignments.filter(
    (assignment) => !TERMINAL_STATUSES.has(normalizeStatus(assignment.assignmentStatus))
  );
  const filteredAssignments = useMemo(() => {
    const query = search.trim().toLowerCase();
    return assignments.filter((assignment) => {
      const matchesSearch = [
        assignment.bookingId,
        assignment.customer,
        assignment.pickupAddress,
        assignment.destinationAddress,
        assignment.driverName,
        assignment.vehiclePlate,
      ].some((value) => String(value ?? "").toLowerCase().includes(query));
      const matchesStatus =
        statusFilter === "ALL" ||
        normalizeStatus(assignment.assignmentStatus) === statusFilter;
      return matchesSearch && matchesStatus && (!dateFilter || assignment.tripDate === dateFilter)
        && (!driverFilter || String(assignment.driverId) === driverFilter)
        && (!vehicleFilter || String(assignment.vehicleId) === vehicleFilter);
    });
  }, [assignments, search, statusFilter, dateFilter, driverFilter, vehicleFilter]);

  const stats = [
    {
      label: "Total Assignments",
      value: assignments.length,
      icon: ClipboardList,
      style: "bg-blue-50 text-blue-700",
    },
    {
      label: "Active Assignments",
      value: activeAssignments.length,
      icon: Clock3,
      style: "bg-amber-50 text-amber-700",
    },
    {
      label: "Completed",
      value: assignments.filter(
        (item) => normalizeStatus(item.assignmentStatus) === "COMPLETED"
      ).length,
      icon: CheckCircle2,
      style: "bg-emerald-50 text-emerald-700",
    },
    {
      label: "Available Drivers",
      value: drivers.length,
      icon: Users,
      style: "bg-violet-50 text-violet-700",
    },
    {
      label: "Available Vehicles",
      value: vehicles.length,
      icon: Truck,
      style: "bg-orange-50 text-orange-700",
    },
  ];

  const openModal = (nextModal) => {
    setFormError("");
    setModal(nextModal);
  };

  useRecordFocus({ id: recordFocus, items: assignments, idKey: "assignmentId", loading,
    error, open: assignment => openModal({ type: "view", assignment }), complete: onRecordOpened });

  const closeModal = () => {
    if (busy) return;
    setModal(null);
    setFormError("");
  };

  const performAction = async (operation, message) => {
    setBusy(true);
    setFormError("");
    setError("");
    try {
      await operation();
      setModal(null);
      setNotice(message);
      window.dispatchEvent(new Event("diladrive:assignments-changed"));
    } catch (requestError) {
      const message = getApiError(requestError);
      setFormError(message);
      if (!modal) setError(message);
    } finally {
      setBusy(false);
    }
  };

  const reassign = (event) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const driverValue = String(form.get("driverId") || "");
    const scheduledEndAt = String(form.get("scheduledEndAt") || "");
    if (!driverValue || !scheduledEndAt) {
      setFormError("Select a new eligible driver and a scheduled end time.");
      return;
    }
    const reason = String(form.get("reason") || "").trim();
    if (!reason) {
      setFormError("Enter the reason for reassignment.");
      return;
    }
    const request = {driverId: Number(driverValue), vehicleId: Number(form.get("vehicleId")), scheduledEndAt, reason, emergency: form.has("emergency")};
    const assignment = modal.assignment;

    performAction(
      () =>
        api.put(`/assignments/${assignment.assignmentId}/reassign`, request),
      "Assignment updated. The previous resources have been released."
    );
  };

  const completeAssignment = (assignment) => {
    performAction(
      () => api.put(`/assignments/${assignment.assignmentId}/complete`),
      "Assignment completed and resources released."
    );
  };

  return (
    <div className="admin-page !space-y-3">
      <header className="admin-page-header !gap-3 !pb-3">
        <div>
          <div className="mb-1 flex items-center gap-2 text-sm text-slate-500">
            <span>Management</span>
            <span>/</span>
            <span className="font-medium text-blue-600">Assignments</span>
          </div>
          <h1 className="admin-page-title">
            Assignment Management
          </h1>
          <p className="admin-page-description">
            View trips, update trip status and reassign eligible drivers.
          </p>
        </div>
        <button
          type="button"
          onClick={() => onNavigate?.("Bookings")}
          disabled={loading}
          className="inline-flex items-center justify-center gap-2 rounded-lg bg-blue-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-blue-700 disabled:opacity-50"
        >
          <ClipboardList size={18} />
          View Bookings
        </button>
      </header>

      {notice && (
        <div
          role="status"
          className="flex items-center justify-between rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-800"
        >
          <span className="flex items-center gap-2">
            <CheckCircle2 size={17} />
            {notice}
          </span>
          <button
            type="button"
            aria-label="Dismiss message"
            onClick={() => setNotice("")}
            className="rounded p-1 hover:bg-emerald-100"
          >
            <X size={16} />
          </button>
        </div>
      )}

      {error && <ErrorBanner message={error} />}

      <section className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
        {stats.map(({ label, value, icon: Icon, style }) => (
          <article
            key={label}
            className="rounded-xl border border-slate-200 bg-white p-3 shadow-sm"
          >
            <div className="flex items-center justify-between gap-3">
              <div>
                <p className="text-xs font-medium text-slate-500">{label}</p>
                <p className="mt-1 text-2xl font-bold text-slate-900">
                  {loading ? "…" : value}
                </p>
              </div>
              <div className={`rounded-lg p-2 ${style}`}>
                <Icon size={20} />
              </div>
            </div>
          </article>
        ))}
      </section>

      <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div className="border-b border-slate-100 p-3 md:p-4">
          <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
            <h2 className="font-semibold text-slate-900">Assignment Records</h2>
            <p className="text-xs text-slate-500">
              Review trip assignments, resource details and current status.
            </p>
          </div>
          <div className="mt-3 grid grid-cols-1 gap-2 md:grid-cols-3 lg:grid-cols-[minmax(110px,180px)_minmax(120px,220px)_minmax(120px,220px)_minmax(100px,160px)_minmax(170px,280px)_auto] xl:grid-cols-[180px_220px_220px_160px_280px_auto]">
            <input
              type="date"
              aria-label="Filter by pickup date"
              value={dateFilter}
              onChange={(event) => setDateFilter(event.target.value)}
              className="h-11 min-w-0 w-full rounded-lg border border-slate-200 bg-white px-3 text-sm text-slate-700 outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100"
            />
            <select
              aria-label="Filter by driver"
              value={driverFilter}
              onChange={(event) => setDriverFilter(event.target.value)}
              className="h-11 min-w-0 w-full rounded-lg border border-slate-200 bg-white px-3 text-sm text-slate-700 outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100"
            >
              <option value="">All drivers</option>
              {[...new Map(assignments.map((a) => [a.driverId, a.driverName])).entries()].map(([id, name]) => (
                <option key={id} value={id}>{name || id}</option>
              ))}
            </select>
            <select
              aria-label="Filter by vehicle"
              value={vehicleFilter}
              onChange={(event) => setVehicleFilter(event.target.value)}
              className="h-11 min-w-0 w-full rounded-lg border border-slate-200 bg-white px-3 text-sm text-slate-700 outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100"
            >
              <option value="">All vehicles</option>
              {[...new Map(assignments.map((a) => [a.vehicleId, a.vehiclePlate])).entries()].map(([id, plate]) => (
                <option key={id} value={id}>{plate || id}</option>
              ))}
            </select>
            <select
              value={statusFilter}
              onChange={(event) => setStatusFilter(event.target.value)}
              aria-label="Filter by assignment status"
              className="h-11 min-w-0 w-full rounded-lg border border-slate-200 bg-white px-3 text-sm text-slate-700 outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100"
            >
              <option value="ALL">All statuses</option>
              {ASSIGNMENT_STATUSES.map((status) => (
                <option key={status} value={status}>
                  {status.replaceAll("_", " ")}
                </option>
              ))}
            </select>
            <label className="relative block min-w-0">
              <Search
                size={17}
                className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
              />
              <input
                type="search"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Search customer, driver, vehicle..."
                className="h-11 w-full rounded-lg border border-slate-200 pl-9 pr-3 text-sm outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100"
              />
            </label>
            <button
              type="button"
              title="Refresh assignment data"
              aria-label="Refresh assignment data"
              onClick={() => setRefreshKey((current) => current + 1)}
              className="inline-flex h-11 w-full items-center justify-center rounded-lg border border-slate-200 px-3 text-slate-600 hover:bg-slate-50 lg:w-auto"
            >
              <RefreshCw size={17} />
            </button>
          </div>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full min-w-[900px] text-left">
            <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-5 py-3 font-semibold">Booking</th>
                <th className="px-5 py-3 font-semibold">Customer</th>
                <th className="px-5 py-3 font-semibold">Driver</th>
                <th className="px-5 py-3 font-semibold">Vehicle</th>
                <th className="px-5 py-3 font-semibold">Assigned On</th>
                <th className="px-5 py-3 font-semibold">Status</th>
                <th className="px-5 py-3 text-right font-semibold">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {loading ? (
                <tr>
                  <td colSpan={7} className="px-5 py-14 text-center text-sm text-slate-500">
                    <LoaderCircle size={22} className="mx-auto animate-spin text-blue-600" />
                    <span className="mt-2 block">Loading assignments...</span>
                  </td>
                </tr>
              ) : filteredAssignments.length === 0 ? (
                <tr>
                  <td colSpan={7} className="px-5 py-14 text-center">
                    <ClipboardList size={28} className="mx-auto text-slate-300" />
                    <p className="mt-3 font-semibold text-slate-800">
                      No assignments found
                    </p>
                    <p className="mt-1 text-sm text-slate-500">
                      Create an assignment or adjust the search and status filter.
                    </p>
                  </td>
                </tr>
              ) : (
                filteredAssignments.map((assignment) => {
                  const status = normalizeStatus(assignment.assignmentStatus);
                  const isActive = !TERMINAL_STATUSES.has(status);
                  return (
                    <tr
                      key={assignment.assignmentId}
                      className="hover:bg-slate-50/70"
                    >
                      <td className="px-5 py-3 text-sm font-medium text-slate-500">
                        {assignment.bookingId && `BK-${String(assignment.bookingId).padStart(6, "0")}`}
                      </td>
                      <td className="px-5 py-3 text-sm font-medium text-slate-800">
                        {assignment.customer || "—"}
                      </td>
                      <td className="px-5 py-3 text-sm text-slate-700">
                        {assignment.driverName || "—"}
                      </td>
                      <td className="px-5 py-3 text-sm font-medium text-slate-700">
                        <div>{[assignment.vehicleModel, assignment.vehicleCategory].filter(Boolean).join(" / ")}</div>
                        {assignment.vehiclePlate || "—"}
                      </td>
                      <td className="whitespace-nowrap px-5 py-3 text-sm text-slate-500">
                        {formatDateTime(assignment.tripDate && assignment.pickupTime
                          ? `${assignment.tripDate}T${assignment.pickupTime}` : null)}
                        <div className="max-w-56 whitespace-normal text-xs">{assignment.pickupAddress} / {assignment.destinationAddress}</div>
                      </td>
                      <td className="px-5 py-3">
                        <span
                          className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ring-inset ${statusClass(
                            status
                          )}`}
                        >
                          {status.replaceAll("_", " ")}
                        </span>
                        {assignment.bookingStatus && <div className="mt-1 text-xs text-slate-500">{assignment.bookingStatus}</div>}
                      </td>
                      <td className="px-5 py-3">
                        <div className="flex justify-end gap-1">
                          <IconButton
                            label="View assignment details"
                            onClick={() => openModal({ type: "view", assignment })}
                            icon={Eye}
                          />
                          {isActive && (
                            <>
                              {status === "ASSIGNED" && <button
                                type="button"
                                onClick={() =>
                                  openModal({ type: "reassign", assignment })
                                }
                                title="Reassign driver or vehicle"
                                aria-label="Reassign assignment"
                                className="rounded-lg p-2 text-blue-600 hover:bg-blue-50"
                              >
                                <RefreshCw size={16} />
                              </button>}
                              {status === "DROP" && <button
                                type="button"
                                onClick={() => completeAssignment(assignment)}
                                title="Complete assignment"
                                aria-label="Complete assignment"
                                className="rounded-lg p-2 text-emerald-600 hover:bg-emerald-50"
                              >
                                <CheckCircle2 size={16} />
                              </button>}
                            </>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
        <footer className="border-t border-slate-100 px-5 py-3 text-xs text-slate-500">
          {loading
            ? " "
            : `${filteredAssignments.length} of ${assignments.length} assignments`}
        </footer>
      </section>

      {modal?.type === "reassign" && (
        <ReassignModal
          assignment={modal.assignment}
          drivers={drivers}
          vehicles={vehicles}
          busy={busy}
          error={formError}
          onClose={closeModal}
          onSubmit={reassign}
        />
      )}
      {modal?.type === "view" && (
        <AssignmentDetailsModal
          assignment={modal.assignment}
          onClose={closeModal}
        />
      )}
    </div>
  );
}

function ErrorBanner({ message }) {
  return (
    <div
      role="alert"
      className="flex items-start gap-2 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800"
    >
      <AlertCircle size={17} className="mt-0.5 shrink-0" />
      <span>{message}</span>
    </div>
  );
}

function IconButton({ label, onClick, icon: Icon }) {
  return (
    <button
      type="button"
      title={label}
      aria-label={label}
      onClick={onClick}
      className="rounded-lg p-2 text-slate-500 hover:bg-slate-100 hover:text-slate-800"
    >
      <Icon size={16} />
    </button>
  );
}

function Modal({ title, subtitle, onClose, children }) {
  useModalScrollLock();

  return (
    <div
      className="fixed inset-0 !m-0 z-50 flex items-center justify-center overflow-hidden bg-slate-950/50 p-4"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <section
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className="modal-panel w-full max-w-xl overflow-y-auto overscroll-contain rounded-2xl bg-white shadow-2xl"
      >
        <header className="sticky top-0 flex items-start justify-between border-b border-slate-100 bg-white p-5">
          <div>
            <h2 className="text-lg font-bold text-slate-900">{title}</h2>
            {subtitle && (
              <p className="mt-1 text-sm text-slate-500">{subtitle}</p>
            )}
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close modal"
            className="rounded-lg p-2 text-slate-400 hover:bg-slate-100"
          >
            <X size={18} />
          </button>
        </header>
        {children}
      </section>
    </div>
  );
}

function FormField({ label, children }) {
  return (
    <label className="block text-sm font-medium text-slate-700">
      {label}
      {children}
    </label>
  );
}

const inputClass =
  "mt-1 block w-full rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm text-slate-800 outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100";

function FormError({ message }) {
  if (!message) return null;
  return (
    <div
      role="alert"
      className="flex items-start gap-2 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700"
    >
      <AlertCircle size={16} className="mt-0.5 shrink-0" />
      <span>{message}</span>
    </div>
  );
}

function FormActions({ onClose, busy, submitLabel, disabled = false }) {
  return (
    <div className="sticky bottom-0 z-10 flex justify-end gap-2 border-t border-slate-100 bg-white py-3">
      <button
        type="button"
        onClick={onClose}
        disabled={busy}
        className="rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-semibold text-slate-600 hover:bg-slate-50 disabled:opacity-50"
      >
        Cancel
      </button>
      <button
        type="submit"
        disabled={busy || disabled}
        className="rounded-xl bg-blue-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
      >
        {busy && <LoaderCircle size={15} className="mr-2 inline animate-spin" />}
        {submitLabel}
      </button>
    </div>
  );
}

function ReassignModal({
  assignment,
  drivers,
  vehicles,
  busy,
  error,
  onClose,
  onSubmit,
}) {
  const [selection, setSelection] = useState(null);
  const [scheduledEndAt, setScheduledEndAt] = useState(assignment.scheduledEndAt?.slice(0,19) || "");
  return (
    <Modal
      title="Reassign Trip"
      subtitle={`Booking for ${assignment.customer || "customer"}`}
      onClose={onClose}
    >
      <form onSubmit={onSubmit} className="space-y-4 p-5">
        <p className="rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-600">
          Select another suitable vehicle and its eligible driver. Previous assignment history will be preserved.
        </p>
        <FormField label="Scheduled end time">
          <input name="scheduledEndAt" type="datetime-local" step="1" required className={inputClass} value={scheduledEndAt} onChange={e => { setSelection(null); setScheduledEndAt(e.target.value); }} />
          <span className="mt-1 block text-xs text-slate-500">Must be after pickup. Bookings may share this pair only when their time ranges do not overlap.</span>
        </FormField>
        <AssignmentResourcePicker bookingId={assignment.bookingId} scheduledEndAt={scheduledEndAt}
          replacementOf={assignment.assignmentId} onChange={setSelection} onSuggestedEnd={setScheduledEndAt} disabled={busy} />
        <FormField label="Reason for reassignment">
          <textarea name="reason" required maxLength={500} rows={3} className={inputClass} />
        </FormField>
        <FormError message={error} />
        <FormActions
          onClose={onClose}
          busy={busy}
          disabled={!selection}
          submitLabel={busy ? "Saving..." : "Save Reassignment"}
        />
      </form>
    </Modal>
  );
}

function AssignmentDetailsModal({ assignment, onClose }) {
  const status = normalizeStatus(assignment.assignmentStatus);
  const details = [
    { label: "Booking Reference", value: assignment.bookingId == null ? "Not recorded" : `BK-${String(assignment.bookingId).padStart(6, "0")}`, icon: ClipboardList },
    { label: "Reassignment reason", value: assignment.reassignmentReason || "Not applicable", icon: ClipboardList },
    { label: "Previous assignment", value: assignment.previousAssignmentId ?? "Not applicable", icon: ClipboardList },
    { label: "Boot usage (luggage units)", value: assignment.luggageUnits ?? "Not recorded", icon: Users },
    { label: "Small / medium / large bags", value: assignment.smallCount == null ? "Not recorded" : `${assignment.smallCount} / ${assignment.mediumCount ?? 0} / ${assignment.largeCount ?? 0}`, icon: ClipboardList },
    {
      label: "Customer Name",
      value: assignment.customer || "—",
      icon: UserRound,
    },
    {
      label: "Driver Name",
      value: assignment.driverName || "—",
      icon: UserRound,
    },
    {
      label: "Vehicle Plate Number",
      value: assignment.vehiclePlate || "—",
      icon: Truck,
    },
    {
      label: "Trip Date",
      value: formatDate(assignment.tripDate),
      icon: CalendarDays,
    },
    {
      label: "Pickup Location",
      value: assignment.pickupAddress || "—",
      icon: ClipboardList,
      wide: true,
    },
    {
      label: "Destination",
      value: assignment.destinationAddress || "—",
      icon: ClipboardList,
      wide: true,
    },
    {
      label: "Assignment Date",
      value: formatDateTime(getAssignmentDate(assignment)),
      icon: Clock3,
    },
    {
      label: "Assignment Status",
      value: status.replaceAll("_", " "),
      icon: CheckCircle2,
      status: true,
    },
  ];

  return (
    <Modal
      title="Assignment Details"
      subtitle="Trip, assigned resources and current status."
      onClose={onClose}
    >
      <div className="grid gap-3 p-5 sm:grid-cols-2">
        {details.map(({ label, value, icon: Icon, wide, status: isStatus }) => {
          return (
            <div
              key={label}
              className={`rounded-xl border border-slate-100 bg-slate-50 p-3 ${
                wide ? "sm:col-span-2" : ""
              }`}
            >
              <p className="flex items-center gap-1.5 text-xs font-medium text-slate-500">
                <Icon size={13} />
                {label}
              </p>
              {isStatus ? (
                <span
                  className={`mt-2 inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ring-inset ${statusClass(status)}`}
                >
                  {value}
                </span>
              ) : (
                <p className="mt-1.5 break-words text-sm font-semibold text-slate-800">
                  {value}
                </p>
              )}
            </div>
          );
        })}
      </div>
      <AssignmentTimeline key={assignment.assignmentId} assignmentId={assignment.assignmentId} />
      <AssignmentPhotos key={assignment.assignmentId} assignmentId={assignment.assignmentId} />
      <div className="flex justify-end border-t border-slate-100 p-5 pt-4">
        <button
          type="button"
          onClick={onClose}
          className="rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-semibold text-slate-600 hover:bg-slate-50"
        >
          Close
        </button>
      </div>
    </Modal>
  );
}

function AssignmentPhotos({ assignmentId }) {
  const [photos, setPhotos] = useState(null);
  const [listError, setListError] = useState("");
  const [selectedPhoto, setSelectedPhoto] = useState(null);
  const [preview, setPreview] = useState(null);
  const [previewError, setPreviewError] = useState("");
  const path = `/assignments/${assignmentId}/photos`;

  useEffect(() => {
    const request = new AbortController();
    api.get(path, { signal: request.signal })
      .then(({ data }) => { if (!request.signal.aborted) setPhotos(data); })
      .catch((error) => { if (!request.signal.aborted) setListError(getApiError(error)); });
    return () => request.abort();
  }, [path]);

  useEffect(() => {
    if (!selectedPhoto) return;
    const request = new AbortController();
    let objectUrl;
    api.get(`${path}/${selectedPhoto.photoId}/content`, {
      responseType: "blob",
      signal: request.signal,
    }).then(({ data }) => {
      if (request.signal.aborted) return;
      objectUrl = URL.createObjectURL(data);
      setPreview(objectUrl);
    }).catch((error) => {
      if (!request.signal.aborted) setPreviewError(getApiError(error));
    });
    return () => {
      request.abort();
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [path, selectedPhoto]);

  const showPhoto = (photo) => {
    if (photo === selectedPhoto) return;
    setPreview(null);
    setPreviewError("");
    setSelectedPhoto(photo);
  };

  return (
    <section className="space-y-3 border-t border-slate-100 p-5" aria-label="Verification photos">
      <h3 className="text-sm font-semibold text-slate-900">Verification photos</h3>
      {listError && <p role="alert" className="text-sm text-rose-700">{listError}</p>}
      {!listError && !photos && <p role="status" className="text-sm text-slate-500">Loading photos…</p>}
      {photos?.length === 0 && <p className="text-sm text-slate-500">No verification photos uploaded.</p>}
      {photos?.map((photo) => (
        <button key={photo.photoId} type="button" onClick={() => showPhoto(photo)}
          aria-pressed={selectedPhoto === photo}
          className="mr-2 rounded-lg border px-3 py-2 text-sm font-medium text-teal-700 aria-pressed:bg-teal-50">
          {photo.photoType.replaceAll("_", " ")} · {formatDateTime(photo.uploadedAt)}
        </button>
      ))}
      {previewError && <p role="alert" className="text-sm text-rose-700">{previewError}</p>}
      {selectedPhoto && !preview && !previewError && <p role="status" className="text-sm text-slate-500">Loading preview…</p>}
      {preview && <img src={preview} alt={`${selectedPhoto.photoType} verification`} className="max-h-72 max-w-full rounded-lg object-contain" />}
    </section>
  );
}
