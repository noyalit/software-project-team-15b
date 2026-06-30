import { create } from 'zustand';

export type UserType = 'guest' | 'member' | 'system-admin' | null;

type AuthState = {
  token: string | null;
  userType: UserType;
  username: string | null;
  /** Temporary site-queue token held while waiting for admission (no session yet). */
  queueToken: string | null;
  /** Zero-based position in the site queue, when waiting. */
  queuePosition: number | null;
  setAuth: (token: string, userType: Exclude<UserType, null>, username?: string | null) => void;
  setUsername: (username: string | null) => void;
  setQueued: (queueToken: string, position: number | null) => void;
  clearQueued: () => void;
  clearAuth: () => void;
  logout: () => void;
  syncFromStorage: () => void;
};

const STORAGE_KEY = 'tm_auth_v1';
const QUEUE_KEY = 'tm_queue_v1';

function load(): Pick<AuthState, 'token' | 'userType' | 'username'> {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return { token: null, userType: null, username: null };
    const parsed = JSON.parse(raw) as { token: string; userType: Exclude<UserType, null>; username?: string | null };
    return { token: parsed.token, userType: parsed.userType, username: parsed.username ?? null };
  } catch {
    return { token: null, userType: null, username: null };
  }
}

function save(token: string | null, userType: UserType, username: string | null) {
  if (!token || !userType) {
    localStorage.removeItem(STORAGE_KEY);
    return;
  }
  localStorage.setItem(STORAGE_KEY, JSON.stringify({ token, userType, username }));
}

function loadQueue(): { queueToken: string | null; queuePosition: number | null } {
  try {
    const raw = localStorage.getItem(QUEUE_KEY);
    if (!raw) return { queueToken: null, queuePosition: null };
    const parsed = JSON.parse(raw) as { queueToken: string; queuePosition: number | null };
    return { queueToken: parsed.queueToken, queuePosition: parsed.queuePosition ?? null };
  } catch {
    return { queueToken: null, queuePosition: null };
  }
}

function saveQueue(queueToken: string | null, queuePosition: number | null) {
  if (!queueToken) {
    localStorage.removeItem(QUEUE_KEY);
    return;
  }
  localStorage.setItem(QUEUE_KEY, JSON.stringify({ queueToken, queuePosition }));
}

function clearOrderContext() {
  localStorage.removeItem('activeOrderId');
  sessionStorage.removeItem('activeOrderId');
  localStorage.removeItem('guestBirthDate');
}

export const useAuthStore = create<AuthState>((set, get) => {
  const initial = load();
  const initialQueue = loadQueue();

  // Keep tabs in sync: when another tab writes the auth entry in localStorage,
  // the `storage` event fires here (it only fires in *other* tabs, never the
  // writer) so we refresh this tab's in-memory state. This makes login/logout/
  // token-refresh in one tab propagate to all open tabs.
  if (typeof window !== 'undefined') {
    window.addEventListener('storage', (e) => {
      if (e.key !== STORAGE_KEY && e.key !== QUEUE_KEY) return;
      get().syncFromStorage();
    });
  }

  return {
    token: initial.token,
    userType: initial.userType,
    username: initial.username,
    queueToken: initialQueue.queueToken,
    queuePosition: initialQueue.queuePosition,
    setAuth: (token, userType, username = null) => {
      clearOrderContext();
      // Admission clears any pending queue session.
      saveQueue(null, null);
      save(token, userType, username);
      set({ token, userType, username, queueToken: null, queuePosition: null });
    },
    setUsername: (username) => {
      const { token, userType } = get();
      save(token, userType, username);
      set({ username });
    },
    setQueued: (queueToken, position) => {
      saveQueue(queueToken, position);
      set({ queueToken, queuePosition: position });
    },
    clearQueued: () => {
      saveQueue(null, null);
      set({ queueToken: null, queuePosition: null });
    },
    clearAuth: () => {
      clearOrderContext();
      save(null, null, null);
      set({ token: null, userType: null, username: null });
    },
    logout: () => {
      // UI-side logout: backend logout is a POST requiring the entrance token.
      // This UI keeps it simple and clears auth; you can wire POST /api/users/logout later.
      get().clearAuth();
    },
    syncFromStorage: () => {
      const next = load();
      const nextQueue = loadQueue();
      set({
        token: next.token,
        userType: next.userType,
        username: next.username,
        queueToken: nextQueue.queueToken,
        queuePosition: nextQueue.queuePosition,
      });
    },
  };
});

/** Reads the token directly from persisted storage, bypassing in-memory state. */
export function getPersistedToken(): string | null {
  return load().token;
}
