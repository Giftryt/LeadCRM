import { parse } from "csv-parse/sync";
import { stringify } from "csv-stringify/sync";
import {
  and,
  asc,
  count,
  desc,
  eq,
  gte,
  ilike,
  inArray,
  isNull,
  lt,
  lte,
  notInArray,
  or,
  sql,
  type SQL,
} from "drizzle-orm";
import { Router } from "express";
import multer from "multer";
import { z } from "zod";
import { config } from "../config.js";
import { db, type Tx } from "../db/index.js";
import { activities, leads, SOURCES, STAGES, users, type NewLead, type Stage } from "../db/schema.js";
import { addDays, isYmd, parseFlexibleDate, todayInTz } from "../lib/dates.js";
import { badRequest, forbidden, notFound, route } from "../lib/http.js";
import {
  findLead,
  LOGGABLE_ACTIVITY,
  matchSource,
  matchStage,
  OPEN_STAGES,
  selectActivities,
  selectLeads,
  serializeActivity,
  serializeLead,
  SOURCE_LABEL,
  STAGE_LABEL,
} from "../lib/leads.js";
import { requireAuth, requireWriter, type AuthUser } from "../middleware/auth.js";

export const leadsRouter = Router();
leadsRouter.use(requireAuth);

/* ---------------- validation ---------------- */

const optText = (max: number) =>
  z
    .string()
    .trim()
    .max(max)
    .nullish()
    .transform((v) => (v ? v : null));

const ymd = z
  .string()
  .refine(isYmd, "must be a date in YYYY-MM-DD format")
  .nullish()
  .transform((v) => (v ? v : null));

const leadFields = {
  name: z.string().trim().min(1, "is required").max(200),
  company: optText(200),
  email: z
    .string()
    .trim()
    .max(254)
    .nullish()
    .transform((v) => (v ? v.toLowerCase() : null))
    .refine((v) => v === null || z.string().email().safeParse(v).success, "is not a valid email address"),
  phone: optText(40),
  source: z.enum(SOURCES),
  stage: z.enum(STAGES),
  valueInr: z.coerce.number().min(0).max(1e12),
  nextFollowUp: ymd,
  notes: optText(10_000),
  lostReason: optText(500),
  ownerId: z
    .string()
    .uuid()
    .nullish()
    .transform((v) => (v ? v : null)),
};

const createSchema = z.object({
  ...leadFields,
  source: leadFields.source.default("WEBSITE"),
  stage: leadFields.stage.default("NEW"),
  valueInr: leadFields.valueInr.default(0),
});
const updateSchema = z.object(leadFields).partial();

const SORTS = {
  name: leads.name,
  company: leads.company,
  valueInr: leads.valueInr,
  nextFollowUp: leads.nextFollowUp,
  updatedAt: leads.updatedAt,
  createdAt: leads.createdAt,
  stage: leads.stage,
} as const;

const listSchema = z.object({
  q: z.string().trim().max(200).optional(),
  stage: z
    .string()
    .optional()
    .transform((v) => (v ? v.split(",").map((s) => s.trim().toUpperCase()) : undefined))
    .pipe(z.array(z.enum(STAGES)).optional()),
  source: z.enum(SOURCES).optional(),
  owner: z.union([z.literal("me"), z.literal("none"), z.string().uuid()]).optional(),
  followUp: z.enum(["overdue", "today", "week", "none"]).optional(),
  open: z.enum(["true", "false"]).optional(),
  sort: z.enum(Object.keys(SORTS) as [keyof typeof SORTS, ...(keyof typeof SORTS)[]]).default("updatedAt"),
  order: z.enum(["asc", "desc"]).default("desc"),
  page: z.coerce.number().int().min(1).default(1),
  pageSize: z.coerce.number().int().min(1).max(1000).default(100),
});
type ListQuery = z.infer<typeof listSchema>;

/* ---------------- helpers ---------------- */

