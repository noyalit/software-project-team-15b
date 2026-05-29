import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { http } from '../api/http';
import type { ApiResponse } from '../api/types';
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
  const { token, userType } = useAuthStore();

  const [couponCode, setCouponCode] = useState('');
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const activeOrderId = orderId ?? localStorage.getItem('activeOrderId') ?? null;

  const activeOrderQuery = useQuery({
    queryKey: ['active-order', activeOrderId, token],
    queryFn: async () => {
      if (!activeOrderId) throw new Error('No active order found');
      const res = await http.get<ApiResponse<ActiveOrderDTO>>(
        `/api/active-orders/${activeOrderId}`
      );
      if (res.data.error) throw new Error(res.data.error);
      if (!res.data.data) throw new Error('Active order not found');
      return res.data.data;
    },
    enabled: Boolean(activeOrderId) && Boolean(token),
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

      throw new Error('Guest checkout is not supported on this page yet.');
    },
    onSuccess: async () => {
      setSuccessMessage('Purchase completed successfully.');
      localStorage.removeItem('activeOrderId');
      await qc.invalidateQueries({ queryKey: ['event'] });
      await qc.invalidateQueries({ queryKey: ['active-order'] });
      navigate('/orders', { replace: true });
    },
  });

  const actionError = activeOrderQuery.error || completeCheckoutMutation.error;
  const actionErrorMessage = actionError ? getApiErrorMessage(actionError) : null;

  const formatMoney = (m?: { amount: number; currency: string } | null) => {
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
              {(activeOrderQuery.data.seats ?? []).length > 0 ? (
                (activeOrderQuery.data.seats ?? []).map((s) => (
                  <div key={s.seatId} className="text-sm text-slate-900">
                    Row {s.row ?? '—'} Seat {s.number ?? '—'}
                  </div>
                ))
              ) : (
                <div className="text-sm text-slate-600">No seats found.</div>
              )}
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
                  {formatMoney(activeOrderQuery.data.subtotal)}
                </span>
              </div>

              <div className="flex items-center justify-between text-sm">
                <span className="text-slate-600">Total</span>
                <span className="font-semibold text-slate-900">
                  {formatMoney(activeOrderQuery.data.total)}
                </span>
              </div>
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

            <button
              onClick={() => {
                if (completeCheckoutMutation.isPending) return;
                setSuccessMessage(null);
                completeCheckoutMutation.mutate();
              }}
              disabled={completeCheckoutMutation.isPending}
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
