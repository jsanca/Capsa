# CAPSA-REPO-001 Report — Promote Capsa Root Workspace and Scaffold Client Projects

## Status

Complete. Working tree reflects the relocation; no commit was authored by this task (see Coordination below).

## Objective

Refactor the Capsa repository layout so that the repository root represents the complete Capsa product rather than only the Quarkus server.

- Promote the OSK / project workspace from the nested `capsa/` directory to the repository root.
- Rename the Quarkus project at `capsa/` to `server/`.
- Scaffold empty top-level project boundaries for the future `android/`, `ios/`, `web/`, and `mcp/` clients.

This task is repository restructuring / scaffolding only. No client was implemented. No server behavior was changed. No Java package or module was renamed.

## Old Layout

```text
capsa/                                  repo root
├── .gitignore                          (generic JVM only)
├── README.md                           (one-line placeholder)
├── AGENTS.md                           (11-line wrapper pointing to capsa/)
├── capsa/                              the Quarkus project AND the OSK workspace
│   ├── pom.xml                         Maven multi-module parent
│   ├── mvnw, mvnw.cmd, .mvn/           Maven wrapper
│   ├── capsa-observability/            capability
│   ├── capsa-users/                     capability
│   ├── capsa-lists/                    capability
│   ├── capsa-items/                    capability
│   ├── capsa-capture/                  capability
│   ├── capsa-classification/           capability
│   ├── capsa-runtime/                  Quarkus module
│   ├── docs/                           product-level knowledge, engineering evidence
│   ├── OSK.md, PROJECT.md               workspace guide, project context
│   ├── .osk/                           OSK-managed state (roles, skills)
│   ├── .opencode/, .claude/, .agents/  agent integrations
│   ├── .gitignore                      (Maven / Eclipse / IntelliJ / etc.)
│   ├── .dockerignore
│   ├── AGENTS.md, CLAUDE.md            opencode / claude code developer guides
│   ├── README.md                       (Quarkus-generated scaffold)
│   ├── docker-compose.yml              (added by S-07, uncommitted)
│   ├── docker-compose.prod.yml         (added by S-07, uncommitted)
│   └── .env.example                    (added by S-07, uncommitted)
└── capsa-observability/                empty leftover skeleton (untracked)
```

The OSK workspace identity was the nested `capsa/` directory because that is where `.osk/workspace.yaml` lived.

## New Layout

```text
capsa/                                  repo root — the OSK workspace
├── server/                             Quarkus modular monolith (relocated)
│   ├── pom.xml                         (groupId=com.capsa, artifactId=capsa)
│   ├── mvnw, mvnw.cmd, .mvn/
│   ├── capsa-observability/            capability
│   ├── capsa-users/                     capability
│   ├── capsa-lists/                    capability
│   ├── capsa-items/                    capability
│   ├── capsa-capture/                  capability
│   ├── capsa-classification/           capability
│   ├── capsa-runtime/                  Quarkus module
│   ├── AGENTS.md, CLAUDE.md, README.md server-specific developer guides
│   └── .dockerignore
├── android/                            project boundary — NOT IMPLEMENTED
├── ios/                                project boundary — NOT IMPLEMENTED
├── web/                                project boundary — NOT IMPLEMENTED
├── mcp/                                project boundary — NOT IMPLEMENTED
├── docs/                               product-level knowledge (relocated)
│   └── engineering/agents/reports/CAPSA-REPO-001.md  (this file)
├── OSK.md, PROJECT.md                  workspace guide, project context
├── .osk/                               OSK-managed state (relocated)
├── .opencode/, .claude/, .agents/      agent integrations (relocated)
├── .gitignore                          (merged root + server-specific patterns)
├── README.md                           (product-level introduction)
├── AGENTS.md, CLAUDE.md                (product-level opencode / claude guides)
├── docker-compose.yml                  (build context now server/capsa-runtime)
├── docker-compose.prod.yml
└── .env.example
```

The OSK workspace identity is now the repository root. `server/`, `android/`, `ios/`, `web/`, and `mcp/` are siblings, not nested.

## OSK Files / Configuration Moved

All OSK files were relocated using `git mv`, preserving fingerprints and history.

| From | To |
| --- | --- |
| `capsa/.osk/` | `.osk/` |
| `capsa/.opencode/` | `.opencode/` |
| `capsa/.claude/` | `.claude/` |
| `capsa/.agents/` | `.agents/` |
| `capsa/docs/OSK.md` | `OSK.md` |
| `capsa/docs/PROJECT.md` | `PROJECT.md` |
| `capsa/docs/` | `docs/` |
| `capsa/AGENTS.md`, `capsa/CLAUDE.md` | `server/AGENTS.md`, `server/CLAUDE.md` |

