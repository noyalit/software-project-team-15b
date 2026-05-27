import { useMutation } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { http } from '../api/http';
import type { ApiResponse, EventDTO } from '../api/types';
import { useEffect, useState } from 'react';

export default function EventSearchPage() {
  const [query, setQuery] = useState('');

  const search = useMutation({
    mutationFn: async () => {
      // Backend supports POST /api/events/search with criteria.
      // Here we send an empty object for "all" and let you extend it with real criteria fields.
      const res = await http.post<ApiResponse<EventDTO[]>>('/api/events/search', {});
      if (res.data.error) throw new Error(res.data.error);
      return res.data.data ?? [];
    },
  });

  useEffect(() => {
    search.mutate();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const filtered = (search.data ?? []).filter((e: EventDTO) => {
    const hay = `${e.name} ${e.artist} ${e.location}`.toLowerCase();
    return hay.includes(query.toLowerCase());
  });

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
        <div>
          <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">Discover Events</h1>
        </div>
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Filter by name, artist, location"
          className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 placeholder:text-slate-400 shadow-sm md:w-80"
        />
      </div>

      {search.isPending && <div className="text-slate-600">Loading…</div>}
      {search.isError && <div className="text-red-300">{(search.error as Error).message}</div>}

      {!search.isPending && !search.isError && filtered.length === 0 ? (
        <div className="rounded-2xl border border-slate-200 bg-white p-6 text-slate-600 shadow-sm">
          no events are currently available
        </div>
      ) : (
        <div className="grid gap-4 md:grid-cols-2">
          {filtered.map((e: EventDTO) => (
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
