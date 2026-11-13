
import { useMemo, useState, useEffect } from "react";
import api from "../services/api";
import {
  Eye,
  Pencil,
  Search,
  X,
  Truck,
  CheckCircle2,
  Wrench,
  Activity,
  CalendarDays,
  Users,
  ChevronDown,
  CarFront,
  RotateCcw,
} from "lucide-react";

const STATUSES = ["Available", "In Use", "Maintenance"];

const inputClass =
  "w-full rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm outline-none transition placeholder:text-slate-400 focus:border-teal-500 focus:ring-4 focus:ring-teal-500/10";

const labelClass =
  "mb-2 block text-sm font-medium text-slate-700";

const formatApiVehicle = (vehicle) => {
  const status = String(vehicle.vehicleStatus || "").toUpperCase();
  return {
    vehicleId: vehicle.vehicleId,
    plate: vehicle.vehiclePlateno || "",
    model: vehicle.vehicleModel || "",
    type: vehicle.vehicleType || "",
    capacity: vehicle.vehicleCapacity ?? "",
    luggageCapacity: vehicle.vehicleLuggageCapacity ?? "",
    roadTaxExpiry: vehicle.vehicleRoadtaxexpiry || "",
    serviceExpiry: vehicle.vehicleServiceexpiry || "",
    status:
      status === "AVAILABLE"
        ? "Available"
        : ["IN_USE", "UNAVAILABLE"].includes(status)
          ? "In Use"
          : status === "MAINTENANCE"
            ? "Maintenance"
            : vehicle.vehicleStatus || "Maintenance",
  };
};

const toApiStatus = (status) =>
  status === "Available"
    ? "AVAILABLE"
    : status === "In Use"
      ? "UNAVAILABLE"
      : "MAINTENANCE";

const getErrorMessage = (error) => {
  const response = error.response?.data;
  return (typeof response === "string"
    ? response
    : response?.message || response?.detail)
    || error.message
    || "The vehicle request could not be completed.";
};

