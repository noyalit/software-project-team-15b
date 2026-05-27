import type { AxiosError } from 'axios';
import type { ApiResponse } from './types';

export function getApiErrorMessage<T>(
  e: unknown,
  options?: {
    fallback?: string;
    serverFallback?: string;
  }
): string {
  const fallback = options?.fallback ?? 'Something went wrong.';
  const serverFallback = options?.serverFallback ?? 'We hit a server error. Please try again in a minute.';

  const err = e as AxiosError<ApiResponse<T>>;
  const status = err.response?.status;
  const apiMessage = err.response?.data?.error;

  if (apiMessage && apiMessage.trim()) {
    return apiMessage;
  }

  if (status && status >= 500) {
    return serverFallback;
  }

  return err.message || fallback;
}
