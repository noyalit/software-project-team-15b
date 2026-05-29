import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { http } from '../api/http';
import { getApiErrorMessage } from '../api/errors';
import type { ApiResponse, CompanyDTO, EventDTO, MemberDTO } from '../api/types';
import { useAuthStore } from '../ui/authStore';

const categories = ['CONCERT', 'SPORTS', 'THEATER', 'CONFERENCE', 'FESTIVAL', 'OTHER'];

type AreaType = 'SEATING' | 'STANDING';

type SeatSpec = {
  row: string;
  number: string;
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

  const [editingEventId, setEditingEventId] = useState<string | null>(null);
  const [editName, setEditName] = useState('');
  const [editArtist, setEditArtist] = useState('');
  const [editCategory, setEditCategory] = useState('');
  const [editStartsAt, setEditStartsAt] = useState('');
  const [editLocation, setEditLocation] = useState('');

  const [areaEventId, setAreaEventId] = useState<string | null>(null);
  const [areaName, setAreaName] = useState('');
  const [areaType, setAreaType] = useState<AreaType>('SEATING');
  const [areaPrice, setAreaPrice] = useState('');
  const [areaCurrency, setAreaCurrency] = useState('ILS');
  const [standingCapacity, setStandingCapacity] = useState('');
  const [seatRows, setSeatRows] = useState('');
  const [seatsPerRow, setSeatsPerRow] = useState('');
  const [openEventDetailsId, setOpenEventDetailsId] = useState<string | null>(null);

  const [editingAreaId, setEditingAreaId] = useState<string | null>(null);
  const [editAreaName, setEditAreaName] = useState('');
  const [editAreaPrice, setEditAreaPrice] = useState('');
  const [editAreaCurrency, setEditAreaCurrency] = useState('ILS');
  const [editStandingCapacity, setEditStandingCapacity] = useState('');

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
      setSuccessMessage('Event updated successfully.');
      await qc.invalidateQueries({ queryKey: ['company-events', selectedCompanyId] });
    },
  });

  const publishMutation = useMutation({
    mutationFn: async (event: EventDTO) => {
        if (!event.areas || event.areas.length === 0) {
        throw new Error('You must add at least one area to the event map before publishing.');
        }

        const res = await http.post<ApiResponse<null>>(
        `/api/events/${event.eventId}/publish`
        );

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

  const addAreaMutation = useMutation({
    mutationFn: async () => {
      if (!areaEventId) throw new Error('No event selected.');
      if (!areaName.trim()) throw new Error('Area name is required.');
      if (!areaPrice.trim()) throw new Error('Base price is required.');

      let seats: SeatSpec[] = [];
      let capacity: number | null = null;

      if (areaType === 'SEATING') {
        const rows = Number(seatRows);
        const perRow = Number(seatsPerRow);

        if (!rows || rows <= 0) throw new Error('Number of rows must be positive.');
        if (!perRow || perRow <= 0) throw new Error('Seats per row must be positive.');

        for (let r = 1; r <= rows; r++) {
          const rowName = String(r);
          for (let s = 1; s <= perRow; s++) {
            seats.push({ row: rowName, number: String(s) });
          }
        }
      } else {
        capacity = Number(standingCapacity);
        if (!capacity || capacity <= 0) {
          throw new Error('Standing capacity must be positive.');
        }
      }

      const body = {
        name: areaName.trim(),
        basePrice: {
          amount: Number(areaPrice),
          currency: areaCurrency.trim() || 'ILS',
        },
        type: areaType,
        standingCapacity: areaType === 'STANDING' ? capacity : undefined,
        seats: areaType === 'SEATING' ? seats : undefined,
      };

      const res = await http.post<ApiResponse<string>>(`/api/events/${areaEventId}/areas`, body);
      if (res.data.error) throw new Error(res.data.error);
      return res.data.data;
    },
    onSuccess: async () => {
      setAreaName('');
      setAreaPrice('');
      setStandingCapacity('');
      setSeatRows('');
      setSeatsPerRow('');
      setSuccessMessage('Area added successfully.');
      await qc.invalidateQueries({ queryKey: ['company-events', selectedCompanyId] });
    },
  });

  const updateAreaMutation = useMutation({
    mutationFn: async () => {
      if (!areaEventId) throw new Error('No event selected.');
      if (!editingAreaId) throw new Error('No area selected.');

      const body = {
        name: editAreaName.trim() || null,
        basePrice: editAreaPrice.trim()
          ? {
              amount: Number(editAreaPrice),
              currency: editAreaCurrency.trim() || 'ILS',
            }
          : null,
        standingCapacity: editStandingCapacity ? Number(editStandingCapacity) : undefined,
      };

      const res = await http.patch<ApiResponse<null>>(
        `/api/events/${areaEventId}/areas/${editingAreaId}`,
        body
      );

      if (res.data.error) throw new Error(res.data.error);
    },
    onSuccess: async () => {
      setEditingAreaId(null);
      setEditAreaName('');
      setEditAreaPrice('');
      setEditStandingCapacity('');
      setSuccessMessage('Area updated successfully.');
      await qc.invalidateQueries({ queryKey: ['company-events', selectedCompanyId] });
    },
  });

  const removeAreaMutation = useMutation({
    mutationFn: async ({ eventId, areaId }: { eventId: string; areaId: string }) => {
      const res = await http.delete<ApiResponse<null>>(`/api/events/${eventId}/areas/${areaId}`);
      if (res.data.error) throw new Error(res.data.error);
    },
    onSuccess: async () => {
      setSuccessMessage('Area removed successfully.');
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
    cancelMutation.error ||
    addAreaMutation.error ||
    updateAreaMutation.error ||
    removeAreaMutation.error;

  const actionErrorMessage = actionError ? getApiErrorMessage(actionError) : null;

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-extrabold text-slate-900">My Events</h1>
        <p className="mt-1 text-sm text-slate-600">
          Select a company, create events, manage areas, and define the event map.
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
            setAreaEventId(null);
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
            <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Event name" className="rounded-md border border-slate-200 px-3 py-2 text-sm" />
            <input value={artist} onChange={(e) => setArtist(e.target.value)} placeholder="Artist" className="rounded-md border border-slate-200 px-3 py-2 text-sm" />

            <select value={category} onChange={(e) => setCategory(e.target.value)} className="rounded-md border border-slate-200 bg-white px-3 py-2 text-sm">
              <option value="">Select category...</option>
              {categories.map((c) => (
                <option key={c} value={c}>{c.charAt(0) + c.slice(1).toLowerCase()}</option>
              ))}
            </select>

            <input type="datetime-local" value={startsAt} onChange={(e) => setStartsAt(e.target.value)} className="rounded-md border border-slate-200 px-3 py-2 text-sm" />
            <input value={location} onChange={(e) => setLocation(e.target.value)} placeholder="Location" className="rounded-md border border-slate-200 px-3 py-2 text-sm md:col-span-2" />
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

          <div className="mt-4 grid gap-3">
            {eventsQuery.data?.length === 0 && (
              <div className="text-sm text-slate-600">No events found.</div>
            )}

            {eventsQuery.data?.map((event) => {
              const isCancelled = event.status === 'CANCELLED';
              const canEditMap = event.status === 'DRAFT';
              const areas = event.areas ?? [];

              return (
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
                      {!isCancelled && (
                        <>
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
                            onClick={() => {
                                setOpenEventDetailsId(event.eventId);
                                setAreaEventId(
                                  canEditMap && areaEventId !== event.eventId ? event.eventId : null
                                );
                                setEditingAreaId(null);
                            }}
                            className="rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-900 hover:bg-slate-50"
                          >
                            {canEditMap ? 'Manage map' : 'View map'}
                          </button>
                        {event.status === 'DRAFT' && (
                          <button
                            onClick={() => publishMutation.mutate(event)}
                            disabled={publishMutation.isPending}
                            className="rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white disabled:opacity-60"
                          >
                            Publish
                          </button>
                        )}

                          <button
                            onClick={() => {
                                const isOpen = openEventDetailsId === event.eventId;

                                setOpenEventDetailsId(isOpen ? null : event.eventId);

                                if (isOpen) {
                                setAreaEventId(null);
                                setEditingAreaId(null);
                                setEditingEventId(null);
                                }
                            }}
                            className="rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-900 hover:bg-slate-50"
                            >
                            {openEventDetailsId === event.eventId ? 'Hide details' : 'Show details'}
                          </button>

                          <button
                            onClick={() => cancelMutation.mutate(event.eventId)}
                            disabled={cancelMutation.isPending}
                            className="rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-sm font-semibold text-rose-900 disabled:opacity-60"
                          >
                            Cancel event
                          </button>
                        </>
                      )}
                    </div>
                  </div>

                  {editingEventId === event.eventId && !isCancelled && (
                    <div className="mt-4 rounded-lg border border-slate-200 bg-white p-4">
                      <div className="font-semibold text-slate-900">Edit event</div>

                      <div className="mt-3 grid gap-3 md:grid-cols-2">
                        <input value={editName} onChange={(e) => setEditName(e.target.value)} placeholder="Event name" className="rounded-md border border-slate-200 px-3 py-2 text-sm" />
                        <input value={editArtist} onChange={(e) => setEditArtist(e.target.value)} placeholder="Artist" className="rounded-md border border-slate-200 px-3 py-2 text-sm" />

                        <select value={editCategory} onChange={(e) => setEditCategory(e.target.value)} className="rounded-md border border-slate-200 bg-white px-3 py-2 text-sm">
                          <option value="">Select category...</option>
                          {categories.map((c) => (
                            <option key={c} value={c}>{c.charAt(0) + c.slice(1).toLowerCase()}</option>
                          ))}
                        </select>

                        <input type="datetime-local" value={editStartsAt} onChange={(e) => setEditStartsAt(e.target.value)} className="rounded-md border border-slate-200 px-3 py-2 text-sm" />
                        <input value={editLocation} onChange={(e) => setEditLocation(e.target.value)} placeholder="Location" className="rounded-md border border-slate-200 px-3 py-2 text-sm md:col-span-2" />
                      </div>

                      <div className="mt-3 flex gap-2">
                        <button onClick={() => updateEventMutation.mutate()} disabled={updateEventMutation.isPending} className="rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white disabled:opacity-60">
                          {updateEventMutation.isPending ? 'Saving...' : 'Save changes'}
                        </button>

                        <button onClick={() => {setEditingEventId(null);}} className="rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-900">
                          Cancel edit
                        </button>
                      </div>
                    </div>
                  )}
                {openEventDetailsId === event.eventId && (
                  <div className="mt-4 rounded-lg border border-slate-200 bg-white p-4">
                    <div className="font-semibold text-slate-900">Event map</div>

                    {areas.length === 0 ? (
                      <div className="mt-2 text-sm text-slate-600">No areas defined yet.</div>
                    ) : (
                      <div className="mt-3 grid gap-3 md:grid-cols-2">
                        {areas.map((area) => (
                          <div key={area.areaId} className="rounded-lg border border-slate-200 bg-slate-50 p-3">
                            <div className="flex items-start justify-between gap-2">
                              <div>
                                <div className="font-semibold text-slate-900">{area.name}</div>
                                <div className="mt-1 text-xs text-slate-600">
                                  {area.type} | Available: {area.availableCapacity}
                                </div>
                                <div className="mt-1 text-xs text-slate-600">
                                  Price: {area.basePrice?.amount ?? '—'} {area.basePrice?.currency ?? ''}
                                </div>
                              </div>

                              {!isCancelled && canEditMap && (
                                <div className="flex gap-2">
                                  <button
                                    onClick={() => {
                                      setAreaEventId(event.eventId);
                                      setEditingAreaId(area.areaId);
                                      setEditAreaName(area.name);
                                      setEditAreaPrice(String(area.basePrice?.amount ?? ''));
                                      setEditAreaCurrency(area.basePrice?.currency ?? 'ILS');
                                      setEditStandingCapacity(area.type === 'STANDING' ? String(area.availableCapacity ?? '') : '');
                                    }}
                                    className="rounded-md border border-slate-200 bg-white px-2 py-1 text-xs font-semibold text-slate-900"
                                  >
                                    Edit
                                  </button>

                                  <button
                                    onClick={() => removeAreaMutation.mutate({ eventId: event.eventId, areaId: area.areaId })}
                                    className="rounded-md border border-rose-200 bg-rose-50 px-2 py-1 text-xs font-semibold text-rose-900"
                                  >
                                    Remove
                                  </button>
                                </div>
                              )}
                            </div>

                            {area.type === 'SEATING' && area.seats?.length > 0 && (
                              <div className="mt-3 overflow-x-auto">
                                <div className="mb-2 rounded bg-slate-200 px-3 py-1 text-center text-xs font-semibold text-slate-700">
                                  Stage / Screen
                                </div>

                                <div className="space-y-2">
                                  {Object.entries(
                                    area.seats.reduce<Record<string, typeof area.seats>>((acc, seat) => {
                                        acc[seat.row] = acc[seat.row] ?? [];
                                        acc[seat.row].push(seat);
                                        return acc;
                                    }, {})
                                    )
                                    .sort(([rowA], [rowB]) => Number(rowA) - Number(rowB))
                                    .map(([row, seats]) => (
                                    <div key={row} className="flex items-center gap-2">
                                      <div className="w-6 text-xs font-semibold text-slate-600">{row}</div>
                                      <div className="flex flex-wrap gap-1">
                                        {seats
                                            .sort((a, b) => Number(a.number) - Number(b.number))
                                            .map((seat) => (
                                          <span
                                            key={seat.seatId}
                                            className="inline-flex h-7 min-w-7 items-center justify-center rounded border border-slate-300 bg-white px-1 text-xs text-slate-700"
                                            title={seat.status}
                                          >
                                            {seat.number}
                                          </span>
                                        ))}
                                      </div>
                                    </div>
                                  ))}
                                </div>
                              </div>
                            )}

                            {area.type === 'STANDING' && (
                              <div className="mt-3 rounded-lg border border-dashed border-slate-300 bg-white p-4 text-center text-sm text-slate-700">
                                Standing area<br />
                                {area.availableCapacity} tickets available
                              </div>
                            )}
                            {editingAreaId === area.areaId && areaEventId === event.eventId && !isCancelled && canEditMap && (
                                <div className="mt-4 rounded-lg border border-slate-200 bg-white p-4">
                                    <div className="font-semibold text-slate-900">Edit area</div>

                                    <div className="mt-3 grid gap-3 md:grid-cols-2">
                                    <input
                                        value={editAreaName}
                                        onChange={(e) => setEditAreaName(e.target.value)}
                                        placeholder="Area name"
                                        className="rounded-md border border-slate-200 px-3 py-2 text-sm"
                                    />

                                    <input
                                        value={editAreaPrice}
                                        onChange={(e) => setEditAreaPrice(e.target.value)}
                                        placeholder="Base price"
                                        className="rounded-md border border-slate-200 px-3 py-2 text-sm"
                                    />

                                    <input
                                        value={editAreaCurrency}
                                        onChange={(e) => setEditAreaCurrency(e.target.value)}
                                        placeholder="Currency"
                                        className="rounded-md border border-slate-200 px-3 py-2 text-sm"
                                    />

                                    {area.type === 'STANDING' && (
                                        <input
                                        value={editStandingCapacity}
                                        onChange={(e) => setEditStandingCapacity(e.target.value)}
                                        placeholder="Standing capacity"
                                        className="rounded-md border border-slate-200 px-3 py-2 text-sm"
                                        />
                                    )}
                                    </div>

                                    <div className="mt-3 flex gap-2">
                                    <button
                                        onClick={() => updateAreaMutation.mutate()}
                                        disabled={updateAreaMutation.isPending}
                                        className="rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white disabled:opacity-60"
                                    >
                                        Save area
                                    </button>

                                    <button
                                        onClick={() => setEditingAreaId(null)}
                                        className="rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-900"
                                    >
                                        Cancel
                                    </button>
                                    </div>
                                </div>
                                )}
                          </div>
                        ))}
                      </div>
                    )}

                    
                  </div>
                  )}

                  {openEventDetailsId === event.eventId &&
                    areaEventId === event.eventId &&
                    !isCancelled &&
                    canEditMap && (
                    <div className="mt-4 rounded-lg border border-slate-200 bg-white p-4">
                      <div className="font-semibold text-slate-900">Add area / map section</div>

                      <div className="mt-3 grid gap-3 md:grid-cols-2">
                        <input value={areaName} onChange={(e) => setAreaName(e.target.value)} placeholder="Area name" className="rounded-md border border-slate-200 px-3 py-2 text-sm" />

                        <select value={areaType} onChange={(e) => setAreaType(e.target.value as AreaType)} className="rounded-md border border-slate-200 bg-white px-3 py-2 text-sm">
                          <option value="SEATING">Seating area</option>
                          <option value="STANDING">Standing area</option>
                        </select>

                        <input value={areaPrice} onChange={(e) => setAreaPrice(e.target.value)} placeholder="Base price" className="rounded-md border border-slate-200 px-3 py-2 text-sm" />
                        <input value={areaCurrency} onChange={(e) => setAreaCurrency(e.target.value)} placeholder="Currency" className="rounded-md border border-slate-200 px-3 py-2 text-sm" />

                        {areaType === 'SEATING' ? (
                          <>
                            <input value={seatRows} onChange={(e) => setSeatRows(e.target.value)} placeholder="Number of rows, e.g. 5" className="rounded-md border border-slate-200 px-3 py-2 text-sm" />
                            <input value={seatsPerRow} onChange={(e) => setSeatsPerRow(e.target.value)} placeholder="Seats per row, e.g. 10" className="rounded-md border border-slate-200 px-3 py-2 text-sm" />
                          </>
                        ) : (
                          <input value={standingCapacity} onChange={(e) => setStandingCapacity(e.target.value)} placeholder="Standing tickets amount" className="rounded-md border border-slate-200 px-3 py-2 text-sm md:col-span-2" />
                        )}
                      </div>

                      <button
                        onClick={() => addAreaMutation.mutate()}
                        disabled={addAreaMutation.isPending}
                        className="mt-3 w-full rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white disabled:opacity-60"
                      >
                        {addAreaMutation.isPending ? 'Adding...' : 'Add area'}
                      </button>
                    </div>
                  )}

                </div>
              );
            })}
          </div>

          {successMessage && (
            <div className="mt-3 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
              {successMessage}
            </div>
          )}

          {actionErrorMessage && (
            <div className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
              {actionErrorMessage}
            </div>
          )}
        </div>
      )}
    </div>
  );
}