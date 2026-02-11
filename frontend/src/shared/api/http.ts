import axios from 'axios';

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
