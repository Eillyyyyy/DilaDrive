export const DRIVER_TYPES = ["Company Driver", "Associate Driver"];
export const ACCOUNT_STATUSES = ["Active", "Disabled"];
export const AVAILABILITY_OPTIONS = ["Available", "Busy", "Unavailable"];
export const VEHICLE_STATUSES = ["Available", "Maintenance", "Inactive"];
export const emptyVehicle = {
  model: "",
  plate: "",
  type: "",
  capacity: "",
  luggageCapacity: "",
  roadTaxExpiry: "",
  serviceExpiry: "",
  status: "Available",
};
export const emptyDriver = {
  name: "",
  email: "",
  phone: "",
  icNo: "",
  licenseNo: "",
  licenseExpiry: "",
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
  icNo: driver.driverIcno || "",
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
  updateVehicle: false,
  vehicle: data.vehicle
    ? {
        model: data.vehicle.vehicleModel ?? "",
        plate: data.vehicle.vehiclePlateno ?? "",
        type: data.vehicle.vehicleType ?? "",
        capacity: data.vehicle.vehicleCapacity ?? "",
        luggageCapacity: data.vehicle.vehicleLuggageCapacity ?? "",
        roadTaxExpiry: data.vehicle.vehicleRoadtaxexpiry ?? "",
        serviceExpiry: data.vehicle.vehicleServiceexpiry ?? "",
        status:
          data.vehicle.vehicleStatus === "AVAILABLE"
            ? "Available"
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
  driverIcno: driver.icNo?.trim() || "",
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
  ...(driver.registerVehicle ? { registerMissingVehicle: true } : {}),
  vehicle:
    driver.vehicle && driver.updateVehicle !== false
      ? {
          vehicleModel: driver.vehicle.model.trim(),
          vehiclePlateno: driver.vehicle.plate.trim().toUpperCase(),
          vehicleType: driver.vehicle.type.trim(),
          vehicleCapacity: Number(driver.vehicle.capacity),
          vehicleLuggageCapacity: Number(driver.vehicle.luggageCapacity),
          vehicleRoadtaxexpiry: driver.vehicle.roadTaxExpiry || null,
          vehicleServiceexpiry: driver.vehicle.serviceExpiry || null,
          vehicleStatus:
            driver.vehicle.status === "Available"
              ? "AVAILABLE"
              : driver.vehicle.status === "Maintenance"
                ? "MAINTENANCE"
                : "INACTIVE",
        }
      : null,
});

export function validateDriverFields(driver, currentId = null, drivers = []) {
  const errors = {};
  if (!driver.name.trim()) errors.name = "Full name is required.";
  const email = driver.email.trim().toLowerCase();
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email))
    errors.email = "Enter a valid email address.";
  const digits = driver.phone.replace(/\D/g, "");
  if (digits.length < 9 || digits.length > 15)
    errors.phone = "Enter a valid phone number (9 to 15 digits).";
  if (currentId === null) {
    if (!driver.vehicle)
      errors.vehicle = "Driver and vehicle information are both required.";
    if (!driver.password || driver.password.length < 8)
      errors.password = "Password must contain at least 8 characters.";
    if (driver.password !== driver.confirmPassword || !driver.confirmPassword)
      errors.confirmPassword = "Passwords must match.";
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
      ["type", "Vehicle type"],
    ]) {
      if (!driver.vehicle[key]?.trim())
        errors["vehicle." + key] =
          label + " is required when including a vehicle.";
    }
    const capacity = Number(driver.vehicle.capacity),
      luggage = Number(driver.vehicle.luggageCapacity);
    if (
      driver.vehicle.capacity === "" ||
      !Number.isInteger(capacity) ||
      capacity < 1
    )
      errors["vehicle.capacity"] = "Enter a passenger capacity of at least 1.";
    if (
      driver.vehicle.luggageCapacity === "" ||
      !Number.isInteger(luggage) ||
      luggage < 0
    )
      errors["vehicle.luggageCapacity"] =
        "Enter a luggage capacity of zero or greater.";
  }
  return errors;
}
