import { useEffect, useMemo, useState } from "react";
import axios from "axios";
import api from "../services/api";
import {
  AlertCircle,
  CalendarDays,
  CheckCircle2,
  ClipboardList,
  Clock3,
  CircleX,
  Eye,
  LoaderCircle,
  Plus,
  RefreshCw,
  Search,
  Truck,
  UserRound,
  Users,
  X,
} from "lucide-react";

const TERMINAL_STATUSES = new Set(["COMPLETED", "CANCELLED"]);
const ASSIGNMENT_STATUSES = [
  "ASSIGNED",
  "OTW",
  "WAITING",
  "PICKUP",
  "DROP",
  "COMPLETED",
  "CANCELLED",
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

export default function Assignments() {
  const [assignments, setAssignments] = useState([]);
  const [bookings, setBookings] = useState([]);
  const [drivers, setDrivers] = useState([]);
  const [vehicles, setVehicles] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshKey, setRefreshKey] = useState(0);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");
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
      api.get("/bookings", { signal: controller.signal }),
      api.get("/drivers/available", { signal: controller.signal }),
      api.get("/vehicles/available", { signal: controller.signal }),
    ])
      .then(([assignmentResponse, bookingResponse, driverResponse, vehicleResponse]) => {
        setAssignments(assignmentResponse.data);
        setBookings(bookingResponse.data);
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

  const activeAssignments = assignments.filter(
    (assignment) => !TERMINAL_STATUSES.has(normalizeStatus(assignment.assignmentStatus))
  );
  const eligibleBookings = bookings.filter(
    (booking) =>
      normalizeStatus(booking.bookingStatus) === "CONFIRMED" &&
      !activeAssignments.some(
        (assignment) => String(assignment.bookingId) === String(booking.bookingId)
      )
  );

  const filteredAssignments = useMemo(() => {
    const query = search.trim().toLowerCase();
    return assignments.filter((assignment) => {
      const matchesSearch = [
        assignment.customer,
        assignment.driverName,
        assignment.vehiclePlate,
      ].some((value) => String(value ?? "").toLowerCase().includes(query));
      const matchesStatus =
        statusFilter === "ALL" ||
        normalizeStatus(assignment.assignmentStatus) === statusFilter;
      return matchesSearch && matchesStatus;
    });
  }, [assignments, search, statusFilter]);

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
      setRefreshKey((current) => current + 1);
    } catch (requestError) {
      const message = getApiError(requestError);
      setFormError(message);
      if (!modal) setError(message);
    } finally {
      setBusy(false);
    }
  };

  const createAssignment = (event) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const bookingId = Number(form.get("bookingId"));
    const driverId = Number(form.get("driverId"));
    const vehicleId = Number(form.get("vehicleId"));
    if (!bookingId || !driverId || !vehicleId) {
      setFormError("Select a booking, an available driver, and an available vehicle.");
      return;
    }

    performAction(
      () =>
        api.post("/assignments", {
          bookingId,
          driverId,
          vehicleId,
        }),
      "Assignment created. The selected driver and vehicle are now unavailable."
    );
  };

  const reassign = (event) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const driverValue = String(form.get("driverId") || "");
    const vehicleValue = String(form.get("vehicleId") || "");
    if (!driverValue && !vehicleValue) {
      setFormError("Select a new driver or a new vehicle.");
      return;
    }

    const request = {};
    if (driverValue) request.driverId = Number(driverValue);
    if (vehicleValue) request.vehicleId = Number(vehicleValue);
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

  const cancelAssignment = (assignment) => {
    if (
      !window.confirm(
        `Cancel the assignment for ${assignment.customer || "this customer"}? Its driver and vehicle will be released.`
      )
    ) {
      return;
    }
    performAction(
      () =>
        api.patch(`/assignments/${assignment.assignmentId}/status`, {
          status: "CANCELLED",
        }),
      "Assignment cancelled and resources released."
    );
  };

  return (
    <div className="admin-page">
      <header className="admin-page-header">
        <div>
          <div className="mb-2 flex items-center gap-2 text-sm text-slate-500">
            <span>Management</span>
            <span>/</span>
            <span className="font-medium text-blue-600">Assignments</span>
          </div>
          <h1 className="admin-page-title">
            Assignment Management
          </h1>
          <p className="admin-page-description">
            Assign and manage drivers and vehicles for confirmed bookings.
          </p>
        </div>
        <button
          type="button"
          onClick={() => openModal({ type: "create" })}
          disabled={loading}
          className="inline-flex items-center justify-center gap-2 rounded-xl bg-blue-600 px-4 py-3 text-sm font-semibold text-white shadow-sm hover:bg-blue-700 disabled:opacity-50"
        >
          <Plus size={18} />
          Create Assignment
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

      <section className="grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
        {stats.map(({ label, value, icon: Icon, style }) => (
          <article
            key={label}
            className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm"
          >
            <div className="flex items-center justify-between gap-3">
              <div>
                <p className="text-xs font-medium text-slate-500">{label}</p>
                <p className="mt-2 text-2xl font-bold text-slate-900">
                  {loading ? "…" : value}
                </p>
              </div>
              <div className={`rounded-xl p-3 ${style}`}>
                <Icon size={20} />
              </div>
            </div>
          </article>
        ))}
      </section>

      <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div className="flex flex-col gap-3 border-b border-slate-100 p-4 md:flex-row md:items-center md:justify-between">
          <div>
            <h2 className="font-semibold text-slate-900">Assignment Records</h2>
            <p className="mt-1 text-xs text-slate-500">
              Review trip assignments, resource details and current status.
            </p>
          </div>
          <div className="flex flex-col gap-2 sm:flex-row">
            <label className="relative block">
              <Search
                size={17}
                className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
              />
              <input
                type="search"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Search customer, driver, vehicle..."
                className="w-full rounded-xl border border-slate-200 py-2.5 pl-9 pr-3 text-sm outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100 sm:w-72"
              />
            </label>
            <select
              value={statusFilter}
              onChange={(event) => setStatusFilter(event.target.value)}
              aria-label="Filter by assignment status"
              className="rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm text-slate-700 outline-none focus:border-blue-400 focus:ring-2 focus:ring-blue-100"
            >
              <option value="ALL">All statuses</option>
              {ASSIGNMENT_STATUSES.map((status) => (
                <option key={status} value={status}>
                  {status.replaceAll("_", " ")}
                </option>
              ))}
            </select>
            <button
              type="button"
              title="Refresh assignment data"
              onClick={() => setRefreshKey((current) => current + 1)}
              className="inline-flex items-center justify-center rounded-xl border border-slate-200 px-3 py-2.5 text-slate-600 hover:bg-slate-50"
            >
              <RefreshCw size={17} />
            </button>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full min-w-[900px] text-left">
            <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
              <tr>
                <th className="px-5 py-3 font-semibold">No.</th>
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
                filteredAssignments.map((assignment, index) => {
                  const status = normalizeStatus(assignment.assignmentStatus);
                  const isActive = !TERMINAL_STATUSES.has(status);
                  return (
                    <tr
                      key={assignment.assignmentId}
                      className="hover:bg-slate-50/70"
                    >
                      <td className="px-5 py-4 text-sm font-medium text-slate-500">
                        {index + 1}
                      </td>
                      <td className="px-5 py-4 text-sm font-medium text-slate-800">
                        {assignment.customer || "—"}
                      </td>
                      <td className="px-5 py-4 text-sm text-slate-700">
                        {assignment.driverName || "—"}
                      </td>
                      <td className="px-5 py-4 text-sm font-medium text-slate-700">
                        {assignment.vehiclePlate || "—"}
                      </td>
                      <td className="whitespace-nowrap px-5 py-4 text-sm text-slate-500">
                        {formatDateTime(getAssignmentDate(assignment))}
                      </td>
                      <td className="px-5 py-4">
                        <span
                          className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ring-inset ${statusClass(
                            status
                          )}`}
                        >
                          {status.replaceAll("_", " ")}
                        </span>
                      </td>
                      <td className="px-5 py-4">
                        <div className="flex justify-end gap-1">
                          <IconButton
                            label="View assignment details"
                            onClick={() => openModal({ type: "view", assignment })}
                            icon={Eye}
                          />
                          {isActive && (
                            <>
                              <button
                                type="button"
                                onClick={() =>
                                  openModal({ type: "reassign", assignment })
                                }
                                title="Reassign driver or vehicle"
                                aria-label="Reassign assignment"
                                className="rounded-lg p-2 text-blue-600 hover:bg-blue-50"
                              >
                                <RefreshCw size={16} />
                              </button>
                              <button
                                type="button"
                                onClick={() => completeAssignment(assignment)}
                                title="Complete assignment"
                                aria-label="Complete assignment"
                                className="rounded-lg p-2 text-emerald-600 hover:bg-emerald-50"
                              >
                                <CheckCircle2 size={16} />
                              </button>
                              <button
                                type="button"
                                onClick={() => cancelAssignment(assignment)}
                                title="Cancel assignment and release resources"
                                aria-label="Cancel assignment"
                                className="rounded-lg p-2 text-rose-600 hover:bg-rose-50"
                              >
                                <CircleX size={16} />
                              </button>
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

      {modal?.type === "create" && (
        <AssignmentFormModal
          title="Create Assignment"
          subtitle="Choose a confirmed booking and currently available resources."
          bookings={eligibleBookings}
          drivers={drivers}
          vehicles={vehicles}
          busy={busy}
          error={formError}
          onClose={closeModal}
          onSubmit={createAssignment}
        />
      )}
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
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-slate-950/50 p-4"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <section
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className="my-auto max-h-[92vh] w-full max-w-xl overflow-y-auto rounded-2xl bg-white shadow-2xl"
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

function FormActions({ onClose, busy, submitLabel }) {
  return (
    <div className="flex justify-end gap-2 border-t border-slate-100 pt-4">
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
        disabled={busy}
        className="rounded-xl bg-blue-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-50"
      >
        {busy && <LoaderCircle size={15} className="mr-2 inline animate-spin" />}
        {submitLabel}
      </button>
    </div>
  );
}

function AssignmentFormModal({
  title,
  subtitle,
  bookings,
  drivers,
  vehicles,
  busy,
  error,
  onClose,
  onSubmit,
}) {
  return (
    <Modal title={title} subtitle={subtitle} onClose={onClose}>
      <form onSubmit={onSubmit} className="space-y-4 p-5">
        <FormField label="Confirmed booking">
          <select name="bookingId" required className={inputClass}>
            <option value="">Select a booking</option>
            {bookings.map((booking) => (
              <option key={booking.bookingId} value={booking.bookingId}>
                {booking.customer || "Customer"} · {formatDate(booking.bookingTripdate)}
                {booking.bookingPickuptime ? ` · ${booking.bookingPickuptime}` : ""}
                {booking.bookingPickupaddress
                  ? ` · ${booking.bookingPickupaddress}`
                  : ""}
              </option>
            ))}
          </select>
          {bookings.length === 0 && (
            <span className="mt-1 block text-xs text-amber-700">
              There are no confirmed bookings without an active assignment.
            </span>
          )}
        </FormField>
        <FormField label="Available driver">
          <select name="driverId" required className={inputClass}>
            <option value="">Select an available driver</option>
            {drivers.map((driver) => (
              <option key={driver.driverId} value={driver.driverId}>
                {driver.driverName}
              </option>
            ))}
          </select>
          {drivers.length === 0 && (
            <span className="mt-1 block text-xs text-amber-700">
              No drivers currently have AVAILABLE status.
            </span>
          )}
        </FormField>
        <FormField label="Available vehicle">
          <select name="vehicleId" required className={inputClass}>
            <option value="">Select an available vehicle</option>
            {vehicles.map((vehicle) => (
              <option key={vehicle.vehicleId} value={vehicle.vehicleId}>
                {vehicle.vehiclePlateno}
                {vehicle.vehicleType ? ` · ${vehicle.vehicleType}` : ""}
              </option>
            ))}
          </select>
          {vehicles.length === 0 && (
            <span className="mt-1 block text-xs text-amber-700">
              No vehicles currently have AVAILABLE status.
            </span>
          )}
        </FormField>
        <p className="rounded-lg bg-blue-50 px-3 py-2 text-xs leading-5 text-blue-800">
          Availability is checked again by the server before saving. A
          notification is created for the selected driver in the same transaction.
        </p>
        <FormError message={error} />
        <FormActions
          onClose={onClose}
          busy={busy}
          submitLabel={busy ? "Creating..." : "Create Assignment"}
        />
      </form>
    </Modal>
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
  return (
    <Modal
      title="Reassign Trip"
      subtitle={`Booking for ${assignment.customer || "customer"}`}
      onClose={onClose}
    >
      <form onSubmit={onSubmit} className="space-y-4 p-5">
        <p className="rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-600">
          Leave a field unchanged to keep its current resource. At least one new
          available resource is required.
        </p>
        <FormField label={`Driver · current: ${assignment.driverName}`}>
          <select name="driverId" defaultValue="" className={inputClass}>
            <option value="">Keep current driver</option>
            {drivers.map((driver) => (
              <option key={driver.driverId} value={driver.driverId}>
                {driver.driverName}
              </option>
            ))}
          </select>
        </FormField>
        <FormField label={`Vehicle · current: ${assignment.vehiclePlate}`}>
          <select name="vehicleId" defaultValue="" className={inputClass}>
            <option value="">Keep current vehicle</option>
            {vehicles.map((vehicle) => (
              <option key={vehicle.vehicleId} value={vehicle.vehicleId}>
                {vehicle.vehiclePlateno}
                {vehicle.vehicleType ? ` · ${vehicle.vehicleType}` : ""}
              </option>
            ))}
          </select>
        </FormField>
        <FormError message={error} />
        <FormActions
          onClose={onClose}
          busy={busy}
          submitLabel={busy ? "Saving..." : "Save Reassignment"}
        />
      </form>
    </Modal>
  );
}

function AssignmentDetailsModal({ assignment, onClose }) {
  const status = normalizeStatus(assignment.assignmentStatus);
  const details = [
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
