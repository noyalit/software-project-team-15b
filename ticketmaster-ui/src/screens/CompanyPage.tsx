import { useQuery } from '@tanstack/react-query';
import type { AxiosError } from 'axios';
import { Link, useParams } from 'react-router-dom';
import { getApiErrorMessage } from '../api/errors';
import { http } from '../api/http';
import type { ApiResponse, CompanyDTO } from '../api/types';
import { useAuthStore } from '../ui/authStore';

export default function CompanyPage() {
  const { companyId } = useParams();
  const { token, userType, clearAuth } = useAuthStore();

  const companyQuery = useQuery({
    queryKey: ['company', companyId, token],
    queryFn: async () => {
      if (!companyId) throw new Error('Company ID is missing.');
      try {
        const res = await http.get<ApiResponse<CompanyDTO>>(`/api/companies/${companyId}`);
        if (res.data.error) throw new Error(res.data.error);
        if (!res.data.data) throw new Error('Company not found');
        return res.data.data;
      } catch (e) {
        const err = e as AxiosError<ApiResponse<CompanyDTO>>;
        const status = err.response?.status;

        if (status === 401) {
          clearAuth();
          throw new Error('Your session expired. Please log in again.');
        }

        throw new Error(
          getApiErrorMessage<CompanyDTO>(e, {
            fallback: 'Failed to load company. Please try again.',
            serverFallback: 'Company is currently unavailable due to a server issue. Please try again later.',
          })
        );
      }
    },
    enabled: Boolean(companyId) && Boolean(token) && userType === 'member',
  });

  if (userType !== 'member') {
    return (
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">Company</h1>
        <p className="mt-2 text-slate-600">Log in as a user to view companies.</p>
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
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">Company</h1>
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

  if (companyQuery.isPending) return <div className="text-slate-600">Loading…</div>;

  if (companyQuery.isError) {
    return (
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">Company</h1>
        <div className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
          {(companyQuery.error as Error).message}
        </div>
        <div className="mt-4">
          <Link
            to="/companies/me"
            className="inline-flex items-center rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800"
          >
            Back to My Companies
          </Link>
        </div>
      </div>
    );
  }

  const company = companyQuery.data;

  return (
    <div className="space-y-4">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">{company.name}</h1>
          <p className="mt-1 text-sm text-slate-600">Status: {company.status}</p>
        </div>
        <Link
          to="/companies/me"
          className="inline-flex items-center justify-center rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-900 hover:bg-slate-50"
        >
          Back
        </Link>
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="text-slate-900 font-semibold">Company details</div>
        <div className="mt-2 text-sm text-slate-700">
          <div>
            <span className="font-medium">ID:</span> <span className="font-mono text-xs">{company.companyId}</span>
          </div>
        </div>
      </div>
    </div>
  );
}
