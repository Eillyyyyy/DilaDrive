import { useEffect, useMemo, useRef, useState } from "react";
import { CalendarDays, ChevronLeft, ChevronRight, RefreshCw, X } from "lucide-react";
import BookingScheduleDrawer, { ScheduleStatus } from "../components/BookingScheduleDrawer";
import useBookingSchedule from "../services/useBookingSchedule";
import {
  addDays, addMonths, dayLabel, filterSchedule, groupSchedule, malaysiaToday,
  monthDays, scheduleStatuses, scheduleTotals, timelineEvents, weekdays, weekDays,
} from "../services/bookingSchedule";

const emptyFilters = { status: "", driver: "", vehicle: "" };
const summaries = ["Monthly Bookings", "Today's Bookings", "Pending Bookings", "Assigned Bookings"];
const field = "h-9 rounded-lg border border-slate-200 bg-white px-2.5 text-sm text-slate-700";
const control = `${field} inline-flex items-center justify-center gap-1.5 hover:bg-slate-50 focus-visible:outline-teal-600`;
const fullDate = { weekday: "long", day: "numeric", month: "long", year: "numeric" };

function EventButton({ event, onOpen, style }) {
  const location = (event.pickup || event.dropoff || "Location pending").split(",")[0];
  return <button type="button" style={style}
    className={`${style ? "absolute flex flex-col overflow-hidden" : "block w-full"} rounded border-l-[3px] px-1.5 py-0.5 text-left text-[11px] leading-[14px] hover:brightness-95 focus-visible:outline-teal-700 ${scheduleStatuses[event.status].event}`}
    title={`${event.reference} · ${event.time || "Time pending"} · ${location} · ${event.status}`}
    aria-label={`Open ${event.reference}, ${event.time || "time pending"}, ${location}, ${event.status}`}
    onClick={() => onOpen(event.id)}>
    <span className="block w-full truncate font-semibold">{event.time || "Time pending"} · {event.reference}</span>
    <span className="block w-full truncate">{location}</span>
  </button>;
}

function DayHeading({ day, today, events, onAgenda }) {
  const number = <span aria-current={day === today ? "date" : undefined}
    className={`inline-grid h-6 min-w-6 place-items-center rounded-full px-1 text-xs font-semibold ${day === today ? "bg-teal-700 text-white" : ""}`}>
    {Number(day.slice(8))}
  </span>;
  return events.length ? <button type="button" className="flex w-full items-center justify-between gap-1 rounded text-left hover:bg-teal-50"
    aria-label={`${dayLabel(day, fullDate)}, ${events.length} bookings`} onClick={() => onAgenda(day)}>
    {number}
    <span className="rounded-full bg-teal-50 px-1.5 py-0.5 text-[10px] font-semibold text-teal-800">{events.length}</span>
  </button> : number;
}

function MonthGrid({ date, today, groups, onOpen, onAgenda }) {
  return <div className="overflow-x-auto" role="region" aria-label="Month grid">
    <div className="grid min-w-[560px] grid-cols-7">
      {weekdays.map(day => <div key={day} className="border-b border-slate-200 bg-slate-50 px-2 py-2 text-center text-xs font-semibold text-slate-600">{day}</div>)}
      {monthDays(date).map(day => {
        const events = groups.get(day) ?? [];
        const adjacent = day.slice(0, 7) !== date.slice(0, 7);
        return <div key={day} className={`min-h-[90px] min-w-0 border-b border-r border-slate-100 p-1.5 ${adjacent ? "bg-slate-50/80 text-slate-500" : "text-slate-800"} ${day === today ? "bg-teal-50/40" : ""}`}>
          <DayHeading day={day} today={today} events={events} onAgenda={onAgenda} />
          <div className="mt-1 space-y-1">
            {events.slice(0, 2).map(event => <EventButton key={event.id} event={event} onOpen={onOpen} />)}
            {events.length > 2 && <button type="button" onClick={() => onAgenda(day)}
              aria-label={`+${events.length - 2} more bookings on ${dayLabel(day, fullDate)}`}
              className="block rounded px-1 text-[11px] font-semibold text-teal-800 hover:bg-teal-50">+{events.length - 2} more</button>}
          </div>
        </div>;
      })}
    </div>
  </div>;
}

