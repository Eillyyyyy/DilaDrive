export const DRIVER_TYPES = ["Company Driver", "Associate Driver"];
export const ACCOUNT_STATUSES = ["Active", "Disabled"];
export const AVAILABILITY_OPTIONS = ["Available", "Busy", "Unavailable"];
export const VEHICLE_STATUSES = ["Active", "Maintenance", "Inactive"];
export const emptyVehicle = {
  model: "",
  plate: "",
  type: "",
  categoryId: "",
  ownership: "Driver-Owned",
  capacity: "",
  luggageCapacity: "",
  roadTaxExpiry: "",
  serviceExpiry: "",
  roadTaxDocumentFile: null,
  roadTaxDocument: null,
  status: "Active",
};
export const emptyDriver = {
  name: "",
  email: "",
  phone: "",
  icNumber: "",
  icNumberMasked: "",
  icDocumentFile: null,
  icDocument: null,
  licenseNo: "",
  licenseExpiry: "",
  licenseDocumentFile: null,
  licenseDocument: null,
  accountStatus: "Active",
  driverType: "Company Driver",
  vehicle: { ...emptyVehicle },
  availability: "Available",
  password: "",
  confirmPassword: "",
};

export const formatDriver = (driver) => ({
  id: driver.driverId,
  name: driver.driverName || "",
  email: driver.driverEmail || "",
  phone: driver.driverPhoneno || "",
  icNumberMasked: driver.driverIcnoMasked || "",
  licenseNo: driver.driverLicenseno || "",
  licenseExpiry: driver.driverLicenseExpiry || "",
  driverType: driver.driverType || "",
  vehicle: driver.vehicle?.vehiclePlateno || driver.vehiclePlateno || "",
  availability:
    {
      AVAILABLE: "Available",
      BUSY: "Busy",
      ASSIGNED: "Busy",
      ON_TRIP: "Busy",
      "ON TRIP": "Busy",
      UNAVAILABLE: "Unavailable",
      OFF_DUTY: "Unavailable",
      "OFF DUTY": "Unavailable",
      OFFLINE: "Unavailable",
    }[driver.driverAvailability?.toUpperCase()] ||
    driver.driverAvailability ||
    "",
  accountStatus:
    { ACTIVE: "Active", DISABLED: "Disabled" }[
      driver.driverStatus?.toUpperCase()
    ] ||
    driver.driverStatus ||
    "Not recorded",
});

export const toDriverEditForm = (data) => ({
  ...formatDriver(data),
  icNumber: "",
  icNumberMasked: data.driverIcnoMasked || "",
  icDocument: data.icDocument || null,
  licenseDocument: data.licenseDocument || null,
  roadTaxDocument: data.roadTaxDocument || null,
  updateVehicle: false,
  vehicle: data.vehicle
    ? {
        model: data.vehicle.vehicleModel ?? "",
        plate: data.vehicle.vehiclePlateno ?? "",
        type: data.vehicle.vehicleType ?? "",
        categoryId: data.vehicle.vehicleCategoryId ?? "",
        ownership: data.vehicle.vehicleOwnership === "COMPANY_OWNED"
          ? "Company-Owned" : data.vehicle.vehicleOwnership === "DRIVER_OWNED"
            ? "Driver-Owned" : "",
        capacity: data.vehicle.vehicleCapacity ?? "",
        luggageCapacity: data.vehicle.bootCapacityUnits ?? data.vehicle.vehicleLuggageCapacity ?? "",
        roadTaxExpiry: data.vehicle.vehicleRoadtaxexpiry ?? "",
        serviceExpiry: data.vehicle.vehicleServiceexpiry ?? "",
        roadTaxDocument: data.roadTaxDocument || null,
        status:
          data.vehicle.vehicleStatus === "AVAILABLE" || data.vehicle.vehicleStatus === "ACTIVE"
            ? "Active"
            : data.vehicle.vehicleStatus === "MAINTENANCE"
              ? "Maintenance"
              : "Inactive",
      }
    : null,
});