function buildWhere(f: ListQuery, user: AuthUser): SQL | undefined {
  const conds: (SQL | undefined)[] = [];
  if (f.q) {
    const like = `%${f.q.replace(/[\\%_]/g, (c) => "\\" + c)}%`;
    conds.push(
      or(
        ilike(leads.name, like),
        ilike(leads.company, like),
        ilike(leads.email, like),
        ilike(leads.phone, like),
        ilike(leads.notes, like),
      ),
    );
  }
  if (f.stage?.length) conds.push(inArray(leads.stage, f.stage));
  if (f.source) conds.push(eq(leads.source, f.source));
  if (f.owner === "me") conds.push(eq(leads.ownerId, user.id));
  else if (f.owner === "none") conds.push(isNull(leads.ownerId));
  else if (f.owner) conds.push(eq(leads.ownerId, f.owner));
  if (f.open === "true") conds.push(inArray(leads.stage, OPEN_STAGES));
  if (f.open === "false") conds.push(notInArray(leads.stage, OPEN_STAGES));
  if (f.followUp) {
    const today = todayInTz(config.APP_TIMEZONE);
    conds.push(inArray(leads.stage, OPEN_STAGES));
    if (f.followUp === "overdue") conds.push(lt(leads.nextFollowUp, today));
    if (f.followUp === "today") conds.push(eq(leads.nextFollowUp, today));
    if (f.followUp === "week")
      conds.push(gte(leads.nextFollowUp, today), lte(leads.nextFollowUp, addDays(today, 7)));
    if (f.followUp === "none") conds.push(isNull(leads.nextFollowUp));
  }
  return conds.length ? and(...conds) : undefined;
}

async function assertOwner(ownerId: string | null | undefined) {
  if (!ownerId) return;
  const [u] = await db.select({ active: users.active }).from(users).where(eq(users.id, ownerId)).limit(1);
  if (!u || !u.active) throw badRequest("ownerId: must be an active user");
}

function stageFields(stage: Stage, now: Date) {
  return { stage, stageChangedAt: now, ...(stage === "WON" ? { wonAt: now } : {}) };
}

async function getLeadOr404(id: string, tx: typeof db | Tx = db) {
  if (!z.string().uuid().safeParse(id).success) throw notFound("Lead");
  const row = await findLead(tx, id);
  if (!row) throw notFound("Lead");
  return row;
}

/* ---------------- routes ---------------- */

leadsRouter.get(
  "/",
  route(async (req, res) => {
    const f = listSchema.parse(req.query);
    const where = buildWhere(f, req.user!);
    const col = SORTS[f.sort];
    const dir = f.order === "asc" ? asc : desc;
    const nullable = f.sort === "nextFollowUp" || f.sort === "company";
    const orderBy = nullable ? sql`${col} ${sql.raw(f.order)} nulls last` : dir(col);
    const [rows, [{ total }]] = await Promise.all([
      selectLeads(db)
        .where(where)
        .orderBy(orderBy, asc(leads.id))
        .limit(f.pageSize)
        .offset((f.page - 1) * f.pageSize),
      db.select({ total: count() }).from(leads).where(where),
    ]);
    res.json({ items: rows.map(serializeLead), total, page: f.page, pageSize: f.pageSize });
  }),
);

leadsRouter.get(
  "/export.csv",
  route(async (req, res) => {
    const f = listSchema.parse(req.query);
    const rows = await selectLeads(db)
      .where(buildWhere(f, req.user!))
      .orderBy(desc(leads.updatedAt))
      .limit(50_000);
    const csv = stringify(
      rows.map(({ lead: l, owner }) => [
        l.name,
        l.company ?? "",
        l.email ?? "",
        l.phone ?? "",
        STAGE_LABEL[l.stage],
        Number(l.valueInr),
        owner?.name ?? "",
        SOURCE_LABEL[l.source],
        l.nextFollowUp ?? "",
        l.notes ?? "",
        l.lostReason ?? "",
        l.createdAt.toISOString().slice(0, 10),
      ]),
      {
        header: true,
        columns: [
          "Name",
          "Company",
          "Email",
          "Phone",
          "Stage",
          "Value (INR)",
          "Owner",
          "Source",
          "Next follow-up",
          "Notes",
          "Reason lost",
          "Created",
        ],
      },
    );
    res.setHeader("Content-Type", "text/csv; charset=utf-8");
    res.setHeader(
      "Content-Disposition",
      `attachment; filename="leads-${todayInTz(config.APP_TIMEZONE)}.csv"`,
    );
    res.send("﻿" + csv); // BOM so Excel opens UTF-8 (₹, names) correctly
  }),
);

