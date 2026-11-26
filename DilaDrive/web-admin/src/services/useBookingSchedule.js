import { useCallback, useEffect, useMemo, useState } from "react";
import api from "./api";
import { scheduleBookings } from "./bookingSchedule";

export default function useBookingSchedule() {
  const [data, setData] = useState(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [revision, setRevision] = useState(0);
  const refresh = useCallback(() => setRevision(value => value + 1), []);

  useEffect(() => {
    const request = new AbortController();
    const config = { signal: request.signal };
    setLoading(true);
    setError("");
    Promise.all([
      api.get("/bookings", config),
      api.get("/assignments", config),
      api.get("/customers", config),
    ]).then(([bookings, assignments, customers]) => {
      if (![bookings.data, assignments.data, customers.data].every(Array.isArray)) {
        throw new Error("Invalid booking schedule response");
      }
      if (!request.signal.aborted) {
        setData({ bookings: bookings.data, assignments: assignments.data, customers: customers.data });
      }
    }).catch(() => {
      if (!request.signal.aborted) setError("Could not refresh the booking schedule. Try again.");
    }).finally(() => {
      if (!request.signal.aborted) setLoading(false);
    });
    return () => request.abort();
  }, [revision]);

  useEffect(() => {
    const timer = window.setInterval(refresh, 30000);
    window.addEventListener("focus", refresh);
    window.addEventListener("diladrive:assignments-changed", refresh);
    return () => {
      window.clearInterval(timer);
      window.removeEventListener("focus", refresh);
      window.removeEventListener("diladrive:assignments-changed", refresh);
    };
  }, [refresh]);

  const events = useMemo(() => data
    ? scheduleBookings(data.bookings, data.assignments, data.customers) : [], [data]);
  return { data, events, error, loading, refresh };
}
