import { useQuery } from '@tanstack/react-query';
import type { AxiosError } from 'axios';
import { Link } from 'react-router-dom';
import { http } from '../api/http';
import { getApiErrorMessage } from '../api/errors';
import type { ApiResponse, CompanyDTO, MemberDTO } from '../api/types';
import { useAuthStore } from '../ui/authStore';

export default function MyCompaniesPage() {
  const { token, userType, clearAuth } = useAuthStore();

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
    queryKey: ['companies', 'me', token],
    queryFn: async () => {
      try {
        const res = await http.get<ApiResponse<CompanyDTO[]>>('/api/companies/me');

        if (res.data.error) throw new Error(res.data.error);

        return res.data.data ?? [];
      } catch (e) {
        const err = e as AxiosError<ApiResponse<CompanyDTO[]>>;
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

  if (userType !== 'member') {
    return (
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">
          My Companies
        </h1>

        <p className="mt-2 text-slate-600">
          Log in as a user to create or manage companies.
        </p>

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
          My Companies
        </h1>

        <p className="mt-2 text-slate-600">
          Please log in to see your companies.
        </p>

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

  if (meQuery.isPending || companiesQuery.isPending) {
    return <div className="text-slate-600">Loading…</div>;
  }

  if (meQuery.isError) {
    return (
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">
          My Companies
        </h1>

        <div className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
          {(meQuery.error as Error).message}
        </div>
      </div>
    );
  }

  if (companiesQuery.isError) {
    return (
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">
          My Companies
        </h1>

        <div className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
          {(companiesQuery.error as Error).message}
        </div>
      </div>
    );
  }

  const activeRole = meQuery.data.activeRole ?? 'RegularMember';
  const canManageCompanies = activeRole === 'Founder' || activeRole === 'Owner' || activeRole === 'CompanyManager';

  return (
    <div className="space-y-4">
      <div>
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div>
            <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">
              My Companies
            </h1>

            <p className="mt-1 text-sm text-slate-600">
              {canManageCompanies
                ? 'Companies you own or founded.'
                : 'Create a company to become a founder.'}
            </p>
          </div>

          <Link
            to="/companies/new"
            className="inline-flex items-center justify-center rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800"
          >
            Create company
          </Link>
        </div>
      </div>

      {!canManageCompanies ? (
        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <div className="text-slate-900 font-semibold">
            Company management is available only for active Founders, Owners, or Company Managers.
          </div>

          <div className="mt-1 text-sm text-slate-600">
            You can still create a new company. After creating it, switch your active role to Founder to manage it.
          </div>
        </div>
      ) : companiesQuery.data.length === 0 ? (
        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <div className="text-slate-900 font-semibold">No companies yet</div>

          <div className="mt-1 text-sm text-slate-600">
            When you create or join a company, it will appear here.
          </div>
        </div>
      ) : (
        <div className="grid gap-4 md:grid-cols-2">
          {companiesQuery.data.map((c) => (
            <div
              key={c.companyId}
              className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm"
            >
              <div className="flex items-start justify-between gap-3">
                <div>
                  <div className="text-lg font-semibold text-slate-900">
                    {c.name}
                  </div>

                  <div className="mt-1 text-sm text-slate-600">
                    Status: {c.status}
                  </div>
                </div>

                <Link
                  to={`/companies/${c.companyId}`}
                  className="rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800"
                >
                  Open
                </Link>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}