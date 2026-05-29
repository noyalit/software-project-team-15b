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

  const err = e as AxiosError<ApiResponse<T> | unknown>;
  const status = err.response?.status;
  const data = err.response?.data as unknown;

  const apiMessage = (data as ApiResponse<T> | undefined)?.error;
  const messageField = (data as { message?: unknown } | undefined)?.message;
  const errorField = (data as { error?: unknown } | undefined)?.error;

  if (apiMessage && apiMessage.trim()) {
    return apiMessage;
  }

  if (typeof messageField === 'string' && messageField.trim()) {
    return messageField;
  }

  if (typeof errorField === 'string' && errorField.trim()) {
    return errorField;
  }

  if (typeof data === 'string' && data.trim()) {
    return data;
  }

  if (status && status >= 500) {
    return serverFallback;
  }

  return err.message || fallback;
}
