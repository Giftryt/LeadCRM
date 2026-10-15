import { runMigrations } from "../src/db/migrate.js";

/** Applies migrations to the test database once before the suite. */
export default async function setup() {
  const url =
    process.env.TEST_DATABASE_URL ??
    "postgresql://postgres:postgres@localhost:5432/leadtracker_test?schema=public";
  await runMigrations(url);
}