export default function Vehicles() {
  const [vehicleList, setVehicleList] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadingVehicleId, setLoadingVehicleId] = useState(null);
  const [savingVehicle, setSavingVehicle] = useState(false);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("All");
  const [selectedVehicle, setSelectedVehicle] = useState(null);
  const [editingVehicle, setEditingVehicle] = useState(null);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  async function fetchVehicles() {
    setLoading(true);
    try {
      const { data } = await api.get("/vehicles");
      setVehicleList(Array.isArray(data) ? data.map(formatApiVehicle) : []);
      setError("");
    } catch (requestError) {
      setError(getErrorMessage(requestError));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    fetchVehicles();
  }, []);

  const stats = useMemo(() => {
    const total = vehicleList.length;
    const available = vehicleList.filter(
      (v) => v.status === "Available"
    ).length;
    const inUse = vehicleList.filter(
      (v) => v.status === "In Use"
    ).length;
    const maintenance = vehicleList.filter(
      (v) => v.status === "Maintenance"
    ).length;

    return { total, available, inUse, maintenance };
  }, [vehicleList]);

  const filteredVehicles = useMemo(() => {
    const query = search.trim().toLowerCase();

    return vehicleList.filter((vehicle) => {
      const matchesSearch = [
        vehicle.plate,
        vehicle.model,
        vehicle.type,
        vehicle.capacity,
      ].some((value) =>
        String(value ?? "").toLowerCase().includes(query)
      );

      const matchesStatus =
        statusFilter === "All" ||
        vehicle.status === statusFilter;

      return matchesSearch && matchesStatus;
    });
  }, [vehicleList, search, statusFilter]);

  const getStatusStyle = (status) => {
    switch (status) {
      case "Available":
        return "bg-emerald-50 text-emerald-700 ring-emerald-600/20";
      case "In Use":
        return "bg-amber-50 text-amber-700 ring-amber-600/20";
      case "Maintenance":
        return "bg-rose-50 text-rose-700 ring-rose-600/20";
      default:
        return "bg-slate-100 text-slate-600 ring-slate-500/20";
    }
  };

  const getStatusIcon = (status) => {
    switch (status) {
      case "Available":
        return <CheckCircle2 size={14} />;
      case "In Use":
        return <Activity size={14} />;
      case "Maintenance":
        return <Wrench size={14} />;
      default:
        return null;
    }
  };

  const formatDate = (date) => {
    if (!date) return "Not set";

    const parsed = new Date(`${date}T00:00:00`);

    if (Number.isNaN(parsed.getTime())) return date;

    return parsed.toLocaleDateString("en-MY", {
      day: "2-digit",
      month: "short",
      year: "numeric",
    });
  };

  const validateVehicle = (vehicle, originalVehicleId = null) => {
    if (
      !vehicle.plate.trim() ||
      !vehicle.type.trim() ||
      vehicle.capacity === ""
    ) {
      return "Please fill in the registration number, vehicle type and capacity.";
    }

    const capacity = Number(vehicle.capacity);

    if (!Number.isInteger(capacity) || capacity < 1) {
      return "Passenger capacity must be a whole number greater than zero.";
    }

    if (vehicle.luggageCapacity !== "" &&
        (!Number.isInteger(Number(vehicle.luggageCapacity)) ||
          Number(vehicle.luggageCapacity) < 0)) {
      return "Luggage capacity must be a whole number zero or greater.";
    }

    const normalizedPlate = vehicle.plate
      .trim()
      .replace(/\s+/g, "")
      .toUpperCase();

    const duplicate = vehicleList.some(
      (item) =>
        item.plate.trim().replace(/\s+/g, "").toUpperCase() ===
          normalizedPlate &&
        item.vehicleId !== originalVehicleId
    );

    if (duplicate) {
      return "This registration number already exists.";
    }

    return "";
  };

  const handleUpdateVehicle = async (e) => {
    e.preventDefault();

    if (!editingVehicle) return;

    const vehicle = {
      ...editingVehicle,
      plate: editingVehicle.plate.trim().toUpperCase(),
      model: editingVehicle.model.trim(),
      type: editingVehicle.type.trim(),
      capacity: Number(editingVehicle.capacity),
      luggageCapacity:
        editingVehicle.luggageCapacity === ""
          ? null
          : Number(editingVehicle.luggageCapacity),
    };

    const validationError = validateVehicle(vehicle, editingVehicle.vehicleId);

    if (validationError) {
      setError(validationError);
      return;
    }

    if (!editingVehicle.vehicleId) {
      setError("This vehicle is not saved in the system and cannot be updated.");
      return;
    }

    setSavingVehicle(true);
    try {
      const { data } = await api.put(
        `/vehicles/${editingVehicle.vehicleId}`,
        {
          vehiclePlateno: vehicle.plate,
          vehicleModel: vehicle.model,
          vehicleType: vehicle.type,
          vehicleCapacity: vehicle.capacity,
          vehicleLuggageCapacity: vehicle.luggageCapacity,
          vehicleRoadtaxexpiry: vehicle.roadTaxExpiry || null,
          vehicleServiceexpiry: vehicle.serviceExpiry || null,
          vehicleStatus: toApiStatus(vehicle.status),
        }
      );
      const updatedVehicle = formatApiVehicle(data);
      setVehicleList((prev) => prev.map((item) =>
        item.vehicleId === updatedVehicle.vehicleId ? updatedVehicle : item
      ));
      setEditingVehicle(null);
      setError("");
      setNotice(`${updatedVehicle.plate} updated successfully.`);
    } catch (requestError) {
      setError(getErrorMessage(requestError));
    } finally {
      setSavingVehicle(false);
    }
  };

  const openEditModal = (vehicle) => {
    setEditingVehicle({
      ...vehicle,
      capacity: String(vehicle.capacity ?? ""),
      luggageCapacity: String(vehicle.luggageCapacity ?? ""),
    });
    setError("");
  };

  const closeEditModal = () => {
    setEditingVehicle(null);
    setError("");
  };

  const updateEditingVehicle = (field, value) => {
    setEditingVehicle((prev) => ({ ...prev, [field]: value }));
  };

  const viewVehicle = async (vehicle) => {
    if (!vehicle.vehicleId) {
      setSelectedVehicle(vehicle);
      return;
    }
    setLoadingVehicleId(vehicle.vehicleId);
    setError("");
    setNotice("");
    try {
      const { data } = await api.get(`/vehicles/${vehicle.vehicleId}`);
      setSelectedVehicle(formatApiVehicle(data));
    } catch (requestError) {
      setError(getErrorMessage(requestError));
    } finally {
      setLoadingVehicleId(null);
    }
  };

  const summaryCards = [
    {
      label: "Total Vehicles",
      value: stats.total,
      icon: Truck,
      color: "bg-slate-100 text-slate-700",
    },
    {
      label: "Available",
      value: stats.available,
      icon: CheckCircle2,
      color: "bg-emerald-50 text-emerald-600",
    },
    {
      label: "In Use",
      value: stats.inUse,
      icon: Activity,
      color: "bg-amber-50 text-amber-600",
    },
    {
      label: "Maintenance",
      value: stats.maintenance,
      icon: Wrench,
      color: "bg-rose-50 text-rose-600",
    },
  ];

  return (
    <div className="admin-page">
      {/* Page header */}
      <div className="admin-page-header">
        <div>
          <div className="mb-2 flex items-center gap-2 text-sm text-slate-500">
            <CarFront size={16} />
            <span>Fleet Management</span>
            <span>/</span>
            <span className="font-medium text-teal-700">
              Vehicles
            </span>
          </div>

          <h1 className="admin-page-title">
            Vehicle Management
          </h1>

          <p className="admin-page-description">
            Manage registered vehicles, monitor availability and
            keep track of road tax and service expiry dates.
          </p>
        </div>

      </div>

      {/* Summary cards */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {summaryCards.map((card) => {
          const Icon = card.icon;

          return (
            <div
              key={card.label}
              className="rounded-2xl border border-slate-200/80 bg-white p-5 shadow-sm transition hover:shadow-md"
            >
              <div className="flex items-start justify-between">
                <div>
                  <p className="text-sm font-medium text-slate-500">
                    {card.label}
                  </p>
                  <p className="mt-3 text-3xl font-bold tracking-tight text-slate-900">
                    {card.value}
                  </p>
                  <p className="mt-1 text-xs text-slate-400">
                    Registered fleet records
                  </p>
                </div>

                <div className={`rounded-xl p-3 ${card.color}`}>
                  <Icon size={22} />
                </div>
              </div>
            </div>
          );
        })}
      </div>

      {/* Main table */}
      <section className="overflow-hidden rounded-2xl border border-slate-200/80 bg-white shadow-sm">
        <div className="flex flex-col gap-4 border-b border-slate-100 p-5 sm:p-6 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <h2 className="text-lg font-bold text-slate-900">
              Vehicle Records
            </h2>
            <p className="mt-1 text-sm text-slate-500">
              View and update vehicle information.
            </p>
          </div>

          <div className="flex flex-col gap-3 sm:flex-row">
            <div className="relative sm:min-w-64">
              <Search
                size={18}
                className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400"
              />
              <input
                type="search"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                placeholder="Search plate, type, capacity..."
                aria-label="Search vehicles"
                className="w-full rounded-xl border border-slate-200 bg-slate-50 py-2.5 pl-10 pr-4 text-sm outline-none transition focus:border-teal-500 focus:bg-white focus:ring-4 focus:ring-teal-500/10"
              />
            </div>

            <div className="relative">
              <select
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value)}
                aria-label="Filter by vehicle status"
                className="w-full appearance-none rounded-xl border border-slate-200 bg-white py-2.5 pl-4 pr-10 text-sm text-slate-700 outline-none focus:border-teal-500 focus:ring-4 focus:ring-teal-500/10 sm:min-w-40"
              >
                <option value="All">All Statuses</option>
                {STATUSES.map((status) => (
                  <option key={status} value={status}>
                    {status}
                  </option>
                ))}
              </select>
              <ChevronDown
                size={16}
                className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-slate-400"
              />
            </div>
          </div>
        </div>

        <div className="flex flex-wrap items-center justify-between gap-3 bg-slate-50/70 px-5 py-3 sm:px-6">
          <p className="text-sm text-slate-500">
            Showing{" "}
            <span className="font-semibold text-slate-800">
              {filteredVehicles.length}
            </span>{" "}
            of{" "}
            <span className="font-semibold text-slate-800">
              {vehicleList.length}
            </span>{" "}
            vehicles
          </p>

          {(search || statusFilter !== "All") && (
            <button
              type="button"
              onClick={() => {
                setSearch("");
                setStatusFilter("All");
              }}
              className="inline-flex items-center gap-1.5 text-sm font-medium text-teal-700 hover:text-teal-800"
            >
              <RotateCcw size={14} />
              Reset filters
            </button>
          )}
        </div>

        {error && !editingVehicle && !selectedVehicle && (
          <div
            role="alert"
            className="mx-5 mt-4 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700 sm:mx-6"
          >
            {error}
          </div>
        )}
        {notice && (
          <div
            role="status"
            className="mx-5 mt-4 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700 sm:mx-6"
          >
            {notice}
          </div>
        )}

        <div className="overflow-x-auto">
          <table className="w-full min-w-[900px] text-left">
            <thead>
              <tr className="border-b border-slate-100 bg-white">
                <th className="px-6 py-4 text-xs font-semibold uppercase tracking-wider text-slate-500">
                  No.
                </th>
                <th className="px-6 py-4 text-xs font-semibold uppercase tracking-wider text-slate-500">
                  Vehicle
                </th>
                <th className="px-6 py-4 text-xs font-semibold uppercase tracking-wider text-slate-500">
                  Capacity
                </th>
                <th className="px-6 py-4 text-xs font-semibold uppercase tracking-wider text-slate-500">
                  Road Tax Expiry
                </th>
                <th className="px-6 py-4 text-xs font-semibold uppercase tracking-wider text-slate-500">
                  Service Expiry
                </th>
                <th className="px-6 py-4 text-xs font-semibold uppercase tracking-wider text-slate-500">
                  Status
                </th>
                <th className="px-6 py-4 text-right text-xs font-semibold uppercase tracking-wider text-slate-500">
                  Actions
                </th>
              </tr>
            </thead>

            <tbody className="divide-y divide-slate-100">
              {filteredVehicles.map((vehicle, index) => (
                <tr
                  key={vehicle.vehicleId ?? vehicle.plate}
                  className="transition hover:bg-slate-50/80"
                >
                  <td className="px-6 py-4 text-sm font-medium text-slate-500">
                    {index + 1}
                  </td>
                  <td className="px-6 py-4">
                    <div className="flex items-center gap-3">
                      <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-teal-50 text-teal-700">
                        <Truck size={21} />
                      </div>
                      <div>
                        <p className="font-semibold text-slate-900">
                          {vehicle.plate}
                        </p>
                        <p className="mt-1 text-sm text-slate-500">
                          {vehicle.model
                            ? `${vehicle.model} · ${vehicle.type}`
                            : vehicle.type}
                        </p>
                      </div>
                    </div>
                  </td>

                  <td className="px-6 py-4">
                    <div className="flex items-center gap-2 text-sm text-slate-700">
                      <Users size={16} className="text-slate-400" />
                      {vehicle.capacity} passengers
                    </div>
                  </td>

                  <td className="px-6 py-4">
                    <DateCell date={vehicle.roadTaxExpiry} formatDate={formatDate} />
                  </td>

                  <td className="px-6 py-4">
                    <DateCell date={vehicle.serviceExpiry} formatDate={formatDate} />
                  </td>

                  <td className="px-6 py-4">
                    <span
                      className={`inline-flex items-center gap-1.5 whitespace-nowrap rounded-full px-3 py-1.5 text-xs font-semibold ring-1 ring-inset ${getStatusStyle(
                        vehicle.status
                      )}`}
                    >
                      {getStatusIcon(vehicle.status)}
                      {vehicle.status}
                    </span>
                  </td>

                  <td className="px-6 py-4">
                    <div className="flex justify-end gap-2">
                      <button
                        type="button"
                        onClick={() => viewVehicle(vehicle)}
                        disabled={Boolean(vehicle.vehicleId)
                          && loadingVehicleId === vehicle.vehicleId}
                        aria-label={`View ${vehicle.plate}`}
                        title="View vehicle"
                        className="inline-flex items-center gap-2 rounded-lg border border-slate-200 px-3 py-2 text-sm font-medium text-slate-600 transition hover:border-teal-200 hover:bg-teal-50 hover:text-teal-700"
                      >
                        <Eye size={16} />
                        {vehicle.vehicleId && loadingVehicleId === vehicle.vehicleId
                          ? "Loading..."
                          : "View"}
                      </button>

                      <button
                        type="button"
                        onClick={() => openEditModal(vehicle)}
                        disabled={!vehicle.vehicleId}
                        aria-label={`Edit ${vehicle.plate}`}
                        title="Update vehicle"
                        className="inline-flex items-center gap-2 rounded-lg bg-teal-600 px-3 py-2 text-sm font-medium text-white transition hover:bg-teal-700"
                      >
                        <Pencil size={15} />
                        Edit
                      </button>

                    </div>
                  </td>
                </tr>
              ))}

              {loading && (
                <tr>
                  <td colSpan={7} className="px-6 py-10 text-center text-sm text-slate-500">
                    Loading vehicle records...
                  </td>
                </tr>
              )}
              {!loading && filteredVehicles.length === 0 && (
                <tr>
                  <td colSpan={7} className="px-6 py-16 text-center">
                    <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-slate-100 text-slate-400">
                      <Truck size={26} />
                    </div>
                    <h3 className="mt-4 font-semibold text-slate-800">
                      No vehicles found
                    </h3>
                    <p className="mt-1 text-sm text-slate-500">
                      Try another search or change the status filter.
                    </p>
                    <button
                      type="button"
                      onClick={() => {
                        setSearch("");
                        setStatusFilter("All");
                      }}
                      className="mt-4 text-sm font-semibold text-teal-700 hover:text-teal-800"
                    >
                      Clear filters
                    </button>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        <div className="border-t border-slate-100 px-5 py-4 text-xs text-slate-400 sm:px-6">
          Vehicle records are displayed using the data available to this page.
        </div>
      </section>

      {/* Update vehicle modal */}
      {editingVehicle && (
        <Modal
          title="Update Vehicle"
          subtitle="Review and update the vehicle information."
          onClose={closeEditModal}
          icon={<Pencil size={20} />}
        >
          <VehicleForm
            vehicle={editingVehicle}
            onChange={updateEditingVehicle}
            onSubmit={handleUpdateVehicle}
            onCancel={closeEditModal}
            submitLabel="Save Changes"
            error={error}
            busy={savingVehicle}
          />
        </Modal>
      )}

      {/* View vehicle modal */}
      {selectedVehicle && (
        <Modal
          title="Vehicle Details"
          subtitle="Complete information for this registered vehicle."
          onClose={() => setSelectedVehicle(null)}
          icon={<Truck size={21} />}
        >
          <div className="space-y-5">
            <div className="flex items-center gap-4 rounded-2xl bg-slate-50 p-4">
              <div className="flex h-14 w-14 items-center justify-center rounded-xl bg-teal-100 text-teal-700">
                <Truck size={28} />
              </div>
              <div className="min-w-0 flex-1">
                <p className="text-lg font-bold text-slate-900">
                  {selectedVehicle.plate}
                </p>
                <p className="text-sm text-slate-500">
                  {selectedVehicle.type}
                </p>
              </div>
              <span
                className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1.5 text-xs font-semibold ring-1 ring-inset ${getStatusStyle(
                  selectedVehicle.status
                )}`}
              >
                {getStatusIcon(selectedVehicle.status)}
                {selectedVehicle.status}
              </span>
            </div>

            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <DetailItem
                icon={<Users size={17} />}
                label="Passenger Capacity"
                value={`${selectedVehicle.capacity} passengers`}
              />
              <DetailItem
                icon={<Luggage size={17} />}
                label="Luggage Capacity"
                value={selectedVehicle.luggageCapacity === ""
                  ? "Not recorded"
                  : `${selectedVehicle.luggageCapacity} items`}
              />
              <DetailItem
                icon={<Truck size={17} />}
                label="Vehicle Model"
                value={selectedVehicle.model}
              />
              <DetailItem
                icon={<CalendarDays size={17} />}
                label="Road Tax Expiry"
                value={formatDate(selectedVehicle.roadTaxExpiry)}
              />
              <DetailItem
                icon={<Wrench size={17} />}
                label="Service Expiry"
                value={formatDate(selectedVehicle.serviceExpiry)}
              />
              <DetailItem
                icon={<Activity size={17} />}
                label="Current Status"
                value={selectedVehicle.status}
              />
            </div>

            <div className="flex flex-col-reverse gap-3 border-t border-slate-100 pt-5 sm:flex-row sm:justify-end">
              <button
                type="button"
                onClick={() => setSelectedVehicle(null)}
                className="rounded-xl border border-slate-200 px-5 py-2.5 text-sm font-semibold text-slate-700 transition hover:bg-slate-50"
              >
                Close
              </button>

              <button
                type="button"
                onClick={() => {
                  openEditModal(selectedVehicle);
                  setSelectedVehicle(null);
                }}
                className="inline-flex items-center justify-center gap-2 rounded-xl bg-teal-600 px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-teal-700"
              >
                <Pencil size={16} />
                Edit Vehicle
              </button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  );
}

function VehicleForm({
  vehicle,
  onChange,
  onSubmit,
  onCancel,
  submitLabel,
  error,
  busy = false,
}) {
  return (
    <form onSubmit={onSubmit} className="space-y-5">
      {error && (
        <div
          role="alert"
          className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700"
        >
          {error}
        </div>
      )}

      <div>
        <label className={labelClass}>Vehicle Model</label>
        <input
          type="text"
          value={vehicle.model || ""}
          onChange={(e) => onChange("model", e.target.value)}
          placeholder="e.g. Toyota Hiace"
          maxLength={100}
          className={inputClass}
        />
      </div>

      <div>
        <label className={labelClass}>
          Registration Number <span className="text-rose-500">*</span>
        </label>
        <input
          type="text"
          value={vehicle.plate}
          onChange={(e) =>
            onChange("plate", e.target.value.toUpperCase())
          }
          placeholder="e.g. ABC 1234"
          autoComplete="off"
          maxLength={20}
          required
          className={inputClass}
        />
      </div>

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div>
          <label className={labelClass}>
            Vehicle Type <span className="text-rose-500">*</span>
          </label>
          <input
            type="text"
            value={vehicle.type}
            onChange={(e) => onChange("type", e.target.value)}
            placeholder="e.g. Sedan, MPV, Bus"
            maxLength={60}
            required
            className={inputClass}
          />
        </div>

        <div>
          <label className={labelClass}>
            Passenger Capacity <span className="text-rose-500">*</span>
          </label>
          <input
            type="number"
            min="1"
            step="1"
            value={vehicle.capacity}
            onChange={(e) => onChange("capacity", e.target.value)}
            placeholder="e.g. 7"
            required
            className={inputClass}
          />
        </div>
      </div>

      <div>
        <label className={labelClass}>Luggage Capacity (items)</label>
        <input
          type="number"
          min="0"
          step="1"
          value={vehicle.luggageCapacity ?? ""}
          onChange={(e) => onChange("luggageCapacity", e.target.value)}
          placeholder="Enter actual item capacity"
          className={inputClass}
        />
      </div>

      <div>
        <label className={labelClass}>Road Tax Expiry Date</label>
        <input
          type="date"
          value={vehicle.roadTaxExpiry || ""}
          onChange={(e) => onChange("roadTaxExpiry", e.target.value)}
          className={inputClass}
        />
      </div>

      <div>
        <label className={labelClass}>Next Service Date</label>
        <input
          type="date"
          value={vehicle.serviceExpiry || ""}
          onChange={(e) => onChange("serviceExpiry", e.target.value)}
          className={inputClass}
        />
      </div>

      <div>
        <label className={labelClass}>Vehicle Status</label>
        <select
          value={vehicle.status || "Available"}
          onChange={(e) => onChange("status", e.target.value)}
          className={inputClass}
        >
          {STATUSES.map((status) => (
            <option key={status} value={status}>
              {status}
            </option>
          ))}
        </select>
      </div>

      <p className="text-xs text-slate-400">
        Fields marked with * are required.
      </p>

      <div className="flex flex-col-reverse gap-3 border-t border-slate-100 pt-5 sm:flex-row sm:justify-end">
        <button
          type="button"
          onClick={onCancel}
          disabled={busy}
          className="rounded-xl border border-slate-200 px-5 py-2.5 text-sm font-semibold text-slate-700 transition hover:bg-slate-50"
        >
          Cancel
        </button>

        <button
          type="submit"
          disabled={busy}
          className="inline-flex items-center justify-center gap-2 rounded-xl bg-teal-600 px-5 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-teal-700 focus:outline-none focus:ring-4 focus:ring-teal-500/20"
        >
          <CheckCircle2 size={16} />
          {busy ? "Saving..." : submitLabel}
        </button>
      </div>
    </form>
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
        className="my-auto w-full max-w-xl overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl"
      >
        <div className="flex items-start justify-between border-b border-slate-100 px-5 py-5 sm:px-6">
          <div className="flex items-start gap-3">
            <div className="rounded-xl bg-teal-50 p-3 text-teal-700">
              {icon}
            </div>
            <div>
              <h2 className="text-lg font-bold text-slate-900">
                {title}
              </h2>
              <p className="mt-1 text-sm leading-5 text-slate-500">
                {subtitle}
              </p>
            </div>
          </div>

          <button
            type="button"
            onClick={onClose}
            aria-label="Close modal"
            className="rounded-lg p-2 text-slate-400 transition hover:bg-slate-100 hover:text-slate-700"
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

function DetailItem({ icon, label, value }) {
  return (
    <div className="rounded-xl border border-slate-100 p-4">
      <div className="mb-2 flex items-center gap-2 text-slate-400">
        {icon}
        <p className="text-xs font-medium uppercase tracking-wide">
          {label}
        </p>
      </div>
      <p className="break-words text-sm font-semibold text-slate-800">
        {value || "Not set"}
      </p>
    </div>
  );
}

function DateCell({ date, formatDate }) {
  if (!date) {
    return <span className="text-sm text-slate-400">Not set</span>;
  }

  const today = new Date();
  today.setHours(0, 0, 0, 0);

  const parsedDate = new Date(`${date}T00:00:00`);
  const daysLeft = Math.ceil(
    (parsedDate.getTime() - today.getTime()) / (1000 * 60 * 60 * 24)
  );

  const isExpired =
    !Number.isNaN(parsedDate.getTime()) && daysLeft < 0;
  const isDueSoon =
    !Number.isNaN(parsedDate.getTime()) &&
    daysLeft >= 0 &&
    daysLeft <= 30;

  return (
    <div>
      <p
        className={`text-sm font-medium ${
          isExpired
            ? "text-rose-600"
            : isDueSoon
            ? "text-amber-600"
            : "text-slate-700"
        }`}
      >
        {formatDate(date)}
      </p>

      {(isExpired || isDueSoon) && (
        <p
          className={`mt-1 text-xs ${
            isExpired ? "text-rose-500" : "text-amber-600"
          }`}
        >
          {isExpired ? "Expired" : "Due within 30 days"}
        </p>
      )}
    </div>
  );
}
