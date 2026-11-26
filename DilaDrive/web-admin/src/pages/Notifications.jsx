import { useEffect, useState } from "react";
import { Bell, ChevronLeft, ChevronRight, Send } from "lucide-react";
import api from "../services/api";
import { inboxPageSize, notificationError } from "../services/useAdminNotifications";
import NotificationItem, { typeLabel } from "../components/NotificationItem";

const input = "rounded-lg border border-slate-200 bg-white px-3 py-2 text-sm focus-visible:outline-teal-700";
const button = `${input} inline-flex items-center justify-center gap-1.5 font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-50`;

function Pagination({ current, total, onPage }) {
  const pages = Math.max(1, Math.ceil(total / inboxPageSize));
  return <footer className="flex items-center justify-between gap-2 border-t border-slate-100 p-3 text-sm text-slate-600">
    <span>{total} {total === 1 ? "notification" : "notifications"}</span>
    <div className="flex items-center gap-2">
      <button type="button" className={button} aria-label="Previous page" disabled={current === 0} onClick={() => onPage(current - 1)}><ChevronLeft size={16} /></button>
      <span>Page {current + 1} of {pages}</span>
      <button type="button" className={button} aria-label="Next page" disabled={current + 1 >= pages} onClick={() => onPage(current + 1)}><ChevronRight size={16} /></button>
    </div>
  </footer>;
}

function DriverMessages() {
  const [drivers, setDrivers] = useState([]);
  const [messages, setMessages] = useState(null);
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [sending, setSending] = useState(false);
  useEffect(() => {
    const request = new AbortController();
    const config = { signal: request.signal };
    api.get("/drivers", config).then(({ data }) => { if (!request.signal.aborted) setDrivers(data); })
      .catch(e => { if (!request.signal.aborted) setError(notificationError(e)); });
    return () => request.abort();
  }, []);
  useEffect(() => {
    const request = new AbortController();
    setMessages(null);
    api.get("/notifications/sent/driver", { signal: request.signal, params: { page, size: inboxPageSize } })
      .then(({ data }) => { if (!request.signal.aborted) setMessages(data); })
      .catch(e => { if (!request.signal.aborted) { setError(notificationError(e)); setMessages({ items: [], total: 0 }); } });
    return () => request.abort();
  }, [page, revision]);
  async function send(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const fields = new FormData(form);
    setSending(true);
    setError("");
    setSuccess("");
    try {
      await api.post("/notifications", { driverId: Number(fields.get("driverId")), title: fields.get("title"),
        message: fields.get("message"), notificationType: fields.get("type") });
      form.reset();
      setPage(0);
      setRevision(value => value + 1);
      setSuccess("Message sent to the driver.");
    } catch (e) {
      setError(notificationError(e));
    } finally {
      setSending(false);
    }
  }
  return <section className="space-y-3" aria-label="Driver messages">
    <details className="rounded-xl border border-slate-200 bg-white p-4">
      <summary className="cursor-pointer text-sm font-semibold text-teal-900">Send a driver notification</summary>
      <form className="mt-3 grid gap-3 sm:grid-cols-2" onSubmit={send}>
        <label className="grid gap-1 text-sm">Driver
          <select name="driverId" required className={input}><option value="">Select driver</option>
            {drivers.map(driver => <option key={driver.driverId} value={driver.driverId}>{driver.driverName}</option>)}</select>
        </label>
        <label className="grid gap-1 text-sm">Type
          <select name="type" className={input}>{["GENERAL", "ASSIGNMENT", "TRIP_STATUS", "REMINDER", "WARNING"].map(type =>
            <option value={type} key={type}>{typeLabel(type)}</option>)}</select>
        </label>
        <label className="grid gap-1 text-sm sm:col-span-2">Title<input name="title" className={input} maxLength={160} required /></label>
        <label className="grid gap-1 text-sm sm:col-span-2">Message<textarea name="message" className={input} rows={3} maxLength={2000} required /></label>
        <button type="submit" disabled={sending} className="inline-flex w-fit items-center gap-2 rounded-lg bg-teal-700 px-3 py-2 text-sm font-semibold text-white disabled:opacity-50"><Send size={15} /> {sending ? "Sending…" : "Send notification"}</button>
      </form>
    </details>
    {error && <p role="alert" className="rounded-lg bg-rose-50 p-3 text-sm text-rose-800">{error}</p>}
    {success && <p role="status" className="rounded-lg bg-teal-50 p-3 text-sm text-teal-800">{success}</p>}
    <section className="overflow-hidden rounded-xl border border-slate-200 bg-white">
      <h2 className="border-b border-slate-100 p-3 text-sm font-bold">Sent to drivers</h2>
      {!messages ? <p role="status" className="p-5 text-sm text-slate-600">Loading driver notifications…</p>
        : messages.items.length ? <ul>{messages.items.map(item => <NotificationItem key={item.notificationId} item={item} sent />)}</ul>
        : <p className="p-5 text-sm text-slate-600">No driver notifications.</p>}
      {messages && <Pagination current={page} total={messages.total} onPage={setPage} />}
    </section>
  </section>;
}