export const toDriverPayload = (driver) => ({
  driverName: driver.name.trim(),
  driverEmail: driver.email.trim().toLowerCase(),
  driverPhoneno: driver.phone.trim(),
  driverLicenseno: driver.licenseNo?.trim() || "",
  driverLicenseExpiry: driver.licenseExpiry || "",
  driverType: driver.driverType || "Company Driver",
  driverAvailability: driver.availability?.toUpperCase() || "AVAILABLE",
  // Omit unknown legacy statuses until the administrator explicitly enables/disables the account.
  ...(ACCOUNT_STATUSES.includes(driver.accountStatus)
    ? { driverStatus: driver.accountStatus.toUpperCase() }
    : {}),
});

export const toDriverCreatePayload = (driver) => ({
  ...toDriverPayload(driver),
  ...(driver.icNumber?.trim()
    ? { driverIcno: normalizeIcNumber(driver.icNumber) }
    : {}),
  ...(driver.registerVehicle ? { registerMissingVehicle: true } : {}),
  vehicle:
    driver.vehicle && driver.updateVehicle !== false
      ? {
          vehicleModel: driver.vehicle.model.trim(),
          vehiclePlateno: driver.vehicle.plate.trim().toUpperCase(),
          vehicleType: driver.vehicle.type,
          vehicleCapacity: Number(driver.vehicle.capacity),
          vehicleLuggageCapacity: Number(driver.vehicle.luggageCapacity),
          vehicleCategoryId: driver.vehicle.categoryId
            ? Number(driver.vehicle.categoryId) : null,
          vehicleOwnership: driver.vehicle.ownership === "Company-Owned"
            ? "COMPANY_OWNED" : driver.vehicle.ownership === "Driver-Owned"
              ? "DRIVER_OWNED" : "",
          vehicleRoadtaxexpiry: driver.vehicle.roadTaxExpiry || null,
          vehicleServiceexpiry: driver.vehicle.serviceExpiry || null,
          vehicleStatus:
            ["Active", "Available"].includes(driver.vehicle.status)
              ? "AVAILABLE"
              : driver.vehicle.status === "Maintenance"
                ? "MAINTENANCE"
                : "INACTIVE",
        }
      : null,
});

export const normalizeIcNumber = (value) =>
  String(value ?? "").trim().replace(/\s+/g, "").replace(/-/g, "");

export function validateDriverFields(driver, currentId = null, drivers = []) {
  const errors = {};
  if (!driver.name.trim()) errors.name = "Full name is required.";
  else if (driver.name.trim().length > 120) errors.name = "Enter a name of up to 120 characters.";
  const email = driver.email.trim().toLowerCase();
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email))
    errors.email = "Enter a valid email address.";
  else if (email.length > 160) errors.email = "Enter an email address of up to 160 characters.";
  const digits = driver.phone.replace(/\D/g, "");
  if (digits.length < 9 || digits.length > 15 || driver.phone.trim().length > 30)
    errors.phone = "Enter a valid phone number (9 to 15 digits).";
  if (driver.icNumber?.trim()) {
    const ic = String(driver.icNumber ?? "").trim().replace(/\s+/g, "");
    if (!/^(?:\d{12}|\d{6}-\d{2}-\d{4})$/.test(ic))
      errors.icNumber = "Enter a Malaysian IC as XXXXXX-XX-XXXX or 12 digits.";
  }
  if (currentId === null) {
    if (!driver.vehicle)
      errors.vehicle = "Driver and vehicle information are both required.";
    if (!driver.password || driver.password.length < 8)
      errors.password = "Password must contain at least 8 characters.";
    else if (new TextEncoder().encode(driver.password).length > 72)
      errors.password = "Enter a password of up to 72 bytes.";
    if (driver.password !== driver.confirmPassword || !driver.confirmPassword)
      errors.confirmPassword = "Passwords must match.";
    if (!driver.licenseNo?.trim()) errors.licenseNo = "Driving licence number is required.";
    if (!driver.licenseExpiry) errors.licenseExpiry = "Driving licence expiry date is required.";
    if (!DRIVER_TYPES.includes(driver.driverType)) errors.driverType = "Select a driver type.";
  }
  if (
    drivers.some(
      (other) => other.id !== currentId && other.email?.toLowerCase() === email,
    )
  )
    errors.email = "This email address already exists.";
  if (
    drivers.some(
      (other) =>
        other.id !== currentId && other.phone?.replace(/\D/g, "") === digits,
    )
  )
    errors.phone = "This phone number already exists.";
  if (driver.vehicle && driver.updateVehicle !== false) {
    for (const [key, label] of [
      ["model", "Vehicle model"],
      ["plate", "Plate number"],
      ["categoryId", "Vehicle category"],
      ["ownership", "Vehicle ownership"],
    ]) {
      if (!String(driver.vehicle[key] ?? "").trim())
        errors["vehicle." + key] =
          label + " is required when including a vehicle.";
    }
    if (currentId === null && !driver.vehicle.roadTaxExpiry)
      errors["vehicle.roadTaxExpiry"] = "Road tax expiry date is required.";
    if (currentId === null && !driver.vehicle.serviceExpiry)
      errors["vehicle.serviceExpiry"] = "Next service date is required.";
    if (driver.vehicle.model.trim().length > 100) errors["vehicle.model"] = "Enter a vehicle model of up to 100 characters.";
    if (driver.vehicle.plate.trim().length > 20) errors["vehicle.plate"] = "Enter a registration number of up to 20 characters.";
    const capacity = Number(driver.vehicle.capacity);
    const luggage = Number(driver.vehicle.luggageCapacity);
    if (!Number.isSafeInteger(capacity) || capacity < 1)
      errors["vehicle.capacity"] = "Passenger capacity is missing or invalid. Select a vehicle category with a passenger capacity.";
    if (driver.vehicle.luggageCapacity == null || String(driver.vehicle.luggageCapacity).trim() === "" ||
        !Number.isSafeInteger(luggage) || luggage < 0)
      errors["vehicle.luggageCapacity"] = "Luggage capacity is missing or invalid. Select a vehicle category with a luggage capacity.";
  }
  return errors;
}

