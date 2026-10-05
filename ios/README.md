# Capsa — iOS Client

## Status

**NOT IMPLEMENTED.**

This directory is an empty project boundary. The iOS client has not been started. The choice of language (Swift / Objective-C), UI framework (SwiftUI / UIKit), project layout, and dependencies is intentionally deferred to a dedicated client bootstrap task.

## Relationship to Capsa Server

When implemented, this client will consume the public Capsa Server contract over its HTTP/REST API. It must not depend on `server/` implementation modules, JPA entities, or internal capability code.

## What this boundary is for

This directory exists so that Git tracks the boundary as a top-level sibling of `server/`, `android/`, `web/`, and `mcp/`, and so a future iOS bootstrap task has a clear location to land. No Xcode project, Swift package, or source tree has been created.