import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router-dom';
import { http } from '../api/http';
import type { ApiResponse, EventDTO } from '../api/types';
import { getApiErrorMessage } from '../api/errors';
import { useAuthStore } from '../ui/authStore';
import Barcode from '../ui/Barcode';

type MoneyDTO = {
  amount: number;
  currency: string;
};

type OrderHistoryDTO = {
  orderId: string;
  userId: string;
  eventId: string;
  areaId: string;
  paymentTransactionId: number;
  totalPrice?: MoneyDTO | null;
  tickets: Array<{ seatId: string; basePrice?: MoneyDTO | null }>;
  cancelled: boolean;
  ticketIdentifier: string;
};

function formatMoney(m?: MoneyDTO | null): string {
  if (!m) return '—';
  return `${m.amount} ${m.currency}`;
}

function formatDate(iso?: string): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString();
}

export default function OrderDetailsPage() {
  const { orderId } = useParams<{ orderId: string }>();
  const { token } = useAuthStore();

  const orderQuery = useQuery({
    queryKey: ['order-history', orderId, token],
    queryFn: async () => {
      try {
        const res = await http.get<ApiResponse<OrderHistoryDTO | null>>(
          `/api/order-history/${orderId}`
        );

        if (res.data.error === 'Order not found') {
          return null;
        }

        if (res.data.error) {
          throw new Error(res.data.error);
        }

        return res.data.data ?? null;
      } catch (e: unknown) {
        const msg = getApiErrorMessage(e);
        if (msg === 'Order not found') {
          return null;
        }
        throw e;
      }
    },
    enabled: Boolean(token && orderId),
  });

  const order = orderQuery.data;

  // Resolve the UUIDs (event / area / seat) into real names via the event.
  const eventQuery = useQuery({
    queryKey: ['event', order?.eventId],
    queryFn: async () => {
      if (!order?.eventId) return null;
      const res = await http.get<ApiResponse<EventDTO>>(
        `/api/events/${order.eventId}`
      );
      if (res.data.error) throw new Error(res.data.error);
      return res.data.data ?? null;
    },
    enabled: Boolean(order?.eventId),
  });

  const event = eventQuery.data ?? null;
  const area = event?.areas.find((a) => a.areaId === order?.areaId) ?? null;

  // seatId -> real seat (row / number) across all areas of the event.
  const seatById = new Map(
    (event?.areas ?? []).flatMap((a) => a.seats.map((s) => [s.seatId, s] as const))
  );

  // The order's real ticketing number returned by the external ticketing API.
  const ticketNumber = order?.ticketIdentifier ?? '';

  return (
    <div className="space-y-4">
      <Link
        to="/orders"
        className="inline-flex text-sm font-semibold text-slate-600 hover:text-slate-900"
      >
        ← Back to orders
      </Link>

      {orderQuery.isPending && (
        <div className="rounded-2xl border border-slate-200 bg-white p-6 text-slate-600 shadow-sm">
          Loading order…
        </div>
      )}

      {orderQuery.isError && (
        <div className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
          {getApiErrorMessage(orderQuery.error)}
        </div>
      )}

      {!orderQuery.isPending && !orderQuery.isError && !order && (
        <div className="rounded-2xl border border-slate-200 bg-white p-6 text-slate-600 shadow-sm">
          Order not found.
        </div>
      )}

      {order && (
        <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-sm">
          {/* Header / barcode */}
          <div className="flex flex-col gap-5 border-b border-dashed border-slate-300 p-6 sm:flex-row sm:items-center sm:justify-between">
            <div className="min-w-0">
              <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                {event ? event.category : 'Event'}
              </div>
              <h1 className="mt-1 truncate text-2xl font-extrabold text-slate-900">
                {event?.name ?? 'Event'}
              </h1>
              {event?.artist && (
                <div className="mt-0.5 text-sm text-slate-600">{event.artist}</div>
              )}
              <dl className="mt-3 space-y-1 text-sm text-slate-600">
                <div className="flex gap-2">
                  <dt className="font-semibold text-slate-700">When:</dt>
                  <dd>{formatDate(event?.startsAt)}</dd>
                </div>
                <div className="flex gap-2">
                  <dt className="font-semibold text-slate-700">Where:</dt>
                  <dd>{event?.location ?? '—'}</dd>
                </div>
                <div className="flex gap-2">
                  <dt className="font-semibold text-slate-700">Area:</dt>
                  <dd>{area?.name ?? '—'}</dd>
                </div>
              </dl>
              {order.cancelled && (
                <span className="mt-3 inline-flex rounded-full bg-rose-100 px-3 py-1 text-xs font-bold text-rose-700">
                  Cancelled
                </span>
              )}
            </div>

            {/* One barcode per order, encoding the ticketing number */}
            <div className="flex w-full max-w-sm flex-col items-center rounded-xl border border-slate-200 bg-slate-50 p-4 sm:w-72">
              <div className="w-full">
                <Barcode value={ticketNumber} height={80} barWidth={2} />
              </div>
              <div className="mt-2 text-xs font-semibold uppercase tracking-wide text-slate-500">
                Ticket number
              </div>
              <div className="break-all text-center font-mono text-xs font-bold tracking-wider text-slate-900">
                {ticketNumber}
              </div>
            </div>
          </div>

          {/* Summary */}
          <div className="grid grid-cols-2 gap-px bg-slate-200 sm:grid-cols-4">
            <div className="bg-white p-4">
              <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                Tickets
              </div>
              <div className="mt-1 text-lg font-bold text-slate-900">
                {order.tickets?.length ?? 0}
              </div>
            </div>
            <div className="bg-white p-4">
              <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                Total spent
              </div>
              <div className="mt-1 text-lg font-bold text-slate-900">
                {formatMoney(order.totalPrice)}
              </div>
            </div>
            <div className="bg-white p-4">
              <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                Transaction #
              </div>
              <div className="mt-1 font-mono text-sm text-slate-900">
                {order.paymentTransactionId}
              </div>
            </div>
            <div className="bg-white p-4">
              <div className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                Order ID
              </div>
              <div className="mt-1 truncate font-mono text-xs text-slate-700">
                {order.orderId}
              </div>
            </div>
          </div>

          {/* Tickets */}
          <div className="p-6">
            <h2 className="text-lg font-bold text-slate-900">Seats</h2>

            {eventQuery.isPending && (
              <p className="mt-2 text-sm text-slate-600">Loading seat details…</p>
            )}

            <div className="mt-3 overflow-hidden rounded-xl border border-slate-200">
              <table className="w-full text-left text-sm">
                <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                  <tr>
                    <th className="px-4 py-2 font-semibold">#</th>
                    <th className="px-4 py-2 font-semibold">Area</th>
                    <th className="px-4 py-2 font-semibold">Row</th>
                    <th className="px-4 py-2 font-semibold">Seat</th>
                    <th className="px-4 py-2 text-right font-semibold">Price</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {(order.tickets ?? []).map((t, i) => {
                    const seat = seatById.get(t.seatId);
                    return (
                      <tr key={t.seatId}>
                        <td className="px-4 py-2 text-slate-500">{i + 1}</td>
                        <td className="px-4 py-2 text-slate-900">{area?.name ?? '—'}</td>
                        <td className="px-4 py-2 text-slate-900">{seat?.row ?? '—'}</td>
                        <td className="px-4 py-2 text-slate-900">{seat?.number ?? '—'}</td>
                        <td className="px-4 py-2 text-right text-slate-900">
                          {formatMoney(t.basePrice)}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>

            <div className="mt-4">
              <Link
                to={`/events/${order.eventId}`}
                className="inline-flex text-sm font-semibold text-slate-700 hover:text-slate-900"
              >
                View event →
              </Link>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
