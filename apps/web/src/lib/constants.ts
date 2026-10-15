// Keep in sync with the Java enums in apps/api/src/main/java/com/leadtracker/lead/Stage.java, LeadSource.java and activity/ActivityType.java.

export const STAGES = [
  { id: "NEW", label: "New", probability: 0.1 },
  { id: "CONTACTED", label: "Contacted", probability: 0.2 },
  { id: "QUALIFIED", label: "Qualified", probability: 0.4 },
  { id: "PROPOSAL", label: "Proposal sent", probability: 0.6 },
  { id: "NEGOTIATION", label: "Negotiation", probability: 0.8 },
  { id: "WON", label: "Won", probability: 1 },
  { id: "LOST", label: "Lost", probability: 0 },
] as const;
export type Stage = (typeof STAGES)[number]["id"];
export const STAGE_LABEL = Object.fromEntries(STAGES.map((s) => [s.id, s.label])) as Record<Stage, string>;
export const OPEN_STAGES: Stage[] = ["NEW", "CONTACTED", "QUALIFIED", "PROPOSAL", "NEGOTIATION"];
export const isOpenStage = (s: Stage) => OPEN_STAGES.includes(s);

export const SOURCES = [
  { id: "WEBSITE", label: "Website" },
  { id: "REFERRAL", label: "Referral" },
  { id: "LINKEDIN", label: "LinkedIn" },
  { id: "COLD_OUTREACH", label: "Cold outreach" },
  { id: "EVENT", label: "Event" },
  { id: "PARTNER", label: "Partner" },
  { id: "INBOUND_CALL", label: "Inbound call" },
  { id: "OTHER", label: "Other" },
] as const;
export type LeadSource = (typeof SOURCES)[number]["id"];
export const SOURCE_LABEL = Object.fromEntries(SOURCES.map((s) => [s.id, s.label])) as Record<
  LeadSource,
  string
>;

export const LOGGABLE_ACTIVITIES = [
  { id: "CALL", label: "Call" },
  { id: "EMAIL", label: "Email" },
  { id: "MEETING", label: "Meeting" },
  { id: "NOTE", label: "Note" },
] as const;
export type ActivityType = "CALL" | "EMAIL" | "MEETING" | "NOTE" | "STAGE_CHANGE" | "CREATED";
export const ACTIVITY_META: Record<ActivityType, { label: string; icon: string }> = {
  CALL: { label: "Call", icon: "C" },
  EMAIL: { label: "Email", icon: "E" },
  MEETING: { label: "Meeting", icon: "M" },
  NOTE: { label: "Note", icon: "N" },
  STAGE_CHANGE: { label: "Stage change", icon: "→" },
  CREATED: { label: "Created", icon: "+" },
};

export const ROLES = [
  { id: "ADMIN", label: "Admin", help: "Everything, including managing users" },
  { id: "REP", label: "Sales rep", help: "Add and work leads" },
  { id: "VIEWER", label: "Viewer", help: "Read-only" },
] as const;
export type Role = (typeof ROLES)[number]["id"];
