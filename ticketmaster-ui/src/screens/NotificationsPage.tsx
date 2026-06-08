import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { http } from '../api/http';
import type { ApiResponse, CompanyDTO } from '../api/types';
import { useAuthStore } from '../ui/authStore';
import { useNotificationsStore } from '../ui/notificationsStore';

function formatTime(iso: string) {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString();
}

const uuidRegex = /\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b/gi;

function extractUuids(text: string) {
  const m = text.match(uuidRegex);
  return m ? Array.from(new Set(m.map((x) => x.toLowerCase()))) : [];
}

export default function NotificationsPage() {
  const notifications = useNotificationsStore((s) => s.notifications);
  const markAllRead = useNotificationsStore((s) => s.markAllRead);
  const { token } = useAuthStore();

  const unread = useMemo(() => notifications.filter((n) => !n.read).length, [notifications]);

  const companyIdsInList = useMemo(() => {
    const ids = new Set<string>();
    for (const n of notifications) {
      if (n.type !== 'PERMISSION_CHANGED' && n.type !== 'COMPANY_CLOSED' && n.type !== 'COMPANY_SUSPENDED') {
        continue;
      }
      for (const id of extractUuids(`${n.title} ${n.message}`)) ids.add(id);
    }
    return Array.from(ids);
  }, [notifications]);

  const companyNamesQuery = useQuery({
    queryKey: ['company-names', 'notifications', companyIdsInList],
    queryFn: async () => {
      const pairs = await Promise.all(
        companyIdsInList.map(async (id) => {
          try {
            const res = await http.get<ApiResponse<CompanyDTO>>(`/api/companies/${id}`);
            if (res.data.error || !res.data.data) return [id, null] as const;
            return [id, res.data.data.name] as const;
          } catch {
            return [id, null] as const;
          }
        })
      );
      return Object.fromEntries(pairs.filter(([, name]) => Boolean(name))) as Record<string, string>;
    },
    enabled: Boolean(token) && companyIdsInList.length > 0,
    staleTime: 5 * 60_000,
  });

  const companyNameMap = companyNamesQuery.data ?? {};

  const displayText = (text: string) => {
    return text.replace(uuidRegex, (m) => companyNameMap[m.toLowerCase()] ?? m);
  };

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
                      {displayText(n.title)}
                      {!n.read && <span className="ml-2 text-xs font-bold text-rose-600">NEW</span>}
                    </div>
                    <div className="mt-1 text-sm text-slate-700">{displayText(n.message)}</div>
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
