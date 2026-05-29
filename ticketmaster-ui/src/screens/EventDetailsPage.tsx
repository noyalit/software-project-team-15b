import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useEffect, useState } from 'react';
import { http } from '../api/http';
import type { ApiResponse, EventDTO } from '../api/types';
import { getApiErrorMessage } from '../api/errors';
import { useAuthStore } from '../ui/authStore';

type ActiveOrderDTO = {
  orderId: string;
  eventId?: string;
  areaId?: string;
  seatIds?: string[];
  seats?: Array<{
    seatId: string;
    row: string | null;
    number: string | null;
  }>;
  totalPrice?: unknown;
  expiresAt?: string | null;
};

type CheckoutStartedDTO = {
  orderId?: string;
  paymentId?: string;
  amount?: unknown;
};

type CheckoutCompletedDTO = {
  orderId?: string;
  tickets?: unknown[];
};

export default function EventDetailsPage() {
  const { eventId } = useParams();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const { token, userType } = useAuthStore();

  const [selectedAreaId, setSelectedAreaId] = useState<string | null>(null);
  const [selectedSeatIds, setSelectedSeatIds] = useState<string[]>([]);
  const [guestBirthDate, setGuestBirthDate] = useState('');
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [activeOrderId, setActiveOrderId] = useState<string | null>(() =>localStorage.getItem('activeOrderId'));
  const [checkoutStarted, setCheckoutStarted] = useState(false);

  const myActiveOrdersQuery = useQuery({
    queryKey: ['active-orders', 'my', token],
    queryFn: async () => {
      const res = await http.get<
        ApiResponse<Array<{ orderId: string; eventId?: string }>>
      >('/api/active-orders/my');
      if (res.data.error) throw new Error(res.data.error);
      return res.data.data ?? [];
    },
    enabled: Boolean(token) && Boolean(eventId),
  });

  useEffect(() => {
    if (activeOrderId) return;
    const orders = myActiveOrdersQuery.data ?? [];
    const matching = orders.find((o) => o.eventId === eventId);
    if (!matching) return;
    setActiveOrderId(matching.orderId);
    localStorage.setItem('activeOrderId', matching.orderId);
  }, [activeOrderId, eventId, myActiveOrdersQuery.data]);

  const eventQuery = useQuery({
    queryKey: ['event', eventId],
    queryFn: async () => {
      const res = await http.get<ApiResponse<EventDTO>>(`/api/events/${eventId}`);
      if (res.data.error) throw new Error(res.data.error);
      if (!res.data.data) throw new Error('Event not found');
      return res.data.data;
    },
    enabled: Boolean(eventId),
  });

  const activeOrderQuery = useQuery({
    queryKey: ['active-order', activeOrderId, token],
    queryFn: async () => {
      try {
        const res = await http.get<ApiResponse<ActiveOrderDTO>>(
          `/api/active-orders/${activeOrderId}`
        );
        if (res.data.error) throw new Error(res.data.error);
        if (!res.data.data) throw new Error('Active order not found');
        return res.data.data;
      } catch (e) {
        const msg = getApiErrorMessage(e);

        // If order is no longer valid/active for the user, clear it so they can start a new one.
        if (
          msg.toLowerCase().includes('active order not found') ||
          msg.toLowerCase().includes('active order not found:') ||
          msg.toLowerCase().includes('not active') ||
          msg.toLowerCase().includes('checkout has expired') ||
          msg.toLowerCase().includes('is not active')
        ) {
          localStorage.removeItem('activeOrderId');
          setActiveOrderId(null);
          setCheckoutStarted(false);
          setSelectedSeatIds([]);
        }

        throw e;
      }
    },
    enabled: Boolean(activeOrderId) && Boolean(token),
  });

  useEffect(() => {
    const expiresAt = activeOrderQuery.data?.expiresAt;
    setCheckoutStarted(Boolean(expiresAt));
  }, [activeOrderQuery.data?.expiresAt]);

  const requestAccessMutation = useMutation({
    mutationFn: async () => {
      if (!eventId) throw new Error('Event ID is missing.');

      const res = await http.post<ApiResponse<unknown>>(
        `/api/active-orders/access/${eventId}`
      );

      if (res.data.error) throw new Error(res.data.error);
      return res.data.data;
    },
  });

  const createOrderMutation = useMutation({
    mutationFn: async (areaId: string) => {
      if (!eventId) throw new Error('Event ID is missing.');

      try {
        const res = await http.post<ApiResponse<string>>('/api/active-orders', {
          eventId,
          areaId,
        });

        if (res.data.error) throw new Error(res.data.error);
        if (!res.data.data) throw new Error('No order ID returned');

        return res.data.data;
      } catch (e) {
        const message = getApiErrorMessage(e);

        if (message.toLowerCase().includes('active order')) {
          try {
            const res2 = await http.get<
              ApiResponse<Array<{ orderId: string; eventId?: string }>>
            >('/api/active-orders/my');
            const orders = res2.data.data ?? [];
            const matching = orders.find((o) => o.eventId === eventId);
            if (matching?.orderId) {
              setActiveOrderId(matching.orderId);
              localStorage.setItem('activeOrderId', matching.orderId);
              return matching.orderId;
            }
          } catch {
            // fall through
          }

          throw new Error(
            'You already have an active order for this event. Please continue it from the Orders page.'
          );
        }

        throw new Error(message || 'Failed to start order.');
      }
    },

    onSuccess: async (orderId) => {
      setActiveOrderId(orderId);
      localStorage.setItem('activeOrderId', orderId);
      setSuccessMessage('Active order started.');
      await qc.invalidateQueries({ queryKey: ['active-order', orderId] });
    },
  });

  const addSeatsMutation = useMutation({
    mutationFn: async () => {
      if (!activeOrderId) throw new Error('Please start an order first.');
      if (checkoutStarted) {
        throw new Error("Checkout already started. You can't change seats now.");
      }
      if (selectedSeatIds.length === 0) throw new Error('Please select at least one seat.');

      const res = await http.post<ApiResponse<null>>(
        `/api/active-orders/${activeOrderId}/seats/add`,
        {
          seatIds: selectedSeatIds,
        }
      );

      if (res.data.error) throw new Error(res.data.error);
    },

    onSuccess: async () => {
      setSuccessMessage('Seats added to active order.');

      await qc.invalidateQueries({ queryKey: ['event', eventId] });
      await qc.invalidateQueries({ queryKey: ['active-order', activeOrderId, token] });
    },
  });

  const removeSeatsMutation = useMutation({
    mutationFn: async () => {
      if (!activeOrderId) throw new Error('Please start an order first.');
      if (checkoutStarted) {
        throw new Error("Checkout already started. You can't change seats now.");
      }
      if (selectedSeatIds.length === 0) throw new Error('Please select at least one seat.');

      const res = await http.post<ApiResponse<null>>(
        `/api/active-orders/${activeOrderId}/seats/remove`,
        {
          seatIds: selectedSeatIds,
        }
      );

      if (res.data.error) throw new Error(res.data.error);
    },

    onSuccess: async () => {
      setSuccessMessage('Seats removed from active order.');
      setSelectedSeatIds([]);
      await qc.invalidateQueries({ queryKey: ['event', eventId] });
      await qc.invalidateQueries({ queryKey: ['active-order', activeOrderId, token] });
    },
  });

  const startCheckoutMutation = useMutation({
    mutationFn: async () => {
      if (!activeOrderId) throw new Error('Please start an order first.');
      if (checkoutStarted) throw new Error('Checkout has already started for this order.');

      if (userType === 'member') {
        const res = await http.post<ApiResponse<CheckoutStartedDTO>>(
          `/api/active-orders/${activeOrderId}/checkout/member/start`
        );

        if (res.data.error) throw new Error(res.data.error);
        return res.data.data;
      }

      if (!guestBirthDate) {
        throw new Error('Please enter birth date for guest checkout.');
      }

      localStorage.setItem('guestBirthDate', guestBirthDate);

      const res = await http.post<ApiResponse<CheckoutStartedDTO>>(
        `/api/active-orders/${activeOrderId}/checkout/guest/start`,
        {
          birthDate: guestBirthDate,
        }
      );

      if (res.data.error) throw new Error(res.data.error);
      return res.data.data;
    },

    onSuccess: () => {
      setCheckoutStarted(true);
      setSuccessMessage('Checkout started.');
      if (activeOrderId) {
        navigate(`/checkout/${activeOrderId}`);
      }
    },
  });

  if (eventQuery.isPending) return <div className="text-slate-600">Loading…</div>;

  if (eventQuery.isError) {
    return (
      <div className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
        {(eventQuery.error as Error).message}
      </div>
    );
  }

  const event = eventQuery.data;
  const isPastEvent = new Date(event.startsAt).getTime() < Date.now();
  const areas = event.areas ?? [];
  const selectedArea = areas.find((area) => area.areaId === selectedAreaId);

  const actionError =
    requestAccessMutation.error ||
    createOrderMutation.error ||
    addSeatsMutation.error ||
    removeSeatsMutation.error ||
    startCheckoutMutation.error 

  const actionErrorMessage = actionError ? getApiErrorMessage(actionError) : null;

  const toggleSeat = (seatId: string) => {
    setSelectedSeatIds((prev) =>
      prev.includes(seatId)
        ? prev.filter((id) => id !== seatId)
        : [...prev, seatId]
    );
  };

  const renderSeatsByRow = (area: NonNullable<EventDTO['areas']>[number]) => {
    const grouped = area.seats.reduce<Record<string, typeof area.seats>>((acc, seat) => {
      acc[seat.row] = acc[seat.row] ?? [];
      acc[seat.row].push(seat);
      return acc;
    }, {});

    return Object.entries(grouped)
      .sort(([a], [b]) => Number(a) - Number(b))
      .map(([row, seats]) => (
        <div key={row} className="flex items-center gap-2">
          <div className="w-8 text-sm font-semibold text-slate-700">Row {row}</div>

          <div className="flex flex-wrap gap-2">
            {seats
              .sort((a, b) => Number(a.number) - Number(b.number))
              .map((seat) => {
                const selected = selectedSeatIds.includes(seat.seatId);
                const unavailable = seat.status !== 'AVAILABLE';

                return (
                  <button
                    key={seat.seatId}
                    disabled={unavailable}
                    onClick={() => toggleSeat(seat.seatId)}
                    className={
                      unavailable
                        ? 'h-9 min-w-9 rounded border border-slate-200 bg-slate-200 px-2 text-xs text-slate-400'
                        : selected
                          ? 'h-9 min-w-9 rounded border border-slate-900 bg-slate-900 px-2 text-xs font-semibold text-white'
                          : 'h-9 min-w-9 rounded border border-slate-300 bg-white px-2 text-xs text-slate-700 hover:bg-slate-100'
                    }
                    title={seat.status}
                  >
                    {seat.number}
                  </button>
                );
              })}
          </div>
        </div>
      ));
  };

  return (
    <div className="space-y-4">
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="flex flex-col gap-2 md:flex-row md:items-start md:justify-between">
          <div>
            <div className="text-sm text-slate-500">
              {new Date(event.startsAt).toLocaleString()}
            </div>

            <h1 className="mt-1 text-2xl font-bold text-slate-900">
              {event.name}
            </h1>

            <div className="mt-1 text-slate-700">{event.artist}</div>
            <div className="mt-2 text-sm text-slate-500">{event.location}</div>
          </div>

          <div className="flex items-center gap-2">
            <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-700">
              {event.status}
            </span>

            <Link
              to={`/companies/${event.companyId}`}
              className="rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-700 hover:bg-slate-200"
            >
              Company
            </Link>
          </div>
        </div>
      </div>

      {event.status !== 'PUBLISHED' && (
        <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
          This event is not published, so purchasing is not available yet.
        </div>
      )}

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h2 className="text-lg font-semibold text-slate-900">Choose area</h2>

        {areas.length === 0 ? (
          <div className="mt-3 text-sm text-slate-600">
            No areas are available for this event yet.
          </div>
        ) : (
          <div className="mt-4 grid gap-3 md:grid-cols-2">
            {areas.map((area) => (
              <button
                key={area.areaId}
                onClick={() => {
                  setSelectedAreaId(area.areaId);
                  setSelectedSeatIds([]);
                  setSuccessMessage(null);
                }}
                className={
                  selectedAreaId === area.areaId
                    ? 'rounded-xl border border-slate-900 bg-slate-50 p-4 text-left'
                    : 'rounded-xl border border-slate-200 bg-slate-50 p-4 text-left hover:border-slate-300'
                }
              >
                <div className="font-semibold text-slate-900">{area.name}</div>

                <div className="mt-1 text-sm text-slate-600">
                  {area.type} | Available: {area.availableCapacity}
                </div>

                <div className="mt-1 text-sm text-slate-600">
                  Price: {area.basePrice?.amount ?? '—'} {area.basePrice?.currency ?? ''}
                </div>
              </button>
            ))}
          </div>
        )}
      </div>

      {isPastEvent && (
        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <h2 className="text-lg font-semibold text-slate-900">
            Event closed
          </h2>

          <p className="mt-2 text-slate-600">
            This event has already taken place and tickets can no longer be purchased.
          </p>
        </div>
      )}

      {!isPastEvent && selectedArea && (
        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <h2 className="text-lg font-semibold text-slate-900">
            Event map — {selectedArea.name}
          </h2>

          {selectedArea.type === 'SEATING' && (
            <div className="mt-4">
              <div className="mb-4 rounded bg-slate-200 px-3 py-2 text-center text-sm font-semibold text-slate-700">
                Stage / Screen
              </div>

              <div className="space-y-3">
                {selectedArea.seats.length === 0 ? (
                  <div className="text-sm text-slate-600">No seats found.</div>
                ) : (
                  renderSeatsByRow(selectedArea)
                )}
              </div>

              <div className="mt-4 text-sm text-slate-600">
                Selected seats: {selectedSeatIds.length}
              </div>
            </div>
          )}

          {selectedArea.type === 'STANDING' && (
            <div className="mt-4 rounded-lg border border-dashed border-slate-300 bg-slate-50 p-6 text-center text-sm text-slate-700">
              Standing area<br />
              {selectedArea.availableCapacity} tickets available
              <div className="mt-3 text-xs text-slate-500">
                Standing purchase needs a backend quantity endpoint or exposed standing ticket IDs.
              </div>
            </div>
          )}

          <div className="mt-4 flex flex-wrap gap-2">
            <button
              onClick={async () => {
                await requestAccessMutation.mutateAsync();
                await createOrderMutation.mutateAsync(selectedArea.areaId);
              }}
              disabled={
                Boolean(activeOrderId) ||
                event.status !== 'PUBLISHED' ||
                createOrderMutation.isPending ||
                selectedArea.type !== 'SEATING'
              }
              className="rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
            >
              Start order
            </button>

            {!activeOrderId && (
              <div className="mt-2 text-sm text-slate-500">
                Start an order before adding or removing seats.
              </div>
            )}
            <button
              onClick={() => {
                if (addSeatsMutation.isPending) return;
                setSuccessMessage(null);
                addSeatsMutation.mutate();
              }}
              disabled={!activeOrderId || checkoutStarted || selectedSeatIds.length === 0 || addSeatsMutation.isPending}
              className="rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
            >
              {addSeatsMutation.isPending ? 'Adding...' : 'Add selected seats'}
            </button>

            <button
              onClick={() => {
                if (removeSeatsMutation.isPending) return;
                setSuccessMessage(null);
                removeSeatsMutation.mutate();
              }}
              disabled={!activeOrderId || checkoutStarted || selectedSeatIds.length === 0 || removeSeatsMutation.isPending}
              className="rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-sm font-semibold text-rose-900 hover:bg-rose-100 disabled:opacity-60"
            >
              {removeSeatsMutation.isPending ? 'Removing...' : 'Remove selected seats'}
            </button>
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

      {!isPastEvent && activeOrderId && (
        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <h2 className="text-lg font-semibold text-slate-900">Active order</h2>

          <div className="mt-2 text-sm text-slate-600">
            Order ID: {activeOrderId}
          </div>

          {activeOrderQuery.isPending && (
            <div className="mt-3 text-sm text-slate-600">Loading order details…</div>
          )}

          {activeOrderQuery.data && (
            <div className="mt-3">
              <div className="text-sm font-medium text-slate-700">Seats in order</div>

              {(() => {
                const order = activeOrderQuery.data;
                const seats = order.seats ?? [];
                const seatIds = order.seatIds ?? [];

                if (seats.length === 0 && seatIds.length === 0) {
                  return (
                    <div className="mt-1 text-sm text-slate-600">No seats added yet.</div>
                  );
                }

                const labels = (seats.length > 0 ? seats.map((s) => ({
                  seatId: s.seatId,
                  label: `Row ${s.row ?? '—'} Seat ${s.number ?? '—'}`,
                })) : seatIds.map((id) => ({
                  seatId: id,
                  label: 'Seat (details unavailable)',
                })));

                return (
                  <div className="mt-2 flex flex-wrap gap-2">
                    {labels.map((x) => (
                      <span
                        key={x.seatId}
                        className="rounded-md border border-slate-200 bg-slate-50 px-2 py-1 text-xs text-slate-700"
                      >
                        {x.label}
                      </span>
                    ))}
                  </div>
                );
              })()}
            </div>
          )}

          {userType !== 'member' && (
            <label className="mt-4 block">
              <div className="text-sm font-medium text-slate-700">Guest birth date</div>
              <input
                type="date"
                value={guestBirthDate}
                onChange={(e) => setGuestBirthDate(e.target.value)}
                className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
              />
            </label>
          )}

          <div className="mt-4 flex flex-wrap gap-2">
            <button
              onClick={() => {
                if (startCheckoutMutation.isPending || checkoutStarted) return;
                setSuccessMessage(null);
                startCheckoutMutation.mutate();
              }}
              disabled={startCheckoutMutation.isPending || checkoutStarted}
              className="rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
            >
              {checkoutStarted ? 'Checkout started' : 'Start checkout'}
            </button>
          </div>
        </div>
      )}

      {successMessage && (
        <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
          {successMessage}
        </div>
      )}

      {actionError && (
        <div className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
          {(actionError as Error).message}
        </div>
      )}
    </div>
  );
}