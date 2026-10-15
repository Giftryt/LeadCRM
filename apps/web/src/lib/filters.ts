import { useCallback, useMemo } from "react";
import { useSearchParams } from "react-router-dom";
import type { LeadQuery } from "../api/client";

/**
 * Filters live in the URL (?q=&owner=&source=&stage=&lead=) so views are shareable and survive refresh.
 * `lead` opens the lead drawer: a lead id, or "new".
 */
export function useFilters() {
  const [sp, setSp] = useSearchParams();
  const filters = useMemo(
    () => ({
      q: sp.get("q") ?? "",
      owner: sp.get("owner") ?? "all",
      source: sp.get("source") ?? "all",
      stage: sp.get("stage") ?? "all",
    }),
    [sp],
  );

  const setParam = useCallback(
    (key: string, value: string | null) =>
      setSp(
        (prev) => {
          const next = new URLSearchParams(prev);
          if (!value || value === "all") next.delete(key);
          else next.set(key, value);
          return next;
        },
        { replace: key === "q" },
      ),
    [setSp],
  );

  const query: LeadQuery = useMemo(
    () => ({
      q: filters.q || undefined,
      owner: filters.owner === "all" ? undefined : filters.owner,
      source: filters.source === "all" ? undefined : filters.source,
      stage: filters.stage === "all" ? undefined : filters.stage,
    }),
    [filters],
  );

  return {
    filters,
    query,
    setFilter: setParam,
    openLeadId: sp.get("lead"),
    openLead: (id: string) => setParam("lead", id),
    closeLead: () => setParam("lead", null),
  };
}
