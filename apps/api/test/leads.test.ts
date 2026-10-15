import { afterAll, beforeEach, describe, expect, it } from "vitest";
import { addDays, todayInTz } from "../src/lib/dates.js";
import { count, eq } from "drizzle-orm";
import { activities, users } from "../src/db/schema.js";
import { db, pool, resetDb, signedIn } from "./helpers.js";

beforeEach(resetDb);
afterAll(() => pool.end());

const today = () => todayInTz("Asia/Kolkata");

describe("leads", () => {
  it("creates a lead with defaults and a 'created' activity", async () => {
    const { agent, user } = await signedIn("REP");
    const res = await agent
      .post("/api/leads")
      .send({ name: "Ananya Rao", company: "Brightleaf", valueInr: 450000, ownerId: user.id });
    expect(res.status).toBe(201);
    expect(res.body).toMatchObject({ name: "Ananya Rao", stage: "NEW", source: "WEBSITE", valueInr: 450000 });
    expect(res.body.owner.id).toBe(user.id);
    const acts = await agent.get(`/api/leads/${res.body.id}/activities`);
    expect(acts.body.items.map((a: { type: string }) => a.type)).toEqual(["CREATED"]);
  });

  it("validates input", async () => {
    const { agent } = await signedIn();
    const res = await agent
      .post("/api/leads")
      .send({ name: "", email: "not-an-email", nextFollowUp: "15/10/2026" });
    expect(res.status).toBe(400);
    const fields = res.body.error.details.map((d: { field: string }) => d.field);
    expect(fields).toEqual(expect.arrayContaining(["name", "email", "nextFollowUp"]));
  });

  it("logs a stage change and sets wonAt when won", async () => {
    const { agent } = await signedIn();
    const { body: lead } = await agent.post("/api/leads").send({ name: "Rohit", stage: "PROPOSAL" });
    const res = await agent.patch(`/api/leads/${lead.id}`).send({ stage: "WON" });
    expect(res.status).toBe(200);
    expect(res.body.stage).toBe("WON");
    expect(res.body.wonAt).not.toBeNull();
    const acts = await agent.get(`/api/leads/${lead.id}/activities`);
    expect(acts.body.items[0]).toMatchObject({
      type: "STAGE_CHANGE",
      body: "Moved from Proposal sent to Won",
    });
  });

  it("does not log a stage change when the stage is unchanged", async () => {
    const { agent } = await signedIn();
    const { body: lead } = await agent.post("/api/leads").send({ name: "Same" });
    await agent.patch(`/api/leads/${lead.id}`).send({ stage: "NEW", notes: "hi" });
    const acts = await agent.get(`/api/leads/${lead.id}/activities`);
    expect(acts.body.items).toHaveLength(1);
  });

  it("filters by search, owner and follow-up window", async () => {
    const { agent, user } = await signedIn();
    const t = today();
    await agent
      .post("/api/leads")
      .send({ name: "Overdue Co", nextFollowUp: addDays(t, -3), ownerId: user.id });
    await agent.post("/api/leads").send({ name: "Today Co", nextFollowUp: t });
    await agent.post("/api/leads").send({ name: "Week Co", nextFollowUp: addDays(t, 4) });
    await agent.post("/api/leads").send({ name: "Closed Co", nextFollowUp: addDays(t, -3), stage: "WON" });

    const names = async (qs: string) =>
      (await agent.get(`/api/leads?${qs}`)).body.items.map((l: { name: string }) => l.name).sort();
    expect(await names("followUp=overdue")).toEqual(["Overdue Co"]);
    expect(await names("followUp=today")).toEqual(["Today Co"]);
    expect(await names("followUp=week")).toEqual(["Today Co", "Week Co"]);
    expect(await names("owner=me")).toEqual(["Overdue Co"]);
    expect(await names("q=week")).toEqual(["Week Co"]);
    expect(await names("stage=won,lost")).toEqual(["Closed Co"]);
  });

  it("logs an activity and moves the follow-up in one step", async () => {
    const { agent } = await signedIn();
    const { body: lead } = await agent.post("/api/leads").send({ name: "Meera" });
    const next = addDays(today(), 3);
    const res = await agent
      .post(`/api/leads/${lead.id}/activities`)
      .send({ type: "CALL", body: "Spoke to CFO", nextFollowUp: next });
    expect(res.status).toBe(201);
    expect(res.body.lead.nextFollowUp).toBe(next);
    expect(res.body.lead.lastContactAt).not.toBeNull();
    const bad = await agent
      .post(`/api/leads/${lead.id}/activities`)
      .send({ type: "STAGE_CHANGE", body: "x" });
    expect(bad.status).toBe(400);
  });
});

