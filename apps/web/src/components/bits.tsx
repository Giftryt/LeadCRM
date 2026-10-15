import type { Lead, UserRef } from "../api/client";
import { isOpenStage, STAGE_LABEL, type Stage } from "../lib/constants";
import { dueState, formatDay, initials } from "../lib/format";

export function StageDot({ stage }: { stage: Stage }) {
  return (
    <span
      className="dot"
      style={{ ["--c" as string]: `var(--st-${stage.toLowerCase()})` }}
      aria-hidden="true"
    />
  );
}

export function StageChip({ stage }: { stage: Stage }) {
  return (
    <span className="chip">
      <StageDot stage={stage} />
      {STAGE_LABEL[stage]}
    </span>
  );
}

export function Avatar({ user }: { user: UserRef }) {
  if (!user)
    return (
      <span className="av none" title="Unassigned">
        –
      </span>
    );
  return (
    <span className="av" title={user.name}>
      {initials(user.name)}
    </span>
  );
}

export function FollowUpPill({ lead, showLater = true }: { lead: Lead; showLater?: boolean }) {
  const d = dueState(lead.nextFollowUp, isOpenStage(lead.stage));
  if (!d || (d === "later" && !showLater)) return null;
  if (d === "today") return <span className="pill today">Today</span>;
  if (d === "overdue") return <span className="pill overdue">Overdue · {formatDay(lead.nextFollowUp)}</span>;
  return <span className="pill">{formatDay(lead.nextFollowUp)}</span>;
}
