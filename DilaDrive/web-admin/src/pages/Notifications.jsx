import { useState } from "react";
import {
  Search,
  Eye,
  Bell,
  Trash2,
  CheckCircle,
  X,
  CheckCheck,
  CalendarDays,
  CreditCard,
  Truck,
  Info,
  Filter,
  Clock,
} from "lucide-react";

const initialNotifications = [
  {
    id: "NOT-001",
    title: "New Booking Request",
    message: "Customer submitted Booking DD-1028.",
    date: "21 Oct 2026",
    time: "09:30 AM",
    status: "Unread",
    type: "Booking",
  },
  {
    id: "NOT-002",
    title: "Driver Assigned",
    message: "Driver Farid assigned to DD-1027.",
    date: "20 Oct 2026",
    time: "03:15 PM",
    status: "Read",
    type: "Driver",
  },
  {
    id: "NOT-003",
    title: "Payment Submitted",
    message: "Payment PAY-001 awaiting verification.",
    date: "20 Oct 2026",
    time: "11:45 AM",
    status: "Unread",
    type: "Payment",
  },
];

const getNotificationIcon = (type) => {
  switch (type) {
    case "Booking":
      return CalendarDays;
    case "Driver":
      return Truck;
    case "Payment":
      return CreditCard;
    default:
      return Info;
  }
};

const getNotificationColor = (type) => {
  switch (type) {
    case "Booking":
      return "bg-blue-50 text-blue-600";
    case "Driver":
      return "bg-purple-50 text-purple-600";
    case "Payment":
      return "bg-amber-50 text-amber-600";
    default:
      return "bg-slate-100 text-slate-600";
  }
};

