# Where the Water Reaches

A locality-level tracker of Pune Municipal Corporation (PMC) water status: where municipal
piped supply reaches, which areas depend on tankers, and where pipeline work is underway.

**Why this exists:** PMC doesn't publish a public, locality-level, machine-readable feed of
water-supply status. This project fills that gap with a curated dataset — every entry cites
where the claim comes from and when it was last checked — plus a review queue for citizen
corrections, so the data can stay current without pretending to be a live official source.

## Project layout

```
backend/    Spring Boot 3 REST API (Java 21) -- dev profile: H2 in-memory; prod profile: MySQL (Aiven)
frontend/   React + Vite app: Leaflet map + searchable/filterable list
```

## Running the backend

Requires JDK 21 and Maven.

```bash
cd backend
mvn spring-boot:run
```

Starts on `http://localhost:8080` using the `dev` profile (in-memory H2, no setup, seeds fresh
on every restart from `LocalityDataInitializer`). See "Deploying with AWS RDS" below to run
against real MySQL instead.

Key endpoints:

| Method | Path                                | Purpose                                      |
|--------|-------------------------------------|-----------------------------------------------|
| GET    | `/api/localities`                   | List localities. Filters: `?status=`, `?search=` |
| GET    | `/api/localities/{id}`              | One locality                                  |
| POST   | `/api/localities`                   | Add a curated (verified) entry                |
| POST   | `/api/localities/reports`           | Submit a citizen report (goes in as unverified) |
| GET    | `/api/localities/reports/pending`   | List reports awaiting review                  |
| POST   | `/api/localities/reports/{id}/approve` | Publish a pending report                   |
| PUT    | `/api/localities/{id}`              | Edit an entry                                 |
| DELETE | `/api/localities/{id}`              | Remove an entry                               |

`status` is one of `MUNICIPAL`, `TANKER_DEPENDENT`, `MIXED`, `PIPELINE_IN_PROGRESS`.

### Auth

Write access requires logging in and using the returned token — not per-user Basic auth on
every request. Two roles:

| Role   | Can do |
|--------|--------|
| ADMIN  | Everything: create/edit/delete localities, approve reports, manage user accounts |
| EDITOR | Create/edit localities, approve citizen reports -- cannot delete entries or manage users |

`GET` requests and `POST /api/localities/reports` (citizen report submission) stay open to
everyone, no login required.

**First login:** on first startup, if no users exist yet, an admin account is created
automatically from `ADMIN_USERNAME`/`ADMIN_PASSWORD` env vars (falls back to
`admin` / `change-me-now` locally if unset). **Set real values for these on Render** under
your service's Environment tab before it's public — the app refuses to start under the `prod`
profile while `ADMIN_PASSWORD` is still the default.

**JWT secret:** similarly, set a real random `JWT_SECRET` (32+ characters — e.g.
`openssl rand -base64 48`) on Render. The app also refuses to start under `prod` with the
default secret in place. Tokens expire after 12 hours by default (`JWT_EXPIRATION_MS`).

**Password requirements:** new accounts created via `POST /api/users` need at least 8
characters, with at least one letter and one number.

**1. Log in to get a token:**
```bash
curl -X POST https://your-backend.onrender.com/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "<your password>"}'
```
Returns `{"token": "...", "username": "admin", "role": "ADMIN"}`.

**2. Use the token on subsequent calls** (`Authorization: Bearer <token>`):
```bash
# Approve a citizen report
curl -X POST https://your-backend.onrender.com/api/localities/reports/3/approve \
  -H "Authorization: Bearer <token>"

# Add an editor (admin only)
curl -X POST https://your-backend.onrender.com/api/users \
  -H "Authorization: Bearer <admin-token>" \
  -H "Content-Type: application/json" \
  -d '{"username": "priya", "password": "<their password>", "role": "EDITOR"}'
```

Tokens expire after 12 hours — log in again to get a fresh one; there's no refresh-token flow
yet, which is fine for occasional admin use but worth adding if this sees frequent editing.

There's no login screen in the frontend yet — it's a read-only public site by design. Admin/editor
actions go through the API directly (curl, Postman, etc.) until/unless a login UI gets added.

## Running the frontend

Requires Node 18+.

```bash
cd frontend
npm install
npm run dev
```

Opens on `http://localhost:5173` and talks to the backend at `http://localhost:8080/api`
by default — copy `.env.example` to `.env` to point it elsewhere.

## About the seed data

