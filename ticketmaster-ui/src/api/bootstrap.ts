import axios from 'axios';
import { useAuthStore, getPersistedToken } from '../ui/authStore';
import type { ApiResponse, EntranceDTO } from './types';

const BOOTSTRAP_LOCK = 'tm-bootstrap';

/**
 * Ensures this browser has a usable session token, returning it — or `null` when the
 * visitor was placed in the site-wide waiting queue.
 *
 * POST /api/users/enter returns an {@link EntranceDTO}: when the site has capacity the
 * visitor is ADMITTED and we persist the guest token; when the site is full the visitor
 * is QUEUED, so we stash the temporary queue token (the SiteQueueGate then polls until
 * admitted) and return `null` to signal "no session yet".
 *
 * Wrapped in a Web Lock so that when several tabs open at once (all with empty storage),
 * exactly one tab calls /enter; the others wait and pick up whatever the winner persisted.
 * Falls back to an unlocked path when the Web Locks API is absent.
 *
 * Uses a bare axios call (not the shared `http` instance) to avoid recursing through the
 * 401 interceptor.
 */
export async function ensureGuestToken(): Promise<string | null> {
  const state = useAuthStore.getState();
  if (state.token) return state.token;
  // Already waiting in the site queue — don't mint another entry.
  if (state.queueToken) return null;

  const run = async (): Promise<string | null> => {
    // Re-check persisted storage inside the lock: another tab may have won the race.
    const persisted = getPersistedToken();
    if (persisted) {
      useAuthStore.getState().syncFromStorage();
      return persisted;
    }

    const res = await axios.post<ApiResponse<EntranceDTO>>('/api/users/enter');
    if (res.data.error) throw new Error(res.data.error);
    const entrance = res.data.data;
    if (!entrance || typeof entrance.token !== 'string' || !entrance.token) {
      throw new Error('No token returned');
    }

    if (entrance.status === 'QUEUED') {
      useAuthStore.getState().setQueued(entrance.token, entrance.position);
      return null;
    }

    useAuthStore.getState().setAuth(entrance.token, 'guest');
    return entrance.token;
  };

  if (typeof navigator !== 'undefined' && navigator.locks) {
    return navigator.locks.request(BOOTSTRAP_LOCK, run);
  }
  return run();
}
