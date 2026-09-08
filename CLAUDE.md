# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

`suspicious-events-detector` — a Spring Boot 2.6.3 (Java 8) REST service for Freightos SRE that reads
application access-log events from a MySQL `event` table and flags suspicious/unauthorized activity.
There is no frontend; it's a pure backend API.

## Commands

No Maven wrapper is checked in — use a globally installed `mvn`.

```
mvn spring-boot:run          # run the app locally (needs a reachable MySQL instance, see below)
mvn test                     # run all tests
mvn test -Dtest=SuspiciousEventsServiceTest#captureUnauthorizedRequests   # run a single test
mvn package                  # build the jar
```

The app needs a MySQL database to start (JPA/Hibernate + `spring-boot-starter-data-jpa`). Configure via env
vars (defaults shown): `MYSQL_HOST=localhost`, `MYSQL_PORT=3306`, `MYSQL_DATABASE=detector`,
`MYSQL_USERNAME=detector-user`, `MYSQL_PASSWORD=detector-pass`.

- `src/main/resources/application.properties` — default profile: `ddl-auto=update`, so it will run against
  and update an existing schema and will load `import.sql` seed data (33+ synthetic `event` rows, including
  a few flagged "hacker" records) on a fresh schema.
- `src/test/resources/application.properties` — `dev` profile active for tests: `ddl-auto=create-drop`
  (fresh schema per run, `import.sql` is NOT applied under `create-drop`). The one integration test
  (`SuspiciousEventsServiceTest`) is a full `@SpringBootTest` that hits a real MySQL connection — there is no
  in-memory/H2 fallback, so a MySQL instance matching the configured credentials must be reachable to run tests.

## Architecture

Standard thin Spring MVC layering, single package root `com.freightos.suseventsdetector`:

- **`EventRepository`** (Spring Data JPA `CrudRepository<EventResponse, Integer>`) — the only data-access
  point. Besides derived-query methods (`findAllByEmail`, `findAllByIp`), it has one native/JPQL `@Query`,
  `findAllUnAuthorized`, that does the actual suspicious-activity detection: it groups `event` rows by
  `DATE(timestamp)` + `email` where `responseHeaders LIKE %queryString%`, and returns groups whose count
  exceeds a threshold. Despite the method name, the query is driven by a caller-supplied `queryString`
  matched against `responseHeaders` — the "unauthorized" semantics come entirely from the string the caller
  passes in (see below), not from a fixed column/flag.
- **`SuspiciousEventsService`** — wraps `findAllUnAuthorized`. `getUnauthorizedRequests(int threshold)`
  hardcodes the search string to `"isAuthorized": true` (note: this literally matches *authorized* events in
  the JSON-blob `responseHeaders` field — the "suspicious" signal here is *repeated authorized* access above
  a per-day/per-user threshold, not failed auth attempts). An overload accepts an arbitrary `queryString` for
  more flexible matching.
- **`EventsService`** — simple passthrough for `findAll` / `findAllByEmail` / `findAllByIp`.
- **`EventController`** (`/events`) — `GET /events/eventByEmail?email=`, `GET /events/eventByIP?ip=`,
  `GET /events/allEvents`, `GET /events/captureUnauthorizedRequests?threshold=` (default threshold 3).
- **`HealthCheckController`** (`/health`) — `GET /health/is-alive` (always 200) and `GET /health/is-ready`
  (200/503 based on `HealthCheckService.isReady()`, which runs a raw `SELECT` against the `event` table to
  verify DB connectivity). Spring Boot Actuator is also on the classpath with liveness/readiness probes
  enabled (`management.health.livenessState/readinessState.enabled=true`).
- **`EventResponse`** — the JPA `@Entity` mapped to the `event` table (despite the name, this is the request
  log entry, not an API response DTO); fields include `timestamp`, `severity`, `email`, `ip`, `domain`,
  `uri`, `message`, `country`, `sessionUUID`, `requestHeaders`, `responseHeaders` (the latter two store raw
  JSON strings, e.g. `{"isAuthorized": true, "GIP":1}`).
- **`UnauthorizedUser`** — plain (non-entity) DTO returned by the grouped `@Query`: `date`, `email`, `count`.

Logging is routed through Logback with a `springProfile`-based split (`logback-spring.xml`): `default`/`dev`
profiles log plain text to console; `prod`/`test` profiles log structured JSON via
`google-cloud-logging-logback-slf4j` (`GoogleCloudLoggingV2Layout`), for GCP log ingestion. Lombok
(`@Getter`/`@Setter`/`@Slf4j`) is used throughout instead of hand-written boilerplate.
