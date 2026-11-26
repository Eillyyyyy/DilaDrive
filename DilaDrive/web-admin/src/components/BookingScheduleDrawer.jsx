import { useEffect, useRef } from "react";
import { ArrowUpRight, X } from "lucide-react";
import useModalScrollLock from "./useModalScrollLock";
import { dayLabel, scheduleStatuses } from "../services/bookingSchedule";

export function ScheduleStatus({ status }) {
  return <span className={`inline-block rounded-full px-2 py-0.5 text-xs font-semibold ${scheduleStatuses[status].event}`}>
    {status}
  </span>;
}

export default function BookingScheduleDrawer({ event, onClose, onOpenBooking }) {
  const dialog = useRef(null);
  useModalScrollLock();
  useEffect(() => {
    const element = dialog.current;
    element.showModal();
    return () => element.close();
  }, []);
  const { booking, assignment } = event;
  const bagCounts = [booking.smallCount, booking.mediumCount, booking.largeCount];
  const luggage = bagCounts.some(count => count != null)
    ? bagCounts.map((count, i) => `${count ?? 0} ${["small", "medium", "large"][i]}`).join(" · ")
    : [booking.bookingLuggagecount, booking.bookingLuggagesize].filter(value => value != null && value !== "").join(" ");
  const details = [
    ["Customer", event.customer], ["Contact", event.phone],
    ["Pickup", event.pickup], ["Drop-off", event.dropoff],
    ["Scheduled pickup", [dayLabel(event.date, { weekday: "short", day: "numeric", month: "short", year: "numeric" }), event.time && `${event.time} MYT`].filter(Boolean).join(" · ")],
    ["Passengers", booking.bookingPassengercount], ["Luggage", luggage],
    ["Boot usage", booking.luggageUnits == null ? null : `${booking.luggageUnits} units`],
    ["Vehicle category", assignment?.vehicleCategory || booking.bookingType],
    ["Assigned vehicle", [assignment?.vehicleModel, assignment?.vehiclePlate].filter(Boolean).join(" · ")],
    ["Assigned driver", assignment?.driverName],
    ...(booking.bookingFlightno || booking.flightDate || booking.flightTime
      ? [["Flight details", [booking.bookingFlightno, booking.flightDate, booking.flightTime?.slice(0, 5)].filter(Boolean).join(" · ")]] : []),
    ["Special requirements", booking.bookingNotes],
  ];
  return <dialog ref={dialog}
    aria-labelledby="schedule-drawer-title"
    className="fixed inset-y-0 left-auto right-0 m-0 h-dvh max-h-none w-full max-w-md overflow-hidden bg-white p-0 shadow-2xl backdrop:bg-slate-950/45"
    onCancel={e => { e.preventDefault(); onClose(); }}
    onClick={e => { if (e.target === e.currentTarget) onClose(); }}>
    <div className="flex h-full flex-col">
      <header className="flex items-start justify-between gap-4 border-b border-slate-200 px-5 py-4">
        <div>
          <h2 id="schedule-drawer-title" className="mb-1 text-xl font-bold text-slate-900">{event.reference}</h2>
          <ScheduleStatus status={event.status} />
        </div>
        <button type="button" aria-label="Close booking details" onClick={onClose}
          className="rounded-lg p-2 text-slate-600 hover:bg-slate-100"><X size={19} /></button>
      </header>
      <dl className="min-h-0 flex-1 divide-y divide-slate-100 overflow-y-auto overscroll-contain px-5">
        {details.map(([label, value]) => <div key={label} className="py-3">
          <dt className="text-xs font-medium text-slate-500">{label}</dt>
          <dd className="mt-0.5 break-words text-sm text-slate-900">{value === "" || value == null ? "—" : value}</dd>
        </div>)}
      </dl>
      <footer className="border-t border-slate-200 p-4">
        <button type="button" onClick={() => onOpenBooking(event.id)}
          className="inline-flex w-full items-center justify-center gap-2 rounded-lg bg-teal-700 px-4 py-2.5 text-sm font-semibold text-white hover:bg-teal-800">
          View Full Booking <ArrowUpRight size={16} />
        </button>
      </footer>
    </div>
  </dialog>;
}
