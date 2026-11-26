const { test } = require("node:test");
const assert = require("node:assert/strict");

test("driver form validates and sends category capacities as numbers", async () => {
  const { emptyDriver, toDriverCreatePayload, validateDriverFields, driverRequestFieldErrors } =
    await import("../src/services/driverForm.js");
  const driver = {
    ...emptyDriver, name: "Test Driver", email: "driver@example.test", phone: "0123456789",
    password: "Example123", confirmPassword: "Example123", licenseNo: "TEST123", licenseExpiry: "2035-01-01",
    vehicle: { ...emptyDriver.vehicle, model: "Test vehicle", plate: "test123", type: "MPV",
      categoryId: "2", capacity: "6", luggageCapacity: "12", roadTaxExpiry: "2035-01-01", serviceExpiry: "2035-01-01" },
  };
  assert.deepEqual(validateDriverFields(driver), {});
  const { vehicle } = toDriverCreatePayload(driver);
  assert.equal(vehicle.vehicleCategoryId, 2);
  assert.equal(vehicle.vehicleCapacity, 6);
  assert.equal(vehicle.vehicleLuggageCapacity, 12);
  const errors = validateDriverFields({ ...driver, vehicle: { ...driver.vehicle, capacity: "", luggageCapacity: "" } });
  assert.ok(errors["vehicle.capacity"]);
  assert.ok(errors["vehicle.luggageCapacity"]);
  assert.ok(validateDriverFields({ ...driver, vehicle: { ...driver.vehicle, capacity: 2.5 } })["vehicle.capacity"]);
  assert.equal(validateDriverFields({ ...driver, vehicle: { ...driver.vehicle, luggageCapacity: 0 } })["vehicle.luggageCapacity"], undefined);
  assert.match(driverRequestFieldErrors({ message: "vehicle.vehicleCapacity must not be null" })["vehicle.capacity"], /category/);
  assert.match(driverRequestFieldErrors({ fieldErrors: { "vehicle.vehicleLuggageCapacity": ["must not be null"] } })["vehicle.luggageCapacity"], /category/);
});
