const targets = {
  CUSTOMER: { segment: "customers", page: "Customers", label: "View customer" },
  BOOKING: { segment: "bookings", page: "Bookings", label: "View booking" },
  DRIVER: { segment: "drivers", page: "Drivers", label: "View driver" },
  VEHICLE: { segment: "vehicles", page: "Vehicles", label: "View vehicle" },
  ASSIGNMENT: { segment: "assignments", page: "Assignments", label: "View assignment" },
};

export function notificationTarget(item) {
  if (!Object.hasOwn(targets, item.entityType)) return null;
  const target = targets[item.entityType];
  const id = String(item.entityId ?? "");
  if (!/^[1-9]\d*$/.test(id) || item.targetRoute !== `/${target.segment}/${id}`) return null;
  return { id, page: target.page, label: target.label };
}
