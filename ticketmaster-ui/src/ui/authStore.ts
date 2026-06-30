import { create } from 'zustand';

export type UserType = 'guest' | 'temp' | 'member' | 'system-admin' | null;

type AuthState = {
  token: string | null;
  userType: UserType;
  username: string | null;
  setAuth: (token: string, userType: Exclude<UserType, null>, username?: string | null) => void;
  setUsername: (username: string | null) => void;
  clearAuth: () => void;
  logout: () => void;
  syncFromStorage: () => void;
};

const STORAGE_KEY = 'tm_auth_v1';

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

function clearOrderContext() {
  localStorage.removeItem('activeOrderId');
  sessionStorage.removeItem('activeOrderId');
  localStorage.removeItem('guestBirthDate');
}

export const useAuthStore = create<AuthState>((set, get) => {
  const initial = load();

  // Keep tabs in sync: when another tab writes the auth entry in localStorage,
  // the `storage` event fires here (it only fires in *other* tabs, never the
  // writer) so we refresh this tab's in-memory state. This makes login/logout/
  // token-refresh in one tab propagate to all open tabs.
  if (typeof window !== 'undefined') {
    window.addEventListener('storage', (e) => {
      if (e.key !== STORAGE_KEY) return;
      get().syncFromStorage();
    });
  }

  return {
    token: initial.token,
    userType: initial.userType,
    username: initial.username,
    setAuth: (token, userType, username = null) => {
      clearOrderContext();
      save(token, userType, username);
      set({ token, userType, username });
    },
    setUsername: (username) => {
      const { token, userType } = get();
      save(token, userType, username);
      set({ username });
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
      set({ token: next.token, userType: next.userType, username: next.username });
    },
  };
});

/** Reads the token directly from persisted storage, bypassing in-memory state. */
export function getPersistedToken(): string | null {
  return load().token;
}
