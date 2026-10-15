import "dotenv/config";
import { z } from "zod";

const schema = z.object({
  NODE_ENV: z.enum(["development", "test", "production"]).default("development"),
  PORT: z.coerce.number().int().positive().default(4000),
  DATABASE_URL: z.string().url(),
  JWT_SECRET: z.string().min(32, "JWT_SECRET must be at least 32 characters"),
  SESSION_TTL_HOURS: z.coerce.number().positive().default(12),
  APP_TIMEZONE: z.string().default("Asia/Kolkata"),
  CORS_ORIGIN: z.string().optional(),
  /** Send the session cookie only over HTTPS. Defaults to true in production; set false only for local HTTP testing. */
  COOKIE_SECURE: z
    .enum(["true", "false"])
    .optional()
    .transform((v) => (v === undefined ? undefined : v === "true")),
});

const parsed = schema.safeParse(process.env);
if (!parsed.success) {
  // Fail fast with a readable message instead of a stack trace deep in a request.
  console.error("Invalid environment configuration:");
  for (const issue of parsed.error.issues) console.error(`  ${issue.path.join(".")}: ${issue.message}`);
  process.exit(1);
}

export const config = parsed.data;
export const isProd = config.NODE_ENV === "production";
export const cookieSecure = config.COOKIE_SECURE ?? isProd;
export const SESSION_COOKIE = "lt_session";
