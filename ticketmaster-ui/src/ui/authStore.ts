import { create } from 'zustand';

export type UserType = 'guest' | 'member' | 'system-admin' | null;

type AuthState = {
  token: string | null;
  userType: UserType;
  setAuth: (token: string, userType: Exclude<UserType, null>) => void;
  clearAuth: () => void;
  logout: () => void;
};

const STORAGE_KEY = 'tm_auth_v1';

function load(): Pick<AuthState, 'token' | 'userType'> {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return { token: null, userType: null };
    const parsed = JSON.parse(raw) as { token: string; userType: Exclude<UserType, null> };
    return { token: parsed.token, userType: parsed.userType };
  } catch {
    return { token: null, userType: null };
  }
}

function save(token: string | null, userType: UserType) {
  if (!token || !userType) {
    localStorage.removeItem(STORAGE_KEY);
    return;
  }
  localStorage.setItem(STORAGE_KEY, JSON.stringify({ token, userType }));
}

export const useAuthStore = create<AuthState>((set, get) => {
  const initial = load();

  return {
    token: initial.token,
    userType: initial.userType,
    setAuth: (token, userType) => {
      save(token, userType);
      set({ token, userType });
    },
    clearAuth: () => {
      save(null, null);
      set({ token: null, userType: null });
    },
    logout: () => {
      // UI-side logout: backend logout is a POST requiring the entrance token.
      // This UI keeps it simple and clears auth; you can wire POST /api/users/logout later.
      get().clearAuth();
    },
  };
});
