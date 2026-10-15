# Lead Tracker CRM

A sales lead tracker for small teams: a drag-and-drop pipeline, a searchable lead list, a follow-up queue, an activity log per lead, CSV import/export, and team accounts with roles.

| Layer    | Tech                                                            |
| -------- | --------------------------------------------------------------- |
| Frontend | React 18, TypeScript, Vite, React Router, TanStack Query        |
| API      | Node.js 20, Express, TypeScript, Zod validation                 |
| Database | PostgreSQL 16 via Drizzle ORM (SQL migrations in `apps/api/drizzle`) |
| Auth     | Email + password (bcrypt), JWT in an httpOnly cookie, roles     |
| Tests    | Vitest + Supertest against a real PostgreSQL database           |
| Delivery | Docker (API + nginx-served web app), docker compose, GitHub Actions CI |

## Features

- **Pipeline board** with seven stages (New → Contacted → Qualified → Proposal sent → Negotiation → Won / Lost). Drag cards to change stage; each column shows count and ₹ total.
- **Summary strip**: open pipeline, weighted forecast (value × stage probability), won this month and win rate, overdue / due-today follow-ups.
- **All leads** table with search, owner/source/stage filters, sorting and pagination. Filters live in the URL, so views can be bookmarked and shared.
- **Follow-ups** grouped into Overdue, Today, Next 7 days, Later and No date, with one-click snooze.
- **Lead details**: contact info, owner, value, source, next follow-up, notes, lost reason. Log calls, emails, meetings and notes, and set the next follow-up in the same step. Stage changes are recorded automatically.
- **CSV import** (flexible headers, Indian DD/MM/YYYY dates, ₹-formatted values, per-row error report) and **CSV export** of the current filters.
- **Team management** for admins: add users, change roles, deactivate accounts (takes effect immediately).
- Light and dark themes, works at phone width.

### Roles

| Role       | Can do                                                                       |
| ---------- | ---------------------------------------------------------------------------- |
| `ADMIN`    | Everything, including managing users and deleting any lead                    |
| `REP`      | Add, edit and log activity on any lead; delete leads they own or created      |
| `VIEWER`   | Read-only access to leads, activity and the dashboard                         |

"Today" for follow-ups is calculated in the business timezone (`APP_TIMEZONE`, default `Asia/Kolkata`), not the server's timezone.

## Project layout

```
apps/
  api/                 Express API
    src/
      db/              schema.ts (tables), migrate.ts, seed.ts
      routes/          auth, users, leads (+ activities, import/export), dashboard
      middleware/      auth (session + roles), error handling
      lib/             dates, lead helpers and serializers
    drizzle/           generated SQL migrations (commit these)
    test/              integration tests (run against PostgreSQL)
  web/                 React app
    src/
      api/             typed API client + TanStack Query hooks
      pages/           Pipeline, Leads, Follow-ups, Team, Account, Login
      components/      Layout, LeadDrawer, shared bits
      lib/             constants, formatting (₹ lakh/crore), URL filters
docker-compose.yml     PostgreSQL + API + web
.github/workflows/     CI: typecheck, tests, build, Docker images
```

## Local development

Requirements: Node.js 20+, PostgreSQL 14+ (or Docker for just the database).

```bash
# 1. Install
npm install

# 2. Start PostgreSQL (skip if you already run one)
docker run -d --name leadtracker-db -p 5432:5432 \
  -e POSTGRES_USER=leadtracker -e POSTGRES_PASSWORD=change-me -e POSTGRES_DB=leadtracker postgres:16-alpine

# 3. Configure the API
cp apps/api/.env.example apps/api/.env      # edit DATABASE_URL and JWT_SECRET

# 4. Create tables, the first admin and sample leads
npm run db:migrate
npm run db:seed

# 5. Run API (http://localhost:4000) and web app (http://localhost:5173) together
npm run dev
```

Sign in with `SEED_ADMIN_EMAIL` / `SEED_ADMIN_PASSWORD` from `apps/api/.env`, then change the password under **Account**. Set `SEED_SAMPLE_LEADS=false` if you don't want demo data.

The Vite dev server proxies `/api` to the API, so the browser sees a single origin and no CORS setup is needed.

### Useful scripts (run from the repo root)

