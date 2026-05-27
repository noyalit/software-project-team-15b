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

  const filtered = (search.data ?? []).filter((e) => {
    const hay = `${e.name} ${e.artist} ${e.location}`.toLowerCase();
    return hay.includes(query.toLowerCase());
  });

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
        <div>
          <h1 className="text-xl font-bold">Events</h1>
          <p className="text-sm text-white/70">Search results powered by `POST /api/events/search`.</p>
        </div>
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Filter by name, artist, location"
          className="w-full rounded-md border border-white/10 bg-white/5 px-3 py-2 text-sm text-white placeholder:text-white/40 md:w-80"
        />
      </div>

      {search.isPending && <div className="text-white/70">Loading…</div>}
      {search.isError && <div className="text-red-300">{(search.error as Error).message}</div>}

      <div className="grid gap-4 md:grid-cols-2">
        {filtered.map((e) => (
          <Link
            key={e.eventId}
            to={`/events/${e.eventId}`}
            className="rounded-2xl border border-white/10 bg-white/5 p-5 hover:bg-white/10"
          >
            <div className="flex items-start justify-between gap-4">
              <div>
                <div className="text-sm text-white/60">{new Date(e.startsAt).toLocaleString()}</div>
                <div className="mt-1 text-lg font-semibold">{e.name}</div>
                <div className="mt-1 text-sm text-white/70">{e.artist}</div>
                <div className="mt-2 text-sm text-white/60">{e.location}</div>
              </div>
              <div className="rounded-full bg-white/10 px-3 py-1 text-xs text-white/80">{e.status}</div>
            </div>
          </Link>
        ))}
      </div>
    </div>
  );
}
