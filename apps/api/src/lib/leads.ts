import { eq } from "drizzle-orm";
import { alias } from "drizzle-orm/pg-core";
import type { Db, Tx } from "../db/index.js";
import {
  activities,
  leads,
  SOURCES,
  STAGES,
  users,
  type Activity,
  type ActivityType,
  type Lead,
  type LeadSource,
  type Stage,
} from "../db/schema.js";

/** Probability used for the weighted forecast. Keep in sync with apps/web/src/lib/constants.ts. */
export const STAGE_PROBABILITY: Record<Stage, number> = {
  NEW: 0.1,
  CONTACTED: 0.2,
  QUALIFIED: 0.4,
  PROPOSAL: 0.6,
  NEGOTIATION: 0.8,
  WON: 1,
  LOST: 0,
};

export const STAGE_LABEL: Record<Stage, string> = {
  NEW: "New",
  CONTACTED: "Contacted",
  QUALIFIED: "Qualified",
  PROPOSAL: "Proposal sent",
  NEGOTIATION: "Negotiation",
  WON: "Won",
  LOST: "Lost",
};

export const SOURCE_LABEL: Record<LeadSource, string> = {
  WEBSITE: "Website",
  REFERRAL: "Referral",
  LINKEDIN: "LinkedIn",
  COLD_OUTREACH: "Cold outreach",
  EVENT: "Event",
  PARTNER: "Partner",
  INBOUND_CALL: "Inbound call",
  OTHER: "Other",
};

export const OPEN_STAGES: Stage[] = ["NEW", "CONTACTED", "QUALIFIED", "PROPOSAL", "NEGOTIATION"];
export const LOGGABLE_ACTIVITY: ActivityType[] = ["CALL", "EMAIL", "MEETING", "NOTE"];

export type UserRef = { id: string; name: string; email: string } | null;

/* ---------- queries that return a lead with its owner and creator ---------- */

const owner = alias(users, "owner");
const creator = alias(users, "creator");

export function selectLeads(db: Db | Tx) {
  return db
    .select({
      lead: leads,
      owner: { id: owner.id, name: owner.name, email: owner.email },
      createdBy: { id: creator.id, name: creator.name, email: creator.email },
    })
    .from(leads)
    .leftJoin(owner, eq(leads.ownerId, owner.id))
    .leftJoin(creator, eq(leads.createdById, creator.id))
    .$dynamic();
}

export async function findLead(db: Db | Tx, id: string) {
  const [row] = await selectLeads(db).where(eq(leads.id, id)).limit(1);
  return row ?? null;
}

export function selectActivities(db: Db | Tx) {
  return db
    .select({ activity: activities, user: { id: users.id, name: users.name, email: users.email } })
    .from(activities)
    .leftJoin(users, eq(activities.userId, users.id))
    .$dynamic();
}

/* ---------- serializers (API response shapes) ---------- */

export function serializeLead(row: { lead: Lead; owner: UserRef; createdBy: UserRef }) {
  const l = row.lead;
  return {
    id: l.id,
    name: l.name,
    company: l.company,
    email: l.email,
    phone: l.phone,
    source: l.source,
    stage: l.stage,
    valueInr: Number(l.valueInr),
    nextFollowUp: l.nextFollowUp,
    notes: l.notes,
    lostReason: l.lostReason,
    owner: row.owner,
    createdBy: row.createdBy,
    stageChangedAt: l.stageChangedAt.toISOString(),
    wonAt: l.wonAt?.toISOString() ?? null,
    lastContactAt: l.lastContactAt?.toISOString() ?? null,
    createdAt: l.createdAt.toISOString(),
    updatedAt: l.updatedAt.toISOString(),
  };
}
export type LeadDto = ReturnType<typeof serializeLead>;

export function serializeActivity(row: { activity: Activity; user: UserRef }) {
  const a = row.activity;
  return {
    id: a.id,
    leadId: a.leadId,
    type: a.type,
    body: a.body,
    user: row.user,
    createdAt: a.createdAt.toISOString(),
  };
}

/* ---------- CSV matching ---------- */

/** Matches a free-text stage ("Proposal sent", "proposal", "WON") to a Stage. */
export function matchStage(input: string): Stage | null {
  const s = input.trim().toLowerCase();
  if (!s) return null;
  for (const st of STAGES) {
    const label = STAGE_LABEL[st].toLowerCase();
    if (st.toLowerCase() === s || label === s || label.startsWith(s)) return st;
  }
  return null;
}

export function matchSource(input: string): LeadSource | null {
  const s = input.trim().toLowerCase().replace(/[_-]/g, " ");
  if (!s) return null;
  for (const src of SOURCES) {
    if (SOURCE_LABEL[src].toLowerCase() === s || src.toLowerCase().replace(/_/g, " ") === s) return src;
  }
  return "OTHER";
}
