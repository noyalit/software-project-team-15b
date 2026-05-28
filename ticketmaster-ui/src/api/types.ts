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

export type SeatView = {
  seatId: string;
  row: string;
  number: string;
  status: string;
};

export type EventAreaView = {
  areaId: string;
  name: string;
  basePrice: MoneyDTO | null;
  type: 'SEATING' | 'STANDING' | string;
  availableCapacity: number;
  seats: SeatView[];
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

export type ManagerPermission =
  | 'MANAGE_EVENTS'
  | 'CONFIGURE_HALLS_AND_SEATS'
  | 'UPDATE_EVENT_MAP'
  | 'DEFINE_PURCHASE_POLICY'
  | 'DEFINE_DISCOUNT_POLICY'
  | 'HANDLE_INQUIRIES'
  | 'VIEW_PURCHASE_AND_ORDER_HISTORY'
  | 'GENERATE_SALES_REPORTS';

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



