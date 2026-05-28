import { useMutation, useQuery } from '@tanstack/react-query';
import type { AxiosError } from 'axios';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { http } from '../api/http';
import { getApiErrorMessage } from '../api/errors';
import type { ApiResponse, EventDTO, QueueSnapshotDTO } from '../api/types';
import { useAuthStore } from '../ui/authStore';

type EventsResponse = ApiResponse<EventDTO[]>;
type QueueResponse = ApiResponse<QueueSnapshotDTO>;
type VoidResponse = ApiResponse<null>;

export default function AdminEventQueuesPage() {
  const { token, userType, clearAuth } = useAuthStore();

  const [selectedEventId, setSelectedEventId] = useState('');
  const [capacity, setCapacity] = useState('100');
  const [maxAccepted, setMaxAccepted] = useState('10');

  const eventsQuery = useQuery({
    queryKey: ['admin', 'events'],
    queryFn: async () => {
      const res = await http.get<EventsResponse>('/api/events');
      if (res.data.error) throw new Error(res.data.error);
      return res.data.data ?? [];
    },
    enabled: Boolean(token) && userType === 'system-admin',
  });

  const queueQuery = useQuery({
    queryKey: ['event-queue', selectedEventId],
    queryFn: async () => {
      const res = await http.get<QueueResponse>(`/api/queues/${selectedEventId}`);
      if (res.data.error) throw new Error(res.data.error);
      if (!res.data.data) throw new Error('Queue not found');
      return res.data.data;
    },
    enabled: Boolean(selectedEventId),
    retry: false,
  });

  const createOrUpdateMutation = useMutation({
    mutationFn: async () => {
      const body = {
        capacity: Number(capacity),
        maxAccepted: Number(maxAccepted),
      };

      try {
        await http.patch<VoidResponse>(
          `/api/queues/${selectedEventId}`,
          body
        );
      } catch {
        await http.post<VoidResponse>(
          `/api/queues/${selectedEventId}`,
          body
        );
      }
    },
  });

  const clearQueueMutation = useMutation({
    mutationFn: async () => {
      await http.delete<VoidResponse>(
        `/api/queues/${selectedEventId}/users`
      );
    },
  });

  if (userType !== 'system-admin') {
    return (
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-extrabold text-slate-900">
          Event Queues
        </h1>
        <p className="mt-2 text-slate-600">
          Log in as admin.
        </p>

        <Link
          to="/login"
          className="mt-4 inline-flex rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white"
        >
          Login
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-4">

      <div>
        <h1 className="text-2xl font-extrabold text-slate-900">
          Event Queues
        </h1>

        <p className="mt-1 text-sm text-slate-600">
          Manage queues for specific events.
        </p>
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">

        <div className="text-lg font-semibold text-slate-900">
          Search event
        </div>

        <div className="mt-4 grid gap-3 md:grid-cols-3">

          <label className="block md:col-span-3">
            <div className="text-sm font-medium text-slate-700">
              Event
            </div>

            <select
              value={selectedEventId}
              onChange={(e) => setSelectedEventId(e.target.value)}
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
            >
              <option value="">Select event…</option>

              {eventsQuery.data?.map((e) => (
                <option key={e.eventId} value={e.eventId}>
                  {e.name} — {e.artist}
                </option>
              ))}
            </select>
          </label>

          <label>
            <div className="text-sm font-medium text-slate-700">
              Queue capacity
            </div>

            <input
              value={capacity}
              onChange={(e) => setCapacity(e.target.value)}
              className="mt-1 w-full rounded-md border border-slate-200 px-3 py-2 text-sm"
            />
          </label>

          <label>
            <div className="text-sm font-medium text-slate-700">
              Max admitted
            </div>

            <input
              value={maxAccepted}
              onChange={(e) => setMaxAccepted(e.target.value)}
              className="mt-1 w-full rounded-md border border-slate-200 px-3 py-2 text-sm"
            />
          </label>

          <div className="flex items-end">
            <button
              onClick={() => createOrUpdateMutation.mutate()}
              disabled={!selectedEventId}
              className="w-full rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800"
            >
              Enable / Update Queue
            </button>
          </div>
        </div>

        <div className="mt-4">
          <button
            onClick={() => clearQueueMutation.mutate()}
            disabled={!selectedEventId}
            className="rounded-md bg-rose-600 px-3 py-2 text-sm font-semibold text-white hover:bg-rose-700"
          >
            Clear Queue
          </button>
        </div>

        {queueQuery.data && (
          <div className="mt-6 grid gap-3 md:grid-cols-4">

            <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
              <div className="text-sm text-slate-600">Capacity</div>
              <div className="mt-1 text-xl font-semibold">
                {queueQuery.data.capacity}
              </div>
            </div>

            <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
              <div className="text-sm text-slate-600">Max admitted</div>
              <div className="mt-1 text-xl font-semibold">
                {queueQuery.data.maxAccepted}
              </div>
            </div>

            <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
              <div className="text-sm text-slate-600">Waiting</div>
              <div className="mt-1 text-xl font-semibold">
                {queueQuery.data.waitingCount}
              </div>
            </div>

            <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
              <div className="text-sm text-slate-600">Admitted</div>
              <div className="mt-1 text-xl font-semibold">
                {queueQuery.data.admittedCount}
              </div>
            </div>

          </div>
        )}

      </div>
    </div>
  );
}