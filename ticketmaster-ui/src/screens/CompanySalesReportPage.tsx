import { useMutation, useQuery } from '@tanstack/react-query';
import type { AxiosError } from 'axios';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { getApiErrorMessage } from '../api/errors';
import { http } from '../api/http';
import type { ApiResponse, CompanyDTO } from '../api/types';
import { useAuthStore } from '../ui/authStore';

type CompaniesResponse = ApiResponse<CompanyDTO[]>;
type SalesReportResponse = ApiResponse<Record<string, unknown>>;

function formatMoneyLike(value: unknown): string {
  if (typeof value === 'string') return value;
  if (typeof value === 'number') return String(value);
  if (!value || typeof value !== 'object') return '—';

  const v = value as {
    amount?: unknown;
    currency?: unknown;
    amountValue?: unknown;
    currencyCode?: unknown;
  };

  const amount =
    v.amountValue ??
    v.amount ??
    (typeof v.amount === 'object' && v.amount !== null
      ? (v.amount as { value?: unknown }).value
      : undefined);
  const currency = v.currencyCode ?? v.currency;

  if (typeof currency !== 'string' || !currency) return '—';
  if (typeof amount === 'string') return `${amount} ${currency}`;
  if (typeof amount === 'number') return `${amount} ${currency}`;
  return '—';
}

export default function CompanySalesReportPage() {
  const { token, userType, clearAuth } = useAuthStore();

  const [selectedCompanyId, setSelectedCompanyId] = useState('');
  const [report, setReport] = useState<Record<string, unknown> | null>(null);

  const companiesQuery = useQuery({
    queryKey: ['company-sales-report', 'companies', token],
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

  const runReport = useMutation({
    mutationFn: async (): Promise<Record<string, unknown>> => {
      if (!selectedCompanyId) return {};
      try {
        const res = await http.get<SalesReportResponse>(
          `/api/order-history/company/${selectedCompanyId}/sales-report`
        );
        if (res.data.error) throw new Error(res.data.error);
        return res.data.data ?? {};
      } catch (e) {
        const err = e as AxiosError<SalesReportResponse>;
        const status = err.response?.status;

        if (status === 401) {
          clearAuth();
          throw new Error('Your session expired. Please log in again.');
        }
        if (status === 403) {
          throw new Error('You are not authorized to generate sales reports for this company.');
        }

        throw new Error(
          getApiErrorMessage<Record<string, unknown>>(e, {
            fallback: 'Failed to load sales report. Please try again.',
            serverFallback:
              'Sales report is currently unavailable due to a server issue. Please try again later.',
          })
        );
      }
    },
    onSuccess: (data) => {
      setReport(data);
    },
  });

  if (userType !== 'member') {
    return (
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">
          Sales Report
        </h1>
        <p className="mt-2 text-slate-600">Log in as a member to view sales reports.</p>
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
          Sales Report
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

  const selectedCompany = companiesQuery.data?.find(
    (c) => c.companyId === selectedCompanyId
  );

  const ticketsSold = typeof report?.ticketsSold === 'number' ? report.ticketsSold : null;
  const totalRevenue = formatMoneyLike(report?.totalRevenue);
  const orders = Array.isArray(report?.orders) ? (report?.orders as unknown[]) : [];

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">
          Sales Report
        </h1>
        <p className="mt-1 text-sm text-slate-600">
          View ticket sales and revenue for your company events.
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
                setReport(null);
              }}
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

          <div className="flex items-end">
            <button
              onClick={() => runReport.mutate()}
              disabled={runReport.isPending || !selectedCompanyId}
              className="w-full rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
            >
              {runReport.isPending ? 'Loading…' : 'Generate report'}
            </button>
          </div>
        </div>

        {runReport.isError && (
          <div className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {(runReport.error as Error).message}
          </div>
        )}
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex items-center justify-between gap-3">
          <div className="text-lg font-semibold text-slate-900">Summary</div>
        </div>

        {selectedCompany && (
          <div className="mt-1 text-sm text-slate-600">
            Company: {selectedCompany.name}
          </div>
        )}

        {!report && (
          <div className="mt-4 text-slate-600">
            Select a company and generate a report.
          </div>
        )}

        {report && (
          <div className="mt-4 grid gap-3 md:grid-cols-3">
            <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
              <div className="text-sm text-slate-600">Tickets sold</div>
              <div className="mt-1 text-xl font-semibold text-slate-900">
                {ticketsSold ?? '—'}
              </div>
            </div>

            <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
              <div className="text-sm text-slate-600">Total revenue</div>
              <div className="mt-1 text-xl font-semibold text-slate-900">
                {totalRevenue}
              </div>
            </div>

            <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
              <div className="text-sm text-slate-600">Orders</div>
              <div className="mt-1 text-xl font-semibold text-slate-900">
                {orders.length}
              </div>
            </div>
          </div>
        )}
      </div>

      {report && orders.length > 0 && (
        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <div className="flex items-center justify-between gap-3">
            <div className="text-lg font-semibold text-slate-900">Orders</div>
            <div className="text-sm text-slate-600">{orders.length} orders</div>
          </div>

          <div className="mt-4 overflow-hidden rounded-xl border border-slate-200">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-50 text-slate-700">
                <tr>
                  <th className="px-4 py-3 font-semibold">Order ID</th>
                  <th className="px-4 py-3 font-semibold">User ID</th>
                  <th className="px-4 py-3 font-semibold">Event ID</th>
                  <th className="px-4 py-3 font-semibold">Tickets</th>
                  <th className="px-4 py-3 font-semibold">Cancelled</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-200">
                {orders.map((o) => {
                  const row = o as {
                    orderId?: unknown;
                    userId?: unknown;
                    eventId?: unknown;
                    tickets?: unknown;
                    cancelled?: unknown;
                  };
                  const ticketCount = Array.isArray(row.tickets) ? row.tickets.length : 0;
                  return (
                    <tr
                      key={String(row.orderId ?? '')}
                      className="bg-white"
                    >
                      <td className="px-4 py-3 font-mono text-xs text-slate-800">
                        {String(row.orderId ?? '—')}
                      </td>
                      <td className="px-4 py-3 font-mono text-xs text-slate-800">
                        {String(row.userId ?? '—')}
                      </td>
                      <td className="px-4 py-3 font-mono text-xs text-slate-800">
                        {String(row.eventId ?? '—')}
                      </td>
                      <td className="px-4 py-3 text-slate-800">{ticketCount}</td>
                      <td className="px-4 py-3 text-slate-800">
                        {row.cancelled ? 'Yes' : 'No'}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
