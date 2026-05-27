import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { http } from '../api/http';
import type { ApiResponse, EventDTO } from '../api/types';

export default function EventDetailsPage() {
  const { eventId } = useParams();

  const eventQuery = useQuery({
    queryKey: ['event', eventId],
    queryFn: async () => {
      const res = await http.get<ApiResponse<EventDTO>>(`/api/events/${eventId}`);
      if (res.data.error) throw new Error(res.data.error);
      if (!res.data.data) throw new Error('Event not found');
      return res.data.data;
    },
    enabled: !!eventId,
  });

  if (eventQuery.isPending) return <div className="text-white/70">Loading…</div>;
  if (eventQuery.isError) return <div className="text-red-300">{(eventQuery.error as Error).message}</div>;

  const e = eventQuery.data;

  return (
    <div className="space-y-4">
      <div className="rounded-2xl border border-white/10 bg-white/5 p-6">
        <div className="flex flex-col gap-2 md:flex-row md:items-start md:justify-between">
          <div>
            <div className="text-sm text-white/60">{new Date(e.startsAt).toLocaleString()}</div>
            <h1 className="mt-1 text-2xl font-bold">{e.name}</h1>
            <div className="mt-1 text-white/80">{e.artist}</div>
            <div className="mt-2 text-sm text-white/60">{e.location}</div>
          </div>
          <div className="flex items-center gap-2">
            <span className="rounded-full bg-white/10 px-3 py-1 text-xs text-white/80">{e.status}</span>
            <Link
              to={`/companies/${e.companyId}`}
              className="rounded-full bg-white/10 px-3 py-1 text-xs text-white/80 hover:bg-white/15"
            >
              Company
            </Link>
          </div>
        </div>
      </div>

      <div className="rounded-2xl border border-white/10 bg-white/5 p-6">
        <h2 className="text-lg font-semibold">Areas</h2>
        <p className="mt-1 text-sm text-white/70">
          Areas come from `EventDTO.areas`. Next step: connect to the seat picker and active order flow.
        </p>
        <div className="mt-4 grid gap-3 md:grid-cols-2">
          {e.areas?.map((a) => (
            <div key={a.areaId} className="rounded-xl border border-white/10 bg-black/20 p-4">
              <div className="font-semibold">{String(a.name)}</div>
              <div className="mt-1 text-xs text-white/60">areaId: {a.areaId}</div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
