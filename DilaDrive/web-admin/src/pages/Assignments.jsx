
import { useEffect, useMemo, useState } from "react";
import axios from "axios";
import {
  Plus,
  Search,
  Eye,
  RefreshCw,
  Bell,
  X,
  CalendarDays,
  Users,
  Truck,
  ClipboardCheck,
  Clock,
  MapPin,
  CheckCircle2,
  AlertCircle,
} from "lucide-react";

const API_URL = "http://localhost:8080/api";

const tripStatuses = [
  "Assigned",
  "OTW",
  "Waiting",
  "Pickup",
  "Drop",
  "Completed",
];

const nextTripStatus = {
  Assigned: "OTW",
  OTW: "Waiting",
  Waiting: "Pickup",
  Pickup: "Drop",
  Drop: "Completed",
  Completed: null,
};

const terminalStatuses = ["Completed", "Cancelled"];

const emptyForm = {
  bookingId: "",
  driverId: "",
  vehicleId: "",
};

const getApiError = (error, fallback) => {
  const response = error.response?.data;
  return (typeof response === "string" ? response : response?.message || response?.detail)
    || error.message
    || fallback;
};

const normalizeStatus = (status) => {
  const normalized = String(status || "").trim().toUpperCase();
  return normalized === "IN_PROGRESS" ? "OTW" : normalized;
};

const mapBooking = (booking) => ({
  id: booking.bookingId,
  customer: `Customer #${booking.custId}`,
  pickup: booking.bookingPickupaddress || "",
  destination: booking.bookingDestinationaddress || "",
  date: booking.bookingTripdate || "",
  time: String(booking.bookingPickuptime || "").slice(0, 5),
  passengers: booking.bookingPassengercount || 0,
  bookingType: booking.bookingType || "",
  status: ({
    CONFIRMED: "Confirmed",
    CANCELLED: "Cancelled",
    COMPLETED: "Completed",
    ASSIGNED: "Assigned",
  }[String(booking.bookingStatus || "").toUpperCase()] || "Pending Verification"),
});

const mapDriver = (driver) => ({
  id: driver.driverId,
  name: driver.driverName || "Unnamed driver",
  availability: normalizeStatus(driver.driverAvailability),
  accountStatus: normalizeStatus(driver.driverStatus),
});

const mapVehicle = (vehicle) => ({
  id: vehicle.vehicleId,
  plate: vehicle.vehiclePlateno || `Vehicle #${vehicle.vehicleId}`,
  type: vehicle.vehicleType || "",
  capacity: vehicle.vehicleCapacity || 0,
  status: normalizeStatus(vehicle.vehicleStatus),
});

const mapAssignment = (assignment) => ({
  id: assignment.assignmentId,
  bookingId: assignment.bookingId,
  customer: assignment.customer || `Booking #${assignment.bookingId}`,
  driverId: assignment.driverId,
  vehicleId: assignment.vehicleId,
  date: assignment.tripDate || "",
  time: String(assignment.pickupTime || "").slice(0, 5),
  durationHours: 2,
  tripStatus: ({
    ASSIGNED: "Assigned",
    OTW: "OTW",
    WAITING: "Waiting",
    PICKUP: "Pickup",
    DROP: "Drop",
    COMPLETED: "Completed",
    CANCELLED: "Cancelled",
  }[normalizeStatus(assignment.assignmentStatus)] || "Assigned"),
  notificationSent: assignment.notificationSent,
  pickup: assignment.pickupAddress || "",
  destination: assignment.destinationAddress || "",
});

function formatDate(date) {
  if (!date) return "-";

  return new Date(`${date}T00:00:00`).toLocaleDateString("en-MY", {
    day: "2-digit",
    month: "short",
    year: "numeric",
  });
}

