import useModalScrollLock from "../components/useModalScrollLock";
import useRecordFocus from "../services/useRecordFocus";

import { useEffect, useMemo, useState, useRef } from "react";
import api from "../services/api";
import { TripCharges } from "../components/TripPricing";
import AssignmentResourcePicker from "../components/AssignmentResourcePicker";
import { assignmentDuration, bookingAssignment, confirmationError } from "../services/bookingAssignments";
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
  ClipboardCheck,
  RefreshCw,
  ArrowRight,
  AlertCircle,
} from "lucide-react";

const BOOKING_STATUSES = [
  "Pending Verification",
  "Confirmed",
  "In Progress",
  "Completed",
  "Cancelled", "Rejected",
];

export default function Bookings({
  onNavigate,
  bookingFocus,
  onBookingOpened,
}) {
 const [bookingList, setBookingList] = useState([]);
 const requestVersion = useRef(0);
 useEffect(() => {
   const refresh = () => { fetchBookings(); };
   refresh();
   window.addEventListener("focus", refresh);
   window.addEventListener("diladrive:assignments-changed", refresh);
   return () => {
     requestVersion.current++;
     window.removeEventListener("focus", refresh);
     window.removeEventListener("diladrive:assignments-changed", refresh);
   };
 }, []);

const fetchBookings = async () => {
  const version = ++requestVersion.current;
  setLoading(true);
  setLoadError("");
  try {
    const [response, customersResponse, assignmentsResponse] =
      await Promise.all([
        api.get("/bookings"),
        api.get("/customers"),
        api.get("/assignments"),
      ]);
    if (version !== requestVersion.current) return;
    const customers = customersResponse.data;

    const formattedBookings =
      response.data.map(booking => {
        const assignment = bookingAssignment(booking, assignmentsResponse.data);
        const inProgress = ["OTW", "WAITING", "PICKUP", "DROP"].includes(assignment.tripStatus);
        return {

        id: booking.bookingId,
        hasAcceptedFare: !!booking.fareSnapshot,
        roadDistancePending: booking.roadDistancePending === true,

        customer:
          booking.contactName || booking.custName ||
          booking.customerName ||
          customers.find((customer) =>
            String(customer.custId) === String(booking.custId)
          )?.custName ||
          "Customer",

        date:
          booking.bookingTripdate,

        pickupTime:
          booking.bookingPickuptime,

        pickup:
          booking.bookingPickupaddress,

        dropoff:
          booking.bookingDestinationaddress,

        flight:
          booking.bookingFlightno,

        passengers:
          booking.bookingPassengercount,

        smallCount: booking.smallCount, mediumCount: booking.mediumCount, largeCount: booking.largeCount,
        phone: booking.contactPhone || customers.find(c => c.custId === booking.custId)?.custPhoneno,
        customerCategory: customers.find(c => c.custId === booking.custId)?.custType,
        distanceKm: booking.bookingDistancekm,
        luggageUnits: booking.luggageUnits, reservedEndAt: booking.reservedEndAt,
        durationSeconds: booking.durationSeconds,
        luggageCount:
          booking.bookingLuggagecount,

        luggageSize:
          booking.bookingLuggagesize,

        fare:
          booking.bookingTripfare,

        type:
          booking.bookingType,

        tripType:
          booking.bookingTriptype,

        notes:
          booking.bookingNotes,

        ...assignment,

        status: inProgress ? "In Progress" : ({
          PENDING: "Pending Verification",
          PENDING_VERIFICATION: "Pending Verification",
          CONFIRMED: "Confirmed",
          ASSIGNED: "Confirmed",
          OTW: "In Progress",
          WAITING: "In Progress",
          PICKUP: "In Progress",
          DROP: "In Progress",
          COMPLETED: "Completed",
          CANCELLED: "Cancelled", REJECTED: "Rejected",
        }[String(booking.bookingStatus || "").toUpperCase()] ||
          booking.bookingStatus ||
          "Pending Verification")

        };
      });

    setBookingList(formattedBookings);
    setSelectedBooking(previous => previous ? formattedBookings.find(b => b.id === previous.id) || null : null);

  } catch (error) {
    if (version === requestVersion.current) setLoadError(error.response?.data?.message || error.response?.data?.detail || error.message);
  } finally {
    if (version === requestVersion.current) setLoading(false);
  }
  };

  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("All");

  const [selectedBooking, setSelectedBooking] = useState(null);
  const [bookingToVerify, setBookingToVerify] = useState(null);
  const [fareInput, setFareInput] = useState("");

  const [error, setError] = useState("");
  const [loadError, setLoadError] = useState("");
  const [loading, setLoading] = useState(true);
  useRecordFocus({ id: bookingFocus, items: bookingList, idKey: "id", loading,
    error: loadError, open: setSelectedBooking, complete: onBookingOpened });
  const [busy, setBusy] = useState(false);
  const submitLock = useRef(false);
  const [notice, setNotice] = useState("");
  const [scheduledEndAt, setScheduledEndAt] = useState("");
  useEffect(() => {
    setScheduledEndAt(bookingToVerify?.reservedEndAt?.slice(0,19) || "");
    setError("");
  }, [bookingToVerify?.id]);
  const [assignmentOption, setAssignmentOption] = useState(null);

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
      (b) => Boolean(b.assignmentId)
    ).length,
  }), [bookingList]);

  const filteredBookings = useMemo(() => {
    const query = search.trim().toLowerCase();

    return bookingList.filter((booking) => {
      const searchable = [
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

  const handleVerifyBooking = async () => {
    if (!bookingToVerify || submitLock.current) return;
    const finalFare = Number(bookingToVerify.fare) > 0 ? Number(bookingToVerify.fare) : Number(fareInput);
    if (!assignmentOption) {
      setError("Recheck availability and select a suitable vehicle and eligible driver."); return;
    }
    if (!scheduledEndAt || scheduledEndAt <= (bookingToVerify.date + "T" + bookingToVerify.pickupTime).slice(0, 16)) {
      setError("Enter a scheduled end time after pickup. No trip duration is assumed."); return;
    }
    if (!bookingToVerify.hasAcceptedFare && (!Number.isFinite(finalFare) || finalFare <= 0)) {
      setError("Enter a final fare greater than zero."); return;
    }
    submitLock.current = true;
    setBusy(true); setError(""); setNotice("");
    try {
      const assignmentBody = {
        driverId: assignmentOption.driverId, vehicleId: assignmentOption.vehicleId,
        scheduledEndAt, ...(bookingToVerify.hasAcceptedFare ? {} : { tripFare: finalFare }),
      };
      if (bookingToVerify.status === "Confirmed") {
        // Repair an already confirmed legacy booking without reconfirming it.
        await api.post("/assignments", { ...assignmentBody, bookingId: bookingToVerify.id });
      } else {
        await api.post(`/bookings/${bookingToVerify.id}/confirm-and-assign`, assignmentBody);
      }
      setBookingToVerify(null);
      setNotice("Booking confirmed and assignment saved.");
      window.dispatchEvent(new Event("diladrive:assignments-changed"));
    } catch (failure) {
      setError(confirmationError(failure));
    } finally {
      submitLock.current = false;
      setBusy(false);
    }
  };

  const reserveSuggestedAssignment = async (option) => {
    if (!bookingToVerify || submitLock.current) return false;
    submitLock.current = true;
    setBusy(true);
    setError("");
    try {
      await api.put(`/bookings/${bookingToVerify.id}/suggested-assignment`, {
        driverId: option.driverId, vehicleId: option.vehicleId, scheduledEndAt,
      });
      return true;
    } catch (failure) {
      setError(confirmationError(failure));
      return false;
    } finally {
      submitLock.current = false;
      setBusy(false);
    }
  };

  const openAssignment = (booking) => {
    setError("");
    if (booking.roadDistancePending) {
      setSelectedBooking(booking);
      return;
    }
    if (!booking.assignmentId && ["Confirmed", "Pending Verification"].includes(booking.status)) {
      setAssignmentOption(null);
      setBookingToVerify(booking);
      setScheduledEndAt(booking.reservedEndAt?.slice(0, 19) || "");
      setFareInput(Number(booking.fare) > 0 ? String(booking.fare) : "");
    } else onNavigate?.("Assignments");
  };

  const getBookingStyle = (status) => {
    const styles = {
      "Pending Verification":
        "bg-amber-50 text-amber-700 ring-amber-600/20",
      Confirmed:
        "bg-emerald-50 text-emerald-700 ring-emerald-600/20",
      Verified:
        "bg-emerald-50 text-emerald-700 ring-emerald-600/20",
      Completed:
        "bg-slate-100 text-slate-600 ring-slate-500/20",
      Cancelled:
        "bg-rose-50 text-rose-700 ring-rose-600/20",
    };

    return styles[status] ||
      "bg-slate-100 text-slate-600 ring-slate-500/20";
  };

  const getTripStyle = (status) => {
    if (status === "COMPLETED") {
      return "bg-emerald-50 text-emerald-700";
    }

    if (status === "OTW" || status === "PICKUP") {
      return "bg-blue-50 text-blue-700";
    }

    if (status === "WAITING" || status === "DROP") {
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
    <div className="admin-page">
      {loading && <p role="status">Loading bookings...</p>}
      {loadError && <ErrorMessage>{loadError} <button type="button" onClick={fetchBookings}>Retry</button></ErrorMessage>}
      {notice && <p role="status" className="rounded-xl bg-emerald-50 p-4 text-emerald-800">{notice}</p>}

      {/* Header */}
      <header className="admin-page-header">
        <div>
          <p className="mb-2 text-sm font-medium text-teal-700">
            DilaDrive / Operations
          </p>

          <h1 className="admin-page-title">
            Booking Management
          </h1>

          <p className="admin-page-description">
            Review customer trip requests, confirm booking
            availability, and manage driver and vehicle assignments.
          </p>
        </div>
        <button type="button" disabled={loading || busy} onClick={fetchBookings} className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm font-semibold text-slate-700 disabled:opacity-50"><RefreshCw size={17} />Refresh</button>
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
                <th className="px-5 py-4 font-semibold">No.</th>
                <th className="px-5 py-4 font-semibold">Customer</th>
                <th className="px-5 py-4 font-semibold">Trip Route</th>
                <th className="px-5 py-4 font-semibold">Schedule</th>
                <th className="px-5 py-4 font-semibold">Fare</th>
                <th className="px-5 py-4 font-semibold">Status</th>
                <th className="px-5 py-4 text-right font-semibold">Actions</th>
              </tr>
            </thead>

            <tbody className="divide-y divide-slate-100">
              {filteredBookings.map((booking, index) => {
                const status =
                  booking.status || "Pending Verification";

                const assigned =
                  Boolean(booking.assignmentId);

                return (
                  <tr
                    key={booking.id}
                    className="transition hover:bg-slate-50/80"
                  >
                    <td className="px-5 py-4 text-sm font-medium text-slate-500">
                      {index + 1}
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

                        {status === "Pending Verification" && !booking.assignmentId && !booking.roadDistancePending && (
                          <button
                            type="button"
                            onClick={() => {
                              setAssignmentOption(null);
      setBookingToVerify(booking);
      setScheduledEndAt(booking.reservedEndAt?.slice(0, 19) || "");
                              setFareInput(
                                Number.isFinite(Number(booking.fare)) &&
                                  Number(booking.fare) > 0
                                  ? String(booking.fare)
                                  : "",
                              );
                              setError("");
                            }}
                            className="inline-flex items-center gap-1.5 rounded-lg bg-emerald-600 px-3 py-2 text-sm font-medium text-white hover:bg-emerald-700"
                          >
                            <CheckCircle2 size={15} />
                            Verify Booking
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
                            {assigned ? "View / Reassign" : "Complete assignment"}
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
                Booking Status
              </p>
              <span
                className={`mt-3 inline-flex rounded-full px-3 py-1 text-xs font-semibold ${getBookingStyle(
                  selectedBooking.status || "Pending Verification"
                )}`}
              >
                {selectedBooking.status || "Pending Verification"}
              </span>
              {selectedBooking.roadDistancePending && (
                <p className="mt-2 text-sm text-amber-800">Confirm the measured road distance before assigning this booking.</p>
              )}
            </div>

            <Detail label="Customer" value={selectedBooking.customer} />
            <Detail label="Contact Number" value={selectedBooking.phone} />
            <Detail label="Pickup Location" value={selectedBooking.pickup} />
            <Detail label="Destination" value={selectedBooking.dropoff} />
            <Detail label="Booking Date" value={formatDate(selectedBooking.date)} />
            <Detail label="Pickup Time / Landing Time" value={selectedBooking.time || selectedBooking.pickupTime} />
            <Detail label="Flight Number" value={selectedBooking.flight} />
            <Detail label="Passengers" value={selectedBooking.passengers} />
            <TripCharges key={selectedBooking.id} bookingId={selectedBooking.id}
              allowDistance={selectedBooking.status === "Pending Verification"}
              onDistanceConfirmed={fetchBookings}
              canEditCharges={["Confirmed", "In Progress", "Completed"].includes(selectedBooking.status)} />
            <Detail label="Luggage Count" value={selectedBooking.luggageCount} />
                         <Detail label="Luggage Size" value={selectedBooking.luggageSize} />
             {selectedBooking.smallCount != null && <>
               <Detail label="Small / medium / large" value={`${selectedBooking.smallCount} / ${selectedBooking.mediumCount} / ${selectedBooking.largeCount}`} />
               <Detail label="Boot usage (luggage units)" value={selectedBooking.luggageUnits} />
               <Detail label="Estimated travel minutes" value={Math.ceil(selectedBooking.durationSeconds / 60)} />
             </>}
             {selectedBooking.status === "Pending Verification" && <button type="button" disabled={busy} className="rounded-xl border border-rose-300 px-4 py-2 text-rose-700" onClick={async () => {
               const reason = window.prompt("Reason for rejecting this booking:");
               if (!reason?.trim()) return;
               setBusy(true);
               try { await api.post(`/bookings/${selectedBooking.id}/reject`, { reason: reason.trim() }); await fetchBookings(); }
               catch (e) { setError(e.response?.data?.message || "Could not reject booking."); }
               finally { setBusy(false); }
             }}>Reject booking</button>}
            <Detail label="Vehicle Type" value={selectedBooking.type} />
            <Detail label="Trip Type" value={selectedBooking.tripType} />
            <Detail label="Additional Notes" value={selectedBooking.notes} />
            <Detail label="Trip Fare" value={money(selectedBooking.fare)} />
            {selectedBooking.legacyRelationship && <p role="alert" className="rounded-xl bg-amber-50 p-3 text-sm text-amber-900">Legacy driver/vehicle references exist without an assignment. They are not a confirmed allocation. Review the resources before completing the assignment.</p>}
            <Detail label="Assigned Driver" value={selectedBooking.driver || "Not assigned"} />
            <Detail label="Assigned Vehicle" value={selectedBooking.vehicle || "Not assigned"} />
            <Detail label="Trip Status" value={selectedBooking.tripStatus || "Not assigned"} />

            {selectedBooking.status === "Pending Verification" && !selectedBooking.assignmentId && !selectedBooking.roadDistancePending && (
              <button
                type="button"
                onClick={() => {
                  setBookingToVerify(selectedBooking);
                  setFareInput(
                    Number.isFinite(Number(selectedBooking.fare)) &&
                      Number(selectedBooking.fare) > 0
                      ? String(selectedBooking.fare)
                      : "",
                  );
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
                  if (!selectedBooking.assignmentId) {
                    setBookingToVerify(selectedBooking);
                    setFareInput(String(selectedBooking.fare || ""));
                  } else openAssignment(selectedBooking);
                  setSelectedBooking(null);
                }}
                className="inline-flex w-full items-center justify-center gap-2 rounded-xl bg-teal-600 px-4 py-3 font-semibold text-white hover:bg-teal-700"
              >
                <Truck size={18} />
                {selectedBooking.assignmentId ? "View / Reassign Trip" : "Complete missing assignment"}
              </button>
            )}
          </div>
        </Modal>
      )}

      {/* Verify confirmation */}
      {bookingToVerify && (
        <Modal
          title={bookingToVerify.status === "Confirmed" ? "Complete missing assignment" : "Verify Booking"}
          subtitle="Check availability before confirming this request."
          onClose={() => {
            if (busy) return;
            setBookingToVerify(null);
            setError("");
          }}
          icon={<ShieldCheck size={21} />}
        >
          <div className="space-y-4">
            <div className="grid gap-x-4 rounded-xl bg-slate-50 p-4 sm:grid-cols-2">
              <Detail label="Booking" value={`BK-${String(bookingToVerify.id).padStart(6, "0")}`} />
              <Detail label="Customer" value={bookingToVerify.customer} />
              <Detail label="Phone" value={bookingToVerify.phone || "Not provided"} />
              <Detail label="Customer category" value={bookingToVerify.customerCategory || "Not recorded"} />
              <Detail label="Selected category" value={bookingToVerify.type || "Not recorded"} />
              <Detail label="Estimated distance" value={bookingToVerify.distanceKm == null ? "Not recorded" : `${bookingToVerify.distanceKm} km`} />
              {bookingToVerify.flight && <Detail label="Flight" value={bookingToVerify.flight} />}
              <Detail label="Pickup date" value={formatDate(bookingToVerify.date)} />
              <Detail label="Pickup time (Malaysia)" value={bookingToVerify.pickupTime} />
              <Detail label="Pickup" value={bookingToVerify.pickup || "N/A"} />
              <Detail label="Destination" value={bookingToVerify.dropoff || "N/A"} />
              <Detail label="Passengers" value={bookingToVerify.passengers || 1} />
              <Detail label="Boot usage (luggage units)" value={bookingToVerify.luggageUnits ?? "Not recorded"} />
              <Detail label="Estimated fare" value={bookingToVerify.fare == null ? "Not recorded" : `RM ${Number(bookingToVerify.fare).toFixed(2)}`} />
              <Detail label="Bag counts (small / medium / large)" value={bookingToVerify.smallCount == null
                ? "Not recorded"
                : `${bookingToVerify.smallCount} / ${bookingToVerify.mediumCount ?? 0} / ${bookingToVerify.largeCount ?? 0}`} />
              {bookingToVerify.durationSeconds > 0 && <Detail label="Estimated drive"
                value={assignmentDuration(bookingToVerify.durationSeconds)} />}
            </div>
            <label className="block space-y-2 text-sm font-medium text-slate-700">
              Scheduled trip end time
              <input type="datetime-local" step="1" required disabled={busy} value={scheduledEndAt} min={(bookingToVerify.date + "T" + bookingToVerify.pickupTime).slice(0,16)} onChange={e => { setAssignmentOption(null); setScheduledEndAt(e.target.value); }} className="w-full rounded-xl border border-slate-300 p-3" />
              <span className="block text-xs font-normal text-slate-500">Include driving duration and the configured buffer. Confirmed active assignments with an unknown end need schedule correction before resource reuse.</span>
            </label>

            <AssignmentResourcePicker bookingId={bookingToVerify.id} scheduledEndAt={scheduledEndAt}
              onChange={setAssignmentOption} onSuggestedEnd={setScheduledEndAt} disabled={busy}
              onReserve={bookingToVerify.status === "Pending Verification" ? reserveSuggestedAssignment : undefined} />

            {!bookingToVerify.hasAcceptedFare && (!Number.isFinite(Number(bookingToVerify.fare)) ||
              Number(bookingToVerify.fare) <= 0) && (
              <label className="block space-y-2 text-sm font-medium text-slate-700">
                Final fare (RM)
                <input
                  type="number"
                  min="0.01"
                  step="0.01"
                  inputMode="decimal"
                  value={fareInput}
                  onChange={(event) => setFareInput(event.target.value)}
                  className="w-full rounded-xl border border-slate-300 px-3 py-2.5 font-normal outline-none focus:border-sky-500 focus:ring-2 focus:ring-sky-100"
                  placeholder="Enter the approved amount"
                />
                <span className="block text-xs font-normal text-slate-500">
                  No configured fare estimate is available. Enter the amount approved by DilaDrive.
                </span>
              </label>
            )}

            {error && <ErrorMessage>{error}</ErrorMessage>}

            <div className="sticky bottom-0 z-10 flex flex-col-reverse gap-3 border-t bg-white py-3 sm:flex-row sm:justify-end">
              <button
                type="button"
                onClick={() => {
                  if (busy) return;
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
                disabled={busy || loading || !assignmentOption}
                className="inline-flex items-center justify-center gap-2 rounded-xl bg-emerald-600 px-5 py-2.5 font-semibold text-white hover:bg-emerald-700"
              >
                <CheckCircle2 size={17} />
                {busy ? "Saving..." : "Verify Booking"}
              </button>
            </div>
          </div>
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
  useModalScrollLock();

  return (
    <div
      className="fixed inset-0 !m-0 z-50 flex items-center justify-center overflow-hidden bg-slate-950/50 p-4 backdrop-blur-sm"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className="modal-panel flex w-full max-w-xl flex-col overflow-hidden rounded-2xl bg-white shadow-2xl"
      >
        <div className="flex shrink-0 items-start justify-between border-b border-slate-100 p-5 sm:p-6">
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

        <div className="min-h-0 overflow-y-auto overscroll-contain p-5 sm:p-6">
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
