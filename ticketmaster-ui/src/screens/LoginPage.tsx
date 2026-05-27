import { useMutation } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { http } from '../api/http';
import type { ApiResponse } from '../api/types';
import { useAuthStore } from '../ui/authStore';
import { useState } from 'react';

type LoginResponse = ApiResponse<string>;

export default function LoginPage() {
  const nav = useNavigate();
  const { setAuth } = useAuthStore();

  const [mode, setMode] = useState<'member' | 'system-admin'>('member');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');

  const login = useMutation({
    mutationFn: async () => {
      const path = mode === 'member' ? '/api/users/login' : '/api/users/login/system-admin';
      const res = await http.post<LoginResponse>(path, { username, password });
      if (res.data.error) throw new Error(res.data.error);
      if (!res.data.data) throw new Error('No token returned');
      return res.data.data;
    },
    onSuccess: (token) => {
      setAuth(token, mode === 'member' ? 'member' : 'system-admin');
      nav('/');
    },
  });

  return (
    <div className="mx-auto max-w-lg">
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">Login</h1>

        <div className="mt-5 flex rounded-xl bg-slate-100 p-1">
          <button
            type="button"
            onClick={() => setMode('member')}
            className={
              mode === 'member'
                ? 'flex-1 rounded-lg bg-white px-3 py-2 text-sm font-semibold text-slate-900 shadow-sm'
                : 'flex-1 rounded-lg px-3 py-2 text-sm font-medium text-slate-700 hover:bg-white/60'
            }
          >
            User
          </button>
          <button
            type="button"
            onClick={() => setMode('system-admin')}
            className={
              mode === 'system-admin'
                ? 'flex-1 rounded-lg bg-white px-3 py-2 text-sm font-semibold text-slate-900 shadow-sm'
                : 'flex-1 rounded-lg px-3 py-2 text-sm font-medium text-slate-700 hover:bg-white/60'
            }
          >
            Admin
          </button>
        </div>

        <div className="mt-5 space-y-4">
          <label className="block">
            <div className="text-sm font-medium text-slate-700">Username</div>
            <input
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm"
            />
          </label>
          <label className="block">
            <div className="text-sm font-medium text-slate-700">Password</div>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm"
            />
          </label>

          <button
            onClick={() => login.mutate()}
            disabled={login.isPending}
            className="w-full rounded-md bg-slate-900 px-4 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-slate-800 disabled:opacity-60"
          >
            {login.isPending ? 'Logging in…' : 'Login'}
          </button>

          {login.isError && <div className="text-sm text-red-600">{(login.error as Error).message}</div>}
        </div>
      </div>
    </div>
  );
}