const hourHeight = 52;
function WeekTimeline({ days, today, groups, onOpen, onAgenda }) {
  const viewport = useRef(null);
  const firstHour = Math.min(7, ...days.flatMap(day => (groups.get(day) ?? [])
    .filter(event => event.time).map(event => Number(event.time.slice(0, 2)))));
  useEffect(() => { viewport.current.scrollTop = firstHour * hourHeight; }, [days[0]]);
  const noTime = days.flatMap(day => (groups.get(day) ?? []).filter(event => !event.time));
  return <>
    <div className="max-h-[590px] overflow-auto overscroll-contain focus-visible:outline-teal-700" ref={viewport}
      role="region" tabIndex={0} aria-label="Week timeline">
      <div className="min-w-[690px] pb-12">
        <div className="sticky top-0 z-10 grid grid-cols-[50px_repeat(7,minmax(0,1fr))] border-b border-slate-200 bg-white">
          <div className="self-end px-1.5 pb-2 text-[10px] font-semibold text-slate-500">MYT</div>
          {days.map((day, i) => <div key={day} className="border-l border-slate-100 p-1.5">
            <div className="px-1 text-xs font-semibold text-slate-600">{weekdays[i]}</div>
            <DayHeading day={day} today={today} events={groups.get(day) ?? []} onAgenda={onAgenda} />
          </div>)}
        </div>
        <div className="grid grid-cols-[50px_repeat(7,minmax(0,1fr))]">
          <div aria-hidden="true" className="bg-slate-50">
            {Array.from({ length: 24 }, (_, hour) => <div key={hour}
              className="border-b border-slate-100 px-1.5 pt-1 text-[10px] text-slate-500" style={{ height: hourHeight }}>
              {String(hour).padStart(2, "0")}:00
            </div>)}
          </div>
          {days.map(day => <div key={day} className={`relative min-w-0 border-l border-slate-200 ${day === today ? "bg-teal-50/30" : ""}`}
            aria-label={dayLabel(day, fullDate)}
            style={{ backgroundImage: "linear-gradient(to bottom, transparent 51px, #e2e8f0 51px)", backgroundSize: `100% ${hourHeight}px` }}>
            {timelineEvents(groups.get(day) ?? []).map(({ event, start, visualEnd, lane, columns }) => (
              <EventButton key={event.id} event={event} onOpen={onOpen} style={{
                top: start * hourHeight / 60,
                height: Math.max(28, (visualEnd - start) * hourHeight / 60 - 2),
                left: `calc(${lane * 100 / columns}% + 2px)`,
                width: `calc(${100 / columns}% - 4px)`,
              }} />
            ))}
          </div>)}
        </div>
      </div>
    </div>
    {noTime.length > 0 && <section className="border-t border-slate-200 p-3">
      <h3 className="mb-2 text-xs font-semibold text-slate-700">Pickup time pending</h3>
      <div className="grid gap-2 sm:grid-cols-3">{noTime.map(event => (
        <div key={event.id} className="min-w-0">
          <p className="mb-1 text-xs text-slate-600">{dayLabel(event.date, { weekday: "short", day: "numeric", month: "short" })}</p>
          <EventButton event={event} onOpen={onOpen} />
        </div>
      ))}</div>
    </section>}
  </>;
}

function resourceOptions(events, idKey, label) {
  const items = new Map();
  for (const { assignment } of events) {
    if (assignment?.[idKey] != null) items.set(String(assignment[idKey]), label(assignment));
  }
  return [...items.entries()].sort((a, b) => a[1].localeCompare(b[1]));
}

