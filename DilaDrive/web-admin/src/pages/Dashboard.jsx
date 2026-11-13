import { useEffect, useState } from "react";
import axios from "axios";
import api from "../services/api";
import {
  ArrowRight,
  CalendarDays,
  CheckCircle2,
  ClipboardList,
  Clock,
  Truck,
  Users,
} from "lucide-react";

import DataTable from "../components/DataTable";

const emptyStats = {
  totalBookings: null,
  pendingBookings: null,
  activeDrivers: null,
  availableVehicles: null,
  activeAssignments: null,
  completedTrips: null,
};

const getApiError = (error) => {
  const response = error.response?.data;
  return (
    (typeof response === "string" ? response : response?.message || response?.detail) ||
    error.message ||
    "Unable to load dashboard data."
  );
};

const formatDate = (value) => {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "—";
  return date.toLocaleDateString("en-MY", {
    day: "2-digit",
    month: "short",
    year: "numeric",
  });
};

const formatCurrency = (value) => {
  if (value == null || value === "") return "—";
  return new Intl.NumberFormat("en-MY", {
    style: "currency",
    currency: "MYR",
  }).format(Number(value));
};

const formatStatus = (value) =>
  String(value || "—")
    .trim()
    .toLowerCase()
    .split(/[_\s]+/)
    .filter(Boolean)
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ");

const getBookingDate = (booking) =>
  booking.bookingTripdate || booking.tripDate;

const getSortDate = (booking) => {
  const value = booking.bookingCreatedat || booking.bookingTripdate;
  const timestamp = new Date(value).getTime();
  return Number.isNaN(timestamp) ? 0 : timestamp;
};

