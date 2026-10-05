# Capsa — Android Client

## Status

**NOT IMPLEMENTED.**

This directory is an empty project boundary. The Android client has not been started. The choice of language, framework, build tool, architecture, and dependencies is intentionally deferred to a dedicated client bootstrap task.

## Relationship to Capsa Server

When implemented, this client will consume the public Capsa Server contract over its HTTP/REST API. It must not depend on `server/` implementation modules, JPA entities, or internal capability code.

## What this boundary is for

This directory exists so that Git tracks the boundary as a top-level sibling of `server/`, `ios/`, `web/`, and `mcp/`, and so a future Android bootstrap task has a clear location to land. No Gradle build, source tree, manifest, or dependencies have been created.