export default function Calendar({ initialDate, onOpenBooking }) {
  const [date, setDate] = useState(() => initialDate || malaysiaToday());
  const [view, setView] = useState("Month");
  const [filters, setFilters] = useState(emptyFilters);
  const [agenda, setAgenda] = useState(null);
  const [selectedId, setSelectedId] = useState(null);
  const agendaRef = useRef(null);
  const { data, events, error, loading, refresh } = useBookingSchedule();
  const today = malaysiaToday();
  const totals = scheduleTotals(events, date, today);
  const groups = useMemo(() => groupSchedule(filterSchedule(events, filters)), [events, filters]);
  const drivers = resourceOptions(events, "driverId", a => a.driverName || `Driver #${a.driverId}`);
  const vehicles = resourceOptions(events, "vehicleId", a => [a.vehicleModel, a.vehiclePlate].filter(Boolean).join(" · ") || `Vehicle #${a.vehicleId}`);
  const selected = events.find(event => event.id === selectedId);
  const days = weekDays(date);
  const heading = dayLabel(date, { month: "long", year: "numeric" });
  const setCalendarDate = value => { setDate(value); setAgenda(null); };
  const step = count => setCalendarDate(view === "Month" ? addMonths(date, count) : addDays(date, count * 7));
  useEffect(() => {
    if (agenda) agendaRef.current?.focus();
  }, [agenda]);

  return <div className="admin-page">
    <header className="admin-page-header">
      <div>
        <h1 className="admin-page-title">Booking Calendar</h1>
        <p className="admin-page-description">Monitor scheduled pickups and assignments in Malaysia time.</p>
      </div>
    </header>
    <dl className="grid grid-cols-2 gap-2 md:grid-cols-4">
      {summaries.map((label, i) => <div key={label} className="rounded-xl border border-slate-200 bg-white px-3 py-2.5 shadow-sm">
        <dt className="text-xs font-medium text-slate-600">{label}</dt>
        <dd className="mt-0.5 text-xl font-bold tabular-nums text-slate-900">
          {data ? totals[i] : loading ? <span className="block h-6 w-9 animate-pulse rounded bg-slate-100" /> : "—"}
        </dd>
      </div>)}
    </dl>
    <section className="rounded-xl border border-slate-200 bg-white shadow-sm" aria-label="Booking schedule">
      <div className="space-y-3 border-b border-slate-200 p-3">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap items-center gap-2">
            <button type="button" className={control} onClick={() => step(-1)} aria-label={`Previous ${view.toLowerCase()}`}><ChevronLeft size={16} /></button>
            <button type="button" className={control} onClick={() => step(1)} aria-label={`Next ${view.toLowerCase()}`}><ChevronRight size={16} /></button>
            <button type="button" className={control} onClick={() => setCalendarDate(today)}>Today</button>
            <div className="ml-1">
              <h2 className="text-lg font-bold text-slate-900">{heading}</h2>
              {view === "Week" && <p className="text-xs text-slate-500">
                {dayLabel(days[0], { day: "numeric", month: "short" })} – {dayLabel(days[6], { day: "numeric", month: "short" })}
              </p>}
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-2">
            <div className="flex rounded-lg bg-slate-100 p-0.5" aria-label="Calendar view">
              {["Month", "Week"].map(option => <button key={option} type="button" aria-pressed={view === option}
                className={`rounded-md px-3 py-1.5 text-sm font-semibold ${view === option ? "bg-white text-teal-800 shadow-sm" : "text-slate-600 hover:text-teal-800"}`}
                onClick={() => { setView(option); setAgenda(null); }}>{option}</button>)}
            </div>
            <input type="date" className={field} aria-label="Calendar date (Malaysia)" value={date} min="1900-01-01" max="9998-12-31"
              onChange={e => { if (/^\d{4}-\d{2}-\d{2}$/.test(e.target.value)) setCalendarDate(e.target.value); }} />
            <button type="button" className={control} onClick={refresh} disabled={loading}>
              <RefreshCw size={14} className={loading ? "animate-spin" : ""} /> Refresh
            </button>
          </div>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {[
            ["status", "All statuses", Object.keys(scheduleStatuses).map(status => [status, status])],
            ["driver", "All drivers", drivers],
            ["vehicle", "All vehicles", vehicles],
          ].map(([key, placeholder, options]) => <select key={key} aria-label={`Filter by ${key}`} className={`${field} max-w-[230px]`} value={filters[key]}
            onChange={e => setFilters(previous => ({ ...previous, [key]: e.target.value }))}>
            <option value="">{placeholder}</option>
            {options.map(([id, label]) => <option key={id} value={id}>{label}</option>)}
          </select>)}
          {Object.values(filters).some(Boolean) && <button type="button" className="px-2 text-sm font-semibold text-teal-800 hover:underline"
            onClick={() => setFilters(emptyFilters)}>Clear filters</button>}
          <ul aria-label="Booking status colors" className="flex flex-wrap gap-x-2.5 gap-y-1 text-xs text-slate-600 xl:ml-auto">
            {["Pending", "Assigned", "In Progress", "Completed", "Cancelled"].map(status => <li key={status} className="flex items-center gap-1">
              <span className={`h-2 w-2 rounded-full ${scheduleStatuses[status].dot}`} />{status}
            </li>)}
          </ul>
        </div>
      </div>
      {error && <p role="alert" className="border-b border-rose-100 bg-rose-50 px-4 py-2 text-sm text-rose-800">{error}</p>}
      {!data && loading ? <div role="status" aria-label="Loading calendar" className="grid animate-pulse grid-cols-7 gap-2 p-3">
        {Array.from({ length: 28 }, (_, i) => <div key={i} className="h-16 rounded bg-slate-100" />)}
      </div> : <>
        {view === "Month"
          ? <MonthGrid date={date} today={today} groups={groups} onOpen={setSelectedId} onAgenda={setAgenda} />
          : <WeekTimeline days={days} today={today} groups={groups} onOpen={setSelectedId} onAgenda={setAgenda} />}
        {data && groups.size === 0 && <p className="px-4 py-3 text-sm text-slate-500">No scheduled bookings match these filters.</p>}
      </>}
      {agenda && <section tabIndex={-1} ref={agendaRef} aria-label="Bookings for selected day"
        className="border-t border-slate-200 bg-slate-50/60 p-4 focus:outline-none">
        <div className="mb-3 flex items-center justify-between">
          <h3 className="flex items-center gap-2 font-semibold text-slate-900"><CalendarDays size={17} /> {dayLabel(agenda, fullDate)}</h3>
          <button type="button" aria-label="Close day schedule" className="rounded p-1 hover:bg-slate-200" onClick={() => setAgenda(null)}><X size={17} /></button>
        </div>
        {(groups.get(agenda) ?? []).length === 0 && <p className="text-sm text-slate-600">No bookings match the current filters on this date.</p>}
        <div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-3">
          {(groups.get(agenda) ?? []).map(event => <button type="button" key={event.id} onClick={() => setSelectedId(event.id)}
            className="min-w-0 rounded-lg border border-slate-200 bg-white p-2 text-left text-sm hover:border-teal-400">
            <span className="mb-1 flex flex-wrap items-center justify-between gap-1 font-semibold">
              {event.time || "Time pending"} · {event.reference}<ScheduleStatus status={event.status} />
            </span>
            <span className="block truncate text-xs text-slate-600">{event.pickup || event.dropoff || "Location pending"}</span>
          </button>)}
        </div>
      </section>}
    </section>
    {selected && <BookingScheduleDrawer event={selected} onClose={() => setSelectedId(null)} onOpenBooking={onOpenBooking} />}
  </div>;
}
