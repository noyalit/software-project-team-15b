import { useMutation, useQuery } from '@tanstack/react-query';
import type { AxiosError } from 'axios';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { http } from '../api/http';
import { getApiErrorMessage } from '../api/errors';
import type {
  ApiResponse,
  EventDTO,
  QueueSnapshotDTO,
} from '../api/types';
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
    queryKey: ['admin', 'events', token],
    queryFn: async () => {
      try {
        const res = await http.post<EventsResponse>('/api/events/search', {});

        if (res.data.error) {
          throw new Error(res.data.error);
        }

        return res.data.data ?? [];
      } catch (e) {
        const err = e as AxiosError<EventsResponse>;
        const status = err.response?.status;

        if (status === 401) {
          clearAuth();
          throw new Error('Your session expired. Please log in again.');
        }

        throw new Error(
          getApiErrorMessage<EventDTO[]>(e, {
            fallback: 'Failed to load events.',
            serverFallback:
              'Events are currently unavailable due to a server issue.',
          })
        );
      }
    },
    enabled: Boolean(token) && userType === 'system-admin',
  });

  const queueQuery = useQuery({
    queryKey: ['admin', 'event-queue', selectedEventId],
    queryFn: async () => {
      try {
        const res = await http.get<QueueResponse>(
          `/api/queues/${selectedEventId}`
        );

        if (res.data.error) {
          throw new Error(res.data.error);
        }

        if (!res.data.data) {
          throw new Error('Queue not found.');
        }

        return res.data.data;
      } catch (e) {
        const err = e as AxiosError<QueueResponse>;
        const status = err.response?.status;

        if (status === 401) {
          clearAuth();
          throw new Error('Your session expired. Please log in again.');
        }

        throw new Error(
          getApiErrorMessage<QueueSnapshotDTO>(e, {
            fallback: 'Queue not found.',
            serverFallback:
              'Queues are currently unavailable due to a server issue.',
          })
        );
      }
    },
    enabled: Boolean(selectedEventId),
    retry: false,
  });

  const createOrUpdateMutation = useMutation({
    mutationFn: async () => {
      if (!selectedEventId.trim()) {
        throw new Error('Please select an event.');
      }

      const nextCapacity = Number(capacity);
      const nextMaxAccepted = Number(maxAccepted);

      if (!Number.isFinite(nextCapacity) || nextCapacity <= 0) {
        throw new Error('Capacity must be positive.');
      }

      if (
        !Number.isFinite(nextMaxAccepted) ||
        nextMaxAccepted <= 0
      ) {
        throw new Error('Max admitted must be positive.');
      }

      const body = {
        capacity: nextCapacity,
        maxAccepted: nextMaxAccepted,
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

      await queueQuery.refetch();
    },
  });

  const clearQueueMutation = useMutation({
    mutationFn: async () => {
      if (!selectedEventId.trim()) {
        throw new Error('Please select an event.');
      }

      await http.delete<VoidResponse>(
        `/api/queues/${selectedEventId}/users`
      );

      await queueQuery.refetch();
    },
  });

  if (userType !== 'system-admin') {
    return (
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">
          Event Queues
        </h1>

        <p className="mt-2 text-slate-600">
          Log in as an admin to use these tools.
        </p>

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
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">
          Event Queues
        </h1>

        <p className="mt-2 text-slate-600">
          Please log in again to continue.
        </p>

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
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">
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
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm"
            >
              <option value="">Select event…</option>

              {eventsQuery.data?.map((event) => (
                <option
                  key={event.eventId}
                  value={event.eventId}
                >
                  {event.name} — {event.artist}
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
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm"
            />
          </label>

          <label>
            <div className="text-sm font-medium text-slate-700">
              Max admitted
            </div>

            <input
              value={maxAccepted}
              onChange={(e) => setMaxAccepted(e.target.value)}
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm"
            />
          </label>

          <div className="flex items-end">
            <button
              onClick={() => createOrUpdateMutation.mutate()}
              disabled={
                createOrUpdateMutation.isPending ||
                !selectedEventId
              }
              className="w-full rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
            >
              {createOrUpdateMutation.isPending
                ? 'Saving…'
                : 'Enable / Update Queue'}
            </button>
          </div>
        </div>

        <div className="mt-4">
          <button
            onClick={() => clearQueueMutation.mutate()}
            disabled={
              clearQueueMutation.isPending ||
              !selectedEventId
            }
            className="rounded-md bg-rose-600 px-3 py-2 text-sm font-semibold text-white hover:bg-rose-700 disabled:opacity-60"
          >
            {clearQueueMutation.isPending
              ? 'Clearing…'
              : 'Clear Queue'}
          </button>
        </div>

        {createOrUpdateMutation.isSuccess && (
          <div className="mt-3 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
            Queue updated successfully.
          </div>
        )}

        {createOrUpdateMutation.isError && (
          <div className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {(createOrUpdateMutation.error as Error).message}
          </div>
        )}

        {clearQueueMutation.isSuccess && (
          <div className="mt-3 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
            Queue cleared successfully.
          </div>
        )}

        {clearQueueMutation.isError && (
          <div className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {(clearQueueMutation.error as Error).message}
          </div>
        )}
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">

        <div className="flex items-center justify-between gap-3">
          <div className="text-lg font-semibold text-slate-900">
            Current status
          </div>

          <button
            onClick={() => queueQuery.refetch()}
            disabled={!selectedEventId}
            className="rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-800 hover:bg-slate-50 disabled:opacity-60"
          >
            Refresh
          </button>
        </div>

        {!selectedEventId && (
          <div className="mt-4 text-slate-600">
            Select an event to view queue status.
          </div>
        )}

        {selectedEventId && queueQuery.isPending && (
          <div className="mt-4 text-slate-600">
            Loading…
          </div>
        )}

        {selectedEventId && queueQuery.isError && (
          <div className="mt-4 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
            No queue exists for this event.
          </div>
        )}

        {selectedEventId &&
          queueQuery.data &&
          !queueQuery.isError && (
            <div className="mt-4 grid gap-3 md:grid-cols-4">

              <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
                <div className="text-sm text-slate-600">
                  Capacity
                </div>

                <div className="mt-1 text-xl font-semibold text-slate-900">
                  {queueQuery.data.capacity}
                </div>

                <div className="mt-1 text-xs text-slate-500">
                  Max users allowed to wait in line.
                </div>
              </div>

              <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
                <div className="text-sm text-slate-600">
                  Max admitted
                </div>

                <div className="mt-1 text-xl font-semibold text-slate-900">
                  {queueQuery.data.maxAccepted}
                </div>

                <div className="mt-1 text-xs text-slate-500">
                  Max users allowed to buy at the same time.
                </div>
              </div>

              <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
                <div className="text-sm text-slate-600">
                  Waiting
                </div>

                <div className="mt-1 text-xl font-semibold text-slate-900">
                  {queueQuery.data.waitingCount}
                </div>

                <div className="mt-1 text-xs text-slate-500">
                  Users currently in line (not admitted yet).
                </div>
              </div>

              <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
                <div className="text-sm text-slate-600">
                  Admitted
                </div>

                <div className="mt-1 text-xl font-semibold text-slate-900">
                  {queueQuery.data.admittedCount}
                </div>

                <div className="mt-1 text-xs text-slate-500">
                  Users with active access right now.
                </div>
              </div>

            </div>
          )}
      </div>
    </div>
  );
}