import { useState } from "react";
import { ArrowRight, CalendarDays, ChevronLeft, ChevronRight } from "lucide-react";
import BookingScheduleDrawer, { ScheduleStatus } from "./BookingScheduleDrawer";
import { addMonths, dayLabel, groupSchedule, malaysiaToday, monthDays, scheduleStatuses, weekdays } from "../services/bookingSchedule";

const fullDate = { weekday: "long", day: "numeric", month: "long", year: "numeric" };
const monthControl = "rounded-lg border border-slate-200 p-1.5 text-slate-700 hover:bg-slate-100 focus-visible:outline-teal-600";

export default function DashboardSchedule({ schedule, onOpenCalendar, onOpenBooking }) {
  const [date, setDate] = useState(null);
  const [month, setMonth] = useState(null);
  const [selectedId, setSelectedId] = useState(null);
  const today = malaysiaToday();
  const selectedDate = date || today;
  const selectedMonth = month || today;
  const { events, data, loading, error, refresh } = schedule;
  const groups = groupSchedule(events);
  const daily = groups.get(selectedDate) ?? [];
  const selected = events.find(event => event.id === selectedId);

  function selectDay(day) {
    setDate(day);
    setMonth(day);
  }

  return <section aria-label="Booking Schedule">
    <h2 className="mb-3 flex items-center gap-2 text-lg font-bold text-slate-900">
      <CalendarDays size={19} className="text-teal-700" /> Booking Schedule
    </h2>
    {error && <p role="alert" className="mb-3 rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-800">
      {error} <button type="button" onClick={refresh} className="font-semibold underline">Retry</button>
    </p>}
    <div className="grid items-start gap-3 lg:grid-cols-[2fr_3fr]">
      <section className="rounded-xl border border-slate-200 bg-white p-3 shadow-sm sm:p-4" aria-label="Mini Booking Calendar">
        <div className="mb-2 flex items-center justify-between gap-2">
          <h3 className="text-sm font-bold text-slate-900">{dayLabel(selectedMonth, { month: "long", year: "numeric" })}</h3>
          <div className="flex gap-1">
            <button type="button" className={monthControl} aria-label="Previous mini calendar month" onClick={() => setMonth(addMonths(selectedMonth, -1))}><ChevronLeft size={15} /></button>
            <button type="button" className={monthControl} aria-label="Next mini calendar month" onClick={() => setMonth(addMonths(selectedMonth, 1))}><ChevronRight size={15} /></button>
          </div>
        </div>
        <div className="grid grid-cols-7 gap-0.5 text-center">
          {weekdays.map(day => <span key={day} className="py-1 text-[11px] font-semibold text-slate-500">{day}</span>)}
          {monthDays(selectedMonth).map(day => {
            const dateEvents = groups.get(day) ?? [];
            const statuses = [...new Set(dateEvents.map(event => event.status))].slice(0, 3);
            const active = selectedDate === day;
            const adjacent = day.slice(0, 7) !== selectedMonth.slice(0, 7);
            return <button key={day} type="button" aria-label={`${dayLabel(day, fullDate)}, ${dateEvents.length} bookings`}
              aria-pressed={active} aria-current={day === today ? "date" : undefined}
              className={`flex h-9 flex-col items-center justify-center gap-0.5 rounded-lg text-xs focus-visible:outline-teal-600 ${active ? "bg-teal-100 font-bold text-teal-950" : "hover:bg-slate-100"} ${adjacent && !active ? "text-slate-400" : ""}`}
              onClick={() => selectDay(day)}>
              <span className={`inline-grid h-5 w-5 place-items-center rounded-full ${day === today ? "bg-teal-700 font-bold text-white" : ""}`}>{Number(day.slice(8))}</span>
              <span aria-hidden="true" className="flex h-1 gap-0.5">{statuses.map(status => <span key={status} className={`h-1 w-1 rounded-full ${scheduleStatuses[status].dot}`} />)}</span>
            </button>;
          })}
        </div>
        <div className="mt-2 border-t border-slate-100 pt-2">
          <button type="button" onClick={() => onOpenCalendar(selectedMonth.slice(0, 7) === selectedDate.slice(0, 7) ? selectedDate : selectedMonth)}
            className="inline-flex items-center gap-1.5 text-sm font-semibold text-teal-800 hover:underline">
            View Full Calendar <ArrowRight size={15} />
          </button>
        </div>
      </section>
      <section className="min-w-0 rounded-xl border border-slate-200 bg-white p-3 shadow-sm sm:p-4" aria-label="Daily Schedule">
        <div className="mb-2 flex flex-wrap items-start justify-between gap-2">
          <div>
            <h3 className="text-sm font-bold text-slate-900">Daily Schedule</h3>
            <p className="mt-0.5 text-xs text-slate-500">{dayLabel(selectedDate, fullDate)} · MYT</p>
          </div>
          {data && <span className="rounded-full bg-teal-50 px-2 py-0.5 text-xs font-semibold text-teal-800">{daily.length} {daily.length === 1 ? "booking" : "bookings"}</span>}
        </div>
        {!data && loading && <div role="status" aria-label="Loading daily schedule" className="space-y-2 py-2">
          {[0, 1, 2].map(row => <div key={row} className="h-12 animate-pulse rounded-lg bg-slate-100" />)}
        </div>}
        {data && daily.length === 0 && <div className="rounded-lg border border-dashed border-slate-200 bg-slate-50 px-4 py-8 text-center">
          <CalendarDays aria-hidden="true" size={22} className="mx-auto mb-2 text-teal-700" />
          <p className="text-sm font-semibold text-slate-800">No scheduled bookings</p>
          <p className="mt-1 text-xs text-slate-600">Select another date to view its schedule.</p>
        </div>}
        {daily.length > 0 && <ul className="max-h-[310px] divide-y divide-slate-100 overflow-auto overscroll-contain">
          {daily.map(event => <li key={event.id}>
            <button type="button" onClick={() => setSelectedId(event.id)}
              aria-label={`Open ${event.reference}`}
              className="grid w-full grid-cols-[3.25rem_minmax(0,1fr)] gap-2 rounded-lg px-1.5 py-2 text-left hover:bg-teal-50/60 focus-visible:outline-teal-600 sm:grid-cols-[3.25rem_minmax(0,1fr)_auto]">
              <span className="pt-0.5 text-xs font-bold tabular-nums text-slate-800">{event.time || "Pending"}</span>
              <span className="min-w-0">
                <span className="block text-sm font-semibold text-slate-900">{event.reference}</span>
                <span className="block truncate text-xs text-slate-700" title={[event.pickup, event.dropoff].filter(Boolean).join(" → ")}>{event.pickup || "Pickup pending"} → {event.dropoff || "Drop-off pending"}</span>
                <span className="block truncate text-xs text-slate-500">
                  {event.assignment?.driverName || "Driver pending"} · {[event.assignment?.vehicleModel, event.assignment?.vehiclePlate].filter(Boolean).join(" · ") || "Vehicle pending"}
                </span>
              </span>
              <span className="col-start-2 sm:col-start-3 sm:self-start"><ScheduleStatus status={event.status} /></span>
            </button>
          </li>)}
        </ul>}
      </section>
    </div>
    {selected && <BookingScheduleDrawer event={selected} onClose={() => setSelectedId(null)} onOpenBooking={onOpenBooking} />}
  </section>;
}
