import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router-dom';
import type { AxiosError } from 'axios';
import { http } from '../api/http';
import type {
  ApiResponse,
  DiscountPolicyDTO,
  EventDTO,
  MemberDTO,
  PriceBreakdownDTO,
  PurchasePolicyDTO,
} from '../api/types';
import { getApiErrorMessage } from '../api/errors';
import { useAuthStore } from '../ui/authStore';
import { useState } from 'react';

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
  expiresAt?: string | null;
  basePricePerSeat?: { amount: number; currency: string } | null;
  subtotal?: { amount: number; currency: string } | null;
  total?: { amount: number; currency: string } | null;
};

type CheckoutCompletedDTO = {
  orderId?: string;
};

export default function CheckoutPage() {
  const { orderId } = useParams();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const { token, userType, clearAuth } = useAuthStore();

  const describePurchasePolicy = (p: PurchasePolicyDTO) => {
    const anyP = p as any;
    const t = (anyP?.type ?? anyP?.policyType ?? anyP?.kind) as string | undefined;
    if (t === 'MAX_TICKETS_PER_ORDER' || anyP?.max != null) {
      return `Max tickets per order: ${anyP?.max}`;
    }
    if (t === 'AGE_RESTRICTION' || anyP?.minAge != null) {
      return `Age restriction: ${anyP?.minAge}+`;
    }
    if (t === 'NO_LONELY_SEAT') {
      return 'No lonely seat';
    }
    return `Unknown policy: ${t ?? 'undefined'}`;
  };

  const describeDiscountPolicy = (p: DiscountPolicyDTO) => {
    const anyP = p as any;
    const t = (anyP?.type ?? anyP?.policyType ?? anyP?.kind) as string | undefined;
    if (t === 'COUPON' || anyP?.code != null) {
      return `Coupon ${anyP?.code} (${anyP?.percentage}%)`;
    }
    if (t === 'EARLY_BIRD' || anyP?.until != null) {
      return `Early bird (${anyP?.percentage}%)`;
    }
    return `Unknown policy: ${t ?? 'undefined'}`;
  };

  const [couponCode, setCouponCode] = useState('');
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const activeOrderId =
    orderId ?? sessionStorage.getItem('activeOrderId') ?? localStorage.getItem('activeOrderId') ?? null;

  const checkoutCompleted = Boolean(successMessage);

  const guestBirthDate =
    userType === 'guest' ? (localStorage.getItem('guestBirthDate') ?? '') : '';

  const meQuery = useQuery({
    queryKey: ['me', token],
    queryFn: async () => {
      const res = await http.get<ApiResponse<MemberDTO>>('/api/users/me');
      if (res.data.error) throw new Error(res.data.error);
      if (!res.data.data) throw new Error('No profile data');
      return res.data.data;
    },
    enabled: Boolean(token) && userType === 'member',
    retry: false,
  });

  const activeOrderQuery = useQuery({
    queryKey: ['active-order', activeOrderId, token],
    queryFn: async () => {
      if (!activeOrderId) throw new Error('No active order found');
      try {
        const res = await http.get<ApiResponse<ActiveOrderDTO>>(
          `/api/active-orders/${activeOrderId}`
        );
        if (res.data.error) throw new Error(res.data.error);
        if (!res.data.data) throw new Error('Active order not found');
        return res.data.data;
      } catch (e) {
        const err = e as AxiosError<ApiResponse<ActiveOrderDTO>>;
        const status = err.response?.status;

        if (status === 401) {
          clearAuth();
          throw new Error('Your session expired. Please log in again.');
        }

        if (status === 410) {
          sessionStorage.removeItem('activeOrderId');
          localStorage.removeItem('activeOrderId');
          if (!orderId) {
            navigate('/events', { replace: true });
          }
          throw new Error('This active order has expired. Please start a new order.');
        }

        if (status === 409) {
          throw new Error('Order is being updated. Please wait a moment…');
        }

        throw e;
      }
    },
    enabled: Boolean(activeOrderId) && Boolean(token) && !checkoutCompleted,
    retry: (failureCount, error) => {
      const err = error as AxiosError<ApiResponse<unknown>>;
      if (err.response?.status === 409) {
        return failureCount < 5;
      }
      return false;
    },
    retryDelay: (attemptIndex) => Math.min(1000 * (attemptIndex + 1), 3000),
  });

  const eventQuery = useQuery({
    queryKey: ['event', activeOrderQuery.data?.eventId],
    queryFn: async () => {
      const eventId = activeOrderQuery.data?.eventId;
      if (!eventId) throw new Error('Event ID is missing');
      const res = await http.get<ApiResponse<EventDTO>>(`/api/events/${eventId}`);
      if (res.data.error) throw new Error(res.data.error);
      if (!res.data.data) throw new Error('Event not found');
      return res.data.data;
    },
    enabled: Boolean(activeOrderQuery.data?.eventId),
    staleTime: 60_000,
  });

  const purchasePoliciesQuery = useQuery({
    queryKey: ['event', 'purchase-policies', eventQuery.data?.eventId],
    queryFn: async () => {
      const eventId = eventQuery.data?.eventId;
      if (!eventId) return [] as PurchasePolicyDTO[];
      const res = await http.get<ApiResponse<PurchasePolicyDTO[]>>(
        `/api/events/${eventId}/purchase-policies`
      );
      if (res.data.error) throw new Error(res.data.error);
      return res.data.data ?? [];
    },
    enabled: Boolean(eventQuery.data?.eventId),
  });

  const discountPoliciesQuery = useQuery({
    queryKey: ['event', 'discount-policies', eventQuery.data?.eventId],
    queryFn: async () => {
      const eventId = eventQuery.data?.eventId;
      if (!eventId) return [] as DiscountPolicyDTO[];
      const res = await http.get<ApiResponse<DiscountPolicyDTO[]>>(
        `/api/events/${eventId}/discount-policies`
      );
      if (res.data.error) throw new Error(res.data.error);
      return res.data.data ?? [];
    },
    enabled: Boolean(eventQuery.data?.eventId),
  });

  const priceQuoteQuery = useQuery({
    queryKey: [
      'event',
      'price-quote',
      activeOrderQuery.data?.eventId,
      activeOrderQuery.data?.areaId,
      activeOrderQuery.data?.seatIds?.length,
      activeOrderQuery.data?.seats?.length,
      couponCode,
      meQuery.data?.userId,
      meQuery.data?.birthDate,
      guestBirthDate,
    ],
    queryFn: async () => {
      const eventId = activeOrderQuery.data?.eventId;
      const areaId = activeOrderQuery.data?.areaId;
      if (!eventId || !areaId) throw new Error('Missing event or area');

      const seats = activeOrderQuery.data?.seats ?? [];
      const seatIds = activeOrderQuery.data?.seatIds ?? [];
      const quantity = seats.length > 0 ? seats.length : seatIds.length;
      if (!quantity) throw new Error('No seats in order');

      const buyerId = userType === 'member' ? (meQuery.data?.userId ?? null) : null;
      const buyerBirthDate =
        userType === 'member'
          ? (meQuery.data?.birthDate ?? null)
          : (guestBirthDate || null);

      const res = await http.post<ApiResponse<PriceBreakdownDTO>>(
        `/api/events/${eventId}/price-quote`,
        {
          areaId,
          quantity,
          buyerId,
          buyerBirthDate,
          couponCode: couponCode.trim() || null,
        }
      );
      if (res.data.error) throw new Error(res.data.error);
      if (!res.data.data) throw new Error('No price quote returned');
      return res.data.data;
    },
    enabled:
      Boolean(token) &&
      Boolean(activeOrderQuery.data?.eventId) &&
      Boolean(activeOrderQuery.data?.areaId) &&
      !checkoutCompleted,
    retry: false,
  });

  const completeCheckoutMutation = useMutation({
    mutationFn: async () => {
      if (!activeOrderId) throw new Error('No active order found');

      if (userType === 'member') {
        const res = await http.post<ApiResponse<CheckoutCompletedDTO>>(
          `/api/active-orders/${activeOrderId}/checkout/member/complete`,
          {
            couponCode: couponCode.trim() || null,
          }
        );
        if (res.data.error) throw new Error(res.data.error);
        return res.data.data;
      }

      const guestBirthDate = localStorage.getItem('guestBirthDate') ?? '';
      if (!guestBirthDate) throw new Error('Please enter birth date for guest checkout.');

      const res = await http.post<ApiResponse<CheckoutCompletedDTO>>(
        `/api/active-orders/${activeOrderId}/checkout/guest/complete`,
        {
          birthDate: guestBirthDate,
          couponCode: couponCode.trim() || null,
        }
      );
      if (res.data.error) throw new Error(res.data.error);
      return res.data.data;
    },
    onSuccess: async () => {
      setSuccessMessage('Purchase completed successfully.');
      sessionStorage.removeItem('activeOrderId');
      localStorage.removeItem('activeOrderId');
      await qc.invalidateQueries({ queryKey: ['event'] });
      await qc.invalidateQueries({ queryKey: ['active-order'] });
      if (activeOrderId && token) {
        await qc.invalidateQueries({ queryKey: ['active-order', activeOrderId, token] });
      }

      if (userType === 'member') {
        navigate('/orders', { replace: true });
      } else {
        navigate('/events', { replace: true });
      }
    },
  });

  const actionError = activeOrderQuery.error || completeCheckoutMutation.error;
  const actionErrorMessage =
    !successMessage && actionError ? getApiErrorMessage(actionError) : null;

  const formatMoney = (m?: { amount: number | string; currency: string } | null) => {
    if (!m) return '—';
    return `${m.amount} ${m.currency}`;
  };

  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
      <div className="flex items-center justify-between gap-4">
        <h1 className="text-2xl font-extrabold text-slate-900">Checkout</h1>
        <Link
          to={activeOrderQuery.data?.eventId ? `/events/${activeOrderQuery.data.eventId}` : '/events'}
          className="text-sm font-semibold text-slate-700 hover:text-slate-900"
        >
          Back to event
        </Link>
      </div>

      {successMessage && (
        <div className="mt-4 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
          {successMessage}
        </div>
      )}

      {actionErrorMessage && (
        <div className="mt-4 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
          {actionErrorMessage}
        </div>
      )}

      {activeOrderQuery.isPending && (
        <div className="mt-4 text-sm text-slate-600">Loading order…</div>
      )}

      {activeOrderQuery.data && (
        <div className="mt-6 grid gap-4">
          <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
            <div className="text-sm text-slate-600">Order ID</div>
            <div className="mt-1 font-mono text-sm text-slate-900">
              {activeOrderQuery.data.orderId}
            </div>

            <div className="mt-4 text-sm text-slate-600">Selected seats</div>
            <div className="mt-1 grid gap-1">
              {(() => {
                const seats = activeOrderQuery.data?.seats ?? [];
                const seatIds = activeOrderQuery.data?.seatIds ?? [];
                const areaType = eventQuery.data?.areas?.find((a) => a.areaId === activeOrderQuery.data?.areaId)?.type;
                const isStanding = areaType === 'STANDING';
                const standingCount = seats.length > 0 ? seats.length : seatIds.length;
                if (isStanding) {
                  return (
                    <div className="text-sm text-slate-900">
                      Standing (x{standingCount})
                    </div>
                  );
                }

                if (seats.length === 0 && seatIds.length === 0) {
                  return <div className="text-sm text-slate-600">No seats found.</div>;
                }

                return seats.map((s) => (
                  <div key={s.seatId} className="text-sm text-slate-900">
                    Row {s.row ?? '—'} Seat {s.number ?? '—'}
                  </div>
                ));
              })()}
            </div>

            <div className="mt-6 grid gap-2">
              <div className="text-sm font-semibold text-slate-900">Cost details</div>

              <div className="flex items-center justify-between text-sm">
                <span className="text-slate-600">Price per seat</span>
                <span className="font-medium text-slate-900">
                  {formatMoney(activeOrderQuery.data.basePricePerSeat)}
                </span>
              </div>

              <div className="flex items-center justify-between text-sm">
                <span className="text-slate-600">Subtotal</span>
                <span className="font-medium text-slate-900">
                  {priceQuoteQuery.data
                    ? formatMoney(priceQuoteQuery.data.subtotal)
                    : formatMoney(activeOrderQuery.data.subtotal)}
                </span>
              </div>

              <div className="flex items-center justify-between text-sm">
                <span className="text-slate-600">Discount</span>
                <span className="font-medium text-slate-900">
                  {priceQuoteQuery.data ? formatMoney(priceQuoteQuery.data.discount) : '—'}
                </span>
              </div>

              <div className="flex items-center justify-between text-sm">
                <span className="text-slate-600">Total</span>
                <span className="font-semibold text-slate-900">
                  {priceQuoteQuery.data
                    ? formatMoney(priceQuoteQuery.data.total)
                    : formatMoney(activeOrderQuery.data.total)}
                </span>
              </div>

              {priceQuoteQuery.isError && (
                <div className="mt-2 rounded border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900">
                  {getApiErrorMessage(priceQuoteQuery.error)}
                </div>
              )}
            </div>
          </div>

          <div className="rounded-xl border border-slate-200 bg-white p-4">
            <label className="block text-sm font-semibold text-slate-900">
              Coupon code
              <input
                value={couponCode}
                onChange={(e) => setCouponCode(e.target.value)}
                placeholder="Optional"
                className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
              />
            </label>

            <div className="mt-4 grid gap-3">
              <div className="text-sm font-semibold text-slate-900">Policies & discounts</div>

              <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
                <div className="text-xs font-semibold uppercase tracking-wide text-slate-600">
                  Purchase policies
                </div>

                {!eventQuery.data?.eventId ? (
                  <div className="mt-1 text-sm text-slate-600">Load the order to view policies.</div>
                ) : purchasePoliciesQuery.isPending ? (
                  <div className="mt-1 text-sm text-slate-600">Loading…</div>
                ) : purchasePoliciesQuery.isError ? (
                  <div className="mt-1 text-sm text-rose-700">
                    {getApiErrorMessage(purchasePoliciesQuery.error)}
                  </div>
                ) : (purchasePoliciesQuery.data ?? []).length === 0 ? (
                  <div className="mt-1 text-sm text-slate-600">No purchase policies.</div>
                ) : (
                  <div className="mt-2 grid gap-1">
                    {(purchasePoliciesQuery.data ?? []).map((p, idx) => (
                      <div key={idx} className="text-sm text-slate-800">
                        {describePurchasePolicy(p)}
                      </div>
                    ))}
                  </div>
                )}
              </div>

              <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
                <div className="text-xs font-semibold uppercase tracking-wide text-slate-600">
                  Discount policies
                </div>

                {!eventQuery.data?.eventId ? (
                  <div className="mt-1 text-sm text-slate-600">Load the order to view policies.</div>
                ) : discountPoliciesQuery.isPending ? (
                  <div className="mt-1 text-sm text-slate-600">Loading…</div>
                ) : discountPoliciesQuery.isError ? (
                  <div className="mt-1 text-sm text-rose-700">
                    {getApiErrorMessage(discountPoliciesQuery.error)}
                  </div>
                ) : (discountPoliciesQuery.data ?? []).length === 0 ? (
                  <div className="mt-1 text-sm text-slate-600">No discount policies.</div>
                ) : (
                  <div className="mt-2 grid gap-1">
                    {(discountPoliciesQuery.data ?? []).map((p, idx) => (
                      <div key={idx} className="text-sm text-slate-800">
                        {describeDiscountPolicy(p)}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>

            <button
              onClick={() => {
                if (completeCheckoutMutation.isPending) return;
                setSuccessMessage(null);
                completeCheckoutMutation.mutate();
              }}
              disabled={
                completeCheckoutMutation.isPending ||
                (activeOrderQuery.isError &&
                  !getApiErrorMessage(activeOrderQuery.error)
                    .toLowerCase()
                    .includes('order is being updated'))
              }
              className="mt-4 inline-flex rounded-md bg-emerald-700 px-3 py-2 text-sm font-semibold text-white hover:bg-emerald-800 disabled:opacity-60"
            >
              Complete purchase
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
