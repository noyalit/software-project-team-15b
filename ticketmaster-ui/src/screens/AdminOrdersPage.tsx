import { useMutation, useQuery } from '@tanstack/react-query';
import type { AxiosError } from 'axios';
import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { getApiErrorMessage } from '../api/errors';
import { http } from '../api/http';
import type { ApiResponse, OrderHistoryDTO } from '../api/types';
import { useAuthStore } from '../ui/authStore';

type OrdersResponse = ApiResponse<OrderHistoryDTO[]>;

type Mode = 'all' | 'user' | 'event' | 'company';

export default function AdminOrdersPage() {
  const { token, userType, clearAuth } = useAuthStore();

  const [mode, setMode] = useState<Mode>('all');
  const [filterId, setFilterId] = useState('');

  const endpoint = useMemo(() => {
    const id = filterId.trim();
    if (mode === 'all') return '/api/order-history/admin/all';
    if (mode === 'user') return id ? `/api/order-history/admin/user/${id}` : null;
    if (mode === 'event') return id ? `/api/order-history/admin/event/${id}` : null;
    if (mode === 'company') return id ? `/api/order-history/admin/company/${id}` : null;
    return null;
  }, [mode, filterId]);

  const ordersQuery = useQuery({
    queryKey: ['admin', 'orders', mode, filterId, token],
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
    enabled: userType === 'system-admin' && Boolean(token) && Boolean(endpoint),
  });

  const runQuery = useMutation({
    mutationFn: async () => {
      return ordersQuery.refetch();
    },
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

  const orders = ordersQuery.data ?? [];

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
                if (next === 'all') setFilterId('');
              }}
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm"
            >
              <option value="all">All orders</option>
              <option value="user">By userId</option>
              <option value="event">By eventId</option>
              <option value="company">By companyId</option>
            </select>
          </label>

          <label className="block md:col-span-2">
            <div className="text-sm font-medium text-slate-700">ID</div>
            <input
              value={filterId}
              onChange={(e) => setFilterId(e.target.value)}
              disabled={mode === 'all'}
              placeholder={mode === 'all' ? 'Not required' : 'UUID'}
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm disabled:opacity-60"
            />
          </label>

          <div className="md:col-span-3">
            <button
              onClick={() => runQuery.mutate()}
              disabled={ordersQuery.isFetching || !endpoint}
              className="w-full rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
            >
              {ordersQuery.isFetching ? 'Loading…' : 'Load orders'}
            </button>
          </div>
        </div>

        {ordersQuery.isError && (
          <div className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {(ordersQuery.error as Error).message}
          </div>
        )}
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex items-center justify-between gap-3">
          <div className="text-lg font-semibold text-slate-900">Results</div>
          <div className="text-sm text-slate-600">{orders.length} orders</div>
        </div>

        {!ordersQuery.isFetching && !ordersQuery.isError && orders.length === 0 && (
          <div className="mt-4 text-slate-600">No orders found.</div>
        )}

        {orders.length > 0 && (
          <div className="mt-4 overflow-hidden rounded-xl border border-slate-200">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-50 text-slate-700">
                <tr>
                  <th className="px-4 py-3 font-semibold">Order ID</th>
                  <th className="px-4 py-3 font-semibold">User ID</th>
                  <th className="px-4 py-3 font-semibold">Event ID</th>
                  <th className="px-4 py-3 font-semibold">Total</th>
                  <th className="px-4 py-3 font-semibold">Tickets</th>
                  <th className="px-4 py-3 font-semibold">Cancelled</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-200">
                {orders.map((o) => (
                  <tr key={o.orderId} className="bg-white">
                    <td className="px-4 py-3 font-mono text-xs text-slate-800">{o.orderId}</td>
                    <td className="px-4 py-3 font-mono text-xs text-slate-800">{o.userId}</td>
                    <td className="px-4 py-3 font-mono text-xs text-slate-800">{o.eventId}</td>
                    <td className="px-4 py-3 text-slate-800">
                      {o.totalPrice ? `${o.totalPrice.amount} ${o.totalPrice.currency}` : '—'}
                    </td>
                    <td className="px-4 py-3 text-slate-800">{o.tickets?.length ?? 0}</td>
                    <td className="px-4 py-3 text-slate-800">{o.cancelled ? 'Yes' : 'No'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
