import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { http } from '../api/http';
import type { ApiResponse, CompanyDTO, EventDTO } from '../api/types';
import { useAuthStore } from '../ui/authStore';

type CreateEventRequest = {
  companyId: string;
  name: string;
  artist: string;
  category: string;
  startsAt: string;
  location: string;
};

export default function MyEventsPage() {
  const qc = useQueryClient();
  const { token, userType } = useAuthStore();

  const [selectedCompanyId, setSelectedCompanyId] = useState('');
  const [name, setName] = useState('');
  const [artist, setArtist] = useState('');
  const [category, setCategory] = useState('');
  const [startsAt, setStartsAt] = useState('');
  const [location, setLocation] = useState('');
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const companiesQuery = useQuery({
    queryKey: ['companies', 'me', token],
    queryFn: async () => {
      const res = await http.get<ApiResponse<CompanyDTO[]>>('/api/companies/me');
      if (res.data.error) throw new Error(res.data.error);
      return res.data.data ?? [];
    },
    enabled: Boolean(token) && userType === 'member',
  });

  const eventsQuery = useQuery({
    queryKey: ['company-events', selectedCompanyId],
    queryFn: async () => {
      const res = await http.get<ApiResponse<EventDTO[]>>(
        `/api/companies/${selectedCompanyId}/events`
      );
      if (res.data.error) throw new Error(res.data.error);
      return res.data.data ?? [];
    },
    enabled: Boolean(selectedCompanyId),
  });

  const createEventMutation = useMutation({
    mutationFn: async () => {
      setSuccessMessage(null);

      if (!selectedCompanyId) throw new Error('Please select a company.');
      if (!name.trim()) throw new Error('Event name is required.');
      if (!artist.trim()) throw new Error('Artist is required.');
      if (!category.trim()) throw new Error('Category is required.');
      if (!startsAt) throw new Error('Start date is required.');
      if (!location.trim()) throw new Error('Location is required.');

      const body = {
        companyId: selectedCompanyId,
        name: name.trim(),
        artist: artist.trim(),
        category: category.trim(),
        startsAt: new Date(startsAt).toISOString(),
        location: location.trim(),
        purchasePolicies: [],
        discountPolicies: [],
        };

      const res = await http.post<ApiResponse<string>>('/api/events', body);

      if (res.data.error) throw new Error(res.data.error);
      return res.data.data;
    },

    onSuccess: async () => {
      setName('');
      setArtist('');
      setCategory('');
      setStartsAt('');
      setLocation('');
      setSuccessMessage('Event created successfully.');
      await qc.invalidateQueries({ queryKey: ['company-events', selectedCompanyId] });
    },
  });

  const publishMutation = useMutation({
    mutationFn: async (eventId: string) => {
      const res = await http.post<ApiResponse<null>>(`/api/events/${eventId}/publish`);
      if (res.data.error) throw new Error(res.data.error);
    },
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ['company-events', selectedCompanyId] });
    },
  });

  const cancelMutation = useMutation({
    mutationFn: async (eventId: string) => {
      const res = await http.post<ApiResponse<null>>(`/api/events/${eventId}/cancel`);
      if (res.data.error) throw new Error(res.data.error);
    },
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ['company-events', selectedCompanyId] });
    },
  });

  if (userType !== 'member') {
    return (
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-extrabold text-slate-900">My Events</h1>
        <p className="mt-2 text-slate-600">Log in as a member to manage events.</p>
        <Link to="/login" className="mt-4 inline-flex rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white">
          Go to login
        </Link>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-extrabold text-slate-900">My Events</h1>
        <p className="mt-1 text-sm text-slate-600">
          Select a company, create events, publish them, or cancel them.
        </p>
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h2 className="text-lg font-semibold text-slate-900">Select company</h2>

        <select
          value={selectedCompanyId}
          onChange={(e) => setSelectedCompanyId(e.target.value)}
          className="mt-3 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
        >
          <option value="">Select company...</option>
          {companiesQuery.data?.map((company) => (
            <option key={company.companyId} value={company.companyId}>
              {company.name}
            </option>
          ))}
        </select>
      </div>

      {selectedCompanyId && (
        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <h2 className="text-lg font-semibold text-slate-900">Create event</h2>

          <div className="mt-4 grid gap-3 md:grid-cols-2">
            <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Event name" className="rounded-md border border-slate-200 px-3 py-2 text-sm" />
            <input value={artist} onChange={(e) => setArtist(e.target.value)} placeholder="Artist" className="rounded-md border border-slate-200 px-3 py-2 text-sm" />
            <input value={category} onChange={(e) => setCategory(e.target.value)} placeholder="Category" className="rounded-md border border-slate-200 px-3 py-2 text-sm" />
            <input type="datetime-local" value={startsAt} onChange={(e) => setStartsAt(e.target.value)} className="rounded-md border border-slate-200 px-3 py-2 text-sm" />
            <input value={location} onChange={(e) => setLocation(e.target.value)} placeholder="Location" className="rounded-md border border-slate-200 px-3 py-2 text-sm md:col-span-2" />
          </div>

          <button
            onClick={() => createEventMutation.mutate()}
            disabled={createEventMutation.isPending}
            className="mt-4 w-full rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800"
          >
            {createEventMutation.isPending ? 'Creating...' : 'Create event'}
          </button>

          {createEventMutation.isError && (
            <div className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
              {(createEventMutation.error as Error).message}
            </div>
          )}

          {successMessage && (
            <div className="mt-3 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
              {successMessage}
            </div>
          )}
        </div>
      )}

      {selectedCompanyId && (
        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <h2 className="text-lg font-semibold text-slate-900">Company events</h2>

          <div className="mt-4 grid gap-3">
            {eventsQuery.data?.length === 0 && (
              <div className="text-sm text-slate-600">No events found.</div>
            )}

            {eventsQuery.data?.map((event) => (
              <div key={event.eventId} className="rounded-xl border border-slate-200 bg-slate-50 p-4">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <div className="font-semibold text-slate-900">{event.name}</div>
                    <div className="mt-1 text-sm text-slate-600">
                      {event.artist} | {event.location} | {event.status}
                    </div>
                  </div>

                  <div className="flex gap-2">
                    <button
                      onClick={() => publishMutation.mutate(event.eventId)}
                      className="rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white"
                    >
                      Publish
                    </button>

                    <button
                      onClick={() => cancelMutation.mutate(event.eventId)}
                      className="rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-sm font-semibold text-rose-900"
                    >
                      Cancel
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}