leadsRouter.post(
  "/",
  requireWriter,
  route(async (req, res) => {
    const body = createSchema.parse(req.body);
    await assertOwner(body.ownerId);
    const now = new Date();
    const row = await db.transaction(async (tx) => {
      const [created] = await tx
        .insert(leads)
        .values({
          ...body,
          valueInr: String(body.valueInr),
          createdById: req.user!.id,
          ...stageFields(body.stage, now),
        })
        .returning({ id: leads.id });
      await tx.insert(activities).values({
        leadId: created.id,
        type: "CREATED",
        body: `Lead added in ${STAGE_LABEL[body.stage]}`,
        userId: req.user!.id,
      });
      return (await findLead(tx, created.id))!;
    });
    res.status(201).json(serializeLead(row));
  }),
);

leadsRouter.get(
  "/:id",
  route(async (req, res) => {
    res.json(serializeLead(await getLeadOr404(req.params.id)));
  }),
);

leadsRouter.patch(
  "/:id",
  requireWriter,
  route(async (req, res) => {
    const body = updateSchema.parse(req.body);
    const { lead: existing } = await getLeadOr404(req.params.id);
    if (body.ownerId !== undefined) await assertOwner(body.ownerId);
    const now = new Date();
    const stageChanged = body.stage !== undefined && body.stage !== existing.stage;
    const { stage, valueInr, ...rest } = body;
    const set: Partial<NewLead> = {
      ...rest,
      ...(valueInr !== undefined ? { valueInr: String(valueInr) } : {}),
      ...(stageChanged ? stageFields(stage!, now) : {}),
      updatedAt: now,
    };
    const row = await db.transaction(async (tx) => {
      await tx.update(leads).set(set).where(eq(leads.id, existing.id));
      if (stageChanged) {
        await tx.insert(activities).values({
          leadId: existing.id,
          type: "STAGE_CHANGE",
          body: `Moved from ${STAGE_LABEL[existing.stage]} to ${STAGE_LABEL[stage!]}`,
          userId: req.user!.id,
        });
      }
      return (await findLead(tx, existing.id))!;
    });
    res.json(serializeLead(row));
  }),
);

leadsRouter.delete(
  "/:id",
  requireWriter,
  route(async (req, res) => {
    const { lead } = await getLeadOr404(req.params.id);
    const u = req.user!;
    if (u.role !== "ADMIN" && lead.ownerId !== u.id && lead.createdById !== u.id) {
      throw forbidden("Only an admin, the lead's owner or the person who added it can delete it.");
    }
    await db.delete(leads).where(eq(leads.id, lead.id)); // activities cascade
    res.status(204).end();
  }),
);

/* ---------------- activities ---------------- */

leadsRouter.get(
  "/:id/activities",
  route(async (req, res) => {
    await getLeadOr404(req.params.id);
    const rows = await selectActivities(db)
      .where(eq(activities.leadId, req.params.id))
      .orderBy(desc(activities.createdAt), desc(activities.id))
      .limit(500);
    res.json({ items: rows.map(serializeActivity) });
  }),
);

const activitySchema = z.object({
  type: z.enum(["CALL", "EMAIL", "MEETING", "NOTE"], {
    errorMap: () => ({ message: `must be one of ${LOGGABLE_ACTIVITY.join(", ")}` }),
  }),
  body: z.string().trim().min(1, "is required").max(10_000),
  /** Optionally set (or clear with null) the lead's next follow-up in the same step. */
  nextFollowUp: z.string().refine(isYmd, "must be a date in YYYY-MM-DD format").nullable().optional(),
});

leadsRouter.post(
  "/:id/activities",
  requireWriter,
  route(async (req, res) => {
    const body = activitySchema.parse(req.body);
    const { lead } = await getLeadOr404(req.params.id);
    const now = new Date();
    const result = await db.transaction(async (tx) => {
      const [created] = await tx
        .insert(activities)
        .values({ leadId: lead.id, type: body.type, body: body.body, userId: req.user!.id })
        .returning({ id: activities.id });
      await tx
        .update(leads)
        .set({
          updatedAt: now,
          ...(body.type !== "NOTE" ? { lastContactAt: now } : {}),
          ...(body.nextFollowUp !== undefined ? { nextFollowUp: body.nextFollowUp } : {}),
        })
        .where(eq(leads.id, lead.id));
      const [activity] = await selectActivities(tx).where(eq(activities.id, created.id));
      return { activity: serializeActivity(activity), lead: serializeLead((await findLead(tx, lead.id))!) };
    });
    res.status(201).json(result);
  }),
);

/* ---------------- CSV import ---------------- */

const upload = multer({ storage: multer.memoryStorage(), limits: { fileSize: 5 * 1024 * 1024, files: 1 } });