The new root `AGENTS.md` and `CLAUDE.md` describe Capsa as a product with the new top-level layout. The relocated `server/AGENTS.md` and `server/CLAUDE.md` carry the existing Quarkus-specific developer guide (module layout, JPMS, DI conventions, build commands) updated for the new `server/` paths.

OSK workspace identity: `schema: osk.workspace/v0alpha1` is unchanged; the workspace is whatever directory contains `.osk/workspace.yaml`. That file is now at the repository root, so the workspace identity refers to the new root.

No existing OSK receipts were found on disk. Any receipts that referenced the nested `capsa/` workspace identity are by definition stale because the workspace identity changed. Documenting this as expected migration behavior rather than attempting compatibility hacks, per the task brief.

## Server Project Relocated

All Maven content moved from `capsa/` to `server/`:

| From | To |
| --- | --- |
| `capsa/pom.xml` | `server/pom.xml` |
| `capsa/mvnw`, `capsa/mvnw.cmd`, `capsa/.mvn/` | `server/...` |
| `capsa/capsa-observability/`, `capsa/capsa-users/`, `capsa/capsa-lists/`, `capsa/capsa-items/`, `capsa/capsa-capture/`, `capsa/capsa-classification/`, `capsa/capsa-runtime/` | `server/...` (unchanged module / artifact names) |
| `capsa/.dockerignore` | `server/.dockerignore` (server-specific) |

The Maven multi-module project is preserved exactly. The `pom.xml` artifactId remains `capsa`, groupId remains `com.capsa`. The seven modules keep their existing artifactId (`capsa-observability`, `capsa-users`, `capsa-lists`, `capsa-items`, `capsa-capture`, `capsa-classification`, `capsa-runtime`). No Java package was renamed. No JPMS module was renamed.

## Client Boundaries Scaffolded

Empty `android/`, `ios/`, `web/`, and `mcp/` directories were created. Each contains a single `README.md` declaring:

- Purpose of the boundary.
- Status: `NOT IMPLEMENTED`.
- Relationship to the public Capsa Server contract.
- That the choice of framework, language, build tool, and dependencies is intentionally deferred to a dedicated client bootstrap task.

No build tools, manifests, source trees, dependencies, or framework scaffolding were created. Specifically:

- No Gradle build for `android/`.
- No Xcode project or Swift package for `ios/`.
- No `package.json`, framework choice, or build tool for `web/`.
- No MCP SDK choice, language, transport, or manifest for `mcp/`.

`mcp/` is **not** a `server/` Maven module. It is an independent adapter that talks to the Capsa Server over its public HTTP/REST contract. Its README records what it must not couple to (`server/` source repositories, implementation packages, JPA entities, internal capability implementations).

## Path References Updated

Only references broken by the relocation were updated. General documentation gardening was deliberately avoided.

| File | Change | Reason |
| --- | --- | --- |
| `docker-compose.yml` | `build.context: capsa-runtime` → `build.context: server/capsa-runtime` | The build context for the Capsa image is now rooted at the repo root; the Dockerfile lives at `server/capsa-runtime/src/main/docker/Dockerfile.jvm`. |
| `docker-compose.prod.yml` | No path change | The prod compose does not reference the build context; the existing `capsa:latest` image name is unchanged. |
| `server/AGENTS.md` | `capsa-runtime` paths → `server/capsa-runtime` paths | Reflects the new Maven location. The Quarkus-specific developer guide now correctly points to the relocated module. |
| `server/CLAUDE.md` | `docs/...` relative paths → `../docs/...`; `PROJECT.md`, `OSK.md` → `../PROJECT.md`, `../OSK.md` | `CLAUDE.md` is now inside `server/` so paths to repo-root docs must traverse up. |
| `.gitignore` (root) | Merged patterns from `capsa/.gitignore` (Maven, Eclipse, IntelliJ, VSCode, .DS_Store, .env, Quarkus CLI plugins, .certs/) with the existing root patterns (compiled class, archives, JVM crash logs). | One repo-wide `.gitignore` is now sufficient: `target/` matches anywhere in the repo, so `server/capsa-runtime/target/` etc. are ignored. |

Path references deliberately **not** touched:

