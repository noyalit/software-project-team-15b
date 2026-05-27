import { Link, Outlet, useLocation } from 'react-router-dom';
import { useAuthStore } from '../ui/authStore';

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
  const { token, userType, logout } = useAuthStore();

  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-10 border-b border-slate-200/70 bg-white/80 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-4 py-3">
          <Link to="/" className="text-lg font-extrabold tracking-tight text-slate-900">
            Ticket4U
          </Link>

          <nav className="flex items-center gap-1">
            <NavLink to="/events" label="Discover Events" />
            {userType === 'member' && <NavLink to="/companies/me" label="My Companies" />}
            {userType === 'member' && <NavLink to="/me" label="Profile" />}
          </nav>

          <div className="flex items-center gap-2">
            {token ? (
              <>
                <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-700">
                  {userType}
                </span>
                <button
                  onClick={logout}
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
