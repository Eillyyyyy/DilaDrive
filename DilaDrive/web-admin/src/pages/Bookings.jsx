
import { useEffect, useMemo, useState } from "react";
import {
  Search,
  Eye,
  CheckCircle2,
  X,
  ShieldCheck,
  CalendarDays,
  MapPin,
  Users,
  Luggage,
  Plane,
  Clock,
  Truck,
  UserRound,
  ClipboardCheck,
  RefreshCw,
  ArrowRight,
  AlertCircle,
} from "lucide-react";

const TRIP_STATUSES = [
  "Assigned",
  "OTW",
  "Waiting",
  "Pickup",
  "Drop",
  "Completed",
];

const BOOKING_STATUSES = [
  "Pending Verification",
  "Confirmed",
  "Cancelled",
];

const initialAssignment = {
  driver: "",
  vehicle: "",
};

export default function Bookings({
  bookings = [],
  drivers = [],
  vehicles = [],
  onVerifyBooking,
  onAssignTrip,
  onReassignTrip,
}) {
  const [bookingList, setBookingList] = useState(
    () => bookings || []
  );

  const [driverList] = useState(() => drivers || []);
  const [vehicleList] = useState(() => vehicles || []);

  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("All");

  const [selectedBooking, setSelectedBooking] = useState(null);
  const [bookingToVerify, setBookingToVerify] = useState(null);
  const [bookingToAssign, setBookingToAssign] = useState(null);

  const [assignment, setAssignment] = useState({
    ...initialAssignment,
  });

  const [error, setError] = useState("");

  // Keep displayed records synchronized with parent data.
  useEffect(() => {
    setBookingList(bookings || []);
  }, [bookings]);

  const stats = useMemo(() => ({
    total: bookingList.length,

    pending: bookingList.filter(
      (b) => (b.status || "Pending Verification") ===
        "Pending Verification"
    ).length,

    confirmed: bookingList.filter(
      (b) => b.status === "Confirmed" ||
        b.status === "Verified"
    ).length,

    assigned: bookingList.filter(
      (b) => Boolean(b.driverId || b.driver)
    ).length,
  }), [bookingList]);

  const filteredBookings = useMemo(() => {
    const query = search.trim().toLowerCase();

    return bookingList.filter((booking) => {
      const searchable = [
        booking.id,
        booking.customer,
        booking.phone,
        booking.pickup,
        booking.dropoff,
        booking.flight,
        booking.status,
      ]
        .map((value) => String(value || ""))
        .join(" ")
        .toLowerCase();

      const matchesSearch = searchable.includes(query);

      const matchesStatus =
        statusFilter === "All" ||
        (booking.status || "Pending Verification") ===
          statusFilter;

      return matchesSearch && matchesStatus;
    });
  }, [bookingList, search, statusFilter]);

  const updateBooking = (id, changes) => {
    setBookingList((previous) =>
      previous.map((booking) =>
        booking.id === id
          ? { ...booking, ...changes }
          : booking
      )
    );

    setSelectedBooking((previous) =>
      previous?.id === id
        ? { ...previous, ...changes }
        : previous
    );
  };

  const handleVerifyBooking = async () => {
    if (!bookingToVerify) return;

    const booking = bookingToVerify;

    // Booking verification must check schedule and resources.
    if (booking.status === "Cancelled") {
      setError("Cancelled bookings cannot be confirmed.");
      return;
    }

    try {
      if (onVerifyBooking) {
        await onVerifyBooking(booking.id);
      }

      updateBooking(booking.id, {
        status: "Confirmed",
      });

      setBookingToVerify(null);
      setError("");
    } catch {
      setError(
        "Unable to confirm this booking. Please try again."
      );
    }
  };

  const openAssignment = (booking) => {
    setBookingToAssign(booking);

    setAssignment({
      driver: booking.driverId || booking.driver || "",
      vehicle: booking.vehicleId || booking.vehicle || "",
    });

    setError("");
  };

  const handleAssignTrip = async (e) => {
    e.preventDefault();

    if (!bookingToAssign) return;

    if (
      !assignment.driver ||
      !assignment.vehicle
    ) {
      setError("Please select both a driver and a vehicle.");
      return;
    }

    const selectedDriver = driverList.find(
      (driver) =>
        String(driver.id || driver.name) ===
        String(assignment.driver)
    );

    const selectedVehicle = vehicleList.find(
      (vehicle) =>
        String(vehicle.id || vehicle.plate) ===
        String(assignment.vehicle)
    );

    if (!selectedDriver || !selectedVehicle) {
      setError("The selected driver or vehicle was not found.");
      return;
    }

    const driverAvailable =
      selectedDriver.status === "Available";

    const vehicleAvailable =
      selectedVehicle.status === "Available";

    if (!driverAvailable) {
      setError("The selected driver is not available.");
      return;
    }

    if (!vehicleAvailable) {
      setError("The selected vehicle is not available.");
      return;
    }

    const passengers = Number(
      bookingToAssign.passengers || 1
    );

    if (Number(selectedVehicle.capacity) < passengers) {
      setError(
        "Vehicle passenger capacity is insufficient for this booking."
      );
      return;
    }

    const isReassignment = Boolean(
      bookingToAssign.driverId || bookingToAssign.driver
    );

    try {
      const payload = {
        bookingId: bookingToAssign.id,
        driverId: selectedDriver.id || selectedDriver.name,
        vehicleId: selectedVehicle.id || selectedVehicle.plate,
      };

      if (isReassignment && onReassignTrip) {
        await onReassignTrip(payload);
      } else if (onAssignTrip) {
        await onAssignTrip(payload);
      }

      updateBooking(bookingToAssign.id, {
        driverId: payload.driverId,
        driver: selectedDriver.name || selectedDriver.id,
        vehicleId: payload.vehicleId,
        vehicle: selectedVehicle.plate || selectedVehicle.id,
        tripStatus: "Assigned",
      });

      setBookingToAssign(null);
      setError("");
    } catch {
      setError(
        "Unable to save the assignment. Please try again."
      );
    }
  };

  const getBookingStyle = (status) => {
    const styles = {
      "Pending Verification":
        "bg-amber-50 text-amber-700 ring-amber-600/20",
      Confirmed:
        "bg-emerald-50 text-emerald-700 ring-emerald-600/20",
      Verified:
        "bg-emerald-50 text-emerald-700 ring-emerald-600/20",
      Cancelled:
        "bg-rose-50 text-rose-700 ring-rose-600/20",
    };

    return styles[status] ||
      "bg-slate-100 text-slate-600 ring-slate-500/20";
  };

  const getTripStyle = (status) => {
    if (status === "Completed") {
      return "bg-emerald-50 text-emerald-700";
    }

    if (status === "OTW" || status === "Pickup") {
      return "bg-blue-50 text-blue-700";
    }

    if (status === "Waiting" || status === "Drop") {
      return "bg-amber-50 text-amber-700";
    }

    return "bg-slate-100 text-slate-600";
  };

  const money = (value) => {
    if (value === undefined || value === null || value === "") {
      return "Not set";
    }

    if (typeof value === "string" && /[^\d.,\s]/.test(value)) {
      return value;
    }

    const amount = Number(String(value).replace(/,/g, ""));

    if (!Number.isFinite(amount)) return String(value);

    return amount.toLocaleString("en-MY", {
      style: "currency",
      currency: "MYR",
    });
  };

  const formatDate = (value) => {
    if (!value) return "Not provided";

    const date = new Date(value);

    if (Number.isNaN(date.getTime())) return value;

    return date.toLocaleDateString("en-MY", {
      day: "2-digit",
      month: "short",
      year: "numeric",
    });
  };

  return (
    <div className="min-h-screen space-y-6 bg-slate-50/70 p-4 sm:p-6 lg:p-8">

      {/* Header */}
      <header className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <p className="mb-2 text-sm font-medium text-teal-700">
            DilaDrive / Operations
          </p>

          <h1 className="text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">
            Booking Management
          </h1>

          <p className="mt-2 max-w-2xl text-sm leading-6 text-slate-500">
            Review customer trip requests, confirm booking
            availability, and manage driver and vehicle assignments.
          </p>
        </div>
      </header>

      {/* Summary */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <Stat
          title="Total Bookings"
          value={stats.total}
          icon={CalendarDays}
          color="bg-slate-100 text-slate-700"
        />

        <Stat
          title="Pending Verification"
          value={stats.pending}
          icon={AlertCircle}
          color="bg-amber-50 text-amber-700"
        />

        <Stat
          title="Confirmed"
          value={stats.confirmed}
          icon={ShieldCheck}
          color="bg-emerald-50 text-emerald-700"
        />

        <Stat
          title="Driver Assigned"
          value={stats.assigned}
          icon={Truck}
          color="bg-blue-50 text-blue-700"
        />
      </div>

      {/* Booking table */}
      <section className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
        <div className="flex flex-col gap-4 border-b border-slate-100 p-5 sm:p-6 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <h2 className="text-lg font-bold text-slate-900">
              Booking Records
            </h2>
            <p className="mt-1 text-sm text-slate-500">
              Review requests and manage trip preparation.
            </p>
          </div>

          <div className="flex flex-col gap-3 sm:flex-row">
            <div className="relative">
              <Search
                size={17}
                className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400"
              />

              <input
                type="search"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Search booking or customer..."
                aria-label="Search bookings"
                className="w-full rounded-xl border border-slate-200 bg-slate-50 py-2.5 pl-10 pr-4 text-sm outline-none focus:border-teal-500 focus:bg-white focus:ring-4 focus:ring-teal-500/10 sm:w-72"
              />
            </div>

            <select
              value={statusFilter}
              onChange={(e) => setStatusFilter(e.target.value)}
              aria-label="Filter bookings by status"
              className="rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm outline-none focus:border-teal-500"
            >
              <option value="All">All booking statuses</option>
              {BOOKING_STATUSES.map((status) => (
                <option key={status} value={status}>
                  {status}
                </option>
              ))}
              <option value="Verified">Verified (legacy)</option>
            </select>
          </div>
        </div>

        <div className="flex items-center justify-between bg-slate-50/70 px-5 py-3 sm:px-6">
          <p className="text-sm text-slate-500">
            Showing{" "}
            <span className="font-semibold text-slate-800">
              {filteredBookings.length}
            </span>{" "}
            of {bookingList.length} bookings
          </p>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full min-w-[1050px] text-left">
            <thead>
              <tr className="border-b border-slate-100 text-xs uppercase tracking-wide text-slate-500">
                <th className="px-5 py-4 font-semibold">Booking</th>
                <th className="px-5 py-4 font-semibold">Customer</th>
                <th className="px-5 py-4 font-semibold">Trip Route</th>
                <th className="px-5 py-4 font-semibold">Schedule</th>
                <th className="px-5 py-4 font-semibold">Fare</th>
                <th className="px-5 py-4 font-semibold">Status</th>
                <th className="px-5 py-4 text-right font-semibold">Actions</th>
              </tr>
            </thead>

            <tbody className="divide-y divide-slate-100">
              {filteredBookings.map((booking) => {
                const status =
                  booking.status || "Pending Verification";

                const assigned =
                  Boolean(booking.driverId || booking.driver);

                return (
                  <tr
                    key={booking.id}
                    className="transition hover:bg-slate-50/80"
                  >
                    <td className="px-5 py-4">
                      <p className="font-semibold text-slate-900">
                        {booking.id}
                      </p>
                      <p className="mt-1 text-xs text-slate-400">
                        {booking.passengers || 1} passenger(s)
                      </p>
                    </td>

                    <td className="px-5 py-4">
                      <p className="font-medium text-slate-800">
                        {booking.customer || "Not provided"}
                      </p>
                      <p className="mt-1 text-xs text-slate-500">
                        {booking.phone || "No phone number"}
                      </p>
                    </td>

                    <td className="px-5 py-4">
                      <div className="max-w-64 space-y-2">
                        <p className="flex items-start gap-2 text-sm text-slate-700">
                          <MapPin
                            size={15}
                            className="mt-0.5 shrink-0 text-emerald-600"
                          />
                          <span>{booking.pickup || "Not provided"}</span>
                        </p>
                        <p className="flex items-start gap-2 text-sm text-slate-500">
                          <ArrowRight
                            size={15}
                            className="mt-0.5 shrink-0"
                          />
                          <span>{booking.dropoff || "Not provided"}</span>
                        </p>
                      </div>
                    </td>

                    <td className="px-5 py-4">
                      <p className="text-sm font-medium text-slate-800">
                        {formatDate(booking.date)}
                      </p>
                      <p className="mt-1 text-xs text-slate-500">
                        {booking.time || booking.pickupTime || "Time not set"}
                      </p>
                    </td>

                    <td className="px-5 py-4 text-sm font-semibold text-slate-800">
                      {money(booking.fare)}
                    </td>

                    <td className="px-5 py-4">
                      <div className="space-y-2">
                        <span
                          className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ring-1 ring-inset ${getBookingStyle(status)}`}
                        >
                          {status}
                        </span>

                        {assigned && (
                          <p className="text-xs text-slate-500">
                            Driver assigned
                          </p>
                        )}

                        {booking.tripStatus && (
                          <span
                            className={`block w-fit rounded-full px-2.5 py-1 text-xs font-medium ${getTripStyle(booking.tripStatus)}`}
                          >
                            Trip: {booking.tripStatus}
                          </span>
                        )}
                      </div>
                    </td>

                    <td className="px-5 py-4">
                      <div className="flex justify-end gap-2">
                        <button
                          type="button"
                          onClick={() => setSelectedBooking(booking)}
                          className="inline-flex items-center gap-1.5 rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
                        >
                          <Eye size={15} />
                          View
                        </button>

                        {status === "Pending Verification" && (
                          <button
                            type="button"
                            onClick={() => {
                              setBookingToVerify(booking);
                              setError("");
                            }}
                            className="inline-flex items-center gap-1.5 rounded-lg bg-emerald-600 px-3 py-2 text-sm font-medium text-white hover:bg-emerald-700"
                          >
                            <CheckCircle2 size={15} />
                            Verify
                          </button>
                        )}

                        {(status === "Confirmed" ||
                          status === "Verified") && (
                          <button
                            type="button"
                            onClick={() => openAssignment(booking)}
                            className="inline-flex items-center gap-1.5 rounded-lg bg-teal-600 px-3 py-2 text-sm font-medium text-white hover:bg-teal-700"
                          >
                            {assigned ? (
                              <RefreshCw size={15} />
                            ) : (
                              <Truck size={15} />
                            )}
                            {assigned ? "Reassign" : "Assign"}
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })}

              {filteredBookings.length === 0 && (
                <tr>
                  <td colSpan={7} className="px-6 py-16 text-center">
                    <Search
                      size={28}
                      className="mx-auto text-slate-300"
                    />
                    <p className="mt-3 font-semibold text-slate-800">
                      No bookings found
                    </p>
                    <p className="mt-1 text-sm text-slate-500">
                      Try changing your search or status filter.
                    </p>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      {/* Booking details */}
      {selectedBooking && (
        <Modal
          title="Booking Details"
          subtitle="Customer and trip information"
          onClose={() => setSelectedBooking(null)}
          icon={<ClipboardCheck size={21} />}
        >
          <div className="space-y-5">
            <div className="rounded-xl bg-slate-50 p-4">
              <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                Booking Reference
              </p>
              <p className="mt-1 text-xl font-bold text-slate-900">
                {selectedBooking.id}
              </p>
              <span
                className={`mt-3 inline-flex rounded-full px-3 py-1 text-xs font-semibold ${getBookingStyle(
                  selectedBooking.status || "Pending Verification"
                )}`}
              >
                {selectedBooking.status || "Pending Verification"}
              </span>
            </div>

            <Detail label="Customer" value={selectedBooking.customer} />
            <Detail label="Contact Number" value={selectedBooking.phone} />
            <Detail label="Pickup Location" value={selectedBooking.pickup} />
            <Detail label="Destination" value={selectedBooking.dropoff} />
            <Detail label="Booking Date" value={formatDate(selectedBooking.date)} />
            <Detail label="Pickup Time / Landing Time" value={selectedBooking.time || selectedBooking.pickupTime} />
            <Detail label="Flight Number" value={selectedBooking.flight} />
            <Detail label="Passengers" value={selectedBooking.passengers} />
            <Detail label="Luggage Count" value={selectedBooking.luggageCount} />
            <Detail label="Luggage Size" value={selectedBooking.luggageSize} />
            <Detail label="Trip Fare" value={money(selectedBooking.fare)} />
            <Detail label="Assigned Driver" value={selectedBooking.driver || "Not assigned"} />
            <Detail label="Assigned Vehicle" value={selectedBooking.vehicle || "Not assigned"} />
            <Detail label="Trip Status" value={selectedBooking.tripStatus || "Not assigned"} />
            <Detail label="Payment Status" value={selectedBooking.paymentStatus || "Not provided"} />

            {selectedBooking.status === "Pending Verification" && (
              <button
                type="button"
                onClick={() => {
                  setBookingToVerify(selectedBooking);
                  setSelectedBooking(null);
                  setError("");
                }}
                className="inline-flex w-full items-center justify-center gap-2 rounded-xl bg-emerald-600 px-4 py-3 font-semibold text-white hover:bg-emerald-700"
              >
                <ShieldCheck size={18} />
                Verify Booking
              </button>
            )}

            {(selectedBooking.status === "Confirmed" ||
              selectedBooking.status === "Verified") && (
              <button
                type="button"
                onClick={() => {
                  openAssignment(selectedBooking);
                  setSelectedBooking(null);
                }}
                className="inline-flex w-full items-center justify-center gap-2 rounded-xl bg-teal-600 px-4 py-3 font-semibold text-white hover:bg-teal-700"
              >
                <Truck size={18} />
                {selectedBooking.driver ? "Reassign Trip" : "Assign Driver & Vehicle"}
              </button>
            )}
          </div>
        </Modal>
      )}

      {/* Verify confirmation */}
      {bookingToVerify && (
        <Modal
          title="Confirm Booking"
          subtitle="Check availability before confirming this request."
          onClose={() => {
            setBookingToVerify(null);
            setError("");
          }}
          icon={<ShieldCheck size={21} />}
        >
          <div className="space-y-4">
            <p className="text-sm leading-6 text-slate-600">
              Confirm that the requested schedule and suitable
              transport resources can accommodate this booking.
            </p>

            <div className="rounded-xl bg-slate-50 p-4">
              <Detail label="Booking ID" value={bookingToVerify.id} />
              <Detail label="Customer" value={bookingToVerify.customer} />
              <Detail label="Route" value={`${bookingToVerify.pickup || "N/A"} → ${bookingToVerify.dropoff || "N/A"}`} />
              <Detail label="Passengers" value={bookingToVerify.passengers || 1} />
            </div>

            {error && <ErrorMessage>{error}</ErrorMessage>}

            <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
              <button
                type="button"
                onClick={() => {
                  setBookingToVerify(null);
                  setError("");
                }}
                className="rounded-xl border border-slate-200 px-4 py-2.5 font-medium text-slate-700 hover:bg-slate-50"
              >
                Cancel
              </button>

              <button
                type="button"
                onClick={handleVerifyBooking}
                className="inline-flex items-center justify-center gap-2 rounded-xl bg-emerald-600 px-5 py-2.5 font-semibold text-white hover:bg-emerald-700"
              >
                <CheckCircle2 size={17} />
                Confirm Booking
              </button>
            </div>
          </div>
        </Modal>
      )}

      {/* Driver and vehicle assignment */}
      {bookingToAssign && (
        <Modal
          title={
            bookingToAssign.driver
              ? "Reassign Trip"
              : "Assign Driver & Vehicle"
          }
          subtitle="Select available resources for this confirmed booking."
          onClose={() => {
            setBookingToAssign(null);
            setError("");
          }}
          icon={<Truck size={21} />}
        >
          <form onSubmit={handleAssignTrip} className="space-y-5">
            <div className="rounded-xl bg-slate-50 p-4">
              <Detail label="Booking ID" value={bookingToAssign.id} />
              <Detail label="Customer" value={bookingToAssign.customer} />
              <Detail label="Passengers" value={bookingToAssign.passengers || 1} />
            </div>

            <div>
              <label className="mb-2 block text-sm font-semibold text-slate-700">
                Available Driver
              </label>

              <select
                value={assignment.driver}
                onChange={(e) =>
                  setAssignment((old) => ({
                    ...old,
                    driver: e.target.value,
                  }))
                }
                required
                className="w-full rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm outline-none focus:border-teal-500"
              >
                <option value="">Select driver</option>
                {driverList
                  .filter((driver) => driver.status === "Available")
                  .map((driver) => (
                    <option
                      key={driver.id || driver.name}
                      value={driver.id || driver.name}
                    >
                      {driver.name} — {driver.status}
                    </option>
                  ))}
              </select>

              {driverList.length === 0 && (
                <p className="mt-2 text-xs text-amber-700">
                  No driver records were provided to this page.
                </p>
              )}
            </div>

            <div>
              <label className="mb-2 block text-sm font-semibold text-slate-700">
                Available Vehicle
              </label>

              <select
                value={assignment.vehicle}
                onChange={(e) =>
                  setAssignment((old) => ({
                    ...old,
                    vehicle: e.target.value,
                  }))
                }
                required
                className="w-full rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm outline-none focus:border-teal-500"
              >
                <option value="">Select vehicle</option>
                {vehicleList
                  .filter((vehicle) => vehicle.status === "Available")
                  .map((vehicle) => (
                    <option
                      key={vehicle.id || vehicle.plate}
                      value={vehicle.id || vehicle.plate}
                    >
                      {vehicle.plate} — {vehicle.type} — {vehicle.capacity} seats
                    </option>
                  ))}
              </select>
            </div>

            {error && <ErrorMessage>{error}</ErrorMessage>}

            <p className="text-xs leading-5 text-slate-400">
              Assignment checks the selected driver's availability
              and the vehicle's passenger capacity. The backend
              must also validate schedule conflicts before saving.
            </p>

            <div className="flex flex-col-reverse gap-3 border-t border-slate-100 pt-5 sm:flex-row sm:justify-end">
              <button
                type="button"
                onClick={() => {
                  setBookingToAssign(null);
                  setError("");
                }}
                className="rounded-xl border border-slate-200 px-4 py-2.5 font-medium text-slate-700 hover:bg-slate-50"
              >
                Cancel
              </button>

              <button
                type="submit"
                className="inline-flex items-center justify-center gap-2 rounded-xl bg-teal-600 px-5 py-2.5 font-semibold text-white hover:bg-teal-700"
              >
                <CheckCircle2 size={17} />
                Save Assignment
              </button>
            </div>
          </form>
        </Modal>
      )}
    </div>
  );
}

function Stat({ title, value, icon: Icon, color }) {
  return (
    <div className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm">
      <div className="flex items-start justify-between">
        <div>
          <p className="text-sm font-medium text-slate-500">{title}</p>
          <p className="mt-3 text-3xl font-bold text-slate-900">
            {value}
          </p>
        </div>
        <div className={`rounded-xl p-3 ${color}`}>
          <Icon size={21} />
        </div>
      </div>
    </div>
  );
}

function Detail({ label, value }) {
  return (
    <div className="border-b border-slate-100 py-3 last:border-0">
      <p className="text-xs font-medium uppercase tracking-wide text-slate-400">
        {label}
      </p>
      <p className="mt-1 break-words text-sm font-semibold text-slate-800">
        {value === undefined || value === null || value === ""
          ? "Not provided"
          : value}
      </p>
    </div>
  );
}

function Modal({ title, subtitle, icon, onClose, children }) {
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-slate-950/50 p-4 backdrop-blur-sm"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className="my-auto w-full max-w-xl overflow-hidden rounded-2xl bg-white shadow-2xl"
      >
        <div className="flex items-start justify-between border-b border-slate-100 p-5 sm:p-6">
          <div className="flex items-start gap-3">
            <div className="rounded-xl bg-teal-50 p-3 text-teal-700">
              {icon}
            </div>
            <div>
              <h2 className="text-lg font-bold text-slate-900">
                {title}
              </h2>
              <p className="mt-1 text-sm text-slate-500">
                {subtitle}
              </p>
            </div>
          </div>

          <button
            type="button"
            onClick={onClose}
            aria-label="Close dialog"
            className="rounded-lg p-2 text-slate-400 hover:bg-slate-100"
          >
            <X size={19} />
          </button>
        </div>

        <div className="max-h-[75vh] overflow-y-auto p-5 sm:p-6">
          {children}
        </div>
      </div>
    </div>
  );
}

function ErrorMessage({ children }) {
  return (
    <div
      role="alert"
      className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700"
    >
      {children}
    </div>
  );
}