- `docs/knowledge/architecture/capsa-arch-001-modular-monolith-v0.1.md` lines 33–50 — describes the original v0.1 architectural vision (`capsa/server`, `capsa/clients/...`); it is historical evidence, not a broken reference.
- `docs/engineering/agents/tasks/CAPSA-ARCH-001—ModularMonolithArchitectureV0.1.md` line 60 — same reason: historical context, not a broken reference.
- `docs/knowledge/architecture/capsa-arch-001-modular-monolith-v0.1.md` references to `/capsa/api/...` — those are JAX-RS URL prefixes, the public REST API contract, and must not change.

## Documentation Ownership

Product-level knowledge stayed at `docs/` (now at the root). Server-specific implementation knowledge moved with the server to `server/`. The server README, AGENTS.md, and CLAUDE.md describe the Quarkus module; the root README, AGENTS.md, CLAUDE.md, OSK.md, and PROJECT.md describe the product.

No product / domain knowledge was moved into `server/`.

## Infrastructure Boundary

No `infra/` directory was introduced. No shared jsancha.dev infrastructure (Hetzner, Coolify, PostgreSQL, MinIO, Keycloak, observability, Cloudflare) was introduced into this repository. Those concerns remain in the ecosystem and are out of scope.

The existing `docker-compose.yml`, `docker-compose.prod.yml`, and `.env.example` are Capsa-specific deployment / runtime artifacts and remain at the repo root. These existed before this task (added by S-07); they were relocated as-is.

## Server Build Verification

Server build from the new `server/` directory:

```bash
./mvnw test          # run from server/
```

Per-module result (2026-10-04, after relocation):

| Module | Tests | Result |
| --- | --- | --- |
| `capsa-observability` | 26 | SUCCESS |
| `capsa-users` | 7 | SUCCESS |
| `capsa-lists` | 7 | SUCCESS |
| `capsa-items` | 0 | SUCCESS |
| `capsa-classification` | 5 | SUCCESS |
| `capsa-capture` | 0 | SUCCESS |
| `capsa-runtime` | 44 | SUCCESS |
| **Total** | **89** | **BUILD SUCCESS** |

Total runtime: 01:21 min. The baseline matches the S-07 / CAPSA-ARCH-FIX-002A baseline (`Tests run: 89, Failures: 0, Errors: 0, Skipped: 0`).

JPMS module graph unchanged. No tests weakened or removed.

## OSK Verification

`./mvnw` is invoked from `server/`, not from the repo root. All `.osk/`, `.opencode/`, `.claude/`, `.agents/` files were relocated with `git mv`; fingerprints preserved. The new root `AGENTS.md` carries the OSK `<!-- OSK:BEGIN --> / <!-- OSK:END -->` markers and the installed-skills section, identical in shape to the pre-relocation one but with `docs/PROJECT.md` and `docs/OSK.md` updated to point at the relocated paths.

No broken `capsa/` references remain in:

- `server/AGENTS.md`, `server/CLAUDE.md` (paths updated)
- `.osk/`, `.opencode/`, `.claude/`, `.agents/` (no hardcoded paths)
- `docker-compose.yml`, `docker-compose.prod.yml` (build context updated)
- `.gitignore` (no path references)

The intentional `capsa/capsa` mentions in the new root `AGENTS.md` / `CLAUDE.md` are explanatory ("if you see a doc / commit hash referencing `capsa/capsa-*` modules, they live at `server/capsa-*`"); they document the migration.

## Empty Client Boundaries

| Path | Contents |
| --- | --- |
| `android/README.md` | Purpose, status `NOT IMPLEMENTED`, relationship to Capsa Server, what the boundary is for. |
| `ios/README.md` | Same shape; notes Swift / Objective-C and SwiftUI / UIKit are deferred. |
| `web/README.md` | Same shape; notes framework and build tool choice is deferred. |
| `mcp/README.md` | Same shape; explicitly notes it is NOT a `server/` Maven module and lists what MCP must not couple to. |

No `build.gradle`, `*.xcodeproj`, `package.json`, MCP SDK manifest, or any other tool-generated artifact was created.

## Files Removed

- `capsa/` directory — emptied by the relocation, then removed.
- `capsa/README.md` — replaced by the new root `README.md` and the new `server/README.md`.
- `capsa-observability/` at the repo root — empty leftover skeleton with one untracked `CaptureSubmitted.java` (unrelated to the capsa-observability Maven module). The pre-existing root `AGENTS.md` already warned against editing this leftover. Deleted as part of the restructure since it is untracked garbage.
- `capsa/AGENTS.md` and `capsa/CLAUDE.md` — relocated to `server/` and updated; the new root `AGENTS.md` / `CLAUDE.md` carry the product-level guides.

