import { useEffect, useRef } from "react";
import { ArrowRight, Bell } from "lucide-react";
import NotificationItem from "./NotificationItem";

export default function NotificationBell({ inbox, open, setOpen, onOpen, onMarkAll, onViewAll }) {
  const root = useRef(null);
  const trigger = useRef(null);
  useEffect(() => {
    if (!open) return undefined;
    const outside = event => { if (!root.current?.contains(event.target)) setOpen(false); };
    const escape = event => {
      if (event.key === "Escape") { setOpen(false); trigger.current?.focus(); }
    };
    document.addEventListener("pointerdown", outside);
    document.addEventListener("keydown", escape);
    return () => {
      document.removeEventListener("pointerdown", outside);
      document.removeEventListener("keydown", escape);
    };
  }, [open, setOpen]);

  return <div ref={root} className="relative">
    <button ref={trigger} type="button" onClick={() => { setOpen(!open); if (!open) inbox.refresh(); }}
      aria-expanded={open} aria-controls="admin-notification-dropdown"
      aria-label={inbox.count ? `Notifications, ${inbox.count} unread` : "Notifications"}
      className="relative rounded-xl border p-2.5 hover:bg-slate-50 focus-visible:outline-teal-700">
      <Bell size={19} />
      {inbox.count > 0 && <span className="absolute -right-1 -top-1 grid h-5 min-w-5 place-items-center rounded-full bg-rose-600 px-1 text-[10px] font-bold text-white">
        {inbox.count > 99 ? "99+" : inbox.count}
      </span>}
    </button>
    {open && <section id="admin-notification-dropdown" aria-label="Recent notifications"
      className="fixed inset-x-2 top-[3.75rem] z-50 overflow-hidden rounded-xl border border-slate-200 bg-white shadow-xl sm:absolute sm:inset-x-auto sm:right-0 sm:top-full sm:mt-2 sm:w-[400px]">
      <div className="flex items-start justify-between gap-2 border-b border-slate-100 p-3">
        <div><h2 className="font-bold text-slate-900">Notifications</h2>
          <p className="text-xs text-slate-600">{inbox.count} unread</p></div>
        <button type="button" onClick={onMarkAll} disabled={!inbox.count || inbox.busy}
          className="rounded px-1.5 py-1 text-xs font-semibold text-teal-800 hover:bg-teal-50 disabled:opacity-45">Mark All as Read</button>
      </div>
      <div className="flex gap-1 border-b border-slate-100 px-3 py-2">
        {["ALL", "UNREAD"].map(status => <button type="button" key={status}
          onClick={() => inbox.setRecentStatus(status)} aria-pressed={inbox.recentStatus === status}
          className={`rounded-lg px-2.5 py-1 text-xs font-semibold ${inbox.recentStatus === status ? "bg-teal-100 text-teal-950" : "text-slate-600 hover:bg-slate-100"}`}>
          {status === "ALL" ? "All" : "Unread"}
        </button>)}
      </div>
      {inbox.error && <p role="alert" className="bg-rose-50 px-3 py-2 text-xs text-rose-800">{inbox.error} <button type="button" onClick={inbox.refresh} className="underline">Retry</button></p>}
      <div className="max-h-[min(55vh,420px)] overflow-y-auto overscroll-contain">
        {inbox.recent.items.length > 0 ? <ul>{inbox.recent.items.map(item =>
          <NotificationItem key={item.notificationId} item={item} compact disabled={inbox.busy} onOpen={onOpen} />)}</ul>
          : inbox.loading ? <p role="status" className="p-5 text-sm text-slate-600">Loading notifications…</p>
          : !inbox.error && <p className="p-5 text-sm text-slate-600">{inbox.recentStatus === "UNREAD" ? "You're all caught up." : "No notifications yet."}</p>}
      </div>
      <button type="button" onClick={onViewAll} className="flex w-full items-center justify-center gap-2 border-t border-slate-100 px-3 py-2.5 text-sm font-semibold text-teal-800 hover:bg-teal-50">
        View All Notifications <ArrowRight size={15} />
      </button>
    </section>}
  </div>;
}
