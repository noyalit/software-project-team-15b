import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { AxiosError } from 'axios';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { http } from '../api/http';
import type { ApiResponse, QueueSnapshotDTO } from '../api/types';
import { useAuthStore } from '../ui/authStore';

type VoidResponse = ApiResponse<null>;

export default function AdminQueuesPage() {
  const qc = useQueryClient();
  const { token, userType, clearAuth } = useAuthStore();

  const [eventId, setEventId] = useState('');
  const [capacity, setCapacity] = useState('200');
  const [maxAccepted, setMaxAccepted] = useState('50');

  const queuesQuery = useQuery({
    queryKey: ['admin', 'queues', token],
    queryFn: async () => {
      try {
        const res = await http.get<ApiResponse<QueueSnapshotDTO[]>>('/api/queues');
        if (res.data.error) throw new Error(res.data.error);
        return res.data.data ?? [];
      } catch (e) {
        const err = e as AxiosError<ApiResponse<QueueSnapshotDTO[]>>;
        const status = err.response?.status;
        const apiMessage = err.response?.data?.error;

        if (status === 401) {
          clearAuth();
          throw new Error('Your session expired. Please log in again.');
        }
        if (status === 403) {
          throw new Error('You are not authorized to view admin queues.');
        }

        throw new Error(apiMessage || err.message);
      }
    },
    enabled: userType === 'system-admin' && Boolean(token),
  });

  const createQueue = useMutation({
    mutationFn: async () => {
      if (!eventId.trim()) throw new Error('Event ID is required.');
      const cap = Number(capacity);
      const max = Number(maxAccepted);
      if (!Number.isFinite(cap) || cap <= 0) throw new Error('Capacity must be a positive number.');
      if (!Number.isFinite(max) || max <= 0) throw new Error('Max accepted must be a positive number.');

      try {
        const res = await http.post<VoidResponse>(`/api/queues/${eventId.trim()}`, {
          capacity: cap,
          maxAccepted: max,
        });
        if (res.data.error) throw new Error(res.data.error);
        return true;
      } catch (e) {
        const err = e as AxiosError<VoidResponse>;
        const status = err.response?.status;
        const apiMessage = err.response?.data?.error;

        if (status === 401) {
          clearAuth();
          throw new Error('Your session expired. Please log in again.');
        }
        if (status === 403) {
          throw new Error('You are not authorized to create queues.');
        }

        throw new Error(apiMessage || err.message);
      }
    },
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ['admin', 'queues'] });
    },
  });

  const deleteQueue = useMutation({
    mutationFn: async (id: string) => {
      try {
        const res = await http.delete<VoidResponse>(`/api/queues/${id}`);
        if (res.data.error) throw new Error(res.data.error);
        return true;
      } catch (e) {
        const err = e as AxiosError<VoidResponse>;
        const status = err.response?.status;
        const apiMessage = err.response?.data?.error;

        if (status === 401) {
          clearAuth();
          throw new Error('Your session expired. Please log in again.');
        }
        if (status === 403) {
          throw new Error('You are not authorized to delete queues.');
        }

        throw new Error(apiMessage || err.message);
      }
    },
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ['admin', 'queues'] });
    },
  });

  const clearUsers = useMutation({
    mutationFn: async (id: string) => {
      try {
        const res = await http.delete<VoidResponse>(`/api/queues/${id}/users`);
        if (res.data.error) throw new Error(res.data.error);
        return true;
      } catch (e) {
        const err = e as AxiosError<VoidResponse>;
        const status = err.response?.status;
        const apiMessage = err.response?.data?.error;

        if (status === 401) {
          clearAuth();
          throw new Error('Your session expired. Please log in again.');
        }
        if (status === 403) {
          throw new Error('You are not authorized to clear queues.');
        }

        throw new Error(apiMessage || err.message);
      }
    },
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ['admin', 'queues'] });
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
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">Admin Queues</h1>
        <p className="mt-1 text-sm text-slate-600">Manage virtual queues for events.</p>
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="text-lg font-semibold text-slate-900">Create / Update Queue</div>
        <div className="mt-4 grid gap-3 md:grid-cols-3">
          <label className="block md:col-span-3">
            <div className="text-sm font-medium text-slate-700">Event ID</div>
            <input
              value={eventId}
              onChange={(e) => setEventId(e.target.value)}
              placeholder="UUID"
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm"
            />
          </label>
          <label className="block">
            <div className="text-sm font-medium text-slate-700">Capacity</div>
            <input
              value={capacity}
              onChange={(e) => setCapacity(e.target.value)}
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm"
            />
          </label>
          <label className="block">
            <div className="text-sm font-medium text-slate-700">Max accepted</div>
            <input
              value={maxAccepted}
              onChange={(e) => setMaxAccepted(e.target.value)}
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm"
            />
          </label>
          <div className="flex items-end">
            <button
              onClick={() => createQueue.mutate()}
              disabled={createQueue.isPending}
              className="w-full rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
            >
              {createQueue.isPending ? 'Saving…' : 'Save queue'}
            </button>
          </div>
        </div>

        {createQueue.isError && (
          <div className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {(createQueue.error as Error).message}
          </div>
        )}
        {createQueue.isSuccess && !createQueue.isError && (
          <div className="mt-3 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
            Queue saved.
          </div>
        )}
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex items-center justify-between gap-3">
          <div className="text-lg font-semibold text-slate-900">Existing Queues</div>
          <button
            onClick={() => queuesQuery.refetch()}
            className="rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-800 hover:bg-slate-50"
          >
            Refresh
          </button>
        </div>

        {queuesQuery.isPending && <div className="mt-4 text-slate-600">Loading…</div>}
        {queuesQuery.isError && (
          <div className="mt-4 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {(queuesQuery.error as Error).message}
          </div>
        )}

        {!queuesQuery.isPending && !queuesQuery.isError && queuesQuery.data.length === 0 && (
          <div className="mt-4 text-slate-600">No queues found.</div>
        )}

        {!queuesQuery.isPending && !queuesQuery.isError && queuesQuery.data.length > 0 && (
          <div className="mt-4 space-y-3">
            {queuesQuery.data.map((q) => (
              <div key={q.eventId} className="rounded-xl border border-slate-200 bg-slate-50 p-4">
                <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
                  <div>
                    <div className="font-semibold text-slate-900">Event: {q.eventId}</div>
                    <div className="mt-1 text-sm text-slate-700">
                      Capacity: {q.capacity} • Max accepted: {q.maxAccepted}
                    </div>
                    <div className="mt-1 text-sm text-slate-700">
                      Waiting: {q.waitingCount} • Admitted: {q.admittedCount}
                    </div>
                  </div>
                  <div className="flex gap-2">
                    <button
                      onClick={() => clearUsers.mutate(q.eventId)}
                      disabled={clearUsers.isPending}
                      className="rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-800 hover:bg-slate-50 disabled:opacity-60"
                    >
                      Clear users
                    </button>
                    <button
                      onClick={() => deleteQueue.mutate(q.eventId)}
                      disabled={deleteQueue.isPending}
                      className="rounded-md bg-rose-600 px-3 py-2 text-sm font-semibold text-white hover:bg-rose-500 disabled:opacity-60"
                    >
                      Delete
                    </button>
                  </div>
                </div>

                {Object.keys(q.admittedUsers ?? {}).length > 0 && (
                  <details className="mt-3">
                    <summary className="cursor-pointer text-sm font-medium text-slate-700">Admitted users</summary>
                    <div className="mt-2 space-y-1 text-xs text-slate-600">
                      {Object.entries(q.admittedUsers).map(([t, expires]) => (
                        <div key={t} className="break-all">
                          {t} → {expires}
                        </div>
                      ))}
                    </div>
                  </details>
                )}
              </div>
            ))}
          </div>
        )}

        {(deleteQueue.isError || clearUsers.isError) && (
          <div className="mt-4 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {((deleteQueue.error || clearUsers.error) as Error).message}
          </div>
        )}
      </div>
    </div>
  );
}
