import { useEffect, useState } from "react";
import axios from "axios";
import {
  CalendarDays,
  Clock,
  Truck,
  Wallet,
  ArrowRight,
  ClipboardList,
} from "lucide-react";

import DataTable from "../components/DataTable";

export default function Dashboard({ bookings = [], onNavigate }) {
  const pendingBookings = bookings.filter(
    (booking) => booking.status === "Pending"
  ).length;

  const [statsData, setStatsData] = useState({
  customers: 0,
  drivers: 0,
  vehicles: 0,
  bookings: 0,
});
useEffect(() => {
  fetchDashboardStats();
}, []);

const fetchDashboardStats = async () => {
  try {

    const response = await axios.get(
      "http://localhost:8080/api/dashboard/stats"
    );

    setStatsData(response.data);

  } catch (error) {

    console.error(error);

  }
};
  const stats = [
    {
      title: "Total Bookings",
      value: "128",
      subtitle: "All recorded bookings",
      icon: CalendarDays,
      color: "text-teal-700",
      bg: "bg-teal-50",
    },
    {
      title: "Pending Approval",
      value: pendingBookings,
      subtitle: "Require your attention",
      icon: Clock,
      color: "text-amber-600",
      bg: "bg-amber-50",
    },
    {
      title: "Active Drivers",
      value: "18",
      subtitle: "Registered active drivers",
      icon: Truck,
      color: "text-blue-600",
      bg: "bg-blue-50",
    },
    {
      title: "Revenue (Demo)",
      value: "RM 24.8k",
      subtitle: "Demo revenue overview",
      icon: Wallet,
      color: "text-purple-600",
      bg: "bg-purple-50",
    },
  ];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-800">
            Dashboard
          </h1>
          <p className="mt-1 text-sm text-gray-500">
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

      {/* Statistics */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {stats.map((stat) => {
          const Icon = stat.icon;

          return (
            <div
              key={stat.title}
              className="rounded-2xl bg-white p-5 shadow-sm transition hover:shadow-md"
            >
              <div className="flex items-center justify-between">
                <p className="text-sm font-medium text-gray-500">
                  {stat.title}
                </p>

                <div className={`rounded-xl p-2.5 ${stat.bg}`}>
                  <Icon size={21} className={stat.color} />
                </div>
              </div>

              <h2 className="mt-4 text-2xl font-bold text-gray-800">
                {stat.value}
              </h2>

              <p className="mt-1 text-xs text-gray-400">
                {stat.subtitle}
              </p>
            </div>
          );
        })}
      </div>

      {/* Recent Bookings */}
      <section className="overflow-hidden rounded-2xl bg-white shadow-sm">
        <div className="flex flex-col gap-3 border-b border-gray-100 px-5 py-5 sm:flex-row sm:items-center sm:justify-between sm:px-6">
          <div>
            <h2 className="text-lg font-bold text-gray-800">
              Recent Bookings
            </h2>
            <p className="mt-1 text-sm text-gray-500">
              Review and manage the latest booking records.
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
          {bookings.length > 0 ? (
            <DataTable
              rows={bookings.slice(0, 5)}
              columns={[
                { key: "id", label: "Booking ID" },
                { key: "customer", label: "Customer" },
                { key: "route", label: "Route" },
                { key: "date", label: "Date" },
                { key: "status", label: "Status" },
                { key: "amount", label: "Amount" },
              ]}
            />
          ) : (
            <div className="flex flex-col items-center justify-center py-12 text-center">
              <div className="rounded-full bg-teal-50 p-4">
                <CalendarDays size={28} className="text-teal-700" />
              </div>

              <h3 className="mt-4 font-semibold text-gray-800">
                No bookings available
              </h3>

              <p className="mt-1 max-w-sm text-sm text-gray-500">
                New booking records will appear here when they are available.
              </p>

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

        {bookings.length > 5 && (
          <div className="border-t border-gray-100 px-5 py-3 text-center sm:text-right">
            <button
              type="button"
              onClick={() => onNavigate?.("Bookings")}
              className="text-sm font-medium text-gray-500 hover:text-teal-700"
            >
              Showing 5 of {bookings.length} bookings
            </button>
          </div>
        )}
      </section>
    </div>
  );
}