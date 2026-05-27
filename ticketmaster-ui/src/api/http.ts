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
