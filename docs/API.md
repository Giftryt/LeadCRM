# Lead Tracker API

Base path: `/api`. Served by the Spring Boot app in `apps/api`. Requests and responses are JSON unless noted. Authenticate by signing in; the API sets an `httpOnly` session cookie. Scripts can instead send `Authorization: Bearer <token>` using the `token` returned by `/auth/login`.

Errors always look like:

```json
{ "error": { "code": "validation_error", "message": "email: is not a valid email address", "details": [{ "field": "email", "message": "is not a valid email address" }] } }
```

| Status | `code`                         | Meaning                                       |
| ------ | ------------------------------ | --------------------------------------------- |
| 400    | `validation_error`, `bad_request`, `bad_json` | The request is invalid          |
| 401    | `unauthorized`                 | Not signed in, session expired, or account inactive |
| 403    | `forbidden`                    | Signed in, but the role doesn't allow this    |
| 404    | `not_found`                    | No such record or endpoint                    |
| 409    | `conflict`                     | Duplicate (e.g. user email already exists)    |
| 413    | `file_too_large`               | CSV larger than 5 MB                          |
| 429    | `rate_limited`                 | Too many sign-in attempts                     |

Enums:

- `stage`: `NEW` `CONTACTED` `QUALIFIED` `PROPOSAL` `NEGOTIATION` `WON` `LOST`
- `source`: `WEBSITE` `REFERRAL` `LINKEDIN` `COLD_OUTREACH` `EVENT` `PARTNER` `INBOUND_CALL` `OTHER`
- `role`: `ADMIN` `REP` `VIEWER`
- activity `type`: `CALL` `EMAIL` `MEETING` `NOTE` (loggable), `STAGE_CHANGE` `CREATED` (automatic)

Dates (`nextFollowUp`) are calendar dates, `YYYY-MM-DD`. Timestamps are ISO 8601 UTC.

## Health

`GET /api/health` → `200 {"status":"ok"}` or `503` if the database is unreachable. No auth.

## Auth

| Method & path                 | Body                                  | Response |
| ----------------------------- | ------------------------------------- | -------- |
| `POST /auth/login`            | `{ email, password }`                 | `{ user, token }` and sets the cookie |
| `POST /auth/logout`           |                                       | `204`, clears the cookie |
| `GET /auth/me`                |                                       | `{ user: { id, email, name, role } }` |
| `POST /auth/change-password`  | `{ currentPassword, newPassword }`    | `204` |

## Users

| Method & path         | Role    | Notes |
| --------------------- | ------- | ----- |
| `GET /users`          | any     | Active users, `{ items: User[] }`. Admins may add `?includeInactive=true`. |
| `POST /users`         | ADMIN   | `{ name, email, role, password }` → `201 User` |
| `PATCH /users/:id`    | ADMIN   | Any of `{ name, role, active, password }`. Admins can't deactivate or demote themselves. |

`User` = `{ id, email, name, role, active, createdAt }`.

## Leads

`Lead`:

```json
{
  "id": "uuid", "name": "Ananya Rao", "company": "Brightleaf Logistics", "email": "ananya@example.com", "phone": "+91 98200 11234",
  "source": "REFERRAL", "stage": "QUALIFIED", "valueInr": 450000, "nextFollowUp": "2026-10-20",
  "notes": "…", "lostReason": null,
  "owner": { "id": "uuid", "name": "Priya", "email": "priya@example.com" }, "createdBy": { … },
  "stageChangedAt": "…", "wonAt": null, "lastContactAt": "…", "createdAt": "…", "updatedAt": "…"
}
```

### `GET /leads`

Query parameters (all optional):

| Param      | Values                                                     |
| ---------- | ---------------------------------------------------------- |
| `q`        | Search in name, company, email, phone, notes               |
| `stage`    | One or more stages, comma-separated (`WON,LOST`)            |
| `source`   | A source                                                   |
| `owner`    | `me`, `none`, or a user id                                 |
| `followUp` | `overdue`, `today`, `week` (today + 7 days), `none` — open leads only |
| `open`     | `true` (not won/lost) or `false`                            |
| `sort`     | `updatedAt` (default), `createdAt`, `name`, `company`, `valueInr`, `nextFollowUp`, `stage` |
| `order`    | `desc` (default) or `asc`                                  |
| `page`, `pageSize` | Pagination; `pageSize` up to 1000, default 100      |

→ `{ items: Lead[], total, page, pageSize }`

### Create, read, update, delete

| Method & path        | Role          | Notes |
| -------------------- | ------------- | ----- |
| `POST /leads`        | ADMIN, REP    | Body: `name` (required), `company`, `email`, `phone`, `source`, `stage`, `valueInr`, `nextFollowUp`, `notes`, `lostReason`, `ownerId`. Logs a `CREATED` activity. → `201 Lead` |
| `GET /leads/:id`     | any           | → `Lead` |
| `PATCH /leads/:id`   | ADMIN, REP    | Any subset of the create fields. A stage change records `stageChangedAt`, sets `wonAt` when moved to `WON`, and logs a `STAGE_CHANGE` activity. → `Lead` |
| `DELETE /leads/:id`  | ADMIN, or the REP who owns or created it | Deletes the lead and its activity. → `204` |

### Activity

| Method & path                  | Role        | Notes |
| ------------------------------ | ----------- | ----- |
| `GET /leads/:id/activities`    | any         | Newest first, `{ items: Activity[] }` |
| `POST /leads/:id/activities`   | ADMIN, REP  | `{ type, body, nextFollowUp? }`. `nextFollowUp` (date or `null`) updates the lead in the same step. Calls, emails and meetings update `lastContactAt`. → `201 { activity, lead }` |

`Activity` = `{ id, leadId, type, body, user, createdAt }`.

### CSV

`GET /leads/export.csv` accepts the same filters as `GET /leads` and returns a UTF-8 CSV (with BOM for Excel).

`POST /leads/import` (ADMIN, REP) takes `multipart/form-data` with a `file` field (CSV, max 5 MB, 5,000 rows). Recognised headers (case-insensitive):

| Field          | Header names                                               |
| -------------- | ---------------------------------------------------------- |
| name           | Name, Contact, Contact name, Lead, Full name               |
| company        | Company, Organisation, Organization, Account               |
| email          | Email, E-mail, Email address                               |
| phone          | Phone, Mobile, Phone number, Contact number                |
| source         | Source, Lead source (unknown values become `OTHER`)        |
| stage          | Stage, Status (labels like "Proposal sent" work)           |
| value          | Value, Value (INR), Deal value, Amount (₹ and commas are stripped) |
| next follow-up | Next follow-up, Follow-up, Follow up                       |
| notes          | Notes, Note, Comments                                      |
| owner          | Owner, Owner email, Assigned to (matched by email or name; defaults to the importer) |

Dates may be `YYYY-MM-DD` or `DD/MM/YYYY`. Rows with no name and no company, an invalid email, or an impossible date are skipped and reported.

→ `201 { imported, skipped, errors: [{ row, message }] }`

## Dashboard

`GET /dashboard/summary?owner=me|none` →

```json
{
  "openPipeline": { "count": 6, "valueInr": 6180000 },
  "weightedForecastInr": 3648000,
  "wonThisMonth": { "count": 1, "valueInr": 3500000 },
  "winRate": 0.5,
  "followUps": { "overdue": 2, "today": 1 },
  "stages": [{ "stage": "NEW", "count": 1, "valueInr": 180000 }, …]
}
```

Stage probabilities for the weighted forecast: New 10%, Contacted 20%, Qualified 40%, Proposal sent 60%, Negotiation 80%.