export default function Notifications({ inbox, onOpen, onRead, onMarkAll }) {
  const [sent, setSent] = useState(false);
  const { query, setQuery, page } = inbox;
  const [search, setSearch] = useState(query.search);
  useEffect(() => {
    if (search.trim() === query.search) return undefined;
    const timer = window.setTimeout(() => setQuery(previous => ({ ...previous, search: search.trim(), page: 0 })), 250);
    return () => window.clearTimeout(timer);
  }, [search, query.search, setQuery]);
  useEffect(() => {
    if (!inbox.loading && !inbox.error && query.page > 0 && !page.items.length) {
      setQuery(previous => ({ ...previous, page: Math.max(0, Math.ceil(page.total / inboxPageSize) - 1) }));
    }
  }, [inbox.loading, inbox.error, page, query.page, setQuery]);
  const filter = (key, value) => setQuery(previous => ({ ...previous, [key]: value, page: 0 }));

  return <div className="admin-page">
    <header className="admin-page-header">
      <div><h1 className="admin-page-title flex items-center gap-2"><Bell size={22} className="text-teal-700" /> Notifications</h1>
        <p className="admin-page-description">Review account, booking and fleet updates.</p></div>
      {!sent && <button type="button" className={button} onClick={onMarkAll} disabled={!inbox.count || inbox.busy}>Mark All as Read</button>}
    </header>
    <div className="flex flex-wrap gap-2">
      <button type="button" className={`${button} ${!sent ? "border-teal-300 bg-teal-50" : ""}`} aria-pressed={!sent} onClick={() => setSent(false)}>Inbox · {inbox.count} unread</button>
      <button type="button" className={`${button} ${sent ? "border-teal-300 bg-teal-50" : ""}`} aria-pressed={sent} onClick={() => setSent(true)}>Driver messages</button>
    </div>
    {sent ? <DriverMessages /> : <section className="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm">
      <div className="flex flex-wrap items-center gap-2 border-b border-slate-100 p-3">
        {["ALL", "UNREAD", "READ"].map(status => <button type="button" key={status} aria-pressed={query.status === status}
          className={`rounded-lg px-3 py-2 text-sm font-semibold ${query.status === status ? "bg-teal-100 text-teal-950" : "text-slate-600 hover:bg-slate-100"}`}
          onClick={() => filter("status", status)}>{typeLabel(status)}</button>)}
        <input type="search" value={search} maxLength={200} onChange={e => setSearch(e.target.value)}
          className={`${input} sm:ml-auto`} placeholder="Search notifications" aria-label="Search notifications" />
        <select className={input} aria-label="Filter by notification type" value={query.type} onChange={e => filter("type", e.target.value)}>
          <option value="">All types</option>
          {page.types.map(type => <option key={type} value={type}>{typeLabel(type)}</option>)}
        </select>
      </div>
      {inbox.error && <p className="bg-rose-50 px-3 py-2 text-sm text-rose-800" role="alert">{inbox.error} <button type="button" className="underline" onClick={inbox.refresh}>Retry</button></p>}
      {page.items.length ? <ul aria-busy={inbox.loading}>{page.items.map(item =>
        <NotificationItem key={item.notificationId} item={item} disabled={inbox.busy} onOpen={onOpen} onRead={onRead} />)}</ul>
        : inbox.loading ? <div role="status" aria-label="Loading notifications" className="space-y-2 p-4">
          {[0, 1, 2].map(row => <div key={row} className="h-12 animate-pulse rounded-lg bg-slate-100" />)}
        </div> : !inbox.error && <p className="p-8 text-center text-sm text-slate-600">No notifications match your filters.</p>}
      <Pagination current={query.page} total={page.total} onPage={value => setQuery(previous => ({ ...previous, page: value }))} />
    </section>}
  </div>;
}
