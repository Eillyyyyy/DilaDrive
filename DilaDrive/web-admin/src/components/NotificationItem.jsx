import { AlertTriangle, Bell, CalendarDays, Check, ClipboardList, Clock3, Truck, UserRound, Users } from "lucide-react";
import { notificationTarget } from "../services/notificationTargets";

const styles = {
  customer: [Users, "bg-blue-100 text-blue-800"],
  booking: [CalendarDays, "bg-teal-100 text-teal-800"],
  assignment: [ClipboardList, "bg-indigo-100 text-indigo-800"],
  driver: [UserRound, "bg-cyan-100 text-cyan-900"],
  vehicle: [Truck, "bg-violet-100 text-violet-800"],
  trip: [Clock3, "bg-purple-100 text-purple-800"],
  warning: [AlertTriangle, "bg-amber-100 text-amber-900"],
  general: [Bell, "bg-slate-100 text-slate-700"],
};

function kind(item) {
  const type = (item.notificationType || "").toUpperCase();
  if (/WARNING|INCIDENT|CANCELLED/.test(type) || (!item.entityType && type.includes("EXPIRY"))) return "warning";
  if (type.startsWith("TRIP")) return "trip";
  const entity = item.entityType || type.split("_")[0];
  const category = entity.toLowerCase();
  return Object.hasOwn(styles, category) ? category : "general";
}

const relative = new Intl.RelativeTimeFormat("en", { numeric: "auto" });
function timeLabel(value) {
  if (!value) return { date: undefined, text: "Time unavailable" };
  const date = new Date(/[Zz]$|[+-]\d{2}:\d{2}$/.test(value) ? value : value + "+08:00");
  if (!Number.isFinite(date.valueOf())) return { date: undefined, text: "Time unavailable" };
  const minutes = Math.round((date.getTime() - Date.now()) / 60000);
  const hours = Math.round(minutes / 60);
  const days = Math.round(hours / 24);
  const text = Math.abs(minutes) < 60 ? relative.format(minutes, "minute")
    : Math.abs(hours) < 24 ? relative.format(hours, "hour")
    : relative.format(days, "day");
  return { date: date.toISOString(), text };
}

export function typeLabel(type) {
  return (type || "GENERAL").toLowerCase().replaceAll("_", " ").replace(/^./, value => value.toUpperCase());
}

export default function NotificationItem({ item, compact = false, disabled, onOpen, onRead, sent = false }) {
  const [Icon, color] = styles[kind(item)];
  const target = notificationTarget(item);
  const time = timeLabel(item.sentAt);
  const action = !sent && (target || compact);
  const body = <>
    <span className="flex items-start justify-between gap-2">
      <span className="break-words text-sm font-semibold text-slate-900">{item.title}</span>
      <time dateTime={time.date} className="shrink-0 text-xs text-slate-500">{time.text}</time>
    </span>
    <span className={`mt-0.5 break-words text-xs leading-relaxed text-slate-700 ${compact ? "line-clamp-2" : "block whitespace-pre-wrap"}`}>{item.message}</span>
  </>;
  return <li className={`flex items-start gap-2.5 border-b border-slate-100 px-3 py-3 last:border-b-0 ${!sent && !item.read ? "bg-teal-50/70" : "bg-white"}`}>
    <span className={`grid h-8 w-8 shrink-0 place-items-center rounded-lg ${color}`}><Icon size={16} aria-hidden="true" /></span>
    <div className="min-w-0 flex-1">
      {action ? <button type="button" disabled={disabled} onClick={() => onOpen(item)}
        className="group block w-full rounded text-left focus-visible:outline-teal-700 disabled:opacity-50">
        {body}
        <span className="mt-1 block text-xs font-semibold text-teal-800 group-hover:underline">{target?.label || "View notification"}</span>
      </button> : <div>{body}</div>}
      <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs">
        {sent ? <span className="text-slate-600">{item.driverName || "Driver"} · {item.read ? "Read" : "Unread"}</span> : <>
          <span className={`inline-flex items-center gap-1 ${item.read ? "text-slate-500" : "font-semibold text-teal-800"}`}>
            {!item.read && <span className="h-1.5 w-1.5 rounded-full bg-teal-700" />}{item.read ? "Read" : "Unread"}
          </span>
          {!target && !compact && <span className="text-slate-500">No linked record</span>}
          {!compact && !item.read && <button type="button" disabled={disabled} onClick={() => onRead(item)}
            className="inline-flex items-center gap-1 text-slate-600 hover:text-teal-800 disabled:opacity-50"><Check size={13} /> Mark as read</button>}
        </>}
      </div>
    </div>
  </li>;
}
