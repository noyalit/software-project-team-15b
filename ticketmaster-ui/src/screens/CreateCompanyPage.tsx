import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { AxiosError } from 'axios';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { getApiErrorMessage } from '../api/errors';
import { http } from '../api/http';
import type { ApiResponse, CompanyDTO } from '../api/types';
import { useAuthStore } from '../ui/authStore';

type CreateCompanyResponse = ApiResponse<CompanyDTO>;

type CreateCompanyRequest = {
  name: string;
  purchasePolicy?: unknown;
  discountPolicy?: unknown;
};

export default function CreateCompanyPage() {
  const nav = useNavigate();
  const qc = useQueryClient();
  const { token, userType, clearAuth } = useAuthStore();

  const [name, setName] = useState('');

  const [purchasePolicyKind, setPurchasePolicyKind] = useState<'NONE' | 'MAX_TICKETS' | 'MIN_AGE' | 'MIN_TICKETS'>(
    'NONE'
  );
  const [maxTickets, setMaxTickets] = useState('');
  const [minAge, setMinAge] = useState('');
  const [minTickets, setMinTickets] = useState('');

  const [discountPolicyKind, setDiscountPolicyKind] = useState<'NONE' | 'SIMPLE' | 'CONDITIONAL'>(
    'NONE'
  );
  const [discountPercent, setDiscountPercent] = useState('');
  const [conditionKind, setConditionKind] = useState<'MAX_TICKETS' | 'MIN_TICKETS' | 'TIME_WINDOW'>(
    'MAX_TICKETS'
  );
  const [conditionMaxTickets, setConditionMaxTickets] = useState('');
  const [conditionMinTickets, setConditionMinTickets] = useState('');
  const [windowFrom, setWindowFrom] = useState('');
  const [windowTo, setWindowTo] = useState('');

  const buildPurchasePolicy = () => {
    if (purchasePolicyKind === 'NONE') return undefined;

    if (purchasePolicyKind === 'MAX_TICKETS') {
      const v = Number(maxTickets);
      if (!Number.isFinite(v) || v < 1) throw new Error('Max tickets must be a number >= 1');
      return {
        '@class': 'com.software_project_team_15b.Ticketmaster.Domain.policy.MaxTicketsRule',
        max: v,
      };
    }
    if (purchasePolicyKind === 'MIN_AGE') {
      const v = Number(minAge);
      if (!Number.isFinite(v) || v < 0) throw new Error('Minimum age must be a number >= 0');
      return {
        '@class': 'com.software_project_team_15b.Ticketmaster.Domain.policy.MinAgeRule',
        minAge: v,
      };
    }
    if (purchasePolicyKind === 'MIN_TICKETS') {
      const v = Number(minTickets);
      if (!Number.isFinite(v) || v < 1) throw new Error('Min tickets must be a number >= 1');
      return {
        '@class': 'com.software_project_team_15b.Ticketmaster.Domain.policy.MinTicketsRule',
        min: v,
      };
    }
    return undefined;
  };

  const buildDiscountPolicy = () => {
    if (discountPolicyKind === 'NONE') return undefined;

    const percent = Number(discountPercent);
    if (!Number.isFinite(percent) || percent <= 0 || percent > 100) {
      throw new Error('Discount percent must be a number between 0 and 100');
    }

    if (discountPolicyKind === 'SIMPLE') {
      return {
        '@class': 'com.software_project_team_15b.Ticketmaster.Domain.policy.SimpleDiscountPolicy',
        percent,
      };
    }

    if (discountPolicyKind === 'CONDITIONAL') {
      let condition: any;
      if (conditionKind === 'MAX_TICKETS') {
        const v = Number(conditionMaxTickets);
        if (!Number.isFinite(v) || v < 1) throw new Error('Condition max tickets must be a number >= 1');
        condition = {
          '@class': 'com.software_project_team_15b.Ticketmaster.Domain.policy.condition.MaxTicketsCondition',
          max: v,
        };
      } else if (conditionKind === 'MIN_TICKETS') {
        const v = Number(conditionMinTickets);
        if (!Number.isFinite(v) || v < 1) throw new Error('Condition min tickets must be a number >= 1');
        condition = {
          '@class': 'com.software_project_team_15b.Ticketmaster.Domain.policy.condition.MinTicketsCondition',
          min: v,
        };
      } else {
        const from = windowFrom ? new Date(windowFrom).toISOString() : null;
        const to = windowTo ? new Date(windowTo).toISOString() : null;
        if (from && to && new Date(from).getTime() > new Date(to).getTime()) {
          throw new Error('Time window: From must be before To');
        }
        condition = {
          '@class': 'com.software_project_team_15b.Ticketmaster.Domain.policy.condition.TimeWindowCondition',
          from,
          to,
        };
      }

      return {
        '@class': 'com.software_project_team_15b.Ticketmaster.Domain.policy.ConditionalDiscountPolicy',
        percent,
        condition,
      };
    }

    return undefined;
  };

  const createMutation = useMutation({
    mutationFn: async () => {
      const trimmed = name.trim();
      if (!trimmed) throw new Error('Company name is required.');

      try {
        const purchasePolicy = buildPurchasePolicy();
        const discountPolicy = buildDiscountPolicy();
        const payload: CreateCompanyRequest = {
          name: trimmed,
          purchasePolicy,
          discountPolicy,
        };
        const res = await http.post<CreateCompanyResponse>('/api/companies', payload);
        if (res.data.error) throw new Error(res.data.error);
        if (!res.data.data) throw new Error('No company returned');
        return res.data.data;
      } catch (e) {
        const err = e as AxiosError<CreateCompanyResponse>;
        const status = err.response?.status;

        if (status === 401) {
          clearAuth();
          throw new Error('Your session expired. Please log in again.');
        }
        if (status === 403) {
          throw new Error('You are not authorized to create a company.');
        }

        throw new Error(
          getApiErrorMessage<CompanyDTO>(e, {
            fallback: 'Failed to create company. Please verify the name and try again.',
            serverFallback: 'Company creation is currently unavailable due to a server issue. Please try again later.',
          })
        );
      }
    },
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ['companies', 'me'] });
      nav('/companies/me');
    },
  });

  if (userType !== 'member') {
    return (
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">Create Company</h1>
        <p className="mt-2 text-slate-600">Log in as a user to create a company.</p>
        <div className="mt-4">
          <Link
            to="/login"
            className="inline-flex items-center rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800"
          >
            Go to login
          </Link>
        </div>
      </div>
    );
  }

  if (!token) {
    return (
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">Create Company</h1>
        <p className="mt-2 text-slate-600">Please log in again to continue.</p>
        <div className="mt-4">
          <Link
            to="/login"
            className="inline-flex items-center rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800"
          >
            Go to login
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">Create Company</h1>
        <p className="mt-1 text-sm text-slate-600">Create a new company. You will become the founder.</p>
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <label className="block">
          <div className="text-sm font-medium text-slate-700">Company name</div>
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="e.g. Live Nation"
            className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm"
          />
        </label>

        <div className="mt-6 rounded-xl border border-slate-200 bg-slate-50 p-4">
          <div className="text-sm font-semibold text-slate-900">Company policies</div>
          <div className="mt-1 text-sm text-slate-600">
            These policies apply at the company level. (Event policies are configured per event.)
          </div>

          <div className="mt-4 grid gap-4">
            <div>
              <div className="text-sm font-medium text-slate-700">Purchase policy</div>
              <select
                value={purchasePolicyKind}
                onChange={(e) => setPurchasePolicyKind(e.target.value as any)}
                className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
              >
                <option value="NONE">No purchase policy</option>
                <option value="MAX_TICKETS">Max tickets per order</option>
                <option value="MIN_TICKETS">Min tickets per order</option>
                <option value="MIN_AGE">Age restriction</option>
              </select>

              {purchasePolicyKind === 'MAX_TICKETS' && (
                <div className="mt-2">
                  <div className="text-xs font-medium text-slate-600">Max tickets</div>
                  <input
                    value={maxTickets}
                    onChange={(e) => setMaxTickets(e.target.value)}
                    placeholder="e.g. 4"
                    className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
                  />
                </div>
              )}

              {purchasePolicyKind === 'MIN_TICKETS' && (
                <div className="mt-2">
                  <div className="text-xs font-medium text-slate-600">Min tickets</div>
                  <input
                    value={minTickets}
                    onChange={(e) => setMinTickets(e.target.value)}
                    placeholder="e.g. 2"
                    className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
                  />
                </div>
              )}

              {purchasePolicyKind === 'MIN_AGE' && (
                <div className="mt-2">
                  <div className="text-xs font-medium text-slate-600">Minimum age</div>
                  <input
                    value={minAge}
                    onChange={(e) => setMinAge(e.target.value)}
                    placeholder="e.g. 18"
                    className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
                  />
                </div>
              )}
            </div>

            <div>
              <div className="text-sm font-medium text-slate-700">Discount policy</div>
              <select
                value={discountPolicyKind}
                onChange={(e) => setDiscountPolicyKind(e.target.value as any)}
                className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
              >
                <option value="NONE">No discount policy</option>
                <option value="SIMPLE">Simple discount (%)</option>
                <option value="CONDITIONAL">Conditional discount (%)</option>
              </select>

              {discountPolicyKind !== 'NONE' && (
                <div className="mt-2">
                  <div className="text-xs font-medium text-slate-600">Discount percent</div>
                  <input
                    value={discountPercent}
                    onChange={(e) => setDiscountPercent(e.target.value)}
                    placeholder="e.g. 10"
                    className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
                  />
                </div>
              )}

              {discountPolicyKind === 'CONDITIONAL' && (
                <div className="mt-3 rounded-lg border border-slate-200 bg-white p-3">
                  <div className="text-xs font-semibold uppercase tracking-wide text-slate-600">Condition</div>

                  <select
                    value={conditionKind}
                    onChange={(e) => setConditionKind(e.target.value as any)}
                    className="mt-2 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
                  >
                    <option value="MAX_TICKETS">Max tickets</option>
                    <option value="MIN_TICKETS">Min tickets</option>
                    <option value="TIME_WINDOW">Time window</option>
                  </select>

                  {conditionKind === 'MAX_TICKETS' && (
                    <div className="mt-2">
                      <div className="text-xs font-medium text-slate-600">Max tickets</div>
                      <input
                        value={conditionMaxTickets}
                        onChange={(e) => setConditionMaxTickets(e.target.value)}
                        placeholder="e.g. 4"
                        className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
                      />
                    </div>
                  )}

                  {conditionKind === 'MIN_TICKETS' && (
                    <div className="mt-2">
                      <div className="text-xs font-medium text-slate-600">Min tickets</div>
                      <input
                        value={conditionMinTickets}
                        onChange={(e) => setConditionMinTickets(e.target.value)}
                        placeholder="e.g. 2"
                        className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
                      />
                    </div>
                  )}

                  {conditionKind === 'TIME_WINDOW' && (
                    <div className="mt-2 grid gap-2 sm:grid-cols-2">
                      <label className="block">
                        <div className="text-xs font-medium text-slate-600">From</div>
                        <input
                          type="datetime-local"
                          value={windowFrom}
                          onChange={(e) => setWindowFrom(e.target.value)}
                          className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
                        />
                      </label>
                      <label className="block">
                        <div className="text-xs font-medium text-slate-600">To</div>
                        <input
                          type="datetime-local"
                          value={windowTo}
                          onChange={(e) => setWindowTo(e.target.value)}
                          className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
                        />
                      </label>
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>
        </div>

        {createMutation.isError && (
          <div className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {(createMutation.error as Error).message}
          </div>
        )}

        <div className="mt-4 flex flex-col gap-2 sm:flex-row sm:justify-end">
          <Link
            to="/companies/me"
            className="inline-flex items-center justify-center rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-900 hover:bg-slate-50"
          >
            Cancel
          </Link>
          <button
            onClick={() => createMutation.mutate()}
            disabled={createMutation.isPending}
            className="inline-flex items-center justify-center rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
          >
            {createMutation.isPending ? 'Creating…' : 'Create company'}
          </button>
        </div>
      </div>
    </div>
  );
}
