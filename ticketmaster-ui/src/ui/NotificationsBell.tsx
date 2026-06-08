import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useNotificationsStore } from './notificationsStore';
import { http } from '../api/http';
import type { ApiResponse, CompanyDTO } from '../api/types';
import { useAuthStore } from './authStore';

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

export default function NotificationsBell() {
  const [open, setOpen] = useState(false);
  const notifications = useNotificationsStore((s) => s.notifications);
  const markAllRead = useNotificationsStore((s) => s.markAllRead);
  const { token } = useAuthStore();

  const unread = useMemo(() => notifications.filter((n) => !n.read).length, [notifications]);
  const top = notifications.slice(0, 5);

  const companyIdsInTop = useMemo(() => {
    const ids = new Set<string>();
    for (const n of top) {
      if (n.type !== 'PERMISSION_CHANGED' && n.type !== 'COMPANY_CLOSED' && n.type !== 'COMPANY_SUSPENDED') {
        continue;
      }
      for (const id of extractUuids(`${n.title} ${n.message}`)) ids.add(id);
    }
    return Array.from(ids);
  }, [top]);

  const companyNamesQuery = useQuery({
    queryKey: ['company-names', companyIdsInTop],
    queryFn: async () => {
      const pairs = await Promise.all(
        companyIdsInTop.map(async (id) => {
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
    enabled: Boolean(token) && companyIdsInTop.length > 0,
    staleTime: 5 * 60_000,
  });

  const companyNameMap = companyNamesQuery.data ?? {};

  const displayText = (text: string) => {
    return text.replace(uuidRegex, (m) => companyNameMap[m.toLowerCase()] ?? m);
  };

  return (
    <div className="relative">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="relative rounded-md px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-100"
      >
        Notifications
        {unread > 0 && (
          <span className="ml-2 rounded-full bg-rose-600 px-2 py-0.5 text-xs font-bold text-white">
            {unread}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 mt-2 w-96 max-w-[90vw] rounded-xl border border-slate-200 bg-white shadow-lg">
          <div className="flex items-center justify-between border-b border-slate-200 px-4 py-3">
            <div className="text-sm font-semibold text-slate-900">Notifications</div>
            <button
              type="button"
              onClick={() => markAllRead()}
              className="text-xs font-semibold text-slate-700 hover:text-slate-900"
            >
              Mark all read
            </button>
          </div>

          <div className="max-h-80 overflow-auto">
            {top.length === 0 ? (
              <div className="px-4 py-6 text-sm text-slate-600">No notifications yet.</div>
            ) : (
              top.map((n) => (
                <div key={n.id} className="border-b border-slate-100 px-4 py-3">
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <div className="text-sm font-semibold text-slate-900">
                        {displayText(n.title)}
                        {!n.read && <span className="ml-2 text-xs font-bold text-rose-600">NEW</span>}
                      </div>
                      <div className="mt-1 text-sm text-slate-700">{displayText(n.message)}</div>
                      <div className="mt-2 text-xs text-slate-500">{formatTime(n.createdAt)}</div>
                    </div>
                  </div>
                </div>
              ))
            )}
          </div>

          <div className="flex items-center justify-between px-4 py-3">
            <Link
              to="/notifications"
              onClick={() => setOpen(false)}
              className="text-sm font-semibold text-indigo-700 hover:text-indigo-800"
            >
              View all
            </Link>
            <button
              type="button"
              onClick={() => setOpen(false)}
              className="rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800"
            >
              Close
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
