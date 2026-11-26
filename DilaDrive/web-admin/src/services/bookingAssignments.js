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

export function preferredAssignment(preview) {
  if (!preview || preview.unavailableReason || preview.preferenceChecks?.eligible === false || !preview.preferred?.driverId ||
      preview.preferred.customerAcceptanceRequired) return null;
  return preview.preferred;
}
export function assignmentPreviewError(error) {
  const status = error.response?.status;
  const data = error.response?.data;
  if (status === 401) return "Your Admin session has expired. Sign in again and retry.";
  if (status === 403) return "Your account does not have access to assignment previews.";
  if (status === 404) {
    return data?.code === "ENDPOINT_NOT_FOUND" || !data?.message
      ? "Assignment preview is unavailable on this backend version. Restart the updated backend, then retry."
      : "This booking could not be found. Refresh Booking Management and try again.";
  }
  if (status === 400) return data?.message || "Check the scheduled end time and retry.";
  if (status === 409) return data?.message || "The booking or assignment changed. Refresh and retry.";
  return "Could not verify assignment availability. Retry when the backend is available.";
}

export function assignmentDuration(seconds) {
  if (!Number.isFinite(seconds) || seconds < 0) return "Not recorded";
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor(seconds % 3600 / 60);
  const remainder = seconds % 60;
  return [hours && `${hours} h`, (hours || minutes) ? `${minutes} min` : "",
    (remainder || !hours && !minutes) ? `${remainder} s` : ""].filter(Boolean).join(" ");
}