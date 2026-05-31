import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { AxiosError } from 'axios';
import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getApiErrorMessage } from '../api/errors';
import { http } from '../api/http';
import type {
  ApiResponse,
  CompanyDTO,
  EventDTO,
  ManagerPermission,
  MemberDTO,
} from '../api/types';
import { useAuthStore } from '../ui/authStore';

export default function CompanyPage() {
  const { companyId } = useParams();
  const qc = useQueryClient();
  const { token, userType, clearAuth } = useAuthStore();
  const [ownerUsername, setOwnerUsername] = useState('');
  const [ownerSuccessMessage, setOwnerSuccessMessage] = useState<string | null>(null);
  const [removeOwnerUsername, setRemoveOwnerUsername] = useState('');
  const [removeOwnerSuccessMessage, setRemoveOwnerSuccessMessage] = useState<string | null>(null);
  const [resignSuccessMessage, setResignSuccessMessage] = useState<string | null>(null);
  const [statusSuccessMessage, setStatusSuccessMessage] = useState<string | null>(null);
  const [managerUsername, setManagerUsername] = useState('');
  const [managerEventId, setManagerEventId] = useState('');
  const [managerPermissions, setManagerPermissions] = useState<ManagerPermission[]>([]);
  const [changeManagerUsername, setChangeManagerUsername] = useState('');
  const [changeManagerEventId, setChangeManagerEventId] = useState('');
  const [newManagerPermissions, setNewManagerPermissions] = useState<ManagerPermission[]>([]);
  const [changeManagerSuccessMessage, setChangeManagerSuccessMessage] = useState<string | null>(null);

  const [removeManagerUsername, setRemoveManagerUsername] = useState('');
  const [removeManagerEventId, setRemoveManagerEventId] = useState('');

  const [managerSuccessMessage, setManagerSuccessMessage] = useState<string | null>(null);
  const [removeManagerSuccessMessage, setRemoveManagerSuccessMessage] = useState<string | null>(null);



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

  const meQuery = useQuery({
    queryKey: ['me', token],
    queryFn: async () => {
      try {
        const res = await http.get<ApiResponse<MemberDTO>>('/api/users/me');
        if (res.data.error) throw new Error(res.data.error);
        if (!res.data.data) throw new Error('User not found');
        return res.data.data;
      } catch (e) {
        const err = e as AxiosError<ApiResponse<MemberDTO>>;
        const status = err.response?.status;

        if (status === 401) {
          clearAuth();
          throw new Error('Your session expired. Please log in again.');
        }

        throw new Error(
          getApiErrorMessage<MemberDTO>(e, {
            fallback: 'Failed to load your profile. Please try again.',
            serverFallback: 'Profile is currently unavailable due to a server issue. Please try again later.',
          })
        );
      }
    },
    enabled: Boolean(token) && userType === 'member',
  });

  const appointmentApprovedQuery = useQuery({
    queryKey: ['appointment-approved', token, meQuery.data?.activeRole],
    queryFn: async () => {
      const res = await http.get<ApiResponse<boolean>>('/api/users/roles/approved');

      if (res.data.error) throw new Error(res.data.error);

      return res.data.data ?? false;
    },
    enabled:
      Boolean(token) &&
      userType === 'member' &&
      (meQuery.data?.activeRole === 'Owner' || meQuery.data?.activeRole === 'Manager'),
  });

  const activeRole = meQuery.data?.activeRole;
  const canManageCompany =
    activeRole === 'Founder' ||
    (activeRole === 'Owner' && appointmentApprovedQuery.data === true);

  if (!canManageCompany) {
    return (
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">
          Company
        </h1>
        <p className="mt-2 text-slate-600">
          Your owner appointment must be approved before you can manage this company.
        </p>
      </div>
    );
  }

  const eventsQuery = useQuery({
    queryKey: ['company-events', companyId],
    queryFn: async () => {
      if (!companyId) return [];

      const res = await http.get<ApiResponse<EventDTO[]>>(
        `/api/companies/${companyId}/events`
      );

      if (res.data.error) {
        throw new Error(res.data.error);
      }

      return res.data.data ?? [];
    },
    enabled: Boolean(companyId),
  });

  const appointOwnerMutation = useMutation({
    mutationFn: async () => {
      setOwnerSuccessMessage(null);
      const username = ownerUsername.trim();
      if (!username) {
        throw new Error('Please enter a username.');
      }
      if (!companyId) {
        throw new Error('Company ID is missing.');
      }

      try {
        const resolved = await http.get<ApiResponse<MemberDTO>>('/api/users/members/resolve', {
          params: { username },
        });
        if (resolved.data.error) throw new Error(resolved.data.error);
        const memberId = resolved.data.data?.userId;
        if (!memberId) throw new Error('Member not found');

        const appointed = await http.post<ApiResponse<MemberDTO>>('/api/users/roles/owner', {
          memberId,
          companyId,
        });
        if (appointed.data.error) throw new Error(appointed.data.error);
        return appointed.data.data ?? null;
      } catch (e) {
        const err = e as AxiosError<ApiResponse<MemberDTO>>;
        const status = err.response?.status;

        if (status === 401) {
          clearAuth();
          throw new Error('Your session expired. Please log in again.');
        }

        throw new Error(
          getApiErrorMessage<MemberDTO>(e, {
            fallback: 'Failed to add owner. Please try again.',
            serverFallback: 'Adding an owner is currently unavailable due to a server issue. Please try again later.',
          })
        );
      }
    },
    onSuccess: () => {
      setOwnerUsername('');
      setOwnerSuccessMessage('Owner added.');
    },
  });

  const removeOwnerMutation = useMutation({
    mutationFn: async () => {
      setRemoveOwnerSuccessMessage(null);
      const username = removeOwnerUsername.trim();
      if (!username) {
        throw new Error('Please enter a username.');
      }
      if (!companyId) {
        throw new Error('Company ID is missing.');
      }

      try {
        const resolved = await http.get<ApiResponse<MemberDTO>>('/api/users/members/resolve', {
          params: { username },
        });
        if (resolved.data.error) throw new Error(resolved.data.error);
        const memberToRemoveId = resolved.data.data?.userId;
        if (!memberToRemoveId) throw new Error('Member not found');

        const removed = await http.post<ApiResponse<MemberDTO>>('/api/users/roles/owner/remove', {
          memberToRemoveId,
          companyId,
        });
        if (removed.data.error) throw new Error(removed.data.error);
        return removed.data.data ?? null;
      } catch (e) {
        const err = e as AxiosError<ApiResponse<MemberDTO>>;
        const status = err.response?.status;

        if (status === 401) {
          clearAuth();
          throw new Error('Your session expired. Please log in again.');
        }

        throw new Error(
          getApiErrorMessage<MemberDTO>(e, {
            fallback: 'Failed to remove owner. Please try again.',
            serverFallback: 'Removing an owner is currently unavailable due to a server issue. Please try again later.',
          })
        );
      }
    },
    onSuccess: () => {
      setRemoveOwnerUsername('');
      setRemoveOwnerSuccessMessage('Owner removed.');
    },
  });

  const appointManagerMutation = useMutation({
    mutationFn: async () => {
      setManagerSuccessMessage(null);

      if (!companyId) {
        throw new Error('Company ID is missing.');
      }

      if (!managerEventId) {
        throw new Error('Please select an event.');
      }

      const username = managerUsername.trim();

      if (!username) {
        throw new Error('Please enter a username.');
      }

      const resolved = await http.get<ApiResponse<MemberDTO>>(
        '/api/users/members/resolve',
        {
          params: { username },
        }
      );

      if (resolved.data.error) {
        throw new Error(resolved.data.error);
      }

      const memberId = resolved.data.data?.userId;

      if (!memberId) {
        throw new Error('Member not found.');
      }

      const res = await http.post<ApiResponse<MemberDTO>>(
        '/api/users/roles/manager',
        {
          memberId,
          companyId,
          eventId: managerEventId,
          permissions: managerPermissions,
        }
      );

      if (res.data.error) {
        throw new Error(res.data.error);
      }

      return res.data.data;
    },

    onSuccess: () => {
      setManagerUsername('');
      setManagerPermissions([]);
      setManagerEventId('');
      setManagerSuccessMessage('Manager appointed successfully.');
    },
  });

  const removeManagerMutation = useMutation({
    mutationFn: async () => {
      setRemoveManagerSuccessMessage(null);

      if (!companyId) {
        throw new Error('Company ID is missing.');
      }

      if (!removeManagerEventId) {
        throw new Error('Please select an event.');
      }

      const username = removeManagerUsername.trim();

      if (!username) {
        throw new Error('Please enter a username.');
      }

      const resolved = await http.get<ApiResponse<MemberDTO>>(
        '/api/users/members/resolve',
        {
          params: { username },
        }
      );

      if (resolved.data.error) {
        throw new Error(resolved.data.error);
      }

      const memberToRemoveId = resolved.data.data?.userId;

      if (!memberToRemoveId) {
        throw new Error('Member not found.');
      }

      const res = await http.post<ApiResponse<MemberDTO>>(
        '/api/users/roles/manager/remove',
        {
          memberToRemoveId,
          companyId,
          eventId: removeManagerEventId,
        }
      );

      if (res.data.error) {
        throw new Error(res.data.error);
      }

      return res.data.data;
    },

    onSuccess: () => {
      setRemoveManagerUsername('');
      setRemoveManagerEventId('');
      setRemoveManagerSuccessMessage('Manager removed successfully.');
    },
  });

  const changeManagerPermissionsMutation = useMutation({
    mutationFn: async () => {
      setChangeManagerSuccessMessage(null);

      if (!changeManagerEventId) {
        throw new Error('Please select an event.');
      }

      const username = changeManagerUsername.trim();

      if (!username) {
        throw new Error('Please enter a username.');
      }

      const resolved = await http.get<ApiResponse<MemberDTO>>(
        '/api/users/members/resolve',
        { params: { username } }
      );

      if (resolved.data.error) throw new Error(resolved.data.error);

      const managerId = resolved.data.data?.userId;

      if (!managerId) {
        throw new Error('Manager not found.');
      }

      const res = await http.post<ApiResponse<MemberDTO>>(
        '/api/users/roles/manager/permissions',
        {
          managerId,
          eventId: changeManagerEventId,
          newPermissions: newManagerPermissions,
        }
      );

      if (res.data.error) throw new Error(res.data.error);

      return res.data.data;
    },

    onSuccess: () => {
      setChangeManagerUsername('');
      setChangeManagerEventId('');
      setNewManagerPermissions([]);
      setChangeManagerSuccessMessage('Manager permissions updated successfully.');
    },
  });

  const resignMutation = useMutation({
    mutationFn: async () => {
      setResignSuccessMessage(null);
      if (!companyId) {
        throw new Error('Company ID is missing.');
      }

      try {
        const res = await http.post<ApiResponse<MemberDTO>>(`/api/users/roles/owner/resign/${companyId}`);
        if (res.data.error) throw new Error(res.data.error);
        return res.data.data ?? null;
      } catch (e) {
        const err = e as AxiosError<ApiResponse<MemberDTO>>;
        const status = err.response?.status;

        if (status === 401) {
          clearAuth();
          throw new Error('Your session expired. Please log in again.');
        }

        throw new Error(
          getApiErrorMessage<MemberDTO>(e, {
            fallback: 'Failed to resign from ownership. Please try again.',
            serverFallback: 'Ownership resignation is currently unavailable due to a server issue. Please try again later.',
          })
        );
      }
    },
    onSuccess: () => {
      setResignSuccessMessage('You resigned from ownership.');
    },
  });

  const changeStatusMutation = useMutation({
    mutationFn: async (newStatus: 'ACTIVE' | 'SUSPENDED' | 'CLOSED') => {
      setStatusSuccessMessage(null);
      if (!companyId) {
        throw new Error('Company ID is missing.');
      }

      try {
        const res = await http.patch<ApiResponse<CompanyDTO>>(`/api/companies/${companyId}/status`, {
          status: newStatus,
        });
        if (res.data.error) throw new Error(res.data.error);
        return res.data.data ?? null;
      } catch (e) {
        const err = e as AxiosError<ApiResponse<CompanyDTO>>;
        const status = err.response?.status;

        if (status === 401) {
          clearAuth();
          throw new Error('Your session expired. Please log in again.');
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
      setStatusSuccessMessage('Company status updated.');
      await qc.invalidateQueries({ queryKey: ['company'] });
      await qc.invalidateQueries({ queryKey: ['companies'] });
    },
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

  const me = meQuery.data;
  const company = companyQuery.data;
  const isFounder = Boolean(meQuery.data?.userId) && meQuery.data?.userId === company.founderId;
  const allPermissions: ManagerPermission[] = [
    'MANAGE_EVENTS',
    'CONFIGURE_HALLS_AND_SEATS',
    'UPDATE_EVENT_MAP',
    'DEFINE_PURCHASE_POLICY',
    'DEFINE_DISCOUNT_POLICY',
    'HANDLE_INQUIRIES',
    'VIEW_PURCHASE_AND_ORDER_HISTORY',
    'GENERATE_SALES_REPORTS',
  ];

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

      {isFounder && (
        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <div className="text-slate-900 font-semibold">Founder actions</div>
          <div className="mt-2 text-sm text-slate-600">
            Only the company founder can suspend, close, or reopen the company.
          </div>

          <div className="mt-4 flex flex-wrap gap-2">
            <button
              onClick={() => changeStatusMutation.mutate('CLOSED')}
              disabled={changeStatusMutation.isPending}
              className="rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-sm font-semibold text-rose-900 hover:bg-rose-100 disabled:opacity-60"
            >
              Close
            </button>
            <button
              onClick={() => changeStatusMutation.mutate('ACTIVE')}
              disabled={changeStatusMutation.isPending}
              className="rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
            >
              Reopen
            </button>
          </div>

          {changeStatusMutation.isError && (
            <div className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
              {(changeStatusMutation.error as Error).message}
            </div>
          )}

          {statusSuccessMessage && !changeStatusMutation.isError && (
            <div className="mt-3 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
              {statusSuccessMessage}
            </div>
          )}
        </div>
      )}

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="text-slate-900 font-semibold">Add owner</div>
        <div className="mt-2 text-sm text-slate-600">Enter a member username to appoint them as an owner.</div>

        <div className="mt-4 grid gap-3 md:grid-cols-3">
          <label className="block md:col-span-2">
            <div className="text-sm font-medium text-slate-700">Username</div>
            <input
              value={ownerUsername}
              onChange={(e) => setOwnerUsername(e.target.value)}
              placeholder="e.g. alice"
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm"
            />
          </label>
          <div className="flex items-end">
            <button
              onClick={() => appointOwnerMutation.mutate()}
              disabled={appointOwnerMutation.isPending}
              className="w-full rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
            >
              {appointOwnerMutation.isPending ? 'Adding…' : 'Add owner'}
            </button>
          </div>
        </div>

        {appointOwnerMutation.isError && (
          <div className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {(appointOwnerMutation.error as Error).message}
          </div>
        )}

        {ownerSuccessMessage && !appointOwnerMutation.isError && (
          <div className="mt-3 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
            {ownerSuccessMessage}
          </div>
        )}
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="text-slate-900 font-semibold">Remove owner appointment</div>
        <div className="mt-2 text-sm text-slate-600">
          Enter a member username to remove their owner appointment (only if they were appointed by you).
        </div>

        <div className="mt-4 grid gap-3 md:grid-cols-3">
          <label className="block md:col-span-2">
            <div className="text-sm font-medium text-slate-700">Username</div>
            <input
              value={removeOwnerUsername}
              onChange={(e) => setRemoveOwnerUsername(e.target.value)}
              placeholder="e.g. alice"
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm"
            />
          </label>
          <div className="flex items-end">
            <button
              onClick={() => removeOwnerMutation.mutate()}
              disabled={removeOwnerMutation.isPending}
              className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-900 hover:bg-slate-50 disabled:opacity-60"
            >
              {removeOwnerMutation.isPending ? 'Removing…' : 'Remove owner'}
            </button>
          </div>
        </div>

        {removeOwnerMutation.isError && (
          <div className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {(removeOwnerMutation.error as Error).message}
          </div>
        )}

        {removeOwnerSuccessMessage && !removeOwnerMutation.isError && (
          <div className="mt-3 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
            {removeOwnerSuccessMessage}
          </div>
        )}
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="text-slate-900 font-semibold">Add manager</div>

        <div className="mt-4 grid gap-4">
          <div>
            <div className="text-sm font-medium text-slate-700">Username</div>

            <input
              value={managerUsername}
              onChange={(e) => setManagerUsername(e.target.value)}
              placeholder="e.g. alice"
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
            />
          </div>

          <div>
            <div className="text-sm font-medium text-slate-700">Event</div>

            <select
              value={managerEventId}
              onChange={(e) => setManagerEventId(e.target.value)}
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
            >
              <option value="">Select event...</option>

              {eventsQuery.data?.map((event) => (
                <option key={event.eventId} value={event.eventId}>
                  {event.name}
                </option>
              ))}
            </select>
          </div>

          <div>
            <div className="text-sm font-medium text-slate-700">
              Permissions
            </div>

            <div className="mt-2 grid gap-2 md:grid-cols-2">
              {allPermissions.map((permission) => (
                <label
                  key={permission}
                  className="flex items-center gap-2 text-sm"
                >
                  <input
                    type="checkbox"
                    checked={managerPermissions.includes(permission)}
                    onChange={(e) => {
                      if (e.target.checked) {
                        setManagerPermissions((prev) => [...prev, permission]);
                      } else {
                        setManagerPermissions((prev) =>
                          prev.filter((p) => p !== permission)
                        );
                      }
                    }}
                  />
                  {permission
                    .toLowerCase()
                    .replaceAll('_', ' ')
                    .replace(/\b\w/g, (c) => c.toUpperCase())}
                </label>
              ))}
            </div>
          </div>

          <button
            onClick={() => appointManagerMutation.mutate()}
            disabled={appointManagerMutation.isPending}
            className="rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800"
          >
            Add manager
          </button>

          {appointManagerMutation.isError && (
            <div className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
              {(appointManagerMutation.error as Error).message}
            </div>
          )}

          {managerSuccessMessage && (
            <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
              {managerSuccessMessage}
            </div>
          )}
        </div>
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="text-slate-900 font-semibold">Change manager permissions</div>

        <div className="mt-4 grid gap-4">
          <div>
            <div className="text-sm font-medium text-slate-700">Username</div>

            <input
              value={changeManagerUsername}
              onChange={(e) => setChangeManagerUsername(e.target.value)}
              placeholder="e.g. alice"
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
            />
          </div>

          <div>
            <div className="text-sm font-medium text-slate-700">Event</div>

            <select
              value={changeManagerEventId}
              onChange={(e) => setChangeManagerEventId(e.target.value)}
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
            >
              <option value="">Select event...</option>

              {eventsQuery.data?.map((event) => (
                <option key={event.eventId} value={event.eventId}>
                  {event.name}
                </option>
              ))}
            </select>
          </div>

          <div>
            <div className="text-sm font-medium text-slate-700">New permissions</div>

            <div className="mt-2 grid gap-2 md:grid-cols-2">
              {allPermissions.map((permission) => (
                <label key={permission} className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={newManagerPermissions.includes(permission)}
                    onChange={(e) => {
                      if (e.target.checked) {
                        setNewManagerPermissions((prev) => [...prev, permission]);
                      } else {
                        setNewManagerPermissions((prev) =>
                          prev.filter((p) => p !== permission)
                        );
                      }
                    }}
                  />

                  {permission
                    .toLowerCase()
                    .replaceAll('_', ' ')
                    .replace(/\b\w/g, (c) => c.toUpperCase())}
                </label>
              ))}
            </div>
          </div>

          <button
            onClick={() => changeManagerPermissionsMutation.mutate()}
            disabled={changeManagerPermissionsMutation.isPending}
            className="rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800"
          >
            {changeManagerPermissionsMutation.isPending
              ? 'Updating...'
              : 'Change permissions'}
          </button>

          {changeManagerPermissionsMutation.isError && (
            <div className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
              {(changeManagerPermissionsMutation.error as Error).message}
            </div>
          )}

          {changeManagerSuccessMessage && (
            <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
              {changeManagerSuccessMessage}
            </div>
          )}
        </div>
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="text-slate-900 font-semibold">
          Remove manager appointment
        </div>

        <div className="mt-4 grid gap-4">
          <div>
            <div className="text-sm font-medium text-slate-700">Username</div>

            <input
              value={removeManagerUsername}
              onChange={(e) => setRemoveManagerUsername(e.target.value)}
              placeholder="e.g. alice"
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
            />
          </div>

          <div>
            <div className="text-sm font-medium text-slate-700">Event</div>

            <select
              value={removeManagerEventId}
              onChange={(e) => setRemoveManagerEventId(e.target.value)}
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
            >
              <option value="">Select event...</option>

              {eventsQuery.data?.map((event) => (
                <option key={event.eventId} value={event.eventId}>
                  {event.name}
                </option>
              ))}
            </select>
          </div>

          <button
            onClick={() => removeManagerMutation.mutate()}
            disabled={removeManagerMutation.isPending}
            className="rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-sm font-semibold text-rose-900 hover:bg-rose-100"
          >
            Remove manager
          </button>

          {removeManagerMutation.isError && (
            <div className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
              {(removeManagerMutation.error as Error).message}
            </div>
          )}

          {removeManagerSuccessMessage && (
            <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
              {removeManagerSuccessMessage}
            </div>
          )}
        </div>
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="text-slate-900 font-semibold">Resign ownership</div>
        <div className="mt-2 text-sm text-slate-600">If you are an owner (not a founder), you can resign from this company.</div>

        <div className="mt-4">
          <button
            onClick={() => resignMutation.mutate()}
            disabled={resignMutation.isPending}
            className="rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-900 hover:bg-slate-50 disabled:opacity-60"
          >
            {resignMutation.isPending ? 'Resigning…' : 'Resign'}
          </button>
        </div>

        {resignMutation.isError && (
          <div className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {(resignMutation.error as Error).message}
          </div>
        )}

        {resignSuccessMessage && !resignMutation.isError && (
          <div className="mt-3 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
            {resignSuccessMessage}
          </div>
        )}
      </div>
    </div>
  );
}
