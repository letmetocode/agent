import axios from 'axios';
import { SESSION_TOKEN_KEY, useSessionStore } from '@/features/session/sessionStore';
import { storage } from '@/shared/utils/storage';

const parsePositiveInt = (raw: string | undefined, fallback: number) => {
  if (!raw) {
    return fallback;
  }
  const value = Number(raw);
  if (!Number.isFinite(value) || value <= 0) {
    return fallback;
  }
  return Math.floor(value);
};

export const DEFAULT_HTTP_TIMEOUT_MS = parsePositiveInt(import.meta.env.VITE_HTTP_TIMEOUT_MS, 15000);
export const CHAT_HTTP_TIMEOUT_MS = parsePositiveInt(
  import.meta.env.VITE_CHAT_TIMEOUT_MS,
  Math.max(DEFAULT_HTTP_TIMEOUT_MS, 90000)
);

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8091',
  timeout: DEFAULT_HTTP_TIMEOUT_MS
});

http.interceptors.request.use((config) => {
  const token = storage.get<string>(SESSION_TOKEN_KEY, '');
  if (!token) {
    return config;
  }
  config.headers = config.headers || {};
  config.headers.Authorization = `Bearer ${token}`;
  return config;
});

const handleUnauthorized = () => {
  useSessionStore.getState().clearAuth();
  if (typeof window === 'undefined') {
    return;
  }
  if (window.location.pathname !== '/login') {
    window.location.assign('/login');
  }
};

http.interceptors.response.use(
  (response) => {
    if (response?.status === 401) {
      handleUnauthorized();
    }
    return response;
  },
  (error) => {
    if (error?.response?.status === 401) {
      handleUnauthorized();
    }
    return Promise.reject(error);
  }
);
