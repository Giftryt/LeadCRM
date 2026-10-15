import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, type Lead, type LeadInput, type LeadQuery, type Page } from "./client";
import type { ActivityType, Stage } from "../lib/constants";

export const keys = {
  leads: (q: LeadQuery) => ["leads", q] as const,
  lead: (id: string) => ["lead", id] as const,
  activities: (id: string) => ["activities", id] as const,
  summary: (owner?: string) => ["summary", owner ?? "all"] as const,
  users: (all = false) => ["users", all] as const,
};

export const useLeads = (q: LeadQuery) =>
  useQuery({ queryKey: keys.leads(q), queryFn: () => api.leads(q), placeholderData: keepPreviousData });
export const useLead = (id: string | null) =>
  useQuery({ queryKey: keys.lead(id ?? ""), queryFn: () => api.lead(id!), enabled: !!id });
export const useActivities = (id: string | null) =>
  useQuery({ queryKey: keys.activities(id ?? ""), queryFn: () => api.activities(id!), enabled: !!id });
export const useSummary = (owner?: string) =>
  useQuery({ queryKey: keys.summary(owner), queryFn: () => api.summary(owner) });
export const useUsers = (includeInactive = false) =>
  useQuery({
    queryKey: keys.users(includeInactive),
    queryFn: () => api.users(includeInactive),
    staleTime: 60_000,
  });

/** Refresh everything a lead change can affect. */
function useInvalidateLeads() {
  const qc = useQueryClient();
  return (id?: string) => {
    qc.invalidateQueries({ queryKey: ["leads"] });
    qc.invalidateQueries({ queryKey: ["summary"] });
    if (id) {
      qc.invalidateQueries({ queryKey: keys.lead(id) });
      qc.invalidateQueries({ queryKey: keys.activities(id) });
    }
  };
}

export function useCreateLead() {
  const invalidate = useInvalidateLeads();
  return useMutation({
    mutationFn: (body: LeadInput) => api.createLead(body),
    onSuccess: (l) => invalidate(l.id),
  });
}

export function useUpdateLead() {
  const invalidate = useInvalidateLeads();
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: Partial<LeadInput> }) => api.updateLead(id, body),
    onSuccess: (l) => invalidate(l.id),
  });
}

/** Stage change from the board, applied to the cached lists immediately and rolled back on error. */
export function useMoveStage() {
  const qc = useQueryClient();
  const invalidate = useInvalidateLeads();
  return useMutation({
    mutationFn: ({ id, stage }: { id: string; stage: Stage }) => api.updateLead(id, { stage }),
    onMutate: async ({ id, stage }) => {
      await qc.cancelQueries({ queryKey: ["leads"] });
      const snapshot = qc.getQueriesData<Page<Lead>>({ queryKey: ["leads"] });
      qc.setQueriesData<Page<Lead>>({ queryKey: ["leads"] }, (old) =>
        old ? { ...old, items: old.items.map((l) => (l.id === id ? { ...l, stage } : l)) } : old,
      );
      return { snapshot };
    },
    onError: (_e, _v, ctx) => {
      for (const [key, data] of ctx?.snapshot ?? []) qc.setQueryData<Page<Lead>>(key, data);
    },
    onSettled: (_d, _e, v) => invalidate(v.id),
  });
}

export function useDeleteLead() {
  const invalidate = useInvalidateLeads();
  return useMutation({ mutationFn: (id: string) => api.deleteLead(id), onSuccess: () => invalidate() });
}

export function useLogActivity(leadId: string) {
  const invalidate = useInvalidateLeads();
  return useMutation({
    mutationFn: (body: { type: ActivityType; body: string; nextFollowUp?: string | null }) =>
      api.logActivity(leadId, body),
    onSuccess: () => invalidate(leadId),
  });
}

export function useImportLeads() {
  const invalidate = useInvalidateLeads();
  return useMutation({ mutationFn: (file: File) => api.importLeads(file), onSuccess: () => invalidate() });
}
