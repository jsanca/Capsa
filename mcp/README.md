# Capsa — MCP Adapter

## Status

**NOT IMPLEMENTED.**

This directory is an empty project boundary. The MCP (Model Context Protocol) adapter has not been started. The choice of language / SDK, transport (stdio / HTTP / SSE), and dependencies is intentionally deferred to a dedicated MCP bootstrap task.

## Relationship to Capsa Server

The MCP adapter is **not** a `server/` Maven module. It is an independent Capsa client boundary that talks to the Capsa Server over its **public** contract — HTTP/REST against the documented API.

The intended conceptual relationship is:

```
AI client / agent
        │
       MCP
        │
        ▼
  public Capsa contract
        │
        ▼
    Capsa Server
```

## What MCP must not do

When implemented, this adapter must **not** couple to:
- `server/` source repositories
- `server/` implementation packages
- JPA entities
- internal capability implementations

It is a pure external consumer of the public Capsa contract.

## What this boundary is for

This directory exists so that Git tracks the boundary as a top-level sibling of `server/`, `android/`, `ios/`, and `web/`, and so a future MCP bootstrap task has a clear location to land. No SDK choice, manifest, source tree, or server-binding wiring has been created.