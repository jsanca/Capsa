# Capsa — Web Client

## Status

**NOT IMPLEMENTED.**

This directory is an empty project boundary. The web client has not been started. The choice of framework (React / Vue / Svelte / Solid / etc.), build tool (Vite / Webpack / Turbopack / etc.), language, and dependencies is intentionally deferred to a dedicated client bootstrap task.

## Relationship to Capsa Server

When implemented, this client will consume the public Capsa Server contract over its HTTP/REST API. It must not depend on `server/` implementation modules, JPA entities, or internal capability code.

## What this boundary is for

This directory exists so that Git tracks the boundary as a top-level sibling of `server/`, `android/`, `ios/`, and `mcp/`, and so a future web bootstrap task has a clear location to land. No `package.json`, source tree, or framework scaffolding has been created.