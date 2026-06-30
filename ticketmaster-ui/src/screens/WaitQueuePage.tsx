import { useMutation, useQuery } from '@tanstack/react-query';
import type { AxiosError } from 'axios';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useEffect } from 'react';
import { http } from '../api/http';
import { getApiErrorMessage } from '../api/errors';
import type { ApiResponse, QueueAccessDTO } from '../api/types';
import { useAuthStore } from '../ui/authStore';

type AccessResponse = ApiResponse<QueueAccessDTO>;

export default function WaitQueuePage() {
  const { eventId } = useParams();
  const navigate = useNavigate();
  const { token, userType, clearAuth } = useAuthStore();

  const joinMutation = useMutation({
    mutationFn: async () => {
      if (!eventId) throw new Error('Event ID is missing.');
      try {
        const res = await http.post<AccessResponse>(
          `/api/active-orders/access/${eventId}`
        );
        if (res.data.error) throw new Error(res.data.error);
        if (!res.data.data) throw new Error('Failed to request access.');
        return res.data.data;
      } catch (e) {
        const err = e as AxiosError<AccessResponse>;
        const status = err.response?.status;

        if (status === 401) {
          clearAuth();
          throw new Error('Your session expired. Please log in again.');
        }

        throw new Error(
          getApiErrorMessage<QueueAccessDTO>(e, {
            fallback: 'Failed to join the queue.',
            serverFallback:
              'Queue is currently unavailable due to a server issue.',
          })
        );
      }
    },
  });

  const accessQuery = useQuery({
    queryKey: ['queue-access', eventId, token],
    queryFn: async () => {
      if (!eventId) throw new Error('Event ID is missing.');

      try {
        const res = await http.post<AccessResponse>(`/api/active-orders/access/${eventId}`);
        if (res.data.error) throw new Error(res.data.error);
        if (!res.data.data) throw new Error('Queue access is unavailable.');
        return res.data.data;
      } catch (e) {
        const err = e as AxiosError<AccessResponse>;
        const status = err.response?.status;

        if (status === 401) {
          clearAuth();
          throw new Error('Your session expired. Please log in again.');
        }

        throw new Error(
          getApiErrorMessage<QueueAccessDTO>(e, {
            fallback: 'Failed to load queue status.',
            serverFallback: 'Queue status is currently unavailable due to a server issue.',
          })
        );
      }
    },
    enabled: Boolean(eventId) && Boolean(token),
    refetchInterval: 2000,
  });

  if (userType !== 'member') {
    return (
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">Queue</h1>
        <p className="mt-2 text-slate-600">Log in as a member to use the queue.</p>
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
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">Queue</h1>
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

  const access = accessQuery.data;

  useEffect(() => {
    if (!eventId) return;
    if (!access) return;
    if (access.status === 'ADMITTED' || access.status === 'NO_QUEUE') {
      navigate(`/events/${eventId}`, { replace: true });
    }
  }, [access, eventId, navigate]);

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">Queue</h1>
        <p className="mt-1 text-sm text-slate-600">Wait here while we reserve your spot.</p>
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div className="text-sm font-semibold text-slate-900">Status</div>
          <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">
            {eventId}
          </div>
        </div>

        {accessQuery.isLoading && (
          <div className="mt-4 text-sm text-slate-600">Loading queue status…</div>
        )}

        {accessQuery.error && (
          <div className="mt-4 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {String(accessQuery.error instanceof Error ? accessQuery.error.message : accessQuery.error)}
          </div>
        )}

        {access && (
          <div className="mt-4 space-y-2">
            {access.status === 'NO_QUEUE' && (
              <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-900">
                No queue is active for this event. You can continue normally.
              </div>
            )}

            {access.status === 'WAITING' && (
              <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900">
                You are waiting in line. Your position is {access.position ?? 'unknown'}.
              </div>
            )}

            {access.status === 'ADMITTED' && (
              <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-900">
                You are admitted. Access expires at {access.accessExpiresAt ?? 'unknown'}.
              </div>
            )}
          </div>
        )}

        <div className="mt-6 flex flex-wrap gap-2">
          <button
            onClick={() => joinMutation.mutate()}
            disabled={joinMutation.isPending || !eventId}
            className="rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
          >
            {joinMutation.isPending ? 'Joining…' : 'Join / Refresh my spot'}
          </button>

          <button
            onClick={() => accessQuery.refetch()}
            disabled={accessQuery.isFetching}
            className="rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-900 hover:bg-slate-50 disabled:opacity-60"
          >
            Refresh status
          </button>

          <Link
            to={eventId ? `/events/${eventId}` : '/events'}
            className="rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-900 hover:bg-slate-50"
          >
            Back to event
          </Link>
        </div>

        {joinMutation.error && (
          <div className="mt-4 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {String(joinMutation.error instanceof Error ? joinMutation.error.message : joinMutation.error)}
          </div>
        )}

        {joinMutation.data && (
          <div className="mt-4 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
            Joined queue: {joinMutation.data.status}
          </div>
        )}
      </div>
    </div>
  );
}
