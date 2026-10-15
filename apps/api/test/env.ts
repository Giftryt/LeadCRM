// Runs before every test file, before the app's config is imported.
export const TEST_DATABASE_URL =
  process.env.TEST_DATABASE_URL ??
  "postgresql://postgres:postgres@localhost:5432/leadtracker_test?schema=public";

process.env.NODE_ENV = "test";
process.env.DATABASE_URL = TEST_DATABASE_URL;
process.env.JWT_SECRET ??= "test-secret-test-secret-test-secret-123";
process.env.APP_TIMEZONE ??= "Asia/Kolkata";
