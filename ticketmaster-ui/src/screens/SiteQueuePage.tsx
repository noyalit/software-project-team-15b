import { useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { http } from '../api/http';
import type { ApiResponse } from '../api/types';
import { getApiErrorMessage } from '../api/errors';
import { useAuthStore } from '../ui/authStore';

type SiteAccessDTO = {
  admitted: boolean;
  position: number | null;
};

export default function SiteQueuePage() {
  const nav = useNavigate();
  const { token, userType, setAuth, clearAuth } = useAuthStore();

  const accessQuery = useQuery({
    queryKey: ['site-queue-access', token],
    queryFn: async () => {
      try {
        const res = await http.get<ApiResponse<SiteAccessDTO>>('/api/queues/site/access');
        if (res.data.error) throw new Error(res.data.error);
        if (!res.data.data) throw new Error('No access view returned');
        return res.data.data;
      } catch (e) {
        throw new Error(
          getApiErrorMessage<SiteAccessDTO>(e, {
            fallback: 'Failed to load your queue status. Please try again.',
            serverFallback: 'Queues are currently unavailable due to a server issue. Please try again later.',
          })
        );
      }
    },
    enabled: Boolean(token),
    refetchInterval: 3000,
    retry: 1,
  });

  useEffect(() => {
    if (!token) return;
    if (!accessQuery.data) return;

    if (accessQuery.data.admitted) {
      if (userType === 'temp') {
        void (async () => {
          const res = await http.post<ApiResponse<string>>('/api/users/enter-from-queue', null);
          if (res.data.error) throw new Error(res.data.error);
          const newToken = res.data.data;
          if (typeof newToken !== 'string' || !newToken) throw new Error('No token returned');
          setAuth(newToken, 'guest');
          nav('/', { replace: true });
        })();
        return;
      }

      setAuth(token, 'guest');
      nav('/', { replace: true });
    } else {
      if (userType !== 'temp') {
        setAuth(token, 'temp');
      }
    }
  }, [accessQuery.data, nav, setAuth, token, userType]);

  if (!token) {
    return (
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">Site Queue</h1>
        <p className="mt-2 text-slate-600">You are not connected. Please refresh the page.</p>
      </div>
    );
  }

  if (accessQuery.isError) {
    return (
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">Site Queue</h1>
        <p className="mt-2 text-slate-600">{accessQuery.error.message}</p>
        <button
          type="button"
          className="mt-4 rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800"
          onClick={() => {
            clearAuth();
            nav('/', { replace: true });
          }}
        >
          Leave queue
        </button>
      </div>
    );
  }

  const position = accessQuery.data?.position;

  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
      <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">Site Queue</h1>
      <p className="mt-2 text-slate-600">
        The site is currently at capacity. You are waiting to be admitted.
      </p>
      <div className="mt-4 rounded-xl border border-slate-200 bg-slate-50 p-4">
        <div className="text-sm text-slate-600">Your position</div>
        <div className="mt-1 text-xl font-semibold text-slate-900">{position ?? '—'}</div>
      </div>
      <p className="mt-3 text-sm text-slate-500">This page refreshes automatically.</p>
    </div>
  );
}
