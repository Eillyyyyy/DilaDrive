import { useEffect, useState } from "react";
import api from "../services/api";

export default function OperationsSummary({ onNavigate }) {
  const [result, setResult] = useState(null);
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    const request = new AbortController();
    api
      .get("/dashboard/operations", { signal: request.signal })
      .then(({ data }) => {
        if (!request.signal.aborted) setResult({ data });
      })
      .catch(() => {
        if (!request.signal.aborted) setResult({ error: true });
      });
    return () => request.abort();
  }, [revision]);
  const money = (value) =>
    new Intl.NumberFormat("en-MY", {
      style: "currency",
      currency: "MYR",
    }).format(value);
  return (
    <section className="space-y-3 rounded-xl border bg-white p-4">
      <div className="flex flex-wrap justify-between gap-2">
        <h2 className="font-semibold">Operations and finance</h2>
        <button
          type="button"
          className="text-sm text-blue-700"
          onClick={() => setRevision((v) => v + 1)}
        >
          Refresh
        </button>
      </div>
      {!result && <p role="status">Loading operations…</p>}
      {result?.error && (
        <p role="alert">Could not load the operational summary.</p>
      )}
      {result?.data && (
        <dl className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {[
            ["Pending account reviews", "pending_customers", "Customers"],
            ["Today's trips", "today_trips", "Calendar"],
            ["Upcoming trips", "upcoming_trips", "Calendar"],
            ["Payments awaiting review", "payments_to_review", "Payments"],
            [
              "Verified payments this month",
              "verified_payments_month",
              "Payments",
              true,
            ],
            [
              "Outstanding issued invoices",
              "issued_invoice_balance",
              "Documents",
              true,
            ],
          ].map(([label, key, page, currency]) => (
            <div key={key} className="rounded-lg bg-slate-50 p-3">
              <dt>
                <button
                  type="button"
                  className="text-left text-sm text-blue-700"
                  onClick={() => onNavigate?.(page)}
                >
                  {label}
                </button>
              </dt>
              <dd className="mt-1 font-semibold">
                {currency ? money(result.data[key]) : result.data[key]}
              </dd>
            </div>
          ))}
        </dl>
      )}
      {result?.data && (
        <div className="rounded-lg border p-3">
          <h3 className="mb-2 font-semibold">Trips in progress</h3>
          {result.data.active_trips?.length ? (
            <ul className="space-y-2 text-sm">
              {result.data.active_trips.map((trip) => (
                <li
                  key={trip.assignment_id}
                  className="flex flex-wrap justify-between gap-2"
                >
                  <span>
                    BK-{String(trip.booking_id).padStart(6, "0")} /{" "}
                    {trip.driver_name} / {trip.plate}
                  </span>
                  <span>
                    {{
                      OTW: "On the way",
                      WAITING: "Waiting",
                      PICKUP: "Passenger picked up",
                      DROP: "Drop-off",
                    }[trip.milestone] ?? trip.milestone}
                  </span>
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-sm text-slate-600">No trips in progress</p>
          )}
        </div>
      )}
      <button
        type="button"
        className="text-sm text-blue-700"
        onClick={() => onNavigate?.("Documents")}
      >
        Open documents
      </button>
    </section>
  );
}
