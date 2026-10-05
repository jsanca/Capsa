# Agent Workspace Guide

Start with [docs/PROJECT.md](docs/PROJECT.md) for project context, then read [docs/OSK.md](docs/OSK.md) before creating or changing workspace documentation. The engineering work index is [docs/engineering/ENGINEERING_LOG.md](docs/engineering/ENGINEERING_LOG.md).

Use canonical project documentation as the authority. Do not place shared project knowledge exclusively in this file.

<!-- OSK:BEGIN -->

## OSK Workspace

Read:

- `docs/PROJECT.md`
- `docs/OSK.md`

<!-- OSK:END -->

## Project

Capsa is a personal capture-and-classification system — Quarkus 3, Java 25, **modular monolith by business capability**. The Maven parent is `pom.xml` at this directory; run `./mvnw` from here (not from the repo root). The Capsa repository root lives at the parent directory and contains product-level OSK workspace, docs, and adjacent client boundaries (`android/`, `ios/`, `web/`, `mcp/`).

Seven capability/runtime modules wired together as one deployable artifact:

- `capsa-observability`, `capsa-users`, `capsa-lists`, `capsa-items`, `capsa-capture`, `capsa-classification` — capability modules (`jar` packaging).
- `capsa-runtime` — the only module with `<packaging>quarkus</packaging>` and the Quarkus Maven plugin. It produces the deployable artifact and wires everything together (Jakarta REST resources, OIDC, exception mappers, Flyway, integration tests).

Architecture and capability contracts are documented in [docs/knowledge/architecture/capsa-arch-001-modular-monolith-v0.1.md](docs/knowledge/architecture/capsa-arch-001-modular-monolith-v0.1.md).

## Module layout and JPMS

Every module is also a JPMS module (`src/main/java/module-info.java`). The pattern:

```text
com.capsa.<capability>.api                              # exports only this
com.capsa.<capability>.internal.domain                  # not exported
com.capsa.<capability>.internal.service                 # opens for CDI
com.capsa.<capability>.internal.persistence.entity     # opens for Hibernate
com.capsa.<capability>.internal.persistence.repository # opens for CDI
```

Rules enforced by JPMS exports/opens:

- Cross-capability collaboration goes through `com.capsa.<other>.api` only — never another capability's repository or service.
- A capability is the sole writer and reader of its own tables. Tables are owned by the capability, not shared.
- `opens` are deliberately targeted (Hibernate entities + CDI proxies); do not blanket-open packages.

`runtime` is an `open module` that requires every capability and the Jakarta/CDI/MicroProfile JWT/SLF4J APIs it touches directly.

## Dependency injection

Constructor injection with `private final` fields is the convention for internal components (services, repositories, strategies). Required collaborators must not be field-injected.

```java
private final ItemRepository itemRepository;
private final ListService listService;

@Inject
public ItemServiceImpl(ItemRepository itemRepository, ListService listService) {
    this.itemRepository = itemRepository;
    this.listService = listService;
}
```

Jakarta REST resources are **exempt** — their `@Inject` field style in `server/capsa-runtime` is intentional and must not be mechanically rewritten.

## Commands

```bash
./mvnw quarkus:dev                       # dev mode; Dev UI at http://localhost:8080/q/dev/
./mvnw test                              # all unit tests
./mvnw test -Dtest=GreetingResourceTest  # single test class
./mvnw -pl <module> test                 # one module's tests
./mvnw package                           # build
./mvnw verify -DskipITs=false            # integration tests (skipped by default)
./mvnw package -Dnative                  # native build (GraalVM, or -Dquarkus.native.container-build=true)
```

Java 25 is required (`maven.compiler.release=25`). `./mvnw` bootstraps Maven 3.9.16 but **not** the JDK.

## Persistence, OIDC, tests

- Database: PostgreSQL via Flyway; Hibernate ORM with `quarkus.hibernate-orm.database.generation=none` (Flyway owns the schema).
- Migrations live only in `server/capsa-runtime/src/main/resources/db/migration/Vxxx__*.sql`.
- OIDC: `quarkus.oidc.auth-server-url=${CAPSA_OIDC_AUTH_SERVER_URL:}` and `quarkus.oidc.client-id=${CAPSA_OIDC_CLIENT_ID:capsa}` — both must be set in production. Disabled in `%dev` and `%test` profiles (`application.properties`).
- Integration tests live in `server/capsa-runtime/src/test/java` and use `@QuarkusTest` + RestAssured + `@TestSecurity` (because OIDC is off in `%test`).
- `skipITs=true` by default in the parent POM. Use `./mvnw verify -DskipITs=false` to run integration tests.
- No CI workflows, pre-commit hooks, or formatter (Spotless/EditorConfig) are present — Java/Maven defaults apply.

## Quarkus CDI indexing

`application.properties` force-indexes the capability JARs for CDI bean and entity discovery (`quarkus.index-dependency.<name>.{group-id,artifact-id}` for each capability). When you add a new capability, mirror that block.

## Tool-Specific Instructions

Add only instructions required by this agent tool here. Keep shared project guidance in `docs/`.

<!-- OSK:SKILLS:BEGIN -->

## OSK Installed Skills

- [Java Engineering Guide](.osk/skills/osk-java-guide/SKILL.md)
<!-- OSK:SKILLS:END -->