import axios from 'axios';
import { useAuthStore } from '../ui/authStore';

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
        const enterRes = await axios.post('/api/users/enter');
        const newToken = enterRes?.data?.data;
        if (typeof newToken === 'string' && newToken) {
          store.setAuth(newToken, 'guest');
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
