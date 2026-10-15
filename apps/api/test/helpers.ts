import bcrypt from "bcryptjs";
import request from "supertest";
import { createApp } from "../src/app.js";
import { db, pool } from "../src/db/index.js";
import { users, type Role } from "../src/db/schema.js";

export const app = createApp();
export const PASSWORD = "Password123";

export async function resetDb() {
  await pool.query("TRUNCATE TABLE activities, leads, users CASCADE");
}

let counter = 0;
/** Creates a user and returns a supertest agent signed in as them. */
export async function signedIn(role: Role = "REP", name?: string) {
  counter += 1;
  const email = `${role.toLowerCase()}${counter}@example.com`;
  const [user] = await db
    .insert(users)
    .values({ email, name: name ?? `${role} ${counter}`, role, passwordHash: await bcrypt.hash(PASSWORD, 4) })
    .returning();
  const agent = request.agent(app);
  const res = await agent.post("/api/auth/login").send({ email, password: PASSWORD });
  if (res.status !== 200) throw new Error(`login failed: ${res.status} ${JSON.stringify(res.body)}`);
  return { agent, user };
}

export { db, pool, request };
