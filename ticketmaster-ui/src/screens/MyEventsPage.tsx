import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { http } from '../api/http';
import type { ApiResponse, CompanyDTO, EventDTO, MemberDTO } from '../api/types';
import { useAuthStore } from '../ui/authStore';

const categories = ['CONCERT', 'SPORTS', 'THEATER', 'CONFERENCE', 'FESTIVAL', 'OTHER'];

export default function MyEventsPage() {
  const qc = useQueryClient();
  const { token, userType } = useAuthStore();

  const [selectedCompanyId, setSelectedCompanyId] = useState('');

  const [name, setName] = useState('');
  const [artist, setArtist] = useState('');
  const [category, setCategory] = useState('');
  const [startsAt, setStartsAt] = useState('');
  const [location, setLocation] = useState('');

  const [editingEventId, setEditingEventId] = useState<string | null>(null);
  const [editName, setEditName] = useState('');
  const [editArtist, setEditArtist] = useState('');
  const [editCategory, setEditCategory] = useState('');
  const [editStartsAt, setEditStartsAt] = useState('');
  const [editLocation, setEditLocation] = useState('');

  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const meQuery = useQuery({
    queryKey: ['me', token],
    queryFn: async () => {
      const res = await http.get<ApiResponse<MemberDTO>>('/api/users/me');
      if (res.data.error) throw new Error(res.data.error);
      if (!res.data.data) throw new Error('No profile data');
      return res.data.data;
    },
    enabled: Boolean(token) && userType === 'member',
  });

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
      if (!category) throw new Error('Category is required.');
      if (!startsAt) throw new Error('Start date is required.');
      if (!location.trim()) throw new Error('Location is required.');

      const body = {
        companyId: selectedCompanyId,
        name: name.trim(),
        artist: artist.trim(),
        category,
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

  const updateEventMutation = useMutation({
    mutationFn: async () => {
      if (!editingEventId) throw new Error('No event selected.');

      const body = {
        name: editName.trim() || null,
        artist: editArtist.trim() || null,
        category: editCategory || null,
        startsAt: editStartsAt ? new Date(editStartsAt).toISOString() : null,
        location: editLocation.trim() || null,
      };

      const res = await http.patch<ApiResponse<null>>(`/api/events/${editingEventId}`, body);
      if (res.data.error) throw new Error(res.data.error);
    },
    onSuccess: async () => {
      setEditingEventId(null);
      setEditName('');
      setEditArtist('');
      setEditCategory('');
      setEditStartsAt('');
      setEditLocation('');
      setSuccessMessage('Event updated successfully.');
      await qc.invalidateQueries({ queryKey: ['company-events', selectedCompanyId] });
    },
  });

  const publishMutation = useMutation({
    mutationFn: async (eventId: string) => {
      const res = await http.post<ApiResponse<null>>(`/api/events/${eventId}/publish`);
      if (res.data.error) throw new Error(res.data.error);
    },
    onSuccess: async () => {
      setSuccessMessage('Event published successfully.');
      await qc.invalidateQueries({ queryKey: ['company-events', selectedCompanyId] });
    },
  });

  const cancelMutation = useMutation({
    mutationFn: async (eventId: string) => {
      const res = await http.post<ApiResponse<null>>(`/api/events/${eventId}/cancel`);
      if (res.data.error) throw new Error(res.data.error);
    },
    onSuccess: async () => {
      setSuccessMessage('Event cancelled successfully.');
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

  if (meQuery.isPending) return <div className="text-slate-600">Loading…</div>;

  if (meQuery.data?.activeRole !== 'Founder' && meQuery.data?.activeRole !== 'Owner') {
    return (
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-extrabold text-slate-900">My Events</h1>
        <p className="mt-2 text-slate-600">
          Only active Owners or Founders can manage events.
        </p>
      </div>
    );
  }

  const actionError =
    createEventMutation.error ||
    updateEventMutation.error ||
    publishMutation.error ||
    cancelMutation.error;

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-extrabold text-slate-900">My Events</h1>
        <p className="mt-1 text-sm text-slate-600">
          Select a company, create events, edit events, publish them, or cancel them.
        </p>
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h2 className="text-lg font-semibold text-slate-900">Select company</h2>

        <select
          value={selectedCompanyId}
          onChange={(e) => {
            setSelectedCompanyId(e.target.value);
            setSuccessMessage(null);
            setEditingEventId(null);
          }}
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
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Event name"
              className="rounded-md border border-slate-200 px-3 py-2 text-sm"
            />

            <input
              value={artist}
              onChange={(e) => setArtist(e.target.value)}
              placeholder="Artist"
              className="rounded-md border border-slate-200 px-3 py-2 text-sm"
            />

            <select
              value={category}
              onChange={(e) => setCategory(e.target.value)}
              className="rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
            >
              <option value="">Select category...</option>
              {categories.map((c) => (
                <option key={c} value={c}>
                  {c.charAt(0) + c.slice(1).toLowerCase()}
                </option>
              ))}
            </select>

            <input
              type="datetime-local"
              value={startsAt}
              onChange={(e) => setStartsAt(e.target.value)}
              className="rounded-md border border-slate-200 px-3 py-2 text-sm"
            />

            <input
              value={location}
              onChange={(e) => setLocation(e.target.value)}
              placeholder="Location"
              className="rounded-md border border-slate-200 px-3 py-2 text-sm md:col-span-2"
            />
          </div>

          <button
            onClick={() => createEventMutation.mutate()}
            disabled={createEventMutation.isPending}
            className="mt-4 w-full rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
          >
            {createEventMutation.isPending ? 'Creating...' : 'Create event'}
          </button>
        </div>
      )}

      {selectedCompanyId && (
        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <h2 className="text-lg font-semibold text-slate-900">Company events</h2>

          {eventsQuery.isPending && (
            <div className="mt-4 text-sm text-slate-600">Loading events…</div>
          )}

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
                    <div className="mt-1 text-xs text-slate-500">
                      {event.category} | {event.startsAt}
                    </div>
                  </div>

                  <div className="flex flex-wrap gap-2">
                    <button
                      onClick={() => {
                        setEditingEventId(event.eventId);
                        setEditName(event.name);
                        setEditArtist(event.artist);
                        setEditCategory(event.category);
                        setEditStartsAt(event.startsAt?.slice(0, 16) ?? '');
                        setEditLocation(event.location);
                      }}
                      className="rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-900 hover:bg-slate-50"
                    >
                      Edit
                    </button>

                    <button
                      onClick={() => publishMutation.mutate(event.eventId)}
                      disabled={publishMutation.isPending}
                      className="rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white disabled:opacity-60"
                    >
                      Publish
                    </button>

                    <button
                      onClick={() => cancelMutation.mutate(event.eventId)}
                      disabled={cancelMutation.isPending}
                      className="rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-sm font-semibold text-rose-900 disabled:opacity-60"
                    >
                      Cancel event
                    </button>
                  </div>
                </div>

                {editingEventId === event.eventId && (
                  <div className="mt-4 rounded-lg border border-slate-200 bg-white p-4">
                    <div className="font-semibold text-slate-900">Edit event</div>

                    <div className="mt-3 grid gap-3 md:grid-cols-2">
                      <input
                        value={editName}
                        onChange={(e) => setEditName(e.target.value)}
                        placeholder="Event name"
                        className="rounded-md border border-slate-200 px-3 py-2 text-sm"
                      />

                      <input
                        value={editArtist}
                        onChange={(e) => setEditArtist(e.target.value)}
                        placeholder="Artist"
                        className="rounded-md border border-slate-200 px-3 py-2 text-sm"
                      />

                      <select
                        value={editCategory}
                        onChange={(e) => setEditCategory(e.target.value)}
                        className="rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
                      >
                        <option value="">Select category...</option>
                        {categories.map((c) => (
                          <option key={c} value={c}>
                            {c.charAt(0) + c.slice(1).toLowerCase()}
                          </option>
                        ))}
                      </select>

                      <input
                        type="datetime-local"
                        value={editStartsAt}
                        onChange={(e) => setEditStartsAt(e.target.value)}
                        className="rounded-md border border-slate-200 px-3 py-2 text-sm"
                      />

                      <input
                        value={editLocation}
                        onChange={(e) => setEditLocation(e.target.value)}
                        placeholder="Location"
                        className="rounded-md border border-slate-200 px-3 py-2 text-sm md:col-span-2"
                      />
                    </div>

                    <div className="mt-3 flex gap-2">
                      <button
                        onClick={() => updateEventMutation.mutate()}
                        disabled={updateEventMutation.isPending}
                        className="rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white disabled:opacity-60"
                      >
                        {updateEventMutation.isPending ? 'Saving...' : 'Save changes'}
                      </button>

                      <button
                        onClick={() => setEditingEventId(null)}
                        className="rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-900"
                      >
                        Cancel edit
                      </button>
                    </div>
                  </div>
                )}
              </div>
            ))}
          </div>

          {successMessage && (
            <div className="mt-3 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
              {successMessage}
            </div>
          )}

          {actionError && (
            <div className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
              {(actionError as Error).message}
            </div>
          )}
        </div>
      )}
    </div>
  );
}