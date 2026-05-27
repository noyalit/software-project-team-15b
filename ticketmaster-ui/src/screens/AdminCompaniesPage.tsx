import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { AxiosError } from 'axios';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { http } from '../api/http';
import { getApiErrorMessage } from '../api/errors';
import type { ApiResponse, CompanyDTO, CompanyStatus } from '../api/types';
import { useAuthStore } from '../ui/authStore';

type CompaniesResponse = ApiResponse<CompanyDTO[]>;

type ChangeStatusResponse = ApiResponse<CompanyDTO>;

export default function AdminCompaniesPage() {
  const qc = useQueryClient();
  const { token, userType, clearAuth } = useAuthStore();

  const [selectedCompanyId, setSelectedCompanyId] = useState('');
  const [status, setStatus] = useState<CompanyStatus>('ACTIVE');

  const companiesQuery = useQuery({
    queryKey: ['admin', 'companies', token],
    queryFn: async () => {
      try {
        const res = await http.get<CompaniesResponse>('/api/companies');
        if (res.data.error) throw new Error(res.data.error);
        return res.data.data ?? [];
      } catch (e) {
        const err = e as AxiosError<CompaniesResponse>;
        const statusCode = err.response?.status;

        if (statusCode === 401) {
          clearAuth();
          throw new Error('Your session expired. Please log in again.');
        }
        if (statusCode === 403) {
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

  const changeStatusMutation = useMutation({
    mutationFn: async () => {
      if (!selectedCompanyId.trim()) throw new Error('Company is required.');
      if (!status.trim()) throw new Error('Status is required.');

      try {
        const res = await http.patch<ChangeStatusResponse>(`/api/companies/${selectedCompanyId.trim()}/status`, { status });
        if (res.data.error) throw new Error(res.data.error);
        if (!res.data.data) throw new Error('No company returned');
        return res.data.data;
      } catch (e) {
        const err = e as AxiosError<ChangeStatusResponse>;
        const statusCode = err.response?.status;

        if (statusCode === 401) {
          clearAuth();
          throw new Error('Your session expired. Please log in again.');
        }
        if (statusCode === 403) {
          throw new Error('You are not authorized to change company status.');
        }

        throw new Error(
          getApiErrorMessage<CompanyDTO>(e, {
            fallback: 'Failed to update company status. Please try again.',
            serverFallback: 'Company updates are currently unavailable due to a server issue. Please try again later.',
          })
        );
      }
    },
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ['admin', 'companies'] });
    },
  });

  if (userType !== 'system-admin') {
    return (
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">Admin</h1>
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
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">Admin</h1>
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

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">Companies</h1>
        <p className="mt-1 text-sm text-slate-600">View all companies and change company status.</p>
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="text-lg font-semibold text-slate-900">Change status</div>
        <div className="mt-4 grid gap-3 md:grid-cols-3">
          <label className="block md:col-span-2">
            <div className="text-sm font-medium text-slate-700">Company</div>
            <select
              value={selectedCompanyId}
              onChange={(e) => {
                const nextId = e.target.value;
                setSelectedCompanyId(nextId);
                const selected = companiesQuery.data?.find((c) => c.id === nextId);
                if (selected) {
                  setStatus(selected.status);
                }
              }}
              disabled={companiesQuery.isPending || companiesQuery.isError}
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm disabled:opacity-60"
            >
              <option value="">Select a company…</option>
              {companiesQuery.data?.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </label>
          <label className="block">
            <div className="text-sm font-medium text-slate-700">Status</div>
            <select
              value={status}
              onChange={(e) => setStatus(e.target.value as CompanyStatus)}
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm"
            >
              <option value="ACTIVE">ACTIVE</option>
              <option value="SUSPENDED">SUSPENDED</option>
              <option value="CLOSED">CLOSED</option>
            </select>
          </label>
          <div className="md:col-span-3">
            <button
              onClick={() => changeStatusMutation.mutate()}
              disabled={changeStatusMutation.isPending}
              className="w-full rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
            >
              {changeStatusMutation.isPending ? 'Saving…' : 'Save'}
            </button>
          </div>
        </div>

        {changeStatusMutation.isError && (
          <div className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {(changeStatusMutation.error as Error).message}
          </div>
        )}
        {changeStatusMutation.isSuccess && !changeStatusMutation.isError && (
          <div className="mt-3 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
            Company updated.
          </div>
        )}
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex items-center justify-between gap-3">
          <div className="text-lg font-semibold text-slate-900">All companies</div>
          <button
            onClick={() => companiesQuery.refetch()}
            className="rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-800 hover:bg-slate-50"
          >
            Refresh
          </button>
        </div>

        {companiesQuery.isPending && <div className="mt-4 text-slate-600">Loading…</div>}
        {companiesQuery.isError && (
          <div className="mt-4 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {(companiesQuery.error as Error).message}
          </div>
        )}

        {!companiesQuery.isPending && !companiesQuery.isError && companiesQuery.data.length === 0 && (
          <div className="mt-4 text-slate-600">No companies found.</div>
        )}

        {!companiesQuery.isPending && !companiesQuery.isError && companiesQuery.data.length > 0 && (
          <div className="mt-4 overflow-hidden rounded-xl border border-slate-200">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-50 text-slate-700">
                <tr>
                  <th className="px-4 py-3 font-semibold">Name</th>
                  <th className="px-4 py-3 font-semibold">Status</th>
                  <th className="px-4 py-3 font-semibold">Company ID</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-200">
                {companiesQuery.data.map((c) => (
                  <tr key={c.id} className="bg-white">
                    <td className="px-4 py-3 font-medium text-slate-900">{c.name}</td>
                    <td className="px-4 py-3 text-slate-700">{c.status}</td>
                    <td className="px-4 py-3 font-mono text-xs text-slate-700">{c.id}</td>
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
