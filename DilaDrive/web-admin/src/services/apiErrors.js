export function getApiErrorMessage(error, fallback = "Request failed.") {
  const data = error.response?.data;
  const message = typeof data === "string" ? data : data?.message || data?.detail || data?.error;
  const status = error.response?.status;
  const path = error.config?.url;
  return [message || (status === 401 ? "Please sign in as an admin." : fallback),
    status ? `HTTP ${status}${path ? ` on ${path}` : ""}` : ""].filter(Boolean).join(" ? ");
}

export function logApiError(error) {
  if (error.code === "ERR_CANCELED" || error.name === "CanceledError") return;
  const data = error.response?.data;
  // Never log request bodies, credentials, cookies, or session identifiers.
  console.error("API request failed", {
    method: error.config?.method?.toUpperCase(),
    baseURL: error.config?.baseURL,
    path: error.config?.url?.split("?")[0],
    status: error.response?.status,
    code: error.code,
    withCredentials: error.config?.withCredentials,
    message: typeof data === "string" ? data : data?.message || data?.detail || data?.error || error.message,
  });
}
