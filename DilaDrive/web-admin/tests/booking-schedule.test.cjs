const { test } = require("node:test");
const assert = require("node:assert/strict");

const load = () => import("../src/services/bookingSchedule.js");
const booking = (bookingId, bookingTripdate, bookingPickuptime, bookingStatus = "PENDING") => ({
  bookingId, bookingTripdate, bookingPickuptime, bookingStatus, custId: 8,
});

test("Malaysia date boundaries and Sunday-first grids stay consistent", async () => {
  const { malaysiaToday, monthDays, weekDays, addMonths } = await load();
  assert.equal(malaysiaToday(new Date("2026-11-25T16:00:00Z")), "2026-11-26");
  assert.deepEqual(weekDays("2026-11-26"), ["2026-11-22", "2026-11-23", "2026-11-24", "2026-11-25", "2026-11-26", "2026-11-27", "2026-11-28"]);
  assert.equal(monthDays("2026-12-15")[0], "2026-11-29");
  assert.equal(monthDays("2026-12-15").at(-1), "2027-01-02");
  assert.equal(addMonths("2026-12-31", 1), "2027-01-01");
});

test("scheduled pickups are deduplicated, sorted, filtered and counted with current assignments", async () => {
  const { filterSchedule, groupSchedule, scheduleBookings, scheduleTotals } = await load();
  const events = scheduleBookings([
    booking(1, "2026-11-26", "13:00:00"),
    booking(2, "2026-11-26", "09:15:00", "CONFIRMED"),
    booking(2, "2026-11-26", "09:15:00", "CONFIRMED"),
    booking(3, "2026-12-02", "10:00:00", "CANCELLED"),
    booking(4, null, "09:30:00"),
  ], [
    { bookingId: 2, assignmentId: 1, assignmentStatus: "ASSIGNED", driverId: 11 },
    { bookingId: 2, assignmentId: 2, assignmentStatus: "PICKUP", driverId: 12, vehicleId: 5 },
    { bookingId: 2, assignmentId: 3, assignmentStatus: "SUPERSEDED", driverId: 13 },
  ], [{ custId: 8, custName: "Customer", custPhoneno: "0123456789" }]);

  assert.deepEqual(groupSchedule(events).get("2026-11-26").map(event => event.reference), ["BK-000002", "BK-000001"]);
  assert.equal(events[0].customer, "Customer");
  assert.equal(events[0].status, "In Progress");
  assert.deepEqual(filterSchedule(events, { status: "In Progress", driver: "12", vehicle: "5" }).map(event => event.id), ["2"]);
  assert.deepEqual(scheduleTotals(events, "2026-11-01", "2026-11-26"), [2, 2, 1, 1]);
  assert.deepEqual(scheduleTotals(events, "2026-12-01", "2026-11-26"), [1, 2, 0, 0]);
});

test("weekly events use scheduled time, spread overlaps and cap ends at midnight", async () => {
  const { scheduleBookings, timelineEvents } = await load();
  const events = scheduleBookings([
    { ...booking(1, "2026-11-26", "09:10:00"), reservedEndAt: "2026-11-26T10:30:00" },
    booking(2, "2026-11-26", "10:00:00"),
    { ...booking(3, "2026-11-26", "23:30:00"), reservedEndAt: "2026-11-27T01:15:00" },
    booking(4, "2026-11-26", null),
  ], [], []);
  assert.deepEqual(timelineEvents(events).map(({ start, end, lane, columns }) => [start, end, lane, columns]), [
    [550, 630, 0, 2], [600, 630, 1, 2], [1410, 1440, 0, 1],
  ]);
});
