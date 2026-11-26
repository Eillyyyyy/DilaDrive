import useModalScrollLock from "../components/useModalScrollLock";
import useRecordFocus from "../services/useRecordFocus";
import AssignmentSchedule from "../components/AssignmentSchedule";
import UnavailablePeriods from "../components/UnavailablePeriods";
import DocumentUploadField from "../components/DocumentUploadField";
import SecureDocumentPreview from "../components/SecureDocumentPreview";
import { useState, useEffect, useRef } from "react";
import api from "../services/api";
import { getApiErrorMessage } from "../services/apiErrors";
import { loadDriverManagement } from "../services/driverManagement";
import { DRIVER_TYPES, ACCOUNT_STATUSES, AVAILABILITY_OPTIONS, VEHICLE_STATUSES,
  emptyDriver, emptyVehicle, formatDriver, toDriverPayload, toDriverCreatePayload,
  toDriverEditForm, validateDriverFields, driverRequestFieldErrors } from "../services/driverForm";
import {
  Search,
  Eye,
  Pencil,
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

// ==========================================
// INITIAL DATA
// ==========================================

const statusStyles = {
  Available: "bg-emerald-50 text-emerald-700",
  Assigned: "bg-blue-50 text-blue-700",
  "On Trip": "bg-amber-50 text-amber-700",
  "Off Duty": "bg-slate-100 text-slate-600",
  Unavailable: "bg-red-50 text-red-700",
  Busy: "bg-amber-50 text-amber-700",
  Offline: "bg-slate-100 text-slate-600",
};

const accountStyles = {
  Active: "bg-emerald-50 text-emerald-700",
  Disabled: "bg-red-50 text-red-700",
};

// ==========================================
// REUSABLE COMPONENTS
// ==========================================

function StatusBadge({ status }) {
  const safeStatus = status || "Not recorded";

  return (
    <span
      className={`inline-flex items-center gap-2 rounded-full px-3 py-1.5 text-xs font-semibold ${statusStyles[safeStatus] || "bg-slate-100 text-slate-600"}`}
    >
      <span
        className={`h-2 w-2 rounded-full ${
          safeStatus === "Available"
            ? "bg-emerald-500"
            : safeStatus === "Busy" ||
                safeStatus === "Assigned" ||
                safeStatus === "On Trip"
              ? "bg-amber-500"
              : "bg-slate-400"
        }`}
      />
      {safeStatus}
    </span>
  );
}

function AccountBadge({ status }) {
  const safeStatus = status || "Not recorded";

  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1.5 text-xs font-semibold ${accountStyles[safeStatus] || "bg-slate-100 text-slate-600"}`}
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

function Field({
  label,
  value,
  onChange,
  type = "text",
  required = false,
  readOnly = false,
  placeholder = "",
  error,
  min,
  max,
  step,
}) {
  return (
    <label className="block">
      <span className="mb-2 block text-sm font-medium text-slate-700">
        {label}
        {required && <span className="ml-1 text-red-500">*</span>}
      </span>

      <input
        type={type}
        aria-invalid={!!error}
        min={min}
        max={max}
        step={step}
        value={value ?? ""}
        onChange={onChange}
        required={required}
        readOnly={readOnly}
        placeholder={placeholder}
        className="w-full rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm outline-none transition placeholder:text-slate-400 focus:border-teal-600 focus:ring-4 focus:ring-teal-600/10"
      />
      {error && <span role="alert" className="mt-1 block text-xs text-red-600">{error}</span>}
    </label>
  );
}

function SummaryCard({
  title,
  value,
  description,
  icon: Icon,
  iconStyle,
}) {
  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm transition hover:shadow-md">
      <div className="flex items-center justify-between">
        <p className="text-sm font-medium text-slate-500">{title}</p>

        <div
          className={`flex h-11 w-11 items-center justify-center rounded-xl ${iconStyle}`}
        >
          <Icon size={21} />
        </div>
      </div>

      <h3 className="mt-4 text-3xl font-bold text-slate-900">{value}</h3>

      <p className="mt-1 text-xs text-slate-500">{description}</p>
    </div>
  );
}

function Modal({ title, subtitle, onClose, children, wide = false }) {
  useModalScrollLock();

  return (
    <div
      className="fixed inset-0 !m-0 z-50 flex items-center justify-center overflow-clip bg-slate-950/50 p-3 backdrop-blur-sm sm:p-5"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className={`modal-panel flex w-full flex-col overflow-clip rounded-2xl bg-white shadow-2xl ${
          wide ? "max-w-3xl" : "max-w-xl"
        }`}
      >
        <div className="flex shrink-0 items-center justify-between border-b border-slate-100 p-5 sm:p-6">
          <div>
            <h2 className="text-xl font-bold text-slate-900">{title}</h2>
            <p className="mt-1 text-sm text-slate-500">{subtitle}</p>
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

// ==========================================
// MAIN DRIVER MANAGEMENT
// ==========================================

function DriverFormFields({ newDriver, setNewDriver, editing, fieldErrors = {},
  vehicleCategories = [], uploadProgress = -1 }) {
  return <>
              {/* PERSONAL INFORMATION */}

              <section className="space-y-4">
                <div>
                  <h3 className="text-sm font-bold text-slate-900">
                    Personal Information
                  </h3>

                  <p className="mt-1 text-xs text-slate-500">
                    Basic contact details for the driver.
                  </p>
                </div>

                <div className="grid gap-4 sm:grid-cols-2">
                  <Field
                    label="Full Name"
                    error={fieldErrors["name"]}
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
                    error={fieldErrors["email"]}
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
                    error={fieldErrors["phone"]}
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
                    label="IC Number"
                    error={fieldErrors["icNumber"]}
                    value={newDriver.icNumber}
                    onChange={(event) => setNewDriver(previous => ({
                      ...previous, icNumber: event.target.value,
                    }))}
                    placeholder={editing && newDriver.icNumberMasked
                      ? `Leave blank to keep ${newDriver.icNumberMasked}` : "XXXXXX-XX-XXXX"}
                  />
                  <div className="sm:col-span-2">
                    <DocumentUploadField
                      label="IC Document"
                      file={newDriver.icDocumentFile}
                      onChange={(file) => setNewDriver(previous => ({
                        ...previous, icDocumentFile: file,
                      }))}
                      currentDocument={newDriver.icDocument}
                      progress={uploadProgress}
                      error={fieldErrors["icDocument"]}
                    />
                  </div>
                </div>
              </section>

              {/* LOGIN CREDENTIALS */}

              {!editing && <section className="space-y-4">
                <div>
                  <h3 className="text-sm font-bold text-slate-900">
                    Login Credentials
                  </h3>
                  <p className="mt-1 text-xs text-slate-500">
                    Set a password for the driver account.
                  </p>
                </div>

                <div className="grid gap-4 sm:grid-cols-2">
                  <Field
                    label="Password"
                    error={fieldErrors["password"]}
                    type="password"
                    value={newDriver.password}
                    onChange={(event) =>
                      setNewDriver({
                        ...newDriver,
                        password: event.target.value,
                      })
                    }
                    placeholder="Enter password"
                    required
                  />

                  <Field
                    label="Confirm Password"
                    error={fieldErrors["confirmPassword"]}
                    type="password"
                    value={newDriver.confirmPassword}
                    onChange={(event) =>
                      setNewDriver({
                        ...newDriver,
                        confirmPassword: event.target.value,
                      })
                    }
                    placeholder="Re-enter password"
                    required
                  />
                </div>

                {newDriver.confirmPassword && (
                  <p
                    className={`text-xs font-medium ${
                      newDriver.password === newDriver.confirmPassword
                        ? "text-emerald-600"
                        : "text-red-600"
                    }`}
                  >
                    {newDriver.password === newDriver.confirmPassword
                      ? "✓ Passwords match"
                      : "Passwords do not match"}
                  </p>
                )}

                <p className="text-xs text-slate-500">
                  Password must contain at least 8 characters.
                </p>
              </section>}

              {/* DRIVER INFORMATION */}

              <section className="space-y-4">
                <div>
                  <h3 className="text-sm font-bold text-slate-900">
                    Driver Information
                  </h3>

                  <p className="mt-1 text-xs text-slate-500">
                    Identification, driving licence and assignment details.
                  </p>
                </div>

                <div className="grid gap-4 sm:grid-cols-2">

                  <Field
                    label="Driving License No"
                    error={fieldErrors["licenseNo"]}
                    value={newDriver.licenseNo}
                    onChange={(event) =>
                      setNewDriver({
                        ...newDriver,
                        licenseNo: event.target.value,
                      })
                    }
                    placeholder="Enter driving licence number"
                    required={!editing}
                  />

                  <Field
                    label="License Expiry Date"
                    error={fieldErrors["licenseExpiry"]}
                    type="date"
                    required={!editing}
                    value={newDriver.licenseExpiry}
                    onChange={(event) =>
                      setNewDriver({
                        ...newDriver,
                        licenseExpiry: event.target.value,
                      })
                    }
                  />

                  <label className="block">
                    <span className="mb-2 block text-sm font-medium text-slate-700">
                      Driver Type
                    </span>

                    <select
                      value={newDriver.driverType}
                      aria-invalid={Boolean(fieldErrors.driverType)}
                      onChange={(event) =>
                        setNewDriver({
                          ...newDriver,
                          driverType: event.target.value,
                        })
                      }
                      className="w-full rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm outline-none focus:border-teal-600 focus:ring-4 focus:ring-teal-600/10"
                    >
                      {DRIVER_TYPES.map(type => <option key={type} value={type}>{type}</option>)}
                    </select>
                    {fieldErrors.driverType && <span role="alert" className="mt-1 block text-xs text-red-600">{fieldErrors.driverType}</span>}
                  </label>
                  <div className="sm:col-span-2">
                    <DocumentUploadField
                      label="Driving Licence Document"
                      file={newDriver.licenseDocumentFile}
                      onChange={(file) => setNewDriver(previous => ({
                        ...previous, licenseDocumentFile: file,
                      }))}
                      currentDocument={newDriver.licenseDocument}
                      progress={uploadProgress}
                      error={fieldErrors["licenseDocument"]}
                      required={!editing}
                    />
                  </div>
                </div>
              </section>

              {/* VEHICLE INFORMATION */}

              <section className="space-y-4 border-t border-slate-100 pt-6">
                <div>
                  <h3 className="text-sm font-bold text-slate-900">
                    Vehicle Information
                  </h3>
                  <p className="mt-1 text-xs text-slate-500">
                    Vehicle details linked to this driver account.
                  </p>
                </div>

                {editing && !newDriver.vehicle && (
                  <p className="rounded-lg bg-amber-50 p-3 text-sm text-amber-800">
                    This legacy driver has no registered vehicle. You can still update the driver profile.
                  </p>
                )}
                {newDriver.vehicle && <fieldset disabled={editing && !newDriver.updateVehicle} className="disabled:opacity-60">
                <div className="grid gap-4 sm:grid-cols-2">
                  <Field
                    label="Vehicle Model"
                    error={fieldErrors["vehicle.model"]}
                    value={newDriver.vehicle.model}
                    onChange={(event) =>
                      setNewDriver((previous) => ({
                        ...previous,
                        vehicle: {
                          ...previous.vehicle,
                          model: event.target.value,
                        },
                      }))
                    }
                    placeholder="e.g. Toyota Hiace"
                    required
                  />

                  <Field
                    label="Vehicle Registration Number"
                    error={fieldErrors["vehicle.plate"]}
                    value={newDriver.vehicle.plate}
                    onChange={(event) =>
                      setNewDriver((previous) => ({
                        ...previous,
                        vehicle: {
                          ...previous.vehicle,
                          plate: event.target.value.toUpperCase(),
                        },
                      }))
                    }
                    placeholder="e.g. MCA1234"
                    required
                  />

                  <label className="block">
                    <span className="mb-2 block text-sm font-medium text-slate-700">
                      Vehicle Category <span className="ml-1 text-red-500">*</span>
                    </span>
                    <select
                      required={!editing || newDriver.updateVehicle}
                      aria-invalid={Boolean(fieldErrors["vehicle.categoryId"])}
                      value={newDriver.vehicle.categoryId}
                      onChange={(event) => {
                        const categoryId = event.target.value;
                        const category = vehicleCategories.find(item =>
                          String(item.vehicleCategoryId) === categoryId);
                        const maxCapacity = category?.maxPassengerCapacity ?? "";
                        setNewDriver(previous => ({
                          ...previous,
                          vehicle: {
                            ...previous.vehicle,
                            categoryId,
                            type: category?.displayName || "",
                            capacity: maxCapacity,
                            luggageCapacity: category?.bootCapacityUnits ?? "",
                          },
                        }));
                      }}
                      className="w-full rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm outline-none focus:border-teal-600 focus:ring-4 focus:ring-teal-600/10"
                    >
                      <option value="">Select a vehicle category</option>
                      {vehicleCategories.map(category => (
                        <option key={category.vehicleCategoryId} value={category.vehicleCategoryId}>
                          {category.displayName} · up to {category.maxPassengerCapacity} passengers
                        </option>
                      ))}
                    </select>
                    {fieldErrors["vehicle.categoryId"] && <span role="alert" className="mt-1 block text-xs text-red-600">{fieldErrors["vehicle.categoryId"]}</span>}
                  </label>

                  <Field
                    label="Maximum Passengers"
                    error={fieldErrors["vehicle.capacity"]}
                    type="number"
                    value={newDriver.vehicle.capacity}
                    readOnly
                  />

                  <Field
                    label="Luggage Capacity (Units)"
                    error={fieldErrors["vehicle.luggageCapacity"]}
                    type="number"
                    value={newDriver.vehicle.luggageCapacity}
                    readOnly
                  />

                  <div className="sm:col-span-2">
                    <details className="text-xs text-slate-600">
                      <summary className="cursor-pointer font-medium text-teal-800">How luggage capacity works</summary>
                      <p className="mt-1">Small bags use 1 unit, medium bags use 2, and large bags use 3. The vehicle category sets the limit separately from passenger capacity.</p>
                    </details>
                  </div>

                  <Field
                    label="Road Tax Expiry Date"
                    error={fieldErrors["vehicle.roadTaxExpiry"]}
                    type="date"
                    required={!editing || newDriver.updateVehicle}
                    value={newDriver.vehicle.roadTaxExpiry}
                    onChange={(event) =>
                      setNewDriver((previous) => ({
                        ...previous,
                        vehicle: {
                          ...previous.vehicle,
                          roadTaxExpiry: event.target.value,
                        },
                      }))
                    }
                  />

                  <Field
                    label="Next Service Date"
                    error={fieldErrors["vehicle.serviceExpiry"]}
                    type="date"
                    required={!editing || newDriver.updateVehicle}
                    value={newDriver.vehicle.serviceExpiry}
                    onChange={(event) =>
                      setNewDriver((previous) => ({
                        ...previous,
                        vehicle: {
                          ...previous.vehicle,
                          serviceExpiry: event.target.value,
                        },
                      }))
                    }
                  />

                  {editing && <label className="block">
                    <span className="mb-2 block text-sm font-medium text-slate-700">
                      Vehicle Status
                      <span className="ml-1 text-red-500">*</span>
                    </span>
                    <select
                      value={newDriver.vehicle.status}
                      onChange={(event) =>
                        setNewDriver((previous) => ({
                          ...previous,
                          vehicle: {
                            ...previous.vehicle,
                            status: event.target.value,
                          },
                        }))
                      }
                      className="w-full rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm outline-none focus:border-teal-600 focus:ring-4 focus:ring-teal-600/10"
                    >
                      {VEHICLE_STATUSES.map(status => <option key={status} value={status}>{status}</option>)}
                    </select>
                  </label>}
                  <label className="block">
                    <span className="mb-2 block text-sm font-medium text-slate-700">
                      Vehicle Ownership <span className="ml-1 text-red-500">*</span>
                    </span>
                    <select
                      required={!editing || newDriver.updateVehicle}
                      value={newDriver.vehicle.ownership}
                      aria-invalid={Boolean(fieldErrors["vehicle.ownership"])}
                      onChange={(event) => setNewDriver(previous => ({
                        ...previous, vehicle: { ...previous.vehicle, ownership: event.target.value },
                      }))}
                      className="w-full rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm outline-none focus:border-teal-600 focus:ring-4 focus:ring-teal-600/10"
                    >
                      <option value="">Select ownership</option>
                      <option value="Driver-Owned">Driver-Owned</option>
                      <option value="Company-Owned">Company-Owned</option>
                    </select>
                    {fieldErrors["vehicle.ownership"] && <span role="alert" className="mt-1 block text-xs text-red-600">{fieldErrors["vehicle.ownership"]}</span>}
                  </label>
                  <div className="sm:col-span-2">
                    <DocumentUploadField
                      label="Road Tax Document"
                      file={newDriver.vehicle.roadTaxDocumentFile}
                      onChange={(file) => setNewDriver(previous => ({
                        ...previous,
                        vehicle: { ...previous.vehicle, roadTaxDocumentFile: file },
                      }))}
                      currentDocument={newDriver.roadTaxDocument}
                      progress={uploadProgress}
                      error={fieldErrors["roadTaxDocument"]}
                      required={!editing}
                    />
                  </div>
                </div>
                </fieldset>}
              </section>

              {editing && <section className="space-y-4 border-t border-slate-100 pt-6">
                <h3 className="text-sm font-bold text-slate-900">Driver Status</h3>
                <div className="grid gap-4 sm:grid-cols-2">
                  {[["availability", "Driver Availability", AVAILABILITY_OPTIONS],
                    ["accountStatus", "Account Status", ACCOUNT_STATUSES]].map(([key, label, options]) => (
                    <label key={key} className="block">
                      <span className="mb-2 block text-sm font-medium text-slate-700">{label}</span>
                      <select value={newDriver[key]}
                        onChange={event => setNewDriver(previous => ({...previous, [key]: event.target.value}))}
                        className="w-full rounded-xl border border-slate-200 bg-white px-4 py-3 text-sm outline-none focus:border-teal-600 focus:ring-4 focus:ring-teal-600/10">
                        {options.map(option => <option key={option}>{option}</option>)}
                      </select>
                    </label>
                  ))}
                </div>
              </section>}
  </>;
}

function driverMultipartForm(payload, files) {
  const form = new FormData();
  form.append("driver", new Blob([JSON.stringify(payload)], { type: "application/json" }));
  for (const [part, file] of Object.entries(files)) {
    if (file) form.append(part, file);
  }
  return form;
}

export default function Drivers({ recordFocus, onRecordOpened }) {
  const [driverList, setDriverList] = useState([]);
  const [vehicleCategories, setVehicleCategories] = useState([]);
  const [loading, setLoading] = useState(true);

  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("All");
  const [accountFilter, setAccountFilter] = useState("All");

  const [selectedDriver, setSelectedDriver] = useState(null);
  const [editingDriver, setEditingDriver] = useState(null);
  const [loadingDriver, setLoadingDriver] = useState(false);
  const editRequest = useRef(0);
  const editAbort = useRef(null);
  const [showCreateModal, setShowCreateModal] = useState(false);

  const [newDriver, setNewDriver] = useState(emptyDriver);
  const [savingDriver, setSavingDriver] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(-1);

  const [error, setError] = useState("");
  const [successMessage, setSuccessMessage] = useState("");
  const [fieldErrors, setFieldErrors] = useState({});
  const driverForm = useRef(null);
  const creatingDriver = useRef(false);
  useEffect(() => {
    if (!Object.keys(fieldErrors).length) return;
    const first = driverForm.current?.querySelector('[aria-invalid="true"]');
    if (!first) return;
    (first.closest("label") ?? first).scrollIntoView({ block: "center" });
    first.focus({ preventScroll: true });
  }, [fieldErrors]);

  const formDriver = editingDriver || newDriver;
  const setFormDriver = editingDriver ? setEditingDriver : setNewDriver;

  // ==========================================
  // FETCH DRIVERS FROM BACKEND
  // ==========================================

  useEffect(() => {
    const controller = new AbortController();
    fetchDrivers({signal: controller.signal}).catch((fetchError) => {
      if (controller.signal.aborted || fetchError.code === "ERR_CANCELED") return;
      setError(getApiErrorMessage(fetchError, fetchError.message || "Failed to load drivers. Please try again."));
    });
    return () => {
      controller.abort();
      editAbort.current?.abort();
      editRequest.current++;
    };
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    api.get("/vehicles/categories", { signal: controller.signal })
      .then(({ data }) => {
        if (!controller.signal.aborted && Array.isArray(data)) setVehicleCategories(data);
      })
      .catch((requestError) => {
        if (requestError.code !== "ERR_CANCELED") {
          setError(getApiErrorMessage(requestError, "Vehicle categories could not be loaded."));
        }
      });
    return () => controller.abort();
  }, []);

  const fetchDrivers = async (config = {}) => {
    setLoading(true);
    try {
      const result = await loadDriverManagement(api, config);
      if (config.signal?.aborted) return;
      setDriverList(result.drivers.map(driver => ({
        ...formatDriver(driver),
        vehicle: result.vehicles === null ? "Vehicle data unavailable" : result.vehicles
          .filter(vehicle => vehicle.driverId === driver.driverId)
          .map(vehicle => vehicle.vehiclePlateno).filter(Boolean).join(", "),
      })));
      setError(result.errors.map(requestError => getApiErrorMessage(requestError,
        "Some driver management data could not be loaded.")).join(" "));
    } finally {
      if (!config.signal?.aborted) setLoading(false);
    }
  };

  // ==========================================
  // DASHBOARD STATISTICS
  // ==========================================

  const totalDrivers = driverList.length;

  const activeAccounts = driverList.filter(
    (driver) => driver.accountStatus === "Active"
  ).length;

  const disabledAccounts = driverList.filter(
    (driver) => driver.accountStatus === "Disabled"
  ).length;

  const availableDrivers = driverList.filter(
    (driver) =>
      driver.availability === "Available" &&
      driver.accountStatus === "Active"
  ).length;

  const busyDrivers = driverList.filter(
    (driver) =>
      driver.availability === "Assigned" ||
      driver.availability === "On Trip" ||
      driver.availability === "Busy"
  ).length;

  // ==========================================
  // SEARCH AND FILTER
  // ==========================================

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
      statusFilter === "All" ||
      driver.availability === statusFilter;

    const matchesAccount =
      accountFilter === "All" ||
      driver.accountStatus === accountFilter;

    return matchesSearch && matchesAvailability && matchesAccount;
  });

  // ==========================================
  // VALIDATION
  // ==========================================

  // ==========================================
  // CREATE DRIVER
  // ==========================================

  const setFormRequestError = requestError => {
    const fields = driverRequestFieldErrors(requestError.response?.data);
    setFieldErrors(fields);
    return Object.keys(fields).length > 0;
  };

  const getRequestError = (requestError, fallback) => {
    return getApiErrorMessage(requestError, fallback);
  };

  const handleCreateDriver = async (event) => {
    event.preventDefault();
    if (creatingDriver.current) return;
    setError("");
    setSuccessMessage("");

    const validationError = validateDriverFields(newDriver, null, driverList);
    for (const [field, value] of [
      ["licenseDocument", newDriver.licenseDocumentFile],
      ["roadTaxDocument", newDriver.vehicle?.roadTaxDocumentFile],
    ]) {
      if (!value?.size) validationError[field] = "Select a non-empty document.";
    }
    setFieldErrors(validationError);
    if (Object.keys(validationError).length) {
      setError("Please correct the highlighted fields.");
      return;
    }

    setSavingDriver(true);
    creatingDriver.current = true;
    setUploadProgress(0);
    try {
      const payload = {
        ...toDriverCreatePayload(newDriver),
        driverPassword: newDriver.password,
      };
      await api.post("/drivers", driverMultipartForm(payload, {
        icDocument: newDriver.icDocumentFile,
        licenseDocument: newDriver.licenseDocumentFile,
        roadTaxDocument: newDriver.vehicle?.roadTaxDocumentFile,
      }), {
        onUploadProgress: (event) => {
          if (event.total) setUploadProgress(Math.round((event.loaded / event.total) * 100));
        },
      });
      setNewDriver(emptyDriver);
      setShowCreateModal(false);
      setSearch("");
      setStatusFilter("All");
      setAccountFilter("All");
      setSuccessMessage("Driver account created successfully.");
      window.dispatchEvent(new Event("diladrive:fleet-changed"));
      try { await fetchDrivers(); } catch {
        setError("Driver was created, but the list could not be refreshed. Please reload the page.");
      }
    } catch (requestError) {
      const hasFieldErrors = setFormRequestError(requestError);
      setError(hasFieldErrors ? "Please correct the highlighted fields."
        : getRequestError(requestError, "Failed to create driver account."));
    } finally {
      creatingDriver.current = false;
      setSavingDriver(false);
      setUploadProgress(-1);
    }
  };

  // ==========================================
  // UPDATE DRIVER
  // ==========================================

  const handleUpdateDriver = async (event) => {
    event.preventDefault();

    setError("");
    setSuccessMessage("");

    const validationError = validateDriverFields(editingDriver, editingDriver.id, driverList);

    setFieldErrors(validationError);
    if (Object.keys(validationError).length) {
      setError("Please correct the highlighted fields.");
      return;
    }

    setSavingDriver(true);
    setUploadProgress(0);
    try {
      const payload = toDriverCreatePayload(editingDriver);
      delete payload.driverIcno;
      if (editingDriver.icNumber?.trim()) {
        payload.icNumber = editingDriver.icNumber.trim().replace(/\s+/g, "").replace(/-/g, "");
      }
      await api.put(`/drivers/${editingDriver.id}`, driverMultipartForm(payload, {
        icDocument: editingDriver.icDocumentFile,
        licenseDocument: editingDriver.licenseDocumentFile,
        roadTaxDocument: editingDriver.vehicle?.roadTaxDocumentFile,
      }), {
        onUploadProgress: (event) => {
          if (event.total) setUploadProgress(Math.round((event.loaded / event.total) * 100));
        },
      });
      setSelectedDriver(null);
      setEditingDriver(null);
      setSuccessMessage("Driver details updated successfully.");
      window.dispatchEvent(new Event("diladrive:fleet-changed"));
      try {
        await fetchDrivers();
      } catch (refreshError) {
        setError("Changes were saved, but the driver list could not be refreshed. Please reload the page.");
      }
    } catch (requestError) {
      setFormRequestError(requestError);
      setError(getRequestError(requestError, "Failed to update driver and vehicle."));
    } finally { setSavingDriver(false); setUploadProgress(-1); }
  };

  const openEditDriver = async (driver) => {
    const requestId = ++editRequest.current;
    editAbort.current?.abort();
    const controller = new AbortController();
    editAbort.current = controller;
    setSelectedDriver(null);
    setError("");
    setSuccessMessage("");
    setLoadingDriver(true);
    try {
      const { data } = await api.get(`/drivers/${driver.id}`, {signal: controller.signal});
      if (requestId !== editRequest.current) return;
      setFieldErrors({});
      setEditingDriver(toDriverEditForm(data));
    } catch (requestError) {
      if (requestError.code !== "ERR_CANCELED" && requestId === editRequest.current) setError(getRequestError(requestError, requestError.message || "Failed to load driver details."));
    } finally {
      if (requestId === editRequest.current) setLoadingDriver(false);
    }
  };

  const openViewDriver = async (driver) => {
    setError("");
    setSelectedDriver({ ...driver, loadingDetails: true });
    try {
      const { data } = await api.get(`/drivers/${driver.id}`);
      setSelectedDriver({
        ...formatDriver(data),
        vehicleDetails: data.vehicle,
        icDocument: data.icDocument,
        licenseDocument: data.licenseDocument,
        roadTaxDocument: data.roadTaxDocument,
      });
    } catch (requestError) {
      setSelectedDriver(null);
      setError(getApiErrorMessage(requestError, "Driver details could not be loaded."));
    }
  };

  useRecordFocus({ id: recordFocus, items: driverList, idKey: "id", loading,
    error, open: openViewDriver, complete: onRecordOpened });

  // ==========================================
  // ACTIVATE / DISABLE DRIVER ACCOUNT
  // ==========================================

  const handleAccountStatus = async (driver) => {
    setError("");
    setSuccessMessage("");

    let nextStatus;

    if (driver.accountStatus === "Active") {
      const confirmed = window.confirm(
        `Disable the account for ${driver.name}?`
      );

      if (!confirmed) return;

      nextStatus = "Disabled";
    } else if (driver.accountStatus === "Disabled") {
      const confirmed = window.confirm(
        `Enable the account for ${driver.name}?`
      );

      if (!confirmed) return;

      nextStatus = "Active";
    } else {
      const confirmed = window.confirm(
        `Enable the account for ${driver.name}?`
      );

      if (!confirmed) return;

      nextStatus = "Active";
    }

    try {
      const updatedDriver = { ...driver, accountStatus: nextStatus };
      const response = await api.put(
        `/drivers/${driver.id}`,
        toDriverPayload(updatedDriver)
      );
      const savedDriver = {...formatDriver(response.data), vehicle: driver.vehicle};
      setDriverList((previous) =>
        previous.map((item) =>
          item.id === driver.id ? savedDriver : item
        )
      );
      setSelectedDriver((previous) =>
        previous?.id === driver.id ? savedDriver : previous
      );
      setSuccessMessage(
        `${driver.name}'s account status is now ${nextStatus}.`
      );
    } catch (requestError) {
      console.error("Error changing driver account status:", requestError);
      setError(
        getRequestError(requestError, "Failed to update driver account status.")
      );
    }
  };

  // ==========================================
  // CLOSE MODALS
  // ==========================================

  const closeModals = () => {
    if (savingDriver) return;
    editRequest.current++;
    editAbort.current?.abort();
    setLoadingDriver(false);
    setShowCreateModal(false);
    setSelectedDriver(null);
    setEditingDriver(null);
    setError("");
  };

  // ==========================================
  // MAIN UI
  // ==========================================

  return (
    <div className="admin-page">

      {/* PAGE HEADER */}

      <div className="admin-page-header">
        <div>
          <div className="mb-2 flex items-center gap-2 text-sm font-medium text-teal-700">
            <ShieldCheck size={17} />
            Fleet Operations
          </div>

          <h1 className="admin-page-title">
            Driver Management
          </h1>

          <p className="admin-page-description">
            Manage driver profiles, accounts and availability.
          </p>
        </div>

        <button
          disabled={loading}
          onClick={() => {
            setError("");
            setSuccessMessage("");
            setNewDriver(emptyDriver);
            setFieldErrors({});
            setShowCreateModal(true);
          }}
          className="inline-flex items-center justify-center gap-2 rounded-xl bg-teal-700 px-5 py-3 text-sm font-semibold text-white shadow-sm transition hover:bg-teal-800"
        >
          <UserPlus size={18} />
          Create Driver Account
        </button>
      </div>

      {/* SUCCESS MESSAGE */}

      {successMessage && (
        <div className="flex items-start gap-3 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800">
          <Check size={18} className="mt-0.5 shrink-0" />

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

      {error && !showCreateModal && !selectedDriver && !editingDriver && (
        <div className="flex items-start gap-3 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-700">
          <p>{error}</p>
          <button
            onClick={() => setError("")}
            className="ml-auto hover:text-red-900"
            aria-label="Dismiss error"
          >
            <X size={17} />
          </button>
        </div>
      )}

      {/* SUMMARY CARDS */}

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
          title="Available Drivers"
          value={availableDrivers}
          description="Active accounts available for assignment"
          icon={Clock3}
          iconStyle="bg-amber-50 text-amber-700"
        />

        <SummaryCard
          title="Disabled Accounts"
          value={disabledAccounts}
          description="Login access disabled"
          icon={LockKeyhole}
          iconStyle="bg-red-50 text-red-700"
        />
      </div>

      {/* AVAILABILITY SUMMARY */}

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
          <p className="text-xs text-slate-500">Currently Busy</p>

          <p className="mt-1 text-lg font-bold text-amber-700">
            {busyDrivers}
          </p>
        </div>

        <p className="ml-auto max-w-sm text-xs leading-5 text-slate-500">
          Drivers need an active account and a clear schedule to
          be eligible for new assignments.
        </p>
      </div>

      {/* DRIVER TABLE */}

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

          {/* SEARCH AND FILTERS */}

          <div className="flex flex-col gap-3 lg:flex-row">
            <div className="relative flex-1">
              <Search
                size={18}
                className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400"
              />

              <input
                type="search"
                placeholder="Search name, email or vehicle..."
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
                {[...new Set(driverList.map(driver => driver.availability).filter(Boolean))].sort().map(status => <option key={status} value={status}>{status}</option>)}
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
              {[...new Set(driverList.map(driver => driver.accountStatus).filter(Boolean))].sort().map(status => <option key={status} value={status}>{status}</option>)}
            </select>
          </div>
        </div>

        {/* TABLE */}

        <div className="overflow-x-auto">
          <table className="w-full min-w-[1200px] text-left">
            <thead className="bg-slate-50">
              <tr className="text-xs uppercase tracking-wider text-slate-500">
                <th className="px-6 py-4 font-semibold">No.</th>
                <th className="px-6 py-4 font-semibold">Driver</th>
                <th className="px-6 py-4 font-semibold">Contact</th>
                <th className="px-6 py-4 font-semibold">IC Number</th>
                <th className="px-6 py-4 font-semibold">Vehicle</th>
                <th className="px-6 py-4 font-semibold">Availability</th>
                <th className="px-6 py-4 font-semibold">Account Status</th>
                <th className="px-6 py-4 text-right font-semibold">Actions</th>
              </tr>
            </thead>

            <tbody className="divide-y divide-slate-100">
              {filteredDrivers.map((driver, index) => (
                <tr
                  key={driver.id}
                  className="transition hover:bg-slate-50"
                >
                  <td className="px-6 py-5 text-sm font-medium text-slate-500">
                    {index + 1}
                  </td>
                  <td className="px-6 py-5">
                    <div className="flex items-center gap-3">
                      <DriverAvatar name={driver.name} />

                      <div>
                        <p className="font-semibold text-slate-900">
                          {driver.name}
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

                  <td className="px-6 py-5 text-sm text-slate-600">
                    {driver.icNumberMasked || "Not recorded"}
                  </td>

                  <td className="px-6 py-5">
                    <div className="flex items-center gap-2 text-sm text-slate-700">
                      <CarFront size={17} className="text-slate-400" />
                      {driver.vehicle || "Not assigned"}
                    </div>
                  </td>

                  <td className="px-6 py-5">
                    <StatusBadge status={driver.availability} />
                  </td>

                  <td className="px-6 py-5">
                    <AccountBadge status={driver.accountStatus} />
                  </td>

                  <td className="px-6 py-5">
                    <div className="flex justify-end gap-2">
                      <button
                        onClick={() => openViewDriver(driver)}
                        aria-label={`View ${driver.name}`}
                        title="View driver"
                        className="rounded-lg border border-slate-200 p-2 text-slate-600 transition hover:bg-slate-100"
                      >
                        <Eye size={16} />
                      </button>

                      <button
                        onClick={() => {
                          setError("");
                          openEditDriver(driver);
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
                  <td colSpan={8} className="px-6 py-16 text-center">
                    <Users
                      size={32}
                      className="mx-auto text-slate-300"
                    />

                    <p className="mt-3 font-semibold text-slate-800">
                      {loading ? "Loading drivers..." : "No drivers found"}
                    </p>

                    {!loading && (
                      <>
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
                      </>
                    )}
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

      {/* ======================================
          CREATE DRIVER ACCOUNT MODAL
      ====================================== */}

      {(showCreateModal || editingDriver) && (
        <Modal
          title={editingDriver ? "Update Driver" : "Create Driver Account"}
          subtitle={editingDriver ? "Update driver and assigned vehicle details." : "Enter the driver's details and create their login credentials."}
          onClose={closeModals}
          wide
        >
          <form ref={driverForm} noValidate onSubmit={editingDriver ? handleUpdateDriver : handleCreateDriver} className="flex min-h-0 flex-col">
            <div className="min-h-0 space-y-6 overflow-y-auto overscroll-contain p-5 sm:p-6">

              <DriverFormFields
                newDriver={formDriver}
                setNewDriver={setFormDriver}
                editing={!!editingDriver}
                fieldErrors={fieldErrors}
                vehicleCategories={vehicleCategories}
                uploadProgress={uploadProgress}
              />

              {/* ERROR MESSAGE */}

              {error && (
                <p className="rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700">
                  {error}
                </p>
              )}
            </div>

            {/* FIXED FOOTER */}

            <div className="flex shrink-0 justify-end gap-3 border-t border-slate-200 bg-slate-50 p-5">
              <button
                type="button"
                onClick={closeModals}
                disabled={savingDriver}
                className="rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-medium text-slate-700 hover:bg-white"
              >
                Cancel
              </button>

              <button
                type="submit"
                disabled={savingDriver}
                className="inline-flex items-center gap-2 rounded-xl bg-teal-700 px-5 py-2.5 text-sm font-semibold text-white hover:bg-teal-800 disabled:cursor-not-allowed disabled:opacity-60"
              >
                <UserPlus size={17} />
                {savingDriver ? (editingDriver ? "Saving..." : "Creating...") : (editingDriver ? "Save Changes" : "Create Account")}
              </button>
            </div>
          </form>
        </Modal>
      )}

      {/* ======================================
          VIEW DRIVER MODAL
      ====================================== */}

      {selectedDriver && (
        <Modal
          title="Driver Account Details"
          subtitle="Driver contact, vehicle, and account information"
          onClose={() => setSelectedDriver(null)}
        >
          <div className="min-h-0 space-y-5 overflow-y-auto overscroll-contain p-6">
            <div className="flex items-center gap-4 rounded-xl bg-slate-50 p-4">
              <DriverAvatar name={selectedDriver.name} />

              <div className="min-w-0">
                <h3 className="font-bold text-slate-900">
                  {selectedDriver.name}
                </h3>

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
                icon={ShieldCheck}
                label="IC Number"
                value={selectedDriver.icNumberMasked || "Not recorded"}
              />

              <Detail
                icon={KeyRound}
                label="Driving License No"
                value={selectedDriver.licenseNo}
              />

              <Detail
                icon={Clock3}
                label="License Expiry"
                value={selectedDriver.licenseExpiry}
              />

              <Detail
                icon={UserCheck}
                label="Driver Type"
                value={selectedDriver.driverType}
              />

              <Detail
                icon={CarFront}
                label="Assigned Vehicle"
                value={selectedDriver.vehicle || "Not assigned"}
              />

              {selectedDriver.vehicleDetails && (
                <>
                  <Detail icon={CarFront} label="Vehicle Model"
                    value={selectedDriver.vehicleDetails.vehicleModel} />
                  <Detail icon={Users} label="Vehicle Category"
                    value={selectedDriver.vehicleDetails.vehicleType} />
                  <Detail icon={Users} label="Passenger Capacity"
                    value={selectedDriver.vehicleDetails.vehicleCapacity} />
                  <Detail icon={Users} label="Boot Capacity (Luggage Units)"
                    value={selectedDriver.vehicleDetails.bootCapacityUnits ?? selectedDriver.vehicleDetails.vehicleLuggageCapacity} />
                  <Detail icon={ShieldCheck} label="Vehicle Ownership"
                    value={selectedDriver.vehicleDetails.vehicleOwnership?.replaceAll("_", "-")} />
                  <Detail icon={Clock3} label="Road Tax Expiry"
                    value={selectedDriver.vehicleDetails.vehicleRoadtaxexpiry} />
                  <Detail icon={Clock3} label="Next Service Date"
                    value={selectedDriver.vehicleDetails.vehicleServiceexpiry} />
                </>
              )}

              <Detail
                icon={UserCheck}
                label="Availability"
                value={selectedDriver.availability}
              />
            </div>

            <section className="space-y-4 rounded-xl border border-slate-200 p-4">
              <div>
                <h3 className="text-sm font-semibold text-slate-900">Identity Documents</h3>
                <p className="mt-1 text-xs text-slate-500">
                  Full IC numbers are masked. Previews are retrieved through authenticated admin endpoints.
                </p>
              </div>
              <div>
                <p className="mb-1 text-xs font-medium text-slate-600">IC document</p>
                <SecureDocumentPreview documentInfo={selectedDriver.icDocument} />
              </div>
              <div>
                <p className="mb-1 text-xs font-medium text-slate-600">Driving licence</p>
                <SecureDocumentPreview documentInfo={selectedDriver.licenseDocument} />
              </div>
              <div>
                <p className="mb-1 text-xs font-medium text-slate-600">Vehicle road tax</p>
                <SecureDocumentPreview documentInfo={selectedDriver.roadTaxDocument} />
              </div>
            </section>

            <AssignmentSchedule driverId={selectedDriver.id} />
            <UnavailablePeriods resource="DRIVER" resourceId={selectedDriver.id} />
            <div className="rounded-xl border border-slate-200 p-4">
              <p className="text-sm font-semibold text-slate-800">
                Account Access
              </p>

              <p className="mt-1 text-sm leading-5 text-slate-500">
                {selectedDriver.accountStatus === "Active"
                  ? "This account is marked as active in the management interface."
                  : selectedDriver.accountStatus === "Disabled"
                    ? "This account is marked as disabled."
                    : "Enable or disable this legacy account to set its access status."}
              </p>

              <p className="mt-2 text-xs leading-5 text-slate-400">
                Active accounts can sign in. Disabled accounts cannot sign in.
              </p>
            </div>
          </div>

          <div className="flex shrink-0 flex-wrap justify-end gap-3 border-t border-slate-200 p-5">
            <button
              onClick={() => setSelectedDriver(null)}
              className="rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-medium text-slate-700 hover:bg-slate-50"
            >
              Close
            </button>

            <button
              onClick={() => handleAccountStatus(selectedDriver)}
              className="inline-flex items-center gap-2 rounded-xl border border-slate-200 px-4 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-50"
            >
              <Power size={16} />

              {selectedDriver.accountStatus === "Active"
                ? "Disable"
                : selectedDriver.accountStatus === "Disabled"
                  ? "Enable"
                  : "Enable"}
            </button>
          </div>
        </Modal>
      )}

      {/* ======================================
          EDIT DRIVER MODAL
      ====================================== */}

      {loadingDriver && <Modal title="Loading Driver" subtitle="Fetching driver and assigned vehicle details..." onClose={closeModals} wide>
        <p role="status" className="p-6 text-sm text-slate-600">Loading driver details...</p>
      </Modal>}
    </div>
  );
}
