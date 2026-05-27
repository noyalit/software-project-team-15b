import { useQuery } from '@tanstack/react-query';
import { http } from '../api/http';
import type { ApiResponse, MemberDTO } from '../api/types';
import { useAuthStore } from '../ui/authStore';

export default function ProfilePage() {
  const { userType } = useAuthStore();

  const meQuery = useQuery({
    queryKey: ['me'],
    queryFn: async () => {
      const res = await http.get<ApiResponse<MemberDTO>>('/api/users/me');
      if (res.data.error) throw new Error(res.data.error);
      if (!res.data.data) throw new Error('No profile data');
      return res.data.data;
    },
    enabled: userType === 'member',
  });

  if (userType !== 'member') {
    return (
      <div className="rounded-2xl border border-white/10 bg-white/5 p-6">
        <h1 className="text-xl font-bold">Profile</h1>
        <p className="mt-2 text-white/70">Login as a member to view your profile.</p>
      </div>
    );
  }

  if (meQuery.isPending) return <div className="text-white/70">Loading…</div>;
  if (meQuery.isError) return <div className="text-red-300">{(meQuery.error as Error).message}</div>;

  const me = meQuery.data;

  return (
    <div className="space-y-4">
      <div className="rounded-2xl border border-white/10 bg-white/5 p-6">
        <h1 className="text-xl font-bold">Profile</h1>
        <div className="mt-4 grid gap-3 md:grid-cols-2">
          <div className="rounded-xl border border-white/10 bg-black/20 p-4">
            <div className="text-sm text-white/60">Username</div>
            <div className="mt-1 font-semibold">{me.username}</div>
          </div>
          <div className="rounded-xl border border-white/10 bg-black/20 p-4">
            <div className="text-sm text-white/60">User ID</div>
            <div className="mt-1 break-all font-mono text-sm">{me.userId}</div>
          </div>
        </div>
      </div>

      <div className="rounded-2xl border border-white/10 bg-white/5 p-6">
        <h2 className="text-lg font-semibold">Roles</h2>
        <p className="mt-1 text-sm text-white/70">
          This comes from `MemberDTO.assignedRoles` and `MemberDTO.activeRole`.
        </p>
        <div className="mt-3 text-sm text-white/80">Active: {me.activeRole ?? '—'}</div>
        <div className="mt-2 flex flex-wrap gap-2">
          {(me.assignedRoles ?? []).map((r) => (
            <span key={r} className="rounded-full bg-white/10 px-3 py-1 text-xs text-white/80">
              {r}
            </span>
          ))}
        </div>
      </div>
    </div>
  );
}
