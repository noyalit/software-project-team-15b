import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { AxiosError } from 'axios';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { http } from '../api/http';
import { getApiErrorMessage } from '../api/errors';
import type { ApiResponse, SiteQueueSnapshotDTO } from '../api/types';
import { useAuthStore } from '../ui/authStore';

type VoidResponse = ApiResponse<null>;

export default function AdminQueuesPage() {
  const qc = useQueryClient();
  const { token, userType, clearAuth } = useAuthStore();

  const [maxVisitors, setMaxVisitors] = useState('100');

  const siteQuery = useQuery({
    queryKey: ['admin', 'site-queue', token],
    queryFn: async () => {
      try {
        const res = await http.get<ApiResponse<SiteQueueSnapshotDTO>>('/api/queues/site');
        if (res.data.error) throw new Error(res.data.error);
        if (!res.data.data) throw new Error('There is no queue.');
        return res.data.data;
      } catch (e) {
        const err = e as AxiosError<ApiResponse<SiteQueueSnapshotDTO>>;
        const status = err.response?.status;

        if (status === 401) {
          clearAuth();
          throw new Error('Your session expired. Please log in again.');
        }
        if (status === 403) {
          throw new Error('You are not authorized to view admin queues.');
        }
        if (status === 400 || status === 404) {
          throw new Error('There is no queue.');
        }

        throw new Error(
          getApiErrorMessage<SiteQueueSnapshotDTO>(e, {
            fallback: 'Failed to load the site queue snapshot. Please try again.',
            serverFallback: 'Queues are currently unavailable due to a server issue. Please try again later.',
          })
        );
      }
    },
    enabled: userType === 'system-admin' && Boolean(token),
  });

  const updateSiteQueue = useMutation({
    mutationFn: async () => {
      const next = Number(maxVisitors);
      if (!Number.isFinite(next) || next <= 0) throw new Error('Max visitors must be a positive number.');

      try {
        const res = await http.patch<ApiResponse<SiteQueueSnapshotDTO>>('/api/queues/site', { maxVisitors: next });
        if (res.data.error) throw new Error(res.data.error);
        if (!res.data.data) throw new Error('There is no queue.');
        return res.data.data;
      } catch (e) {
        const err = e as AxiosError<ApiResponse<SiteQueueSnapshotDTO>>;
        const status = err.response?.status;

        if (status === 401) {
          clearAuth();
          throw new Error('Your session expired. Please log in again.');
        }
        if (status === 403) {
          throw new Error('You are not authorized to update the site queue.');
        }
        if (status === 400 || status === 404) {
          throw new Error('There is no queue.');
        }

        throw new Error(
          getApiErrorMessage<SiteQueueSnapshotDTO>(e, {
            fallback: 'Failed to update the site queue. Please verify your input and try again.',
            serverFallback: 'Queues are currently unavailable due to a server issue. Please try again later.',
          })
        );
      }
    },
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ['admin', 'site-queue'] });
    },
  });

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
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">Site Queue</h1>
        <p className="mt-1 text-sm text-slate-600">Control how many visitors can be admitted to the site at the same time.</p>
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="text-lg font-semibold text-slate-900">Update site limit</div>

        <div className="mt-4 grid gap-3 md:grid-cols-3">
          <label className="block md:col-span-2">
            <div className="text-sm font-medium text-slate-700">Max visitors</div>
            <input
              value={maxVisitors}
              onChange={(e) => setMaxVisitors(e.target.value)}
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm"
            />
          </label>
          <div className="flex items-end">
            <button
              onClick={() => updateSiteQueue.mutate()}
              disabled={updateSiteQueue.isPending}
              className="w-full rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
            >
              {updateSiteQueue.isPending ? 'Saving…' : 'Save'}
            </button>
          </div>
        </div>

        {updateSiteQueue.isError && (
          <div className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {(updateSiteQueue.error as Error).message}
          </div>
        )}
        {updateSiteQueue.isSuccess && !updateSiteQueue.isError && (
          <div className="mt-3 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
            Site queue updated.
          </div>
        )}
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex items-center justify-between gap-3">
          <div className="text-lg font-semibold text-slate-900">Current status</div>
          <button
            onClick={() => siteQuery.refetch()}
            className="rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-800 hover:bg-slate-50"
          >
            Refresh
          </button>
        </div>

        {siteQuery.isPending && <div className="mt-4 text-slate-600">Loading…</div>}
        {siteQuery.isError && (
          <div className="mt-4 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {(siteQuery.error as Error).message}
          </div>
        )}

        {!siteQuery.isPending && !siteQuery.isError && (
          <div className="mt-4 grid gap-3 md:grid-cols-3">
            <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
              <div className="text-sm text-slate-600">Max visitors</div>
              <div className="mt-1 text-xl font-semibold text-slate-900">{siteQuery.data.maxVisitors}</div>
            </div>
            <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
              <div className="text-sm text-slate-600">Waiting</div>
              <div className="mt-1 text-xl font-semibold text-slate-900">{siteQuery.data.waitingCount}</div>
            </div>
            <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
              <div className="text-sm text-slate-600">Admitted</div>
              <div className="mt-1 text-xl font-semibold text-slate-900">{siteQuery.data.admittedCount}</div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
