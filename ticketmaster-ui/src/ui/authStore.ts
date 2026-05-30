import { create } from 'zustand';

export type UserType = 'guest' | 'member' | 'system-admin' | null;

type AuthState = {
  token: string | null;
  userType: UserType;
  username: string | null;
  setAuth: (token: string, userType: Exclude<UserType, null>, username?: string | null) => void;
  setUsername: (username: string | null) => void;
  clearAuth: () => void;
  logout: () => void;
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
  localStorage.removeItem('guestBirthDate');
}

export const useAuthStore = create<AuthState>((set, get) => {
  const initial = load();

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
  };
});
