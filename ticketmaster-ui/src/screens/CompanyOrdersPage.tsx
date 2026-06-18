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

export default function CompanyOrdersPage() {
  const { token, userType, clearAuth } = useAuthStore();

  const [selectedCompanyId, setSelectedCompanyId] = useState('');
  const [loadedOrders, setLoadedOrders] = useState<OrderHistoryDTO[]>([]);

  const meQuery = useQuery({
    queryKey: ['me', token],
    queryFn: async () => {
      const res = await http.get<ApiResponse<MemberDTO>>('/api/users/me');
      if (res.data.error) throw new Error(res.data.error);
      if (!res.data.data) throw new Error('No profile data');
      return res.data.data;
    },
    enabled: userType === 'member' && Boolean(token),
  });

  const companiesQuery = useQuery({
    queryKey: ['company-orders', 'companies', token],
    queryFn: async () => {
      try {
        const res = await http.get<CompaniesResponse>('/api/companies/me');
        if (res.data.error) throw new Error(res.data.error);
        return res.data.data ?? [];
      } catch (e) {
        const err = e as AxiosError<CompaniesResponse>;
        const status = err.response?.status;

        if (status === 401) {
          clearAuth();
          throw new Error('Your session expired. Please log in again.');
        }

        throw new Error(
          getApiErrorMessage<CompanyDTO[]>(e, {
            fallback: 'Failed to load your companies. Please try again.',
            serverFallback:
              'Companies are currently unavailable due to a server issue. Please try again later.',
          })
        );
      }
    },
    enabled: userType === 'member' && Boolean(token),
  });

  const companyManagerCompaniesQuery = useQuery({
    queryKey: ['company-orders', 'company-manager-companies', token, meQuery.data?.assignedRoles],
    queryFn: async () => {
      const companyIds = (meQuery.data?.assignedRoles ?? [])
        .filter((role) => role.roleName === 'CompanyManager' && role.companyId)
        .map((role) => role.companyId as string);

      const uniqueCompanyIds = [...new Set(companyIds)];

      const companies = await Promise.all(
        uniqueCompanyIds.map(async (companyId) => {
          const res = await http.get<ApiResponse<CompanyDTO>>(`/api/companies/${companyId}`);
          if (res.data.error) throw new Error(res.data.error);
          if (!res.data.data) throw new Error('Company not found');
          return res.data.data;
        })
      );

      return companies;
    },
    enabled:
      userType === 'member' &&
      Boolean(token) &&
      Boolean(meQuery.data?.assignedRoles?.some((role) => role.roleName === 'CompanyManager')),
  });

  const eventsQuery = useQuery({
    queryKey: ['company-orders', 'events', token],
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
            serverFallback:
              'Events are currently unavailable due to a server issue. Please try again later.',
          })
        );
      }
    },
    enabled: userType === 'member' && Boolean(token),
  });

  const eventsById = useMemo(() => {
    const map = new Map<string, EventDTO>();
    for (const ev of eventsQuery.data ?? []) {
      map.set(ev.eventId, ev);
    }
    return map;
  }, [eventsQuery.data]);

  const userIdsToResolve = useMemo(() => {
    const uniq = new Set<string>();
    for (const o of loadedOrders) {
      if (o.userId) uniq.add(o.userId);
    }
    return Array.from(uniq).sort();
  }, [loadedOrders]);

  const usernamesQuery = useQuery({
    queryKey: ['company-orders', 'usernames', userIdsToResolve, token],
    queryFn: async () => {
      const result: Record<string, string> = {};
      for (const userId of userIdsToResolve) {
        try {
          const res = await http.get<ResolveMemberResponse>(
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
          const err = e as AxiosError<ResolveMemberResponse>;
          const status = err.response?.status;
          if (status === 401) {
            clearAuth();
            throw new Error('Your session expired. Please log in again.');
          }
        }
      }
      return result;
    },
    enabled: userType === 'member' && Boolean(token) && userIdsToResolve.length > 0,
    staleTime: 60_000,
  });

  const runQuery = useMutation({
    mutationFn: async (): Promise<OrderHistoryDTO[]> => {
      if (!selectedCompanyId) return [];

      try {
        const res = await http.get<OrdersResponse>(
          `/api/order-history/company/${selectedCompanyId}/orders`
        );
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
          throw new Error('You are not authorized to view this company\'s order history.');
        }

        throw new Error(
          getApiErrorMessage<OrderHistoryDTO[]>(e, {
            fallback: 'Failed to load company orders. Please try again.',
            serverFallback:
              'Company order history is currently unavailable due to a server issue. Please try again later.',
          })
        );
      }
    },
    onSuccess: (data) => {
      setLoadedOrders(data);
    },
  });

  if (userType !== 'member') {
    return (
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">
          Company Orders
        </h1>
        <p className="mt-2 text-slate-600">Log in as a member to view company orders.</p>
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
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">
          Company Orders
        </h1>
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

  const visibleCompanies = [
    ...(companiesQuery.data ?? []),
    ...(companyManagerCompaniesQuery.data ?? []),
  ].filter(
    (company, index, arr) =>
      arr.findIndex((c) => c.companyId === company.companyId) === index
  );

  const selectedCompany = visibleCompanies.find(
    (c) => c.companyId === selectedCompanyId
  );

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">
          Company Orders
        </h1>
        <p className="mt-1 text-sm text-slate-600">
          View tickets sold and order history for your company events.
        </p>
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="text-lg font-semibold text-slate-900">Filters</div>

        <div className="mt-4 grid gap-3 md:grid-cols-3">
          <label className="block md:col-span-2">
            <div className="text-sm font-medium text-slate-700">Company</div>
            <select
              value={selectedCompanyId}
              onChange={(e) => {
                setSelectedCompanyId(e.target.value);
                setLoadedOrders([]);
              }}
              disabled={companiesQuery.isPending || companiesQuery.isError}
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm disabled:opacity-60"
            >
              <option value="">Select a company…</option>
              {visibleCompanies.map((c) => (
                <option key={c.companyId} value={c.companyId}>
                  {c.name}
                </option>
              ))}
            </select>
          </label>

          <div className="flex items-end">
            <button
              onClick={() => runQuery.mutate()}
              disabled={runQuery.isPending || !selectedCompanyId}
              className="w-full rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
            >
              {runQuery.isPending ? 'Loading…' : 'Load orders'}
            </button>
          </div>
        </div>

        {runQuery.isError && (
          <div className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {(runQuery.error as Error).message}
          </div>
        )}
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex items-center justify-between gap-3">
          <div className="text-lg font-semibold text-slate-900">Results</div>
          <div className="text-sm text-slate-600">{loadedOrders.length} orders</div>
        </div>

        {selectedCompany && (
          <div className="mt-1 text-sm text-slate-600">
            Company: {selectedCompany.name}
          </div>
        )}

        {!runQuery.isPending && !runQuery.isError && loadedOrders.length === 0 && (
          <div className="mt-4 text-slate-600">No orders found.</div>
        )}

        {loadedOrders.length > 0 && (
          <div className="mt-4 overflow-hidden rounded-xl border border-slate-200">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-50 text-slate-700">
                <tr>
                  <th className="px-4 py-3 font-semibold">Order ID</th>
                  <th className="px-4 py-3 font-semibold">Event</th>
                  <th className="px-4 py-3 font-semibold">Username</th>
                  <th className="px-4 py-3 font-semibold">Total</th>
                  <th className="px-4 py-3 font-semibold">Tickets</th>
                  <th className="px-4 py-3 font-semibold">Cancelled</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-200">
                {loadedOrders.map((o) => {
                  const ev = eventsById.get(o.eventId);
                  const usernameResolved = usernamesQuery.data?.[o.userId];
                  return (
                    <tr key={o.orderId} className="bg-white">
                      <td className="px-4 py-3 font-mono text-xs text-slate-800">
                        {o.orderId}
                      </td>
                      <td className="px-4 py-3 text-slate-800">
                        {ev ? `${ev.name} — ${ev.artist}` : o.eventId}
                      </td>
                      <td className="px-4 py-3 text-slate-800">
                        {usernameResolved ?? '—'}
                      </td>
                      <td className="px-4 py-3 text-slate-800">
                        {o.totalPrice
                          ? `${o.totalPrice.amount} ${o.totalPrice.currency}`
                          : '—'}
                      </td>
                      <td className="px-4 py-3 text-slate-800">
                        {o.tickets?.length ?? 0}
                      </td>
                      <td className="px-4 py-3 text-slate-800">
                        {o.cancelled ? 'Yes' : 'No'}
                      </td>
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
