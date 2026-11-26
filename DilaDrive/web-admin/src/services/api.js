import axios from "axios";
import { logApiError } from "./apiErrors";

const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL
  || "http://localhost:8080/api";

const api = axios.create({
  baseURL: configuredBaseUrl.replace(/\/+$/, ""),
  withCredentials: true,
});

export const adminSessionExpiredEvent = "diladrive:admin-session-expired";
let sessionVersion = 0;

api.interceptors.request.use(config => {
  config.sessionVersion = sessionVersion;
  return config;
});

api.interceptors.response.use(response => {
  if (response.config.url === "/auth/login") sessionVersion += 1;
  return response;
}, error => {
  if (error.response?.status === 401 && error.config?.sessionVersion === sessionVersion
      && !["/auth/login", "/auth/session", "/auth/logout"].includes(error.config?.url)) {
    window.dispatchEvent(new Event(adminSessionExpiredEvent));
  }
  logApiError(error);
  return Promise.reject(error);
});

export default api;
