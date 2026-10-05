# Capsa

Personal capture & retrieval.

Capsa is a product comprising a Quarkus server and (planned) Android, iOS, Web, and MCP clients. Today only the server is implemented.

## Top-level layout

| Path | Purpose | Status |
| --- | --- | --- |
| `server/` | Quarkus 3 modular monolith (Java 25) | Implemented |
| `android/` | Android client | NOT IMPLEMENTED |
| `ios/` | iOS client | NOT IMPLEMENTED |
| `web/` | Web client | NOT IMPLEMENTED |
| `mcp/` | MCP adapter (independent of `server/`) | NOT IMPLEMENTED |
| `docs/` | Product knowledge, engineering evidence, decisions, roadmap | Active |
| `OSK.md`, `PROJECT.md` | Workspace operating guide and product context | Active |
| `docker-compose.yml`, `docker-compose.prod.yml`, `.env.example` | Local / production reference deployment of `server/` | Active |

## Where to start

- **Product context** — [PROJECT.md](PROJECT.md)
- **Workspace operating guide** — [OSK.md](OSK.md)
- **Engineering work index** — [docs/engineering/ENGINEERING_LOG.md](docs/engineering/ENGINEERING_LOG.md)
- **Server-specific developer guide** — [server/AGENTS.md](server/AGENTS.md) (opencode) / [server/CLAUDE.md](server/CLAUDE.md) (claude code)

## Build and run the server

From `server/`:

```bash
./mvnw test
./mvnw quarkus:dev
./mvnw package
```

For local container smoke-testing from the repo root:

```bash
docker compose -f docker-compose.yml up
```

See `server/AGENTS.md` for the full set of commands and architectural notes.

## Boundaries

`server/`, `android/`, `ios/`, `web/`, and `mcp/` are siblings, not nested. `mcp/` is an independent adapter that talks to Capsa Server over its public contract — it does not depend on server implementation modules or JPA entities.