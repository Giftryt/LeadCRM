/**
 * Applies pending SQL migrations from apps/api/drizzle.
 *   development: npm run db:migrate
 *   production:  npm run db:deploy   (runs automatically when the Docker container starts)
 */
import { existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { drizzle } from "drizzle-orm/node-postgres";
import { migrate } from "drizzle-orm/node-postgres/migrator";
import pg from "pg";

export function migrationsFolder(): string {
  // dist/db/migrate.js -> dist/drizzle ; src/db/migrate.ts -> drizzle
  for (const rel of ["../drizzle", "../../drizzle"]) {
    const p = fileURLToPath(new URL(rel, import.meta.url));
    if (existsSync(p)) return p;
  }
  throw new Error("Migrations folder not found");
}

export async function runMigrations(databaseUrl: string) {
  const pool = new pg.Pool({ connectionString: databaseUrl, max: 1 });
  try {
    await migrate(drizzle(pool), { migrationsFolder: migrationsFolder() });
  } finally {
    await pool.end();
  }
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  await import("dotenv/config");
  const url = process.env.DATABASE_URL;
  if (!url) {
    console.error("DATABASE_URL is not set");
    process.exit(1);
  }
  await runMigrations(url);
  console.log("Migrations applied");
}
