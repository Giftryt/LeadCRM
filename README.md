# Lead Tracker CRM

A sales lead tracker for small teams: a drag-and-drop pipeline, a searchable lead list, a follow-up queue, an activity log per lead, CSV import/export, and team accounts with roles.

| Layer    | Tech                                                                         |
| -------- | ---------------------------------------------------------------------------- |
| Frontend | React 18, TypeScript, Vite, React Router, TanStack Query                     |
| API      | Java 21, Spring Boot 3.3 (Web, Security, Data JPA), Maven                    |
| Database | PostgreSQL 16, schema managed by Flyway (`apps/api/src/main/resources/db/migration`) |
| Auth     | Email + password (BCrypt), JWT session in an httpOnly cookie, roles          |
| Tests    | JUnit 5 + MockMvc against a real PostgreSQL database; Vitest for the web app |
| Delivery | Docker (Temurin JRE API + nginx-served web app), docker compose, GitHub Actions CI |

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

| Role     | Can do                                                                  |
| -------- | ----------------------------------------------------------------------- |
| `ADMIN`  | Everything, including managing users and deleting any lead              |
| `REP`    | Add, edit and log activity on any lead; delete leads they own or created |
| `VIEWER` | Read-only access to leads, activity and the dashboard                   |

"Today" for follow-ups is calculated in the business timezone (`APP_TIMEZONE`, default `Asia/Kolkata`), not the server's timezone.

## Project layout

```
apps/
  api/                         Spring Boot API (Maven)
    pom.xml
    src/main/java/com/leadtracker/
      auth/                    sign-in, JWT sessions, security filter, rate limiting
      user/                    users and roles, team management endpoints
      lead/                    leads: entity, input validation, SQL read queries, service, CSV import, controller
      activity/                lead history entries
      dashboard/               summary numbers
      common/                  error handling, dates, CSV, health check
      config/                  settings (AppProperties), Spring Security, clock
      seed/                    first admin and sample leads on startup
    src/main/resources/
      application.yml          all settings, overridable with environment variables
      db/migration/            Flyway SQL migrations (V1__init.sql, ...)
    src/test/                  integration tests (MockMvc + PostgreSQL) and unit tests
  web/                         React app
    src/
      api/                     typed API client + TanStack Query hooks
      pages/                   Pipeline, Leads, Follow-ups, Team, Account, Login
      components/              Layout, LeadDrawer, shared bits
      lib/                     constants, formatting (₹ lakh/crore), URL filters
docker-compose.yml             PostgreSQL + API + web
.github/workflows/ci.yml       Java build and tests, web typecheck/tests/build, Docker images
```

## Local development

Requirements: **Java 21**, **Maven 3.9+**, **Node.js 20+**, and PostgreSQL 14+ (or Docker for just the database).

```bash
# 1. Start PostgreSQL (skip if you already run one)
docker run -d --name leadtracker-db -p 5432:5432 \
  -e POSTGRES_USER=leadtracker -e POSTGRES_PASSWORD=change-me -e POSTGRES_DB=leadtracker postgres:16-alpine

# 2. Settings for the API (or put these in your IDE run configuration)
export DB_URL=jdbc:postgresql://localhost:5432/leadtracker DB_USERNAME=leadtracker DB_PASSWORD=change-me
export JWT_SECRET=$(openssl rand -base64 48)
export SEED_ADMIN_EMAIL=admin@example.com SEED_ADMIN_PASSWORD=ChangeMe123! SEED_SAMPLE_LEADS=true

# 3. Install web dependencies
npm install

# 4. Run the API (http://localhost:4000) and web app (http://localhost:5173) together
npm run dev
```

On first start the API creates the tables (Flyway), the first admin and, with `SEED_SAMPLE_LEADS=true`, eight demo leads. Sign in with the seed admin, then change the password under **Account**.

The Vite dev server proxies `/api` to the API, so the browser sees a single origin and no CORS setup is needed.

To run the API from an IDE, run `com.leadtracker.LeadTrackerApplication` with the environment variables above. In IntelliJ IDEA, open `apps/api/pom.xml` as a project.

### Useful commands (from the repo root)

| Command             | What it does                                                    |
| ------------------- | --------------------------------------------------------------- |
| `npm run dev`       | API (`mvn spring-boot:run`) + Vite dev server                   |
| `npm test`          | API tests (`mvn verify`, needs PostgreSQL) + web unit tests      |
| `npm run build`     | API jar in `apps/api/target/` + web build in `apps/web/dist/`   |
| `npm run typecheck` | TypeScript check for the web app                                |

### Changing the database schema

Add a new file in `apps/api/src/main/resources/db/migration/` named `V2__short_description.sql` (then V3, V4...), and update the matching JPA entity. Flyway applies new migrations automatically when the API starts. Never edit a migration that has already run in any environment.

