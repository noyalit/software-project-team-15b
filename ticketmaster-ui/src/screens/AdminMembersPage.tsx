import { useMutation, useQuery } from '@tanstack/react-query';
import type { AxiosError } from 'axios';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { http } from '../api/http';
import { getApiErrorMessage } from '../api/errors';
import type { ApiResponse, MemberDTO } from '../api/types';
import { useAuthStore } from '../ui/authStore';

type ResolveMemberResponse = ApiResponse<MemberDTO>;

type CancelMemberResponse = ApiResponse<boolean>;

export default function AdminMembersPage() {
  const { token, userType, clearAuth } = useAuthStore();

  const [username, setUsername] = useState('');
  const [resolvedMember, setResolvedMember] = useState<MemberDTO | null>(null);
  const [cancelResult, setCancelResult] = useState<boolean | null>(null);

  const resolveQuery = useQuery({
    queryKey: ['admin', 'members', 'resolve', token, username],
    queryFn: async () => {
      setCancelResult(null);
      setResolvedMember(null);
      try {
        const res = await http.get<ResolveMemberResponse>('/api/users/members/resolve', {
          params: { username },
        });
        if (res.data.error) throw new Error(res.data.error);
        if (!res.data.data) throw new Error('Member not found');
        setResolvedMember(res.data.data);
        return res.data.data;
      } catch (e) {
        const err = e as AxiosError<ResolveMemberResponse>;
        const statusCode = err.response?.status;

        if (statusCode === 401) {
          clearAuth();
          throw new Error('Your session expired. Please log in again.');
        }

        throw new Error(
          getApiErrorMessage<MemberDTO>(e, {
            fallback: 'Failed to find member. Please verify the username and try again.',
            serverFallback: 'Member lookup is currently unavailable due to a server issue. Please try again later.',
          })
        );
      }
    },
    enabled: false,
    retry: false,
  });

  const cancelMemberMutation = useMutation({
    mutationFn: async () => {
      if (!resolvedMember?.userId) throw new Error('Resolve a member first.');

      try {
        const res = await http.post<CancelMemberResponse>('/api/users/admin/cancel-member', {
          memberIdToCancel: resolvedMember.userId,
        });
        if (res.data.error) throw new Error(res.data.error);
        if (res.data.data == null) throw new Error('No response returned');
        return res.data.data;
      } catch (e) {
        const err = e as AxiosError<CancelMemberResponse>;
        const statusCode = err.response?.status;

        if (statusCode === 401) {
          clearAuth();
          throw new Error('Your session expired. Please log in again.');
        }
        if (statusCode === 403) {
          throw new Error('You are not authorized to manage members.');
        }

        throw new Error(
          getApiErrorMessage<boolean>(e, {
            fallback: 'Failed to suspend member. Please try again.',
            serverFallback: 'Member management is currently unavailable due to a server issue. Please try again later.',
          })
        );
      }
    },
    onSuccess: (ok) => {
      setCancelResult(ok);

      if (ok) {
        setResolvedMember(null);
        setUsername('');
      }
    },
  });

  if (userType !== 'system-admin') {
    return (
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">Members</h1>
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
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">Members</h1>
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

  const isFounder = Boolean(resolvedMember?.assignedRoles?.some((r) => r.roleName === 'Founder'));

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">Members</h1>
        <p className="mt-1 text-sm text-slate-600">Resolve a member by username and suspend them.</p>
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="text-lg font-semibold text-slate-900">Find member</div>
        <div className="mt-4 grid gap-3 md:grid-cols-3">
          <label className="block md:col-span-2">
            <div className="text-sm font-medium text-slate-700">Username</div>
            <input
              value={username}
              onChange={(e) => {
                setUsername(e.target.value);
                setCancelResult(null);
              }}
              placeholder="username"
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm"
            />
          </label>
          <div className="md:col-span-1 flex items-end">
            <button
              onClick={() => resolveQuery.refetch()}
              disabled={resolveQuery.isFetching || !username.trim()}
              className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-800 hover:bg-slate-50 disabled:opacity-60"
            >
              {resolveQuery.isFetching ? 'Searching…' : 'Search'}
            </button>
          </div>
        </div>

        {resolveQuery.isError && (
          <div className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {(resolveQuery.error as Error).message}
          </div>
        )}

        {cancelResult === true && (
          <div className="mt-3 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
            Member suspended successfully. Please search again to manage another member.
          </div>
        )}

        {resolvedMember && (
          <div className="mt-4 rounded-xl border border-slate-200 bg-slate-50 p-4">
            <div className="text-sm font-semibold text-slate-900">Member</div>
            <div className="mt-2 grid gap-2 text-sm text-slate-700 md:grid-cols-2">
              <div>
                <span className="font-medium">Username:</span> {resolvedMember.username}
              </div>
              <div>
                <span className="font-medium">Birth date:</span> {resolvedMember.birthDate ?? '—'}
              </div>
              <div className="md:col-span-2">
                <span className="font-medium">Roles:</span>{' '}
                {(resolvedMember.assignedRoles ?? []).length > 0
                  ? (resolvedMember.assignedRoles ?? [])
                      .map((r) => (typeof r === 'string' ? r : r.roleName))
                      .join(', ')
                  : 'RegularMember'}
              </div>
            </div>

            {isFounder && (
              <div className="mt-3 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
                This member has a Founder role and cannot be suspended.
              </div>
            )}

            <div className="mt-4">
              <button
                onClick={() => cancelMemberMutation.mutate()}
                disabled={
                  cancelMemberMutation.isPending ||
                  isFounder
                }
                className="w-full rounded-md bg-rose-600 px-3 py-2 text-sm font-semibold text-white hover:bg-rose-700 disabled:opacity-60"
              >
                {cancelMemberMutation.isPending ? 'Suspending…' : 'Suspend member'}
              </button>
            </div>

            {cancelMemberMutation.isError && (
              <div className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
                {(cancelMemberMutation.error as Error).message}
              </div>
            )}

            {cancelResult === false && (
              <div className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
                Member was not suspended.
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
