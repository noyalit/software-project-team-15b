import { useEffect, useRef, useState } from 'react';
import axios from 'axios';
import { ensureGuestToken } from '../api/bootstrap';
import type { ApiResponse, EntranceDTO } from '../api/types';
import { useAuthStore } from './authStore';

const POLL_INTERVAL_MS = 2000;

/**
 * Full-screen waiting room shown while this browser holds a site-queue token but no
 * session yet. Polls POST /api/users/enter/poll with the temp token; once the backend
 * reports ADMITTED it stores the issued guest token (which clears the queue state and
 * lets the app render normally).
 *
 * Uses a bare axios call rather than the shared `http` instance: while queued there is no
 * session token to attach, and we must send the temp queue token as the Authorization
 * header explicitly without tripping the 401 re-entry interceptor.
 */
export default function SiteQueueGate() {
  const { queueToken, queuePosition, setAuth, setQueued, clearQueued } = useAuthStore();
  const [error, setError] = useState<string | null>(null);
  const pollingRef = useRef(false);

  useEffect(() => {
    if (!queueToken) return;
    let cancelled = false;

    const poll = async () => {
      if (pollingRef.current) return;
      pollingRef.current = true;
      try {
        const res = await axios.post<ApiResponse<EntranceDTO>>(
          '/api/users/enter/poll',
          null,
          { headers: { Authorization: queueToken } }
        );
        if (cancelled) return;
        const entrance = res.data.data;
        if (res.data.error || !entrance) {
          throw new Error(res.data.error ?? 'Queue poll failed.');
        }
        if (entrance.status === 'ADMITTED') {
          setAuth(entrance.token, 'guest');
        } else {
          setQueued(entrance.token, entrance.position);
          setError(null);
        }
      } catch (e) {
        if (cancelled) return;
        const status = (e as { response?: { status?: number } })?.response?.status;
        if (status === 401) {
          // Queue session expired — drop it and re-enter from scratch.
          clearQueued();
          void ensureGuestToken();
          return;
        }
        setError('Lost contact with the queue. Retrying…');
      } finally {
        pollingRef.current = false;
      }
    };

    void poll();
    const id = window.setInterval(poll, POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      window.clearInterval(id);
    };
  }, [queueToken, setAuth, setQueued, clearQueued]);

  if (!queueToken) return null;

  const positionLabel =
    queuePosition === null || queuePosition < 0 ? '—' : `#${queuePosition + 1}`;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/70 p-4 backdrop-blur-sm">
      <div className="w-full max-w-md rounded-2xl border border-slate-200 bg-white p-8 text-center shadow-xl">
        <div className="mx-auto mb-5 h-12 w-12 animate-spin rounded-full border-4 border-slate-200 border-t-slate-900" />
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">
          You&rsquo;re in line
        </h1>
        <p className="mt-2 text-sm text-slate-600">
          The site is busy right now. We&rsquo;ll let you in automatically as soon as a
          spot opens up — please keep this tab open.
        </p>

        <div className="mt-6 rounded-xl border border-slate-200 bg-slate-50 px-4 py-5">
          <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">
            Your position
          </div>
          <div className="mt-1 text-4xl font-black text-slate-900">{positionLabel}</div>
        </div>

        {error && (
          <div className="mt-4 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900">
            {error}
          </div>
        )}

        <p className="mt-5 text-xs text-slate-400">Checking every couple of seconds…</p>
      </div>
    </div>
  );
}
