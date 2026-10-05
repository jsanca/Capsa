# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Start with [PROJECT.md](../PROJECT.md) to understand the project, then read [OSK.md](../OSK.md) before creating or changing workspace documentation. The product-level engineering work index is [docs/engineering/ENGINEERING_LOG.md](../docs/engineering/ENGINEERING_LOG.md).

Use canonical project documentation as the authority. Do not place shared project knowledge exclusively in this file.

<!-- OSK:BEGIN -->

## OSK Workspace

Read:

- `docs/PROJECT.md`
- `docs/OSK.md`

<!-- OSK:END -->

## Commands

```bash
# Dev mode with live reload (Dev UI at http://localhost:8080/q/dev/)
./mvnw quarkus:dev

# Build
./mvnw package

# Run all unit tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=CaptureResourceTest

# Run one module's tests
./mvnw -pl capsa-capture test

# Run integration tests (skipped by default)
./mvnw verify -DskipITs=false

# Native build (requires GraalVM, or use -Dquarkus.native.container-build=true)
./mvnw package -Dnative
```

Java 25 is required (`maven.compiler.release=25`). `./mvnw` bootstraps Maven 3.9.16 but **not** the JDK.

## Architecture

Capsa is a personal capture-and-classification system. Raw text (or other input) enters as a **Capture**, Capsa classifies it against the user's semantic **Lists**, and produces an **Item** in the correct list. When confidence is low Capsa surfaces the ambiguity to the user instead of silently guessing.

The backend is a **modular monolith** (Java 25 + Quarkus 3) organized by business capability. See [docs/knowledge/architecture/capsa-arch-001-modular-monolith-v0.1.md](../docs/knowledge/architecture/capsa-arch-001-modular-monolith-v0.1.md) for the current architecture reference.

Seven modules form one deployable artifact:

- `capsa-observability`, `capsa-users`, `capsa-lists`, `capsa-items`, `capsa-capture`, `capsa-classification` — capability modules (`jar` packaging).
- `capsa-runtime` — the **only** module with `<packaging>quarkus</packaging>` and the Quarkus Maven plugin. It produces the deployable artifact and wires everything together (Jakarta REST resources, OIDC, exception mappers, Flyway, integration tests).

**Classification is an abstraction**, not a provider. The domain must not depend on any specific AI/LLM/rules implementation. The adapter layer owns classifier wiring.

## Module Layout and JPMS

Every module is also a JPMS module (`src/main/java/module-info.java`). The package pattern:

```text
com.capsa.<capability>.api                              # exported: public contract
com.capsa.<capability>.internal.domain                  # not exported
com.capsa.<capability>.internal.service                 # opens for CDI
com.capsa.<capability>.internal.persistence.entity     # opens for Hibernate
com.capsa.<capability>.internal.persistence.repository # opens for CDI
```

Cross-capability collaboration goes through `com.capsa.<other>.api` only — never another capability's repository or service implementation. A capability is the sole owner of its own tables. `opens` are deliberately targeted; do not blanket-open packages.

`capsa-runtime` is an `open module` that requires every capability and the Jakarta/CDI/MicroProfile JWT/SLF4J APIs it touches directly.

## Dependency Injection

Prefer constructor injection for required dependencies in internal components (services, repositories, strategies, and similar long-lived collaborators).

Required collaborators must be `private final`.

```java
private final ItemRepository itemRepository;
private final ListService listService;

@Inject
public ItemServiceImpl(ItemRepository itemRepository, ListService listService) {
    this.itemRepository = itemRepository;
    this.listService = listService;
}
```

Jakarta REST resources are **exempt** from this convention — their `@Inject` field style in `capsa-runtime/` is intentional and should not be mechanically rewritten.

## Local Docker Run (docker-dev)

`docker-compose.yml` at the repo root builds and runs the full stack with PostgreSQL. OIDC is disabled in the `docker-dev` profile (the default).

```bash
# From the repo root — build JVM image, then start postgres + capsa
docker build -t capsa:latest -f server/capsa-runtime/src/main/docker/Dockerfile.jvm server/capsa-runtime
docker compose up

# Rebuild and restart after code changes
./mvnw -f server/pom.xml package -DskipTests && docker compose up --build

# To test with a real OIDC provider
# QUARKUS_PROFILE=prod CAPSA_OIDC_AUTH_SERVER_URL=https://... docker compose up
```

Copy `.env.example` → `.env` for environment variable overrides. The JVM image is in `capsa-runtime/src/main/docker/Dockerfile.jvm`; a native variant (`Dockerfile.native`) requires GraalVM or `-Dquarkus.native.container-build=true`.

## Observability

Capability services emit business events via the `Observability` port (`com.capsa.observability.api.Observability`). The port has one method: `void emit(ObservabilityEvent event)` — it returns immediately and never throws.

```java
observability.emit(new ListCreatedEvent(UUID.randomUUID(), Instant.now(),
    new EventActor.User(userId), listId));
```

Emission is transaction-aware: events are buffered and dispatched only after the enclosing transaction commits. Events raised in a rolled-back transaction are silently discarded. The current sink is SLF4J on logger `capsa.observability`; the adapter in `capsa-runtime` can be swapped without touching capability code.

See [docs/knowledge/observability/capsa-obs-001-observability-event-foundation.md](../docs/knowledge/observability/capsa-obs-001-observability-event-foundation.md) for the full event contract.

## Persistence and OIDC

- Database: PostgreSQL via Flyway; Hibernate ORM with `quarkus.hibernate-orm.database.generation=none` (Flyway owns the schema).
- Migrations live only in `capsa-runtime/src/main/resources/db/migration/Vxxx__*.sql`.
- OIDC: `quarkus.oidc.auth-server-url=${CAPSA_OIDC_AUTH_SERVER_URL:}` and `quarkus.oidc.client-id=${CAPSA_OIDC_CLIENT_ID:capsa}` — both must be set in production. Both are disabled in `%dev` and `%test` profiles.
- Integration tests live in `capsa-runtime/src/test/java` and use `@QuarkusTest` + RestAssured + `@TestSecurity` (OIDC is off in `%test`).

## Adding a New Capability

When adding a new capability module, mirror the CDI force-indexing block in `application.properties`:

```properties
quarkus.index-dependency.<name>.group-id=com.capsa
quarkus.index-dependency.<name>.artifact-id=capsa-<name>
```

Without this, Quarkus will not discover the capability's beans and entities.

## Tool-Specific Instructions

Add only instructions required by Claude here. Keep shared project guidance in `docs/`.
