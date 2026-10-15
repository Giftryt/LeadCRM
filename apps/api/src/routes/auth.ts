import bcrypt from "bcryptjs";
import { Router } from "express";
import rateLimit from "express-rate-limit";
import { z } from "zod";
import { config } from "../config.js";
import { badRequest, route, unauthorized } from "../lib/http.js";
import { clearSession, issueSession, requireAuth } from "../middleware/auth.js";
import { eq } from "drizzle-orm";
import { db } from "../db/index.js";
import { users } from "../db/schema.js";
import { passwordSchema } from "./users.js";

export const authRouter = Router();

const loginLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  limit: config.NODE_ENV === "test" ? 1000 : 20,
  standardHeaders: "draft-7",
  legacyHeaders: false,
  message: {
    error: { code: "rate_limited", message: "Too many sign-in attempts. Wait 15 minutes and try again." },
  },
});

const loginSchema = z.object({
  email: z.string().trim().toLowerCase().email(),
  password: z.string().min(1),
});

authRouter.post(
  "/login",
  loginLimiter,
  route(async (req, res) => {
    const { email, password } = loginSchema.parse(req.body);
    const [user] = await db.select().from(users).where(eq(users.email, email)).limit(1);
    // Same message for unknown email and wrong password, so accounts can't be enumerated.
    const ok = user ? await bcrypt.compare(password, user.passwordHash) : false;
    if (!user || !ok) throw unauthorized("Email or password is incorrect.");
    if (!user.active) throw unauthorized("Your account is not active. Contact an admin.");
    const token = issueSession(res, user.id);
    res.json({ user: { id: user.id, email: user.email, name: user.name, role: user.role }, token });
  }),
);

authRouter.post("/logout", (_req, res) => {
  clearSession(res);
  res.status(204).end();
});

authRouter.get("/me", requireAuth, (req, res) => {
  res.json({ user: req.user });
});

const changePasswordSchema = z.object({
  currentPassword: z.string().min(1),
  newPassword: passwordSchema,
});

authRouter.post(
  "/change-password",
  requireAuth,
  route(async (req, res) => {
    const { currentPassword, newPassword } = changePasswordSchema.parse(req.body);
    const [user] = await db.select().from(users).where(eq(users.id, req.user!.id)).limit(1);
    if (!(await bcrypt.compare(currentPassword, user.passwordHash))) {
      throw badRequest("Current password is incorrect.");
    }
    await db
      .update(users)
      .set({ passwordHash: await bcrypt.hash(newPassword, 12) })
      .where(eq(users.id, user.id));
    res.status(204).end();
  }),
);