`LocalityDataInitializer` seeds ~39 localities compiled from PMC's 2026-27 budget announcement,
recent news coverage of merged-village tanker dependency, RTI data on tanker spending, and a
property-buyer water guide -- full citations are in each row's `sourceName`/`sourceUrl`. A few
long-established core areas (Kothrud, Aundh, etc.) are included as a general baseline without a
specific dated 2026 source, flagged as such in their notes -- treat those as lower-confidence
and worth a local check before relying on them. It only seeds if the table is empty, so it's
safe on every restart against a persistent database and won't duplicate rows.

This is a **starting point, not ground truth**. Update entries as PMC publishes new information,
as you verify things locally, or by reviewing citizen reports through the `/reports` endpoints.

**On the `ward` field:** PMC's 41 wards are numbered (1-41), not named -- there's no public,
fetchable mapping of "this locality is in ward N," since that requires GIS boundary data, not
just a locality's name or coordinates. Some entries have an informal area name in `ward` as a
loose hint; most are left `null` rather than guessing. If you want it accurate, PMC's own ward
locator (search "find my ward pmc.gov.in") is the reliable source per-address -- worth filling
in over time via the `PUT /api/localities/{id}` endpoint rather than trying to bulk-populate it.

## Deploying with a hosted MySQL database (Aiven)

The `prod` Spring profile (`application-prod.properties`) points at MySQL via env vars.
[Aiven](https://aiven.io) has a genuinely free MySQL plan (1GB, no credit card, no time limit)
that works directly with it -- and unlike AWS RDS, there's no VPC/security-group setup needed
since it's open to the internet by default (secured by SSL + password, not IP allowlisting).

**1. Create the service**
Aiven dashboard → Create service → MySQL → Free plan → pick any region → name it → Create.
Takes a couple minutes to provision.

**2. Grab your connection details**
From the service overview page: **Host**, **Port**, **User** (usually `avnadmin`),
**Password**, and the **default database** name.

**3. Set env vars on Render** (service → Environment tab):
```
SPRING_PROFILES_ACTIVE=prod
DB_HOST=<your-aiven-host>
DB_PORT=<your-aiven-port>
DB_NAME=<your-aiven-database-name>
DB_USERNAME=avnadmin
DB_PASSWORD=<your-aiven-password>
ADMIN_USERNAME=<a real admin username>
ADMIN_PASSWORD=<a real admin password>
```
Save → Render redeploys → `AdminBootstrap` creates your admin account and
`LocalityDataInitializer` seeds the localities, both against Aiven this time, both persisting
across future redeploys since `ddl-auto=update` no longer wipes the schema.

Note the free Aiven plan's 1GB storage cap -- comfortably enough for this project's data, but
worth knowing if you plan to grow it substantially.

## Optional: async report notifications (RabbitMQ)

When a citizen submits a report (`POST /api/localities/reports`), the app can publish a
`report.submitted` event -- useful for notifying admins (email/Slack/etc.) without slowing
down or risking the citizen's actual request. **Disabled by default** (`RABBITMQ_ENABLED=false`)
-- with no broker configured, nothing changes; report submission works exactly as before.

**To enable it:**
1. Get a broker. [CloudAMQP](https://www.cloudamqp.com) has a genuine free tier ("Little
   Lemur" -- small, but free, no card required).
2. Set env vars: `RABBITMQ_ENABLED=true`, `RABBITMQ_HOST`, `RABBITMQ_PORT` (usually `5671` for
   TLS), `RABBITMQ_USERNAME`, `RABBITMQ_PASSWORD`, `RABBITMQ_VHOST` (CloudAMQP's vhost is
   usually the same as the username), `RABBITMQ_SSL_ENABLED=true`.
3. For email notifications, also set: `MAIL_USERNAME` and `MAIL_PASSWORD` (an SMTP account to
   send *from*), and `ADMIN_NOTIFICATION_EMAIL` (where reports get sent *to*). Defaults assume
   Gmail SMTP -- if using Gmail, `MAIL_USERNAME` is your Gmail address and `MAIL_PASSWORD` must
   be an [App Password](https://myaccount.google.com/apppasswords) (not your real Gmail
   password; requires 2FA enabled on the account). Any SMTP provider works if you'd rather use
   one -- just also set `MAIL_HOST`/`MAIL_PORT` to match.
4. Redeploy. `RabbitMQConfig` creates the exchange/queue, `ReportEventPublisher` starts
   publishing, and `ReportNotificationListener` consumes and emails
   `ADMIN_NOTIFICATION_EMAIL` with the report details and a direct approve link. If mail env
   vars are missing, it logs and skips the email rather than failing -- the report itself
   always saves successfully either way.

## Next steps worth considering

- A login screen + admin UI in the frontend for reviewing pending citizen reports and managing
  localities, rather than calling the API directly.
