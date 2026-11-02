import { useState } from "react";
import {
  Search,
  Eye,
  Pencil,
  Plus,
  X,
  Users,
  UserCheck,
  Clock3,
  CarFront,
  Mail,
  Phone,
  ShieldCheck,
  SlidersHorizontal,
  Check,
  UserPlus,
  KeyRound,
  Ban,
  Power,
  LockKeyhole,
  CircleCheck,
} from "lucide-react";

const initialDrivers = [
  {
    id: "DRV-001",
    name: "Farid Ahmad",
    email: "farid@example.com",
    phone: "0123456789",
    vehicle: "VAN-1028",
    status: "Busy",
    accountStatus: "Active",
  },
  {
    id: "DRV-002",
    name: "Amir Hakim",
    email: "amir@example.com",
    phone: "0134567890",
    vehicle: "VAN-1029",
    status: "Available",
    accountStatus: "Active",
  },
  {
    id: "DRV-003",
    name: "Daniel Lee",
    email: "daniel@example.com",
    phone: "0145678901",
    vehicle: "",
    status: "Offline",
    accountStatus: "Pending Activation",
  },
];

const emptyDriver = {
  name: "",
  email: "",
  phone: "",
  vehicle: "",
  status: "Available",
};

const statusStyles = {
  Available: "bg-emerald-50 text-emerald-700",
  Busy: "bg-amber-50 text-amber-700",
  Offline: "bg-slate-100 text-slate-600",
};

const accountStyles = {
  Active: "bg-emerald-50 text-emerald-700",
  "Pending Activation": "bg-amber-50 text-amber-700",
  Disabled: "bg-red-50 text-red-700",
};

function StatusBadge({ status }) {
  const safeStatus = statusStyles[status] ? status : "Offline";

  return (
    <span
      className={`inline-flex items-center gap-2 rounded-full px-3 py-1.5 text-xs font-semibold ${statusStyles[safeStatus]}`}
    >
      <span
        className={`h-2 w-2 rounded-full ${
          safeStatus === "Available"
            ? "bg-emerald-500"
            : safeStatus === "Busy"
            ? "bg-amber-500"
            : "bg-slate-400"
        }`}
      />
      {safeStatus}
    </span>
  );
}

