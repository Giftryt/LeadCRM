/**
 * Database schema (Drizzle ORM, PostgreSQL).
 * After changing this file run `npm run db:generate` to create a SQL migration in apps/api/drizzle,
 * review it, commit it, then `npm run db:migrate` to apply it.
 */
import { boolean, date, index, numeric, pgEnum, pgTable, text, timestamp, uuid } from "drizzle-orm/pg-core";

export const ROLES = ["ADMIN", "REP", "VIEWER"] as const;
export const STAGES = ["NEW", "CONTACTED", "QUALIFIED", "PROPOSAL", "NEGOTIATION", "WON", "LOST"] as const;
export const SOURCES = [
  "WEBSITE",
  "REFERRAL",
  "LINKEDIN",
  "COLD_OUTREACH",
  "EVENT",
  "PARTNER",
  "INBOUND_CALL",
  "OTHER",
] as const;
export const ACTIVITY_TYPES = ["CALL", "EMAIL", "MEETING", "NOTE", "STAGE_CHANGE", "CREATED"] as const;

export type Role = (typeof ROLES)[number];
export type Stage = (typeof STAGES)[number];
export type LeadSource = (typeof SOURCES)[number];
export type ActivityType = (typeof ACTIVITY_TYPES)[number];

export const roleEnum = pgEnum("role", ROLES);
export const stageEnum = pgEnum("stage", STAGES); // declaration order = pipeline order, used for sorting
export const sourceEnum = pgEnum("lead_source", SOURCES);
export const activityTypeEnum = pgEnum("activity_type", ACTIVITY_TYPES);

const ts = (name: string) => timestamp(name, { withTimezone: true, mode: "date" });

export const users = pgTable("users", {
  id: uuid("id").primaryKey().defaultRandom(),
  email: text("email").notNull().unique(),
  name: text("name").notNull(),
  passwordHash: text("password_hash").notNull(),
  role: roleEnum("role").notNull().default("REP"),
  active: boolean("active").notNull().default(true),
  createdAt: ts("created_at").notNull().defaultNow(),
  updatedAt: ts("updated_at")
    .notNull()
    .defaultNow()
    .$onUpdate(() => new Date()),
});

export const leads = pgTable(
  "leads",
  {
    id: uuid("id").primaryKey().defaultRandom(),
    name: text("name").notNull(),
    company: text("company"),
    email: text("email"),
    phone: text("phone"),
    source: sourceEnum("source").notNull().default("WEBSITE"),
    stage: stageEnum("stage").notNull().default("NEW"),
    /** Deal value in Indian rupees. Returned by pg as a string; convert with Number(). */
    valueInr: numeric("value_inr", { precision: 14, scale: 2 }).notNull().default("0"),
    /** Calendar date of the next planned follow-up, "YYYY-MM-DD". */
    nextFollowUp: date("next_follow_up", { mode: "string" }),
    notes: text("notes"),
    lostReason: text("lost_reason"),
    ownerId: uuid("owner_id").references(() => users.id, { onDelete: "set null" }),
    createdById: uuid("created_by_id").references(() => users.id, { onDelete: "set null" }),
    stageChangedAt: ts("stage_changed_at").notNull().defaultNow(),
    wonAt: ts("won_at"),
    lastContactAt: ts("last_contact_at"),
    createdAt: ts("created_at").notNull().defaultNow(),
    updatedAt: ts("updated_at")
      .notNull()
      .defaultNow()
      .$onUpdate(() => new Date()),
  },
  (t) => [
    index("leads_stage_idx").on(t.stage),
    index("leads_owner_idx").on(t.ownerId),
    index("leads_next_follow_up_idx").on(t.nextFollowUp),
    index("leads_updated_at_idx").on(t.updatedAt),
  ],
);

export const activities = pgTable(
  "activities",
  {
    id: uuid("id").primaryKey().defaultRandom(),
    leadId: uuid("lead_id")
      .notNull()
      .references(() => leads.id, { onDelete: "cascade" }),
    type: activityTypeEnum("type").notNull(),
    body: text("body").notNull(),
    userId: uuid("user_id").references(() => users.id, { onDelete: "set null" }),
    createdAt: ts("created_at").notNull().defaultNow(),
  },
  (t) => [index("activities_lead_created_idx").on(t.leadId, t.createdAt)],
);

export type User = typeof users.$inferSelect;
export type Lead = typeof leads.$inferSelect;
export type NewLead = typeof leads.$inferInsert;
export type Activity = typeof activities.$inferSelect;
