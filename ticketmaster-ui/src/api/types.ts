export type ApiResponse<T> = {
  data: T | null;
  error: string | null;
};

export type Category = string;
export type EventStatus = string;

export type CompanyStatus = 'ACTIVE' | 'SUSPENDED' | 'CLOSED';

export type MoneyDTO = {
  amount: string;
  currency: string;
};

export type TicketDTO = {
  seatId: string;
  basePrice: MoneyDTO | null;
};

export type OrderHistoryDTO = {
  orderId: string;
  userId: string;
  eventId: string;
  areaId: string;
  totalPrice: MoneyDTO | null;
  tickets: TicketDTO[];
  cancelled: boolean;
};

export type EventAreaView = {
  areaId: string;
  name: string;
  [k: string]: unknown;
};

export type EventDTO = {
  eventId: string;
  companyId: string;
  name: string;
  artist: string;
  category: Category;
  startsAt: string;
  location: string;
  status: EventStatus;
  areas: EventAreaView[];
};

export type CompanyDTO = {
  companyId: string;
  name: string;
  status: CompanyStatus;
  founderId?: string;
};

export type MemberDTO = {
  userId: string;
  username: string;
  birthDate?: string;
  activeRole?: string;
  assignedRoles?: string[];
};

export type QueueSnapshotDTO = {
  eventId: string;
  capacity: number;
  maxAccepted: number;
  waitingCount: number;
  admittedCount: number;
  admittedUsers: Record<string, string>;
};

export type SiteQueueSnapshotDTO = {
  maxVisitors: number;
  waitingCount: number;
  admittedCount: number;
};