function AccountBadge({ status }) {
  const safeStatus = accountStyles[status]
    ? status
    : "Pending Activation";

  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1.5 text-xs font-semibold ${accountStyles[safeStatus]}`}
    >
      {safeStatus === "Active" ? (
        <CircleCheck size={13} />
      ) : safeStatus === "Disabled" ? (
        <Ban size={13} />
      ) : (
        <Clock3 size={13} />
      )}

      {safeStatus}
    </span>
  );
}

function DriverAvatar({ name }) {
  const initials = (name || "D")
    .split(" ")
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0])
    .join("")
    .toUpperCase();

  return (
    <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-teal-50 font-bold text-teal-800">
      {initials}
    </div>
  );
}

export default function Drivers() {
  const [driverList, setDriverList] = useState(initialDrivers);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("All");
  const [accountFilter, setAccountFilter] = useState("All");

  const [selectedDriver, setSelectedDriver] = useState(null);
  const [editingDriver, setEditingDriver] = useState(null);
  const [showCreateModal, setShowCreateModal] = useState(false);

  const [newDriver, setNewDriver] = useState(emptyDriver);
  const [error, setError] = useState("");
  const [successMessage, setSuccessMessage] = useState("");

  const totalDrivers = driverList.length;

  const activeAccounts = driverList.filter(
    (driver) => driver.accountStatus === "Active"
  ).length;

  const pendingAccounts = driverList.filter(
    (driver) => driver.accountStatus === "Pending Activation"
  ).length;

  const disabledAccounts = driverList.filter(
    (driver) => driver.accountStatus === "Disabled"
  ).length;

  const availableDrivers = driverList.filter(
    (driver) =>
      driver.status === "Available" &&
      driver.accountStatus === "Active"
  ).length;

  const busyDrivers = driverList.filter(
    (driver) => driver.status === "Busy"
  ).length;

  const filteredDrivers = driverList.filter((driver) => {
    const keyword = search.toLowerCase().trim();

    const matchesSearch = [
      driver.id,
      driver.name,
      driver.email,
      driver.phone,
      driver.vehicle,
    ].some((value) =>
      String(value || "").toLowerCase().includes(keyword)
    );

    const matchesAvailability =
      statusFilter === "All" || driver.status === statusFilter;

    const matchesAccount =
      accountFilter === "All" ||
      driver.accountStatus === accountFilter;

    return (
      matchesSearch &&
      matchesAvailability &&
      matchesAccount
    );
  });

  const generateDriverId = () => {
    const largestNumber = driverList.reduce((max, driver) => {
      const number = Number(
        String(driver.id || "").replace("DRV-", "")
      );

      return Number.isFinite(number)
        ? Math.max(max, number)
        : max;
    }, 0);

    return `DRV-${String(largestNumber + 1).padStart(3, "0")}`;
  };

  const validateDriver = (driver, currentId = null) => {
    const name = driver.name.trim();
    const email = driver.email.trim().toLowerCase();
    const phone = driver.phone.trim();

    if (!name || !email || !phone) {
      return "Name, email and phone number are required.";
    }

    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      return "Please enter a valid email address.";
    }

    const phoneDigits = phone.replace(/\D/g, "");

    if (phoneDigits.length < 9 || phoneDigits.length > 15) {
      return "Please enter a valid phone number.";
    }

    const duplicate = driverList.some(
      (existing) =>
        existing.id !== currentId &&
        (
          existing.email?.toLowerCase() === email ||
          existing.phone?.replace(/\s/g, "") === phone.replace(/\s/g, "")
        )
    );

    if (duplicate) {
      return "This email address or phone number already exists.";
    }

    return "";
  };

  const handleCreateDriver = (event) => {
    event.preventDefault();
    setError("");
    setSuccessMessage("");

    const validationError = validateDriver(newDriver);

    if (validationError) {
      setError(validationError);
      return;
    }

    const createdDriver = {
      ...newDriver,
      id: generateDriverId(),
      name: newDriver.name.trim(),
      email: newDriver.email.trim().toLowerCase(),
      phone: newDriver.phone.trim(),
      vehicle: newDriver.vehicle.trim(),
      accountStatus: "Pending Activation",
    };

    setDriverList((previous) => [
      ...previous,
      createdDriver,
    ]);

    setNewDriver(emptyDriver);
    setShowCreateModal(false);
    setSearch("");
    setStatusFilter("All");
    setAccountFilter("All");

    setSuccessMessage(
      `${createdDriver.id} was created. The account is pending activation.`
    );
  };

  const handleUpdateDriver = (event) => {
    event.preventDefault();
    setError("");
    setSuccessMessage("");

    const validationError = validateDriver(
      editingDriver,
      editingDriver.id
    );

    if (validationError) {
      setError(validationError);
      return;
    }

    const updatedDriver = {
      ...editingDriver,
      name: editingDriver.name.trim(),
      email: editingDriver.email.trim().toLowerCase(),
      phone: editingDriver.phone.trim(),
      vehicle: editingDriver.vehicle.trim(),
    };

    setDriverList((previous) =>
      previous.map((driver) =>
        driver.id === updatedDriver.id
          ? updatedDriver
          : driver
      )
    );

    setSelectedDriver(updatedDriver);
    setEditingDriver(null);
    setSuccessMessage("Driver details updated successfully.");
  };

  const handleAccountStatus = (driver) => {
    setError("");
    setSuccessMessage("");

    let nextStatus;

    if (driver.accountStatus === "Active") {
      const confirmed = window.confirm(
        `Disable the account for ${driver.name}? They will no longer be allowed to log in once this is enforced by the authentication system.`
      );

      if (!confirmed) return;

      nextStatus = "Disabled";
    } else if (driver.accountStatus === "Disabled") {
      const confirmed = window.confirm(
        `Reactivate the account for ${driver.name}?`
      );

      if (!confirmed) return;

      nextStatus = "Active";
    } else {
      const confirmed = window.confirm(
        `Activate the account for ${driver.name}?`
      );

      if (!confirmed) return;

      nextStatus = "Active";
    }

    const updatedDriver = {
      ...driver,
      accountStatus: nextStatus,
    };

    setDriverList((previous) =>
      previous.map((item) =>
        item.id === driver.id ? updatedDriver : item
      )
    );

    setSelectedDriver((previous) =>
      previous?.id === driver.id ? updatedDriver : previous
    );

    setSuccessMessage(
      `${driver.name}'s account status is now ${nextStatus}.`
    );
  };

  const closeModals = () => {
    setShowCreateModal(false);
    setSelectedDriver(null);
    setEditingDriver(null);
    setError("");
  };

  const Field = ({
    label,
    value,
    onChange,
    type = "text",
    required = false,
    placeholder = "",
  }) => (
    <label className="block">
      <span className="mb-2 block text-sm font-medium text-slate-700">
        {label}
        {required && <span className="ml-1 text-red-500">*</span>}
      </span>

      <input
        type={type}
        value={value || ""}
        onChange={onChange}
        required={required}
        placeholder={placeholder}
        className="w-full rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm outline-none transition placeholder:text-slate-400 focus:border-teal-600 focus:ring-4 focus:ring-teal-600/10"
      />
    </label>
  );

  const SummaryCard = ({
    title,
    value,
    description,
    icon: Icon,
    iconStyle,
  }) => (
    <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition hover:shadow-md">
      <div className="flex items-center justify-between">
        <p className="text-sm font-medium text-slate-500">
          {title}
        </p>

        <div
          className={`flex h-11 w-11 items-center justify-center rounded-xl ${iconStyle}`}
        >
          <Icon size={21} />
        </div>
      </div>

      <h3 className="mt-4 text-3xl font-bold text-slate-900">
        {value}
      </h3>

      <p className="mt-1 text-xs text-slate-500">
        {description}
      </p>
    </div>
  );

  return (
    <div className="min-h-full space-y-6 bg-slate-50/60 p-4 sm:p-6 lg:p-8">

      {/* Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <div className="mb-2 flex items-center gap-2 text-sm font-medium text-teal-700">
            <ShieldCheck size={17} />
            Fleet Operations
          </div>

          <h1 className="text-2xl font-bold tracking-tight text-slate-900 sm:text-3xl">
            Driver Management
          </h1>

          <p className="mt-2 text-sm text-slate-500 sm:text-base">
            Manage driver profiles, accounts and availability.
          </p>
        </div>

        <button
          onClick={() => {
            setError("");
            setSuccessMessage("");
            setNewDriver(emptyDriver);
            setShowCreateModal(true);
          }}
          className="inline-flex items-center justify-center gap-2 rounded-xl bg-teal-700 px-5 py-3 text-sm font-semibold text-white shadow-sm transition hover:bg-teal-800"
        >
          <UserPlus size={18} />
          Create Driver Account
        </button>
      </div>

      {/* Success Message */}
      {successMessage && (
        <div className="flex items-start gap-3 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800">
          <CheckCircleIcon />
          <p>{successMessage}</p>

          <button
            onClick={() => setSuccessMessage("")}
            className="ml-auto text-emerald-700 hover:text-emerald-900"
            aria-label="Dismiss message"
          >
            <X size={17} />
          </button>
        </div>
      )}

      {/* Summary Cards */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <SummaryCard
          title="Total Drivers"
          value={totalDrivers}
          description="Registered driver profiles"
          icon={Users}
          iconStyle="bg-sky-50 text-sky-700"
        />

        <SummaryCard
          title="Active Accounts"
          value={activeAccounts}
          description="Accounts activated"
          icon={UserCheck}
          iconStyle="bg-emerald-50 text-emerald-700"
        />

        <SummaryCard
          title="Pending Activation"
          value={pendingAccounts}
          description="Awaiting admin activation"
          icon={Clock3}
          iconStyle="bg-amber-50 text-amber-700"
        />

        <SummaryCard
          title="Disabled Accounts"
          value={disabledAccounts}
          description="Login access disabled"
          icon={LockKeyRoundIcon}
          iconStyle="bg-red-50 text-red-700"
        />
      </div>

      {/* Availability Summary */}
      <div className="flex flex-wrap items-center gap-x-6 gap-y-3 rounded-2xl border border-slate-200 bg-white px-5 py-4 shadow-sm">
        <div>
          <p className="text-xs text-slate-500">
            Available for Assignment
          </p>
          <p className="mt-1 text-lg font-bold text-emerald-700">
            {availableDrivers}
          </p>
        </div>

        <div className="h-10 w-px bg-slate-200" />

        <div>
          <p className="text-xs text-slate-500">
            Currently Busy
          </p>
          <p className="mt-1 text-lg font-bold text-amber-700">
            {busyDrivers}
          </p>
        </div>

        <p className="ml-auto max-w-sm text-xs leading-5 text-slate-500">
          Only drivers with an Active account and Available status
          should be eligible for new assignments.
        </p>
      </div>

      {/* Driver Table */}
      <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">

        <div className="flex flex-col gap-4 border-b border-slate-200 p-5 sm:p-6">
          <div>
            <h2 className="text-lg font-bold text-slate-900">
              Driver Accounts
            </h2>

            <p className="mt-1 text-sm text-slate-500">
              Manage driver details, account access and availability.
            </p>
          </div>

          <div className="flex flex-col gap-3 lg:flex-row">
            <div className="relative flex-1">
              <Search
                size={18}
                className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400"
              />

              <input
                type="search"
                placeholder="Search ID, name, email or vehicle..."
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                className="w-full rounded-xl border border-slate-200 py-3 pl-10 pr-4 text-sm outline-none transition focus:border-teal-600 focus:ring-4 focus:ring-teal-600/10"
              />
            </div>

            <div className="flex items-center gap-2 rounded-xl border border-slate-200 px-3">
              <SlidersHorizontal
                size={17}
                className="text-slate-500"
              />

              <select
                value={statusFilter}
                onChange={(event) =>
                  setStatusFilter(event.target.value)
                }
                className="w-full bg-transparent py-3 text-sm text-slate-700 outline-none"
              >
                <option value="All">All Availability</option>
                <option value="Available">Available</option>
                <option value="Busy">Busy</option>
                <option value="Offline">Offline</option>
              </select>
            </div>

            <select
              value={accountFilter}
              onChange={(event) =>
                setAccountFilter(event.target.value)
              }
              className="rounded-xl border border-slate-200 bg-white px-3 py-3 text-sm text-slate-700 outline-none focus:border-teal-600"
            >
              <option value="All">All Accounts</option>
              <option value="Active">Active</option>
              <option value="Pending Activation">
                Pending Activation
              </option>
              <option value="Disabled">Disabled</option>
            </select>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full min-w-[1100px] text-left">
            <thead className="bg-slate-50">
              <tr className="text-xs uppercase tracking-wider text-slate-500">
                <th className="px-6 py-4 font-semibold">
                  Driver
                </th>

                <th className="px-6 py-4 font-semibold">
                  Contact
                </th>

                <th className="px-6 py-4 font-semibold">
                  Vehicle
                </th>

                <th className="px-6 py-4 font-semibold">
                  Availability
                </th>

                <th className="px-6 py-4 font-semibold">
                  Account Status
                </th>

                <th className="px-6 py-4 text-right font-semibold">
                  Actions
                </th>
              </tr>
            </thead>

            <tbody className="divide-y divide-slate-100">
              {filteredDrivers.map((driver) => (
                <tr
                  key={driver.id}
                  className="transition hover:bg-slate-50"
                >
                  <td className="px-6 py-5">
                    <div className="flex items-center gap-3">
                      <DriverAvatar name={driver.name} />

                      <div>
                        <p className="font-semibold text-slate-900">
                          {driver.name}
                        </p>

                        <p className="mt-1 text-xs text-slate-500">
                          {driver.id}
                        </p>
                      </div>
                    </div>
                  </td>

                  <td className="px-6 py-5">
                    <p className="text-sm text-slate-700">
                      {driver.email}
                    </p>

                    <p className="mt-1 text-xs text-slate-500">
                      {driver.phone}
                    </p>
                  </td>

                  <td className="px-6 py-5">
                    <div className="flex items-center gap-2 text-sm text-slate-700">
                      <CarFront
                        size={17}
                        className="text-slate-400"
                      />
                      {driver.vehicle || "Not assigned"}
                    </div>
                  </td>

                  <td className="px-6 py-5">
                    <StatusBadge status={driver.status} />
                  </td>

                  <td className="px-6 py-5">
                    <AccountBadge status={driver.accountStatus} />
                  </td>

                  <td className="px-6 py-5">
                    <div className="flex justify-end gap-2">
                      <button
                        onClick={() => {
                          setError("");
                          setSelectedDriver(driver);
                        }}
                        title="View driver"
                        className="rounded-lg border border-slate-200 p-2 text-slate-600 transition hover:bg-slate-100"
                      >
                        <Eye size={16} />
                      </button>

                      <button
                        onClick={() => {
                          setError("");
                          setEditingDriver({ ...driver });
                        }}
                        title="Edit driver"
                        className="rounded-lg bg-teal-50 p-2 text-teal-700 transition hover:bg-teal-100"
                      >
                        <Pencil size={16} />
                      </button>

                      <button
                        onClick={() => handleAccountStatus(driver)}
                        title={
                          driver.accountStatus === "Active"
                            ? "Disable account"
                            : driver.accountStatus === "Disabled"
                            ? "Reactivate account"
                            : "Activate account"
                        }
                        className={`rounded-lg p-2 transition ${
                          driver.accountStatus === "Active"
                            ? "bg-red-50 text-red-600 hover:bg-red-100"
                            : "bg-emerald-50 text-emerald-700 hover:bg-emerald-100"
                        }`}
                      >
                        {driver.accountStatus === "Active" ? (
                          <Ban size={16} />
                        ) : (
                          <Power size={16} />
                        )}
                      </button>
                    </div>
                  </td>
                </tr>
              ))}

              {filteredDrivers.length === 0 && (
                <tr>
                  <td colSpan={6} className="px-6 py-16 text-center">
                    <Users
                      size={32}
                      className="mx-auto text-slate-300"
                    />

                    <p className="mt-3 font-semibold text-slate-800">
                      No drivers found
                    </p>

                    <p className="mt-1 text-sm text-slate-500">
                      Try another keyword or change your filters.
                    </p>

                    <button
                      onClick={() => {
                        setSearch("");
                        setStatusFilter("All");
                        setAccountFilter("All");
                      }}
                      className="mt-3 text-sm font-semibold text-teal-700 hover:underline"
                    >
                      Clear filters
                    </button>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        <div className="border-t border-slate-200 bg-slate-50/70 px-6 py-3 text-xs text-slate-500">
          Showing {filteredDrivers.length} of {totalDrivers} drivers
        </div>
      </div>

      {/* Create Account Modal */}
      {showCreateModal && (
        <Modal
          title="Create Driver Account"
          subtitle="Register a driver and prepare their account for activation."
          onClose={closeModals}
        >
          <form onSubmit={handleCreateDriver}>
            <div className="space-y-5 p-6">
              <div className="flex items-start gap-3 rounded-xl border border-blue-100 bg-blue-50 p-4">
                <KeyRound
                  size={20}
                  className="mt-0.5 shrink-0 text-blue-700"
                />

                <div>
                  <p className="text-sm font-semibold text-blue-900">
                    Account setup
                  </p>

                  <p className="mt-1 text-sm leading-5 text-blue-800">
                    The driver will be registered with Pending
                    Activation status. Login credentials must be
                    securely set up through the authentication system.
                  </p>
                </div>
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <Field
                  label="Full Name"
                  value={newDriver.name}
                  onChange={(event) =>
                    setNewDriver({
                      ...newDriver,
                      name: event.target.value,
                    })
                  }
                  placeholder="Enter full name"
                  required
                />

                <Field
                  label="Email Address"
                  type="email"
                  value={newDriver.email}
                  onChange={(event) =>
                    setNewDriver({
                      ...newDriver,
                      email: event.target.value,
                    })
                  }
                  placeholder="driver@example.com"
                  required
                />

                <Field
                  label="Phone Number"
                  value={newDriver.phone}
                  onChange={(event) =>
                    setNewDriver({
                      ...newDriver,
                      phone: event.target.value,
                    })
                  }
                  placeholder="e.g. 0123456789"
                  required
                />

                <Field
                  label="Assigned Vehicle"
                  value={newDriver.vehicle}
                  onChange={(event) =>
                    setNewDriver({
                      ...newDriver,
                      vehicle: event.target.value,
                    })
                  }
                  placeholder="Optional vehicle ID"
                />

                <label className="block sm:col-span-2">
                  <span className="mb-2 block text-sm font-medium text-slate-700">
                    Initial Availability
                  </span>

                  <select
                    value={newDriver.status}
                    onChange={(event) =>
                      setNewDriver({
                        ...newDriver,
                        status: event.target.value,
                      })
                    }
                    className="w-full rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm outline-none focus:border-teal-600 focus:ring-4 focus:ring-teal-600/10"
                  >
                    <option value="Available">Available</option>
                    <option value="Busy">Busy</option>
                    <option value="Offline">Offline</option>
                  </select>
                </label>
              </div>

              {error && (
                <p className="rounded-lg bg-red-50 p-3 text-sm text-red-700">
                  {error}
                </p>
              )}
            </div>

            <div className="flex justify-end gap-3 border-t bg-slate-50 p-6">
              <button
                type="button"
                onClick={closeModals}
                className="rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-medium text-slate-700 hover:bg-white"
              >
                Cancel
              </button>

              <button
                type="submit"
                className="inline-flex items-center gap-2 rounded-xl bg-teal-700 px-5 py-2.5 text-sm font-semibold text-white hover:bg-teal-800"
              >
                <UserPlus size={17} />
                Create Account
              </button>
            </div>
          </form>
        </Modal>
      )}

      {/* View Driver Modal */}
      {selectedDriver && (
        <Modal
          title="Driver Account Details"
          subtitle={`Driver ID: ${selectedDriver.id}`}
          onClose={() => setSelectedDriver(null)}
        >
          <div className="space-y-5 p-6">
            <div className="flex items-center gap-4 rounded-xl bg-slate-50 p-4">
              <DriverAvatar name={selectedDriver.name} />

              <div className="min-w-0">
                <h3 className="font-bold text-slate-900">
                  {selectedDriver.name}
                </h3>

                <p className="mt-1 text-sm text-slate-500">
                  {selectedDriver.id}
                </p>

                <div className="mt-2">
                  <AccountBadge status={selectedDriver.accountStatus} />
                </div>
              </div>
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <Detail
                icon={Mail}
                label="Email Address"
                value={selectedDriver.email}
              />

              <Detail
                icon={Phone}
                label="Phone Number"
                value={selectedDriver.phone}
              />

              <Detail
                icon={CarFront}
                label="Assigned Vehicle"
                value={selectedDriver.vehicle || "Not assigned"}
              />

              <Detail
                icon={UserCheck}
                label="Availability"
                value={selectedDriver.status}
              />
            </div>

            <div className="rounded-xl border border-slate-200 p-4">
              <p className="text-sm font-semibold text-slate-800">
                Account Access
              </p>

              <p className="mt-1 text-sm leading-5 text-slate-500">
                {selectedDriver.accountStatus === "Active"
                  ? "This account is marked as active in the management interface."
                  : selectedDriver.accountStatus === "Disabled"
                  ? "This account is marked as disabled."
                  : "This account is waiting for activation."}
              </p>

              <p className="mt-2 text-xs leading-5 text-slate-400">
                Actual login permissions must be enforced by the
                backend authentication system.
              </p>
            </div>
          </div>

          <div className="flex flex-wrap justify-end gap-3 border-t p-6">
            <button
              onClick={() => setSelectedDriver(null)}
              className="rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-medium text-slate-700 hover:bg-slate-50"
            >
              Close
            </button>

            <button
              onClick={() => {
                setEditingDriver({ ...selectedDriver });
                setSelectedDriver(null);
                setError("");
              }}
              className="inline-flex items-center gap-2 rounded-xl bg-teal-700 px-4 py-2.5 text-sm font-semibold text-white hover:bg-teal-800"
            >
              <Pencil size={16} />
              Edit Details
            </button>

            <button
              onClick={() => handleAccountStatus(selectedDriver)}
              className="inline-flex items-center gap-2 rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-50"
            >
              <Power size={16} />
              {selectedDriver.accountStatus === "Active"
                ? "Disable"
                : selectedDriver.accountStatus === "Disabled"
                ? "Reactivate"
                : "Activate"}
            </button>
          </div>
        </Modal>
      )}

      {/* Edit Driver Modal */}
      {editingDriver && (
        <Modal
          title="Update Driver"
          subtitle={`Editing ${editingDriver.id}`}
          onClose={() => {
            setEditingDriver(null);
            setError("");
          }}
        >
          <form onSubmit={handleUpdateDriver}>
            <div className="grid gap-4 p-6 sm:grid-cols-2">
              <Field
                label="Full Name"
                value={editingDriver.name}
                onChange={(event) =>
                  setEditingDriver({
                    ...editingDriver,
                    name: event.target.value,
                  })
                }
                required
              />

              <Field
                label="Email Address"
                type="email"
                value={editingDriver.email}
                onChange={(event) =>
                  setEditingDriver({
                    ...editingDriver,
                    email: event.target.value,
                  })
                }
                required
              />

              <Field
                label="Phone Number"
                value={editingDriver.phone}
                onChange={(event) =>
                  setEditingDriver({
                    ...editingDriver,
                    phone: event.target.value,
                  })
                }
                required
              />

              <Field
                label="Assigned Vehicle"
                value={editingDriver.vehicle}
                onChange={(event) =>
                  setEditingDriver({
                    ...editingDriver,
                    vehicle: event.target.value,
                  })
                }
                placeholder="Vehicle ID (optional)"
              />

              <label className="block">
                <span className="mb-2 block text-sm font-medium text-slate-700">
                  Availability
                </span>

                <select
                  value={editingDriver.status || "Offline"}
                  onChange={(event) =>
                    setEditingDriver({
                      ...editingDriver,
                      status: event.target.value,
                    })
                  }
                  className="w-full rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm outline-none focus:border-teal-600 focus:ring-4 focus:ring-teal-600/10"
                >
                  <option value="Available">Available</option>
                  <option value="Busy">Busy</option>
                  <option value="Offline">Offline</option>
                </select>
              </label>

              <label className="block">
                <span className="mb-2 block text-sm font-medium text-slate-700">
                  Account Status
                </span>

                <select
                  value={editingDriver.accountStatus}
                  onChange={(event) =>
                    setEditingDriver({
                      ...editingDriver,
                      accountStatus: event.target.value,
                    })
                  }
                  className="w-full rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm outline-none focus:border-teal-600 focus:ring-4 focus:ring-teal-600/10"
                >
                  <option value="Pending Activation">
                    Pending Activation
                  </option>
                  <option value="Active">Active</option>
                  <option value="Disabled">Disabled</option>
                </select>
              </label>

              {error && (
                <p className="rounded-lg bg-red-50 p-3 text-sm text-red-700 sm:col-span-2">
                  {error}
                </p>
              )}
            </div>

            <div className="flex justify-end gap-3 border-t bg-slate-50 p-6">
              <button
                type="button"
                onClick={() => {
                  setEditingDriver(null);
                  setError("");
                }}
                className="rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-medium text-slate-700 hover:bg-white"
              >
                Cancel
              </button>

              <button
                type="submit"
                className="inline-flex items-center gap-2 rounded-xl bg-teal-700 px-5 py-2.5 text-sm font-semibold text-white hover:bg-teal-800"
              >
                <Check size={17} />
                Save Changes
              </button>
            </div>
          </form>
        </Modal>
      )}
    </div>
  );
}

function Modal({ title, subtitle, onClose, children }) {
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-slate-950/50 p-4 backdrop-blur-sm"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <div className="my-8 w-full max-w-xl overflow-hidden rounded-2xl bg-white shadow-2xl">
        <div className="flex items-center justify-between border-b border-slate-100 p-6">
          <div>
            <h2 className="text-xl font-bold text-slate-900">
              {title}
            </h2>

            <p className="mt-1 text-sm text-slate-500">
              {subtitle}
            </p>
          </div>

          <button
            type="button"
            onClick={onClose}
            aria-label="Close modal"
            className="rounded-lg p-2 text-slate-500 hover:bg-slate-100"
          >
            <X size={19} />
          </button>
        </div>

        {children}
      </div>
    </div>
  );
}

function Detail({ icon: Icon, label, value }) {
  return (
    <div className="rounded-xl border border-slate-200 p-4">
      <Icon size={18} className="mb-3 text-slate-400" />

      <p className="text-xs text-slate-500">{label}</p>

      <p className="mt-1 break-words text-sm font-semibold text-slate-800">
        {value || "Not provided"}
      </p>
    </div>
  );
}

function CheckCircleIcon() {
  return <Check size={18} className="mt-0.5 shrink-0" />;
}

function LockKeyRoundIcon() {
  return <LockKeyhole size={21} />;
}
