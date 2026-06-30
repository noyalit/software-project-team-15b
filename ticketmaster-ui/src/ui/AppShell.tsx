import { useEffect, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { http } from '../api/http';
import { ensureGuestToken } from '../api/bootstrap';
import type { ApiResponse, MemberDTO } from '../api/types';
import { useAuthStore } from '../ui/authStore';
// import logo from '../assets/Ticket4U_logo.jpeg';
import logo from '../assets/Ticket4U_logo.png';
import NotificationsBell from './NotificationsBell';
import { connectNotifications, disconnectNotifications } from './notificationsClient';

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
  const nav = useNavigate();

  const [darkMode, setDarkMode] = useState<boolean>(() => {
    const saved = localStorage.getItem('theme');
    if (saved === 'dark') return true;
    if (saved === 'light') return false;
    return window.matchMedia?.('(prefers-color-scheme: dark)')?.matches ?? false;
  });

  const [a11yOpen, setA11yOpen] = useState(false);
  const [highContrast, setHighContrast] = useState<boolean>(() => localStorage.getItem('a11y-high-contrast') === '1');
  const [largeText, setLargeText] = useState<boolean>(() => localStorage.getItem('a11y-large-text') === '1');
  const [reduceMotion, setReduceMotion] = useState<boolean>(() => {
    const saved = localStorage.getItem('a11y-reduce-motion');
    if (saved === '1') return true;
    if (saved === '0') return false;
    return window.matchMedia?.('(prefers-reduced-motion: reduce)')?.matches ?? false;
  });

  useEffect(() => {
    const root = document.documentElement;
    root.classList.toggle('dark', darkMode);
    localStorage.setItem('theme', darkMode ? 'dark' : 'light');
  }, [darkMode]);

  useEffect(() => {
    const root = document.documentElement;
    root.classList.toggle('a11y-high-contrast', highContrast);
    root.classList.toggle('a11y-large-text', largeText);
    root.classList.toggle('a11y-reduce-motion', reduceMotion);
    localStorage.setItem('a11y-high-contrast', highContrast ? '1' : '0');
    localStorage.setItem('a11y-large-text', largeText ? '1' : '0');
    localStorage.setItem('a11y-reduce-motion', reduceMotion ? '1' : '0');
  }, [highContrast, largeText, reduceMotion]);

  const themeToggleLabel = useMemo(() => (darkMode ? 'Light mode' : 'Dark mode'), [darkMode]);

  useQuery({
    queryKey: ['enter-system'],
    queryFn: async () => {
      const newToken = await ensureGuestToken();
      if (!newToken) throw new Error('No token returned');
      return newToken;
    },
    enabled: !token,
    staleTime: Infinity,
    retry: 1,
  });

  useEffect(() => {
    if (token && userType === 'temp') {
      nav('/site-queue', { replace: true });
    }
  }, [nav, token, userType]);

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

  useEffect(() => {
    if (userType !== 'member') {
      disconnectNotifications();
      return;
    }

    const userId = meQuery.data?.userId;
    if (!userId) return;
    connectNotifications(userId, meQuery.data?.assignedRoles);

    return () => {
      disconnectNotifications();
    };
  }, [userType, meQuery.data?.userId, meQuery.data?.assignedRoles]);

  useEffect(() => {
    const handler = () => {
      void disconnectNotifications();
    };
    window.addEventListener('beforeunload', handler);
    return () => window.removeEventListener('beforeunload', handler);
  }, []);

  const activeRole = meQuery.data?.activeRole;
  const hasManagerAssignment =
    Boolean(meQuery.data?.assignedRoles?.some((r) => r.roleName === 'Manager' && r.eventId));

  const hasCompanyManagerAssignment =
    Boolean(meQuery.data?.assignedRoles?.some((r) => r.roleName === 'CompanyManager' && r.companyId));

  const appointmentApprovedQuery = useQuery({
    queryKey: ['appointment-approved', token, activeRole],
    queryFn: async () => {
      const res = await http.get<ApiResponse<boolean>>('/api/users/roles/approved');
      if (res.data.error) throw new Error(res.data.error);
      return res.data.data ?? false;
    },
    enabled:
      Boolean(token) &&
      userType === 'member' &&
      (activeRole === 'Owner' || activeRole === 'Manager' || activeRole === 'CompanyManager'),
});

const isApprovedAppointment =
  activeRole === 'Founder' || appointmentApprovedQuery.data === true;

const canAccessOwnerPages =
  activeRole === 'Founder' || (activeRole === 'Owner' && isApprovedAppointment)
  || (activeRole === 'CompanyManager' && isApprovedAppointment);

const canAccessManagerPages =
  canAccessOwnerPages || (activeRole === 'Manager' && isApprovedAppointment) || hasManagerAssignment || hasCompanyManagerAssignment;

  const badgeText = (() => {
    if (!token) return null;
    if (userType === 'member') {
      const name = username || meQuery.data?.username;
      return name ? `Hello ${name}!` : 'Hello!';
    }
    if (userType === 'system-admin') return 'Admin';
    return null;
  })();

  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-10 border-b border-slate-200/70 bg-white/80 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center justify-between gap-4 px-4 py-3">
          <Link to="/" className="flex shrink-0 items-center">
            <img src={logo} alt="Ticket4U" className="h-14 w-auto shrink-0 object-contain" />
            <span className="sr-only">Ticket4U</span>
          </Link>

          <nav className="flex items-center gap-1">
            {userType !== 'system-admin' && <NavLink to="/events" label="Discover Events" />}
            {userType === 'member' && <NavLink to="/companies/me" label="My Companies" />}
            {userType === 'member' && canAccessManagerPages  && (
                <NavLink to="/my-events" label="My Events" />
            )}
            {userType === 'member' && canAccessOwnerPages && (
                <NavLink to="/company-orders" label="Company Orders" />
            )}
            {userType === 'member' && canAccessOwnerPages  && (
                <NavLink to="/company-sales" label="Sales Report" />
            )}

            {userType === 'member' &&
              (activeRole === 'Founder' || activeRole === 'Owner') &&
              canAccessOwnerPages && (
                <NavLink to="/hierarchy-report" label="Hierarchy Report" />
            )}
            {userType === 'member' && <NavLink to="/orders" label="Orders" />}
            {userType === 'member' && <NavLink to="/me" label="Profile" />}
            {userType === 'system-admin' && <NavLink to="/admin/queues" label="Site Queue" />}
            {userType === 'system-admin' && <NavLink to="/admin/event-queues" label="Event Queues" />}
            {userType === 'system-admin' && <NavLink to="/admin/companies" label="Companies" />}
            {userType === 'system-admin' && <NavLink to="/admin/orders" label="Orders" />}
          </nav>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => setDarkMode((v) => !v)}
              className="rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-900 hover:bg-slate-50"
              aria-label={themeToggleLabel}
              title={themeToggleLabel}
            >
              {darkMode ? 'Light' : 'Dark'}
            </button>

            <div className="relative">
              <button
                type="button"
                onClick={() => setA11yOpen((v) => !v)}
                className="rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-900 hover:bg-slate-50"
                aria-expanded={a11yOpen}
                aria-haspopup="menu"
              >
                Accessibility
              </button>

              {a11yOpen && (
                <div
                  role="menu"
                  className="absolute right-0 mt-2 w-64 rounded-xl border border-slate-200 bg-white p-3 shadow-lg"
                >
                  <label className="flex items-center justify-between gap-3 text-sm text-slate-800">
                    <span>High contrast</span>
                    <input
                      type="checkbox"
                      checked={highContrast}
                      onChange={(e) => setHighContrast(e.target.checked)}
                    />
                  </label>

                  <label className="mt-2 flex items-center justify-between gap-3 text-sm text-slate-800">
                    <span>Large text</span>
                    <input
                      type="checkbox"
                      checked={largeText}
                      onChange={(e) => setLargeText(e.target.checked)}
                    />
                  </label>

                  <label className="mt-2 flex items-center justify-between gap-3 text-sm text-slate-800">
                    <span>Reduce motion</span>
                    <input
                      type="checkbox"
                      checked={reduceMotion}
                      onChange={(e) => setReduceMotion(e.target.checked)}
                    />
                  </label>

                  <button
                    type="button"
                    onClick={() => setA11yOpen(false)}
                    className="mt-3 w-full rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800"
                  >
                    Done
                  </button>
                </div>
              )}
            </div>
            {token && userType !== 'guest' ? (
              <>
                {userType === 'member' && <NotificationsBell />}
                {badgeText && (
                  <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-700">
                    {badgeText}
                  </span>
                )}
                <button
                  onClick={async () => {
                    localStorage.removeItem('activeOrderId');
                    sessionStorage.removeItem('activeOrderId');
                    await disconnectNotifications();
                    await new Promise((r) => setTimeout(r, 150));
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
