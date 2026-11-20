const terminal = new Set(["COMPLETED", "CANCELLED", "SUPERSEDED"]);
export function bookingAssignment(booking, assignments) {
  const history = assignments.filter(a => String(a.bookingId) === String(booking.bookingId))
    .sort((a, b) => Number(b.assignmentId) - Number(a.assignmentId));
  const active = history.find(a => !terminal.has(String(a.assignmentStatus).toUpperCase()));
  const assignment = active || history[0];
  return {
    assignmentId: active?.assignmentId,
    driverId: assignment?.driverId,
    vehicleId: assignment?.vehicleId,
    driver: assignment?.driverName || "",
    vehicle: assignment?.vehiclePlate || "",
    tripStatus: assignment?.assignmentStatus || "Not assigned",
    legacyRelationship: !assignment && Boolean(booking.driverId || booking.vehicleId),
  };
}

// Preserve resource errors; a legacy Spring 404 has no application message.
export function confirmationError(error) {
  const data = error.response?.data;
  const message = typeof data === "string" ? data.trim() : data?.message || data?.detail;
  const endpointMissing = data?.code === "ENDPOINT_NOT_FOUND" ||
    (error.response?.status === 404 && !message);
  if (endpointMissing) {
    const base = error.config?.baseURL || "/api";
    const path = error.config?.url || "/bookings/{id}/confirm-and-assign";
    const url = /^https?:\/\//.test(path) ? path : base.replace(/\/$/, "") + "/" + path.replace(/^\//, "");
    return "Confirm & Assign endpoint was not found (POST " + url + "). Restart the updated DilaDrive Spring Boot backend and check the API base URL/context path. No assignment was confirmed by this response.";
  }
  return message || error.message || "Unable to confirm and assign this booking.";
}
