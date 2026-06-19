import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { http } from '../api/http';
import type { ApiResponse, EventDTO } from '../api/types';
import { useEffect, useState } from 'react';

export default function EventSearchPage() {
  const [name, setName] = useState('');
  const [artist, setArtist] = useState('');
  const [location, setLocation] = useState('');
  const [category, setCategory] = useState('');
  const [dateFrom, setDateFrom] = useState('');
  const [dateTo, setDateTo] = useState('');
  const [priceMin, setPriceMin] = useState('');
  const [priceMax, setPriceMax] = useState('');
  const [sortBy, setSortBy] = useState<'dateAsc' | 'dateDesc' | 'nameAsc' | 'nameDesc'>('dateAsc');

  const search = useQuery({
    queryKey: ['events', 'search', name, artist, location, category, dateFrom, dateTo, priceMin, priceMax],
    queryFn: async () => {
      const criteria = {
        name: name.trim() || null,
        artist: artist.trim() || null,
        location: location.trim() || null,
        category: category.trim() || null,
        dateFrom: dateFrom ? new Date(dateFrom).toISOString() : null,
        dateTo: dateTo ? new Date(dateTo).toISOString() : null,
        priceMin: priceMin ? Number(priceMin) : null,
        priceMax: priceMax ? Number(priceMax) : null,
      };

      const res = await http.post<ApiResponse<EventDTO[]>>('/api/events/search', criteria);
      if (res.data.error) throw new Error(res.data.error);
      return res.data.data ?? [];
    },
  });

  useEffect(() => {
    // ensure we load once even if user doesn't touch filters
    // react-query will already run the query
  }, []);

  const pickMinBasePrice = (e: EventDTO) => {
    const prices = (e.areas ?? [])
      .map((a) => {
        const raw = a.basePrice?.amount;
        const n = raw == null ? NaN : Number(raw);
        return Number.isFinite(n) ? n : NaN;
      })
      .filter((n) => Number.isFinite(n));
    return prices.length === 0 ? null : Math.min(...prices);
  };

  const publishedEvents = (search.data ?? []).filter(
    (e) => e.status === 'PUBLISHED'
  );

  const sorted = [...publishedEvents].sort((a, b) => {
    if (sortBy === 'nameAsc') return a.name.localeCompare(b.name);
    if (sortBy === 'nameDesc') return b.name.localeCompare(a.name);
    const da = new Date(a.startsAt).getTime();
    const db = new Date(b.startsAt).getTime();
    if (sortBy === 'dateDesc') return db - da;
    return da - db;
  });

  return (
    <div className="space-y-4">
      <div className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
        <div className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
          <div>
            <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">Discover Events</h1>
            <div className="mt-1 text-sm text-slate-600">Search and filter the global catalog</div>
          </div>

          <div className="flex flex-wrap items-end gap-2">
            <label className="text-sm font-semibold text-slate-700">
              Sort
              <select
                value={sortBy}
                onChange={(e) => setSortBy(e.target.value as typeof sortBy)}
                className="mt-1 w-44 rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
              >
                <option value="dateAsc">Date (soonest)</option>
                <option value="dateDesc">Date (latest)</option>
                <option value="nameAsc">Name (A-Z)</option>
                <option value="nameDesc">Name (Z-A)</option>
              </select>
            </label>

            <button
              onClick={() => {
                setName('');
                setArtist('');
                setLocation('');
                setCategory('');
                setDateFrom('');
                setDateTo('');
                setPriceMin('');
                setPriceMax('');
                setSortBy('dateAsc');
              }}
              className="rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-800 hover:bg-slate-50"
            >
              Clear
            </button>
          </div>
        </div>

        <div className="mt-4 grid gap-3 md:grid-cols-4">
          <label className="block text-sm font-semibold text-slate-700">
            Event name
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="e.g. Coldplay"
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
            />
          </label>

          <label className="block text-sm font-semibold text-slate-700">
            Artist
            <input
              value={artist}
              onChange={(e) => setArtist(e.target.value)}
              placeholder="e.g. Adele"
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
            />
          </label>

          <label className="block text-sm font-semibold text-slate-700">
            Location
            <input
              value={location}
              onChange={(e) => setLocation(e.target.value)}
              placeholder="City / venue"
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
            />
          </label>

          <label className="block text-sm font-semibold text-slate-700">
            Category
            <input
              value={category}
              onChange={(e) => setCategory(e.target.value)}
              placeholder="e.g. MUSIC"
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
            />
          </label>

          <label className="block text-sm font-semibold text-slate-700">
            Date from
            <input
              type="date"
              value={dateFrom}
              onChange={(e) => setDateFrom(e.target.value)}
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
            />
          </label>

          <label className="block text-sm font-semibold text-slate-700">
            Date to
            <input
              type="date"
              value={dateTo}
              onChange={(e) => setDateTo(e.target.value)}
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
            />
          </label>

          <label className="block text-sm font-semibold text-slate-700">
            Min price
            <input
              type="number"
              inputMode="decimal"
              min={0}
              value={priceMin}
              onChange={(e) => setPriceMin(e.target.value)}
              placeholder="0"
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
            />
          </label>

          <label className="block text-sm font-semibold text-slate-700">
            Max price
            <input
              type="number"
              inputMode="decimal"
              min={0}
              value={priceMax}
              onChange={(e) => setPriceMax(e.target.value)}
              placeholder="500"
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
            />
          </label>
        </div>
      </div>

      {search.isPending && <div className="text-slate-600">Loading…</div>}
      {search.isError && <div className="text-red-300">{(search.error as Error).message}</div>}

      {!search.isPending && !search.isError && sorted.length === 0 ? (
        <div className="rounded-2xl border border-slate-200 bg-white p-6 text-slate-600 shadow-sm">
          no events are currently available
        </div>
      ) : (
        <div className="grid gap-4 md:grid-cols-2">
          {sorted.map((e: EventDTO) => (
            <Link
              key={e.eventId}
              to={`/events/${e.eventId}`}
              className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm hover:border-slate-300"
            >
              <div className="flex items-start justify-between gap-4">
                <div>
                  <div className="text-sm text-slate-500">{new Date(e.startsAt).toLocaleString()}</div>
                  <div className="mt-1 text-lg font-semibold text-slate-900">{e.name}</div>
                  <div className="mt-1 text-sm text-slate-600">{e.artist}</div>
                  <div className="mt-2 text-sm text-slate-500">{e.location}</div>
                  {(() => {
                    const p = pickMinBasePrice(e);
                    if (p == null) return null;
                    return (
                      <div className="mt-2 text-xs font-semibold text-slate-700">
                        From {p}
                      </div>
                    );
                  })()}
                </div>
                <div className="rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-700">{e.status}</div>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
