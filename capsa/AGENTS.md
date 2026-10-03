# Agent Workspace Guide

Start with [docs/PROJECT.md](docs/PROJECT.md) to understand the project, then read [docs/OSK.md](docs/OSK.md) before creating or changing workspace documentation. `docs/OSK.md` explains documentation placement; read the relevant folder README for local guidance.

Use canonical project documentation as the authority. Do not place shared project knowledge exclusively in this file.

<!-- OSK:BEGIN -->

## OSK Workspace

Read:

- `docs/PROJECT.md`
- `docs/OSK.md`

<!-- OSK:END -->

## Project

Capsa is a personal capture-and-classification system (Quarkus 3, Java 25, modular monolith). The Maven project lives in this `capsa/` subdirectory, not the repo root — run `./mvnw` from here.

The codebase is currently only a Quarkus scaffold (`GreetingResource`). Domain implementation has not started: the v0.1 domain model is a draft under `docs/knowledge/domain/capsa-domain-model-v0.1.md` awaiting engineering review (see `docs/engineering/ENGINEERING_LOG.md`).

Intended layering (not yet enforced): **domain** is pure Java with no Quarkus/JPA/provider dependency; **application** orchestrates use cases; **infrastructure** (Quarkus, Jakarta REST, PostgreSQL/Flyway, classifier providers) is wired by Quarkus. Classification is a capability/abstraction, never a concrete provider. JPMS boundaries are intended but not yet in place.

## Commands

```bash
./mvnw quarkus:dev                       # dev mode; Dev UI at http://localhost:8080/q/dev/
./mvnw test                              # unit tests
./mvnw test -Dtest=GreetingResourceTest  # single test class
./mvnw package                           # build
./mvnw verify -DskipITs=false            # integration tests (skipped by default)
./mvnw package -Dnative                  # native build (GraalVM, or -Dquarkus.native.container-build=true)
```

Java 25 is required (`maven.compiler.release=25`); `./mvnw` bootstraps Maven 3.9.16 but not the JDK.

## Tool-Specific Instructions

Add only instructions required by this agent tool here. Keep shared project guidance in `docs/`.

<!-- OSK:SKILLS:BEGIN -->

## OSK Installed Skills

- [Java Engineering Guide](.osk/skills/osk-java-guide/SKILL.md)
<!-- OSK:SKILLS:END -->