describe("permissions", () => {
  it("lets viewers read but not write", async () => {
    const { agent: rep } = await signedIn("REP");
    const { agent: viewer } = await signedIn("VIEWER");
    const { body: lead } = await rep.post("/api/leads").send({ name: "Kabir" });
    expect((await viewer.get(`/api/leads/${lead.id}`)).status).toBe(200);
    expect((await viewer.post("/api/leads").send({ name: "X" })).status).toBe(403);
    expect((await viewer.patch(`/api/leads/${lead.id}`).send({ name: "Y" })).status).toBe(403);
    expect(
      (await viewer.post(`/api/leads/${lead.id}/activities`).send({ type: "NOTE", body: "x" })).status,
    ).toBe(403);
  });

  it("lets only admins, owners or creators delete a lead", async () => {
    const { agent: a } = await signedIn("REP");
    const { agent: b } = await signedIn("REP");
    const { agent: admin } = await signedIn("ADMIN");
    const { body: lead } = await a.post("/api/leads").send({ name: "Delete me" });
    expect((await b.delete(`/api/leads/${lead.id}`)).status).toBe(403);
    expect((await admin.delete(`/api/leads/${lead.id}`)).status).toBe(204);
    expect((await a.get(`/api/leads/${lead.id}`)).status).toBe(404);
    expect((await db.select({ n: count() }).from(activities))[0].n).toBe(0);
  });

  it("rejects assigning a lead to an inactive user", async () => {
    const { agent } = await signedIn();
    const { user: other } = await signedIn();
    await db.update(users).set({ active: false }).where(eq(users.id, other.id));
    const res = await agent.post("/api/leads").send({ name: "X", ownerId: other.id });
    expect(res.status).toBe(400);
  });
});

describe("CSV import and export", () => {
  it("imports rows, maps headers and reports bad rows", async () => {
    const { agent } = await signedIn("ADMIN", "Asha Admin");
    const csv = [
      "Name,Company,Email,Stage,Value,Next follow-up,Source,Owner",
      'Ravi Kumar,Acme,ravi@acme.in,Proposal sent,"₹2,50,000",20/11/2026,LinkedIn,Asha Admin',
      ",Only Company Pvt Ltd,,,,,,",
      "Bad Email,X,not-an-email,,,,,",
      "Bad Date,Y,,,,31/02/2026,,",
    ].join("\n");
    const res = await agent.post("/api/leads/import").attach("file", Buffer.from(csv), "leads.csv");
    expect(res.status).toBe(201);
    expect(res.body.imported).toBe(2);
    expect(res.body.skipped).toBe(2);
    expect(res.body.errors.map((e: { row: number }) => e.row)).toEqual([4, 5]);

    const { body } = await agent.get("/api/leads?sort=name&order=asc");
    const ravi = body.items.find((l: { name: string }) => l.name === "Ravi Kumar");
    expect(ravi).toMatchObject({
      stage: "PROPOSAL",
      valueInr: 250000,
      nextFollowUp: "2026-11-20",
      source: "LINKEDIN",
    });
    expect(ravi.owner.name).toBe("Asha Admin");
    expect(body.items.some((l: { name: string }) => l.name === "Only Company Pvt Ltd")).toBe(true);
  });

  it("exports CSV with a header row", async () => {
    const { agent } = await signedIn();
    await agent.post("/api/leads").send({ name: "Export, Me", valueInr: 1000 });
    const res = await agent.get("/api/leads/export.csv");
    expect(res.status).toBe(200);
    expect(res.headers["content-type"]).toContain("text/csv");
    const lines = res.text.replace(/^﻿/, "").trim().split("\n");
    expect(lines[0]).toContain("Name,Company,Email");
    expect(lines[1]).toContain('"Export, Me"');
  });
});

describe("dashboard", () => {
  it("summarises pipeline, forecast, wins and follow-ups", async () => {
    const { agent } = await signedIn();
    const t = today();
    await agent
      .post("/api/leads")
      .send({ name: "A", stage: "PROPOSAL", valueInr: 100000, nextFollowUp: addDays(t, -1) });
    await agent.post("/api/leads").send({ name: "B", stage: "NEW", valueInr: 50000, nextFollowUp: t });
    const { body: c } = await agent
      .post("/api/leads")
      .send({ name: "C", stage: "NEGOTIATION", valueInr: 200000 });
    await agent.patch(`/api/leads/${c.id}`).send({ stage: "WON" });
    await agent.post("/api/leads").send({ name: "D", stage: "LOST", valueInr: 10000 });

    const res = await agent.get("/api/dashboard/summary");
    expect(res.status).toBe(200);
    expect(res.body.openPipeline).toEqual({ count: 2, valueInr: 150000 });
    expect(res.body.weightedForecastInr).toBe(65000); // 1,00,000 × 0.6 + 50,000 × 0.1
    expect(res.body.wonThisMonth).toEqual({ count: 1, valueInr: 200000 });
    expect(res.body.winRate).toBe(0.5);
    expect(res.body.followUps).toEqual({ overdue: 1, today: 1 });
  });
});
