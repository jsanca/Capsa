# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Start with [docs/PROJECT.md](docs/PROJECT.md) to understand the project, then read [docs/OSK.md](docs/OSK.md) before creating or changing workspace documentation.

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
./mvnw test -Dtest=GreetingResourceTest

# Run integration tests (skipped by default)
./mvnw verify -DskipITs=false

# Native build (requires GraalVM, or use -Dquarkus.native.container-build=true)
./mvnw package -Dnative
```

## Architecture

Capsa is a personal capture-and-classification system. Raw text (or other input) enters as a **Capture**, Capsa classifies it against the user's semantic **Lists**, and produces an **Item** in the correct list. When confidence is low Capsa surfaces the ambiguity to the user instead of silently guessing.

The backend is a **modular monolith** targeting Java 25 + Quarkus 3. The intended layering:

- **Domain** — pure Java, no Quarkus dependency; contains entities (`User`, `List`, `Capture`, `Item`, `Classification`) and use-case logic.
- **Application** — orchestrates use cases; also framework-free where practical.
- **Infrastructure/Adapter** — Quarkus, Jakarta REST, PostgreSQL (Flyway migrations), and classifier providers. Quarkus acts as the runtime and wires infrastructure to the domain.

JPMS module boundaries are intended to enforce this separation, but are not yet in place.

**Classification is an abstraction**, not a provider. The domain must not depend on any specific AI/LLM/rules implementation. The adapter layer owns classifier wiring.

**Planned testing layers** (not yet implemented beyond the scaffold):
- Domain → JUnit unit tests
- Application → use-case tests
- API → Karate contract/integration tests
- Persistence → PostgreSQL tests via Testcontainers

The current codebase is a Quarkus scaffold. Domain implementation begins after v0.1 use cases are refined (see `docs/engineering/agents/intent/intent1.md`).

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

Jakarta REST resources are **exempt** from this convention — their `@Inject` field style is intentional and should not be mechanically rewritten.

Constructor injection makes required dependencies explicit, keeps collaborators immutable after construction, enables direct unit-test construction without CDI or reflection, and makes dependency growth visible at the constructor signature.

## Tool-Specific Instructions

Add only instructions required by Claude here. Keep shared project guidance in `docs/`.