export default function Dashboard({ onNavigate }) {
  const [stats, setStats] = useState(emptyStats);
  const [recentBookings, setRecentBookings] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError("");

    Promise.all([
      api.get("/dashboard/stats", { signal: controller.signal }),
      api.get("/bookings", { signal: controller.signal }),
      api.get("/customers", { signal: controller.signal }),
    ])
      .then(([statsResponse, bookingsResponse, customersResponse]) => {
        const customers = Array.isArray(customersResponse.data)
          ? customersResponse.data
          : [];
        const customerNames = new Map(
          customers.map((customer) => [
            String(customer.custId),
            customer.custName,
          ])
        );
        const bookings = Array.isArray(bookingsResponse.data)
          ? bookingsResponse.data
          : [];

        setStats({
          totalBookings: statsResponse.data.totalBookings,
          pendingBookings: statsResponse.data.pendingBookings,
          activeDrivers: statsResponse.data.activeDrivers,
          availableVehicles: statsResponse.data.availableVehicles,
          activeAssignments: statsResponse.data.activeAssignments,
          completedTrips: statsResponse.data.completedTrips,
        });
        setRecentBookings(
          bookings
            .map((booking) => ({
              customer:
                booking.custName ||
                booking.customerName ||
                customerNames.get(String(booking.custId)) ||
                "—",
              route: `${booking.bookingPickupaddress || "—"} → ${
                booking.bookingDestinationaddress || "—"
              }`,
              date: formatDate(getBookingDate(booking)),
              status: formatStatus(booking.bookingStatus),
              amount: formatCurrency(booking.bookingTripfare),
              sortDate: getSortDate(booking),
            }))
            .sort((left, right) => right.sortDate - left.sortDate)
            .slice(0, 5)
        );
      })
      .catch((requestError) => {
        if (!axios.isCancel(requestError)) setError(getApiError(requestError));
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, []);

  const cards = [
    {
      title: "Total Bookings",
      value: stats.totalBookings,
      subtitle: "All recorded bookings",
      icon: CalendarDays,
      color: "text-teal-700",
      bg: "bg-teal-50",
    },
    {
      title: "Pending Bookings",
      value: stats.pendingBookings,
      subtitle: "Awaiting confirmation",
      icon: Clock,
      color: "text-amber-600",
      bg: "bg-amber-50",
    },
    {
      title: "Active Drivers",
      value: stats.activeDrivers,
      subtitle: "Active driver accounts",
      icon: Truck,
      color: "text-blue-600",
      bg: "bg-blue-50",
    },
    {
      title: "Available Vehicles",
      value: stats.availableVehicles,
      subtitle: "Ready for assignment",
      icon: Users,
      color: "text-violet-600",
      bg: "bg-violet-50",
    },
    {
      title: "Active Assignments",
      value: stats.activeAssignments,
      subtitle: "Trips currently in progress",
      icon: ClipboardList,
      color: "text-indigo-600",
      bg: "bg-indigo-50",
    },
    {
      title: "Completed Trips",
      value: stats.completedTrips,
      subtitle: "Completed assignments",
      icon: CheckCircle2,
      color: "text-emerald-600",
      bg: "bg-emerald-50",
    },
  ];

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">Dashboard</h1>
          <p className="admin-page-description">
            Welcome to DilaDrive. Here is your business overview.
          </p>
        </div>

        <button
          type="button"
          onClick={() => onNavigate?.("Bookings")}
          className="inline-flex items-center justify-center gap-2 rounded-xl bg-teal-700 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-teal-800"
        >
          <ClipboardList size={17} />
          Manage Bookings
        </button>
      </div>

      {error && (
        <div
          role="alert"
          className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700"
        >
          {error}
        </div>
      )}

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-3">
        {cards.map((card) => {
          const Icon = card.icon;
          return (
            <div
              key={card.title}
              className="rounded-2xl bg-white p-5 shadow-sm transition hover:shadow-md"
            >
              <div className="flex items-center justify-between">
                <p className="text-sm font-medium text-gray-500">{card.title}</p>
                <div className={`rounded-xl p-2.5 ${card.bg}`}>
                  <Icon size={21} className={card.color} />
                </div>
              </div>
              <h2 className="mt-4 text-2xl font-bold text-gray-800">
                {loading ? "…" : card.value ?? "—"}
              </h2>
              <p className="mt-1 text-xs text-gray-400">{card.subtitle}</p>
            </div>
          );
        })}
      </div>

      <section className="overflow-hidden rounded-2xl bg-white shadow-sm">
        <div className="flex flex-col gap-3 border-b border-gray-100 px-5 py-5 sm:flex-row sm:items-center sm:justify-between sm:px-6">
          <div>
            <h2 className="text-lg font-bold text-gray-800">Recent Bookings</h2>
            <p className="mt-1 text-sm text-gray-500">
              Latest booking records from the system.
            </p>
          </div>

          <button
            type="button"
            onClick={() => onNavigate?.("Bookings")}
            className="inline-flex items-center gap-1 self-start text-sm font-semibold text-teal-700 transition hover:text-teal-900 sm:self-center"
          >
            View All
            <ArrowRight size={16} />
          </button>
        </div>

        <div className="p-4 sm:p-6">
          {loading ? (
            <p className="py-12 text-center text-sm text-gray-500">
              Loading recent bookings…
            </p>
          ) : recentBookings.length > 0 ? (
            <DataTable
              rows={recentBookings}
              columns={[
                { key: "customer", label: "Customer" },
                { key: "route", label: "Route" },
                { key: "date", label: "Trip Date" },
                { key: "status", label: "Status" },
                { key: "amount", label: "Fare" },
              ]}
            />
          ) : (
            <div className="flex flex-col items-center justify-center py-12 text-center">
              <div className="rounded-full bg-teal-50 p-4">
                <CalendarDays size={28} className="text-teal-700" />
              </div>
              <h3 className="mt-4 font-semibold text-gray-800">
                {error ? "Bookings are unavailable" : "No bookings available"}
              </h3>
              {!error && (
                <p className="mt-1 max-w-sm text-sm text-gray-500">
                  Booking records will appear here when available.
                </p>
              )}
              <button
                type="button"
                onClick={() => onNavigate?.("Bookings")}
                className="mt-4 text-sm font-semibold text-teal-700 hover:text-teal-900"
              >
                Go to Bookings
              </button>
            </div>
          )}
        </div>

        {!loading && recentBookings.length > 0 && (
          <div className="border-t border-gray-100 px-5 py-3 text-center sm:text-right">
            <button
              type="button"
              onClick={() => onNavigate?.("Bookings")}
              className="text-sm font-medium text-gray-500 hover:text-teal-700"
            >
              Showing {recentBookings.length} latest bookings
            </button>
          </div>
        )}
      </section>
    </div>
  );
}
