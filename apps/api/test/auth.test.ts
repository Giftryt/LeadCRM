import { afterAll, beforeEach, describe, expect, it } from "vitest";
import { eq } from "drizzle-orm";
import { users } from "../src/db/schema.js";
import { app, db, PASSWORD, pool, request, resetDb, signedIn } from "./helpers.js";

beforeEach(resetDb);
afterAll(() => pool.end());

describe("auth", () => {
  it("signs in, reads /me, and signs out", async () => {
    const { agent, user } = await signedIn("REP", "Priya");
    const me = await agent.get("/api/auth/me");
    expect(me.status).toBe(200);
    expect(me.body.user).toMatchObject({ id: user.id, name: "Priya", role: "REP" });

    expect((await agent.post("/api/auth/logout")).status).toBe(204);
    expect((await agent.get("/api/auth/me")).status).toBe(401);
  });

  it("rejects a wrong password with a generic message", async () => {
    const { user } = await signedIn();
    const res = await request(app).post("/api/auth/login").send({ email: user.email, password: "nope" });
    expect(res.status).toBe(401);
    expect(res.body.error.message).toBe("Email or password is incorrect.");
  });

  it("blocks deactivated users immediately, even with a valid session", async () => {
    const { agent, user } = await signedIn();
    await db.update(users).set({ active: false }).where(eq(users.id, user.id));
    expect((await agent.get("/api/leads")).status).toBe(401);
  });

  it("requires a session for protected routes", async () => {
    expect((await request(app).get("/api/leads")).status).toBe(401);
  });

  it("changes password with the current password", async () => {
    const { agent, user } = await signedIn();
    const bad = await agent
      .post("/api/auth/change-password")
      .send({ currentPassword: "x", newPassword: "NewPassword99" });
    expect(bad.status).toBe(400);
    const ok = await agent
      .post("/api/auth/change-password")
      .send({ currentPassword: PASSWORD, newPassword: "NewPassword99" });
    expect(ok.status).toBe(204);
    const login = await request(app)
      .post("/api/auth/login")
      .send({ email: user.email, password: "NewPassword99" });
    expect(login.status).toBe(200);
  });
});

describe("users", () => {
  it("lets admins create users and blocks reps", async () => {
    const { agent: admin } = await signedIn("ADMIN");
    const { agent: rep } = await signedIn("REP");
    const body = { name: "Neha", email: "Neha@Example.com", role: "REP", password: "Welcome2026" };

    expect((await rep.post("/api/users").send(body)).status).toBe(403);
    const created = await admin.post("/api/users").send(body);
    expect(created.status).toBe(201);
    expect(created.body.email).toBe("neha@example.com");
    expect(created.body.passwordHash).toBeUndefined();
    expect((await admin.post("/api/users").send(body)).status).toBe(409);
  });

  it("rejects weak passwords", async () => {
    const { agent: admin } = await signedIn("ADMIN");
    const res = await admin.post("/api/users").send({ name: "A", email: "a@example.com", password: "short" });
    expect(res.status).toBe(400);
    expect(res.body.error.code).toBe("validation_error");
  });

  it("stops an admin from deactivating themselves", async () => {
    const { agent, user } = await signedIn("ADMIN");
    expect((await agent.patch(`/api/users/${user.id}`).send({ active: false })).status).toBe(400);
  });
});
