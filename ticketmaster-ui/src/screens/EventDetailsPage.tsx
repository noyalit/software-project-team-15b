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

  if (eventQuery.isPending) return <div className="text-slate-600">Loading…</div>;
  if (eventQuery.isError) return <div className="text-red-300">{(eventQuery.error as Error).message}</div>;

  const e = eventQuery.data;

  return (
    <div className="space-y-4">
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex flex-col gap-2 md:flex-row md:items-start md:justify-between">
          <div>
            <div className="text-sm text-slate-500">{new Date(e.startsAt).toLocaleString()}</div>
            <h1 className="mt-1 text-2xl font-bold">{e.name}</h1>
            <div className="mt-1 text-slate-700">{e.artist}</div>
            <div className="mt-2 text-sm text-slate-500">{e.location}</div>
          </div>
          <div className="flex items-center gap-2">
            <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-700">{e.status}</span>
            <Link
              to={`/companies/${e.companyId}`}
              className="rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-700 hover:bg-slate-200"
            >
              Company
            </Link>
          </div>
        </div>
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h2 className="text-lg font-semibold">Areas</h2>
        <p className="mt-1 text-sm text-slate-600">
          Areas come from `EventDTO.areas`. Next step: connect to the seat picker and active order flow.
        </p>
        <div className="mt-4 grid gap-3 md:grid-cols-2">
          {e.areas?.map((a) => (
            <div key={a.areaId} className="rounded-xl border border-slate-200 bg-slate-50 p-4">
              <div className="font-semibold">{String(a.name)}</div>
              <div className="mt-1 text-xs text-slate-500">areaId: {a.areaId}</div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
