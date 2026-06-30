import axios from 'axios';
import { useAuthStore } from '../ui/authStore';
import { ensureGuestToken } from './bootstrap';

export const http = axios.create({
  baseURL: '/',
});

http.interceptors.request.use((config) => {
  const { token } = useAuthStore.getState();
  if (token) {
    config.headers = config.headers ?? {};
    config.headers['Authorization'] = token;
  }
  return config;
});

http.interceptors.response.use(
  (res) => res,
  async (error) => {
    const status = error?.response?.status;
    const originalRequest = error?.config;
    if (!originalRequest) {
      return Promise.reject(error);
    }

    if (status === 401 && !originalRequest.__retry) {
      originalRequest.__retry = true;

      const store = useAuthStore.getState();
      store.clearAuth();

      try {
        // ensureGuestToken serializes concurrent re-entry across tabs via a Web
        // Lock, so simultaneous 401s don't each mint a separate guest session.
        const newToken = await ensureGuestToken();
        if (typeof newToken === 'string' && newToken) {
          originalRequest.headers = originalRequest.headers ?? {};
          originalRequest.headers['Authorization'] = newToken;
          return http.request(originalRequest);
        }
      } catch {
        // fall through
      }
    }

    return Promise.reject(error);
  }
);
