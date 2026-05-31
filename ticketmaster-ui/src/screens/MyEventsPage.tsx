import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { http } from '../api/http';
import { getApiErrorMessage } from '../api/errors';
import type {
  ApiResponse,
  CompanyDTO,
  DiscountPolicyDTO,
  EventDTO,
  MemberDTO,
  PurchasePolicyDTO,
} from '../api/types';
import { useAuthStore } from '../ui/authStore';

const categories = ['CONCERT', 'SPORTS', 'THEATER', 'CONFERENCE', 'FESTIVAL', 'OTHER'];

type AreaType = 'SEATING' | 'STANDING';

type SeatSpec = {
  row: string;
  number: string;
};

type LotteryEligibilityDTO = {
  status: string;
};

function toDatetimeLocalValue(d: Date) {
  const pad = (n: number) => String(n).padStart(2, '0');
  return (
    d.getFullYear() +
    '-' +
    pad(d.getMonth() + 1) +
    '-' +
    pad(d.getDate()) +
    'T' +
    pad(d.getHours()) +
    ':' +
    pad(d.getMinutes())
  );
}

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

  const [lotteryEventId, setLotteryEventId] = useState<string | null>(null);
  const [lotteryWinners, setLotteryWinners] = useState<string[] | null>(null);
  const [lotteryWinnerCount, setLotteryWinnerCount] = useState('10');
  const [lotteryExpiration, setLotteryExpiration] = useState('');

  const [editingAreaId, setEditingAreaId] = useState<string | null>(null);
  const [editAreaName, setEditAreaName] = useState('');
  const [editAreaPrice, setEditAreaPrice] = useState('');
  const [editAreaCurrency, setEditAreaCurrency] = useState('ILS');
  const [editStandingCapacity, setEditStandingCapacity] = useState('');

  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const [policyEventId, setPolicyEventId] = useState<string | null>(null);
  const [purchasePoliciesDraft, setPurchasePoliciesDraft] = useState<PurchasePolicyDTO[]>([]);
  const [discountPoliciesDraft, setDiscountPoliciesDraft] = useState<DiscountPolicyDTO[]>([]);

  const [newPurchaseType, setNewPurchaseType] = useState<'MAX_TICKETS_PER_ORDER' | 'AGE_RESTRICTION' | 'NO_LONELY_SEAT'>('MAX_TICKETS_PER_ORDER');
  const [newMaxTickets, setNewMaxTickets] = useState('4');
  const [newMinAge, setNewMinAge] = useState('18');

  const [newDiscountType, setNewDiscountType] = useState<'COUPON' | 'EARLY_BIRD'>('COUPON');
  const [newCouponCode, setNewCouponCode] = useState('');
  const [newDiscountPercentage, setNewDiscountPercentage] = useState('10');
  const [newCouponExpiresAt, setNewCouponExpiresAt] = useState('');
  const [newEarlyBirdUntil, setNewEarlyBirdUntil] = useState('');

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

  const appointmentApprovedQuery = useQuery({
    queryKey: ['appointment-approved', token, meQuery.data?.activeRole],
    queryFn: async () => {
      const res = await http.get<ApiResponse<boolean>>('/api/users/roles/approved');

      if (res.data.error) throw new Error(res.data.error);

      return res.data.data ?? false;
    },
    enabled:
      Boolean(token) &&
      userType === 'member' &&
      (meQuery.data?.activeRole === 'Owner' || meQuery.data?.activeRole === 'Manager'),
  });

  const activeRole = meQuery.data?.activeRole;
  const isApprovedAppointment =
    activeRole === 'Founder' || appointmentApprovedQuery.data === true;

  const canManageEvents =
  activeRole === 'Founder' ||
  ((activeRole === 'Owner' || activeRole === 'Manager') && isApprovedAppointment);

  if (!canManageEvents) {
    return (
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-extrabold text-slate-900">My Events</h1>
        <p className="mt-2 text-slate-600">
          Your appointment must be approved before you can manage events.
        </p>
      </div>
    );
  }

  const deleteLotteryMutation = useMutation({
    mutationFn: async ({ companyId, eventId }: { companyId: string; eventId: string }) => {
      setSuccessMessage(null);

      if (!companyId) throw new Error('Company ID is missing.');
      if (!eventId) throw new Error('Event ID is missing.');

      const res = await http.delete<ApiResponse<null>>(
        `/api/companies/${companyId}/events/${eventId}/lottery`
      );
      if (res.data.error) throw new Error(res.data.error);
    },
    onSuccess: async (_data, variables) => {
      setSuccessMessage('Lottery deleted successfully.');
      setLotteryWinners(null);
      setLotteryEventId(null);

      await qc.invalidateQueries({
        queryKey: ['lottery', 'eligibility', 'company-events', selectedCompanyId, token],
      });
    },
  });

  const drawLotteryMutation = useMutation({
    mutationFn: async ({ companyId, eventId }: { companyId: string; eventId: string }) => {
      setSuccessMessage(null);

      if (!companyId) throw new Error('Company ID is missing.');
      if (!eventId) throw new Error('Event ID is missing.');

      const count = Number(lotteryWinnerCount);
      if (!Number.isFinite(count) || count < 0) {
        throw new Error('Winner count must be 0 or more.');
      }

      if (!lotteryExpiration) {
        throw new Error('Expiration time is required.');
      }

      const expirationTime = new Date(lotteryExpiration).toISOString();

      const res = await http.post<ApiResponse<string[]>>(
        `/api/companies/${companyId}/events/${eventId}/lottery/draw`,
        {
          count,
          expirationTime,
        }
      );

      if (res.data.error) throw new Error(res.data.error);
      return res.data.data ?? [];
    },
    onSuccess: async (winners) => {
      setLotteryWinners(winners);
      setSuccessMessage(`Lottery drawn successfully. Winners: ${winners.length}.`);
    },
  });

  const fetchWinnersMutation = useMutation({
    mutationFn: async ({ companyId, eventId }: { companyId: string; eventId: string }) => {
      setSuccessMessage(null);
      if (!companyId) throw new Error('Company ID is missing.');
      if (!eventId) throw new Error('Event ID is missing.');

      const res = await http.get<ApiResponse<string[]>>(
        `/api/companies/${companyId}/events/${eventId}/lottery/winners`
      );
      if (res.data.error) throw new Error(res.data.error);
      return res.data.data ?? [];
    },
    onSuccess: async (winners) => {
      setLotteryWinners(winners);
      setSuccessMessage(`Loaded winners. Total: ${winners.length}.`);
    },
  });

  const createLotteryMutation = useMutation({
    mutationFn: async ({ companyId, eventId }: { companyId: string; eventId: string }) => {
      setSuccessMessage(null);

      if (!companyId) throw new Error('Company ID is missing.');
      if (!eventId) throw new Error('Event ID is missing.');

      const res = await http.post<ApiResponse<null>>(
        `/api/companies/${companyId}/events/${eventId}/lottery`
      );
      if (res.data.error) throw new Error(res.data.error);
    },
    onSuccess: async (_data, variables) => {
      setSuccessMessage('Lottery created successfully.');

      await qc.invalidateQueries({
        queryKey: ['lottery', 'eligibility', 'company-events', selectedCompanyId, token],
      });

      setLotteryWinners(null);
      setLotteryEventId(variables.eventId);

      setLotteryWinnerCount('10');
      const expiry = new Date(Date.now() + 24 * 60 * 60 * 1000);
      setLotteryExpiration(toDatetimeLocalValue(expiry));
    },
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

  const lotteryStatusByEventQuery = useQuery({
    queryKey: ['lottery', 'eligibility', 'company-events', selectedCompanyId, token],
    queryFn: async () => {
      const events = eventsQuery.data ?? [];

      const statuses = await Promise.all(
        events.map(async (event) => {
          try {
            const res = await http.get<ApiResponse<LotteryEligibilityDTO>>(
              `/api/companies/${event.companyId}/events/${event.eventId}/lottery/eligibility`
            );
            if (res.data.error) throw new Error(res.data.error);
            return [event.eventId, res.data.data?.status ?? 'NO_LOTTERY_REQUIRED'] as const;
          } catch {
            return [event.eventId, 'NO_LOTTERY_REQUIRED'] as const;
          }
        })
      );

      return Object.fromEntries(statuses) as Record<string, string>;
    },
    enabled: Boolean(token) && Boolean(eventsQuery.data?.length),
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

  if (
    meQuery.data?.activeRole !== 'Founder' &&
    meQuery.data?.activeRole !== 'Owner' &&
    meQuery.data?.activeRole !== 'Manager'
  ) {
    return (
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-extrabold text-slate-900">My Events</h1>
        <p className="mt-2 text-slate-600">
          Only active Owners, Founders, or Managers can manage events.
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
    removeAreaMutation.error ||
    createLotteryMutation.error ||
    deleteLotteryMutation.error ||
    drawLotteryMutation.error ||
    fetchWinnersMutation.error;

  const actionErrorMessage = actionError ? getApiErrorMessage(actionError) : null;

  const purchasePoliciesQuery = useQuery({
    queryKey: ['event', 'purchase-policies', policyEventId],
    queryFn: async () => {
      if (!policyEventId) return [] as PurchasePolicyDTO[];
      const res = await http.get<ApiResponse<PurchasePolicyDTO[]>>(
        `/api/events/${policyEventId}/purchase-policies`
      );
      if (res.data.error) throw new Error(res.data.error);
      return res.data.data ?? [];
    },
    enabled: Boolean(policyEventId),
  });

  const discountPoliciesQuery = useQuery({
    queryKey: ['event', 'discount-policies', policyEventId],
    queryFn: async () => {
      if (!policyEventId) return [] as DiscountPolicyDTO[];
      const res = await http.get<ApiResponse<DiscountPolicyDTO[]>>(
        `/api/events/${policyEventId}/discount-policies`
      );
      if (res.data.error) throw new Error(res.data.error);
      return res.data.data ?? [];
    },
    enabled: Boolean(policyEventId),
  });

  const replacePurchasePoliciesMutation = useMutation({
    mutationFn: async ({ eventId, policies }: { eventId: string; policies: PurchasePolicyDTO[] }) => {
      const res = await http.put<ApiResponse<null>>(`/api/events/${eventId}/purchase-policies`, policies);
      if (res.data.error) throw new Error(res.data.error);
    },
    onSuccess: async (_data, variables) => {
      setSuccessMessage('Purchase policies saved successfully.');
      await qc.invalidateQueries({ queryKey: ['event', 'purchase-policies', variables.eventId] });
    },
  });

  const replaceDiscountPoliciesMutation = useMutation({
    mutationFn: async ({ eventId, policies }: { eventId: string; policies: DiscountPolicyDTO[] }) => {
      const res = await http.put<ApiResponse<null>>(`/api/events/${eventId}/discount-policies`, policies);
      if (res.data.error) throw new Error(res.data.error);
    },
    onSuccess: async (_data, variables) => {
      setSuccessMessage('Discount policies saved successfully.');
      await qc.invalidateQueries({ queryKey: ['event', 'discount-policies', variables.eventId] });
    },
  });

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
                                setPolicyEventId(isOpen ? null : event.eventId);
                                setPurchasePoliciesDraft([]);
                                setDiscountPoliciesDraft([]);
                                setSuccessMessage(null);

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

                          <button
                            onClick={() =>
                              createLotteryMutation.mutate({
                                companyId: event.companyId,
                                eventId: event.eventId,
                              })
                            }
                            disabled={
                              createLotteryMutation.isPending ||
                              (lotteryStatusByEventQuery.data?.[event.eventId] ??
                                'NO_LOTTERY_REQUIRED') !== 'NO_LOTTERY_REQUIRED'
                            }
                            className="rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-900 hover:bg-slate-50 disabled:opacity-60"
                          >
                            {createLotteryMutation.isPending ? 'Creating lottery…' : 'Create lottery'}
                          </button>

                          <button
                            onClick={() => {
                              const status =
                                lotteryStatusByEventQuery.data?.[event.eventId] ?? 'NO_LOTTERY_REQUIRED';
                              const hasLottery = status !== 'NO_LOTTERY_REQUIRED';
                              if (!hasLottery) return;

                              const isOpen = lotteryEventId === event.eventId;
                              setLotteryEventId(isOpen ? null : event.eventId);
                              setLotteryWinners(null);
                              setSuccessMessage(null);

                              if (!isOpen) {
                                setLotteryWinnerCount('10');
                                const expiry = new Date(Date.now() + 24 * 60 * 60 * 1000);
                                setLotteryExpiration(toDatetimeLocalValue(expiry));
                              }
                            }}
                            disabled={
                              (lotteryStatusByEventQuery.data?.[event.eventId] ??
                                'NO_LOTTERY_REQUIRED') === 'NO_LOTTERY_REQUIRED'
                            }
                            className="rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-900 hover:bg-slate-50 disabled:opacity-60"
                          >
                            {lotteryEventId === event.eventId ? 'Hide lottery' : 'Manage lottery'}
                          </button>
                        </>
                      )}
                    </div>
                  </div>

                  {lotteryEventId === event.eventId &&
                    (lotteryStatusByEventQuery.data?.[event.eventId] ?? 'NO_LOTTERY_REQUIRED') !==
                      'NO_LOTTERY_REQUIRED' &&
                    !isCancelled && (
                    <div className="mt-4 rounded-lg border border-slate-200 bg-white p-4">
                      <div className="font-semibold text-slate-900">Lottery management</div>
                      <div className="mt-1 text-sm text-slate-600">
                        You can configure the draw (how many winners) and the winners' access expiration time.
                      </div>

                      <div className="mt-4 grid gap-3 md:grid-cols-3">
                        <label className="block">
                          <div className="text-sm font-medium text-slate-700">Winners to draw</div>
                          <input
                            value={lotteryWinnerCount}
                            onChange={(e) => setLotteryWinnerCount(e.target.value)}
                            className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
                          />
                        </label>

                        <label className="block md:col-span-2">
                          <div className="text-sm font-medium text-slate-700">Winner access expires at</div>
                          <input
                            type="datetime-local"
                            value={lotteryExpiration}
                            onChange={(e) => setLotteryExpiration(e.target.value)}
                            className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
                          />
                        </label>
                      </div>

                      <div className="mt-4 flex flex-wrap gap-2">
                        <button
                          onClick={() =>
                            drawLotteryMutation.mutate({
                              companyId: event.companyId,
                              eventId: event.eventId,
                            })
                          }
                          disabled={drawLotteryMutation.isPending}
                          className="rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
                        >
                          {drawLotteryMutation.isPending ? 'Running draw…' : 'Run draw'}
                        </button>

                        <button
                          onClick={() =>
                            fetchWinnersMutation.mutate({
                              companyId: event.companyId,
                              eventId: event.eventId,
                            })
                          }
                          disabled={fetchWinnersMutation.isPending}
                          className="rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-900 hover:bg-slate-50 disabled:opacity-60"
                        >
                          {fetchWinnersMutation.isPending ? 'Loading…' : 'View winners'}
                        </button>

                        <button
                          onClick={() =>
                            deleteLotteryMutation.mutate({
                              companyId: event.companyId,
                              eventId: event.eventId,
                            })
                          }
                          disabled={deleteLotteryMutation.isPending}
                          className="rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-sm font-semibold text-rose-900 hover:bg-rose-100 disabled:opacity-60"
                        >
                          {deleteLotteryMutation.isPending ? 'Deleting…' : 'Delete lottery'}
                        </button>
                      </div>

                      {lotteryWinners && (
                        <div className="mt-4">
                          <div className="text-sm font-semibold text-slate-900">Winners</div>
                          {lotteryWinners.length === 0 ? (
                            <div className="mt-1 text-sm text-slate-600">No winners yet.</div>
                          ) : (
                            <div className="mt-2 grid gap-1">
                              {lotteryWinners.map((id) => (
                                <div key={id} className="flex flex-wrap items-center justify-between gap-2 rounded-md border border-slate-200 bg-slate-50 px-3 py-2">
                                  <div className="text-sm text-slate-800">
                                    {id}
                                  </div>
                                  <button
                                    onClick={() => {
                                      setSuccessMessage(`Send message UI not connected yet (winner: ${id}).`);
                                    }}
                                    className="rounded-md border border-slate-200 bg-white px-3 py-1.5 text-xs font-semibold text-slate-900 hover:bg-slate-50"
                                  >
                                    Send message
                                  </button>
                                </div>
                              ))}
                            </div>
                          )}
                        </div>
                      )}
                    </div>
                  )}

                  {openEventDetailsId === event.eventId && !isCancelled && (
                    <div className="mt-4 rounded-lg border border-slate-200 bg-white p-4">
                      <div className="font-semibold text-slate-900">Event policies</div>

                      <div className="mt-2 text-sm text-slate-600">
                        Define purchase restrictions and discount rules for this event.
                      </div>

                      <div className="mt-4 grid gap-4 md:grid-cols-2">
                        <div className="rounded-lg border border-slate-200 bg-slate-50 p-4">
                          <div className="font-semibold text-slate-900">Purchase policies</div>

                          {purchasePoliciesQuery.isPending ? (
                            <div className="mt-2 text-sm text-slate-600">Loading…</div>
                          ) : purchasePoliciesQuery.isError ? (
                            <div className="mt-2 text-sm text-rose-700">
                              {getApiErrorMessage(purchasePoliciesQuery.error)}
                            </div>
                          ) : (
                            <div className="mt-3 space-y-2">
                              {(purchasePoliciesDraft.length ? purchasePoliciesDraft : purchasePoliciesQuery.data ?? []).map(
                                (p, idx) => (
                                  <div
                                    key={idx}
                                    className="flex items-center justify-between gap-2 rounded-md border border-slate-200 bg-white px-3 py-2"
                                  >
                                    <div className="text-sm text-slate-800">
                                      {p.type === 'MAX_TICKETS_PER_ORDER'
                                        ? `Max tickets per order: ${(p as any).max}`
                                        : p.type === 'AGE_RESTRICTION'
                                          ? `Age restriction: ${(p as any).minAge}+`
                                          : p.type === 'NO_LONELY_SEAT'
                                            ? 'No lonely seat'
                                            : `Unknown policy: ${p.type}`}
                                    </div>
                                    <button
                                      onClick={() => {
                                        const base = purchasePoliciesDraft.length
                                          ? purchasePoliciesDraft
                                          : purchasePoliciesQuery.data ?? [];
                                        setPurchasePoliciesDraft(base.filter((_, i) => i !== idx));
                                      }}
                                      className="rounded-md border border-rose-200 bg-rose-50 px-2 py-1 text-xs font-semibold text-rose-900"
                                    >
                                      Remove
                                    </button>
                                  </div>
                                )
                              )}

                              {(purchasePoliciesDraft.length ? purchasePoliciesDraft : purchasePoliciesQuery.data ?? [])
                                .length === 0 && <div className="text-sm text-slate-600">No purchase policies.</div>}
                            </div>
                          )}

                          <div className="mt-4 grid gap-2">
                            <select
                              value={newPurchaseType}
                              onChange={(e) => setNewPurchaseType(e.target.value as any)}
                              className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
                            >
                              <option value="MAX_TICKETS_PER_ORDER">Max tickets per order</option>
                              <option value="AGE_RESTRICTION">Age restriction</option>
                              <option value="NO_LONELY_SEAT">No lonely seat</option>
                            </select>

                            {newPurchaseType === 'MAX_TICKETS_PER_ORDER' && (
                              <input
                                value={newMaxTickets}
                                onChange={(e) => setNewMaxTickets(e.target.value)}
                                placeholder="Max"
                                className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
                              />
                            )}

                            {newPurchaseType === 'AGE_RESTRICTION' && (
                              <input
                                value={newMinAge}
                                onChange={(e) => setNewMinAge(e.target.value)}
                                placeholder="Min age"
                                className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
                              />
                            )}

                            <button
                              onClick={() => {
                                const base = purchasePoliciesDraft.length
                                  ? purchasePoliciesDraft
                                  : purchasePoliciesQuery.data ?? [];

                                if (newPurchaseType === 'MAX_TICKETS_PER_ORDER') {
                                  const max = Number(newMaxTickets);
                                  if (!Number.isFinite(max) || max < 1) return;
                                  setPurchasePoliciesDraft([...base, { type: 'MAX_TICKETS_PER_ORDER', max }]);
                                  return;
                                }

                                if (newPurchaseType === 'AGE_RESTRICTION') {
                                  const minAge = Number(newMinAge);
                                  if (!Number.isFinite(minAge) || minAge < 0) return;
                                  setPurchasePoliciesDraft([...base, { type: 'AGE_RESTRICTION', minAge }]);
                                  return;
                                }

                                setPurchasePoliciesDraft([...base, { type: 'NO_LONELY_SEAT' }]);
                              }}
                              className="rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-900 hover:bg-slate-50"
                            >
                              Add purchase policy
                            </button>
                          </div>

                          <button
                            onClick={() => {
                              const eventId = event.eventId;
                              const policies = purchasePoliciesDraft.length
                                ? purchasePoliciesDraft
                                : purchasePoliciesQuery.data ?? [];
                              replacePurchasePoliciesMutation.mutate({ eventId, policies });
                            }}
                            disabled={replacePurchasePoliciesMutation.isPending}
                            className="mt-4 w-full rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white disabled:opacity-60"
                          >
                            {replacePurchasePoliciesMutation.isPending ? 'Saving…' : 'Save purchase policies'}
                          </button>

                          <button
                            onClick={() => setPurchasePoliciesDraft([])}
                            className="mt-2 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-900"
                          >
                            Reset purchase policies
                          </button>
                        </div>

                        <div className="rounded-lg border border-slate-200 bg-slate-50 p-4">
                          <div className="font-semibold text-slate-900">Discount policies</div>

                          {discountPoliciesQuery.isPending ? (
                            <div className="mt-2 text-sm text-slate-600">Loading…</div>
                          ) : discountPoliciesQuery.isError ? (
                            <div className="mt-2 text-sm text-rose-700">
                              {getApiErrorMessage(discountPoliciesQuery.error)}
                            </div>
                          ) : (
                            <div className="mt-3 space-y-2">
                              {(discountPoliciesDraft.length ? discountPoliciesDraft : discountPoliciesQuery.data ?? []).map(
                                (p, idx) => (
                                  <div
                                    key={idx}
                                    className="flex items-center justify-between gap-2 rounded-md border border-slate-200 bg-white px-3 py-2"
                                  >
                                    <div className="text-sm text-slate-800">
                                      {p.type === 'COUPON'
                                        ? `Coupon ${(p as any).code} — ${(p as any).percentage}%`
                                        : p.type === 'EARLY_BIRD'
                                          ? `Early bird — ${(p as any).percentage}%`
                                          : `Unknown policy: ${p.type}`}
                                    </div>
                                    <button
                                      onClick={() => {
                                        const base = discountPoliciesDraft.length
                                          ? discountPoliciesDraft
                                          : discountPoliciesQuery.data ?? [];
                                        setDiscountPoliciesDraft(base.filter((_, i) => i !== idx));
                                      }}
                                      className="rounded-md border border-rose-200 bg-rose-50 px-2 py-1 text-xs font-semibold text-rose-900"
                                    >
                                      Remove
                                    </button>
                                  </div>
                                )
                              )}

                              {(discountPoliciesDraft.length ? discountPoliciesDraft : discountPoliciesQuery.data ?? [])
                                .length === 0 && <div className="text-sm text-slate-600">No discount policies.</div>}
                            </div>
                          )}

                          <div className="mt-4 grid gap-2">
                            <select
                              value={newDiscountType}
                              onChange={(e) => setNewDiscountType(e.target.value as any)}
                              className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
                            >
                              <option value="COUPON">Coupon</option>
                              <option value="EARLY_BIRD">Early bird</option>
                            </select>

                            <input
                              value={newDiscountPercentage}
                              onChange={(e) => setNewDiscountPercentage(e.target.value)}
                              placeholder="Percentage"
                              className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
                            />

                            {newDiscountType === 'COUPON' && (
                              <>
                                <input
                                  value={newCouponCode}
                                  onChange={(e) => setNewCouponCode(e.target.value)}
                                  placeholder="Coupon code"
                                  className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
                                />
                                <input
                                  type="datetime-local"
                                  value={newCouponExpiresAt}
                                  onChange={(e) => setNewCouponExpiresAt(e.target.value)}
                                  className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
                                />
                              </>
                            )}

                            {newDiscountType === 'EARLY_BIRD' && (
                              <input
                                type="datetime-local"
                                value={newEarlyBirdUntil}
                                onChange={(e) => setNewEarlyBirdUntil(e.target.value)}
                                className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
                              />
                            )}

                            <button
                              onClick={() => {
                                const base = discountPoliciesDraft.length
                                  ? discountPoliciesDraft
                                  : discountPoliciesQuery.data ?? [];
                                const percentage = Number(newDiscountPercentage);
                                if (!Number.isFinite(percentage) || percentage < 0) return;

                                if (newDiscountType === 'COUPON') {
                                  if (!newCouponCode.trim() || !newCouponExpiresAt) return;
                                  setDiscountPoliciesDraft([
                                    ...base,
                                    {
                                      type: 'COUPON',
                                      code: newCouponCode.trim(),
                                      percentage,
                                      expiresAt: new Date(newCouponExpiresAt).toISOString(),
                                    },
                                  ]);
                                  return;
                                }

                                if (!newEarlyBirdUntil) return;
                                setDiscountPoliciesDraft([
                                  ...base,
                                  {
                                    type: 'EARLY_BIRD',
                                    percentage,
                                    until: new Date(newEarlyBirdUntil).toISOString(),
                                  },
                                ]);
                              }}
                              className="rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-900 hover:bg-slate-50"
                            >
                              Add discount policy
                            </button>
                          </div>

                          <button
                            onClick={() => {
                              const eventId = event.eventId;
                              const policies = discountPoliciesDraft.length
                                ? discountPoliciesDraft
                                : discountPoliciesQuery.data ?? [];
                              replaceDiscountPoliciesMutation.mutate({ eventId, policies });
                            }}
                            disabled={replaceDiscountPoliciesMutation.isPending}
                            className="mt-4 w-full rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white disabled:opacity-60"
                          >
                            {replaceDiscountPoliciesMutation.isPending ? 'Saving…' : 'Save discount policies'}
                          </button>

                          <button
                            onClick={() => setDiscountPoliciesDraft([])}
                            className="mt-2 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-900"
                          >
                            Reset discount policies
                          </button>
                        </div>
                      </div>
                    </div>
                  )}

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