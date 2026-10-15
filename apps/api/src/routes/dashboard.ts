import { and, count, eq, gte, inArray, isNull, lt, sum, type SQL } from "drizzle-orm";
import { Router } from "express";
import { config } from "../config.js";
import { db } from "../db/index.js";
import { leads, STAGES } from "../db/schema.js";
import { startOfMonthInTz, todayInTz } from "../lib/dates.js";
import { route } from "../lib/http.js";
import { OPEN_STAGES, STAGE_PROBABILITY } from "../lib/leads.js";
import { requireAuth } from "../middleware/auth.js";

export const dashboardRouter = Router();
dashboardRouter.use(requireAuth);

/**
 * GET /api/dashboard/summary?owner=me|none
 * Headline numbers for the summary strip, plus per-stage totals for the board header.
 */
dashboardRouter.get(
  "/summary",
  route(async (req, res) => {
    const ownerCond: SQL | undefined =
      req.query.owner === "me"
        ? eq(leads.ownerId, req.user!.id)
        : req.query.owner === "none"
          ? isNull(leads.ownerId)
          : undefined;
    const tz = config.APP_TIMEZONE;
    const today = todayInTz(tz);
    const open = inArray(leads.stage, OPEN_STAGES);

    const [byStage, [won], [overdue], [dueToday]] = await Promise.all([
      db
        .select({ stage: leads.stage, n: count(), v: sum(leads.valueInr) })
        .from(leads)
        .where(ownerCond)
        .groupBy(leads.stage),
      db
        .select({ n: count(), v: sum(leads.valueInr) })
        .from(leads)
        .where(and(ownerCond, eq(leads.stage, "WON"), gte(leads.wonAt, startOfMonthInTz(tz)))),
      db
        .select({ n: count() })
        .from(leads)
        .where(and(ownerCond, open, lt(leads.nextFollowUp, today))),
      db
        .select({ n: count() })
        .from(leads)
        .where(and(ownerCond, open, eq(leads.nextFollowUp, today))),
    ]);

    const stages = STAGES.map((stage) => {
      const g = byStage.find((b) => b.stage === stage);
      return { stage, count: g?.n ?? 0, valueInr: Number(g?.v ?? 0) };
    });
    const openStages = stages.filter((s) => OPEN_STAGES.includes(s.stage));
    const wonAll = stages.find((s) => s.stage === "WON")!;
    const lostAll = stages.find((s) => s.stage === "LOST")!;
    const closed = wonAll.count + lostAll.count;

    res.json({
      openPipeline: {
        count: openStages.reduce((a, s) => a + s.count, 0),
        valueInr: openStages.reduce((a, s) => a + s.valueInr, 0),
      },
      weightedForecastInr: Math.round(
        openStages.reduce((a, s) => a + s.valueInr * STAGE_PROBABILITY[s.stage], 0),
      ),
      wonThisMonth: { count: won.n, valueInr: Number(won.v ?? 0) },
      winRate: closed ? wonAll.count / closed : null,
      followUps: { overdue: overdue.n, today: dueToday.n },
      stages,
    });
  }),
);
