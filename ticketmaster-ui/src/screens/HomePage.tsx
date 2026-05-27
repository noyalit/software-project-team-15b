import { Link } from 'react-router-dom';

export default function HomePage() {
  return (
    <div className="space-y-6">
      <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-3xl font-extrabold tracking-tight text-slate-900">Discover Events</h1>
        <p className="mt-2 max-w-2xl text-slate-600">
          Browse events, view details, and purchase tickets. If you log in as a member, you can also manage companies and
          events based on your assigned roles.
        </p>
        <div className="mt-4 flex gap-3">
          <Link
            to="/events"
            className="rounded-md bg-indigo-600 px-4 py-2 text-sm font-semibold text-white hover:bg-indigo-500"
          >
            Browse events
          </Link>
          <Link
            to="/login"
            className="rounded-md px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-100"
          >
            Login
          </Link>
        </div>
      </section>

      <section className="grid gap-4 md:grid-cols-2">
        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <h2 className="text-lg font-semibold text-slate-900">Buying flow</h2>
          <p className="mt-2 text-sm text-slate-600">
            Search events, open an event page, and continue to checkout. (Seat selection & checkout screens can be added
            next.)
          </p>
        </div>
        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <h2 className="text-lg font-semibold text-slate-900">Management flow</h2>
          <p className="mt-2 text-sm text-slate-600">
            Members can open “My Companies” and drill into company events (powered by your new /api/companies/me endpoint).
          </p>
        </div>
      </section>
    </div>
  );
}
