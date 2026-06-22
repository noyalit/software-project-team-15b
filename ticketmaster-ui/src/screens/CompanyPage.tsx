import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { AxiosError } from 'axios';
import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getApiErrorMessage } from '../api/errors';
import { http } from '../api/http';
import type {
  ApiResponse,
  CompanyDTO,
  EventDTO,
  ManagerPermission,
  MemberDTO,
} from '../api/types';
import { useAuthStore } from '../ui/authStore';

export default function CompanyPage() {
  const { companyId } = useParams();
  const qc = useQueryClient();
  const { token, userType, clearAuth } = useAuthStore();
  const [ownerUsername, setOwnerUsername] = useState('');
  const [ownerSuccessMessage, setOwnerSuccessMessage] = useState<string | null>(null);
  const [removeOwnerUsername, setRemoveOwnerUsername] = useState('');
  const [removeOwnerSuccessMessage, setRemoveOwnerSuccessMessage] = useState<string | null>(null);
  const [resignSuccessMessage, setResignSuccessMessage] = useState<string | null>(null);
  const [statusSuccessMessage, setStatusSuccessMessage] = useState<string | null>(null);
  const [managerUsername, setManagerUsername] = useState('');
  const [managerEventId, setManagerEventId] = useState('');
  const [managerPermissions, setManagerPermissions] = useState<ManagerPermission[]>([]);
  const [changeManagerUsername, setChangeManagerUsername] = useState('');
  const [changeManagerEventId, setChangeManagerEventId] = useState('');
  const [newManagerPermissions, setNewManagerPermissions] = useState<ManagerPermission[]>([]);
  const [changeManagerSuccessMessage, setChangeManagerSuccessMessage] = useState<string | null>(null);

  const [removeManagerUsername, setRemoveManagerUsername] = useState('');
  const [removeManagerEventId, setRemoveManagerEventId] = useState('');

  const [managerSuccessMessage, setManagerSuccessMessage] = useState<string | null>(null);
  const [removeManagerSuccessMessage, setRemoveManagerSuccessMessage] = useState<string | null>(null);

  const [companyManagerUsername, setCompanyManagerUsername] = useState('');
  const [companyManagerPermissions, setCompanyManagerPermissions] = useState<ManagerPermission[]>([]);
  const [companyManagerSuccessMessage, setCompanyManagerSuccessMessage] = useState<string | null>(null);

  const [changeCompanyManagerUsername, setChangeCompanyManagerUsername] = useState('');
  const [newCompanyManagerPermissions, setNewCompanyManagerPermissions] = useState<ManagerPermission[]>([]);
  const [changeCompanyManagerSuccessMessage, setChangeCompanyManagerSuccessMessage] = useState<string | null>(null);

  const [removeCompanyManagerUsername, setRemoveCompanyManagerUsername] = useState('');
  const [removeCompanyManagerSuccessMessage, setRemoveCompanyManagerSuccessMessage] = useState<string | null>(null);

  const [purchasePolicyKind, setPurchasePolicyKind] = useState<
    'NONE' | 'MAX_TICKETS' | 'MIN_AGE' | 'MIN_TICKETS'
  >('NONE');
  const [maxTickets, setMaxTickets] = useState('');
  const [minAge, setMinAge] = useState('');
  const [minTickets, setMinTickets] = useState('');
  const [purchasePolicySuccessMessage, setPurchasePolicySuccessMessage] = useState<string | null>(null);

  const [discountPolicyKind, setDiscountPolicyKind] = useState<'NONE' | 'SIMPLE' | 'CONDITIONAL' | 'COUPON'>('NONE');
  const [discountPercent, setDiscountPercent] = useState('');
  const [couponCode, setCouponCode] = useState('');
  const [couponExpiresAt, setCouponExpiresAt] = useState('');
  const [conditionKind, setConditionKind] = useState<'MAX_TICKETS' | 'MIN_TICKETS' | 'TIME_WINDOW'>(
    'MAX_TICKETS'
  );
  const [conditionMaxTickets, setConditionMaxTickets] = useState('');
  const [conditionMinTickets, setConditionMinTickets] = useState('');
  const [windowFrom, setWindowFrom] = useState('');
  const [windowTo, setWindowTo] = useState('');
  const [discountPolicySuccessMessage, setDiscountPolicySuccessMessage] = useState<string | null>(null);

  // Draft chains: the company now supports multiple purchase/discount policies, so the
  // single-policy selectors above act as an "add new policy" form that appends here.
  const [purchasePoliciesDraft, setPurchasePoliciesDraft] = useState<any[]>([]);
  const [discountPoliciesDraft, setDiscountPoliciesDraft] = useState<any[]>([]);
  const [purchasePoliciesDirty, setPurchasePoliciesDirty] = useState(false);
  const [discountPoliciesDirty, setDiscountPoliciesDirty] = useState(false);
  const [purchasePolicyAddError, setPurchasePolicyAddError] = useState<string | null>(null);
  const [discountPolicyAddError, setDiscountPolicyAddError] = useState<string | null>(null);

  // Policies use the clean "type" discriminator wire format (see CompanyPurchasePolicyDTO /
  // CompanyDiscountPolicyDTO on the backend), e.g. { type: 'MAX_TICKETS', max: 4 }.
  const describeCompanyPurchasePolicy = (p: any, depth = 0): string[] => {
    if (!p || typeof p !== 'object') return ['Unknown policy'];
    switch (p.type) {
      case 'MAX_TICKETS':
        return [`Max tickets per order: ${p.max}`];
      case 'MIN_TICKETS':
        return [`Min tickets per order: ${p.min}`];
      case 'MIN_AGE':
        return [`Age restriction: ${p.minAge}+`];
      case 'AND':
      case 'OR': {
        const lines = [p.type === 'OR' ? 'Any of:' : 'All of:'];
        for (const child of p.children ?? []) {
          for (const l of describeCompanyPurchasePolicy(child, depth + 1)) {
            lines.push('  '.repeat(depth + 1) + l);
          }
        }
        return lines;
      }
      default:
        return ['Unknown policy'];
    }
  };

  const describeCompanyDiscountPolicy = (p: any, depth = 0): string[] => {
    if (!p || typeof p !== 'object') return ['Unknown policy'];
    switch (p.type) {
      case 'SIMPLE':
        return [`Simple discount (${p.percent}%)`];
      case 'COUPON': {
        const pct = p.percentage != null ? ` (${p.percentage}%)` : '';
        return [`Coupon ${String(p.code ?? '').trim()}${pct}`.trim()];
      }
      case 'CONDITIONAL': {
        const cond = p.condition;
        const base = `Conditional discount (${p.percent}%)`;
        if (cond && typeof cond === 'object') {
          if (cond.type === 'MAX_TICKETS') return [`${base} when quantity <= ${cond.max}`];
          if (cond.type === 'MIN_TICKETS') return [`${base} when quantity >= ${cond.min}`];
          if (cond.type === 'TIME_WINDOW') {
            const from = cond.from ? new Date(cond.from).toLocaleString() : null;
            const to = cond.to ? new Date(cond.to).toLocaleString() : null;
            if (from && to) return [`${base} between ${from} and ${to}`];
            if (from) return [`${base} from ${from}`];
            if (to) return [`${base} until ${to}`];
          }
        }
        return [base];
      }
      case 'SUM':
      case 'MAX': {
        const lines = [p.type === 'SUM' ? 'Sum of:' : 'Max of:'];
        for (const child of p.children ?? []) {
          for (const l of describeCompanyDiscountPolicy(child, depth + 1)) {
            lines.push('  '.repeat(depth + 1) + l);
          }
        }
        return lines;
      }
      default:
        return ['Unknown policy'];
    }
  };



  const companyQuery = useQuery({
    queryKey: ['company', companyId, token],
    queryFn: async () => {
      if (!companyId) throw new Error('Company ID is missing.');
      try {
        const res = await http.get<ApiResponse<CompanyDTO>>(`/api/companies/${companyId}`);
        if (res.data.error) throw new Error(res.data.error);
        if (!res.data.data) throw new Error('Company not found');
        return res.data.data;
      } catch (e) {
        const err = e as AxiosError<ApiResponse<CompanyDTO>>;
        const status = err.response?.status;

        if (status === 401) {
          clearAuth();
          throw new Error('Your session expired. Please log in again.');
        }

        throw new Error(
          getApiErrorMessage<CompanyDTO>(e, {
            fallback: 'Failed to load company. Please try again.',
            serverFallback: 'Company is currently unavailable due to a server issue. Please try again later.',
          })
        );
      }
    },
    enabled: Boolean(companyId) && Boolean(token) && userType === 'member',
  });

  const meQuery = useQuery({
    queryKey: ['me', token],
    queryFn: async () => {
      try {
        const res = await http.get<ApiResponse<MemberDTO>>('/api/users/me');
        if (res.data.error) throw new Error(res.data.error);
        if (!res.data.data) throw new Error('User not found');
        return res.data.data;
      } catch (e) {
        const err = e as AxiosError<ApiResponse<MemberDTO>>;
        const status = err.response?.status;

        if (status === 401) {
          clearAuth();
          throw new Error('Your session expired. Please log in again.');
        }

        throw new Error(
          getApiErrorMessage<MemberDTO>(e, {
            fallback: 'Failed to load your profile. Please try again.',
            serverFallback: 'Profile is currently unavailable due to a server issue. Please try again later.',
          })
        );
      }
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
      (
        meQuery.data?.activeRole === 'Owner' ||
        meQuery.data?.activeRole === 'Manager' ||
        meQuery.data?.activeRole === 'CompanyManager'
      ),
  });

  const activeRole = meQuery.data?.activeRole;
  const canManageCompany =
    activeRole === 'Founder' ||
    (
      (activeRole === 'Owner' || activeRole === 'CompanyManager') &&
      appointmentApprovedQuery.data === true
    );

  if (!canManageCompany) {
    return (
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">
          Company
        </h1>
        <p className="mt-2 text-slate-600">
          Your appointment must be approved before you can manage this company.
        </p>
      </div>
    );
  }

  const eventsQuery = useQuery({
    queryKey: ['company-events', companyId],
    queryFn: async () => {
      if (!companyId) return [];

      const res = await http.get<ApiResponse<EventDTO[]>>(
        `/api/companies/${companyId}/events`
      );

      if (res.data.error) {
        throw new Error(res.data.error);
      }

      return res.data.data ?? [];
    },
    enabled: Boolean(companyId),
  });

  const companyPurchasePoliciesQuery = useQuery({
    queryKey: ['company', 'purchase-policies', companyId],
    queryFn: async () => {
      if (!companyId) return [] as any[];
      const res = await http.get<ApiResponse<any[]>>(`/api/companies/${companyId}/purchase-policies`);
      if (res.data.error) throw new Error(res.data.error);
      return res.data.data ?? [];
    },
    enabled: Boolean(companyId) && Boolean(token) && userType === 'member',
  });

  const companyDiscountPoliciesQuery = useQuery({
    queryKey: ['company', 'discount-policies', companyId],
    queryFn: async () => {
      if (!companyId) return [] as any[];
      const res = await http.get<ApiResponse<any[]>>(`/api/companies/${companyId}/discount-policies`);
      if (res.data.error) throw new Error(res.data.error);
      return res.data.data ?? [];
    },
    enabled: Boolean(companyId) && Boolean(token) && userType === 'member',
  });

  // Reset editor state when switching companies so an unsaved draft from one company
  // can't leak into (and overwrite) another company's policy chain. Clearing the dirty
  // flags lets the seeding effects below re-seed from the new company's data.
  useEffect(() => {
    setPurchasePoliciesDirty(false);
    setDiscountPoliciesDirty(false);
    setPurchasePoliciesDraft([]);
    setDiscountPoliciesDraft([]);
  }, [companyId]);

  // Seed the editable purchase-policy chain from the server's current chain, until the user edits it.
  // The server returns the clean "type"-tagged DTO shape, so the items round-trip back on save as-is.
  useEffect(() => {
    if (!companyPurchasePoliciesQuery.isSuccess) return;
    if (purchasePoliciesDirty) return;
    setPurchasePoliciesDraft(companyPurchasePoliciesQuery.data ?? []);
  }, [companyId, purchasePoliciesDirty, companyPurchasePoliciesQuery.data, companyPurchasePoliciesQuery.isSuccess]);

  // Seed the editable discount-policy chain from the server's current chain, until the user edits it.
  useEffect(() => {
    if (!companyDiscountPoliciesQuery.isSuccess) return;
    if (discountPoliciesDirty) return;
    setDiscountPoliciesDraft(companyDiscountPoliciesQuery.data ?? []);
  }, [companyId, discountPoliciesDirty, companyDiscountPoliciesQuery.data, companyDiscountPoliciesQuery.isSuccess]);

  const buildPurchasePolicy = () => {
    if (purchasePolicyKind === 'NONE') {
      throw new Error('Please select a policy type to add.');
    }
    if (purchasePolicyKind === 'MAX_TICKETS') {
      const v = Number(maxTickets);
      if (!Number.isFinite(v) || v < 1) throw new Error('Max tickets must be a number >= 1');
      return { type: 'MAX_TICKETS', max: v };
    }
    if (purchasePolicyKind === 'MIN_AGE') {
      const v = Number(minAge);
      if (!Number.isFinite(v) || v < 0) throw new Error('Minimum age must be a number >= 0');
      return { type: 'MIN_AGE', minAge: v };
    }
    if (purchasePolicyKind === 'MIN_TICKETS') {
      const v = Number(minTickets);
      if (!Number.isFinite(v) || v < 1) throw new Error('Min tickets must be a number >= 1');
      return { type: 'MIN_TICKETS', min: v };
    }
    throw new Error('Invalid purchase policy');
  };

  const buildDiscountPolicy = () => {
    if (discountPolicyKind === 'NONE') {
      throw new Error('Please select a policy type to add.');
    }

    const percent = Number(discountPercent);
    if (!Number.isFinite(percent) || percent <= 0 || percent > 100) {
      throw new Error('Discount percent must be a number between 0 and 100');
    }

    if (discountPolicyKind === 'SIMPLE') {
      return { type: 'SIMPLE', percent };
    }

    if (discountPolicyKind === 'COUPON') {
      const code = couponCode.trim();
      if (!code) {
        throw new Error('Coupon code is required');
      }
      const expiresAt = couponExpiresAt.trim();
      if (expiresAt) {
        const ms = new Date(expiresAt).getTime();
        if (!Number.isFinite(ms)) {
          throw new Error('Expires at must be a valid date/time');
        }
      }
      return {
        type: 'COUPON',
        code,
        percentage: percent,
        expiresAt: expiresAt ? new Date(expiresAt).toISOString() : null,
      };
    }

    if (discountPolicyKind === 'CONDITIONAL') {
      let condition: any;
      if (conditionKind === 'MAX_TICKETS') {
        const v = Number(conditionMaxTickets);
        if (!Number.isFinite(v) || v < 1) throw new Error('Condition max tickets must be a number >= 1');
        condition = { type: 'MAX_TICKETS', max: v };
      } else if (conditionKind === 'MIN_TICKETS') {
        const v = Number(conditionMinTickets);
        if (!Number.isFinite(v) || v < 1) throw new Error('Condition min tickets must be a number >= 1');
        condition = { type: 'MIN_TICKETS', min: v };
      } else {
        const from = windowFrom ? new Date(windowFrom).toISOString() : null;
        const to = windowTo ? new Date(windowTo).toISOString() : null;
        if (from && to && new Date(from).getTime() > new Date(to).getTime()) {
          throw new Error('Time window: From must be before To');
        }
        condition = { type: 'TIME_WINDOW', from, to };
      }

      return { type: 'CONDITIONAL', percent, condition };
    }

    throw new Error('Invalid discount policy');
  };

  const updatePurchasePolicyMutation = useMutation({
    mutationFn: async () => {
      setPurchasePolicySuccessMessage(null);
      if (!companyId) throw new Error('Company ID is missing.');
      try {
        const res = await http.put<ApiResponse<CompanyDTO>>(
          `/api/companies/${companyId}/purchase-policies`,
          purchasePoliciesDraft
        );

        if (res.data.error) throw new Error(res.data.error);
        return res.data.data ?? null;
      } catch (e) {
        const err = e as AxiosError<ApiResponse<CompanyDTO>>;

        if (err.response?.status === 403) {
          throw new Error('You do not have permission to edit the purchase policies for this company.');
        }

        throw new Error(getApiErrorMessage<CompanyDTO>(e));
      }
    },
    onSuccess: async () => {
      setPurchasePolicySuccessMessage('Company purchase policies saved successfully.');
      setPurchasePoliciesDirty(false);
      await qc.invalidateQueries({ queryKey: ['company', companyId, token] });
      await qc.invalidateQueries({ queryKey: ['company', 'purchase-policies', companyId] });
    },
  });

  const updateDiscountPolicyMutation = useMutation({
    mutationFn: async () => {
      setDiscountPolicySuccessMessage(null);
      if (!companyId) throw new Error('Company ID is missing.');
      try {
        const res = await http.put<ApiResponse<CompanyDTO>>(
          `/api/companies/${companyId}/discount-policies`,
          discountPoliciesDraft
        );

        if (res.data.error) throw new Error(res.data.error);
        return res.data.data ?? null;
      } catch (e) {
        const err = e as AxiosError<ApiResponse<CompanyDTO>>;

        if (err.response?.status === 403) {
          throw new Error('You do not have permission to edit the discount policies for this company.');
        }

        throw new Error(getApiErrorMessage<CompanyDTO>(e));
      }
    },
    onSuccess: async () => {
      setDiscountPolicySuccessMessage('Company discount policies saved successfully.');
      setDiscountPoliciesDirty(false);
      await qc.invalidateQueries({ queryKey: ['company', companyId, token] });
      await qc.invalidateQueries({ queryKey: ['company', 'discount-policies', companyId] });
    },
  });

  const addPurchasePolicyToDraft = () => {
    setPurchasePolicyAddError(null);
    setPurchasePolicySuccessMessage(null);
    try {
      const policy = buildPurchasePolicy();
      setPurchasePoliciesDirty(true);
      setPurchasePoliciesDraft((prev) => [...prev, policy]);
      setPurchasePolicyKind('NONE');
      setMaxTickets('');
      setMinTickets('');
      setMinAge('');
    } catch (e) {
      setPurchasePolicyAddError((e as Error).message);
    }
  };

  const addDiscountPolicyToDraft = () => {
    setDiscountPolicyAddError(null);
    setDiscountPolicySuccessMessage(null);
    try {
      const policy = buildDiscountPolicy();
      setDiscountPoliciesDirty(true);
      setDiscountPoliciesDraft((prev) => [...prev, policy]);
      setDiscountPolicyKind('NONE');
      setDiscountPercent('');
      setCouponCode('');
      setCouponExpiresAt('');
      setConditionMaxTickets('');
      setConditionMinTickets('');
      setWindowFrom('');
      setWindowTo('');
    } catch (e) {
      setDiscountPolicyAddError((e as Error).message);
    }
  };

  const removePurchasePolicyFromDraft = (idx: number) => {
    setPurchasePoliciesDirty(true);
    setPurchasePoliciesDraft((prev) => prev.filter((_, i) => i !== idx));
  };

  const removeDiscountPolicyFromDraft = (idx: number) => {
    setDiscountPoliciesDirty(true);
    setDiscountPoliciesDraft((prev) => prev.filter((_, i) => i !== idx));
  };

  const appointOwnerMutation = useMutation({
    mutationFn: async () => {
      setOwnerSuccessMessage(null);
      const username = ownerUsername.trim();
      if (!username) {
        throw new Error('Please enter a username.');
      }
      if (!companyId) {
        throw new Error('Company ID is missing.');
      }

      try {
        const resolved = await http.get<ApiResponse<MemberDTO>>('/api/users/members/resolve', {
          params: { username },
        });
        if (resolved.data.error) throw new Error(resolved.data.error);
        const memberId = resolved.data.data?.userId;
        if (!memberId) throw new Error('Member not found');

        const appointed = await http.post<ApiResponse<MemberDTO>>('/api/users/roles/owner', {
          memberId,
          companyId,
        });
        if (appointed.data.error) throw new Error(appointed.data.error);
        return appointed.data.data ?? null;
      } catch (e) {
        const err = e as AxiosError<ApiResponse<MemberDTO>>;
        const status = err.response?.status;

        if (status === 401) {
          clearAuth();
          throw new Error('Your session expired. Please log in again.');
        }

        throw new Error(
          getApiErrorMessage<MemberDTO>(e, {
            fallback: 'Failed to add owner. Please try again.',
            serverFallback: 'Adding an owner is currently unavailable due to a server issue. Please try again later.',
          })
        );
      }
    },
    onSuccess: () => {
      setOwnerUsername('');
      setOwnerSuccessMessage('Owner added.');
    },
  });

  const removeOwnerMutation = useMutation({
    mutationFn: async () => {
      setRemoveOwnerSuccessMessage(null);
      const username = removeOwnerUsername.trim();
      if (!username) {
        throw new Error('Please enter a username.');
      }
      if (!companyId) {
        throw new Error('Company ID is missing.');
      }

      try {
        const resolved = await http.get<ApiResponse<MemberDTO>>('/api/users/members/resolve', {
          params: { username },
        });
        if (resolved.data.error) throw new Error(resolved.data.error);
        const memberToRemoveId = resolved.data.data?.userId;
        if (!memberToRemoveId) throw new Error('Member not found');

        const removed = await http.post<ApiResponse<MemberDTO>>('/api/users/roles/owner/remove', {
          memberToRemoveId,
          companyId,
        });
        if (removed.data.error) throw new Error(removed.data.error);
        return removed.data.data ?? null;
      } catch (e) {
        const err = e as AxiosError<ApiResponse<MemberDTO>>;
        const status = err.response?.status;

        if (status === 401) {
          clearAuth();
          throw new Error('Your session expired. Please log in again.');
        }

        throw new Error(
          getApiErrorMessage<MemberDTO>(e, {
            fallback: 'Failed to remove owner. Please try again.',
            serverFallback: 'Removing an owner is currently unavailable due to a server issue. Please try again later.',
          })
        );
      }
    },
    onSuccess: () => {
      setRemoveOwnerUsername('');
      setRemoveOwnerSuccessMessage('Owner removed.');
    },
  });

  const appointManagerMutation = useMutation({
    mutationFn: async () => {
      setManagerSuccessMessage(null);

      if (!companyId) {
        throw new Error('Company ID is missing.');
      }

      if (!managerEventId) {
        throw new Error('Please select an event.');
      }

      const username = managerUsername.trim();

      if (!username) {
        throw new Error('Please enter a username.');
      }

      const resolved = await http.get<ApiResponse<MemberDTO>>(
        '/api/users/members/resolve',
        {
          params: { username },
        }
      );

      if (resolved.data.error) {
        throw new Error(resolved.data.error);
      }

      const memberId = resolved.data.data?.userId;

      if (!memberId) {
        throw new Error('Member not found.');
      }

      const res = await http.post<ApiResponse<MemberDTO>>(
        '/api/users/roles/manager',
        {
          memberId,
          companyId,
          eventId: managerEventId,
          permissions: managerPermissions,
        }
      );

      if (res.data.error) {
        throw new Error(res.data.error);
      }

      return res.data.data;
    },

    onSuccess: () => {
      setManagerUsername('');
      setManagerPermissions([]);
      setManagerEventId('');
      setManagerSuccessMessage('Event manager appointed successfully.');
    },
  });

  const removeManagerMutation = useMutation({
    mutationFn: async () => {
      setRemoveManagerSuccessMessage(null);

      if (!companyId) {
        throw new Error('Company ID is missing.');
      }

      if (!removeManagerEventId) {
        throw new Error('Please select an event.');
      }

      const username = removeManagerUsername.trim();

      if (!username) {
        throw new Error('Please enter a username.');
      }

      const resolved = await http.get<ApiResponse<MemberDTO>>(
        '/api/users/members/resolve',
        {
          params: { username },
        }
      );

      if (resolved.data.error) {
        throw new Error(resolved.data.error);
      }

      const memberToRemoveId = resolved.data.data?.userId;

      if (!memberToRemoveId) {
        throw new Error('Member not found.');
      }

      const res = await http.post<ApiResponse<MemberDTO>>(
        '/api/users/roles/manager/remove',
        {
          memberToRemoveId,
          companyId,
          eventId: removeManagerEventId,
        }
      );

      if (res.data.error) {
        throw new Error(res.data.error);
      }

      return res.data.data;
    },

    onSuccess: () => {
      setRemoveManagerUsername('');
      setRemoveManagerEventId('');
      setRemoveManagerSuccessMessage('Event manager removed successfully.');
    },
  });

  const changeManagerPermissionsMutation = useMutation({
    mutationFn: async () => {
      setChangeManagerSuccessMessage(null);

      if (!changeManagerEventId) {
        throw new Error('Please select an event.');
      }

      const username = changeManagerUsername.trim();

      if (!username) {
        throw new Error('Please enter a username.');
      }

      const resolved = await http.get<ApiResponse<MemberDTO>>(
        '/api/users/members/resolve',
        { params: { username } }
      );

      if (resolved.data.error) throw new Error(resolved.data.error);

      const managerId = resolved.data.data?.userId;

      if (!managerId) {
        throw new Error('Manager not found.');
      }

      const res = await http.post<ApiResponse<MemberDTO>>(
        '/api/users/roles/manager/permissions',
        {
          managerId,
          eventId: changeManagerEventId,
          newPermissions: newManagerPermissions,
        }
      );

      if (res.data.error) throw new Error(res.data.error);

      return res.data.data;
    },

    onSuccess: () => {
      setChangeManagerUsername('');
      setChangeManagerEventId('');
      setNewManagerPermissions([]);
      setChangeManagerSuccessMessage('Event manager permissions updated successfully.');
    },
  });

  const appointCompanyManagerMutation = useMutation({
    mutationFn: async () => {
      setCompanyManagerSuccessMessage(null);

      try {
        if (!companyId) throw new Error('Company ID is missing.');

        const username = companyManagerUsername.trim();
        if (!username) throw new Error('Please enter a username.');

        const resolved = await http.get<ApiResponse<MemberDTO>>('/api/users/members/resolve', {
          params: { username },
        });

        if (resolved.data.error) throw new Error(resolved.data.error);

        const memberId = resolved.data.data?.userId;
        if (!memberId) throw new Error('Member not found.');

        const res = await http.post<ApiResponse<MemberDTO>>('/api/users/roles/company-manager', {
          memberId,
          companyId,
          permissions: companyManagerPermissions,
        });

        if (res.data.error) throw new Error(res.data.error);

        return res.data.data;
      } catch (e) {
        throw new Error(
          getApiErrorMessage<MemberDTO>(e, {
            fallback: 'Failed to appoint company manager.',
            serverFallback: 'Could not appoint company manager. Please check that the user can be appointed and try again.',
          })
        );
      }
    },
    onSuccess: () => {
      setCompanyManagerUsername('');
      setCompanyManagerPermissions([]);
      setCompanyManagerSuccessMessage('Company manager appointed successfully.');
    },
  });

  const changeCompanyManagerPermissionsMutation = useMutation({
    mutationFn: async () => {
      setChangeCompanyManagerSuccessMessage(null);

      try {
        if (!companyId) throw new Error('Company ID is missing.');

        const username = changeCompanyManagerUsername.trim();
        if (!username) throw new Error('Please enter a username.');

        const resolved = await http.get<ApiResponse<MemberDTO>>('/api/users/members/resolve', {
          params: { username },
        });

        if (resolved.data.error) throw new Error(resolved.data.error);

        const companyManagerId = resolved.data.data?.userId;
        if (!companyManagerId) throw new Error('Company manager not found.');

        const res = await http.post<ApiResponse<MemberDTO>>('/api/users/roles/company-manager/permissions', {
          companyManagerId,
          companyId,
          newPermissions: newCompanyManagerPermissions,
        });

        if (res.data.error) throw new Error(res.data.error);

        return res.data.data;
      } catch (e) {
        throw new Error(
          getApiErrorMessage<MemberDTO>(e, {
            fallback: 'Failed to change company manager permissions.',
            serverFallback: 'Could not change company manager permissions. Please try again.',
          })
        );
      }
    },
    onSuccess: () => {
      setChangeCompanyManagerUsername('');
      setNewCompanyManagerPermissions([]);
      setChangeCompanyManagerSuccessMessage('Company manager permissions updated successfully.');
    },
  });

  const removeCompanyManagerMutation = useMutation({
    mutationFn: async () => {
      setRemoveCompanyManagerSuccessMessage(null);

      try {
        if (!companyId) throw new Error('Company ID is missing.');

        const username = removeCompanyManagerUsername.trim();
        if (!username) throw new Error('Please enter a username.');

        const resolved = await http.get<ApiResponse<MemberDTO>>('/api/users/members/resolve', {
          params: { username },
        });

        if (resolved.data.error) throw new Error(resolved.data.error);

        const memberToRemoveId = resolved.data.data?.userId;
        if (!memberToRemoveId) throw new Error('Member not found.');

        const res = await http.post<ApiResponse<MemberDTO>>('/api/users/roles/company-manager/remove', {
          memberToRemoveId,
          companyId,
        });

        if (res.data.error) throw new Error(res.data.error);

        return res.data.data;
      } catch (e) {
        throw new Error(
          getApiErrorMessage<MemberDTO>(e, {
            fallback: 'Failed to remove company manager.',
            serverFallback: 'Could not remove company manager. Please try again.',
          })
        );
      }
    },
    onSuccess: () => {
      setRemoveCompanyManagerUsername('');
      setRemoveCompanyManagerSuccessMessage('Company manager removed successfully.');
    },
  });

  const resignMutation = useMutation({
    mutationFn: async () => {
      setResignSuccessMessage(null);
      if (!companyId) {
        throw new Error('Company ID is missing.');
      }

      try {
        const res = await http.post<ApiResponse<MemberDTO>>(`/api/users/roles/owner/resign/${companyId}`);
        if (res.data.error) throw new Error(res.data.error);
        return res.data.data ?? null;
      } catch (e) {
        const err = e as AxiosError<ApiResponse<MemberDTO>>;
        const status = err.response?.status;

        if (status === 401) {
          clearAuth();
          throw new Error('Your session expired. Please log in again.');
        }

        throw new Error(
          getApiErrorMessage<MemberDTO>(e, {
            fallback: 'Failed to resign from ownership. Please try again.',
            serverFallback: 'Ownership resignation is currently unavailable due to a server issue. Please try again later.',
          })
        );
      }
    },
    onSuccess: () => {
      setResignSuccessMessage('You resigned from ownership.');
    },
  });

  const changeStatusMutation = useMutation({
    mutationFn: async (newStatus: 'ACTIVE' | 'SUSPENDED' | 'CLOSED') => {
      setStatusSuccessMessage(null);
      if (!companyId) {
        throw new Error('Company ID is missing.');
      }

      try {
        const endpoint =
          newStatus === 'CLOSED'
            ? `/api/companies/${companyId}/close`
            : `/api/companies/${companyId}/activate`;

        const res = await http.patch<ApiResponse<CompanyDTO>>(endpoint);
        if (res.data.error) throw new Error(res.data.error);
        return res.data.data ?? null;
      } catch (e) {
        const err = e as AxiosError<ApiResponse<CompanyDTO>>;
        const status = err.response?.status;

        if (status === 401) {
          clearAuth();
          throw new Error('Your session expired. Please log in again.');
        }

        throw new Error(
          getApiErrorMessage<CompanyDTO>(e, {
            fallback: 'Failed to update company status. Please try again.',
            serverFallback: 'Company updates are currently unavailable due to a server issue. Please try again later.',
          })
        );
      }
    },
    onSuccess: async () => {
      setStatusSuccessMessage('Company status updated.');
      await qc.invalidateQueries({ queryKey: ['company'] });
      await qc.invalidateQueries({ queryKey: ['companies'] });
    },
  });

  if (userType !== 'member') {
    return (
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">Company</h1>
        <p className="mt-2 text-slate-600">Log in as a user to view companies.</p>
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
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">Company</h1>
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

  if (companyQuery.isPending) return <div className="text-slate-600">Loading…</div>;

  if (companyQuery.isError) {
    return (
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">Company</h1>
        <div className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
          {(companyQuery.error as Error).message}
        </div>
        <div className="mt-4">
          <Link
            to="/companies/me"
            className="inline-flex items-center rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800"
          >
            Back to My Companies
          </Link>
        </div>
      </div>
    );
  }

  const me = meQuery.data;
  const company = companyQuery.data;
  const isFounder = Boolean(meQuery.data?.userId) && meQuery.data?.userId === company.founderId;
  const allPermissions: ManagerPermission[] = [
    'MANAGE_EVENTS',
    'CONFIGURE_HALLS_AND_SEATS',
    'UPDATE_EVENT_MAP',
    'DEFINE_PURCHASE_POLICY',
    'DEFINE_DISCOUNT_POLICY',
    'HANDLE_INQUIRIES',
    'VIEW_PURCHASE_AND_ORDER_HISTORY',
    'GENERATE_SALES_REPORTS',
  ];

  return (
    <div className="space-y-4">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-extrabold tracking-tight text-slate-900">{company.name}</h1>
          <p className="mt-1 text-sm text-slate-600">Status: {company.status}</p>
        </div>
        <Link
          to="/companies/me"
          className="inline-flex items-center justify-center rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-900 hover:bg-slate-50"
        >
          Back
        </Link>
      </div>

      {isFounder && (
        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
          <div className="text-slate-900 font-semibold">Founder actions</div>
          <div className="mt-2 text-sm text-slate-600">
            Only the company founder can suspend, close, or reopen the company.
          </div>

          <div className="mt-4 flex flex-wrap gap-2">
            <button
              onClick={() => changeStatusMutation.mutate('CLOSED')}
              disabled={changeStatusMutation.isPending}
              className="rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-sm font-semibold text-rose-900 hover:bg-rose-100 disabled:opacity-60"
            >
              Close
            </button>
            <button
              onClick={() => changeStatusMutation.mutate('ACTIVE')}
              disabled={changeStatusMutation.isPending}
              className="rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
            >
              Reopen
            </button>
          </div>

          {changeStatusMutation.isError && (
            <div className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
              {(changeStatusMutation.error as Error).message}
            </div>
          )}

          {statusSuccessMessage && !changeStatusMutation.isError && (
            <div className="mt-3 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
              {statusSuccessMessage}
            </div>
          )}
        </div>
      )}

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="text-slate-900 font-semibold">Company policies</div>
        <div className="mt-2 text-sm text-slate-600">
          Update company-level purchase and discount policies.
        </div>

        <div className="mt-4 grid gap-6 md:grid-cols-2">
          <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
            <div className="text-sm font-semibold text-slate-900">Purchase policies</div>

            {companyPurchasePoliciesQuery.isPending ? (
              <div className="mt-2 text-sm text-slate-600">Loading…</div>
            ) : companyPurchasePoliciesQuery.isError ? (
              <div className="mt-2 text-sm text-rose-700">
                {getApiErrorMessage(companyPurchasePoliciesQuery.error)}
              </div>
            ) : (
              <div className="mt-3 space-y-2">
                {purchasePoliciesDraft.map((p: any, idx: number) => (
                  <div
                    key={idx}
                    className="flex items-start justify-between gap-2 rounded-md border border-slate-200 bg-white px-3 py-2"
                  >
                    <div className="text-sm text-slate-800">
                      {describeCompanyPurchasePolicy(p).map((line, j) => (
                        <div key={j}>{line}</div>
                      ))}
                    </div>
                    <button
                      onClick={() => removePurchasePolicyFromDraft(idx)}
                      className="shrink-0 rounded-md border border-rose-200 bg-rose-50 px-2 py-1 text-xs font-semibold text-rose-900"
                    >
                      Remove
                    </button>
                  </div>
                ))}

                {purchasePoliciesDraft.length === 0 && (
                  <div className="text-sm text-slate-600">No purchase policies.</div>
                )}
              </div>
            )}

            <div className="mt-4 border-t border-slate-200 pt-4">
              <div className="text-sm font-medium text-slate-700">Add a purchase policy</div>
              <select
                value={purchasePolicyKind}
                onChange={(e) => setPurchasePolicyKind(e.target.value as any)}
                className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
              >
                <option value="NONE">Select purchase policy…</option>
                <option value="MAX_TICKETS">Max tickets per order</option>
                <option value="MIN_TICKETS">Min tickets per order</option>
                <option value="MIN_AGE">Age restriction</option>
              </select>

              {purchasePolicyKind === 'MAX_TICKETS' && (
                <div className="mt-3">
                  <div className="text-sm font-medium text-slate-700">Max tickets</div>
                  <input
                    value={maxTickets}
                    onChange={(e) => setMaxTickets(e.target.value)}
                    placeholder="e.g. 4"
                    className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
                  />
                </div>
              )}

              {purchasePolicyKind === 'MIN_TICKETS' && (
                <div className="mt-3">
                  <div className="text-sm font-medium text-slate-700">Min tickets</div>
                  <input
                    value={minTickets}
                    onChange={(e) => setMinTickets(e.target.value)}
                    placeholder="e.g. 2"
                    className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
                  />
                </div>
              )}

              {purchasePolicyKind === 'MIN_AGE' && (
                <div className="mt-3">
                  <div className="text-sm font-medium text-slate-700">Minimum age</div>
                  <input
                    value={minAge}
                    onChange={(e) => setMinAge(e.target.value)}
                    placeholder="e.g. 18"
                    className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
                  />
                </div>
              )}

              <button
                onClick={addPurchasePolicyToDraft}
                className="mt-3 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-900 hover:bg-slate-50"
              >
                Add purchase policy
              </button>

              {purchasePolicyAddError && (
                <div className="mt-2 rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
                  {purchasePolicyAddError}
                </div>
              )}
            </div>

            <button
              onClick={() => updatePurchasePolicyMutation.mutate()}
              disabled={updatePurchasePolicyMutation.isPending || !companyPurchasePoliciesQuery.isSuccess}
              className="mt-4 w-full rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
            >
              {updatePurchasePolicyMutation.isPending ? 'Saving…' : 'Save purchase policies'}
            </button>

            {updatePurchasePolicyMutation.isError && (
              <div className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
                {(updatePurchasePolicyMutation.error as Error).message}
              </div>
            )}

            {purchasePolicySuccessMessage && !updatePurchasePolicyMutation.isError && (
              <div className="mt-3 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
                {purchasePolicySuccessMessage}
              </div>
            )}
          </div>

          <div className="rounded-xl border border-slate-200 bg-slate-50 p-4">
            <div className="text-sm font-semibold text-slate-900">Discount policies</div>

            {companyDiscountPoliciesQuery.isPending ? (
              <div className="mt-2 text-sm text-slate-600">Loading…</div>
            ) : companyDiscountPoliciesQuery.isError ? (
              <div className="mt-2 text-sm text-rose-700">
                {getApiErrorMessage(companyDiscountPoliciesQuery.error)}
              </div>
            ) : (
              <div className="mt-3 space-y-2">
                {discountPoliciesDraft.map((p: any, idx: number) => (
                  <div
                    key={idx}
                    className="flex items-start justify-between gap-2 rounded-md border border-slate-200 bg-white px-3 py-2"
                  >
                    <div className="text-sm text-slate-800">
                      {describeCompanyDiscountPolicy(p).map((line, j) => (
                        <div key={j}>{line}</div>
                      ))}
                    </div>
                    <button
                      onClick={() => removeDiscountPolicyFromDraft(idx)}
                      className="shrink-0 rounded-md border border-rose-200 bg-rose-50 px-2 py-1 text-xs font-semibold text-rose-900"
                    >
                      Remove
                    </button>
                  </div>
                ))}

                {discountPoliciesDraft.length === 0 && (
                  <div className="text-sm text-slate-600">No discount policies.</div>
                )}
              </div>
            )}

            <div className="mt-4 border-t border-slate-200 pt-4">
              <div className="text-sm font-medium text-slate-700">Add a discount policy</div>
              <select
                value={discountPolicyKind}
                onChange={(e) => setDiscountPolicyKind(e.target.value as any)}
                className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
              >
                <option value="NONE">Select discount policy…</option>
                <option value="SIMPLE">Simple discount</option>
                <option value="CONDITIONAL">Conditional discount</option>
                <option value="COUPON">Coupon code</option>
              </select>

              {(discountPolicyKind === 'SIMPLE' || discountPolicyKind === 'CONDITIONAL' || discountPolicyKind === 'COUPON') && (
                <div className="mt-3">
                  <div className="text-sm font-medium text-slate-700">Discount percent</div>
                  <input
                    value={discountPercent}
                    onChange={(e) => setDiscountPercent(e.target.value)}
                    placeholder="e.g. 10"
                    className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
                  />
                </div>
              )}

              {discountPolicyKind === 'COUPON' && (
                <div className="mt-3 grid gap-3">
                  <div>
                    <div className="text-sm font-medium text-slate-700">Coupon code</div>
                    <input
                      value={couponCode}
                      onChange={(e) => setCouponCode(e.target.value)}
                      placeholder="e.g. SUMMER10"
                      className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
                    />
                  </div>
                  <div>
                    <div className="text-sm font-medium text-slate-700">Expires at (optional)</div>
                    <input
                      type="datetime-local"
                      value={couponExpiresAt}
                      onChange={(e) => setCouponExpiresAt(e.target.value)}
                      className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
                    />
                  </div>
                </div>
              )}

              {discountPolicyKind === 'CONDITIONAL' && (
                <div className="mt-3 grid gap-3">
                  <div>
                    <div className="text-sm font-medium text-slate-700">Condition</div>
                    <select
                      value={conditionKind}
                      onChange={(e) => setConditionKind(e.target.value as any)}
                      className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
                    >
                      <option value="MAX_TICKETS">Max tickets</option>
                      <option value="MIN_TICKETS">Min tickets</option>
                      <option value="TIME_WINDOW">Time window</option>
                    </select>
                  </div>

                  {conditionKind === 'MAX_TICKETS' && (
                    <div>
                      <div className="text-sm font-medium text-slate-700">Max tickets</div>
                      <input
                        value={conditionMaxTickets}
                        onChange={(e) => setConditionMaxTickets(e.target.value)}
                        placeholder="e.g. 4"
                        className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
                      />
                    </div>
                  )}

                  {conditionKind === 'MIN_TICKETS' && (
                    <div>
                      <div className="text-sm font-medium text-slate-700">Min tickets</div>
                      <input
                        value={conditionMinTickets}
                        onChange={(e) => setConditionMinTickets(e.target.value)}
                        placeholder="e.g. 2"
                        className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
                      />
                    </div>
                  )}

                  {conditionKind === 'TIME_WINDOW' && (
                    <div className="grid gap-3 md:grid-cols-2">
                      <label className="block">
                        <div className="text-sm font-medium text-slate-700">From</div>
                        <input
                          type="datetime-local"
                          value={windowFrom}
                          onChange={(e) => setWindowFrom(e.target.value)}
                          className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
                        />
                      </label>
                      <label className="block">
                        <div className="text-sm font-medium text-slate-700">To</div>
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

              <button
                onClick={addDiscountPolicyToDraft}
                className="mt-3 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-900 hover:bg-slate-50"
              >
                Add discount policy
              </button>

              {discountPolicyAddError && (
                <div className="mt-2 rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
                  {discountPolicyAddError}
                </div>
              )}
            </div>

            <button
              onClick={() => updateDiscountPolicyMutation.mutate()}
              disabled={updateDiscountPolicyMutation.isPending || !companyDiscountPoliciesQuery.isSuccess}
              className="mt-4 w-full rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
            >
              {updateDiscountPolicyMutation.isPending ? 'Saving…' : 'Save discount policies'}
            </button>

            {updateDiscountPolicyMutation.isError && (
              <div className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
                {(updateDiscountPolicyMutation.error as Error).message}
              </div>
            )}

            {discountPolicySuccessMessage && !updateDiscountPolicyMutation.isError && (
              <div className="mt-3 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
                {discountPolicySuccessMessage}
              </div>
            )}
          </div>
        </div>
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="text-slate-900 font-semibold">Add owner</div>
        <div className="mt-2 text-sm text-slate-600">Enter a member username to appoint them as an owner.</div>

        <div className="mt-4 grid gap-3 md:grid-cols-3">
          <label className="block md:col-span-2">
            <div className="text-sm font-medium text-slate-700">Username</div>
            <input
              value={ownerUsername}
              onChange={(e) => setOwnerUsername(e.target.value)}
              placeholder="e.g. alice"
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm"
            />
          </label>
          <div className="flex items-end">
            <button
              onClick={() => appointOwnerMutation.mutate()}
              disabled={appointOwnerMutation.isPending}
              className="w-full rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
            >
              {appointOwnerMutation.isPending ? 'Adding…' : 'Add owner'}
            </button>
          </div>
        </div>

        {appointOwnerMutation.isError && (
          <div className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {(appointOwnerMutation.error as Error).message}
          </div>
        )}

        {ownerSuccessMessage && !appointOwnerMutation.isError && (
          <div className="mt-3 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
            {ownerSuccessMessage}
          </div>
        )}
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="text-slate-900 font-semibold">Remove owner appointment</div>
        <div className="mt-2 text-sm text-slate-600">
          Enter a member username to remove their owner appointment (only if they were appointed by you).
        </div>

        <div className="mt-4 grid gap-3 md:grid-cols-3">
          <label className="block md:col-span-2">
            <div className="text-sm font-medium text-slate-700">Username</div>
            <input
              value={removeOwnerUsername}
              onChange={(e) => setRemoveOwnerUsername(e.target.value)}
              placeholder="e.g. alice"
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm text-slate-900 shadow-sm"
            />
          </label>
          <div className="flex items-end">
            <button
              onClick={() => removeOwnerMutation.mutate()}
              disabled={removeOwnerMutation.isPending}
              className="w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-900 hover:bg-slate-50 disabled:opacity-60"
            >
              {removeOwnerMutation.isPending ? 'Removing…' : 'Remove owner'}
            </button>
          </div>
        </div>

        {removeOwnerMutation.isError && (
          <div className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {(removeOwnerMutation.error as Error).message}
          </div>
        )}

        {removeOwnerSuccessMessage && !removeOwnerMutation.isError && (
          <div className="mt-3 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
            {removeOwnerSuccessMessage}
          </div>
        )}
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="text-slate-900 font-semibold">Add Event Manager</div>

        <div className="mt-4 grid gap-4">
          <div>
            <div className="text-sm font-medium text-slate-700">Username</div>

            <input
              value={managerUsername}
              onChange={(e) => setManagerUsername(e.target.value)}
              placeholder="e.g. alice"
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
            />
          </div>

          <div>
            <div className="text-sm font-medium text-slate-700">Event</div>

            <select
              value={managerEventId}
              onChange={(e) => setManagerEventId(e.target.value)}
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
            >
              <option value="">Select event...</option>

              {eventsQuery.data?.map((event) => (
                <option key={event.eventId} value={event.eventId}>
                  {event.name}
                </option>
              ))}
            </select>
          </div>

          <div>
            <div className="text-sm font-medium text-slate-700">
              Permissions
            </div>

            <div className="mt-2 grid gap-2 md:grid-cols-2">
              {allPermissions.map((permission) => (
                <label
                  key={permission}
                  className="flex items-center gap-2 text-sm"
                >
                  <input
                    type="checkbox"
                    checked={managerPermissions.includes(permission)}
                    onChange={(e) => {
                      if (e.target.checked) {
                        setManagerPermissions((prev) => [...prev, permission]);
                      } else {
                        setManagerPermissions((prev) =>
                          prev.filter((p) => p !== permission)
                        );
                      }
                    }}
                  />
                  {permission
                    .toLowerCase()
                    .replaceAll('_', ' ')
                    .replace(/\b\w/g, (c) => c.toUpperCase())}
                </label>
              ))}
            </div>
          </div>

          <button
            onClick={() => appointManagerMutation.mutate()}
            disabled={appointManagerMutation.isPending}
            className="rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800"
          >
            Add Event Manager
          </button>

          {appointManagerMutation.isError && (
            <div className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
              {(appointManagerMutation.error as Error).message}
            </div>
          )}

          {managerSuccessMessage && (
            <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
              {managerSuccessMessage}
            </div>
          )}
        </div>
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="text-slate-900 font-semibold">Change Event Manager Permissions</div>

        <div className="mt-4 grid gap-4">
          <div>
            <div className="text-sm font-medium text-slate-700">Username</div>

            <input
              value={changeManagerUsername}
              onChange={(e) => setChangeManagerUsername(e.target.value)}
              placeholder="e.g. alice"
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
            />
          </div>

          <div>
            <div className="text-sm font-medium text-slate-700">Event</div>

            <select
              value={changeManagerEventId}
              onChange={(e) => setChangeManagerEventId(e.target.value)}
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
            >
              <option value="">Select event...</option>

              {eventsQuery.data?.map((event) => (
                <option key={event.eventId} value={event.eventId}>
                  {event.name}
                </option>
              ))}
            </select>
          </div>

          <div>
            <div className="text-sm font-medium text-slate-700">New permissions</div>

            <div className="mt-2 grid gap-2 md:grid-cols-2">
              {allPermissions.map((permission) => (
                <label key={permission} className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={newManagerPermissions.includes(permission)}
                    onChange={(e) => {
                      if (e.target.checked) {
                        setNewManagerPermissions((prev) => [...prev, permission]);
                      } else {
                        setNewManagerPermissions((prev) =>
                          prev.filter((p) => p !== permission)
                        );
                      }
                    }}
                  />

                  {permission
                    .toLowerCase()
                    .replaceAll('_', ' ')
                    .replace(/\b\w/g, (c) => c.toUpperCase())}
                </label>
              ))}
            </div>
          </div>

          <button
            onClick={() => changeManagerPermissionsMutation.mutate()}
            disabled={changeManagerPermissionsMutation.isPending}
            className="rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800"
          >
            {changeManagerPermissionsMutation.isPending
              ? 'Updating...'
              : 'Change permissions'}
          </button>

          {changeManagerPermissionsMutation.isError && (
            <div className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
              {(changeManagerPermissionsMutation.error as Error).message}
            </div>
          )}

          {changeManagerSuccessMessage && (
            <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
              {changeManagerSuccessMessage}
            </div>
          )}
        </div>
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="text-slate-900 font-semibold">
          Remove Event Manager Appointment
        </div>

        <div className="mt-4 grid gap-4">
          <div>
            <div className="text-sm font-medium text-slate-700">Username</div>

            <input
              value={removeManagerUsername}
              onChange={(e) => setRemoveManagerUsername(e.target.value)}
              placeholder="e.g. alice"
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
            />
          </div>

          <div>
            <div className="text-sm font-medium text-slate-700">Event</div>

            <select
              value={removeManagerEventId}
              onChange={(e) => setRemoveManagerEventId(e.target.value)}
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
            >
              <option value="">Select event...</option>

              {eventsQuery.data?.map((event) => (
                <option key={event.eventId} value={event.eventId}>
                  {event.name}
                </option>
              ))}
            </select>
          </div>

          <button
            onClick={() => removeManagerMutation.mutate()}
            disabled={removeManagerMutation.isPending}
            className="rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-sm font-semibold text-rose-900 hover:bg-rose-100"
          >
            Remove Event Manager
          </button>

          {removeManagerMutation.isError && (
            <div className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
              {(removeManagerMutation.error as Error).message}
            </div>
          )}

          {removeManagerSuccessMessage && (
            <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
              {removeManagerSuccessMessage}
            </div>
          )}
        </div>
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="text-slate-900 font-semibold">Add Company Manager</div>

        <div className="mt-2 text-sm text-slate-600">
          Company managers belong to the company and are not limited to a specific event.
        </div>

        <div className="mt-4 grid gap-3">
          <label className="block">
            <div className="text-sm font-medium text-slate-700">Username</div>
            <input
              value={companyManagerUsername}
              onChange={(e) => setCompanyManagerUsername(e.target.value)}
              placeholder="e.g. alice"
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
            />
          </label>

          <div className="grid gap-2 md:grid-cols-2">
            {allPermissions.map((permission) => (
              <label key={permission} className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={companyManagerPermissions.includes(permission)}
                  onChange={(e) => {
                    if (e.target.checked) {
                      setCompanyManagerPermissions((prev) => [...prev, permission]);
                    } else {
                      setCompanyManagerPermissions((prev) =>
                        prev.filter((p) => p !== permission)
                      );
                    }
                  }}
                />
                {permission
                  .toLowerCase()
                  .replaceAll('_', ' ')
                  .replace(/\b\w/g, (c) => c.toUpperCase())}
              </label>
            ))}
          </div>

          <button
            onClick={() => appointCompanyManagerMutation.mutate()}
            disabled={appointCompanyManagerMutation.isPending}
            className="rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
          >
            {appointCompanyManagerMutation.isPending
              ? 'Adding...'
              : 'Add Company Manager'}
          </button>

          {appointCompanyManagerMutation.isError && (
            <div className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
              {(appointCompanyManagerMutation.error as Error).message}
            </div>
          )}

          {companyManagerSuccessMessage && (
            <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
              {companyManagerSuccessMessage}
            </div>
          )}
        </div>
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="text-slate-900 font-semibold">
          Change Company Manager Permissions
        </div>

        <div className="mt-4 grid gap-3">
          <label className="block">
            <div className="text-sm font-medium text-slate-700">Username</div>
            <input
              value={changeCompanyManagerUsername}
              onChange={(e) => setChangeCompanyManagerUsername(e.target.value)}
              placeholder="e.g. alice"
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
            />
          </label>

          <div className="grid gap-2 md:grid-cols-2">
            {allPermissions.map((permission) => (
              <label key={permission} className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={newCompanyManagerPermissions.includes(permission)}
                  onChange={(e) => {
                    if (e.target.checked) {
                      setNewCompanyManagerPermissions((prev) => [
                        ...prev,
                        permission,
                      ]);
                    } else {
                      setNewCompanyManagerPermissions((prev) =>
                        prev.filter((p) => p !== permission)
                      );
                    }
                  }}
                />
                {permission
                  .toLowerCase()
                  .replaceAll('_', ' ')
                  .replace(/\b\w/g, (c) => c.toUpperCase())}
              </label>
            ))}
          </div>

          <button
            onClick={() => changeCompanyManagerPermissionsMutation.mutate()}
            disabled={changeCompanyManagerPermissionsMutation.isPending}
            className="rounded-md bg-slate-900 px-3 py-2 text-sm font-semibold text-white hover:bg-slate-800 disabled:opacity-60"
          >
            {changeCompanyManagerPermissionsMutation.isPending
              ? 'Updating...'
              : 'Change Company Manager Permissions'}
          </button>

          {changeCompanyManagerPermissionsMutation.isError && (
            <div className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
              {(changeCompanyManagerPermissionsMutation.error as Error).message}
            </div>
          )}

          {changeCompanyManagerSuccessMessage && (
            <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
              {changeCompanyManagerSuccessMessage}
            </div>
          )}
        </div>
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="text-slate-900 font-semibold">
          Remove Company Manager Appointment
        </div>

        <div className="mt-2 text-sm text-slate-600">
          Enter a username to remove their company manager appointment.
        </div>

        <div className="mt-4 grid gap-3">
          <label className="block">
            <div className="text-sm font-medium text-slate-700">Username</div>
            <input
              value={removeCompanyManagerUsername}
              onChange={(e) => setRemoveCompanyManagerUsername(e.target.value)}
              placeholder="e.g. alice"
              className="mt-1 w-full rounded-md border border-slate-200 bg-white px-3 py-2 text-sm"
            />
          </label>

          <button
            onClick={() => removeCompanyManagerMutation.mutate()}
            disabled={removeCompanyManagerMutation.isPending}
            className="rounded-md border border-rose-200 bg-rose-50 px-3 py-2 text-sm font-semibold text-rose-900 hover:bg-rose-100 disabled:opacity-60"
          >
            {removeCompanyManagerMutation.isPending
              ? 'Removing...'
              : 'Remove Company Manager'}
          </button>

          {removeCompanyManagerMutation.isError && (
            <div className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
              {(removeCompanyManagerMutation.error as Error).message}
            </div>
          )}

          {removeCompanyManagerSuccessMessage && (
            <div className="rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
              {removeCompanyManagerSuccessMessage}
            </div>
          )}
        </div>
      </div>

      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm">
        <div className="text-slate-900 font-semibold">Resign ownership</div>
        <div className="mt-2 text-sm text-slate-600">If you are an owner (not a founder), you can resign from this company.</div>

        <div className="mt-4">
          <button
            onClick={() => resignMutation.mutate()}
            disabled={resignMutation.isPending}
            className="rounded-md border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-900 hover:bg-slate-50 disabled:opacity-60"
          >
            {resignMutation.isPending ? 'Resigning…' : 'Resign'}
          </button>
        </div>

        {resignMutation.isError && (
          <div className="mt-3 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-800">
            {(resignMutation.error as Error).message}
          </div>
        )}

        {resignSuccessMessage && !resignMutation.isError && (
          <div className="mt-3 rounded-lg border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-800">
            {resignSuccessMessage}
          </div>
        )}
      </div>
    </div>
  );
}
