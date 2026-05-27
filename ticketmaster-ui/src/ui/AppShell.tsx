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
          ? 'rounded-md bg-white/10 px-3 py-2 text-sm font-semibold'
          : 'rounded-md px-3 py-2 text-sm text-white/80 hover:bg-white/5'
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
      <header className="sticky top-0 z-10 border-b border-white/10 bg-[#0b1220]/90 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-4 py-3">
          <Link to="/" className="text-lg font-bold tracking-tight">
            Ticketmaster
          </Link>

          <nav className="flex items-center gap-1">
            <NavLink to="/events" label="Discover" />
            {userType === 'member' && <NavLink to="/companies/me" label="My Companies" />}
            {userType === 'member' && <NavLink to="/me" label="Profile" />}
          </nav>

          <div className="flex items-center gap-2">
            {token ? (
              <>
                <span className="rounded-full bg-white/10 px-3 py-1 text-xs text-white/80">
                  {userType}
                </span>
                <button
                  onClick={logout}
                  className="rounded-md bg-white px-3 py-2 text-sm font-semibold text-[#0b1220] hover:bg-white/90"
                >
                  Logout
                </button>
              </>
            ) : (
              <>
                <NavLink to="/login" label="Login" />
                <Link
                  to="/register"
                  className="rounded-md bg-white px-3 py-2 text-sm font-semibold text-[#0b1220] hover:bg-white/90"
                >
                  Register
                </Link>
              </>
            )}
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-6xl px-4 py-6">
        <Outlet />
      </main>
    </div>
  );
}
