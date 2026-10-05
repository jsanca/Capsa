# Capsa Server

The Capsa backend is a Quarkus 3 modular monolith organized by business capability. Java 25. JPMS in every module.

The Maven parent is `pom.xml` at this directory. Run `./mvnw` from here, not from the repo root.

```bash
./mvnw quarkus:dev                       # dev mode; Dev UI at http://localhost:8080/q/dev/
./mvnw test                              # all unit tests
./mvnw -pl <module> test                 # one module's tests
./mvnw package                           # build
./mvnw verify -DskipITs=false            # integration tests (skipped by default)
./mvnw package -Dnative                  # native build (GraalVM, or -Dquarkus.native.container-build=true)
```

Java 25 is required (`maven.compiler.release=25`). `./mvnw` bootstraps Maven 3.9.16 but **not** the JDK.

For the developer guide — module layout, JPMS rules, DI conventions, OIDC, persistence — see:

- [AGENTS.md](AGENTS.md) — opencode agent instruction file
- [CLAUDE.md](CLAUDE.md) — Claude Code agent instruction file

Both files carry the same project conventions.

For local container smoke-testing, the repo root provides `docker-compose.yml` (OIDC disabled) and `docker-compose.prod.yml` (production reference). See the repo root [README.md](../README.md) and [`.env.example`](../.env.example) for environment configuration.

## Modules

| Module | Packaging | Purpose |
| --- | --- | --- |
| `capsa-observability` | `jar` | Capability: structured event foundation (transaction-aware dispatch). |
| `capsa-users` | `jar` | Capability: identity provisioning and current-user lookup. |
| `capsa-lists` | `jar` | Capability: semantic list ownership and contribution. |
| `capsa-items` | `jar` | Capability: captured-item persistence (UC-02, UC-05, UC-06). |
| `capsa-capture` | `jar` | Capability: capture intake and resolution (UC-03, UC-04). |
| `capsa-classification` | `jar` | Capability: classification abstraction and pipeline. |
| `capsa-runtime` | `quarkus` | The only module with the Quarkus Maven plugin. Wires capabilities into one deployable artifact (REST resources, OIDC, exception mappers, Flyway, integration tests). |