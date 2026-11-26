const { test } = require("node:test");
const assert = require("node:assert/strict");

test("notification links only open supported internal record targets", async () => {
  const { notificationTarget } = await import("../src/services/notificationTargets.js");
  const cases = [
    ["CUSTOMER", "customers", "Customers"],
    ["BOOKING", "bookings", "Bookings"],
    ["DRIVER", "drivers", "Drivers"],
    ["VEHICLE", "vehicles", "Vehicles"],
    ["ASSIGNMENT", "assignments", "Assignments"],
  ];
  for (const [entityType, segment, page] of cases) {
    const record = { entityType, entityId: 42, targetRoute: `/${segment}/42` };
    assert.equal(notificationTarget(record).page, page);
    assert.equal(notificationTarget(record).id, "42");
    assert.equal(notificationTarget({ ...record, targetRoute: `/${segment}/43` }), null);
    assert.equal(notificationTarget({ ...record, targetRoute: `https://example.invalid/${segment}/42` }), null);
  }
  assert.equal(notificationTarget({ entityType: "PAYMENT", entityId: 42, targetRoute: "/payments/42" }), null);
  assert.equal(notificationTarget({ entityType: "BOOKING", entityId: 42, targetRoute: null }), null);
  assert.equal(notificationTarget({ entityType: "BOOKING", entityId: "../42", targetRoute: "/bookings/../42" }), null);
});
