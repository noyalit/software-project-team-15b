import { useMutation } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { http } from '../api/http';
import type { ApiResponse, MemberDTO } from '../api/types';
import { useState } from 'react';

type RegisterResponse = ApiResponse<MemberDTO>;

export default function RegisterPage() {
  const nav = useNavigate();
  const [entranceToken, setEntranceToken] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [birthDate, setBirthDate] = useState('');

  const register = useMutation({
    mutationFn: async () => {
      const res = await http.post<RegisterResponse>(
        '/api/users/register',
        { username, password, birthDate },
        { headers: { Authorization: entranceToken } }
      );
      if (res.data.error) throw new Error(res.data.error);
      if (!res.data.data) throw new Error('No member returned');
      return res.data.data;
    },
    onSuccess: () => {
      nav('/login');
    },
  });

  return (
    <div className="mx-auto max-w-lg rounded-2xl border border-white/10 bg-white/5 p-6">
      <h1 className="text-xl font-bold">Register</h1>
      <p className="mt-1 text-sm text-white/70">Register also requires an entrance token from `POST /api/users/enter`.</p>

      <div className="mt-4 space-y-3">
        <label className="block">
          <div className="text-sm text-white/70">Entrance token</div>
          <input
            value={entranceToken}
            onChange={(e) => setEntranceToken(e.target.value)}
            className="mt-1 w-full rounded-md border border-white/10 bg-white/5 px-3 py-2 text-sm text-white"
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
        <label className="block">
          <div className="text-sm text-white/70">Birth date</div>
          <input
            value={birthDate}
            onChange={(e) => setBirthDate(e.target.value)}
            placeholder="YYYY-MM-DD"
            className="mt-1 w-full rounded-md border border-white/10 bg-white/5 px-3 py-2 text-sm text-white"
          />
        </label>

        <button
          onClick={() => register.mutate()}
          disabled={register.isPending}
          className="w-full rounded-md bg-white px-4 py-2 text-sm font-semibold text-[#0b1220] hover:bg-white/90 disabled:opacity-60"
        >
          {register.isPending ? 'Creating…' : 'Create account'}
        </button>

        {register.isError && <div className="text-sm text-red-300">{(register.error as Error).message}</div>}
      </div>
    </div>
  );
}
