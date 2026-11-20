
import { useMemo, useState, useEffect } from "react";
import api from "../services/api";
import {
  Eye,
  Pencil,
  Luggage,
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

const formatApiVehicle = (vehicle, drivers = []) => {
  const status = String(vehicle.vehicleStatus || "").toUpperCase();
  const driver = vehicle.driverId == null ? null : drivers.find(item => String(item.driverId) === String(vehicle.driverId));
  return {
    vehicleId: vehicle.vehicleId,
    driverName: driver?.driverName || "",
    assignedDriver: driver ? {
      name: driver.driverName, email: driver.driverEmail, phone: driver.driverPhoneno,
      type: driver.driverType, status: driver.driverStatus,
    } : null,
    plate: vehicle.vehiclePlateno || "",
    model: vehicle.vehicleModel || "",
    type: vehicle.vehicleType || "",
    capacity: vehicle.vehicleCapacity ?? "",
    luggageCapacity: vehicle.vehicleLuggageCapacity ?? "",
    maxLargeLuggage: vehicle.maxLargeLuggage ?? "",
    maxBackpacks: vehicle.maxBackpacks ?? "",
    roadTaxExpiry: vehicle.vehicleRoadtaxexpiry || "",
    serviceExpiry: vehicle.vehicleServiceexpiry || "",
    status:
      status === "AVAILABLE"
        ? "Available"
        : ["IN_USE", "UNAVAILABLE"].includes(status)
          ? "Inactive"
          : status === "MAINTENANCE"
            ? "Maintenance"
            : status === "INACTIVE" ? "Inactive" : vehicle.vehicleStatus || "Not recorded",
  };
};

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
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("All");
  const [selectedVehicle, setSelectedVehicle] = useState(null);
  const [error, setError] = useState("");
  const [drivers, setDrivers] = useState([]);
  const [editingVehicle, setEditingVehicle] = useState(null);
  const [savingVehicle, setSavingVehicle] = useState(false);
  const [notice, setNotice] = useState("");

  async function fetchVehicles() {
    setLoading(true);
    try {
      const [vehicles, driverResponse] = await Promise.all([api.get("/vehicles"), api.get("/drivers")]);
      setDrivers(driverResponse.data);
      setVehicleList(vehicles.data.map(vehicle => formatApiVehicle(vehicle, driverResponse.data)));
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

  const statuses = [...new Set(vehicleList.map(vehicle => vehicle.status))].sort();

  const stats = useMemo(() => {
    const total = vehicleList.length;
    const available = vehicleList.filter(
      (v) => v.status === "Available"
    ).length;
    const inactive = vehicleList.filter(
      (v) => v.status === "Inactive"
    ).length;
    const maintenance = vehicleList.filter(
      (v) => v.status === "Maintenance"
    ).length;

    return { total, available, inactive, maintenance };
  }, [vehicleList]);

  const filteredVehicles = useMemo(() => {
    const query = search.trim().toLowerCase();

    return vehicleList.filter((vehicle) => {
      const matchesSearch = [
        vehicle.plate,
        vehicle.model,
        vehicle.type,
        vehicle.capacity,
        vehicle.driverName,
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
      case "Inactive":
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
      case "Inactive":
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

  const viewVehicle = async (vehicle) => {
    if (!vehicle.vehicleId) {
      setSelectedVehicle(vehicle);
      return;
    }
    setLoadingVehicleId(vehicle.vehicleId);
    setError("");
    try {
      const { data } = await api.get(`/vehicles/${vehicle.vehicleId}`);
      setSelectedVehicle(formatApiVehicle(data, drivers));
    } catch (requestError) {
      setError(getErrorMessage(requestError));
    } finally {
      setLoadingVehicleId(null);
    }
  };

  const saveVehicle = async event => {
    event.preventDefault();
    setSavingVehicle(true);
    setError("");
    setNotice("");
    try {
      const v = editingVehicle;
      await api.put(`/vehicles/${v.vehicleId}`, {
        vehicleModel: v.model.trim(), vehiclePlateno: v.plate.trim().toUpperCase(), vehicleType: v.type.trim(),
        vehicleCapacity: Number(v.capacity), vehicleLuggageCapacity: Number(v.luggageCapacity),
        maxLargeLuggage: v.maxLargeLuggage === "" ? null : Number(v.maxLargeLuggage),
        maxBackpacks: v.maxBackpacks === "" ? null : Number(v.maxBackpacks),
        vehicleRoadtaxexpiry: v.roadTaxExpiry || null, vehicleServiceexpiry: v.serviceExpiry || null,
        vehicleStatus: v.status.toUpperCase(),
      });
      setEditingVehicle(null);
      setNotice("Vehicle updated successfully.");
      await fetchVehicles();
    } catch (requestError) { setError(getErrorMessage(requestError)); }
    finally { setSavingVehicle(false); }
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
      label: "Inactive",
      value: stats.inactive,
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
            Monitor registered vehicles, assigned drivers, availability, road tax and service dates. Edit vehicle information while preserving its linked driver.
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
              View vehicle information and driver assignments.
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
                placeholder="Search plate, model, type, driver..."
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
                {statuses.map((status) => (
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

        {notice && <p role="status" className="m-5 rounded-xl bg-emerald-50 p-3 text-sm text-emerald-700">{notice}</p>}
        {error && !editingVehicle && (
          <div
            role="alert"
            className="mx-5 mt-4 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700 sm:mx-6"
          >
            {error}
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
                  Assigned Driver
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

                  <td className="px-6 py-4 text-sm text-slate-700">{vehicle.driverName || "No assigned driver"}</td>

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
                      <button type="button" title="Edit vehicle" aria-label={`Edit ${vehicle.plate}`}
                        onClick={() => {setEditingVehicle({...vehicle}); setSelectedVehicle(null); setError(""); setNotice("");}}
                        className="inline-flex items-center gap-2 rounded-lg bg-teal-700 px-3 py-2 text-sm font-medium text-white hover:bg-teal-800">
                        <Pencil size={16} /> Edit
                      </button>



                    </div>
                  </td>
                </tr>
              ))}

              {loading && (
                <tr>
                  <td colSpan={8} className="px-6 py-10 text-center text-sm text-slate-500">
                    Loading vehicle records...
                  </td>
                </tr>
              )}
              {!loading && filteredVehicles.length === 0 && (
                <tr>
                  <td colSpan={8} className="px-6 py-16 text-center">
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
          Vehicle records are loaded from the database. Vehicle updates preserve the current driver relationship.
        </div>
      </section>

      {editingVehicle && <Modal title="Edit Vehicle" subtitle="Update this vehicle. Its linked driver cannot be changed here."
        icon={<Pencil size={20} />} onClose={() => {if (!savingVehicle) {setEditingVehicle(null); setError("");}}}>
        <form onSubmit={saveVehicle} className="space-y-5">
          <section aria-label="Assigned driver information" className="rounded-xl border border-slate-200 bg-slate-50 p-4">
            <h3 className="mb-3 font-semibold text-slate-900">Assigned Driver</h3>
            {editingVehicle.assignedDriver ? <dl className="grid gap-4 sm:grid-cols-2">
              {[["name", "Driver Name"], ["email", "Driver Email"], ["phone", "Driver Phone Number"],
                ["type", "Driver Type"], ["status", "Driver Status"]].map(([key, label]) => <div key={key}>
                  <dt className="text-xs text-slate-500">{label}</dt>
                  <dd className="mt-1 break-words text-sm font-medium text-slate-800">{editingVehicle.assignedDriver[key] || "Not provided"}</dd>
                </div>)}
            </dl> : <p className="text-sm text-slate-600">No assigned driver</p>}
          </section>
          <fieldset disabled={savingVehicle} className="grid gap-4 sm:grid-cols-2">
            {[["model", "Vehicle Model", "text", true], ["plate", "Plate Number", "text", true],
              ["type", "Vehicle Type", "text", true], ["capacity", "Passenger Capacity", "number", true],
              ["luggageCapacity", "Luggage Capacity (points)", "number", true],
              ["maxLargeLuggage", "Maximum Large Luggage", "number", false],
              ["maxBackpacks", "Maximum Backpacks", "number", false],
              ["roadTaxExpiry", "Road Tax Expiry Date", "date", false],
              ["serviceExpiry", "Next Service Date", "date", false]].map(([key, label, type, required]) => <label key={key} className="block text-sm font-medium text-slate-700">
                <span className="mb-2 block">{label}{required ? " *" : ""}</span>
                <input type={type} required={required} min={key === "capacity" ? 1 : 0} step={type === "number" ? 1 : undefined}
                  value={editingVehicle[key] ?? ""} onChange={event => setEditingVehicle(previous => ({...previous, [key]: event.target.value}))}
                  className="w-full rounded-xl border border-slate-200 px-4 py-3 text-sm focus:border-teal-600" />
              </label>)}
            <label className="block text-sm font-medium text-slate-700"><span className="mb-2 block">Vehicle Status</span>
              <select value={editingVehicle.status} onChange={event => setEditingVehicle(previous => ({...previous, status: event.target.value}))}
                className="w-full rounded-xl border border-slate-200 px-4 py-3 text-sm">
                {["Available", "Maintenance", "Inactive"].map(status => <option key={status}>{status}</option>)}
              </select>
            </label>
          </fieldset>
          {(editingVehicle.maxLargeLuggage === "" || editingVehicle.maxBackpacks === "") && (
            <p className="rounded-xl bg-amber-50 p-3 text-sm text-amber-900">
              Enter both verified luggage limits for this vehicle. The combined points limit still applies.
            </p>
          )}
          {error && <p role="alert" className="rounded-xl bg-red-50 p-3 text-sm text-red-700">{error}</p>}
          <div className="flex justify-end gap-3 border-t pt-4">
            <button type="button" disabled={savingVehicle} onClick={() => {setEditingVehicle(null); setError("");}} className="rounded-xl border px-4 py-2">Cancel</button>
            <button type="submit" disabled={savingVehicle} className="rounded-xl bg-teal-700 px-4 py-2 text-white">{savingVehicle ? "Saving..." : "Save Changes"}</button>
          </div>
        </form>
      </Modal>}

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
              <DetailItem icon={<Users size={17} />} label="Assigned Driver" value={selectedVehicle.driverName || "No assigned driver"} />
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
                  : `${selectedVehicle.luggageCapacity} points`}
              />
              <DetailItem icon={<Luggage size={17} />} label="Maximum Large Luggage"
                value={selectedVehicle.maxLargeLuggage === "" ? "Not configured" : String(selectedVehicle.maxLargeLuggage)} />
              <DetailItem icon={<Luggage size={17} />} label="Maximum Backpacks"
                value={selectedVehicle.maxBackpacks === "" ? "Not configured" : String(selectedVehicle.maxBackpacks)} />
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

            </div>
          </div>
        </Modal>
      )}
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
        {value ?? "Not set"}
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
