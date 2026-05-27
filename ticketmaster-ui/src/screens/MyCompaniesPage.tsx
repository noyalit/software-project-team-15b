import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { http } from '../api/http';
import type { ApiResponse, CompanyDTO } from '../api/types';
import { useAuthStore } from '../ui/authStore';

export default function MyCompaniesPage() {
  const { userType } = useAuthStore();

  const companiesQuery = useQuery({
    queryKey: ['companies', 'me'],
    queryFn: async () => {
      const res = await http.get<ApiResponse<CompanyDTO[]>>('/api/companies/me');
      if (res.data.error) throw new Error(res.data.error);
      return res.data.data ?? [];
    },
    enabled: userType === 'member',
  });

  if (userType !== 'member') {
    return (
      <div className="rounded-2xl border border-white/10 bg-white/5 p-6">
        <h1 className="text-xl font-bold">My Companies</h1>
        <p className="mt-2 text-white/70">Login as a member to see companies you own/founded.</p>
        <Link to="/login" className="mt-4 inline-block rounded-md bg-white px-4 py-2 text-sm font-semibold text-[#0b1220]">
          Login
        </Link>
      </div>
    );
  }

  if (companiesQuery.isPending) return <div className="text-white/70">Loading…</div>;
  if (companiesQuery.isError) return <div className="text-red-300">{(companiesQuery.error as Error).message}</div>;

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-xl font-bold">My Companies</h1>
        <p className="text-sm text-white/70">Powered by `GET /api/companies/me`.</p>
      </div>

      <div className="grid gap-4 md:grid-cols-2">
        {companiesQuery.data.map((c) => (
          <div key={c.id} className="rounded-2xl border border-white/10 bg-white/5 p-6">
            <div className="flex items-start justify-between gap-3">
              <div>
                <div className="text-lg font-semibold">{c.name}</div>
                <div className="mt-1 text-sm text-white/60">status: {c.status}</div>
                <div className="mt-2 text-xs text-white/50">companyId: {c.id}</div>
              </div>
              <Link
                to={`/companies/${c.id}`}
                className="rounded-md bg-white px-3 py-2 text-sm font-semibold text-[#0b1220] hover:bg-white/90"
              >
                Open
              </Link>
            </div>

            <div className="mt-4 flex gap-2">
              <a
                className="rounded-md border border-white/10 px-3 py-2 text-sm text-white/80 hover:bg-white/5"
                href={`/api/companies/${c.id}`}
                target="_blank"
                rel="noreferrer"
              >
                API: company
              </a>
              <a
                className="rounded-md border border-white/10 px-3 py-2 text-sm text-white/80 hover:bg-white/5"
                href={`/api/companies/${c.id}/events`}
                target="_blank"
                rel="noreferrer"
              >
                API: events
              </a>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
