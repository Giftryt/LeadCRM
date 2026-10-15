import { useState } from "react";
import { useLeads } from "../api/hooks";
import { Avatar, FollowUpPill, StageChip } from "../components/bits";
import { EmptyLeads } from "../components/EmptyLeads";
import { errorText } from "../components/Toast";
import { SOURCE_LABEL } from "../lib/constants";
import { useFilters } from "../lib/filters";
import { formatDay, inrFull } from "../lib/format";

const PAGE_SIZE = 50;
type SortKey = "name" | "stage" | "valueInr" | "nextFollowUp" | "updatedAt";

export function LeadsPage() {
  const { query, openLead } = useFilters();
  const [sort, setSort] = useState<{ key: SortKey; order: "asc" | "desc" }>({
    key: "updatedAt",
    order: "desc",
  });
  const [page, setPage] = useState(1);
  const { data, isLoading, error, isFetching } = useLeads({
    ...query,
    sort: sort.key,
    order: sort.order,
    page,
    pageSize: PAGE_SIZE,
  });

  if (error) return <p className="banner">{errorText(error)}</p>;
  if (isLoading) return <p className="muted">Loading leads…</p>;
  if (!data?.total)
    return <EmptyLeads filtered={!!(query.q || query.owner || query.source || query.stage)} />;

  const pages = Math.max(1, Math.ceil(data.total / PAGE_SIZE));
  const th = (key: SortKey, label: string, cls = "") => (
    <th
      className={cls}
      aria-sort={sort.key === key ? (sort.order === "asc" ? "ascending" : "descending") : undefined}
    >
      <button
        onClick={() => {
          setPage(1);
          setSort((s) =>
            s.key === key
              ? { key, order: s.order === "asc" ? "desc" : "asc" }
              : { key, order: key === "name" || key === "nextFollowUp" ? "asc" : "desc" },
          );
        }}
      >
        {label}
        {sort.key === key ? (sort.order === "asc" ? " ↑" : " ↓") : ""}
      </button>
    </th>
  );

  return (
    <>
      <div className="tbl-wrap" aria-busy={isFetching}>
        <table>
          <thead>
            <tr>
              {th("name", "Lead")}
              {th("stage", "Stage")}
              {th("valueInr", "Value", "r")}
              <th>Owner</th>
              {th("nextFollowUp", "Next follow-up")}
              <th>Source</th>
              {th("updatedAt", "Updated")}
            </tr>
          </thead>
          <tbody>
            {data.items.map((l) => (
              <tr
                key={l.id}
                onClick={() => openLead(l.id)}
                tabIndex={0}
                onKeyDown={(e) => e.key === "Enter" && openLead(l.id)}
              >
                <td>
                  <b>{l.name}</b>
                  <div className="muted">{l.company}</div>
                </td>
                <td>
                  <StageChip stage={l.stage} />
                </td>
                <td className="r num">{inrFull(l.valueInr)}</td>
                <td>
                  {l.owner ? (
                    <span className="chip">
                      <Avatar user={l.owner} />
                      {l.owner.name}
                    </span>
                  ) : (
                    <span className="muted">Unassigned</span>
                  )}
                </td>
                <td>
                  <FollowUpPill lead={l} /> {!l.nextFollowUp && <span className="muted">—</span>}
                </td>
                <td>{SOURCE_LABEL[l.source]}</td>
                <td className="muted num">{formatDay(l.updatedAt.slice(0, 10))}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="pager">
        <span className="muted">
          {(page - 1) * PAGE_SIZE + 1}–{Math.min(page * PAGE_SIZE, data.total)} of {data.total}
        </span>
        <button className="btn sm" disabled={page <= 1} onClick={() => setPage((p) => p - 1)}>
          Previous
        </button>
        <button className="btn sm" disabled={page >= pages} onClick={() => setPage((p) => p + 1)}>
          Next
        </button>
      </div>
    </>
  );
}