const requestFields = {
  driverName: ["name", "Enter a valid full name."],
  driverEmail: ["email", "Enter a valid email address."],
  driverPhoneno: ["phone", "Enter a valid phone number."],
  driverPassword: ["password", "Enter a valid password."],
  driverIcno: ["icNumber", "Enter a valid Malaysian IC number."],
  driverType: ["driverType", "Select a driver type."],
  driverLicenseno: ["licenseNo", "Enter the driving licence number."],
  driverLicenseExpiry: ["licenseExpiry", "Enter a valid driving licence expiry date."],
  vehicleModel: ["vehicle.model", "Enter a valid vehicle model."],
  vehiclePlateno: ["vehicle.plate", "Enter a valid vehicle registration number."],
  vehicleCategoryId: ["vehicle.categoryId", "Select a vehicle category."],
  vehicleCapacity: ["vehicle.capacity", "Select a category with a valid passenger capacity."],
  vehicleLuggageCapacity: ["vehicle.luggageCapacity", "Select a category with a valid luggage capacity."],
  vehicleOwnership: ["vehicle.ownership", "Select the vehicle ownership."],
  vehicleRoadtaxexpiry: ["vehicle.roadTaxExpiry", "Enter a valid road tax expiry date."],
  vehicleServiceexpiry: ["vehicle.serviceExpiry", "Enter a valid next service date."],
};

export function driverRequestFieldErrors(data) {
  const message = typeof data === "string" ? data : data?.message || "";
  const paths = Object.keys(data?.fieldErrors ?? {});
  if (paths.length === 0) paths.push(message.split(/\s/)[0]);
  const fields = {};
  for (const path of paths) {
    const key = path.split(".").at(-1);
    if (Object.hasOwn(requestFields, key)) {
      const [field, friendly] = requestFields[key];
      fields[field] = friendly;
    }
  }
  if (Object.keys(fields).length) return fields;
  for (const [pattern, field] of [
    [/IC document/i, "icDocument"], [/driving licence document/i, "licenseDocument"],
    [/road tax document/i, "roadTaxDocument"], [/email address/i, "email"],
    [/IC number/i, "icNumber"], [/passenger capacity/i, "vehicle.capacity"],
    [/luggage capacity/i, "vehicle.luggageCapacity"], [/registration number|plate/i, "vehicle.plate"],
    [/licence expiry/i, "licenseExpiry"], [/vehicle category/i, "vehicle.categoryId"],
    [/password/i, "password"],
  ]) {
    if (pattern.test(message)) return { [field]: message };
  }
  return fields;
}
