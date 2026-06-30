import axios from 'axios';
import { useAuthStore, getPersistedToken } from '../ui/authStore';
import type { ApiResponse } from './types';

const BOOTSTRAP_LOCK = 'tm-bootstrap';

/**
 * Ensures this browser has a guest entrance token, returning it.
 *
 * Wrapped in a Web Lock so that when several tabs open at once (all with empty
 * storage), exactly one tab calls POST /api/users/enter; the others wait, then
 * pick up the token the winner persisted instead of minting their own guest
 * session. Falls back to an unlocked path when the Web Locks API is absent.
 *
 * Uses a bare axios call (not the shared `http` instance) to avoid recursing
 * through the 401 interceptor.
 */
export async function ensureGuestToken(): Promise<string | null> {
  const existing = useAuthStore.getState().token;
  if (existing) return existing;

  const run = async (): Promise<string | null> => {
    // Re-check against persisted storage inside the lock: another tab may have
    // won the race and written a token while we were waiting.
    const persisted = getPersistedToken();
    if (persisted) {
      useAuthStore.getState().syncFromStorage();
      return persisted;
    }

    const res = await axios.post<ApiResponse<string>>('/api/users/enter');
    if (res.data.error) throw new Error(res.data.error);
    const newToken = res.data.data;
    if (typeof newToken !== 'string' || !newToken) throw new Error('No token returned');

    useAuthStore.getState().setAuth(newToken, 'guest');
    return newToken;
  };

  if (typeof navigator !== 'undefined' && navigator.locks) {
    return navigator.locks.request(BOOTSTRAP_LOCK, run);
  }
  return run();
}
