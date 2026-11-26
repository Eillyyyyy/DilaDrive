import { useCallback, useEffect, useRef, useState } from "react";
import api from "./api";
import { getApiErrorMessage } from "./apiErrors";

export const inboxPageSize = 12;
export const defaultInboxQuery = { page: 0, status: "ALL", type: "", search: "" };
const empty = { items: [], total: 0, types: [] };
export const notificationError = error =>
  getApiErrorMessage(error, "Could not update notifications. Please try again.");

export default function useAdminNotifications(adminId, fullPage) {
  const [revision, setRevision] = useState(0);
  const [query, setQuery] = useState(defaultInboxQuery);
  const [recentStatus, setRecentStatus] = useState("ALL");
  const [state, setState] = useState({});
  const [busy, setBusy] = useState(false);
  const mutation = useRef(null);
  const owner = useRef(adminId);
  owner.current = adminId;
  const refresh = useCallback(() => setRevision(value => value + 1), []);

  useEffect(() => {
    if (!adminId) return undefined;
    const request = new AbortController();
    const config = { signal: request.signal };
    setState(previous => ({ ...(previous.owner === adminId ? previous : {}), owner: adminId,
      loading: true, error: "" }));
    Promise.allSettled([
      api.get("/notifications/inbox", { ...config, params: { size: 8, status: recentStatus } }),
      api.get("/notifications/unread-count", config),
      fullPage
        ? api.get("/notifications/inbox", { ...config, params: { ...query, size: inboxPageSize } })
        : Promise.resolve(null),
    ]).then(([recent, count, page]) => {
      if (request.signal.aborted) return;
      setState(previous => ({
        ...previous, loading: false,
        recent: recent.value?.data ?? previous.recent,
        recentStatus: recent.value ? recentStatus : previous.recentStatus,
        count: count.value?.data?.count ?? previous.count,
        page: page.value?.data ?? previous.page,
        pageQuery: page.value ? query : previous.pageQuery,
        error: [recent, count, page].some(result => result.status === "rejected")
          ? "Could not refresh notifications. Try again." : "",
      }));
    });
    return () => request.abort();
  }, [adminId, fullPage, query, recentStatus, refresh, revision]);

  useEffect(() => {
    if (!adminId) return undefined;
    const poll = () => { if (!document.hidden) refresh(); };
    window.addEventListener("focus", poll);
    document.addEventListener("visibilitychange", poll);
    const timer = window.setInterval(poll, 30000);
    return () => {
      window.removeEventListener("focus", poll);
      document.removeEventListener("visibilitychange", poll);
      window.clearInterval(timer);
    };
  }, [adminId, refresh]);

  async function write(send, apply) {
    if (mutation.current || !adminId) return false;
    const token = {};
    mutation.current = token;
    setBusy(true);
    try {
      const { data } = await send();
      if (owner.current !== adminId) return false;
      setState(previous => previous.owner === adminId ? apply(previous, data) : previous);
      refresh();
      return true;
    } finally {
      if (mutation.current === token) mutation.current = null;
      setBusy(false);
    }
  }

  function markRead(item) {
    if (item.read) return Promise.resolve(true);
    return write(() => api.patch(`/notifications/${item.notificationId}/read`), (previous, updated) => {
      const update = collection => collection && {
        ...collection,
        items: collection.items.map(row => row.notificationId === updated.notificationId ? updated : row),
      };
      return { ...previous, recent: update(previous.recent), page: update(previous.page),
        count: Math.max(0, (previous.count ?? 0) - 1) };
    });
  }

  function markAllRead() {
    return write(() => api.patch("/notifications/read-all"), (previous, { count }) => {
      const update = collection => collection && {
        ...collection, items: collection.items.map(item => ({ ...item, read: true })),
      };
      return { ...previous, recent: update(previous.recent), page: update(previous.page), count };
    });
  }

  const current = state.owner === adminId ? state : {};
  return {
    recent: (current.recentStatus === recentStatus && current.recent) || empty,
    page: (current.pageQuery === query && current.page) || empty, count: current.count ?? 0,
    loading: Boolean(adminId && (current.loading || (!current.error &&
      (current.recentStatus !== recentStatus || (fullPage && current.pageQuery !== query))))),
    error: current.error ?? "", busy, refresh,
    query, setQuery, recentStatus, setRecentStatus, markRead, markAllRead,
  };
}