export default function Assignments() {
  const [bookings, setBookings] = useState([]);
  const [drivers, setDrivers] = useState([]);
  const [vehicles, setVehicles] = useState([]);
  const [availableDriverIds, setAvailableDriverIds] = useState(new Set());
  const [availableVehicleIds, setAvailableVehicleIds] = useState(new Set());
  const [assignments, setAssignments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("All");

  const [selectedAssignment, setSelectedAssignment] = useState(null);
  const [showAssignModal, setShowAssignModal] = useState(false);
  const [showReassignModal, setShowReassignModal] = useState(false);
  const [selectedRecord, setSelectedRecord] = useState(null);

  const [assignmentForm, setAssignmentForm] = useState(emptyForm);
  const [formError, setFormError] = useState("");
  const [successMessage, setSuccessMessage] = useState("");

  useEffect(() => {
    loadData().catch((error) => {
      console.error("Unable to load assignment data:", error);
      setFormError(getApiError(error, "Unable to load assignment data."));
    });
  }, []);

  const loadData = async () => {
    setLoading(true);
    try {
      const [
        bookingResponse,
        driverResponse,
        availableDriverResponse,
        vehicleResponse,
        availableVehicleResponse,
        assignmentResponse,
      ] = await Promise.all([
        axios.get(`${API_URL}/bookings`),
        axios.get(`${API_URL}/drivers`),
        axios.get(`${API_URL}/drivers/available`),
        axios.get(`${API_URL}/vehicles`),
        axios.get(`${API_URL}/vehicles/available`),
        axios.get(`${API_URL}/assignments`),
      ]);
      setBookings(bookingResponse.data.map(mapBooking));
      setDrivers(driverResponse.data.map(mapDriver));
      setAvailableDriverIds(
        new Set(availableDriverResponse.data.map((driver) => String(driver.driverId)))
      );
      setVehicles(vehicleResponse.data.map(mapVehicle));
      setAvailableVehicleIds(
        new Set(availableVehicleResponse.data.map((vehicle) => String(vehicle.vehicleId)))
      );
      setAssignments(assignmentResponse.data.map(mapAssignment));
    } finally {
      setLoading(false);
    }
  };

  const getDriver = (driverId) =>
    drivers.find((driver) => String(driver.id) === String(driverId));

  const getVehicle = (vehicleId) =>
    vehicles.find((vehicle) => String(vehicle.id) === String(vehicleId));

  const getBooking = (bookingId) =>
    bookings.find((booking) => String(booking.id) === String(bookingId));

  const activeAssignments = assignments.filter(
    (item) => !terminalStatuses.includes(item.tripStatus)
  );

  // A booking can only have one active assignment.
  const eligibleBookings = bookings.filter(
    (booking) =>
      booking.status === "Confirmed" &&
      !activeAssignments.some(
        (assignment) => assignment.bookingId === booking.id
      )
  );

  const selectedBooking = getBooking(assignmentForm.bookingId);

  const availableDrivers = drivers.filter((driver) =>
    availableDriverIds.has(String(driver.id)) ||
    (selectedRecord && String(selectedRecord.driverId) === String(driver.id))
  );
  const currentBooking = getBooking(selectedRecord?.bookingId);
  const resourceBooking = selectedBooking || currentBooking;
  const availableVehicles = vehicles.filter((vehicle) => {
    const available =
      availableVehicleIds.has(String(vehicle.id)) ||
      (selectedRecord && String(selectedRecord.vehicleId) === String(vehicle.id));
    const matchesType =
      !resourceBooking?.bookingType ||
      !vehicle.type ||
      vehicle.type.toLowerCase() === resourceBooking.bookingType.toLowerCase();
    const fitsCapacity =
      !resourceBooking?.passengers ||
      vehicle.capacity >= resourceBooking.passengers;
    return available && matchesType && fitsCapacity;
  });

  const filteredAssignments = useMemo(() => {
    const query = search.toLowerCase().trim();

    return assignments.filter((item) => {
      const driverName = drivers.find(
        (driver) => String(driver.id) === String(item.driverId)
      )?.name || "";

      const vehiclePlate = vehicles.find(
        (vehicle) => String(vehicle.id) === String(item.vehicleId)
      )?.plate || "";

      const matchesSearch = [
        item.id,
        item.bookingId,
        item.customer,
        driverName,
        vehiclePlate,
      ].some((value) => String(value || "").toLowerCase().includes(query));

      const matchesStatus =
        statusFilter === "All" || item.tripStatus === statusFilter;

      return matchesSearch && matchesStatus;
    });
  }, [assignments, search, statusFilter, drivers, vehicles]);

  const stats = [
    {
      label: "Total Assignments",
      value: assignments.length,
      icon: ClipboardCheck,
      color: "bg-blue-50 text-blue-700",
    },
    {
      label: "Assigned",
      value: assignments.filter((a) => a.tripStatus === "Assigned").length,
      icon: Users,
      color: "bg-indigo-50 text-indigo-700",
    },
    {
      label: "Trips In Progress",
      value: assignments.filter(
        (a) => !["Assigned", "Completed", "Cancelled"].includes(a.tripStatus)
      ).length,
      icon: Clock,
      color: "bg-amber-50 text-amber-700",
    },
    {
      label: "Completed",
      value: assignments.filter((a) => a.tripStatus === "Completed").length,
      icon: CheckCircle2,
      color: "bg-emerald-50 text-emerald-700",
    },
  ];

  const resetForm = () => {
    setAssignmentForm(emptyForm);
    setFormError("");
    setSelectedRecord(null);
  };

  const openAssignModal = () => {
    resetForm();
    setShowAssignModal(true);
  };

  const closeAssignModal = () => {
    setShowAssignModal(false);
    resetForm();
  };

  const handleAssign = async () => {
    setFormError("");
    setSuccessMessage("");

    const booking = getBooking(assignmentForm.bookingId);

    if (!booking) {
      setFormError("Please select a booking.");
      return;
    }

    if (booking.status !== "Confirmed") {
      setFormError("Only confirmed bookings can be assigned.");
      return;
    }

    if (!assignmentForm.driverId || !assignmentForm.vehicleId) {
      setFormError("Please select both a driver and a vehicle.");
      return;
    }

    if (!availableDriverIds.has(String(assignmentForm.driverId))) {
      setFormError("Only available drivers can be assigned.");
      return;
    }

    if (!availableVehicleIds.has(String(assignmentForm.vehicleId))) {
      setFormError("Only available vehicles can be assigned.");
      return;
    }

    try {
      const response = await axios.post(`${API_URL}/assignments`, {
        bookingId: Number(booking.id),
        driverId: Number(assignmentForm.driverId),
        vehicleId: Number(assignmentForm.vehicleId),
      });
      setAssignments((previous) => [
        mapAssignment(response.data),
        ...previous,
      ]);
      closeAssignModal();
      await loadData();
      setSuccessMessage(
        "Driver and vehicle assigned successfully. The driver was notified."
      );
    } catch (error) {
      console.error("Unable to create assignment:", error);
      setFormError(getApiError(error, "Unable to create assignment."));
    }
  };

  const openReassignModal = (assignment) => {
    if (terminalStatuses.includes(assignment.tripStatus)) {
      setSuccessMessage("");
      setFormError("Completed trips cannot be reassigned.");
      return;
    }

    setSelectedRecord(assignment);
    setAssignmentForm({
      bookingId: assignment.bookingId,
      driverId: assignment.driverId,
      vehicleId: assignment.vehicleId,
    });
    setFormError("");
    setShowReassignModal(true);
  };

  const handleReassign = async () => {
    setFormError("");
    setSuccessMessage("");

    if (!selectedRecord) {
      setFormError("Please select an assignment.");
      return;
    }

    const booking = getBooking(selectedRecord.bookingId);

    if (!booking) {
      setFormError("The original booking could not be found.");
      return;
    }

    if (!assignmentForm.driverId || !assignmentForm.vehicleId) {
      setFormError("Please select both a driver and a vehicle.");
      return;
    }

    const driverChanged =
      String(assignmentForm.driverId) !== String(selectedRecord.driverId);
    const vehicleChanged =
      String(assignmentForm.vehicleId) !== String(selectedRecord.vehicleId);
    if (!driverChanged && !vehicleChanged) {
      setFormError("Choose a different driver or vehicle to reassign.");
      return;
    }

    if (
      driverChanged &&
      !availableDriverIds.has(String(assignmentForm.driverId))
    ) {
      setFormError("Only available drivers can be selected.");
      return;
    }
    if (
      vehicleChanged &&
      !availableVehicleIds.has(String(assignmentForm.vehicleId))
    ) {
      setFormError("Only available vehicles can be selected.");
      return;
    }

    try {
      const response = await axios.put(
        `${API_URL}/assignments/${selectedRecord.id}/reassign`,
        {
          driverId: Number(assignmentForm.driverId),
          vehicleId: Number(assignmentForm.vehicleId),
        }
      );
      const updatedAssignment = mapAssignment(response.data);
      setAssignments((previous) =>
        previous.map((item) =>
          item.id === updatedAssignment.id ? updatedAssignment : item
        )
      );
      setSelectedAssignment((previous) =>
        previous?.id === updatedAssignment.id ? updatedAssignment : previous
      );
      setShowReassignModal(false);
      resetForm();
      await loadData();
      setSuccessMessage(
        "Assignment updated successfully. The new driver was notified."
      );
    } catch (error) {
      console.error("Unable to reassign trip:", error);
      setFormError(getApiError(error, "Unable to reassign trip."));
    }
  };

  const handleAdvanceTrip = async (assignment) => {
    const nextStatus = nextTripStatus[assignment.tripStatus];

    if (!nextStatus) return;

    try {
      const response = await axios.patch(
        `${API_URL}/assignments/${assignment.id}/status`,
        { status: nextStatus }
      );
      const updatedAssignment = mapAssignment(response.data);
      setAssignments((previous) =>
        previous.map((item) =>
          item.id === updatedAssignment.id ? updatedAssignment : item
        )
      );
      setSelectedAssignment(updatedAssignment);
      await loadData();
      setSuccessMessage(`Trip status updated to ${nextStatus}.`);
    } catch (error) {
      console.error("Unable to update trip status:", error);
      setFormError(getApiError(error, "Unable to update trip status."));
    }
  };

  const statusStyle = (status) => {
    switch (status) {
      case "Assigned":
        return "bg-blue-100 text-blue-700";
      case "OTW":
      case "Waiting":
        return "bg-amber-100 text-amber-700";
      case "Pickup":
      case "Drop":
        return "bg-purple-100 text-purple-700";
      case "Completed":
        return "bg-emerald-100 text-emerald-700";
      case "Cancelled":
        return "bg-red-100 text-red-700";
      default:
        return "bg-slate-100 text-slate-700";
    }
  };

  const renderDriverOptions = () => (
    <>
      <option value="">Select available driver</option>
      {availableDrivers.map((driver) => (
        <option key={driver.id} value={String(driver.id)}>
          {driver.name}
        </option>
      ))}
    </>
  );

  const renderVehicleOptions = () => (
    <>
      <option value="">Select available vehicle</option>
      {availableVehicles.map((vehicle) => (
        <option key={vehicle.id} value={String(vehicle.id)}>
          {vehicle.plate} — {vehicle.type} (Capacity: {vehicle.capacity})
        </option>
      ))}
    </>
  );

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="rounded-3xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <p className="mb-2 text-sm font-semibold uppercase tracking-wider text-teal-700">
              Transport Operations
            </p>
            <h2 className="text-3xl font-bold text-slate-900">
              Assignment Management
            </h2>
            <p className="mt-2 text-slate-500">
              Assign drivers and vehicles to confirmed bookings and monitor trip progress.
            </p>
          </div>

          <button
            onClick={openAssignModal}
            disabled={loading}
            className="inline-flex items-center justify-center gap-2 rounded-2xl bg-teal-600 px-5 py-3 font-medium text-white hover:bg-teal-700"
          >
            <Plus size={18} />
            Create Assignment
          </button>
        </div>
      </div>

      {/* Feedback */}
      {successMessage && (
        <div className="flex items-start gap-2 rounded-2xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800">
          <CheckCircle2 size={18} className="mt-0.5 shrink-0" />
          <p>{successMessage}</p>
          <button
            onClick={() => setSuccessMessage("")}
            className="ml-auto"
            aria-label="Dismiss message"
          >
            <X size={16} />
          </button>
        </div>
      )}

      {formError && !showAssignModal && !showReassignModal && (
        <ErrorMessage message={formError} />
      )}

      {loading && (
        <p className="rounded-2xl border border-slate-200 bg-white p-4 text-sm text-slate-500">
          Loading assignments and available resources...
        </p>
      )}

      {/* Summary */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {stats.map((stat) => {
          const Icon = stat.icon;

          return (
            <div
              key={stat.label}
              className="rounded-3xl border border-slate-200 bg-white p-5 shadow-sm"
            >
              <div className="flex items-center justify-between">
                <div>
                  <p className="text-sm text-slate-500">{stat.label}</p>
                  <p className="mt-2 text-3xl font-bold text-slate-900">
                    {stat.value}
                  </p>
                </div>
                <div className={`rounded-2xl p-3 ${stat.color}`}>
                  <Icon size={22} />
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {/* Assignment Records */}
      <div className="overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm">
        <div className="flex flex-col gap-4 border-b border-slate-100 p-6 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <h3 className="text-lg font-semibold text-slate-900">
              Assignment Records
            </h3>
            <p className="mt-1 text-sm text-slate-500">
              Review assignments, manage changes and monitor trip statuses.
            </p>
          </div>

          <div className="flex flex-col gap-3 sm:flex-row">
            <div className="relative w-full sm:w-64">
              <Search
                size={18}
                className="absolute left-4 top-1/2 -translate-y-1/2 text-slate-400"
              />
              <input
                type="text"
                placeholder="Search booking, driver..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="w-full rounded-2xl border border-slate-200 bg-slate-50 py-3 pl-11 pr-4 outline-none focus:border-teal-500"
              />
            </div>

            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              className="rounded-2xl border border-slate-200 bg-white px-4 py-3 outline-none focus:border-teal-500"
            >
              <option value="All">All statuses</option>
              {tripStatuses.map((status) => (
                <option key={status} value={status}>
                  {status}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead className="bg-slate-50 text-slate-500">
              <tr>
                {[
                  "Assignment",
                  "Booking / Customer",
                  "Driver",
                  "Vehicle",
                  "Schedule",
                  "Trip Status",
                  "Actions",
                ].map((heading) => (
                  <th
                    key={heading}
                    className="whitespace-nowrap px-6 py-4 text-left font-medium"
                  >
                    {heading}
                  </th>
                ))}
              </tr>
            </thead>

            <tbody className="divide-y divide-slate-100">
              {filteredAssignments.map((assignment) => {
                const driver = getDriver(assignment.driverId);
                const vehicle = getVehicle(assignment.vehicleId);

                return (
                  <tr
                    key={assignment.id}
                    className="hover:bg-slate-50"
                  >
                    <td className="whitespace-nowrap px-6 py-5 font-semibold text-slate-900">
                      {assignment.id}
                    </td>

                    <td className="px-6 py-5">
                      <p className="font-medium text-slate-900">
                        {assignment.bookingId}
                      </p>
                      <p className="mt-1 text-slate-500">
                        {assignment.customer}
                      </p>
                    </td>

                    <td className="whitespace-nowrap px-6 py-5">
                      {driver?.name || "Driver unavailable"}
                    </td>

                    <td className="whitespace-nowrap px-6 py-5">
                      {vehicle?.plate || "Vehicle unavailable"}
                    </td>

                    <td className="whitespace-nowrap px-6 py-5">
                      <p>{formatDate(assignment.date)}</p>
                      <p className="mt-1 text-slate-500">
                        {assignment.time}
                      </p>
                    </td>

                    <td className="whitespace-nowrap px-6 py-5">
                      <span
                        className={`inline-flex rounded-full px-3 py-1 text-xs font-semibold ${statusStyle(
                          assignment.tripStatus
                        )}`}
                      >
                        {assignment.tripStatus}
                      </span>
                    </td>

                    <td className="px-6 py-5">
                      <div className="flex justify-center gap-2">
                        <button
                          onClick={() => {
                            setSelectedAssignment(assignment);
                            setFormError("");
                          }}
                          title="View assignment"
                          aria-label={`View ${assignment.id}`}
                          className="rounded-xl border border-slate-200 bg-white p-2.5 text-slate-600 hover:bg-slate-100"
                        >
                          <Eye size={16} />
                        </button>

                        <button
                          onClick={() => openReassignModal(assignment)}
                          disabled={assignment.tripStatus === "Completed"}
                          title="Reassign driver or vehicle"
                          aria-label={`Reassign ${assignment.id}`}
                          className="rounded-xl bg-blue-600 p-2.5 text-white hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-40"
                        >
                          <RefreshCw size={16} />
                        </button>

                        <button
                          type="button"
                          disabled
                          title={assignment.notificationSent
                            ? "Assignment notification recorded"
                            : "No notification record"}
                          aria-label={`Notification status for ${assignment.id}`}
                          className={`rounded-xl p-2.5 text-white disabled:cursor-default disabled:opacity-100 ${
                            assignment.notificationSent ? "bg-emerald-700" : "bg-slate-300"
                          }`}
                        >
                          <Bell size={16} />
                        </button>
                      </div>
                    </td>
                  </tr>
                );
              })}

              {filteredAssignments.length === 0 && (
                <tr>
                  <td
                    colSpan={7}
                    className="px-6 py-12 text-center text-slate-500"
                  >
                    No assignment records found.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        <div className="border-t border-slate-100 px-6 py-4 text-sm text-slate-500">
          Showing {filteredAssignments.length} of {assignments.length} assignments
        </div>
      </div>

      {/* Create Assignment Modal */}
      {showAssignModal && (
        <Modal
          title="Assign Driver & Vehicle"
          subtitle="Select a confirmed booking, then choose available resources."
          onClose={closeAssignModal}
        >
          <div className="space-y-4">
            <Field label="Confirmed Booking">
              <select
                value={assignmentForm.bookingId}
                onChange={(e) =>
                  setAssignmentForm({
                    bookingId: e.target.value,
                    driverId: "",
                    vehicleId: "",
                  })
                }
                className="w-full rounded-xl border border-slate-200 p-3 outline-none focus:border-teal-500"
              >
                <option value="">Select booking</option>
                {eligibleBookings.map((booking) => (
                  <option key={booking.id} value={String(booking.id)}>
                    {booking.id} — {booking.customer}
                  </option>
                ))}
              </select>
            </Field>

            {eligibleBookings.length === 0 && (
              <p className="text-sm text-amber-700">
                There are no confirmed bookings awaiting assignment.
              </p>
            )}

            {selectedBooking && (
              <div className="space-y-3 rounded-2xl bg-slate-50 p-4">
                <Info
                  icon={MapPin}
                  label="Route"
                  value={`${selectedBooking.pickup} → ${selectedBooking.destination}`}
                />
                <Info
                  icon={CalendarDays}
                  label="Pickup Schedule"
                  value={`${formatDate(selectedBooking.date)} at ${selectedBooking.time}`}
                />
                <Info
                  icon={Users}
                  label="Passengers"
                  value={`${selectedBooking.passengers} passenger(s)`}
                />
              </div>
            )}

            <Field label="Available Driver">
              <select
                value={assignmentForm.driverId}
                onChange={(e) =>
                  setAssignmentForm((prev) => ({
                    ...prev,
                    driverId: e.target.value,
                  }))
                }
                disabled={!selectedBooking}
                className="w-full rounded-xl border border-slate-200 p-3 outline-none focus:border-teal-500 disabled:bg-slate-100"
              >
                <option value="">Select available driver</option>
                {availableDrivers.map((driver) => (
                  <option key={driver.id} value={String(driver.id)}>
                    {driver.name}
                  </option>
                ))}
              </select>
              {selectedBooking && availableDrivers.length === 0 && (
                <p className="mt-1 text-xs text-amber-700">
                  No driver is available for this time slot.
                </p>
              )}
            </Field>

            <Field label="Available Vehicle">
              <select
                value={assignmentForm.vehicleId}
                onChange={(e) =>
                  setAssignmentForm((prev) => ({
                    ...prev,
                    vehicleId: e.target.value,
                  }))
                }
                disabled={!selectedBooking}
                className="w-full rounded-xl border border-slate-200 p-3 outline-none focus:border-teal-500 disabled:bg-slate-100"
              >
                {renderVehicleOptions()}
              </select>
              {selectedBooking && availableVehicles.length === 0 && (
                <p className="mt-1 text-xs text-amber-700">
                  No suitable vehicle is available for this booking.
                </p>
              )}
            </Field>

            {formError && <ErrorMessage message={formError} />}

            <div className="flex justify-end gap-3 pt-3">
              <button
                onClick={closeAssignModal}
                className="rounded-xl border border-slate-200 px-5 py-2.5 font-medium text-slate-600 hover:bg-slate-50"
              >
                Cancel
              </button>
              <button
                onClick={handleAssign}
                disabled={!selectedBooking}
                className="rounded-xl bg-teal-600 px-5 py-2.5 font-medium text-white hover:bg-teal-700 disabled:cursor-not-allowed disabled:opacity-50"
              >
                Confirm Assignment
              </button>
            </div>
          </div>
        </Modal>
      )}

      {/* View Assignment Modal */}
      {selectedAssignment && (
        <Modal
          title="Assignment Details"
          subtitle={`${selectedAssignment.id} · ${selectedAssignment.bookingId}`}
          onClose={() => setSelectedAssignment(null)}
        >
          {(() => {
            const booking = getBooking(selectedAssignment.bookingId);
            const driver = getDriver(selectedAssignment.driverId);
            const vehicle = getVehicle(selectedAssignment.vehicleId);

            return (
              <div className="space-y-5">
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                  <Info label="Customer" value={selectedAssignment.customer} />
                  <Info label="Booking Status" value={booking?.status || "-"} />
                  <Info label="Driver" value={driver?.name || "-"} />
                  <Info label="Vehicle" value={vehicle?.plate || "-"} />
                  <Info label="Vehicle Type" value={vehicle?.type || "-"} />
                  <Info
                    label="Passenger Capacity"
                    value={
                      vehicle
                        ? `${booking?.passengers || 0} / ${vehicle.capacity} passengers`
                        : "-"
                    }
                  />
                  <Info
                    label="Pickup Date & Time"
                    value={`${formatDate(selectedAssignment.date)} · ${selectedAssignment.time}`}
                  />
                  <Info
                    label="Trip Status"
                    value={selectedAssignment.tripStatus}
                  />
                </div>

                {booking && (
                  <div className="rounded-2xl bg-slate-50 p-4">
                    <Info
                      icon={MapPin}
                      label="Pickup Location"
                      value={booking.pickup}
                    />
                    <div className="my-3 border-t border-slate-200" />
                    <Info
                      icon={MapPin}
                      label="Destination"
                      value={booking.destination}
                    />
                  </div>
                )}

                <div className="rounded-2xl border border-slate-200 p-4">
                  <p className="font-semibold text-slate-900">
                    Trip Progress
                  </p>

                  <div className="mt-4 flex flex-wrap gap-2">
                    {tripStatuses.map((status, index) => {
                      const currentIndex = tripStatuses.indexOf(
                        selectedAssignment.tripStatus
                      );
                      const reached = index <= currentIndex;

                      return (
                        <span
                          key={status}
                          className={`rounded-full px-3 py-1.5 text-xs font-medium ${
                            reached
                              ? "bg-teal-100 text-teal-800"
                              : "bg-slate-100 text-slate-500"
                          }`}
                        >
                          {status}
                        </span>
                      );
                    })}
                  </div>

                  {nextTripStatus[selectedAssignment.tripStatus] && (
                    <button
                      onClick={() => handleAdvanceTrip(selectedAssignment)}
                      className="mt-4 w-full rounded-xl bg-teal-600 px-4 py-3 font-medium text-white hover:bg-teal-700"
                    >
                      Update to {nextTripStatus[selectedAssignment.tripStatus]}
                    </button>
                  )}

                  {selectedAssignment.tripStatus === "Completed" && (
                    <p className="mt-4 text-sm text-emerald-700">
                      This trip has been completed.
                    </p>
                  )}
                </div>

                <div className="rounded-2xl border border-slate-200 p-4">
                  <p className="font-semibold text-slate-900">
                    Driver Notification
                  </p>
                  <p className="mt-1 text-sm text-slate-500">
                    {selectedAssignment.notificationSent
                      ? "Notification is marked as sent."
                      : "No notification has been marked as sent."}
                  </p>
                </div>

              </div>
            );
          })()}
        </Modal>
      )}
      
      {/* Reassign Modal */}
      {showReassignModal && selectedRecord && (
        <Modal
          title="Reassign Driver & Vehicle"
          subtitle={`Update resources for ${selectedRecord.bookingId}.`}
          onClose={() => {
            setShowReassignModal(false);
            resetForm();
          }}
        >
          <div className="space-y-4">
            <div className="rounded-2xl bg-slate-50 p-4">
              <Info label="Customer" value={selectedRecord.customer} />
              <Info
                label="Current Driver"
                value={getDriver(selectedRecord.driverId)?.name || "-"}
              />
              <Info
                label="Current Vehicle"
                value={getVehicle(selectedRecord.vehicleId)?.plate || "-"}
              />
              <Info
                label="Trip Status"
                value={selectedRecord.tripStatus}
              />
            </div>

            <Field label="New Driver">
              <select
                value={assignmentForm.driverId}
                onChange={(e) =>
                  setAssignmentForm((prev) => ({
                    ...prev,
                    driverId: e.target.value,
                  }))
                }
                className="w-full rounded-xl border border-slate-200 p-3 outline-none focus:border-teal-500"
              >
                {renderDriverOptions()}
              </select>
            </Field>

            <Field label="New Vehicle">
              <select
                value={assignmentForm.vehicleId}
                onChange={(e) =>
                  setAssignmentForm((prev) => ({
                    ...prev,
                    vehicleId: e.target.value,
                  }))
                }
                className="w-full rounded-xl border border-slate-200 p-3 outline-none focus:border-teal-500"
              >
                {renderVehicleOptions()}
              </select>
            </Field>

            <p className="text-xs text-slate-500">
              The system revalidates availability before saving and returns changed resources to Available.
            </p>

            {formError && <ErrorMessage message={formError} />}

            <div className="flex justify-end gap-3 pt-3">
              <button
                onClick={() => {
                  setShowReassignModal(false);
                  resetForm();
                }}
                className="rounded-xl border border-slate-200 px-5 py-2.5 font-medium text-slate-600 hover:bg-slate-50"
              >
                Cancel
              </button>
              <button
                onClick={handleReassign}
                className="rounded-xl bg-blue-600 px-5 py-2.5 font-medium text-white hover:bg-blue-700"
              >
                Save Changes
              </button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
}

function Modal({ title, subtitle, onClose, children }) {
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/50 p-4"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="max-h-[90vh] w-full max-w-2xl overflow-y-auto rounded-3xl bg-white shadow-xl">
        <div className="sticky top-0 flex items-start justify-between border-b border-slate-100 bg-white p-6">
          <div>
            <h3 className="text-xl font-bold text-slate-900">{title}</h3>
            {subtitle && (
              <p className="mt-1 text-sm text-slate-500">{subtitle}</p>
            )}
          </div>
          <button
            onClick={onClose}
            aria-label="Close modal"
            className="rounded-xl p-2 text-slate-500 hover:bg-slate-100"
          >
            <X size={20} />
          </button>
        </div>
        <div className="p-6">{children}</div>
      </div>
    </div>
  );
}

function Field({ label, children }) {
  return (
    <label className="block">
      <span className="mb-2 block text-sm font-medium text-slate-700">
        {label}
      </span>
      {children}
    </label>
  );
}

function Info({ icon: Icon, label, value }) {
  return (
    <div className="mb-3 last:mb-0">
      <p className="flex items-center gap-2 text-sm text-slate-500">
        {Icon && <Icon size={15} />}
        {label}
      </p>
      <p className="mt-1 break-words font-medium text-slate-900">
        {value || "-"}
      </p>
    </div>
  );
}

function ErrorMessage({ message }) {
  if (!message) return null;

  return (
    <div className="flex items-start gap-2 rounded-xl border border-red-200 bg-red-50 p-3 text-sm text-red-700">
      <AlertCircle size={18} className="mt-0.5 shrink-0" />
      <p>{message}</p>
    </div>
  );
}
