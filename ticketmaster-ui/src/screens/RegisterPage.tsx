import { useMutation } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { http } from '../api/http';
import type { ApiResponse, MemberDTO } from '../api/types';
import { useState } from 'react';

type RegisterResponse = ApiResponse<MemberDTO>;

export default function RegisterPage() {
  const nav = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [birthDate, setBirthDate] = useState('');

  const register = useMutation({
    mutationFn: async () => {
      const res = await http.post<RegisterResponse>('/api/users/register', { username, password, birthDate });
      if (res.data.error) throw new Error(res.data.error);
      if (!res.data.data) throw new Error('No member returned');
      return res.data.data;
    },
    onSuccess: () => {
      nav('/login');
    },
  });

  return (
    <div className="mx-auto max-w-lg">
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">Register</h1>

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
          <div className="mt-1 text-xs text-slate-500">
            Must include at least 1 uppercase letter and 1 number (e.g. <code>Password1</code>).
          </div>
        </label>
        <label className="block">
          <div className="text-sm font-medium text-slate-700">Birth date</div>
          <input
            value={birthDate}
            onChange={(e) => setBirthDate(e.target.value)}
            placeholder="YYYY-MM-DD"
            className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm placeholder:text-slate-400"
          />
        </label>

        <button
          onClick={() => register.mutate()}
          disabled={register.isPending}
          className="w-full rounded-md bg-slate-900 px-4 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-slate-800 disabled:opacity-60"
        >
          {register.isPending ? 'Creating…' : 'Create account'}
        </button>

        {register.isError && <div className="text-sm text-red-600">{(register.error as Error).message}</div>}
      </div>
      </div>
    </div>
  );
}