export default function Notifications() {
  const [notifications, setNotifications] =
    useState(initialNotifications);

  const [search, setSearch] = useState("");
  const [filterStatus, setFilterStatus] = useState("All");
  const [selectedNotification, setSelectedNotification] =
    useState(null);

  const [showCreateForm, setShowCreateForm] =
    useState(false);

  const [newNotification, setNewNotification] = useState({
    title: "",
    message: "",
    type: "General",
  });

  const unreadCount = notifications.filter(
    (notification) => notification.status === "Unread"
  ).length;

  const readCount = notifications.filter(
    (notification) => notification.status === "Read"
  ).length;

  const filteredNotifications = notifications.filter(
    (notification) => {
      const searchText = search.toLowerCase();

      const matchesSearch =
        notification.id.toLowerCase().includes(searchText) ||
        notification.title.toLowerCase().includes(searchText) ||
        notification.message.toLowerCase().includes(searchText) ||
        notification.type.toLowerCase().includes(searchText);

      const matchesStatus =
        filterStatus === "All" ||
        notification.status === filterStatus;

      return matchesSearch && matchesStatus;
    }
  );

  const handleMarkRead = (id) => {
    setNotifications((previous) =>
      previous.map((notification) =>
        notification.id === id
          ? { ...notification, status: "Read" }
          : notification
      )
    );

    setSelectedNotification((previous) =>
      previous && previous.id === id
        ? { ...previous, status: "Read" }
        : previous
    );
  };

  const handleMarkAllRead = () => {
    setNotifications((previous) =>
      previous.map((notification) => ({
        ...notification,
        status: "Read",
      }))
    );

    setSelectedNotification((previous) =>
      previous ? { ...previous, status: "Read" } : null
    );
  };

  const handleView = (notification) => {
    // Automatically mark as read when opened.
    handleMarkRead(notification.id);

    setSelectedNotification({
      ...notification,
      status: "Read",
    });
  };

  const handleDelete = (id) => {
    const confirmed = window.confirm(
      "Are you sure you want to delete this notification?"
    );

    if (!confirmed) return;

    setNotifications((previous) =>
      previous.filter((notification) => notification.id !== id)
    );

    setSelectedNotification((previous) =>
      previous?.id === id ? null : previous
    );
  };

  const handleCreateNotification = (event) => {
    event.preventDefault();

    if (
      !newNotification.title.trim() ||
      !newNotification.message.trim()
    ) {
      alert("Please fill in the title and message.");
      return;
    }

    const nextNumber =
      notifications.reduce((max, notification) => {
        const number = Number(notification.id.split("-")[1]);
        return Math.max(max, number || 0);
      }, 0) + 1;

    const now = new Date();

    const createdNotification = {
      id: `NOT-${String(nextNumber).padStart(3, "0")}`,
      title: newNotification.title.trim(),
      message: newNotification.message.trim(),
      date: now.toLocaleDateString("en-GB", {
        day: "2-digit",
        month: "short",
        year: "numeric",
      }),
      time: now.toLocaleTimeString("en-US", {
        hour: "2-digit",
        minute: "2-digit",
      }),
      status: "Unread",
      type: newNotification.type,
    };

    setNotifications((previous) => [
      createdNotification,
      ...previous,
    ]);

    setSearch("");
    setFilterStatus("All");

    setNewNotification({
      title: "",
      message: "",
      type: "General",
    });

    setShowCreateForm(false);
  };

  return (
    <div className="min-h-screen space-y-6 bg-slate-50/70 p-4 md:p-7">

      {/* Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <div className="flex items-center gap-3">
            <div className="rounded-2xl bg-teal-100 p-3 text-teal-700">
              <Bell size={25} />
            </div>

            <div>
              <h1 className="text-2xl font-bold text-slate-800 md:text-3xl">
                Notifications
              </h1>

              <p className="mt-1 text-sm text-slate-500">
                Monitor booking, driver and payment activities.
              </p>
            </div>
          </div>
        </div>

        <div className="flex flex-wrap gap-2">
          <button
            onClick={handleMarkAllRead}
            disabled={unreadCount === 0}
            className="inline-flex items-center justify-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2.5 text-sm font-medium text-slate-700 transition hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-50"
          >
            <CheckCheck size={17} />
            Mark All as Read
          </button>

          <button
            onClick={() => setShowCreateForm(true)}
            className="inline-flex items-center justify-center gap-2 rounded-xl bg-teal-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-teal-700"
          >
            <Bell size={17} />
            Add Notification
          </button>
        </div>
      </div>

      {/* Statistics */}
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">

        <div className="rounded-2xl border border-slate-100 bg-white p-5 shadow-sm">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-slate-500">
                Total Notifications
              </p>

              <h2 className="mt-2 text-3xl font-bold text-slate-800">
                {notifications.length}
              </h2>

              <p className="mt-1 text-xs text-slate-400">
                All system alerts
              </p>
            </div>

            <div className="rounded-xl bg-blue-50 p-3 text-blue-600">
              <Bell size={23} />
            </div>
          </div>
        </div>

        <div className="rounded-2xl border border-slate-100 bg-white p-5 shadow-sm">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-slate-500">
                Unread
              </p>

              <h2 className="mt-2 text-3xl font-bold text-orange-500">
                {unreadCount}
              </h2>

              <p className="mt-1 text-xs text-slate-400">
                Require your attention
              </p>
            </div>

            <div className="rounded-xl bg-orange-50 p-3 text-orange-600">
              <Clock size={23} />
            </div>
          </div>
        </div>

        <div className="rounded-2xl border border-slate-100 bg-white p-5 shadow-sm">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium text-slate-500">
                Read
              </p>

              <h2 className="mt-2 text-3xl font-bold text-green-600">
                {readCount}
              </h2>

              <p className="mt-1 text-xs text-slate-400">
                Already reviewed
              </p>
            </div>

            <div className="rounded-xl bg-green-50 p-3 text-green-600">
              <CheckCircle size={23} />
            </div>
          </div>
        </div>

      </div>

      {/* Notification Records */}
      <div className="overflow-hidden rounded-2xl border border-slate-100 bg-white shadow-sm">

        {/* Search and Filter */}
        <div className="border-b border-slate-100 p-5 md:p-6">
          <div className="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">

            <div>
              <h2 className="text-lg font-bold text-slate-800">
                Notification Records
              </h2>

              <p className="mt-1 text-sm text-slate-500">
                View and manage all system notifications.
              </p>
            </div>

            <div className="flex flex-col gap-3 sm:flex-row">

              <div className="relative">
                <Search
                  size={18}
                  className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
                />

                <input
                  type="text"
                  placeholder="Search notifications..."
                  value={search}
                  onChange={(event) =>
                    setSearch(event.target.value)
                  }
                  className="w-full rounded-xl border border-slate-200 bg-white py-2.5 pl-10 pr-4 text-sm outline-none transition focus:border-teal-500 focus:ring-2 focus:ring-teal-100 sm:w-64"
                />
              </div>

              <div className="relative">
                <Filter
                  size={16}
                  className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-slate-400"
                />

                <select
                  value={filterStatus}
                  onChange={(event) =>
                    setFilterStatus(event.target.value)
                  }
                  className="w-full appearance-none rounded-xl border border-slate-200 bg-white py-2.5 pl-9 pr-9 text-sm outline-none transition focus:border-teal-500 sm:w-40"
                >
                  <option value="All">All Status</option>
                  <option value="Unread">Unread</option>
                  <option value="Read">Read</option>
                </select>
              </div>

            </div>
          </div>
        </div>

        {/* Table */}
        <div className="overflow-x-auto">
          <table className="min-w-[800px] w-full text-left">

            <thead className="bg-slate-50">
              <tr className="text-xs uppercase tracking-wider text-slate-500">
                <th className="px-6 py-4 font-semibold">
                  Notification
                </th>

                <th className="px-6 py-4 font-semibold">
                  Category
                </th>

                <th className="px-6 py-4 font-semibold">
                  Date & Time
                </th>

                <th className="px-6 py-4 font-semibold">
                  Status
                </th>

                <th className="px-6 py-4 text-center font-semibold">
                  Actions
                </th>
              </tr>
            </thead>

            <tbody className="divide-y divide-slate-100">

              {filteredNotifications.map((notification) => {
                const Icon = getNotificationIcon(
                  notification.type
                );

                return (
                  <tr
                    key={notification.id}
                    className={`transition hover:bg-slate-50 ${
                      notification.status === "Unread"
                        ? "bg-teal-50/30"
                        : ""
                    }`}
                  >
                    <td className="px-6 py-5">
                      <div className="flex items-start gap-3">
                        <div
                          className={`mt-1 rounded-xl p-2.5 ${getNotificationColor(
                            notification.type
                          )}`}
                        >
                          <Icon size={19} />
                        </div>

                        <div className="min-w-0">
                          <div className="flex flex-wrap items-center gap-2">
                            <p className="font-semibold text-slate-800">
                              {notification.title}
                            </p>

                            {notification.status === "Unread" && (
                              <span className="h-2 w-2 rounded-full bg-teal-500" />
                            )}
                          </div>

                          <p className="mt-1 text-sm text-slate-500">
                            {notification.message}
                          </p>

                          <p className="mt-1 text-xs text-slate-400">
                            {notification.id}
                          </p>
                        </div>
                      </div>
                    </td>

                    <td className="px-6 py-5">
                      <span
                        className={`inline-flex rounded-lg px-2.5 py-1 text-xs font-medium ${getNotificationColor(
                          notification.type
                        )}`}
                      >
                        {notification.type}
                      </span>
                    </td>

                    <td className="px-6 py-5">
                      <p className="text-sm font-medium text-slate-700">
                        {notification.date}
                      </p>

                      <p className="mt-1 text-xs text-slate-400">
                        {notification.time}
                      </p>
                    </td>

                    <td className="px-6 py-5">
                      <span
                        className={`inline-flex rounded-full px-3 py-1 text-xs font-semibold ${
                          notification.status === "Unread"
                            ? "bg-orange-100 text-orange-700"
                            : "bg-green-100 text-green-700"
                        }`}
                      >
                        {notification.status}
                      </span>
                    </td>

                    <td className="px-6 py-5">
                      <div className="flex items-center justify-center gap-2">

                        <button
                          onClick={() => handleView(notification)}
                          title="View notification"
                          aria-label="View notification"
                          className="rounded-lg bg-blue-50 p-2 text-blue-600 transition hover:bg-blue-100"
                        >
                          <Eye size={17} />
                        </button>

                        {notification.status === "Unread" && (
                          <button
                            onClick={() =>
                              handleMarkRead(notification.id)
                            }
                            title="Mark as read"
                            aria-label="Mark as read"
                            className="rounded-lg bg-green-50 p-2 text-green-600 transition hover:bg-green-100"
                          >
                            <CheckCircle size={17} />
                          </button>
                        )}

                        <button
                          onClick={() =>
                            handleDelete(notification.id)
                          }
                          title="Delete notification"
                          aria-label="Delete notification"
                          className="rounded-lg bg-red-50 p-2 text-red-600 transition hover:bg-red-100"
                        >
                          <Trash2 size={17} />
                        </button>

                      </div>
                    </td>
                  </tr>
                );
              })}

            </tbody>
          </table>

          {/* Empty State */}
          {filteredNotifications.length === 0 && (
            <div className="flex flex-col items-center justify-center px-6 py-16 text-center">
              <div className="rounded-full bg-slate-100 p-5 text-slate-400">
                <Bell size={30} />
              </div>

              <h3 className="mt-4 font-semibold text-slate-800">
                No notifications found
              </h3>

              <p className="mt-1 max-w-sm text-sm text-slate-500">
                Try another search keyword or change the status filter.
              </p>

              {(search || filterStatus !== "All") && (
                <button
                  onClick={() => {
                    setSearch("");
                    setFilterStatus("All");
                  }}
                  className="mt-4 text-sm font-semibold text-teal-600 hover:text-teal-700"
                >
                  Clear search and filters
                </button>
              )}
            </div>
          )}

          {/* Footer */}
          <div className="border-t border-slate-100 px-6 py-4">
            <p className="text-sm text-slate-500">
              Showing{" "}
              <span className="font-semibold text-slate-700">
                {filteredNotifications.length}
              </span>{" "}
              of{" "}
              <span className="font-semibold text-slate-700">
                {notifications.length}
              </span>{" "}
              notifications
            </p>
          </div>
        </div>
      </div>

      {/* View Notification Modal */}
      {selectedNotification && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-sm"
          onClick={() => setSelectedNotification(null)}
        >
          <div
            className="w-full max-w-lg overflow-hidden rounded-2xl bg-white shadow-2xl"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="flex items-center justify-between border-b border-slate-100 p-5 md:p-6">
              <div>
                <h2 className="text-xl font-bold text-slate-800">
                  Notification Details
                </h2>

                <p className="mt-1 text-sm text-slate-500">
                  {selectedNotification.id}
                </p>
              </div>

              <button
                onClick={() => setSelectedNotification(null)}
                aria-label="Close modal"
                className="rounded-lg p-2 text-slate-400 transition hover:bg-slate-100 hover:text-slate-700"
              >
                <X size={21} />
              </button>
            </div>

            <div className="space-y-5 p-5 md:p-6">

              <div className="flex items-start gap-4">
                <div
                  className={`rounded-xl p-3 ${getNotificationColor(
                    selectedNotification.type
                  )}`}
                >
                  {(() => {
                    const Icon = getNotificationIcon(
                      selectedNotification.type
                    );

                    return <Icon size={24} />;
                  })()}
                </div>

                <div className="flex-1">
                  <h3 className="text-lg font-bold text-slate-800">
                    {selectedNotification.title}
                  </h3>

                  <p className="mt-2 text-sm leading-6 text-slate-600">
                    {selectedNotification.message}
                  </p>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className="rounded-xl bg-slate-50 p-4">
                  <p className="text-xs font-medium text-slate-500">
                    Category
                  </p>

                  <p className="mt-2 font-semibold text-slate-800">
                    {selectedNotification.type}
                  </p>
                </div>

                <div className="rounded-xl bg-slate-50 p-4">
                  <p className="text-xs font-medium text-slate-500">
                    Status
                  </p>

                  <span
                    className={`mt-2 inline-flex rounded-full px-3 py-1 text-xs font-semibold ${
                      selectedNotification.status === "Unread"
                        ? "bg-orange-100 text-orange-700"
                        : "bg-green-100 text-green-700"
                    }`}
                  >
                    {selectedNotification.status}
                  </span>
                </div>

                <div className="col-span-2 rounded-xl bg-slate-50 p-4">
                  <p className="text-xs font-medium text-slate-500">
                    Date & Time
                  </p>

                  <p className="mt-2 font-semibold text-slate-800">
                    {selectedNotification.date} at{" "}
                    {selectedNotification.time}
                  </p>
                </div>
              </div>
            </div>

            <div className="flex flex-col-reverse gap-3 border-t border-slate-100 p-5 sm:flex-row sm:justify-end md:px-6">
              {selectedNotification.status === "Unread" && (
                <button
                  onClick={() =>
                    handleMarkRead(selectedNotification.id)
                  }
                  className="inline-flex items-center justify-center gap-2 rounded-xl bg-teal-600 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-teal-700"
                >
                  <CheckCircle size={17} />
                  Mark as Read
                </button>
              )}

              <button
                onClick={() => setSelectedNotification(null)}
                className="rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-semibold text-slate-700 transition hover:bg-slate-50"
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Add Notification Modal */}
      {showCreateForm && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 p-4 backdrop-blur-sm"
          onClick={() => setShowCreateForm(false)}
        >
          <div
            className="w-full max-w-lg overflow-hidden rounded-2xl bg-white shadow-2xl"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="flex items-center justify-between border-b border-slate-100 p-5 md:p-6">
              <div>
                <h2 className="text-xl font-bold text-slate-800">
                  Add Notification
                </h2>

                <p className="mt-1 text-sm text-slate-500">
                  Create a notification for testing.
                </p>
              </div>

              <button
                onClick={() => setShowCreateForm(false)}
                aria-label="Close form"
                className="rounded-lg p-2 text-slate-400 transition hover:bg-slate-100"
              >
                <X size={21} />
              </button>
            </div>

            <form
              onSubmit={handleCreateNotification}
              className="space-y-5 p-5 md:p-6"
            >
              <div>
                <label className="mb-2 block text-sm font-semibold text-slate-700">
                  Notification Title
                </label>

                <input
                  type="text"
                  required
                  maxLength={100}
                  placeholder="e.g. New Booking Request"
                  value={newNotification.title}
                  onChange={(event) =>
                    setNewNotification((previous) => ({
                      ...previous,
                      title: event.target.value,
                    }))
                  }
                  className="w-full rounded-xl border border-slate-200 px-4 py-3 text-sm outline-none transition focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
                />
              </div>

              <div>
                <label className="mb-2 block text-sm font-semibold text-slate-700">
                  Category
                </label>

                <select
                  value={newNotification.type}
                  onChange={(event) =>
                    setNewNotification((previous) => ({
                      ...previous,
                      type: event.target.value,
                    }))
                  }
                  className="w-full rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm outline-none transition focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
                >
                  <option value="General">General</option>
                  <option value="Booking">Booking</option>
                  <option value="Driver">Driver</option>
                  <option value="Payment">Payment</option>
                </select>
              </div>

              <div>
                <label className="mb-2 block text-sm font-semibold text-slate-700">
                  Message
                </label>

                <textarea
                  required
                  maxLength={500}
                  rows={4}
                  placeholder="Enter notification details..."
                  value={newNotification.message}
                  onChange={(event) =>
                    setNewNotification((previous) => ({
                      ...previous,
                      message: event.target.value,
                    }))
                  }
                  className="w-full resize-none rounded-xl border border-slate-200 px-4 py-3 text-sm outline-none transition focus:border-teal-500 focus:ring-2 focus:ring-teal-100"
                />
              </div>

              <div className="flex flex-col-reverse gap-3 border-t border-slate-100 pt-5 sm:flex-row sm:justify-end">
                <button
                  type="button"
                  onClick={() => setShowCreateForm(false)}
                  className="rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-semibold text-slate-700 transition hover:bg-slate-50"
                >
                  Cancel
                </button>

                <button
                  type="submit"
                  className="rounded-xl bg-teal-600 px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-teal-700"
                >
                  Create Notification
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

    </div>
  );
}
