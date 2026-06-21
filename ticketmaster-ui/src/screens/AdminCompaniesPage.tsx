import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { AxiosError } from 'axios';
import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { http } from '../api/http';
import { getApiErrorMessage } from '../api/errors';
import type { ApiResponse, CompanyDTO } from '../api/types';
import { useAuthStore } from '../ui/authStore';

type CompaniesResponse = ApiResponse<CompanyDTO[]>;
type ChangeStatusResponse = ApiResponse<CompanyDTO>;

export default function AdminCompaniesPage() {
  const qc = useQueryClient();
  const { token, userType, clearAuth } = useAuthStore();
  const [selectedCompanyId, setSelectedCompanyId] = useState('');

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
            serverFallback:
              'Companies are currently unavailable due to a server issue. Please try again later.',
          })
        );
      }
    },
    enabled: userType === 'system-admin' && Boolean(token),
  });

  const selectedCompany = useMemo(
    () => companiesQuery.data?.find((c) => c.companyId === selectedCompanyId) ?? null,
    [companiesQuery.data, selectedCompanyId]
  );

  const changeStatusMutation = useMutation({
    mutationFn: async () => {
      if (!selectedCompanyId.trim()) throw new Error('Please select a company.');

      try {
        const res = await http.patch<ChangeStatusResponse>(
          `/api/companies/${selectedCompanyId.trim()}/suspend`
        );

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
            serverFallback:
              'Company updates are currently unavailable due to a server issue. Please try again later.',
          })
        );
      }
    },
    onSuccess: async () => {
      setSelectedCompanyId('');
      await qc.invalidateQueries({ queryKey: ['admin', 'companies'] });
    },
  });

  const statusClass = (status: string) => {
    if (status === 'ACTIVE') {
      return 'bg-emerald-50 text-emerald-700 border-emerald-200';
    }
    if (status === 'SUSPENDED') {
      return 'bg-rose-50 text-rose-700 border-rose-200';
    }
    if (status === 'CLOSED') {
      return 'bg-slate-100 text-slate-700 border-slate-200';
    }
    return 'bg-slate-50 text-slate-700 border-slate-200';
  };

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
    <div className="space-y-5">
      <div>
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">Companies</h1>
        <p className="mt-1 text-sm text-slate-600">
          View companies and suspend a company when needed.
        </p>
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex flex-col gap-1">
          <h2 className="text-lg font-semibold text-slate-900">Suspend company</h2>
          <p className="text-sm text-slate-600">
            Select a company from the list and suspend its activity.
          </p>
        </div>

        <div className="mt-5 grid gap-3 md:grid-cols-[1fr_auto] md:items-end">
          <label className="block">
            <div className="text-sm font-medium text-slate-700">Company</div>

            <select
              value={selectedCompanyId}
              onChange={(e) => setSelectedCompanyId(e.target.value)}
              disabled={companiesQuery.isPending || companiesQuery.isError}
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm disabled:opacity-60"
            >
              <option value="">Select a company...</option>
              {companiesQuery.data?.map((company) => (
                <option key={company.companyId} value={company.companyId}>
                  {company.name} — {company.status}
                </option>
              ))}
            </select>
          </label>

          <button
            onClick={() => changeStatusMutation.mutate()}
            disabled={
              changeStatusMutation.isPending ||
              !selectedCompanyId.trim() ||
              selectedCompany?.status === 'SUSPENDED'
            }
            className="rounded-md bg-rose-600 px-6 py-2 text-sm font-semibold text-white hover:bg-rose-700 disabled:opacity-60"
          >
            {changeStatusMutation.isPending ? 'Suspending...' : 'Suspend company'}
          </button>
        </div>

        {selectedCompany && (
          <div className="mt-4 rounded-xl border border-slate-200 bg-slate-50 p-4">
            <div className="text-sm text-slate-600">Selected company</div>
            <div className="mt-1 flex flex-wrap items-center gap-2">
              <span className="font-semibold text-slate-900">{selectedCompany.name}</span>
              <span
                className={`rounded-full border px-3 py-1 text-xs font-semibold ${statusClass(
                  selectedCompany.status
                )}`}
              >
                {selectedCompany.status}
              </span>
            </div>
          </div>
        )}

        {changeStatusMutation.isError && (
          <div className="mt-4 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {(changeStatusMutation.error as Error).message}
          </div>
        )}

        {changeStatusMutation.isSuccess && !changeStatusMutation.isError && (
          <div className="mt-4 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
            Company suspended successfully.
          </div>
        )}
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 className="text-lg font-semibold text-slate-900">All companies</h2>
            <p className="mt-1 text-sm text-slate-600">
              {companiesQuery.data?.length ?? 0} companies found.
            </p>
          </div>

          <button
            onClick={() => companiesQuery.refetch()}
            className="rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-800 hover:bg-slate-50"
          >
            Refresh
          </button>
        </div>

        {companiesQuery.isPending && <div className="mt-4 text-slate-600">Loading...</div>}

        {companiesQuery.isError && (
          <div className="mt-4 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {(companiesQuery.error as Error).message}
          </div>
        )}

        {!companiesQuery.isPending && !companiesQuery.isError && companiesQuery.data.length === 0 && (
          <div className="mt-4 rounded-xl border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600">
            No companies found.
          </div>
        )}

        {!companiesQuery.isPending && !companiesQuery.isError && companiesQuery.data.length > 0 && (
          <div className="mt-4 grid gap-3 md:grid-cols-2">
            {companiesQuery.data.map((company) => (
              <div
                key={company.companyId}
                className="rounded-xl border border-slate-200 bg-slate-50 p-4"
              >
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <div className="font-semibold text-slate-900">{company.name}</div>
                    <div className="mt-1 text-sm text-slate-600">
                      Company status overview
                    </div>
                  </div>

                  <span
                    className={`rounded-full border px-3 py-1 text-xs font-semibold ${statusClass(
                      company.status
                    )}`}
                  >
                    {company.status}
                  </span>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}