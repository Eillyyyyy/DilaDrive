export const weekdays = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
const dateFormat = new Intl.DateTimeFormat("en-CA", {
  timeZone: "Asia/Kuala_Lumpur", year: "numeric", month: "2-digit", day: "2-digit",
});
const civilDate = key => new Date(`${key}T12:00:00Z`);
const civilKey = date => date.toISOString().slice(0, 10);

export function malaysiaToday(now = new Date()) {
  const parts = Object.fromEntries(dateFormat.formatToParts(now).map(({ type, value }) => [type, value]));
  return `${parts.year}-${parts.month}-${parts.day}`;
}
export function dayLabel(key, options) {
  return new Intl.DateTimeFormat("en-MY", { ...options, timeZone: "UTC" }).format(civilDate(key));
}
export function addDays(key, count) {
  const date = civilDate(key);
  date.setUTCDate(date.getUTCDate() + count);
  return civilKey(date);
}
export function addMonths(key, count) {
  const date = civilDate(`${key.slice(0, 7)}-01`);
  date.setUTCMonth(date.getUTCMonth() + count);
  return civilKey(date);
}
export function weekDays(key) {
  const sunday = addDays(key, -civilDate(key).getUTCDay());
  return Array.from({ length: 7 }, (_, i) => addDays(sunday, i));
}
export function monthDays(key) {
  const first = `${key.slice(0, 7)}-01`;
  const start = weekDays(first)[0];
  const last = addDays(addMonths(first, 1), -1);
  const end = weekDays(last)[6];
  const length = Math.round((civilDate(end) - civilDate(start)) / 86400000) + 1;
  return Array.from({ length }, (_, i) => addDays(start, i));
}

export const scheduleStatuses = {
  Pending: { event: "border-l-amber-500 bg-amber-50 text-amber-950", dot: "bg-amber-500" },
  Confirmed: { event: "border-l-slate-500 bg-slate-100 text-slate-900", dot: "bg-slate-500" },
  Assigned: { event: "border-l-blue-600 bg-blue-50 text-blue-950", dot: "bg-blue-600" },
  "In Progress": { event: "border-l-violet-600 bg-violet-50 text-violet-950", dot: "bg-violet-600" },
  Completed: { event: "border-l-emerald-600 bg-emerald-50 text-emerald-950", dot: "bg-emerald-600" },
  Cancelled: { event: "border-l-rose-600 bg-rose-50 text-rose-950", dot: "bg-rose-600" },
  Rejected: { event: "border-l-rose-600 bg-rose-50 text-rose-950", dot: "bg-rose-600" },
};
const activeStatuses = new Set(["ASSIGNED", "OTW", "WAITING", "PICKUP", "DROP", "IN_PROGRESS"]);
const progressStatuses = new Set(["OTW", "WAITING", "PICKUP", "DROP", "IN_PROGRESS"]);
const normalized = value => String(value ?? "").trim().toUpperCase().replaceAll(" ", "_");
function scheduleStatus(booking, assignment) {
  const status = normalized(booking.bookingStatus);
  const progress = normalized(assignment?.assignmentStatus);
  if (status === "REJECTED") return "Rejected";
  if (status === "CANCELLED") return "Cancelled";
  if (status === "COMPLETED" || progress === "COMPLETED") return "Completed";
  if (progressStatuses.has(status) || progressStatuses.has(progress)) return "In Progress";
  if (status === "ASSIGNED" || progress === "ASSIGNED") return "Assigned";
  if (status === "CONFIRMED" || status === "VERIFIED") return "Confirmed";
  return "Pending";
}

export function scheduleBookings(bookings, assignments, customers) {
  const byBooking = new Map();
  for (const assignment of assignments) {
    if (normalized(assignment.assignmentStatus) === "SUPERSEDED") continue;
    const id = String(assignment.bookingId);
    const previous = byBooking.get(id);
    const active = activeStatuses.has(normalized(assignment.assignmentStatus));
    const previousActive = activeStatuses.has(normalized(previous?.assignmentStatus));
    if (!previous || (active !== previousActive ? active : Number(assignment.assignmentId) > Number(previous.assignmentId))) {
      byBooking.set(id, assignment);
    }
  }
  const customerMap = new Map(customers.map(c => [String(c.custId), c]));
  const unique = new Map();
  for (const booking of bookings) {
    const id = String(booking.bookingId ?? "");
    const date = booking.bookingTripdate;
    if (!id || !/^\d{4}-\d{2}-\d{2}$/.test(date ?? "")) continue;
    const assignment = byBooking.get(id);
    const customer = customerMap.get(String(booking.custId));
    unique.set(id, {
      id, date, booking, assignment,
      time: booking.bookingPickuptime?.slice(0, 5) || "",
      reference: booking.bookingReference || `BK-${id.padStart(6, "0")}`,
      status: scheduleStatus(booking, assignment),
      customer: booking.contactName || booking.custName || booking.customerName || customer?.custName || assignment?.customer,
      phone: booking.contactPhone || customer?.custPhoneno || assignment?.customerPhone,
      pickup: booking.bookingPickupaddress || assignment?.pickupAddress,
      dropoff: booking.bookingDestinationaddress || assignment?.destinationAddress,
    });
  }
  return [...unique.values()].sort((a, b) =>
    a.date.localeCompare(b.date) || (a.time || "99:99").localeCompare(b.time || "99:99") ||
    a.reference.localeCompare(b.reference));
}

export function groupSchedule(events) {
  const groups = new Map();
  for (const event of events) {
    if (!groups.has(event.date)) groups.set(event.date, []);
    groups.get(event.date).push(event);
  }
  return groups;
}

export function scheduleTotals(events, selectedDate, today) {
  const month = events.filter(event => event.date.startsWith(selectedDate.slice(0, 7)));
  return [
    month.length,
    events.filter(event => event.date === today).length,
    month.filter(event => event.status === "Pending").length,
    month.filter(event => event.status === "Assigned" || event.status === "In Progress").length,
  ];
}

export function filterSchedule(events, filters) {
  return events.filter(event =>
    (!filters.status || filters.status === event.status) &&
    (!filters.driver || filters.driver === String(event.assignment?.driverId)) &&
    (!filters.vehicle || filters.vehicle === String(event.assignment?.vehicleId)));
}

export function timelineEvents(events) {
  const lanes = [];
  const minutes = time => Number(time.slice(0, 2)) * 60 + Number(time.slice(3, 5));
  const timed = events.filter(event => event.time).map(event => {
    const start = minutes(event.time);
    const endAt = event.assignment?.scheduledEndAt || event.booking.reservedEndAt;
    const endDate = endAt?.slice(0, 10);
    const endMinute = endDate > event.date ? 1440 : endDate === event.date ? minutes(endAt.slice(11, 16)) : 0;
    const end = Math.min(1440, Math.max(start + 1, endMinute > start ? endMinute : start + 30));
    const visualEnd = Math.max(end, start + 60);
    let lane = lanes.findIndex(availableAt => availableAt <= start);
    if (lane < 0) lane = lanes.length;
    lanes[lane] = visualEnd;
    return { event, start, end, visualEnd, lane };
  });
  let cluster = [];
  let clusterEnd = 0;
  let clusterLanes = 0;
  const flush = () => cluster.forEach(item => { item.columns = clusterLanes; });
  for (const item of timed) {
    if (item.start >= clusterEnd) {
      flush();
      cluster = [];
      clusterLanes = 0;
    }
    cluster.push(item);
    clusterEnd = Math.max(clusterEnd, item.visualEnd);
    clusterLanes = Math.max(clusterLanes, item.lane + 1);
  }
  flush();
  return timed;
}
