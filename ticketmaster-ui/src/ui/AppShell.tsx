import { useQuery } from '@tanstack/react-query';
import { Link, Outlet, useLocation } from 'react-router-dom';
import { http } from '../api/http';
import type { ApiResponse, MemberDTO } from '../api/types';
import { useAuthStore } from '../ui/authStore';
import logo from '../assets/Ticket4U_logo.jpeg';

function NavLink({ to, label }: { to: string; label: string }) {
  const { pathname } = useLocation();
  const active = pathname === to;
  return (
    <Link
      to={to}
      className={
        active
          ? 'rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white shadow-sm'
          : 'rounded-md px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100'
      }
    >
      {label}
    </Link>
  );
}

export default function AppShell() {
  const { token, userType, username, logout } = useAuthStore();
  const location = useLocation();

  const meQuery = useQuery({
    queryKey: ['me', token],
    queryFn: async () => {
      const res = await http.get<ApiResponse<MemberDTO>>('/api/users/me');
      if (res.data.error) throw new Error(res.data.error);
      if (!res.data.data) throw new Error('No user returned');
      return res.data.data;
    },
    enabled: Boolean(token) && userType === 'member',
    staleTime: 60_000,
  });

  const badgeText = (() => {
    if (!token) return null;
    if (userType === 'member') {
      const name = username || meQuery.data?.username;
      return name ? `Hello ${name}!` : 'Hello!';
    }
    return 'Admin';
  })();

  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-10 border-b border-slate-200/70 bg-white/80 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-4 py-3">
          <Link to="/" className="flex items-center">
            <img src={logo} alt="Ticket4U" className="h-10 w-auto" />
            <span className="sr-only">Ticket4U</span>
          </Link>

          <nav className="flex items-center gap-1">
            {userType !== 'system-admin' && <NavLink to="/events" label="Discover Events" />}
            {userType === 'member' && <NavLink to="/companies/me" label="My Companies" />}
            {userType === 'member' && <NavLink to="/my-events" label="My Events" />}
            {userType === 'member' && <NavLink to="/me" label="Profile" />}
            {userType === 'system-admin' && <NavLink to="/admin/queues" label="Site Queue" />}
            {userType === 'system-admin' && <NavLink to="/admin/event-queues" label="Event Queues" />}
            {userType === 'system-admin' && <NavLink to="/admin/companies" label="Companies" />}
            {userType === 'system-admin' && <NavLink to="/admin/members" label="Members" />}
            {userType === 'system-admin' && <NavLink to="/admin/orders" label="Orders" />}
          </nav>

          <div className="flex items-center gap-2">
            {token ? (
              <>
                {badgeText && (
                  <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-700">
                    {badgeText}
                  </span>
                )}
                <button
                  onClick={() => {
                    logout();
                    window.location.href = '/';
                  }}
                  className="rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800"
                >
                  Logout
                </button>
              </>
            ) : (
              <>
                <NavLink to="/login" label="Login" />
                <Link
                  to="/register"
                  className="rounded-md bg-indigo-600 px-3 py-2 text-sm font-semibold text-white hover:bg-indigo-500"
                >
                  Register
                </Link>
              </>
            )}
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-6xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  );
}
