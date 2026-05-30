import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { http } from '../api/http';
import type { ApiResponse } from '../api/types';
import { getApiErrorMessage } from '../api/errors';
import { useAuthStore } from '../ui/authStore';

type ActiveOrderDTO = {
  orderId: string;
  eventId?: string;
};

type MoneyDTO = {
  amount: number;
  currency: string;
};

type OrderHistoryDTO = {
  orderId: string;
  eventId: string;
  totalPrice?: MoneyDTO | null;
  tickets: Array<{ seatId: string; basePrice?: MoneyDTO | null }>;
  cancelled: boolean;
};

export default function OrdersPage() {
  const { token } = useAuthStore();
  const myActiveOrdersQuery = useQuery({
    queryKey: ['active-orders', 'my', token],
    queryFn: async () => {
      const res = await http.get<ApiResponse<ActiveOrderDTO[]>>('/api/active-orders/my');
      if (res.data.error) throw new Error(res.data.error);
      return res.data.data ?? [];
    },
    enabled: Boolean(token),
  });

  const orderHistoryQuery = useQuery({
    queryKey: ['order-history', 'my-orders', token],
    queryFn: async () => {
      const res = await http.get<ApiResponse<OrderHistoryDTO[]>>(
        '/api/order-history/my-orders'
      );
      if (res.data.error) throw new Error(res.data.error);
      return res.data.data ?? [];
    },
    enabled: Boolean(token),
  });

  const formatMoney = (m?: MoneyDTO | null) => {
    if (!m) return '—';
    return `${m.amount} ${m.currency}`;
  };

  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
      <h1 className="text-2xl font-extrabold text-slate-900">My Orders</h1>

      {myActiveOrdersQuery.isPending && (
        <p className="mt-2 text-slate-600">Loading active orders...</p>
      )}

      {myActiveOrdersQuery.isError && (
        <div className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
          {getApiErrorMessage(myActiveOrdersQuery.error)}
        </div>
      )}

      {!myActiveOrdersQuery.isPending && !myActiveOrdersQuery.isError && (
        <div className="mt-4">
          <div className="text-lg font-bold text-slate-900">Active orders</div>

          {(myActiveOrdersQuery.data ?? []).length === 0 ? (
            <p className="mt-2 text-slate-600">You do not have an active order.</p>
          ) : (
            <div className="mt-3 grid gap-3">
              {(myActiveOrdersQuery.data ?? []).map((o) => (
                <div
                  key={o.orderId}
                  className="rounded-xl border border-slate-200 bg-slate-50 p-4"
                >
                  <div className="text-sm font-semibold text-slate-900">Active order</div>
                  <div className="mt-1 text-sm text-slate-600">Order ID: {o.orderId}</div>

                  <div className="mt-3 flex flex-wrap gap-2">
                    <Link
                      to={`/checkout/${o.orderId}`}
                      className="inline-flex rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white"
                    >
                      Continue
                    </Link>
                    {o.eventId && (
                      <Link
                        to={`/events/${o.eventId}`}
                        className="inline-flex rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-800"
                      >
                        View event
                      </Link>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
      <div className="mt-8">
        <div className="text-lg font-bold text-slate-900">Completed orders</div>

        {orderHistoryQuery.isPending && (
          <div className="mt-2 text-sm text-slate-600">Loading completed orders…</div>
        )}

        {orderHistoryQuery.isError && (
          <div className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {getApiErrorMessage(orderHistoryQuery.error)}
          </div>
        )}

        {!orderHistoryQuery.isPending && !orderHistoryQuery.isError && (
          <div className="mt-3 grid gap-3">
            {(orderHistoryQuery.data ?? []).filter((o) => !o.cancelled).length === 0 ? (
              <div className="text-sm text-slate-600">No completed orders yet.</div>
            ) : (
              (orderHistoryQuery.data ?? [])
                .filter((o) => !o.cancelled)
                .map((o) => (
                  <div
                    key={o.orderId}
                    className="rounded-xl border border-slate-200 bg-white p-4"
                  >
                    <div className="flex flex-wrap items-center justify-between gap-3">
                      <div>
                        <div className="text-sm font-semibold text-slate-900">Order ID</div>
                        <div className="mt-1 font-mono text-xs text-slate-700">{o.orderId}</div>
                      </div>

                      <div className="text-right">
                        <div className="text-sm text-slate-600">Total</div>
                        <div className="text-sm font-semibold text-slate-900">
                          {formatMoney(o.totalPrice)}
                        </div>
                      </div>
                    </div>

                    <div className="mt-3 flex flex-wrap items-center justify-between gap-3">
                      <div className="text-sm text-slate-600">
                        Tickets: {o.tickets?.length ?? 0}
                      </div>
                      <Link
                        to={`/events/${o.eventId}`}
                        className="text-sm font-semibold text-slate-700 hover:text-slate-900"
                      >
                        View event
                      </Link>
                    </div>
                  </div>
                ))
            )}
          </div>
        )}
      </div>
    </div>
  );
}
