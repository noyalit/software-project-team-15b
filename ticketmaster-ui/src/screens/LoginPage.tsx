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
  const [entranceToken, setEntranceToken] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');

  const login = useMutation({
    mutationFn: async () => {
      const path = mode === 'member' ? '/api/users/login' : '/api/users/login/system-admin';
      const res = await http.post<LoginResponse>(
        path,
        { username, password },
        { headers: { Authorization: entranceToken } }
      );
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
    <div className="mx-auto max-w-lg rounded-2xl border border-white/10 bg-white/5 p-6">
      <h1 className="text-xl font-bold">Login</h1>
      <p className="mt-1 text-sm text-white/70">
        Login requires an **entrance token** from `POST /api/users/enter`. Paste it here.
      </p>

      <div className="mt-4 flex gap-2">
        <button
          onClick={() => setMode('member')}
          className={
            mode === 'member'
              ? 'rounded-md bg-white px-3 py-2 text-sm font-semibold text-[#0b1220]'
              : 'rounded-md border border-white/10 px-3 py-2 text-sm text-white/80 hover:bg-white/5'
          }
        >
          Member
        </button>
        <button
          onClick={() => setMode('system-admin')}
          className={
            mode === 'system-admin'
              ? 'rounded-md bg-white px-3 py-2 text-sm font-semibold text-[#0b1220]'
              : 'rounded-md border border-white/10 px-3 py-2 text-sm text-white/80 hover:bg-white/5'
          }
        >
          System Admin
        </button>
      </div>

      <div className="mt-4 space-y-3">
        <label className="block">
          <div className="text-sm text-white/70">Entrance token</div>
          <input
            value={entranceToken}
            onChange={(e) => setEntranceToken(e.target.value)}
            className="mt-1 w-full rounded-md border border-white/10 bg-white/5 px-3 py-2 text-sm text-white"
            placeholder="Paste token from /api/users/enter"
          />
        </label>
        <label className="block">
          <div className="text-sm text-white/70">Username</div>
          <input
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            className="mt-1 w-full rounded-md border border-white/10 bg-white/5 px-3 py-2 text-sm text-white"
          />
        </label>
        <label className="block">
          <div className="text-sm text-white/70">Password</div>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="mt-1 w-full rounded-md border border-white/10 bg-white/5 px-3 py-2 text-sm text-white"
          />
        </label>

        <button
          onClick={() => login.mutate()}
          disabled={login.isPending}
          className="w-full rounded-md bg-white px-4 py-2 text-sm font-semibold text-[#0b1220] hover:bg-white/90 disabled:opacity-60"
        >
          {login.isPending ? 'Logging in…' : 'Login'}
        </button>

        {login.isError && <div className="text-sm text-red-300">{(login.error as Error).message}</div>}
      </div>
    </div>
  );
}
