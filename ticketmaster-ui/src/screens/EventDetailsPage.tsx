import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useEffect, useState } from 'react';
import { http } from '../api/http';
import type {
  ApiResponse,
  CompanyDTO,
  DiscountPolicyDTO,
  EventDTO,
  MemberDTO,
  PurchasePolicyDTO,
} from '../api/types';
import { getApiErrorMessage } from '../api/errors';
import { useAuthStore } from '../ui/authStore';
import type { AxiosError } from 'axios';

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

type LotteryEligibilityDTO = {
  status:
    | 'NO_LOTTERY_REQUIRED'
    | 'LOTTERY_OPEN_NOT_ENTERED'
    | 'LOTTERY_OPEN_ENTERED'
    | 'WON_AND_ACCESS_VALID'
    | 'NOT_SELECTED'
    | 'ACCESS_EXPIRED'
    | string;
};

export default function EventDetailsPage() {
  const { eventId } = useParams();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const { token, userType, clearAuth } = useAuthStore();

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

  const describeCompanyDiscountPolicy = (raw: any): string => {
    const p = raw?.policy && typeof raw.policy === 'object' ? raw.policy : raw;
    if (!p || typeof p !== 'object') return 'Unknown policy';

    const cls = String(p['@class'] ?? raw?.['@class'] ?? '');
    const clsShort = cls ? (cls.split('.').pop() ?? cls) : '';
    const percent =
      p.percent ??
      p.percentage ??
      p.discountPercent ??
      p.discountPercentage ??
      raw?.percent ??
      raw?.percentage;

    const condition =
      (p.condition && typeof p.condition === 'object' ? p.condition : null) ??
      (p.discountCondition && typeof p.discountCondition === 'object' ? p.discountCondition : null) ??
      (raw?.condition && typeof raw.condition === 'object' ? raw.condition : null);

    const looksLikeSimple = clsShort.includes('SimpleDiscountPolicy') || (condition == null && percent != null);
    const looksLikeConditional =
      clsShort.includes('ConditionalDiscountPolicy') ||
      (condition != null && percent != null);

    if (looksLikeSimple && percent != null) {
      return `Simple discount (${percent}%)`;
    }

    if (looksLikeConditional && percent != null) {
      const cond = condition;
      if (cond && typeof cond === 'object') {
        const condCls = String(cond['@class'] ?? '');
        if (cond.max != null || condCls.includes('MaxTicketsCondition')) {
          return cond.max != null
            ? `Conditional discount (${percent}%) when quantity <= ${cond.max}`
            : `Conditional discount (${percent}%)`;
        }
        if (cond.min != null || condCls.includes('MinTicketsCondition')) {
          return cond.min != null
            ? `Conditional discount (${percent}%) when quantity >= ${cond.min}`
            : `Conditional discount (${percent}%)`;
        }
        if (condCls.includes('TimeWindowCondition') || cond.from != null || cond.to != null) {
          const from = cond.from ? new Date(cond.from).toLocaleString() : null;
          const to = cond.to ? new Date(cond.to).toLocaleString() : null;
          if (from && to) return `Conditional discount (${percent}%) between ${from} and ${to}`;
          if (from) return `Conditional discount (${percent}%) from ${from}`;
          if (to) return `Conditional discount (${percent}%) until ${to}`;
          return `Conditional discount (${percent}%)`;
        }
      }
      return `Conditional discount (${percent}%)`;
    }

    if (clsShort) return clsShort;
    return 'Unknown policy';
  };

  const [selectedAreaId, setSelectedAreaId] = useState<string | null>(null);
  const [selectedSeatIds, setSelectedSeatIds] = useState<string[]>([]);
  const [standingQuantity, setStandingQuantity] = useState(1);
  const [guestBirthDate, setGuestBirthDate] = useState('');
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [activeOrderId, setActiveOrderId] = useState<string | null>(() => {
    const fromSession = sessionStorage.getItem('activeOrderId');
    if (fromSession) return fromSession;
    const fromLocal = localStorage.getItem('activeOrderId');
    if (fromLocal) {
      sessionStorage.setItem('activeOrderId', fromLocal);
      localStorage.removeItem('activeOrderId');
      return fromLocal;
    }
    return null;
  });
  const [checkoutStarted, setCheckoutStarted] = useState(false);

  const validateEligibility = async (input: {
    areaId: string;
    quantity: number;
    seatIds: string[];
  }) => {
    if (!eventId) throw new Error('Event ID is missing.');
    if (!input.areaId) throw new Error('Area ID is missing.');
    if (!input.quantity || input.quantity < 1) throw new Error('Quantity must be at least 1.');
    if (userType === 'member' && meQuery.isPending) {
      throw new Error('Loading your profile. Please try again in a moment.');
    }

    if (userType === 'member' && !meQuery.data?.birthDate) {
      throw new Error('Birth date is missing from your profile.');
    }

    try {
      const res = await http.post<ApiResponse<null>>(`/api/events/${eventId}/eligibility`, {
        eventId,
        areaId: input.areaId,
        buyerId: null,
        buyerBirthDate: userType === 'member'
          ? meQuery.data?.birthDate ?? null
          : guestBirthDate || null,
        quantity: input.quantity,
        seatIds: input.seatIds,
        couponCode: null,
      });
      if (res.data.error) throw new Error(res.data.error);
    } catch (e) {
      throw new Error(getApiErrorMessage(e));
    }
  };

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

  const addStandingQuantityMutation = useMutation({
    mutationFn: async () => {
      if (!activeOrderId) throw new Error('Please start an order first.');
      if (checkoutStarted) {
        throw new Error("Checkout already started. You can't change tickets now.");
      }
      if (standingQuantity < 1) throw new Error('Please select at least 1 ticket.');

      const areaId = selectedAreaId ?? activeOrderQuery.data?.areaId ?? null;
      if (!areaId) throw new Error('Please select an area first.');

      const currentCount =
        (activeOrderQuery.data?.seats?.length ?? 0) || (activeOrderQuery.data?.seatIds?.length ?? 0);
      const newQuantity = currentCount + standingQuantity;

      await validateEligibility({ areaId, quantity: newQuantity, seatIds: [] });

      const res = await http.post<ApiResponse<null>>(
        `/api/active-orders/${activeOrderId}/standing/add`,
        {
          quantity: standingQuantity,
        }
      );

      if (res.data.error) throw new Error(res.data.error);
    },
    onSuccess: async () => {
      setSuccessMessage('Tickets added to active order.');
      await qc.invalidateQueries({ queryKey: ['event', eventId] });
      await qc.invalidateQueries({ queryKey: ['active-order', activeOrderId, token] });
    },
  });

  const removeStandingQuantityMutation = useMutation({
    mutationFn: async () => {
      if (!activeOrderId) throw new Error('Please start an order first.');
      if (checkoutStarted) {
        throw new Error("Checkout already started. You can't change tickets now.");
      }
      if (standingQuantity < 1) throw new Error('Please select at least 1 ticket.');

      const res = await http.post<ApiResponse<null>>(
        `/api/active-orders/${activeOrderId}/standing/remove`,
        {
          quantity: standingQuantity,
        }
      );

      if (res.data.error) throw new Error(res.data.error);
    },
    onSuccess: async () => {
      setSuccessMessage('Tickets removed from active order.');
      await qc.invalidateQueries({ queryKey: ['event', eventId] });
      await qc.invalidateQueries({ queryKey: ['active-order', activeOrderId, token] });
    },
  });

  useEffect(() => {
    if (activeOrderId) return;
    const orders = myActiveOrdersQuery.data ?? [];
    const matching = orders.find((o) => o.eventId === eventId);
    if (!matching) return;
    setActiveOrderId(matching.orderId);
    sessionStorage.setItem('activeOrderId', matching.orderId);
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

  const companyQuery = useQuery({
    queryKey: ['company', eventQuery.data?.companyId],
    queryFn: async () => {
      const companyId = eventQuery.data?.companyId;

      if (!companyId) {
        throw new Error('Company ID is missing.');
      }

      const res = await http.get<ApiResponse<CompanyDTO>>(
        `/api/companies/${companyId}`
      );

      if (res.data.error) throw new Error(res.data.error);
      if (!res.data.data) throw new Error('Company not found');

      return res.data.data;
    },
    enabled: Boolean(token) && Boolean(eventQuery.data?.companyId),
    retry: false,
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

  const purchasePoliciesQuery = useQuery({
    queryKey: ['event', 'purchase-policies', eventId],
    queryFn: async () => {
      const res = await http.get<ApiResponse<PurchasePolicyDTO[]>>(
        `/api/events/${eventId}/purchase-policies`
      );
      if (res.data.error) throw new Error(res.data.error);
      return res.data.data ?? [];
    },
    enabled: Boolean(eventId),
  });

  const discountPoliciesQuery = useQuery({
    queryKey: ['event', 'discount-policies', eventId],
    queryFn: async () => {
      const res = await http.get<ApiResponse<DiscountPolicyDTO[]>>(
        `/api/events/${eventId}/discount-policies`
      );
      if (res.data.error) throw new Error(res.data.error);
      return res.data.data ?? [];
    },
    enabled: Boolean(eventId),
  });

  const lotteryEligibilityQuery = useQuery({
    queryKey: ['lottery', 'eligibility', eventId, token],
    queryFn: async () => {
      const event = eventQuery.data;
      if (!event) throw new Error('Event is missing.');

      const res = await http.get<ApiResponse<LotteryEligibilityDTO>>(
        `/api/companies/${event.companyId}/events/${event.eventId}/lottery/eligibility`
      );
      if (res.data.error) throw new Error(res.data.error);
      if (!res.data.data) throw new Error('No eligibility returned');
      return res.data.data;
    },
    enabled: Boolean(token) && Boolean(eventQuery.data?.companyId) && Boolean(eventId),
    retry: false,
  });

  const enterLotteryMutation = useMutation({
    mutationFn: async () => {
      const event = eventQuery.data;
      if (!event) throw new Error('Event is missing.');

      const res = await http.post<ApiResponse<null>>(
        `/api/companies/${event.companyId}/events/${event.eventId}/lottery/entries`
      );
      if (res.data.error) throw new Error(res.data.error);
    },
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ['lottery', 'eligibility', eventId, token] });
      setSuccessMessage('Registered to the lottery draw.');
    },
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
          sessionStorage.removeItem('activeOrderId');
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

      try {
        const res = await http.post<ApiResponse<unknown>>(
          `/api/active-orders/access/${eventId}`
        );

        if (res.status === 401) {
          clearAuth();
          navigate('/login');
          return null;
        }

        if (
          userType === 'member' &&
          (res.status === 410 ||
            String(res.data.error ?? '')
              .toLowerCase()
              .includes('does not have access'))
        ) {
          navigate(`/queue/${eventId}`);
          return null;
        }

        if (res.data.error) throw new Error(res.data.error);
        return res.data.data;
      } catch (e) {
        const err = e as AxiosError<ApiResponse<unknown>>;
        const status = err.response?.status;
        const message = getApiErrorMessage(e);
        const lower = message.toLowerCase();

        if (status === 401) {
          clearAuth();
          navigate('/login');
          return null;
        }

        const shouldQueue =
          status === 410 ||
          lower.includes('does not have access') ||
          lower.includes('no access') ||
          lower.includes('queue') ||
          lower.includes('capacity');

        if (userType === 'member' && shouldQueue) {
          navigate(`/queue/${eventId}`);
          return null;
        }

        throw e;
      }
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
        const err = e as AxiosError<ApiResponse<unknown>>;
        const status = err.response?.status;
        const message = getApiErrorMessage(e);

        const lower = String(message ?? '').toLowerCase();
        const shouldQueue =
          status === 410 ||
          lower.includes('does not have access') ||
          lower.includes('no access') ||
          lower.includes('queue') ||
          lower.includes('capacity');

        if (userType === 'member' && shouldQueue) {
          navigate(`/queue/${eventId}`);
          throw e;
        }

        const isAlreadyHasActiveOrder =
          lower.includes('already has an active order') ||
          lower.includes('active order request conflicts with existing data');

        if (isAlreadyHasActiveOrder) {
          try {
            const res2 = await http.get<
              ApiResponse<Array<{ orderId: string; eventId?: string }>>
            >('/api/active-orders/my');
            const orders = res2.data.data ?? [];
            const matching = orders.find((o) => o.eventId === eventId);
            if (matching?.orderId) {
              setActiveOrderId(matching.orderId);
              sessionStorage.setItem('activeOrderId', matching.orderId);
              return matching.orderId;
            }
          } catch {
            // fall through
          }

          throw new Error(message);
        }

        throw new Error(message || 'Failed to start order.');
      }
    },

    onSuccess: async (orderId) => {
      setActiveOrderId(orderId);
      sessionStorage.setItem('activeOrderId', orderId);
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

      const areaId = selectedAreaId ?? activeOrderQuery.data?.areaId ?? null;
      if (!areaId) throw new Error('Please select an area first.');

      const existing = (activeOrderQuery.data?.seatIds ?? []).map(String);
      const combinedSeatIds = Array.from(new Set([...existing, ...selectedSeatIds]));
      const newQuantity = combinedSeatIds.length;

      await validateEligibility({ areaId, quantity: newQuantity, seatIds: combinedSeatIds });

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

      const areaId = activeOrderQuery.data?.areaId ?? selectedAreaId;
      if (!areaId) throw new Error('Area ID is missing.');

      const seatIds = (activeOrderQuery.data?.seatIds ?? []).map(String);
      const quantity =
        (activeOrderQuery.data?.seats?.length ?? 0) || (activeOrderQuery.data?.seatIds?.length ?? 0);
      if (!quantity) throw new Error('No tickets in order.');

      await validateEligibility({ areaId, quantity, seatIds });

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

  const eligibilityStatus = lotteryEligibilityQuery.data?.status ?? null;

  const actionError =
    requestAccessMutation.error ||
    createOrderMutation.error ||
    addSeatsMutation.error ||
    removeSeatsMutation.error ||
    addStandingQuantityMutation.error ||
    removeStandingQuantityMutation.error ||
    startCheckoutMutation.error 

  const actionErrorMessage = actionError ? getApiErrorMessage(actionError) : null;
  const isMemberProfileLoading = userType === 'member' && meQuery.isPending;

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

          <div className="flex items-center gap-2">
            <span className="rounded border border-slate-200 bg-white px-2 py-1 text-[10px] font-semibold uppercase tracking-wide text-slate-600">
              Exit
            </span>

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

            <span className="rounded border border-slate-200 bg-white px-2 py-1 text-[10px] font-semibold uppercase tracking-wide text-slate-600">
              Exit
            </span>
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
          <div className="rounded-full bg-slate-100 px-3 py-1 text-xs font-medium text-slate-700">
            {token && companyQuery.isPending
              ? 'Loading company...'
              : companyQuery.data?.name ?? 'Company'}
          </div>
        </div>

        <div className="mt-4 grid gap-3 md:grid-cols-2">
          <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
            <div className="text-xs font-semibold uppercase tracking-wide text-slate-600">
              Event purchase policies
            </div>

            {purchasePoliciesQuery.isPending ? (
              <div className="mt-2 text-sm text-slate-600">Loading…</div>
            ) : purchasePoliciesQuery.isError ? (
              <div className="mt-2 text-sm text-rose-700">
                {getApiErrorMessage(purchasePoliciesQuery.error)}
              </div>
            ) : (purchasePoliciesQuery.data ?? []).length === 0 ? (
              <div className="mt-2 text-sm text-slate-600">No purchase policies.</div>
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

          <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
            <div className="text-xs font-semibold uppercase tracking-wide text-slate-600">
              Company purchase policies
            </div>

            {companyPurchasePoliciesQuery.isPending ? (
              <div className="mt-2 text-sm text-slate-600">Loading…</div>
            ) : companyPurchasePoliciesQuery.isError ? (
              <div className="mt-2 text-sm text-rose-700">
                {getApiErrorMessage(companyPurchasePoliciesQuery.error)}
              </div>
            ) : (companyPurchasePoliciesQuery.data ?? []).length === 0 ? (
              <div className="mt-2 text-sm text-slate-600">No company purchase policies.</div>
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

          <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
            <div className="text-xs font-semibold uppercase tracking-wide text-slate-600">
              Event discount policies
            </div>

            {discountPoliciesQuery.isPending ? (
              <div className="mt-2 text-sm text-slate-600">Loading…</div>
            ) : discountPoliciesQuery.isError ? (
              <div className="mt-2 text-sm text-rose-700">
                {getApiErrorMessage(discountPoliciesQuery.error)}
              </div>
            ) : (discountPoliciesQuery.data ?? []).length === 0 ? (
              <div className="mt-2 text-sm text-slate-600">No discount policies.</div>
            ) : (
              <div className="mt-2 grid gap-1">
                {(discountPoliciesQuery.data ?? []).map((p, idx) => (
                  <div key={idx} className="text-sm text-slate-800">
                    {p.type === 'COUPON'
                      ? `Coupon ${(p as any).code} (${(p as any).percentage}%)`
                      : p.type === 'EARLY_BIRD'
                        ? `Early bird (${(p as any).percentage}%)`
                        : p.type}
                  </div>
                ))}
              </div>
            )}
          </div>

          <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
            <div className="text-xs font-semibold uppercase tracking-wide text-slate-600">
              Company discount policies
            </div>

            {companyDiscountPoliciesQuery.isPending ? (
              <div className="mt-2 text-sm text-slate-600">Loading…</div>
            ) : companyDiscountPoliciesQuery.isError ? (
              <div className="mt-2 text-sm text-rose-700">
                {getApiErrorMessage(companyDiscountPoliciesQuery.error)}
              </div>
            ) : (companyDiscountPoliciesQuery.data ?? []).length === 0 ? (
              <div className="mt-2 text-sm text-slate-600">No company discount policies.</div>
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

              <div className="mb-3 flex items-center justify-between text-xs font-semibold text-slate-600">
                <span className="rounded border border-slate-200 bg-white px-2 py-1 uppercase tracking-wide">
                  Exit
                </span>
                <span className="rounded border border-slate-200 bg-white px-2 py-1 uppercase tracking-wide">
                  Exit
                </span>
              </div>

              <div className="space-y-3">
                {selectedArea.seats.length === 0 ? (
                  <div className="text-sm text-slate-600">No seats found.</div>
                ) : (
                  renderSeatsByRow(selectedArea)
                )}
              </div>

              <div className="mt-3 flex items-center justify-between text-xs font-semibold text-slate-600">
                <span className="rounded border border-slate-200 bg-white px-2 py-1 uppercase tracking-wide">
                  Exit
                </span>
                <span className="rounded border border-slate-200 bg-white px-2 py-1 uppercase tracking-wide">
                  Exit
                </span>
              </div>

              <div className="mt-4 text-sm text-slate-600">
                Selected seats: {selectedSeatIds.length}
              </div>
            </div>
          )}

          {selectedArea.type === 'STANDING' && (
            <div className="mt-4 rounded-lg border border-slate-200 bg-slate-50 p-6">
              <div className="mb-4 rounded bg-slate-200 px-3 py-2 text-center text-sm font-semibold text-slate-700">
                Stage / Screen
              </div>

              <div className="mb-3 flex items-center justify-between text-xs font-semibold text-slate-600">
                <span className="rounded border border-slate-200 bg-white px-2 py-1 uppercase tracking-wide">
                  Exit
                </span>
                <span className="rounded border border-slate-200 bg-white px-2 py-1 uppercase tracking-wide">
                  Exit
                </span>
              </div>

              <div className="rounded-lg border border-dashed border-slate-300 bg-white p-6 text-center">
                <div className="text-sm font-semibold text-slate-900">Standing area</div>
                <div className="mt-1 text-sm text-slate-700">
                  {selectedArea.availableCapacity} tickets available
                </div>
              </div>

              <div className="mt-3 flex items-center justify-between text-xs font-semibold text-slate-600">
                <span className="rounded border border-slate-200 bg-white px-2 py-1 uppercase tracking-wide">
                  Exit
                </span>
                <span className="rounded border border-slate-200 bg-white px-2 py-1 uppercase tracking-wide">
                  Exit
                </span>
              </div>

              <label className="mt-4 block">
                <div className="text-sm font-medium text-slate-700">Quantity</div>
                <div className="mt-1 flex items-center gap-2">
                  <span className="rounded border border-slate-200 bg-white px-2 py-1 text-[10px] font-semibold uppercase tracking-wide text-slate-600">
                    Exit
                  </span>
                  <input
                    type="number"
                    min={1}
                    value={standingQuantity}
                    onChange={(e) => setStandingQuantity(Number(e.target.value || 1))}
                    className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
                  />
                  <span className="rounded border border-slate-200 bg-white px-2 py-1 text-[10px] font-semibold uppercase tracking-wide text-slate-600">
                    Exit
                  </span>
                </div>
              </label>
            </div>
          )}

          {eligibilityStatus === 'LOTTERY_OPEN_NOT_ENTERED' && (
            <div className="mt-4 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900">
              This event has a lottery draw. Register to the draw. You can purchase only if you win after the draw.
            </div>
          )}

          {eligibilityStatus === 'LOTTERY_OPEN_ENTERED' && (
            <div className="mt-4 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-900">
              You are registered to the lottery draw. You can purchase only if you win after the draw.
            </div>
          )}

          {eligibilityStatus === 'NOT_SELECTED' && (
            <div className="mt-4 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
              You were not selected in the lottery draw for this event.
            </div>
          )}

          <div className="mt-4 flex flex-wrap gap-2">
            {userType === 'member' && eligibilityStatus === 'LOTTERY_OPEN_NOT_ENTERED' && (
              <button
                onClick={() => enterLotteryMutation.mutate()}
                disabled={enterLotteryMutation.isPending}
                className="rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-800 hover:bg-slate-50 disabled:opacity-60"
              >
                {enterLotteryMutation.isPending ? 'Registering…' : 'Register to draw'}
              </button>
            )}
            <button
              onClick={async () => {
                try {
                  const access = await requestAccessMutation.mutateAsync();
                  if (access === null) return;
                  await createOrderMutation.mutateAsync(selectedArea.areaId);
                } catch {
                  // errors are displayed via actionErrorMessage
                }
              }}
              disabled={
                Boolean(activeOrderId) ||
                !token ||
                isMemberProfileLoading ||
                event.status !== 'PUBLISHED' ||
                (userType === 'member' &&
                  eligibilityStatus !== null &&
                  eligibilityStatus !== 'NO_LOTTERY_REQUIRED' &&
                  eligibilityStatus !== 'WON_AND_ACCESS_VALID') ||
                createOrderMutation.isPending
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
            {selectedArea.type === 'SEATING' ? (
              <>
                <button
                  onClick={() => {
                    if (addSeatsMutation.isPending) return;
                    setSuccessMessage(null);
                    addSeatsMutation.mutate();
                  }}
                  disabled={
                    !activeOrderId ||
                    checkoutStarted ||
                    selectedSeatIds.length === 0 ||
                    addSeatsMutation.isPending ||
                    isMemberProfileLoading
                  }
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
              </>
            ) : (
              <>
                <button
                  onClick={() => {
                    if (addStandingQuantityMutation.isPending) return;
                    setSuccessMessage(null);
                    addStandingQuantityMutation.mutate();
                  }}
                  disabled={
                    !activeOrderId ||
                    checkoutStarted ||
                    addStandingQuantityMutation.isPending ||
                    isMemberProfileLoading
                  }
                  className="rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
                >
                  {addStandingQuantityMutation.isPending ? 'Adding...' : 'Add tickets'}
                </button>

                <button
                  onClick={() => {
                    if (removeStandingQuantityMutation.isPending) return;
                    setSuccessMessage(null);
                    removeStandingQuantityMutation.mutate();
                  }}
                  disabled={!activeOrderId || checkoutStarted || removeStandingQuantityMutation.isPending}
                  className="rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-sm font-semibold text-rose-900 hover:bg-rose-100 disabled:opacity-60"
                >
                  {removeStandingQuantityMutation.isPending ? 'Removing...' : 'Remove tickets'}
                </button>
              </>
            )}
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
          <h2 className="text-lg font-semibold text-slate-900">
            Active order
          </h2>

          <div className="mt-4" />

          <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
            <div className="text-xs font-semibold uppercase tracking-wide text-slate-600">
              Purchase policies
            </div>

            {purchasePoliciesQuery.isPending ? (
              <div className="mt-2 text-sm text-slate-600">Loading…</div>
            ) : purchasePoliciesQuery.isError ? (
              <div className="mt-2 text-sm text-rose-700">
                {getApiErrorMessage(purchasePoliciesQuery.error)}
              </div>
            ) : (purchasePoliciesQuery.data ?? []).length === 0 ? (
              <div className="mt-2 text-sm text-slate-600">No purchase policies.</div>
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

                const orderAreaType = eventQuery.data?.areas?.find((a) => a.areaId === order.areaId)?.type;
                const isStanding = orderAreaType === 'STANDING';
                const standingCount = seats.length > 0 ? seats.length : seatIds.length;

                if (isStanding) {
                  return (
                    <div className="mt-2 text-sm text-slate-700">
                      Standing (x{standingCount})
                    </div>
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
              disabled={
                startCheckoutMutation.isPending ||
                checkoutStarted ||
                isMemberProfileLoading
              }
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