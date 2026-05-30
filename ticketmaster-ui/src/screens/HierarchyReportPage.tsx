import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { http } from '../api/http';
import type { ApiResponse, CompanyDTO, CompanyRoleTreeDTO, MemberDTO } from '../api/types';
import { useAuthStore } from '../ui/authStore';

function formatPermission(permission: string) {
  return permission
    .toLowerCase()
    .replaceAll('_', ' ')
    .replace(/\b\w/g, (c) => c.toUpperCase());
}

export default function HierarchyReportPage() {
  const { token, userType } = useAuthStore();
  const [selectedCompanyId, setSelectedCompanyId] = useState('');

  const meQuery = useQuery({
    queryKey: ['me', token],
    queryFn: async () => {
      const res = await http.get<ApiResponse<MemberDTO>>('/api/users/me');
      if (res.data.error) throw new Error(res.data.error);
      if (!res.data.data) throw new Error('Profile not found');
      return res.data.data;
    },
    enabled: Boolean(token) && userType === 'member',
  });

  const companiesQuery = useQuery({
    queryKey: ['companies', 'me', token],
    queryFn: async () => {
        const res = await http.get<ApiResponse<CompanyDTO[]>>('/api/companies/me');

        if (res.data.error) throw new Error(res.data.error);

        return res.data.data ?? [];
    },
    enabled:
        Boolean(token) &&
        userType === 'member' &&
        (meQuery.data?.activeRole === 'Founder' || meQuery.data?.activeRole === 'Owner'),
    });

  const treeQuery = useQuery({
    queryKey: ['company-role-tree', selectedCompanyId],
    queryFn: async () => {
      const res = await http.get<ApiResponse<CompanyRoleTreeDTO>>(
        `/api/users/companies/${selectedCompanyId}/roles/tree`
      );

      if (res.data.error) throw new Error(res.data.error);
      if (!res.data.data) throw new Error('No hierarchy report found');

      return res.data.data;
    },
    enabled: Boolean(selectedCompanyId),
  });

  if (userType !== 'member') {
    return (
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-extrabold text-slate-900">Hierarchy Report</h1>
        <p className="mt-2 text-slate-600">Log in as a member to view hierarchy reports.</p>
      </div>
    );
  }

  const activeRole = meQuery.data?.activeRole;
  const canView = activeRole === 'Founder' || activeRole === 'Owner';

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-extrabold text-slate-900">Hierarchy Report</h1>
        <p className="mt-1 text-sm text-slate-600">
          View the company roles tree and manager permissions.
        </p>
      </div>

      {!canView ? (
        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <div className="font-semibold text-slate-900">
            This page is available only for active Founders or Owners.
          </div>
          <div className="mt-1 text-sm text-slate-600">
            Switch your active role to Founder or Owner from the Profile page.
          </div>
        </div>
      ) : (
        <>
          <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
            <label className="block">
              <div className="text-sm font-medium text-slate-700">Company</div>

              <select
                value={selectedCompanyId}
                onChange={(e) => setSelectedCompanyId(e.target.value)}
                className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
              >
                <option value="">Select company...</option>

                {companiesQuery.data?.map((company) => (
                  <option key={company.companyId} value={company.companyId}>
                    {company.name}
                  </option>
                ))}
              </select>
            </label>
          </div>

          {treeQuery.isError && (
            <div className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
              {(treeQuery.error as Error).message}
            </div>
          )}

          {treeQuery.data && (
            <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
              <h2 className="text-lg font-semibold text-slate-900">Roles tree</h2>

              <div className="mt-4 space-y-3">
                {treeQuery.data.roles.map((role) => (
                  <div
                    key={`${role.memberId}-${role.roleName}-${role.eventId ?? role.companyId}`}
                    className="rounded-xl border border-slate-200 bg-slate-50 p-4"
                  >
                    <div className="font-semibold text-slate-900">{role.roleName}</div>

                    <div className="mt-1 text-sm text-slate-600">
                      Member ID: {role.memberId}
                    </div>

                    {role.appointedBy && (
                      <div className="text-sm text-slate-600">
                        Appointed by: {role.appointedBy}
                      </div>
                    )}

                    {role.eventId && (
                      <div className="text-sm text-slate-600">
                        Event ID: {role.eventId}
                      </div>
                    )}

                    {role.roleName === 'Manager' && (
                      <div className="mt-3">
                        <div className="text-sm font-semibold text-slate-800">
                          Permissions
                        </div>

                        {role.permissions?.length ? (
                          <div className="mt-2 flex flex-wrap gap-2">
                            {role.permissions.map((permission) => (
                              <span
                                key={permission}
                                className="rounded-full bg-slate-200 px-3 py-1 text-xs font-medium text-slate-700"
                              >
                                {formatPermission(permission)}
                              </span>
                            ))}
                          </div>
                        ) : (
                          <div className="mt-1 text-sm text-slate-500">
                            No permissions.
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}