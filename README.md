# Where the Water Reaches

A locality-level tracker of Pune Municipal Corporation (PMC) water status: where municipal
piped supply reaches, which areas depend on tankers, and where pipeline work is underway.

**Why this exists:** PMC doesn't publish a public, locality-level, machine-readable feed of
water-supply status. This project fills that gap with a curated dataset — every entry cites
where the claim comes from and when it was last checked — plus a review queue for citizen
corrections, so the data can stay current without pretending to be a live official source.

## Project layout

```
backend/    Spring Boot 3 REST API (Java 21), H2 in-memory by default, MySQL-ready
frontend/   React + Vite app: Leaflet map + searchable/filterable list
```

## Running the backend

Requires JDK 21 and Maven.

```bash
cd backend
mvn spring-boot:run
```

Starts on `http://localhost:8080`. On every restart it re-seeds from
`src/main/resources/data.sql` (in-memory H2 — see `application.properties` for the
commented-out MySQL block to make it persistent against your CF + MySQL setup instead).

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

`backend/src/main/resources/data.sql` has ~24 localities compiled from PMC's 2026-27 budget
announcement, recent news coverage of merged-village tanker dependency, and a property-buyer
water guide — full citations are in each row's `source_name`/`source_url`. A few long-established
core areas (Kothrud, Aundh, etc.) are included as a general baseline without a specific dated
2026 source, flagged as such in their notes — treat those as lower-confidence and worth a local
check before relying on them.

This is a **starting point, not ground truth**. Update entries as PMC publishes new information,
as you verify things locally, or by reviewing citizen reports through the `/reports` endpoints.

## Next steps worth considering

- Add simple auth in front of the write endpoints (`POST`/`PUT`/`DELETE`, and the reports
  approval flow) before deploying publicly.
- A lightweight admin UI for reviewing pending citizen reports, rather than calling the API
  directly.
- Swap the H2 dev database for your MySQL DBaaS setup using the commented block in
  `application.properties`.
