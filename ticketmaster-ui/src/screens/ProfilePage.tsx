import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { AxiosError } from 'axios';
import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { http } from '../api/http';
import { getApiErrorMessage } from '../api/errors';
import type { ApiResponse, MemberDTO } from '../api/types';
import { useAuthStore } from '../ui/authStore';

export default function ProfilePage() {
  const qc = useQueryClient();
  const { token, userType, clearAuth, setUsername } = useAuthStore();

  const [newUsername, setNewUsername] = useState('');
  const [newBirthDate, setNewBirthDate] = useState('');
  const [newPassword, setNewPassword] = useState('');

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
    enabled: userType === 'member' && Boolean(token),
  });

  useEffect(() => {
    if (meQuery.data) {
      setNewUsername(meQuery.data.username ?? '');
      setNewBirthDate(meQuery.data.birthDate ?? '');
    }
  }, [meQuery.data]);

  const changeUsernameMutation = useMutation({
    mutationFn: async () => {
      try {
        if (!newUsername.trim()) {
          throw new Error('Username cannot be empty.');
        }

        const res = await http.post<ApiResponse<MemberDTO>>(
          '/api/users/me/username',
          {
            newUsername,
          }
        );

        if (res.data.error) {
          throw new Error(res.data.error);
        }

        if (!res.data.data) {
          throw new Error('No profile data returned');
        }

        return res.data.data;

      } catch (e) {
        const message = getApiErrorMessage<MemberDTO>(e, {
          fallback: 'Failed to change username.',
          serverFallback:
            'Username is unavailable or already exists.',
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
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ['me'] });
    },
  });

  const changePasswordMutation = useMutation({
    mutationFn: async () => {

      if (
        newPassword.length < 8 ||
        !/[A-Z]/.test(newPassword) ||
        !/\d/.test(newPassword)
      ) {
        throw new Error(
          'Password must be at least 8 characters long and include at least 1 uppercase letter and 1 number.'
        );
      }

      const res = await http.post<ApiResponse<MemberDTO>>(
        '/api/users/me/password',
        {
          newPassword,
        }
      );

      if (res.data.error) throw new Error(res.data.error);
      if (!res.data.data) throw new Error('No profile data returned');

      return res.data.data;
    },

    onSuccess: () => {
      setNewPassword('');
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

        {(changeUsernameMutation.isError ||
          changeBirthDateMutation.isError ||
          changePasswordMutation.isError) && (() => {
            const error =
              changeUsernameMutation.error ??
              changeBirthDateMutation.error ??
              changePasswordMutation.error;

            return (
              <div className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
                {error instanceof Error ? error.message : 'Failed to update profile.'}
              </div>
            );
          })()}
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h2 className="text-lg font-semibold text-slate-900">Roles</h2>

        <div className="mt-3 flex flex-wrap gap-2">
          {me.activeRole && (
            <span className="rounded-full bg-slate-900 px-3 py-1 text-xs font-semibold text-white">
              {me.activeRole}
            </span>
          )}

          {(me.assignedRoles ?? []).map((r) => (
            <span key={r} className="rounded-full bg-slate-100 px-3 py-1 text-xs text-slate-700">
              {r}
            </span>
          ))}

          {!me.activeRole && (me.assignedRoles ?? []).length === 0 && (
            <div className="text-sm text-slate-600">No roles assigned.</div>
          )}
        </div>
      </div>
    </div>
  );
}