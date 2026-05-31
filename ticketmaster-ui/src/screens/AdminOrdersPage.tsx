import { useMutation, useQuery } from '@tanstack/react-query';
import type { AxiosError } from 'axios';
import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { getApiErrorMessage } from '../api/errors';
import { http } from '../api/http';
import type { ApiResponse, CompanyDTO, EventDTO, MemberDTO, OrderHistoryDTO } from '../api/types';
import { useAuthStore } from '../ui/authStore';

type OrdersResponse = ApiResponse<OrderHistoryDTO[]>;
type CompaniesResponse = ApiResponse<CompanyDTO[]>;
type EventsResponse = ApiResponse<EventDTO[]>;
type ResolveMemberResponse = ApiResponse<MemberDTO>;

type Mode = 'all' | 'user' | 'event' | 'company';

export default function AdminOrdersPage() {
  const { token, userType, clearAuth } = useAuthStore();

  const [loadedOrders, setLoadedOrders] = useState<OrderHistoryDTO[]>([]);

  const [mode, setMode] = useState<Mode>('all');
  const [username, setUsername] = useState('');
  const [selectedCompanyId, setSelectedCompanyId] = useState('');
  const [selectedEventId, setSelectedEventId] = useState('');

  const endpoint = useMemo(() => {
    if (mode === 'all') return '/api/order-history/admin/all';
    if (mode === 'company') return selectedCompanyId ? `/api/order-history/admin/company/${selectedCompanyId}` : null;
    if (mode === 'event') return selectedEventId ? `/api/order-history/admin/event/${selectedEventId}` : null;
    return null;
  }, [mode, selectedCompanyId, selectedEventId]);

  const companiesQuery = useQuery({
    queryKey: ['admin', 'orders', 'companies', token],
    queryFn: async () => {
      try {
        const res = await http.get<CompaniesResponse>('/api/companies');
        if (res.data.error) throw new Error(res.data.error);
        return res.data.data ?? [];
      } catch (e) {
        const err = e as AxiosError<CompaniesResponse>;
        const status = err.response?.status;

        if (status === 401) {
          clearAuth();
          throw new Error('Your session expired. Please log in again.');
        }
        if (status === 403) {
          throw new Error('You are not authorized to view companies.');
        }

        throw new Error(
          getApiErrorMessage<CompanyDTO[]>(e, {
            fallback: 'Failed to load companies. Please try again.',
            serverFallback: 'Companies are currently unavailable due to a server issue. Please try again later.',
          })
        );
      }
    },
    enabled: userType === 'system-admin' && Boolean(token),
  });

  const eventsQuery = useQuery({
    queryKey: ['admin', 'orders', 'events', token],
    queryFn: async () => {
      try {
        const res = await http.post<EventsResponse>('/api/events/search', {});
        if (res.data.error) throw new Error(res.data.error);
        return res.data.data ?? [];
      } catch (e) {
        const err = e as AxiosError<EventsResponse>;
        const status = err.response?.status;

        if (status === 401) {
          clearAuth();
          throw new Error('Your session expired. Please log in again.');
        }

        throw new Error(
          getApiErrorMessage<EventDTO[]>(e, {
            fallback: 'Failed to load events. Please try again.',
            serverFallback: 'Events are currently unavailable due to a server issue. Please try again later.',
          })
        );
      }
    },
    enabled: userType === 'system-admin' && Boolean(token),
  });

  const resolveMemberMutation = useMutation({
    mutationFn: async () => {
      const u = username.trim();
      if (!u) {
        throw new Error('Username is required.');
      }

      try {
        const res = await http.get<ResolveMemberResponse>('/api/users/members/resolve', { params: { username: u } });
        if (res.data.error) throw new Error(res.data.error);
        if (!res.data.data) throw new Error('Member not found');
        return res.data.data;
      } catch (e) {
        const err = e as AxiosError<ResolveMemberResponse>;
        const status = err.response?.status;

        if (status === 401) {
          clearAuth();
          throw new Error('Your session expired. Please log in again.');
        }
        if (status === 403) {
          throw new Error('You are not authorized to resolve members.');
        }

        throw new Error(
          getApiErrorMessage<MemberDTO>(e, {
            fallback: 'Failed to resolve member. Please verify the username and try again.',
            serverFallback: 'Member lookup is currently unavailable due to a server issue. Please try again later.',
          })
        );
      }
    },
  });

  const ordersQuery = useQuery({
    queryKey: ['admin', 'orders', mode, selectedCompanyId, selectedEventId, token],
    queryFn: async () => {
      if (!endpoint) return [];
      try {
        const res = await http.get<OrdersResponse>(endpoint);
        if (res.data.error) throw new Error(res.data.error);
        return res.data.data ?? [];
      } catch (e) {
        const err = e as AxiosError<OrdersResponse>;
        const status = err.response?.status;

        if (status === 401) {
          clearAuth();
          throw new Error('Your session expired. Please log in again.');
        }
        if (status === 403) {
          throw new Error('You are not authorized to view order history.');
        }

        throw new Error(
          getApiErrorMessage<OrderHistoryDTO[]>(e, {
            fallback: 'Failed to load order history. Please try again.',
            serverFallback: 'Order history is currently unavailable due to a server issue. Please try again later.',
          })
        );
      }
    },
    enabled: false,
  });

  const runQuery = useMutation({
    mutationFn: async (): Promise<OrderHistoryDTO[]> => {
      if (mode === 'user') {
        const member = await resolveMemberMutation.mutateAsync();
        const res = await http.get<OrdersResponse>(
          `/api/order-history/admin/user/${member.userId}`
        );
        if (res.data.error) throw new Error(res.data.error);
        return res.data.data ?? [];
      }

      if (!endpoint) return [];
      const res = await http.get<OrdersResponse>(endpoint);
      if (res.data.error) throw new Error(res.data.error);
      return res.data.data ?? [];
    },
    onSuccess: (data) => {
      setLoadedOrders(data);
    },
  });

  const eventsById = useMemo(() => {
    const map = new Map<string, EventDTO>();
    for (const ev of eventsQuery.data ?? []) {
      map.set(ev.eventId, ev);
    }
    return map;
  }, [eventsQuery.data]);

  const companiesById = useMemo(() => {
    const map = new Map<string, CompanyDTO>();
    for (const c of companiesQuery.data ?? []) {
      map.set(c.companyId, c);
    }
    return map;
  }, [companiesQuery.data]);

  const userIdsToResolve = useMemo(() => {
    const uniq = new Set<string>();
    for (const o of loadedOrders) {
      if (o.userId) uniq.add(o.userId);
    }
    return Array.from(uniq).sort();
  }, [loadedOrders]);

  const usernamesQuery = useQuery({
    queryKey: ['admin', 'orders', 'usernames', userIdsToResolve, token],
    queryFn: async () => {
      const result: Record<string, string> = {};
      for (const userId of userIdsToResolve) {
        try {
          const res = await http.get<ApiResponse<MemberDTO>>(
            '/api/users/members/resolve-by-id',
            {
              params: { userId },
            }
          );
          if (res.data.error) continue;
          if (res.data.data?.username) {
            result[userId] = res.data.data.username;
          }
        } catch (e) {
          const err = e as AxiosError<ApiResponse<MemberDTO>>;
          const status = err.response?.status;
          if (status === 401) {
            clearAuth();
            throw new Error('Your session expired. Please log in again.');
          }
        }
      }
      return result;
    },
    enabled: userType === 'system-admin' && Boolean(token) && userIdsToResolve.length > 0,
    staleTime: 60_000,
  });

  if (userType !== 'system-admin') {
    return (
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">Orders</h1>
        <p className="mt-2 text-slate-600">Log in as an admin to use these tools.</p>
        <div className="mt-4">
          <Link
            to="/login"
            className="inline-flex items-center rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800"
          >
            Go to login
          </Link>
        </div>
      </div>
    );
  }

  if (!token) {
    return (
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">Orders</h1>
        <p className="mt-2 text-slate-600">Please log in again to continue.</p>
        <div className="mt-4">
          <Link
            to="/login"
            className="inline-flex items-center rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800"
          >
            Go to login
          </Link>
        </div>
      </div>
    );
  }

  const ordersToShow = loadedOrders;

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">Orders</h1>
        <p className="mt-1 text-sm text-slate-600">View order history across the system.</p>
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="text-lg font-semibold text-slate-900">Filters</div>
        <div className="mt-4 grid gap-3 md:grid-cols-3">
          <label className="block">
            <div className="text-sm font-medium text-slate-700">Scope</div>
            <select
              value={mode}
              onChange={(e) => {
                const next = e.target.value as Mode;
                setMode(next);
                setUsername('');
                setSelectedCompanyId('');
                setSelectedEventId('');
              }}
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm"
            >
              <option value="all">All orders</option>
              <option value="user">By username</option>
              <option value="event">By event name</option>
              <option value="company">By company name</option>
            </select>
          </label>

          {mode === 'user' && (
            <label className="block md:col-span-2">
              <div className="text-sm font-medium text-slate-700">Username</div>
              <input
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="username"
                className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm"
              />
            </label>
          )}

          {mode === 'company' && (
            <label className="block md:col-span-2">
              <div className="text-sm font-medium text-slate-700">Company</div>
              <select
                value={selectedCompanyId}
                onChange={(e) => setSelectedCompanyId(e.target.value)}
                disabled={companiesQuery.isPending || companiesQuery.isError}
                className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm disabled:opacity-60"
              >
                <option value="">Select a company…</option>
                {companiesQuery.data?.map((c) => (
                  <option key={c.companyId} value={c.companyId}>
                    {c.name}
                  </option>
                ))}
              </select>
            </label>
          )}

          {mode === 'event' && (
            <label className="block md:col-span-2">
              <div className="text-sm font-medium text-slate-700">Event</div>
              <select
                value={selectedEventId}
                onChange={(e) => setSelectedEventId(e.target.value)}
                disabled={eventsQuery.isPending || eventsQuery.isError}
                className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm disabled:opacity-60"
              >
                <option value="">Select an event…</option>
                {eventsQuery.data?.map((ev) => (
                  <option key={ev.eventId} value={ev.eventId}>
                    {ev.name}
                  </option>
                ))}
              </select>
            </label>
          )}

          <div className="md:col-span-3">
            <button
              onClick={() => runQuery.mutate()}
              disabled={runQuery.isPending || (mode !== 'user' && !endpoint) || (mode === 'user' && !username.trim())}
              className="w-full rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
            >
              {runQuery.isPending ? 'Loading…' : 'Load orders'}
            </button>
          </div>
        </div>

        {(ordersQuery.isError || runQuery.isError) && (
          <div className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {((ordersQuery.error ?? runQuery.error) as Error).message}
          </div>
        )}
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex items-center justify-between gap-3">
          <div className="text-lg font-semibold text-slate-900">Results</div>
          <div className="text-sm text-slate-600">{ordersToShow.length} orders</div>
        </div>

        {!runQuery.isPending && !ordersQuery.isError && !runQuery.isError && ordersToShow.length === 0 && (
          <div className="mt-4 text-slate-600">No orders found.</div>
        )}

        {ordersToShow.length > 0 && (
          <div className="mt-4 overflow-hidden rounded-xl border border-slate-200">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-50 text-slate-700">
                <tr>
                  <th className="px-4 py-3 font-semibold">Order ID</th>
                  <th className="px-4 py-3 font-semibold">Username</th>
                  <th className="px-4 py-3 font-semibold">Event</th>
                  <th className="px-4 py-3 font-semibold">Company</th>
                  <th className="px-4 py-3 font-semibold">Total</th>
                  <th className="px-4 py-3 font-semibold">Tickets</th>
                  <th className="px-4 py-3 font-semibold">Cancelled</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-200">
                {ordersToShow.map((o) => {
                  const ev = eventsById.get(o.eventId);
                  const company = ev?.companyId
                    ? companiesById.get(ev.companyId)
                    : undefined;
                  const usernameResolved = usernamesQuery.data?.[o.userId];
                  return (
                  <tr key={o.orderId} className="bg-white">
                    <td className="px-4 py-3 font-mono text-xs text-slate-800">{o.orderId}</td>
                    <td className="px-4 py-3 text-slate-800">
                      {usernameResolved ?? '—'}
                    </td>
                    <td className="px-4 py-3 text-slate-800">
                      {ev ? `${ev.name} — ${ev.artist}` : o.eventId}
                    </td>
                    <td className="px-4 py-3 text-slate-800">
                      {company ? company.name : ev?.companyId ?? '—'}
                    </td>
                    <td className="px-4 py-3 text-slate-800">
                      {o.totalPrice ? `${o.totalPrice.amount} ${o.totalPrice.currency}` : '—'}
                    </td>
                    <td className="px-4 py-3 text-slate-800">{o.tickets?.length ?? 0}</td>
                    <td className="px-4 py-3 text-slate-800">{o.cancelled ? 'Yes' : 'No'}</td>
                  </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
