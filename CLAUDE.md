# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Start with [PROJECT.md](PROJECT.md) to understand the product, then read [OSK.md](OSK.md) before creating or changing workspace documentation. The engineering work index is [docs/engineering/ENGINEERING_LOG.md](docs/engineering/ENGINEERING_LOG.md).

Use canonical project documentation as the authority. Do not place shared product knowledge exclusively in this file.

This is the **Capsa product** repository. The Capsa Server (Quarkus modular monolith) lives under [`server/`](server/) with its own Quarkus-specific guide ([server/CLAUDE.md](server/CLAUDE.md)). Each adjacent client boundary ([`android/`](android/), [`ios/`](ios/), [`web/`](web/), [`mcp/`](mcp/)) has a README declaring its status and relationship to the server.

<!-- OSK:BEGIN -->

## OSK Workspace

Read:

- `PROJECT.md`
- `OSK.md`

<!-- OSK:END -->

## Top-level layout

```text
capsa/
├── server/   Quarkus 3 modular monolith — the only currently-implemented client boundary
├── android/  project boundary — NOT IMPLEMENTED
├── ios/      project boundary — NOT IMPLEMENTED
├── web/      project boundary — NOT IMPLEMENTED
├── mcp/      project boundary — independent MCP adapter (NOT IMPLEMENTED)
├── docs/     product-level knowledge, engineering evidence, decisions, roadmap
├── OSK.md    workspace operating guide
└── PROJECT.md
```

`server/`, `android/`, `ios/`, `web/`, and `mcp/` are siblings, not nested. `docs/`, `.osk/`, `.opencode/`, `.claude/`, `.agents/`, `OSK.md`, and `PROJECT.md` are product-level — they belong to the workspace as a whole and are not owned by any single boundary.

## Server module anatomy

`server/` is a Maven multi-module project (parent `server/pom.xml`, groupId `com.capsa`, artifactId `capsa`). From `server/` run `./mvnw`. See [server/CLAUDE.md](server/CLAUDE.md) for module layout, JPMS, DI conventions, and build/test commands.

## Client boundaries

`android/`, `ios/`, `web/`, and `mcp/` are **project boundaries, not implementations**. Each contains a README declaring purpose, status (`NOT IMPLEMENTED`), and its intended relationship to the public Capsa Server contract. No build tools, dependencies, or source trees have been created.

`mcp/` is **not** a `server/` Maven module. It is an independent adapter that talks to the Capsa Server over its public contract (HTTP/REST) — it does not depend on server implementation modules, JPA entities, or internal capability code.

## Watch out

- **No CI workflows, pre-commit hooks, or formatter** (Spotless / EditorConfig) are configured at this repo. Java / Maven / Docker defaults apply.
- **No `infra/`** directory. The Capsa repo intentionally does not carry Hetzner, Coolify, shared PostgreSQL / MinIO / Keycloak / observability infrastructure, or ecosystem Cloudflare configuration. Those belong to the jsancha.dev ecosystem and are out of scope for this repository.
- **No `/capsa/...` path quirk**: the pre-reconcat `capsa/capsa` layout is no longer the source of truth. If you see a doc, skill, or commit hash referencing `capsa/capsa-*` modules, the underlying modules live at `server/capsa-*`.

## Tool-Specific Instructions

Add only instructions required by Claude here. Keep shared product guidance in `docs/`.