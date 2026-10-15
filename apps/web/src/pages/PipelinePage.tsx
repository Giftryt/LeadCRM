import { useState } from "react";
import { useLeads, useMoveStage } from "../api/hooks";
import { useAuth } from "../auth/AuthContext";
import { Avatar, FollowUpPill, StageDot } from "../components/bits";
import { EmptyLeads } from "../components/EmptyLeads";
import { errorText, useToast } from "../components/Toast";
import { STAGE_LABEL, STAGES, type Stage } from "../lib/constants";
import { useFilters } from "../lib/filters";
import { inrShort } from "../lib/format";

export function PipelinePage() {
  const { query, openLead } = useFilters();
  const { canWrite } = useAuth();
  const { data, isLoading, error } = useLeads({
    ...query,
    stage: undefined,
    pageSize: 1000,
    sort: "updatedAt",
    order: "desc",
  });
  const move = useMoveStage();
  const toast = useToast();
  const [dragId, setDragId] = useState<string | null>(null);
  const [overStage, setOverStage] = useState<Stage | null>(null);

  if (error) return <p className="banner">{errorText(error)}</p>;
  if (isLoading) return <p className="muted">Loading pipeline…</p>;
  if (!data?.items.length) return <EmptyLeads filtered={!!(query.q || query.owner || query.source)} />;

  const drop = (stage: Stage) => {
    const lead = data.items.find((l) => l.id === dragId);
    setDragId(null);
    setOverStage(null);
    if (!lead || lead.stage === stage) return;
    move.mutate(
      { id: lead.id, stage },
      {
        onSuccess: () => toast(`${lead.name} moved to ${STAGE_LABEL[stage]}`),
        onError: (e) => toast(errorText(e), { error: true }),
      },
    );
  };

  return (
    <>
      <div className="board">
        {STAGES.map((st) => {
          const items = data.items
            .filter((l) => l.stage === st.id)
            .sort((a, b) => (a.nextFollowUp ?? "9999").localeCompare(b.nextFollowUp ?? "9999"));
          const total = items.reduce((a, l) => a + l.valueInr, 0);
          return (
            <section
              key={st.id}
              className={`col${overStage === st.id ? " over" : ""}`}
              aria-label={st.label}
              onDragOver={(e) => {
                if (!dragId) return;
                e.preventDefault();
                setOverStage(st.id);
              }}
              onDragLeave={() => setOverStage((s) => (s === st.id ? null : s))}
              onDrop={(e) => {
                e.preventDefault();
                drop(st.id);
              }}
            >
              <div className="col-h">
                <StageDot stage={st.id} />
                <b>{st.label}</b>
                <span className="cnt">{items.length}</span>
                <span className="tot">{inrShort(total)}</span>
              </div>
              {items.map((l) => (
                <button
                  key={l.id}
                  className={`card${dragId === l.id ? " dragging" : ""}`}
                  draggable={canWrite}
                  onDragStart={(e) => {
                    setDragId(l.id);
                    e.dataTransfer.effectAllowed = "move";
                    e.dataTransfer.setData("text/plain", l.id);
                  }}
                  onDragEnd={() => {
                    setDragId(null);
                    setOverStage(null);
                  }}
                  onClick={() => openLead(l.id)}
                >
                  <span className="t">{l.name}</span>
                  {l.company && <span className="co">{l.company}</span>}
                  <span className="row">
                    <span className="amt num">{inrShort(l.valueInr)}</span>
                    <FollowUpPill lead={l} />
                    <Avatar user={l.owner} />
                  </span>
                </button>
              ))}
              {!items.length && <div className="col-empty">{canWrite ? "Drag a lead here" : "No leads"}</div>}
            </section>
          );
        })}
      </div>
      {canWrite && (
        <p className="hint">
          Drag cards between columns to change stage. On a phone, open a lead and change its stage there.
        </p>
      )}
    </>
  );
}