const HEADERS: Record<string, string[]> = {
  name: ["name", "contact", "contact name", "lead", "full name"],
  company: ["company", "organisation", "organization", "account", "company name"],
  email: ["email", "e-mail", "email address"],
  phone: ["phone", "mobile", "phone number", "contact number", "mobile number"],
  source: ["source", "lead source"],
  stage: ["stage", "status"],
  valueInr: ["value", "value (inr)", "deal value", "amount", "deal size"],
  nextFollowUp: ["next follow-up", "next follow up", "follow-up", "follow up", "followup", "next followup"],
  notes: ["notes", "note", "comments"],
  owner: ["owner", "owner email", "assigned to"],
};
const MAX_IMPORT_ROWS = 5000;

leadsRouter.post(
  "/import",
  requireWriter,
  upload.single("file"),
  route(async (req, res) => {
    if (!req.file) throw badRequest("Attach a CSV file in the 'file' field.");
    let rows: string[][];
    try {
      rows = parse(req.file.buffer, {
        bom: true,
        skip_empty_lines: true,
        relax_column_count: true,
        trim: true,
      });
    } catch (e) {
      throw badRequest(`The file couldn't be read as CSV: ${(e as Error).message}`);
    }
    if (rows.length < 2) throw badRequest("The file needs a header row and at least one lead.");
    if (rows.length - 1 > MAX_IMPORT_ROWS)
      throw badRequest(`Import at most ${MAX_IMPORT_ROWS} leads at a time.`);

    const header = rows[0].map((h) => h.trim().toLowerCase());
    const col: Record<string, number> = {};
    for (const [key, names] of Object.entries(HEADERS)) {
      const i = header.findIndex((h) => names.includes(h));
      if (i >= 0) col[key] = i;
    }
    if (col.name === undefined && col.company === undefined) {
      throw badRequest("Couldn't find a Name or Company column. Rename a header to Name and try again.");
    }

    // Owners can be referenced by email or name.
    const team = await db
      .select({ id: users.id, email: users.email, name: users.name })
      .from(users)
      .where(eq(users.active, true));
    const findOwner = (v: string) => {
      const s = v.trim().toLowerCase();
      return team.find((u) => u.email === s || u.name.toLowerCase() === s)?.id ?? null;
    };

    const get = (r: string[], k: string) => (col[k] === undefined ? "" : (r[col[k]] ?? "").trim());
    const errors: { row: number; message: string }[] = [];
    const toCreate: NewLead[] = [];
    const now = new Date();

    rows.slice(1).forEach((r, idx) => {
      const rowNo = idx + 2; // 1-based, counting the header
      const name = get(r, "name") || get(r, "company");
      if (!name) return errors.push({ row: rowNo, message: "Missing name and company" });
      const email = get(r, "email").toLowerCase();
      if (email && !z.string().email().safeParse(email).success) {
        return errors.push({ row: rowNo, message: `Invalid email "${email}"` });
      }
      const rawDate = get(r, "nextFollowUp");
      const follow = rawDate ? parseFlexibleDate(rawDate) : null;
      if (rawDate && !follow) return errors.push({ row: rowNo, message: `Unrecognised date "${rawDate}"` });
      const value = Number(get(r, "valueInr").replace(/[^\d.]/g, "")) || 0;
      const stage = matchStage(get(r, "stage")) ?? "NEW";
      const ownerRaw = get(r, "owner");
      toCreate.push({
        name: name.slice(0, 200),
        company: get(r, "company") || null,
        email: email || null,
        phone: get(r, "phone") || null,
        source: matchSource(get(r, "source")) ?? "WEBSITE",
        valueInr: String(value),
        nextFollowUp: follow,
        notes: get(r, "notes") || null,
        ownerId: ownerRaw ? findOwner(ownerRaw) : req.user!.id,
        createdById: req.user!.id,
        ...stageFields(stage, now),
      });
    });

    const imported = await db.transaction(async (tx) => {
      let n = 0;
      for (let i = 0; i < toCreate.length; i += 500) {
        const ids = await tx
          .insert(leads)
          .values(toCreate.slice(i, i + 500))
          .returning({ id: leads.id });
        await tx
          .insert(activities)
          .values(
            ids.map(({ id }) => ({
              leadId: id,
              type: "CREATED" as const,
              body: "Imported from CSV",
              userId: req.user!.id,
            })),
          );
        n += ids.length;
      }
      return n;
    });

    res.status(201).json({ imported, skipped: errors.length, errors: errors.slice(0, 100) });
  }),
);
