# S-07 — Deployment — Report

## Status

Complete

## Objective

Deploy Capsa v0.1 as a containerized JVM application suitable for a VPS behind a reverse proxy:

- `docker-compose.yml` — local development stack (Capsa + PostgreSQL, OIDC disabled)
- `docker-compose.prod.yml` — production reference stack (all secrets via env vars, OIDC required)
- `.env.example` — documented inventory of all required environment variables
- `application.properties` — production and docker-dev datasource configuration
- Docker image built and smoke-tested (`GET /q/health/ready` → UP)
- Native build attempted (`./mvnw package -Dnative -Dquarkus.native.container-build=true`)

## Summary

**Configuration changes (`application.properties`).**
Three datasource properties were added under `%prod` and `%docker-dev` profile prefixes:
`quarkus.datasource.jdbc.url`, `quarkus.datasource.username`, `quarkus.datasource.password`.
These reference `CAPSA_DB_URL`, `CAPSA_DB_USER`, `CAPSA_DB_PASSWORD`. Profile prefixes ensure
Quarkus DevServices continues to auto-provision PostgreSQL in dev/test (no URL configured there)
while the explicit URL is used in prod and local Docker runs.

A `%docker-dev` Quarkus profile was introduced to disable OIDC for local Docker smoke testing.
`%docker-dev.quarkus.oidc.enabled=false` parallels `%dev.quarkus.oidc.enabled=false`. This is a
build-time property that must be baked in at package time; it cannot be overridden at runtime via env var.
As a result, the local compose image is built with `-Dquarkus.profile=docker-dev` and the
compose stack sets `QUARKUS_PROFILE=docker-dev` at runtime. The fallback
`quarkus.oidc.auth-server-url=${CAPSA_OIDC_AUTH_SERVER_URL:http://localhost/oidc-disabled}` satisfies
Quarkus's config validation when no OIDC URL is provided.

**`docker-compose.yml` (local dev).**
Defines two services: `postgres:17` and `capsa:latest`. The `postgres` service exposes port 5432
and has a `pg_isready` health check. The `capsa` service depends on `postgres` being healthy.
The `capsa` service uses the `docker-dev` profile (`QUARKUS_PROFILE=docker-dev`) so OIDC is
disabled and the application accepts unauthenticated requests to health endpoints. Build context
is `capsa-runtime` (the Dockerfile copies from `target/quarkus-app/` relative to that directory).

**`docker-compose.prod.yml` (production).**
Production variant: `capsa` port is bound to `127.0.0.1:8080` (reverse proxy upstream only),
`CAPSA_OIDC_AUTH_SERVER_URL` is required, both services restart `unless-stopped`. Secrets are
provided through env vars; `.env` (gitignored) or system env are the intended carriers.

**`.env.example`.** Documents `CAPSA_DB_URL`, `CAPSA_DB_USER`, `CAPSA_DB_PASSWORD`,
`CAPSA_OIDC_AUTH_SERVER_URL`, `CAPSA_OIDC_CLIENT_ID` with descriptions and example values.

**JVM Docker image.**
`Dockerfile.jvm` (pre-existing) targets `ubi9/openjdk-25-runtime:1.24`. Build flow:
1. `./mvnw package -DskipTests -Dquarkus.profile=docker-dev` — bakes in the `docker-dev` build-time flags
2. `docker build -f src/main/docker/Dockerfile.jvm -t capsa:latest .` from `capsa-runtime/`

**Native build.**
`./mvnw package -Dnative -Dquarkus.native.container-build=true -DskipTests` built the native
runner using the Quarkus Mandrel/GraalVM builder image (`quay.io/quarkus/ubi9-quarkus-mandrel-builder-image:jdk-25`)
in approximately 10 minutes. No JPMS reflection configuration was required beyond what Quarkus
provides automatically. The resulting `capsa-runtime-1.0.0-SNAPSHOT-runner` starts in **0.358 seconds**.

## Files Changed

- `capsa-runtime/src/main/resources/application.properties` — added `%prod` and `%docker-dev` datasource
  properties; added `%docker-dev.quarkus.oidc.enabled=false`; changed `quarkus.oidc.auth-server-url`
  fallback from empty string to `http://localhost/oidc-disabled` to satisfy config validation.
- `docker-compose.yml` — new; local dev stack with OIDC disabled via `docker-dev` profile.
- `docker-compose.prod.yml` — new; production reference stack; port bound to 127.0.0.1.
- `.env.example` — new; env var inventory with descriptions.

No capability module files changed. No `capsa-runtime` Java source files changed.

## Evidence

**`application.properties` datasource and OIDC profile config:**

