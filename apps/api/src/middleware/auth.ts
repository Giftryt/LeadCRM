import type { RequestHandler, Response } from "express";
import jwt from "jsonwebtoken";
import { config, cookieSecure, SESSION_COOKIE } from "../config.js";
import { forbidden, route, unauthorized } from "../lib/http.js";
import { eq } from "drizzle-orm";
import { db } from "../db/index.js";
import { users, type Role } from "../db/schema.js";

export type AuthUser = { id: string; email: string; name: string; role: Role };

declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace Express {
    interface Request {
      user?: AuthUser;
    }
  }
}

export function issueSession(res: Response, userId: string) {
  const token = jwt.sign({ sub: userId }, config.JWT_SECRET, {
    expiresIn: `${config.SESSION_TTL_HOURS}h`,
  });
  res.cookie(SESSION_COOKIE, token, {
    httpOnly: true,
    secure: cookieSecure,
    sameSite: "lax",
    maxAge: config.SESSION_TTL_HOURS * 3600 * 1000,
    path: "/",
  });
  return token;
}

export function clearSession(res: Response) {
  res.clearCookie(SESSION_COOKIE, { path: "/" });
}

function readToken(req: Parameters<RequestHandler>[0]): string | null {
  const cookie = req.cookies?.[SESSION_COOKIE];
  if (typeof cookie === "string" && cookie) return cookie;
  // Bearer tokens are accepted for scripts and integrations.
  const h = req.headers.authorization;
  if (h?.startsWith("Bearer ")) return h.slice(7);
  return null;
}

/** Requires a valid session for an active user. The user is re-read on each request so deactivation is immediate. */
export const requireAuth: RequestHandler = route(async (req, _res, next) => {
  const token = readToken(req);
  if (!token) throw unauthorized();
  let sub: string;
  try {
    const payload = jwt.verify(token, config.JWT_SECRET);
    if (typeof payload === "string" || typeof payload.sub !== "string") throw new Error("bad payload");
    sub = payload.sub;
  } catch {
    throw unauthorized("Your session has expired. Sign in again.");
  }
  const [user] = await db
    .select({ id: users.id, email: users.email, name: users.name, role: users.role, active: users.active })
    .from(users)
    .where(eq(users.id, sub))
    .limit(1);
  if (!user || !user.active) throw unauthorized("Your account is not active. Contact an admin.");
  req.user = { id: user.id, email: user.email, name: user.name, role: user.role };
  next();
});

export const requireRole =
  (...roles: Role[]): RequestHandler =>
  (req, _res, next) => {
    if (!req.user) return next(unauthorized());
    if (!roles.includes(req.user.role)) return next(forbidden());
    next();
  };

/** Anyone except viewers can change data. */
export const requireWriter = requireRole("ADMIN", "REP");
