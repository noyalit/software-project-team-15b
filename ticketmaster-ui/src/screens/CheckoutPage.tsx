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
import { useEffect, useState } from 'react';

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

async function sleep(ms: number) {
  return new Promise((r) => window.setTimeout(r, ms));
}

export default function CheckoutPage() {
  const { orderId } = useParams();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const { token, userType, clearAuth } = useAuthStore();

  useEffect(() => {
    window.scrollTo({ top: 0, left: 0, behavior: 'auto' });
  }, [orderId]);

  const isCouponPolicy = (p: DiscountPolicyDTO) => {
    const anyP = p as any;
    const t = (anyP?.type ?? anyP?.policyType ?? anyP?.kind) as string | undefined;
    return t === 'COUPON' || typeof anyP?.code === 'string';
  };

  const couponPolicyExpiresAtMs = (p: DiscountPolicyDTO) => {
    const anyP = p as any;
    const raw = anyP?.expiresAt;
    if (typeof raw !== 'string' || !raw) return null;
    const ms = new Date(raw).getTime();
    return Number.isFinite(ms) ? ms : null;
  };

  const describePurchasePolicy = (p: PurchasePolicyDTO) => {
    const anyP = p as any;
    const t = (anyP?.type ?? anyP?.policyType ?? anyP?.kind) as string | undefined;
    if (t === 'MAX_TICKETS_PER_ORDER' || anyP?.max != null) {
      return `Max tickets per order: ${anyP?.max}`;
    }
    if (t === 'MIN_TICKETS_PER_ORDER' || anyP?.min != null) {
      return `Min tickets per order: ${anyP?.min}`;
    }
    if (t === 'AGE_RESTRICTION' || anyP?.minAge != null) {
      return `Age restriction: ${anyP?.minAge}+`;
    }
    if (t === 'NO_LONELY_SEAT') {
      return 'No lonely seat';
    }
    return `Unknown policy: ${t ?? 'undefined'}`;
  };

  const describeCompanyPurchasePolicy = (p: any, depth = 0): string[] => {
    if (!p || typeof p !== 'object') return ['Unknown policy'];
    const cls = String(p['@class'] ?? '');

    if (Array.isArray(p.children)) {
      const header = cls.includes('OrPurchasePolicy')
        ? 'Any of:'
        : cls.includes('AndPurchasePolicy')
          ? 'All of:'
          : 'Group:';
      const lines: string[] = [];
      lines.push(header);
      for (const child of p.children) {
        const childLines = describeCompanyPurchasePolicy(child, depth + 1);
        for (const l of childLines) lines.push('  '.repeat(depth + 1) + l);
      }
      return lines;
    }

    if (p.max != null) return [`Max tickets per order: ${p.max}`];
    if (p.minAge != null) return [`Age restriction: ${p.minAge}+`];
    if (p.min != null) return [`Min tickets per order: ${p.min}`];

    if (cls) return [cls.split('.').pop() ?? cls];
    return ['Unknown policy'];
  };

  const describeCompanyDiscountPolicy = (p: any): string => {
    if (!p || typeof p !== 'object') return 'Unknown policy';
    const cls = String(p['@class'] ?? '');

    if (p.percent != null && cls.includes('SimpleDiscountPolicy')) {
      return `Simple discount (${p.percent}%)`;
    }

    if (p.percent != null && cls.includes('ConditionalDiscountPolicy')) {
      const cond = p.condition;
      if (cond && typeof cond === 'object') {
        const condCls = String(cond['@class'] ?? '');
        if (cond.max != null && condCls.includes('MaxTicketsCondition')) {
          return `Conditional discount (${p.percent}%) when quantity <= ${cond.max}`;
        }
        if (cond.min != null && condCls.includes('MinTicketsCondition')) {
          return `Conditional discount (${p.percent}%) when quantity >= ${cond.min}`;
        }
        if (condCls.includes('TimeWindowCondition')) {
          const from = cond.from ? new Date(cond.from).toLocaleString() : null;
          const to = cond.to ? new Date(cond.to).toLocaleString() : null;
          if (from && to) return `Conditional discount (${p.percent}%) between ${from} and ${to}`;
          if (from) return `Conditional discount (${p.percent}%) from ${from}`;
          if (to) return `Conditional discount (${p.percent}%) until ${to}`;
          return `Conditional discount (${p.percent}%)`;
        }
      }
      return `Conditional discount (${p.percent}%)`;
    }

    if (cls) return cls.split('.').pop() ?? cls;
    return 'Unknown policy';
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
  const [cardNumber, setCardNumber] = useState('');
  const [cardMonth, setCardMonth] = useState('');
  const [cardYear, setCardYear] = useState('');
  const [cardHolder, setCardHolder] = useState('');
  const [cardCvv, setCardCvv] = useState('');
  const [cardHolderId, setCardHolderId] = useState('');
  const [paymentErrors, setPaymentErrors] = useState<Record<string, string>>({});
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const [nowTick, setNowTick] = useState(() => Date.now());

  useEffect(() => {
    const id = window.setInterval(() => setNowTick(Date.now()), 30_000);
    return () => window.clearInterval(id);
  }, []);

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

        if (status === 404) {
          sessionStorage.removeItem('activeOrderId');
          localStorage.removeItem('activeOrderId');
          if (!orderId) {
            navigate('/events', { replace: true });
          }
          throw new Error('Active order was not found. Please start a new order.');
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

  const companyPurchasePoliciesQuery = useQuery({
    queryKey: ['company', 'purchase-policies', eventQuery.data?.companyId],
    queryFn: async () => {
      const companyId = eventQuery.data?.companyId;
      if (!companyId) return [] as any[];
      const res = await http.get<ApiResponse<any[]>>(`/api/companies/${companyId}/purchase-policies`);
      if (res.data.error) throw new Error(res.data.error);
      return res.data.data ?? [];
    },
    enabled: Boolean(token) && Boolean(eventQuery.data?.companyId),
  });

  const companyDiscountPoliciesQuery = useQuery({
    queryKey: ['company', 'discount-policies', eventQuery.data?.companyId],
    queryFn: async () => {
      const companyId = eventQuery.data?.companyId;
      if (!companyId) return [] as any[];
      const res = await http.get<ApiResponse<any[]>>(`/api/companies/${companyId}/discount-policies`);
      if (res.data.error) throw new Error(res.data.error);
      return res.data.data ?? [];
    },
    enabled: Boolean(token) && Boolean(eventQuery.data?.companyId),
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

  const hasActiveCoupon = (discountPoliciesQuery.data ?? []).some((p) => {
    if (!isCouponPolicy(p)) return false;
    const exp = couponPolicyExpiresAtMs(p);
    if (exp == null) return false;
    return exp > nowTick;
  });

  const activeCouponCodes = new Set(
    (discountPoliciesQuery.data ?? [])
      .filter((p) => {
        if (!isCouponPolicy(p)) return false;
        const exp = couponPolicyExpiresAtMs(p);
        return exp != null && exp > nowTick;
      })
      .map((p) => String((p as any)?.code ?? ''))
      .filter(Boolean)
  );

  const enteredCouponCode = couponCode.trim();
  const shouldShowCouponPolicies = Boolean(enteredCouponCode) && activeCouponCodes.has(enteredCouponCode);

  const visibleDiscountPolicies = (discountPoliciesQuery.data ?? []).filter((p) => {
    if (!isCouponPolicy(p)) return true;
    return shouldShowCouponPolicies;
  });

  useEffect(() => {
    if (!hasActiveCoupon && couponCode) {
      setCouponCode('');
    }
  }, [hasActiveCoupon, couponCode]);

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

      const nextPaymentErrors: Record<string, string> = {};
      const cardNumberDigits = cardNumber.replace(/\s+/g, '');
      if (!cardNumberDigits) {
        nextPaymentErrors.cardNumber = 'Please enter your card number.';
      } else if (!/^\d{13,19}$/.test(cardNumberDigits)) {
        nextPaymentErrors.cardNumber = 'Card number must contain 13-19 digits.';
      }

      const monthStr = cardMonth.trim();
      const month = monthStr ? Number(monthStr) : NaN;
      if (!monthStr) {
        nextPaymentErrors.cardMonth = 'Please enter the card expiry month.';
      } else if (!Number.isInteger(month) || month < 1 || month > 12) {
        nextPaymentErrors.cardMonth = 'Month must be a number between 1 and 12.';
      }

      const yearStr = cardYear.trim();
      const normalizedYear = yearStr.length === 2 ? `20${yearStr}` : yearStr;
      const year = normalizedYear ? Number(normalizedYear) : NaN;
      if (!yearStr) {
        nextPaymentErrors.cardYear = 'Please enter the card expiry year.';
      } else if (!/^\d{2}$/.test(yearStr) && !/^\d{4}$/.test(yearStr)) {
        nextPaymentErrors.cardYear = 'Year must be 2 digits (YY) or 4 digits (YYYY).';
      } else if (!Number.isInteger(year) || year < 2000 || year > 2100) {
        nextPaymentErrors.cardYear = 'Please enter a valid year.';
      }

      const holder = cardHolder.trim();
      if (!holder) {
        nextPaymentErrors.cardHolder = 'Please enter the card holder name.';
      } else if (holder.length < 2) {
        nextPaymentErrors.cardHolder = 'Card holder name is too short.';
      }

      const cvv = cardCvv.trim();
      if (!cvv) {
        nextPaymentErrors.cardCvv = 'Please enter the CVV/CVC code.';
      } else if (!/^\d{3,4}$/.test(cvv)) {
        nextPaymentErrors.cardCvv = 'CVV must be 3-4 digits.';
      }

      const id = cardHolderId.trim();
      if (!id) {
        nextPaymentErrors.cardHolderId = 'Please enter the ID of the card holder.';
      } else if (!/^\d{5,20}$/.test(id)) {
        nextPaymentErrors.cardHolderId = 'ID must contain only digits.';
      }

      if (!nextPaymentErrors.cardMonth && !nextPaymentErrors.cardYear) {
        const now = new Date();
        const currentYear = now.getFullYear();
        const currentMonth = now.getMonth() + 1;
        if (Number.isFinite(month) && Number.isFinite(year)) {
          if (year < currentYear || (year === currentYear && month < currentMonth)) {
            nextPaymentErrors.cardYear = 'This card appears to be expired.';
          }
        }
      }

      setPaymentErrors(nextPaymentErrors);
      if (Object.keys(nextPaymentErrors).length > 0) {
        throw new Error('Please fix the payment details highlighted above.');
      }

      let orderView: ActiveOrderDTO | null = null;
      for (let attempt = 0; attempt < 5; attempt++) {
        try {
          const res = await http.get<ApiResponse<ActiveOrderDTO>>(`/api/active-orders/${activeOrderId}`);
          if (res.data.error) throw new Error(res.data.error);
          orderView = res.data.data;
          break;
        } catch (e) {
          const err = e as AxiosError<ApiResponse<unknown>>;
          if (err.response?.status === 404) {
            sessionStorage.removeItem('activeOrderId');
            localStorage.removeItem('activeOrderId');
            throw new Error('Active order was not found. Please start a new order.');
          }
          if (err.response?.status === 409) {
            if (attempt === 4) {
              const serverError = err.response?.data && typeof err.response.data === 'object' ? (err.response.data as any).error : null;
              if (typeof serverError === 'string' && serverError.trim()) {
                throw new Error(serverError);
              }
              throw e;
            }
            await sleep(Math.min(400 * (attempt + 1), 1200));
            continue;
          }
          throw e;
        }
      }

      if (!orderView) throw new Error('Checkout is temporarily unavailable. Please try again in a moment.');

      const isInCheckout = Boolean(orderView?.expiresAt);
      if (!isInCheckout) {
        try {
          if (userType === 'member') {
            const res = await http.post<ApiResponse<unknown>>(
              `/api/active-orders/${activeOrderId}/checkout/member/start`
            );
            if (res.data.error) throw new Error(res.data.error);
          } else {
            const guestBirthDate = localStorage.getItem('guestBirthDate') ?? '';
            if (!guestBirthDate) throw new Error('Please enter birth date for guest checkout.');
            const res = await http.post<ApiResponse<unknown>>(
              `/api/active-orders/${activeOrderId}/checkout/guest/start`,
              { birthDate: guestBirthDate }
            );
            if (res.data.error) throw new Error(res.data.error);
          }
        } catch (e) {
          const err = e as AxiosError<ApiResponse<unknown>>;
          if (err.response?.status !== 409) throw e;
        }
      }

      const paymentDetails = {
        cardNumber: cardNumberDigits,
        month: monthStr,
        year: normalizedYear,
        holder,
        cvv,
        id,
      };

      if (userType === 'member') {
        const res = await http.post<ApiResponse<CheckoutCompletedDTO>>(
          `/api/active-orders/${activeOrderId}/checkout/member/complete`,
          {
            couponCode: couponCode.trim() || null,
            paymentDetails,
          }
        );
        if (res.data.error) throw new Error(res.data.error);
        return res.data.data;
      }

      const res = await http.post<ApiResponse<CheckoutCompletedDTO>>(
        `/api/active-orders/${activeOrderId}/checkout/guest/complete`,
        {
          birthDate: localStorage.getItem('guestBirthDate') ?? '',
          couponCode: couponCode.trim() || null,
          paymentDetails,
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

          </div>

          <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
            <div className="text-sm font-semibold text-slate-900">Payment details</div>

            {Object.keys(paymentErrors).length > 0 && (
              <div className="mt-3 rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
                Please review the payment details below and fix the highlighted fields.
              </div>
            )}

            <div className="mt-3 grid gap-3 md:grid-cols-2">
              <label className="block md:col-span-2">
                <div className="text-xs font-medium text-slate-600">Card number</div>
                <input
                  value={cardNumber}
                  onChange={(e) => {
                    setCardNumber(e.target.value);
                    setPaymentErrors((prev) => {
                      if (!prev.cardNumber) return prev;
                      const { cardNumber, ...rest } = prev;
                      return rest;
                    });
                  }}
                  placeholder="1234123412341234"
                  className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
                />
                {paymentErrors.cardNumber && (
                  <div className="mt-1 text-xs text-rose-700">{paymentErrors.cardNumber}</div>
                )}
              </label>

              <label className="block">
                <div className="text-xs font-medium text-slate-600">Month</div>
                <input
                  value={cardMonth}
                  onChange={(e) => {
                    setCardMonth(e.target.value);
                    setPaymentErrors((prev) => {
                      if (!prev.cardMonth) return prev;
                      const { cardMonth, ...rest } = prev;
                      return rest;
                    });
                  }}
                  placeholder="MM"
                  className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
                />
                {paymentErrors.cardMonth && (
                  <div className="mt-1 text-xs text-rose-700">{paymentErrors.cardMonth}</div>
                )}
              </label>

              <label className="block">
                <div className="text-xs font-medium text-slate-600">Year</div>
                <input
                  value={cardYear}
                  onChange={(e) => {
                    setCardYear(e.target.value);
                    setPaymentErrors((prev) => {
                      if (!prev.cardYear) return prev;
                      const { cardYear, ...rest } = prev;
                      return rest;
                    });
                  }}
                  placeholder="YYYY"
                  className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
                />
                {paymentErrors.cardYear && (
                  <div className="mt-1 text-xs text-rose-700">{paymentErrors.cardYear}</div>
                )}
              </label>

              <label className="block md:col-span-2">
                <div className="text-xs font-medium text-slate-600">Card holder</div>
                <input
                  value={cardHolder}
                  onChange={(e) => {
                    setCardHolder(e.target.value);
                    setPaymentErrors((prev) => {
                      if (!prev.cardHolder) return prev;
                      const { cardHolder, ...rest } = prev;
                      return rest;
                    });
                  }}
                  placeholder="Full name"
                  className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
                />
                {paymentErrors.cardHolder && (
                  <div className="mt-1 text-xs text-rose-700">{paymentErrors.cardHolder}</div>
                )}
              </label>

              <label className="block">
                <div className="text-xs font-medium text-slate-600">CVV</div>
                <input
                  value={cardCvv}
                  onChange={(e) => {
                    setCardCvv(e.target.value);
                    setPaymentErrors((prev) => {
                      if (!prev.cardCvv) return prev;
                      const { cardCvv, ...rest } = prev;
                      return rest;
                    });
                  }}
                  placeholder="123"
                  className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
                />
                {paymentErrors.cardCvv && (
                  <div className="mt-1 text-xs text-rose-700">{paymentErrors.cardCvv}</div>
                )}
              </label>

              <label className="block">
                <div className="text-xs font-medium text-slate-600">ID</div>
                <input
                  value={cardHolderId}
                  onChange={(e) => {
                    setCardHolderId(e.target.value);
                    setPaymentErrors((prev) => {
                      if (!prev.cardHolderId) return prev;
                      const { cardHolderId, ...rest } = prev;
                      return rest;
                    });
                  }}
                  placeholder="ID number"
                  className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
                />
                {paymentErrors.cardHolderId && (
                  <div className="mt-1 text-xs text-rose-700">{paymentErrors.cardHolderId}</div>
                )}
              </label>
            </div>
          </div>

          <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
            <div className="text-sm font-semibold text-slate-900">Order summary</div>

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
            {hasActiveCoupon && (
              <label className="block text-sm font-semibold text-slate-900">
                Coupon code
                <input
                  value={couponCode}
                  onChange={(e) => setCouponCode(e.target.value)}
                  placeholder="Optional"
                  className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
                />
              </label>
            )}

            <div className="mt-4 grid gap-3">
              <div className="text-sm font-semibold text-slate-900">Policies & discounts</div>

              <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
                <div className="text-xs font-semibold uppercase tracking-wide text-slate-600">
                  Event purchase policies
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
                  Company purchase policies
                </div>

                {!eventQuery.data?.companyId ? (
                  <div className="mt-1 text-sm text-slate-600">Load the event to view policies.</div>
                ) : companyPurchasePoliciesQuery.isPending ? (
                  <div className="mt-1 text-sm text-slate-600">Loading…</div>
                ) : companyPurchasePoliciesQuery.isError ? (
                  <div className="mt-1 text-sm text-rose-700">
                    {getApiErrorMessage(companyPurchasePoliciesQuery.error)}
                  </div>
                ) : (companyPurchasePoliciesQuery.data ?? []).length === 0 ? (
                  <div className="mt-1 text-sm text-slate-600">No company purchase policies.</div>
                ) : (
                  <div className="mt-2 grid gap-1">
                    {(companyPurchasePoliciesQuery.data ?? []).flatMap((p: any, idx: number) => {
                      const lines = describeCompanyPurchasePolicy(p);
                      return lines.map((line, j) => (
                        <div key={`${idx}-${j}`} className="text-sm text-slate-800">
                          {line}
                        </div>
                      ));
                    })}
                  </div>
                )}
              </div>

              <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
                <div className="text-xs font-semibold uppercase tracking-wide text-slate-600">
                  Event discount policies
                </div>

                {!eventQuery.data?.eventId ? (
                  <div className="mt-1 text-sm text-slate-600">Load the order to view policies.</div>
                ) : discountPoliciesQuery.isPending ? (
                  <div className="mt-1 text-sm text-slate-600">Loading…</div>
                ) : discountPoliciesQuery.isError ? (
                  <div className="mt-1 text-sm text-rose-700">
                    {getApiErrorMessage(discountPoliciesQuery.error)}
                  </div>
                ) : visibleDiscountPolicies.length === 0 ? (
                  <div className="mt-1 text-sm text-slate-600">No discount policies.</div>
                ) : (
                  <div className="mt-2 grid gap-1">
                    {visibleDiscountPolicies.map((p, idx) => (
                      <div key={idx} className="text-sm text-slate-800">
                        {(() => {
                          const label = describeDiscountPolicy(p);
                          if (!isCouponPolicy(p)) return label;
                          const exp = couponPolicyExpiresAtMs(p);
                          if (exp == null) return label;
                          return exp > nowTick ? label : `${label} (expired)`;
                        })()}
                      </div>
                    ))}
                  </div>
                )}
              </div>

              <div className="rounded-lg border border-slate-200 bg-slate-50 p-3">
                <div className="text-xs font-semibold uppercase tracking-wide text-slate-600">
                  Company discount policies
                </div>

                {!eventQuery.data?.companyId ? (
                  <div className="mt-1 text-sm text-slate-600">Load the event to view policies.</div>
                ) : companyDiscountPoliciesQuery.isPending ? (
                  <div className="mt-1 text-sm text-slate-600">Loading…</div>
                ) : companyDiscountPoliciesQuery.isError ? (
                  <div className="mt-1 text-sm text-rose-700">
                    {getApiErrorMessage(companyDiscountPoliciesQuery.error)}
                  </div>
                ) : (companyDiscountPoliciesQuery.data ?? []).length === 0 ? (
                  <div className="mt-1 text-sm text-slate-600">No company discount policies.</div>
                ) : (
                  <div className="mt-2 grid gap-1">
                    {(companyDiscountPoliciesQuery.data ?? []).map((p: any, idx: number) => (
                      <div key={idx} className="text-sm text-slate-800">
                        {describeCompanyDiscountPolicy(p)}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>

            <button
              type="button"
              onClick={() => {
                if (completeCheckoutMutation.isPending) return;
                setSuccessMessage('Processing purchase…');
                completeCheckoutMutation.mutate(undefined, {
                  onError: () => {
                    setSuccessMessage(null);
                  },
                });
              }}
              disabled={
                completeCheckoutMutation.isPending || !activeOrderQuery.data
              }
              className="mt-4 inline-flex rounded-md bg-emerald-700 px-3 py-2 text-sm font-semibold text-white hover:bg-emerald-800 disabled:opacity-60"
            >
              {completeCheckoutMutation.isPending ? 'Completing…' : 'Complete purchase'}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