| Command              | What it does                                              |
| -------------------- | --------------------------------------------------------- |
| `npm run dev`        | API with reload + Vite dev server                         |
| `npm test`           | API integration tests + web unit tests                    |
| `npm run typecheck`  | TypeScript checks for both apps                           |
| `npm run build`      | Production builds into `apps/*/dist`                      |
| `npm run db:migrate` | Apply pending migrations                                  |
| `npm run db:seed`    | Create the first admin (and sample leads if enabled)      |
| `npm run db:studio`  | Browse the database in Drizzle Studio                     |

### Changing the database schema

1. Edit `apps/api/src/db/schema.ts`.
2. `npm run db:generate -w apps/api` writes a new SQL file to `apps/api/drizzle/`. Review it.
3. `npm run db:migrate` applies it locally. Commit the schema and the migration together.

CI fails if `schema.ts` changes without a matching migration.

### Tests

API tests use a separate database (default `postgresql://postgres:postgres@localhost:5432/leadtracker_test`, override with `TEST_DATABASE_URL`). Create it once with `createdb leadtracker_test`; migrations are applied automatically and tables are truncated between tests.

## Deployment

### Docker compose (single server)

```bash
cp .env.example .env            # set POSTGRES_PASSWORD, JWT_SECRET (openssl rand -base64 48), SEED_ADMIN_*
docker compose up --build -d
docker compose exec api node dist/db/seed.js   # creates the first admin
```

The app is served on `http://<server>:8080`. Put it behind HTTPS (a load balancer, Caddy, or nginx with Let's Encrypt). The session cookie is `Secure` by default, so sign-in only works over HTTPS. To try the stack on plain `http://localhost`, set `COOKIE_SECURE=false` in `.env`.

The API container applies pending migrations on every start, so deploying a new version is `git pull && docker compose up --build -d`.

### Managed platforms

- **API**: any Node 20 host or container service (Render, Railway, Fly.io, AWS ECS/App Runner, Azure Container Apps, Cloud Run). Build with `apps/api/Dockerfile` from the repo root. Required env: `DATABASE_URL`, `JWT_SECRET`, `NODE_ENV=production`.
- **Database**: managed PostgreSQL (RDS, Cloud SQL, Azure Database, Neon, Supabase). Enable automated backups.
- **Web**: `apps/web/Dockerfile` (nginx), or any static host serving `apps/web/dist`. Route `/api/*` to the API on the **same domain** so the session cookie works. If the API must live on a different domain, set `CORS_ORIGIN` to the web app's origin and change the cookie to `SameSite=None` in `apps/api/src/middleware/auth.ts`.

### Environment variables

| Variable             | Required | Default          | Notes                                                |
| -------------------- | -------- | ---------------- | ---------------------------------------------------- |
| `DATABASE_URL`       | yes      |                  | PostgreSQL connection string                         |
| `JWT_SECRET`         | yes      |                  | 32+ random characters; rotating it signs everyone out |
| `NODE_ENV`           |          | `development`    | `production` in deployed environments                |
| `PORT`               |          | `4000`           | API port                                             |
| `SESSION_TTL_HOURS`  |          | `12`             | How long a sign-in lasts                             |
| `APP_TIMEZONE`       |          | `Asia/Kolkata`   | Decides what "today" means for follow-ups            |
| `COOKIE_SECURE`      |          | `true` in prod   | Send the session cookie over HTTPS only              |
| `CORS_ORIGIN`        |          |                  | Comma-separated origins, only for cross-origin setups |
| `SEED_ADMIN_EMAIL` / `SEED_ADMIN_PASSWORD` / `SEED_ADMIN_NAME` | for seeding | | First admin account |
| `SEED_SAMPLE_LEADS`  |          | `false`          | `true` adds 8 demo leads (development only)           |

## Security notes

- Passwords are hashed with bcrypt (cost 12) and must be 10+ characters with a letter and a number.
- Sessions are signed JWTs in an `httpOnly`, `SameSite=Lax` cookie. Every request re-checks the user, so deactivating someone signs them out immediately.
- Sign-in is rate limited (20 attempts per 15 minutes per IP) and returns the same message for unknown emails and wrong passwords.
- All input is validated with Zod; queries are parameterised through Drizzle.
- `helmet` sets security headers; request logs redact cookies and auth headers.

## API

See [docs/API.md](docs/API.md) for every endpoint, its parameters and response shapes.

## Roadmap ideas

- Password reset by email (needs an email provider such as SES, SendGrid or Postmark)
- Google Workspace / Microsoft 365 single sign-on
- Email and WhatsApp reminders for due follow-ups
- Custom pipeline stages and fields per team
- Lead assignment rules and round-robin
- Audit log export and data retention settings
