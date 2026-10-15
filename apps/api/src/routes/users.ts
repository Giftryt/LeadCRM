import bcrypt from "bcryptjs";
import { asc, eq } from "drizzle-orm";
import { Router } from "express";
import { z } from "zod";
import { db } from "../db/index.js";
import { ROLES, users } from "../db/schema.js";
import { badRequest, conflict, notFound, route } from "../lib/http.js";
import { requireAuth, requireRole } from "../middleware/auth.js";

export const passwordSchema = z
  .string()
  .min(10, "must be at least 10 characters")
  .max(200)
  .refine((p) => /[A-Za-z]/.test(p) && /\d/.test(p), "must include at least one letter and one number");

const publicUser = {
  id: users.id,
  email: users.email,
  name: users.name,
  role: users.role,
  active: users.active,
  createdAt: users.createdAt,
};

export const usersRouter = Router();
usersRouter.use(requireAuth);

/** Everyone signed in can list active users (needed for owner pickers). Admins can include inactive ones. */
usersRouter.get(
  "/",
  route(async (req, res) => {
    const includeInactive = req.query.includeInactive === "true" && req.user!.role === "ADMIN";
    const items = await db
      .select(publicUser)
      .from(users)
      .where(includeInactive ? undefined : eq(users.active, true))
      .orderBy(asc(users.name));
    res.json({ items });
  }),
);

const createSchema = z.object({
  name: z.string().trim().min(1).max(120),
  email: z.string().trim().toLowerCase().email(),
  role: z.enum(ROLES).default("REP"),
  password: passwordSchema,
});

usersRouter.post(
  "/",
  requireRole("ADMIN"),
  route(async (req, res) => {
    const body = createSchema.parse(req.body);
    const [exists] = await db
      .select({ id: users.id })
      .from(users)
      .where(eq(users.email, body.email))
      .limit(1);
    if (exists) throw conflict("A user with that email already exists.");
    const [user] = await db
      .insert(users)
      .values({
        name: body.name,
        email: body.email,
        role: body.role,
        passwordHash: await bcrypt.hash(body.password, 12),
      })
      .returning(publicUser);
    res.status(201).json(user);
  }),
);

const updateSchema = z
  .object({
    name: z.string().trim().min(1).max(120),
    role: z.enum(ROLES),
    active: z.boolean(),
    password: passwordSchema,
  })
  .partial();

usersRouter.patch(
  "/:id",
  requireRole("ADMIN"),
  route(async (req, res) => {
    const body = updateSchema.parse(req.body);
    const id = req.params.id;
    if (id === req.user!.id && (body.active === false || (body.role && body.role !== "ADMIN"))) {
      throw badRequest("You can't deactivate yourself or remove your own admin role.");
    }
    const { password, ...rest } = body;
    const set = { ...rest, ...(password ? { passwordHash: await bcrypt.hash(password, 12) } : {}) };
    if (!Object.keys(set).length) throw badRequest("Nothing to update.");
    const [user] = await db.update(users).set(set).where(eq(users.id, id)).returning(publicUser);
    if (!user) throw notFound("User");
    res.json(user);
  }),
);
