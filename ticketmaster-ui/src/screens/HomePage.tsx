import { Link } from 'react-router-dom';

export default function HomePage() {
  return (
    <div className="space-y-6">
      <section className="rounded-2xl border border-white/10 bg-white/5 p-6">
        <h1 className="text-2xl font-bold">Discover events</h1>
        <p className="mt-2 max-w-2xl text-white/70">
          Browse events, view details, and purchase tickets. If you log in as a member, you can also manage companies and
          events based on your assigned roles.
        </p>
        <div className="mt-4 flex gap-3">
          <Link
            to="/events"
            className="rounded-md bg-white px-4 py-2 text-sm font-semibold text-[#0b1220] hover:bg-white/90"
          >
            Browse events
          </Link>
          <Link to="/login" className="rounded-md px-4 py-2 text-sm text-white/80 hover:bg-white/5">
            Login
          </Link>
        </div>
      </section>

      <section className="grid gap-4 md:grid-cols-2">
        <div className="rounded-2xl border border-white/10 bg-white/5 p-6">
          <h2 className="text-lg font-semibold">Buying flow</h2>
          <p className="mt-2 text-sm text-white/70">
            Search events, open an event page, and continue to checkout. (Seat selection & checkout screens can be added
            next.)
          </p>
        </div>
        <div className="rounded-2xl border border-white/10 bg-white/5 p-6">
          <h2 className="text-lg font-semibold">Management flow</h2>
          <p className="mt-2 text-sm text-white/70">
            Members can open “My Companies” and drill into company events (powered by your new /api/companies/me endpoint).
          </p>
        </div>
      </section>
    </div>
  );
}
