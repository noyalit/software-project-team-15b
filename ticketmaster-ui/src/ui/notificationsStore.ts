import { create } from 'zustand';
import type { NotificationDTO } from '../api/types';

export type UiNotification = NotificationDTO & {
  id: string;
  read: boolean;
};

type NotificationsState = {
  notifications: UiNotification[];
  addNotification: (n: NotificationDTO) => void;
  markAllRead: () => void;
  markRead: (id: string) => void;
  clear: () => void;
};

function makeId(n: NotificationDTO) {
  const base = `${n.type}|${n.title}|${n.message}|${n.createdAt}`;
  let h = 0;
  for (let i = 0; i < base.length; i += 1) {
    h = (h << 5) - h + base.charCodeAt(i);
    h |= 0;
  }
  return `${Date.now()}-${Math.abs(h)}`;
}

export const useNotificationsStore = create<NotificationsState>((set) => ({
  notifications: [],
  addNotification: (n) =>
    set((s) => {
      const id = makeId(n);
      const next: UiNotification = { ...n, id, read: false };
      return { notifications: [next, ...s.notifications].slice(0, 200) };
    }),
  markAllRead: () =>
    set((s) => ({
      notifications: s.notifications.map((n) => ({ ...n, read: true })),
    })),
  markRead: (id) =>
    set((s) => ({
      notifications: s.notifications.map((n) => (n.id === id ? { ...n, read: true } : n)),
    })),
  clear: () => set({ notifications: [] }),
}));