```properties
# Datasource - configured via environment variables in production and docker-dev;
# DevServices auto-provisions PostgreSQL in dev/test (no URL configured there).
%prod.quarkus.datasource.jdbc.url=${CAPSA_DB_URL}
%prod.quarkus.datasource.username=${CAPSA_DB_USER}
%prod.quarkus.datasource.password=${CAPSA_DB_PASSWORD}
%docker-dev.quarkus.datasource.jdbc.url=${CAPSA_DB_URL}
%docker-dev.quarkus.datasource.username=${CAPSA_DB_USER}
%docker-dev.quarkus.datasource.password=${CAPSA_DB_PASSWORD}

# OIDC - production server configured via environment variables.
# The fallback "http://localhost/oidc-disabled" satisfies Quarkus config validation
# when OIDC is disabled at runtime (QUARKUS_OIDC_ENABLED=false).
quarkus.oidc.auth-server-url=${CAPSA_OIDC_AUTH_SERVER_URL:http://localhost/oidc-disabled}
quarkus.oidc.client-id=${CAPSA_OIDC_CLIENT_ID:capsa}

# Dev and test modes disable OIDC; use @TestSecurity in tests.
# The docker-dev profile is for local Docker Compose smoke testing without an OIDC provider.
%dev.quarkus.oidc.enabled=false
%test.quarkus.oidc.enabled=false
%docker-dev.quarkus.oidc.enabled=false
```

**`docker-compose.yml` (key sections):**

```yaml
services:
  postgres:
    image: postgres:17
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U capsa"]

  capsa:
    image: capsa:latest
    build:
      context: capsa-runtime
      dockerfile: src/main/docker/Dockerfile.jvm
    environment:
      CAPSA_DB_URL: jdbc:postgresql://postgres:5432/capsa
      QUARKUS_PROFILE: ${QUARKUS_PROFILE:-docker-dev}
    depends_on:
      postgres:
        condition: service_healthy
```

**Native startup log:**

```
INFO  [io.quarkus] (main) capsa 1.0.0-SNAPSHOT native (powered by Quarkus 3.39.2) started in 0.358s.
INFO  [io.quarkus] (main) Profile docker-dev activated.
INFO  [io.quarkus] (main) Installed features: [agroal, cdi, flyway, hibernate-orm,
      jdbc-postgresql, narayana-jta, oidc, rest, rest-jsonb, security,
      smallrye-context-propagation, smallrye-health, vertx]
```

## Validation

**JVM Docker Compose smoke test:**

```bash
./mvnw package -DskipTests -Dquarkus.profile=docker-dev
docker build -f src/main/docker/Dockerfile.jvm -t capsa:latest capsa-runtime/
docker compose up -d
# wait for health
curl http://localhost:8080/q/health/ready
```

```json
{
    "status": "UP",
    "checks": [
        {
            "name": "Database connections health check",
            "status": "UP",
            "data": {
                "<default>": "UP"
            }
        }
    ]
}
```

```
GET /q/health/live → {"status":"UP","checks":[]}
```

Flyway output (from container logs):

```
Database: jdbc:postgresql://postgres:5432/capsa (PostgreSQL 17.11)
Successfully validated 6 migrations
Current version of schema "public": 006
Schema "public" is up to date. No migration necessary.
```

JVM startup time: **8.594 seconds** (cold start including Flyway validation).

**Native build:**

```bash
./mvnw package -Dnative -Dquarkus.native.container-build=true -DskipTests
```

```
Finished generating 'capsa-runtime-1.0.0-SNAPSHOT-runner' in 8m 13s.
BUILD SUCCESS
```

Native startup time: **0.358 seconds**.

**Full test suite (after all config changes):**

```bash
./mvnw test
```

```
Tests run: 44, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Per-module breakdown unchanged from CAPSA-ARCH-FIX-002 baseline.

## Limitations

- **API smoke test requires OIDC.** `POST /capsa/api/lists` (and all capability endpoints) require a
  Bearer token regardless of profile. In `docker-dev` mode, endpoints reject unauthenticated requests
  with 500 (NPE from `OidcCurrentUser` when principal has no name). This is the same behavior as
  `%dev` mode; a full API smoke test against the Docker stack requires an OIDC provider in compose
  or a self-signed JWT. Adding a lightweight OIDC mock is left as a post-v0.1 concern.
- **`quarkus.oidc.enabled` is build-time only.** Setting `QUARKUS_OIDC_ENABLED=false` at runtime
  has no effect on a packaged JVM or native binary. The `docker-dev` profile must be active at
  `./mvnw package` time for the build-time flag to be baked in.
- **Native image built with `%prod` profile.** The container-build native image uses the default
  prod profile. Running it with `QUARKUS_PROFILE=docker-dev` at runtime produces a warning ("profile
  used to build differs from runtime profile") because the OIDC build-time flag is not set to false
  in the native binary. For a production-ready native image, rebuild with `-Dquarkus.profile=prod`
  and supply a real OIDC auth server URL.
- **Reverse proxy not included.** Caddy or nginx configuration is an operator concern; the compose
  files expose the application on port 8080 (`prod`: `127.0.0.1:8080`, `dev`: `0.0.0.0:8080`).
- **No TLS termination in Capsa.** TLS belongs to the reverse proxy.

## Open Follow-Up

- CAPSA-ARCH-REVIEW-002 findings M-2 through M-6 and L-4 through L-9 remain open (see
  CAPSA-ARCH-REVIEW-002 report for the full list). These are not deployment concerns.
- An OIDC provider in docker-compose (e.g., Keycloak) would enable full end-to-end API smoke tests
  against the Docker stack.

## Related Records

- **Source review:** [CAPSA-ARCH-REVIEW-002](CAPSA-ARCH-REVIEW-002.md)
- **Previous slice:** [S-06-report](S-06-report.md) — Error Model + OIDC Hardening
- **Engineering log:** [ENGINEERING_LOG.md](../../ENGINEERING_LOG.md)