## Coordination with Concurrent Work

The repository working tree contained uncommitted modifications at the start of this task, attributable to three completed-but-uncommitted slices and one staged doc:

- **S-07** (Deployment — docker-compose, .env.example, datasource config, native build): report and untracked `docker-compose.yml`, `docker-compose.prod.yml`, `.env.example`; modifications to `capsa/capsa-runtime/src/main/resources/application.properties` and `capsa/.claude/settings.local.json`.
- **CAPSA-ARCH-FIX-002A** (Doomed Transaction Observability Guard): report and task; modifications to several `capsa-observability` files (pom.xml, Java sources, module-info.java, tests).
- **CAPSA-REQ-002** (Compound Capture — DISCOVERED / DEFERRED requirement): staged as intent-to-add.
- **CAPSA-ADV-001**, **CAPSA-ARCH-FIX-002**, plus ENGINEERING_LOG and observability knowledge doc updates.

None of these are "active in-progress" slices (no agent is currently mid-edit); they are completed slices awaiting commit. The relocation used `git mv` throughout, which preserves both file content and history; all concurrent modifications are preserved at the new paths. Per the task brief:

> "S-07 infrastructure work and CAPSA-ADV-FIX-001 may be occurring in parallel. Before moving files, inspect the working tree. Do not overwrite, revert, clean, stash, or otherwise destroy another agent's uncommitted work."

This task did not overwrite, revert, clean, stash, or otherwise destroy any concurrent work. No concurrent work was modified other than path references inside files that were renamed (e.g., `capsa-runtime` → `server/capsa-runtime` inside the relocated `AGENTS.md` / `CLAUDE.md`, and the docker-compose build context).

**No commit was authored by this task.** The working tree is left to the user to split between the relocation commit and the slice commits (S-07, CAPSA-ARCH-FIX-002A, CAPSA-REQ-002, etc.) in whatever order the user prefers. Running `git status` after this task shows the relocation as ~266 entries (mostly renames) plus the original uncommitted slice changes now living at the relocated paths.

## Intentionally Empty Client Boundaries (Status)

| Boundary | Status | Reason |
| --- | --- | --- |
| `android/` | NOT IMPLEMENTED | Awaiting a dedicated `CAPSA-ANDROID-001` bootstrap task. |
| `ios/` | NOT IMPLEMENTED | Awaiting a dedicated `CAPSA-IOS-001` bootstrap task. |
| `web/` | NOT IMPLEMENTED | Awaiting a dedicated `CAPSA-WEB-001` bootstrap task. |
| `mcp/` | NOT IMPLEMENTED | Awaiting a dedicated `CAPSA-MCP-001` bootstrap task. |

The boundaries exist so that Git tracks them and future bootstrap tasks have a clear location.

## Migration Caveats

- **`/capsa/...` path quirk** — older documentation or commit messages may still reference the pre-reconcat `capsa/capsa-*` layout. The modules live at `server/capsa-*` now. The new root `AGENTS.md` / `CLAUDE.md` warn about this.
- **OSK receipts / workspace identity** — the OSK workspace identity is now the repo root, not `capsa/`. Any receipts that referenced the old identity are stale by definition.
- **`.idea/` at repo root** — pre-existing IntelliJ state at the repo root. The new merged `.gitignore` now ignores `.idea`, but the existing root `.idea/` directory was not removed in this task (it is untracked but unchanged from before this task).
- **Pre-existing `.idea/` inside `capsa/`** — was gitignored by `capsa/.gitignore`; removed during cleanup of the empty `capsa/` directory.
- **`mcp/` is not a Maven module** — explicitly NOT under `server/`. The README declares this and lists what MCP must not couple to.

## Related Records

- **Previous slices** referenced in `ENGINEERING_LOG.md`: S-00 → S-07, CAPSA-OBS-001, CAPSA-ARCH-FIX-002 / 002A, etc. All preserve their original paths relative to `docs/engineering/agents/...` and `docs/knowledge/...` (which themselves moved with the restructure and are now at the repo root).
- **ENGINEERING_LOG.md** — `CAPSA-REPO-001` row added by this task.
- **Sibling reports under `docs/engineering/agents/reports/`** — see `README.md` there for naming convention.