export type ApiResponse<T> = {
  data: T | null;
  error: string | null;
};

export type Category = string;
export type EventStatus = string;

export type EventAreaView = {
  areaId: string;
  name: string;
  // area type fields vary by backend; keep it permissive
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
  id: string;
  name: string;
  status: string;
  founderId?: string;
};

export type MemberDTO = {
  userId: string;
  username: string;
  birthDate?: string;
  activeRole?: string;
  assignedRoles?: string[];
};
