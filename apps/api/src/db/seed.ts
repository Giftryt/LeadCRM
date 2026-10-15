/**
 * Creates the first admin account (only when there are no users) and, optionally, sample leads.
 *   npm run db:seed
 * Reads SEED_ADMIN_EMAIL / SEED_ADMIN_PASSWORD / SEED_ADMIN_NAME / SEED_SAMPLE_LEADS from the environment.
 */
import "dotenv/config";
import bcrypt from "bcryptjs";
import { count, eq } from "drizzle-orm";
import { addDays, todayInTz } from "../lib/dates.js";
import { db, pool } from "./index.js";
import { activities, leads, users, type LeadSource, type Stage } from "./schema.js";

async function main() {
  const email = (process.env.SEED_ADMIN_EMAIL ?? "").trim().toLowerCase();
  const password = process.env.SEED_ADMIN_PASSWORD ?? "";
  const name = process.env.SEED_ADMIN_NAME ?? "Admin";

  const [{ n: userCount }] = await db.select({ n: count() }).from(users);
  let [admin] = await db.select().from(users).where(eq(users.role, "ADMIN")).limit(1);
  if (userCount === 0) {
    if (!email || password.length < 10) {
      throw new Error(
        "Set SEED_ADMIN_EMAIL and SEED_ADMIN_PASSWORD (10+ characters) to create the first admin.",
      );
    }
    [admin] = await db
      .insert(users)
      .values({ email, name, role: "ADMIN", passwordHash: await bcrypt.hash(password, 12) })
      .returning();
    console.log(`Created admin ${email}. Sign in and change the password under Account.`);
  } else {
    console.log("Users already exist; skipping admin creation.");
  }

  if (process.env.SEED_SAMPLE_LEADS !== "true") return;
  const [{ n: leadCount }] = await db.select({ n: count() }).from(leads);
  if (leadCount > 0) {
    console.log("Leads already exist; skipping sample leads.");
    return;
  }
  const today = todayInTz(process.env.APP_TIMEZONE ?? "Asia/Kolkata");
  const samples: [string, string, LeadSource, Stage, number, number | null][] = [
    ["Ananya Rao", "Brightleaf Logistics", "REFERRAL", "QUALIFIED", 450000, -2],
    ["Rohit Mehra", "Kestrel Software", "LINKEDIN", "PROPOSAL", 1200000, 0],
    ["Fatima Sheikh", "Tanvi Hospitality Group", "EVENT", "NEGOTIATION", 2800000, 2],
    ["Arjun Nair", "Coastline Foods", "WEBSITE", "NEW", 180000, 1],
    ["Meera Iyer", "Northstar Clinics", "COLD_OUTREACH", "CONTACTED", 650000, -1],
    ["Vikram Desai", "Granite Infra", "PARTNER", "WON", 3500000, null],
    ["Sneha Kulkarni", "Pebble Retail", "INBOUND_CALL", "LOST", 220000, null],
    ["Kabir Malhotra", "Orbit Edtech", "WEBSITE", "QUALIFIED", 900000, 5],
  ];
  const now = new Date();
  const created = await db
    .insert(leads)
    .values(
      samples.map(([n, company, source, stage, value, follow]) => ({
        name: n,
        company,
        email: `${n.split(" ")[0].toLowerCase()}@example.com`,
        source,
        stage,
        valueInr: String(value),
        nextFollowUp: follow === null ? null : addDays(today, follow),
        notes: "Sample lead (development seed).",
        lostReason: stage === "LOST" ? "Went with an in-house option" : null,
        ownerId: admin?.id,
        createdById: admin?.id,
        wonAt: stage === "WON" ? now : null,
      })),
    )
    .returning({ id: leads.id });
  await db
    .insert(activities)
    .values(
      created.map(({ id }) => ({
        leadId: id,
        type: "CREATED" as const,
        body: "Sample lead from seed",
        userId: admin?.id,
      })),
    );
  console.log(`Created ${created.length} sample leads.`);
}

main()
  .catch((e) => {
    console.error(e instanceof Error ? e.message : e);
    process.exitCode = 1;
  })
  .finally(() => pool.end());
