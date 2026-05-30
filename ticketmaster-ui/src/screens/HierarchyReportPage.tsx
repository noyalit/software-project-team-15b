import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { http } from '../api/http';
import type { ApiResponse, CompanyDTO, MemberDTO } from '../api/types';
import { useAuthStore } from '../ui/authStore';

interface RoleTreeNodeDTO {
  memberId: string;
  memberName: string;
  roleName: string;
  appointedBy?: string;
  appointedByName?: string;
  companyId: string;
  eventId?: string;
  eventName?: string;
  permissions?: string[];
  children: RoleTreeNodeDTO[];
}

interface CompanyRoleTreeDTO {
  companyId: string;
  companyName: string;
  root: RoleTreeNodeDTO | null;
}

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

  // Recursive render component function to generate the tree view visually
  const renderTreeNode = (node: RoleTreeNodeDTO) => {
    if (!node) return null;

    return (
      <div key={`${node.memberId}-${node.roleName}`} className="my-3 ml-2 relative">
        {/* Tree structural connector block */}
        <div className="rounded-xl border border-slate-200 bg-slate-50 p-4 shadow-xs max-w-xl transition-all hover:border-slate-300">
          <div className="flex items-center justify-between gap-4">
            <span className="font-bold text-slate-900 text-base">{node.memberName}</span>
            <span className="rounded-md bg-blue-50 px-2.5 py-0.5 text-xs font-semibold text-blue-700 border border-blue-100">
              {node.roleName}
            </span>
          </div>

          <div className="mt-2 space-y-1 text-xs text-slate-600">
            {node.appointedByName && (
              <div>
                <span className="text-slate-400 font-medium">Appointed by:</span> {node.appointedByName}
              </div>
            )}

            {node.eventName && (
              <div>
                <span className="text-slate-400 font-medium">Event context:</span> {node.eventName}
              </div>
            )}
          </div>

          {node.roleName === 'Manager' && (
            <div className="mt-3 pt-3 border-t border-slate-200">
              <div className="text-xs font-semibold text-slate-800">Permissions</div>

              {node.permissions && node.permissions.length > 0 ? (
                <div className="mt-2 flex flex-wrap gap-1.5">
                  {node.permissions.map((permission) => (
                    <span
                      key={permission}
                      className="rounded-full bg-slate-200/80 px-2.5 py-0.5 text-[11px] font-medium text-slate-700"
                    >
                      {formatPermission(permission)}
                    </span>
                  ))}
                </div>
              ) : (
                <div className="mt-1 text-xs text-slate-400 italic">No permissions assigned.</div>
              )}
            </div>
          )}
        </div>

        {/* Children Render Area - Adds nesting padding and an elegant hierarchy line guide */}
        {node.children && node.children.length > 0 && (
          <div className="pl-6 mt-2 ml-4 border-l-2 border-dashed border-slate-200 space-y-2">
            {node.children.map((childNode) => renderTreeNode(childNode))}
          </div>
        )}
      </div>
    );
  };

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-extrabold text-slate-900">Hierarchy Report</h1>
        <p className="mt-1 text-sm text-slate-600">
          View the company roles tree hierarchy and structural permissions dynamically.
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
          {/* Company Selector */}
          <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
            <label className="block">
              <div className="text-sm font-medium text-slate-700">Company</div>

              <select
                value={selectedCompanyId}
                onChange={(e) => setSelectedCompanyId(e.target.value)}
                className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm focus:border-blue-500 focus:outline-hidden"
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

          {/* Hierarchical Tree Render Block */}
          {treeQuery.data && (
            <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
              <h2 className="text-lg font-semibold text-slate-900 border-b border-slate-100 pb-3">
                Roles Tree Structure ({treeQuery.data.companyName})
              </h2>

              <div className="mt-4 overflow-x-auto">
                {treeQuery.data.root ? (
                  <div className="py-2 pl-1">
                    {renderTreeNode(treeQuery.data.root)}
                  </div>
                ) : (
                  <div className="text-sm text-slate-500 italic py-4">
                    No active layout tree found for this selection.
                  </div>
                )}
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}