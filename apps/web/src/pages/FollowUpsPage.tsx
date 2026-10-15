import type { Lead } from "../api/client";
import { useLeads, useUpdateLead } from "../api/hooks";
import { useAuth } from "../auth/AuthContext";
import { Avatar, StageDot } from "../components/bits";
import { errorText, useToast } from "../components/Toast";
import { STAGE_LABEL } from "../lib/constants";
import { useFilters } from "../lib/filters";
import { addDaysYmd, formatDay, inrShort, todayYmd } from "../lib/format";

export function FollowUpsPage() {
  const { query, openLead } = useFilters();
  const { canWrite } = useAuth();
  const { data, isLoading, error } = useLeads({
    ...query,
    open: "true",
    pageSize: 1000,
    sort: "nextFollowUp",
    order: "asc",
  });
  const update = useUpdateLead();
  const toast = useToast();

  if (error) return <p className="banner">{errorText(error)}</p>;
  if (isLoading) return <p className="muted">Loading follow-ups…</p>;

  const t = todayYmd();
  const week = addDaysYmd(t, 7);
  const items = data?.items ?? [];
  const groups: [string, Lead[]][] = [
    ["Overdue", items.filter((l) => l.nextFollowUp && l.nextFollowUp < t)],
    ["Today", items.filter((l) => l.nextFollowUp === t)],
    ["Next 7 days", items.filter((l) => l.nextFollowUp && l.nextFollowUp > t && l.nextFollowUp <= week)],
    ["Later", items.filter((l) => l.nextFollowUp && l.nextFollowUp > week)],
    ["No follow-up set", items.filter((l) => !l.nextFollowUp)],
  ];

  const snooze = (l: Lead, days: number) => {
    const base = l.nextFollowUp && l.nextFollowUp > t ? l.nextFollowUp : t;
    const next = addDaysYmd(base, days);
    update.mutate(
      { id: l.id, body: { nextFollowUp: next } },
      {
        onSuccess: () => toast(`Follow-up moved to ${formatDay(next)}`),
        onError: (e) => toast(errorText(e), { error: true }),
      },
    );
  };

  if (!items.length) {
    return (
      <div className="empty">
        <h2>Nothing to follow up</h2>
        <p>Open leads with a follow-up date show up here, grouped by when they're due.</p>
      </div>
    );
  }

  return (
    <div className="fu">
      {groups
        .filter(([, list]) => list.length)
        .map(([title, list]) => (
          <section key={title}>
            <h2>
              {title} <span className="cnt">{list.length}</span>
            </h2>
            <div className="fu-list">
              {list.map((l) => (
                <div className="fu-row" key={l.id}>
                  <Avatar user={l.owner} />
                  <button className="main" onClick={() => openLead(l.id)}>
                    <b>
                      {l.name}
                      {l.company && <span className="muted"> · {l.company}</span>}
                    </b>
                    <span className="muted">
                      <StageDot stage={l.stage} /> {STAGE_LABEL[l.stage]} · {inrShort(l.valueInr)}
                      {l.nextFollowUp && ` · ${formatDay(l.nextFollowUp)}`}
                      {l.phone && ` · ${l.phone}`}
                    </span>
                  </button>
                  {canWrite && (
                    <div className="acts">
                      <button className="btn sm" onClick={() => snooze(l, 1)}>
                        +1 day
                      </button>
                      <button className="btn sm" onClick={() => snooze(l, 3)}>
                        +3 days
                      </button>
                      <button className="btn sm" onClick={() => snooze(l, 7)}>
                        +1 week
                      </button>
                      <button className="btn sm primary" onClick={() => openLead(l.id)}>
                        Log activity
                      </button>
                    </div>
                  )}
                </div>
              ))}
            </div>
          </section>
        ))}
    </div>
  );
}
