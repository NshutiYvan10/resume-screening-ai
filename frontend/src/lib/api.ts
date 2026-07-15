import axios, { AxiosError, AxiosRequestConfig } from 'axios';

const ACCESS_KEY = 'ra_access';
const REFRESH_KEY = 'ra_refresh';

export const tokenStore = {
  get access() {
    return localStorage.getItem(ACCESS_KEY);
  },
  get refresh() {
    return localStorage.getItem(REFRESH_KEY);
  },
  set(access: string | null, refresh: string | null) {
    if (access) localStorage.setItem(ACCESS_KEY, access);
    else localStorage.removeItem(ACCESS_KEY);
    if (refresh) localStorage.setItem(REFRESH_KEY, refresh);
    else localStorage.removeItem(REFRESH_KEY);
  },
  clear() {
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
  },
};

export const api = axios.create({
  baseURL: '/api/v1',
  headers: { 'Content-Type': 'application/json' },
});

api.interceptors.request.use((config) => {
  const token = tokenStore.access;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

let refreshing: Promise<string | null> | null = null;
let onAuthFailure: (() => void) | null = null;

export function setAuthFailureHandler(fn: () => void) {
  onAuthFailure = fn;
}

async function performRefresh(): Promise<string | null> {
  const refresh = tokenStore.refresh;
  if (!refresh) return null;
  try {
    const { data } = await axios.post('/api/v1/auth/refresh', { refreshToken: refresh });
    tokenStore.set(data.accessToken, data.refreshToken);
    return data.accessToken;
  } catch {
    tokenStore.clear();
    return null;
  }
}

api.interceptors.response.use(
  (res) => res,
  async (error: AxiosError) => {
    const original = error.config as AxiosRequestConfig & { _retried?: boolean };
    const status = error.response?.status;

    if (status === 401 && original && !original._retried && tokenStore.refresh) {
      original._retried = true;
      if (!refreshing) refreshing = performRefresh().finally(() => (refreshing = null));
      const newToken = await refreshing;
      if (newToken) {
        original.headers = { ...original.headers, Authorization: `Bearer ${newToken}` };
        return api(original);
      }
      onAuthFailure?.();
    }
    return Promise.reject(error);
  }
);

export function apiErrorMessage(error: unknown, fallback = 'Something went wrong'): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { message?: string; fieldErrors?: Record<string, string> } | undefined;
    if (data?.fieldErrors && Object.keys(data.fieldErrors).length) {
      return Object.values(data.fieldErrors)[0];
    }
    return data?.message || error.message || fallback;
  }
  return fallback;
}
