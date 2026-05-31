import { useMemo } from 'react';
import { useNotificationsStore } from '../ui/notificationsStore';

function formatTime(iso: string) {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString();
}

export default function NotificationsPage() {
  const notifications = useNotificationsStore((s) => s.notifications);
  const markAllRead = useNotificationsStore((s) => s.markAllRead);

  const unread = useMemo(() => notifications.filter((n) => !n.read).length, [notifications]);

  return (
    <div className="space-y-4">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">Notifications</h1>
          <p className="mt-1 text-sm text-slate-600">
            {unread > 0 ? `${unread} unread` : 'All caught up.'}
          </p>
        </div>

        <button
          type="button"
          onClick={() => markAllRead()}
          className="rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800"
        >
          Mark all read
        </button>
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white shadow-sm">
        {notifications.length === 0 ? (
          <div className="p-6 text-sm text-slate-600">No notifications yet.</div>
        ) : (
          <div className="divide-y divide-slate-100">
            {notifications.map((n) => (
              <div key={n.id} className="p-4">
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <div className="text-sm font-semibold text-slate-900">
                      {n.title}
                      {!n.read && <span className="ml-2 text-xs font-bold text-rose-600">NEW</span>}
                    </div>
                    <div className="mt-1 text-sm text-slate-700">{n.message}</div>
                    <div className="mt-2 text-xs text-slate-500">{formatTime(n.createdAt)}</div>
                  </div>
                  <div className="text-xs font-semibold text-slate-600">{String(n.type)}</div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