### Tests

API tests run against a real PostgreSQL database named `leadtracker_test` (default `jdbc:postgresql://localhost:5432/leadtracker_test`, user `postgres` / `postgres`; override with `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`). Create it once with `createdb leadtracker_test`. Flyway migrates it and each test starts from empty tables.

```bash
mvn -f apps/api/pom.xml verify      # API
npm run test -w apps/web            # web
```

## Deployment

### Docker compose (single server)

```bash
cp .env.example .env    # set POSTGRES_PASSWORD, JWT_SECRET (openssl rand -base64 48), SEED_ADMIN_*
docker compose up --build -d
```

The app is served on `http://<server>:8080`. The first admin is created on startup from `SEED_ADMIN_EMAIL` / `SEED_ADMIN_PASSWORD`. Put it behind HTTPS (a load balancer, Caddy, or nginx with Let's Encrypt). The session cookie is `Secure` by default in compose, so sign-in only works over HTTPS. To try the stack on plain `http://localhost`, set `COOKIE_SECURE=false` in `.env`.

Migrations run on every start, so deploying a new version is `git pull && docker compose up --build -d`.

### Other platforms

- **API**: any container platform (AWS ECS / App Runner, Azure Container Apps, Google Cloud Run, Kubernetes, Render, Railway) using `apps/api/Dockerfile` built from the repo root, or run the jar directly with `java -jar lead-tracker-api.jar`. Required settings: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `COOKIE_SECURE=true`.
- **Database**: managed PostgreSQL (RDS, Cloud SQL, Azure Database, Neon, Supabase). Enable automated backups.
- **Web**: `apps/web/Dockerfile` (nginx), or any static host serving `apps/web/dist`. Route `/api/*` to the API on the **same domain** so the session cookie works. If the API must live on a different domain, set `CORS_ORIGIN` to the web app's origin and change the cookie to `SameSite=None` in `SessionCookies.java`.
- **Several API instances**: sessions are stateless JWTs, so instances need no shared session store. The sign-in rate limiter is per instance; use your load balancer's rate limiting for a shared limit.

### Environment variables

| Variable            | Required | Default                                     | Notes                                                   |
| ------------------- | -------- | ------------------------------------------- | ------------------------------------------------------- |
| `DB_URL`            | yes      | `jdbc:postgresql://localhost:5432/leadtracker` | JDBC URL                                             |
| `DB_USERNAME` / `DB_PASSWORD` | yes | `leadtracker` / `change-me`            | Database credentials                                    |
| `JWT_SECRET`        | yes      |                                             | 32+ random characters; changing it signs everyone out   |
| `PORT`              |          | `4000`                                      | API port                                                |
| `SESSION_TTL_HOURS` |          | `12`                                        | How long a sign-in lasts                                |
| `APP_TIMEZONE`      |          | `Asia/Kolkata`                              | Decides what "today" means for follow-ups               |
| `COOKIE_SECURE`     |          | `false` (compose: `true`)                   | Send the session cookie over HTTPS only. `true` in production |
| `CORS_ORIGIN`       |          |                                             | Comma-separated origins, only for cross-origin setups   |
| `LOGIN_RATE_LIMIT`  |          | `20`                                        | Sign-in attempts per IP per 15 minutes                  |
| `SEED_ADMIN_EMAIL` / `SEED_ADMIN_PASSWORD` / `SEED_ADMIN_NAME` | first run | | First admin, created only when there are no users |
| `SEED_SAMPLE_LEADS` |          | `false`                                     | `true` adds 8 demo leads when there are none (development only) |

Any Spring Boot property can also be set the usual way (`SPRING_DATASOURCE_URL`, `--server.port=...`, `application-<profile>.yml`).

## Security notes

- Passwords are hashed with BCrypt (cost 12) and must be 10+ characters with a letter and a number.
- Sessions are signed JWTs (HS256) in an `httpOnly`, `SameSite=Lax` cookie. Every request re-checks the user, so deactivating someone locks them out immediately.
- Sign-in is rate limited per IP and returns the same message for unknown emails and wrong passwords.
- All input is validated server-side; all SQL is parameterised (JPA and `NamedParameterJdbcTemplate`).
- CSRF tokens are not used: the cookie is `SameSite=Lax` and every write takes a JSON or multipart body.

## API

See [docs/API.md](docs/API.md) for every endpoint, its parameters and response shapes.

## Roadmap ideas

- Password reset by email (needs an email provider such as SES, SendGrid or Postmark)
- Google Workspace / Microsoft 365 single sign-on (Spring Security OAuth2 client)
- Email and WhatsApp reminders for due follow-ups
- Custom pipeline stages and fields per team
- Lead assignment rules and round-robin
- OpenAPI / Swagger UI via springdoc-openapi
