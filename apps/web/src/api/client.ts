import type { ActivityType, LeadSource, Role, Stage } from "../lib/constants";

/* ---------- response types (mirror the API serializers) ---------- */

export type UserRef = { id: string; name: string; email: string } | null;
export type Me = { id: string; email: string; name: string; role: Role };
export type User = Me & { active: boolean; createdAt: string };

export type Lead = {
  id: string;
  name: string;
  company: string | null;
  email: string | null;
  phone: string | null;
  source: LeadSource;
  stage: Stage;
  valueInr: number;
  nextFollowUp: string | null;
  notes: string | null;
  lostReason: string | null;
  owner: UserRef;
  createdBy: UserRef;
  stageChangedAt: string;
  wonAt: string | null;
  lastContactAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export type LeadInput = {
  name: string;
  company: string | null;
  email: string | null;
  phone: string | null;
  source: LeadSource;
  stage: Stage;
  valueInr: number;
  nextFollowUp: string | null;
  notes: string | null;
  lostReason: string | null;
  ownerId: string | null;
};

export type Activity = {
  id: string;
  leadId: string;
  type: ActivityType;
  body: string;
  user: UserRef;
  createdAt: string;
};

export type Page<T> = { items: T[]; total: number; page: number; pageSize: number };

export type Summary = {
  openPipeline: { count: number; valueInr: number };
  weightedForecastInr: number;
  wonThisMonth: { count: number; valueInr: number };
  winRate: number | null;
  followUps: { overdue: number; today: number };
  stages: { stage: Stage; count: number; valueInr: number }[];
};

export type ImportResult = { imported: number; skipped: number; errors: { row: number; message: string }[] };

export type LeadQuery = {
  q?: string;
  stage?: string;
  source?: string;
  owner?: string;
  followUp?: "overdue" | "today" | "week" | "none";
  open?: "true" | "false";
  sort?: string;
  order?: "asc" | "desc";
  page?: number;
  pageSize?: number;
};

/* ---------- fetch wrapper ---------- */

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public code: string,
    public details?: { field: string; message: string }[],
  ) {
    super(message);
  }
}

/** Called on any 401 so the app can send the user back to sign-in. */
let onUnauthorized: () => void = () => {};
export const setUnauthorizedHandler = (fn: () => void) => {
  onUnauthorized = fn;
};

export function toQueryString(params: Record<string, string | number | undefined>): string {
  const sp = new URLSearchParams();
  for (const [k, v] of Object.entries(params))
    if (v !== undefined && v !== "" && v !== "all") sp.set(k, String(v));
  const s = sp.toString();
  return s ? `?${s}` : "";
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const isForm = body instanceof FormData;
  let res: Response;
  try {
    res = await fetch(`/api${path}`, {
      method,
      credentials: "same-origin",
      headers: body && !isForm ? { "Content-Type": "application/json" } : undefined,
      body: body === undefined ? undefined : isForm ? body : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, "Couldn't reach the server. Check your connection and try again.", "network");
  }
  if (res.status === 204) return undefined as T;
  const data = await res.json().catch(() => null);
  if (!res.ok) {
    if (res.status === 401 && path !== "/auth/login") onUnauthorized();
    const err = data?.error ?? {};
    throw new ApiError(
      res.status,
      err.message ?? `Request failed (${res.status})`,
      err.code ?? "error",
      err.details,
    );
  }
  return data as T;
}

export const api = {
  login: (email: string, password: string) =>
    request<{ user: Me }>("POST", "/auth/login", { email, password }),
  logout: () => request<void>("POST", "/auth/logout"),
  me: () => request<{ user: Me }>("GET", "/auth/me"),
  changePassword: (currentPassword: string, newPassword: string) =>
    request<void>("POST", "/auth/change-password", { currentPassword, newPassword }),

  users: (includeInactive = false) =>
    request<{ items: User[] }>("GET", `/users${includeInactive ? "?includeInactive=true" : ""}`),
  createUser: (body: { name: string; email: string; role: Role; password: string }) =>
    request<User>("POST", "/users", body),
  updateUser: (id: string, body: Partial<{ name: string; role: Role; active: boolean; password: string }>) =>
    request<User>("PATCH", `/users/${id}`, body),

  leads: (q: LeadQuery) => request<Page<Lead>>("GET", `/leads${toQueryString(q)}`),
  lead: (id: string) => request<Lead>("GET", `/leads/${id}`),
  createLead: (body: LeadInput) => request<Lead>("POST", "/leads", body),
  updateLead: (id: string, body: Partial<LeadInput>) => request<Lead>("PATCH", `/leads/${id}`, body),
  deleteLead: (id: string) => request<void>("DELETE", `/leads/${id}`),
  activities: (leadId: string) => request<{ items: Activity[] }>("GET", `/leads/${leadId}/activities`),
  logActivity: (leadId: string, body: { type: ActivityType; body: string; nextFollowUp?: string | null }) =>
    request<{ activity: Activity; lead: Lead }>("POST", `/leads/${leadId}/activities`, body),
  importLeads: (file: File) => {
    const fd = new FormData();
    fd.append("file", file);
    return request<ImportResult>("POST", "/leads/import", fd);
  },
  exportUrl: (q: LeadQuery) => `/api/leads/export.csv${toQueryString(q)}`,

  summary: (owner?: string) => request<Summary>("GET", `/dashboard/summary${toQueryString({ owner })}`),
};
