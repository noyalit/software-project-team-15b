import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { http } from '../api/http';
import type { ApiResponse } from '../api/types';

type ActiveOrderDTO = {
  orderId: string;
};

export default function OrdersPage() {
  const activeOrderId = localStorage.getItem('activeOrderId');

  const activeOrderQuery = useQuery({
    queryKey: ['active-order', activeOrderId],
    queryFn: async () => {
      const res = await http.get<ApiResponse<ActiveOrderDTO>>(
        `/api/active-orders/${activeOrderId}`
      );

      if (res.data.error) throw new Error(res.data.error);
      if (!res.data.data) throw new Error('No active order found');

      return res.data.data;
    },
    enabled: Boolean(activeOrderId),
  });

  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
      <h1 className="text-2xl font-extrabold text-slate-900">My Orders</h1>

      {!activeOrderId && (
        <p className="mt-2 text-slate-600">You do not have an active order.</p>
      )}

      {activeOrderQuery.isPending && activeOrderId && (
        <p className="mt-2 text-slate-600">Loading active order...</p>
      )}

      {activeOrderQuery.isError && (
        <div className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
          {(activeOrderQuery.error as Error).message}
        </div>
      )}

      {activeOrderQuery.data && (
        <div className="mt-4 rounded-xl border border-slate-200 bg-slate-50 p-4">
          <div className="font-semibold text-slate-900">Active order</div>
          <div className="mt-1 text-sm text-slate-600">
            Order ID: {activeOrderId}
          </div>

          <Link
            to={`/orders/${activeOrderId}`}
            className="mt-3 inline-flex rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white"
          >
            Continue order
          </Link>
        </div>
      )}
    </div>
  );
}