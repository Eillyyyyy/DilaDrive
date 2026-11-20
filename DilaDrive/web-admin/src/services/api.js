import axios from "axios";
import { logApiError } from "./apiErrors";

const configuredBaseUrl = import.meta.env.VITE_API_BASE_URL
  || "http://localhost:8080/api";

const api = axios.create({
  baseURL: configuredBaseUrl.replace(/\/+$/, ""),
  withCredentials: true,
});

api.interceptors.response.use(response => response, error => {
  logApiError(error);
  return Promise.reject(error);
});

export default api;
