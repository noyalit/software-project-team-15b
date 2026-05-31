import { Link } from 'react-router-dom';
import { useAuthStore } from '../ui/authStore';

export default function HomePage() {
  const { token, userType } = useAuthStore();

  const isLoggedIn = Boolean(token) && userType !== 'guest';

  return (
    <div className="space-y-6">
      <section className="relative overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-sm">
        <div className="absolute inset-0 bg-gradient-to-br from-indigo-50 via-white to-sky-50" />
        <div className="relative p-8 md:p-12">
          <div className="max-w-3xl">
            <div className="text-sm font-semibold tracking-wide text-indigo-700">Ticket4U</div>
            <h1 className="mt-2 text-4xl font-extrabold tracking-tight text-slate-900 md:text-5xl">
              Discover events.
              <span className="block text-indigo-700">Get tickets instantly.</span>
            </h1>
            <p className="mt-4 text-base text-slate-600 md:text-lg">
              Browse shows near you, explore event details, and manage your experience in one clean dashboard.
            </p>

            <div className="mt-6 flex flex-wrap gap-3">
              <Link
                to="/events"
                className="rounded-md bg-slate-900 px-4 py-2 text-sm font-semibold text-white hover:bg-slate-800"
              >
                Explore events
              </Link>

              {!isLoggedIn ? (
                <>
                  <Link
                    to="/login"
                    className="rounded-md border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-800 hover:bg-slate-50"
                  >
                    Login
                  </Link>
                  <Link
                    to="/register"
                    className="rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-500"
                  >
                    Create account
                  </Link>
                </>
              ) : userType === 'member' ? (
                <>
                  <Link
                    to="/companies/me"
                    className="rounded-md border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-800 hover:bg-slate-50"
                  >
                    My Companies
                  </Link>
                  <Link
                    to="/me"
                    className="rounded-md border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-800 hover:bg-slate-50"
                  >
                    Profile
                  </Link>
                </>
              ) : userType === 'system-admin' ? (
                <Link
                  to="/admin/queues"
                  className="rounded-md border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-800 hover:bg-slate-50"
                >
                  Admin tools
                </Link>
              ) : null}
            </div>
          </div>

          <div className="mt-10 grid gap-4 md:grid-cols-3">
            <div className="rounded-2xl border border-slate-200 bg-white/70 p-5 backdrop-blur">
              <div className="text-sm font-semibold text-slate-900">Smart discovery</div>
              <div className="mt-1 text-sm text-slate-600">Search by artist, location, and event name in seconds.</div>
            </div>
            <div className="rounded-2xl border border-slate-200 bg-white/70 p-5 backdrop-blur">
              <div className="text-sm font-semibold text-slate-900">Clear details</div>
              <div className="mt-1 text-sm text-slate-600">See areas, status, and venue info with a clean layout.</div>
            </div>
            <div className="rounded-2xl border border-slate-200 bg-white/70 p-5 backdrop-blur">
              <div className="text-sm font-semibold text-slate-900">Role-based access</div>
              <div className="mt-1 text-sm text-slate-600">Members and admins get the tools they need, instantly.</div>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
