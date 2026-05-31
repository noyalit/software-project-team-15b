import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { AxiosError } from 'axios';
import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { http } from '../api/http';
import { getApiErrorMessage } from '../api/errors';
import type { ApiResponse, MemberDTO, CompanyDTO, EventDTO } from '../api/types';
import { useAuthStore } from '../ui/authStore';

export default function ProfilePage() {
  const qc = useQueryClient();
  const { token, userType, clearAuth, setUsername } = useAuthStore();

  const [newUsername, setNewUsername] = useState('');
  const [newBirthDate, setNewBirthDate] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [appointmentApproved, setAppointmentApproved] = useState(false);
  const [approvedAppointmentTarget, setApprovedAppointmentTarget] = useState<string | null>(null);

  const meQuery = useQuery({
    queryKey: ['me', token],
    queryFn: async () => {
      try {
        const res = await http.get<ApiResponse<MemberDTO>>('/api/users/me');
        if (res.data.error) throw new Error(res.data.error);
        if (!res.data.data) throw new Error('No profile data');
        return res.data.data;
      } catch (e) {
        const err = e as AxiosError<ApiResponse<MemberDTO>>;
        if (err.response?.status === 401) {
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
    enabled: userType === 'member' && Boolean(token),
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

  const companiesQuery = useQuery({
    queryKey: ['profile', 'companies', token],
    queryFn: async () => {
      const res = await http.get<ApiResponse<CompanyDTO[]>>('/api/companies/me');
      if (res.data.error) throw new Error(res.data.error);
      return res.data.data ?? [];
    },
    enabled: Boolean(token) && userType === 'member',
  });

  const companyEventsQuery = useQuery({
    queryKey: ['profile', 'company-events', token, companiesQuery.data?.map((c) => c.companyId).join(',')],
    queryFn: async () => {
      const companies = companiesQuery.data ?? [];

      const result = await Promise.all(
        companies.map(async (company) => {
          const res = await http.get<ApiResponse<EventDTO[]>>(`/api/companies/${company.companyId}/events`);
          if (res.data.error) throw new Error(res.data.error);
          return {
            company,
            events: res.data.data ?? [],
          };
        })
      );

      return result;
    },
    enabled: Boolean(token) && userType === 'member' && Boolean(companiesQuery.data?.length),
  });

  const managerEventsQuery = useQuery({
    queryKey: ['profile', 'manager-events', token, meQuery.data?.assignedRoles],
    queryFn: async () => {
      const roles = meQuery.data?.assignedRoles ?? [];

      const eventIds = roles
        .filter((role) => role.roleName === 'Manager' && role.eventId)
        .map((role) => role.eventId as string);

      const uniqueEventIds = [...new Set(eventIds)];

      const events = await Promise.all(
        uniqueEventIds.map(async (eventId) => {
          const res = await http.get<ApiResponse<EventDTO>>(`/api/events/${eventId}`);
          if (res.data.error) throw new Error(res.data.error);
          if (!res.data.data) throw new Error('Event not found');
          return res.data.data;
        })
      );

      return events;
    },
    enabled:
      Boolean(token) &&
      userType === 'member' &&
      Boolean(meQuery.data?.assignedRoles?.some((role) => role.roleName === 'Manager')),
  });

  useEffect(() => {
    if (meQuery.data) {
      setNewUsername(meQuery.data.username ?? '');
      setNewBirthDate(meQuery.data.birthDate ?? '');
    }
  }, [meQuery.data]);

  const refreshProfile = async () => {
    await qc.invalidateQueries({ queryKey: ['me'] });
    await qc.invalidateQueries({ queryKey: ['profile', 'companies'] });
    await qc.invalidateQueries({ queryKey: ['profile', 'company-events'] });
  };

  const changeUsernameMutation = useMutation({
    mutationFn: async () => {
      try {
        if (!newUsername.trim()) throw new Error('Username cannot be empty.');

        const res = await http.post<ApiResponse<MemberDTO>>('/api/users/me/username', {
          newUsername,
        });

        if (res.data.error) throw new Error(res.data.error);
        if (!res.data.data) throw new Error('No profile data returned');

        return res.data.data;
      } catch (e) {
        const message = getApiErrorMessage<MemberDTO>(e, {
          fallback: 'Failed to change username.',
          serverFallback: 'Username is unavailable or already exists.',
        });

        if (
          message.toLowerCase().includes('exists') ||
          message.toLowerCase().includes('taken') ||
          message.toLowerCase().includes('duplicate')
        ) {
          throw new Error('This username is already taken.');
        }

        throw new Error(message);
      }
    },
    onSuccess: async (updated) => {
      setUsername(updated.username);
      await refreshProfile();
    },
  });

  const changeBirthDateMutation = useMutation({
    mutationFn: async () => {
      const selectedDate = new Date(newBirthDate);
      const today = new Date();

      today.setHours(0, 0, 0, 0);
      selectedDate.setHours(0, 0, 0, 0);

      if (selectedDate > today) {
        throw new Error('Birth date cannot be in the future.');
      }

      const res = await http.post<ApiResponse<MemberDTO>>('/api/users/me/birth-date', {
        newBirthDate,
      });

      if (res.data.error) throw new Error(res.data.error);
      if (!res.data.data) throw new Error('No profile data returned');

      return res.data.data;
    },
    onSuccess: refreshProfile,
  });

  const changePasswordMutation = useMutation({
    mutationFn: async () => {
      if (newPassword.length < 8 || !/[A-Z]/.test(newPassword) || !/\d/.test(newPassword)) {
        throw new Error(
          'Password must be at least 8 characters long and include at least 1 uppercase letter and 1 number.'
        );
      }

      const res = await http.post<ApiResponse<MemberDTO>>('/api/users/me/password', {
        newPassword,
      });

      if (res.data.error) throw new Error(res.data.error);
      if (!res.data.data) throw new Error('No profile data returned');

      return res.data.data;
    },
    onSuccess: () => {
      setNewPassword('');
    },
  });

  const changeRoleMutation = useMutation({
    mutationFn: async (roleTarget: string) => {
      let url = '';

      if (roleTarget === 'RegularMember') {
        url = '/api/users/me/roles/regular';
      } else if (roleTarget.startsWith('Founder:')) {
        const companyId = roleTarget.replace('Founder:', '');
        url = `/api/users/me/roles/founder/${companyId}`;
      } else if (roleTarget.startsWith('Owner:')) {
        const companyId = roleTarget.replace('Owner:', '');
        url = `/api/users/me/roles/owner/${companyId}`;
      } else if (roleTarget.startsWith('Manager:')) {
        const eventId = roleTarget.replace('Manager:', '');
        url = `/api/users/me/roles/manager/${eventId}`;
      } else {
        throw new Error('Unsupported role.');
      }

      const res = await http.post<ApiResponse<MemberDTO>>(url);

      if (res.data.error) throw new Error(res.data.error);
      if (!res.data.data) throw new Error('No profile data returned');

      return res.data.data;
    },
    onSuccess: refreshProfile,
  });

  const approveAppointmentMutation = useMutation({
    mutationFn: async (targetName: string) => {
      const res = await http.post<ApiResponse<MemberDTO>>('/api/users/roles/approve');

      if (res.data.error) throw new Error(res.data.error);
      if (!res.data.data) throw new Error('No profile data returned');

      return targetName;
    },

    onSuccess: async (targetName) => {
      setApprovedAppointmentTarget(targetName);
      await qc.invalidateQueries({ queryKey: ['appointment-approved'] });
      await refreshProfile();
    },
  });

  if (userType !== 'member') {
    return (
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">Profile</h1>
        <p className="mt-2 text-slate-600">Log in as a user to view your profile.</p>
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
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">Profile</h1>
        <p className="mt-2 text-slate-600">Please log in to view your profile.</p>
      </div>
    );
  }

  if (meQuery.isPending) return <div className="text-slate-600">Loading…</div>;

  if (meQuery.isError) {
    return (
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">Profile</h1>
        <div className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
          {(meQuery.error as Error).message}
        </div>
      </div>
    );
  }

  const me = meQuery.data;
  const companies = companiesQuery.data ?? [];
  const currentRole = me.activeRole ?? 'RegularMember';
  const isCurrentAppointmentApproved = appointmentApprovedQuery.data === true || approveAppointmentMutation.isSuccess;

  const assignedRoles = me.assignedRoles ?? [];

  const roleNames = assignedRoles.map((role) =>
    typeof role === 'string' ? role : role.roleName
  );

  const managerEventIds = assignedRoles
    .filter((role) => typeof role !== 'string' && role.roleName === 'Manager' && role.eventId)
    .map((role) => role.eventId);
  const founderCompanies = companies.filter((company) => company.founderId === me.userId);
  const ownerCompanies = roleNames.includes('Owner') || currentRole === 'Owner'
    ? companies
    : [];

  const managedEvents = managerEventsQuery.data ?? [];
  const hasFounderRole = founderCompanies.length > 0 || roleNames.includes('Founder')|| currentRole === 'Founder';
  const hasOwnerRole = ownerCompanies.length > 0 || roleNames.includes('Owner') || currentRole === 'Owner';
  const hasManagerRole = roleNames.includes('Manager') || currentRole === 'Manager';

  const updateError =
    changeUsernameMutation.error ??
    changeBirthDateMutation.error ??
    changePasswordMutation.error;

  const renderRoleButton = (isActive: boolean, target: string, small = false) => (
    <button
      onClick={() => changeRoleMutation.mutate(target)}
      disabled={isActive || changeRoleMutation.isPending}
      className={
        isActive
          ? `rounded-md bg-emerald-100 ${small ? 'px-3 py-1.5 text-xs' : 'px-3 py-2 text-sm'} font-semibold text-emerald-800`
          : `rounded-md bg-slate-900 ${small ? 'px-3 py-1.5 text-xs' : 'px-3 py-2 text-sm'} font-semibold text-white hover:bg-slate-800 disabled:opacity-60`
      }
    >
      {isActive ? 'Active' : small ? 'Switch' : 'Switch to this role'}
    </button>
  );

  return (
    <div className="space-y-4">
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">Profile</h1>

        <div className="mt-4 grid gap-3 md:grid-cols-2">
          <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
            <div className="text-sm text-slate-600">Username</div>
            <div className="mt-1 font-semibold text-slate-900">{me.username}</div>
          </div>

          <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
            <div className="text-sm text-slate-600">Birth date</div>
            <div className="mt-1 font-semibold text-slate-900">{me.birthDate ?? '—'}</div>
          </div>
        </div>
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h2 className="text-lg font-semibold text-slate-900">Edit personal details</h2>

        <div className="mt-4 grid gap-4 md:grid-cols-2">
          <label className="block">
            <div className="text-sm font-medium text-slate-700">New username</div>
            <input
              value={newUsername}
              onChange={(e) => setNewUsername(e.target.value)}
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm"
            />
            <button
              onClick={() => changeUsernameMutation.mutate()}
              disabled={changeUsernameMutation.isPending || !newUsername.trim()}
              className="mt-2 w-full rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
            >
              {changeUsernameMutation.isPending ? 'Saving…' : 'Change username'}
            </button>
          </label>

          <label className="block">
            <div className="text-sm font-medium text-slate-700">New birth date</div>
            <input
              type="date"
              value={newBirthDate}
              onChange={(e) => setNewBirthDate(e.target.value)}
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm"
            />
            <button
              onClick={() => changeBirthDateMutation.mutate()}
              disabled={changeBirthDateMutation.isPending || !newBirthDate}
              className="mt-2 w-full rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
            >
              {changeBirthDateMutation.isPending ? 'Saving…' : 'Change birth date'}
            </button>
          </label>

          <label className="block md:col-span-2">
            <div className="text-sm font-medium text-slate-700">New password</div>
            <input
              type="password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              placeholder="Enter new password"
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm"
            />
            <button
              onClick={() => changePasswordMutation.mutate()}
              disabled={changePasswordMutation.isPending || !newPassword.trim()}
              className="mt-2 w-full rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
            >
              {changePasswordMutation.isPending ? 'Saving…' : 'Change password'}
            </button>
          </label>
        </div>

        {(changeUsernameMutation.isSuccess ||
          changeBirthDateMutation.isSuccess ||
          changePasswordMutation.isSuccess) && (
          <div className="mt-3 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
            Profile updated successfully.
          </div>
        )}

        {updateError && (
          <div className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {updateError instanceof Error ? updateError.message : 'Failed to update profile.'}
          </div>
        )}
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h2 className="text-lg font-semibold text-slate-900">Roles</h2>

        <p className="mt-1 text-sm text-slate-600">
          Current active role: <span className="font-semibold">{currentRole}</span>
        </p>

        {approveAppointmentMutation.isError && (
          <div className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {(approveAppointmentMutation.error as Error).message}
          </div>
        )}

        <div className="mt-4 grid gap-3 md:grid-cols-2">
          <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
            <div className="flex items-center justify-between gap-3">
              <div>
                <div className="font-semibold text-slate-900">RegularMember</div>
                {currentRole === 'RegularMember' && (
                  <div className="mt-1 text-xs font-semibold text-emerald-700">Active now</div>
                )}
              </div>
              {renderRoleButton(currentRole === 'RegularMember', 'RegularMember')}
            </div>
          </div>

          {hasFounderRole && (
            <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
              <div className="flex items-center justify-between gap-3">
                <div>
                  <div className="font-semibold text-slate-900">Founder</div>
                  {currentRole === 'Founder' && (
                    <div className="mt-1 text-xs font-semibold text-emerald-700">
                      Active now
                    </div>
                  )}
                </div>

                {renderRoleButton(
                  currentRole === 'Founder',
                  `Founder:${founderCompanies[0]?.companyId}`
                )}
              </div>

              <div className="mt-3 space-y-2">
                {founderCompanies.length === 0 ? (
                  <div className="text-sm text-slate-600">No founder companies found.</div>
                ) : (
                  founderCompanies.map((company) => (
                    <div
                      key={company.companyId}
                      className="flex items-center justify-between gap-3 rounded-lg border border-slate-200 bg-white px-3 py-2"
                    >
                      <span className="text-sm font-medium text-slate-800">{company.name}</span>
                    </div>
                  ))
                )}
              </div>
            </div>
          )}

          {hasOwnerRole && (
            <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">

              <div className="flex items-center justify-between gap-3">
                <div>
                  <div className="font-semibold text-slate-900">Owner</div>

                  {currentRole === 'Owner' && (
                    <div className="mt-1 text-xs font-semibold text-emerald-700">
                      Active now
                    </div>
                  )}
                </div>

                {ownerCompanies.length > 0 &&
                  renderRoleButton(
                    currentRole === 'Owner',
                    `Owner:${ownerCompanies[0].companyId}`
                  )}
              </div>

              <div className="mt-3 space-y-2">
                {ownerCompanies.length === 0 ? (
                  <div className="text-sm text-slate-600">No owner companies found.</div>
                ) : (
                  ownerCompanies.map((company) => (
                    <div
                      key={company.companyId}
                      className="flex items-center justify-between gap-3 rounded-lg border border-slate-200 bg-white px-3 py-2"
                    >
                      <span className="text-sm font-medium text-slate-800">{company.name}</span>
                      <div className="flex items-center gap-2">
                        {approvedAppointmentTarget === company.companyId ? (
                          <span className="rounded-md bg-emerald-100 px-3 py-1.5 text-xs font-semibold text-emerald-800">
                            Appointment approved
                          </span>
                        ) : currentRole === 'Owner' && !isCurrentAppointmentApproved ? (
                          <button
                            onClick={() => approveAppointmentMutation.mutate(company.companyId)}
                            disabled={approveAppointmentMutation.isPending}
                            className="rounded-md bg-slate-900 px-3 py-1.5 text-xs font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
                          >
                            Approve appointment
                          </button>
                        ) : null}
                      </div>
                    </div>
                  ))
                )}
              </div>
            </div>
          )}

          {hasManagerRole && (
            <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">

              <div className="flex items-center justify-between gap-3">
                <div>
                  <div className="font-semibold text-slate-900">Manager</div>

                  {currentRole === 'Manager' && (
                    <div className="mt-1 text-xs font-semibold text-emerald-700">
                      Active now
                    </div>
                  )}
                </div>

                {renderRoleButton(
                  currentRole === 'Manager',
                  `Manager:${managedEvents[0]?.eventId}`
                )}
              </div>

              <div className="mt-3 space-y-2">
                {managedEvents.length === 0 ? (
                  <div className="text-sm text-slate-600">No managed events found.</div>
                ) : (
                  managedEvents.map((event) => (
                    <div
                      key={event.eventId}
                      className="flex items-center justify-between gap-3 rounded-lg border border-slate-200 bg-white px-3 py-2"
                    >
                      <span className="text-sm font-medium text-slate-800">
                        {event.name}
                      </span>
                      <div className="flex items-center gap-2">
                        {approvedAppointmentTarget === event.eventId ? (
                          <span className="rounded-md bg-emerald-100 px-3 py-1.5 text-xs font-semibold text-emerald-800">
                            Appointment approved
                          </span>
                        ) : currentRole === 'Manager' && !isCurrentAppointmentApproved ? (
                          <button
                            onClick={() => approveAppointmentMutation.mutate(event.eventId)}
                            disabled={approveAppointmentMutation.isPending}
                            className="rounded-md bg-slate-900 px-3 py-1.5 text-xs font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
                          >
                            Approve appointment
                          </button>
                        ) : null}

                        {renderRoleButton(currentRole === 'Manager', `Manager:${event.eventId}`, true)}
                      </div>
                    </div>
                  ))
                )}
              </div>
            </div>
          )}
        </div>

        {changeRoleMutation.isSuccess && (
          <div className="mt-3 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
            Active role changed successfully.
          </div>
        )}

        {changeRoleMutation.isError && (
          <div className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {(changeRoleMutation.error as Error).message}
          </div>
        )}
      </div>
    </div>
  );
}