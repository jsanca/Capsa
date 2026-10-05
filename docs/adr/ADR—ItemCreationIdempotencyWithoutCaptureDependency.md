# ADR — Item Creation Idempotency Without a Capture Module Dependency

**Date:** 2026-10-02  
**Status:** Accepted  
**Deciders:** Engineering  
**Context:** S-03 (Items module), S-05 (Capture module)

---

## Context

`Item.createFromCapture` must record which Capture produced the Item so that:

1. UC-03 can enforce idempotency — calling the flow twice for the same Capture must not create a second Item.
2. The classification pipeline can retrieve evidence tied to the Item.

A natural type for this would be a `CaptureId` value record living in `capsa.capture.api`.

However, the resolved module graph is:

```
items → {lists, users}
capture → {classification, items, lists, users}
```

`capsa.items` already depends on `capsa.capture` indirectly through the build order, but a **compile-time `requires capsa.capture`** in `module-info.java` of `capsa.items` would invert the allowed direction — `capture → items` and `items → capture` — creating a **cycle** that JPMS forbids.

---

## Decision

`Item.createFromCapture` accepts a raw `UUID captureId` parameter rather than a `CaptureId` typed record.

A `UNIQUE` database constraint on `items.capture_id` enforces correctness independently of the type system.

```java
// capsa.items — no import of anything from capsa.capture
Item createFromCapture(UserId owner, ListId list, UUID captureId, String name, String notes);
```

The Items module never declares `requires capsa.capture` in its `module-info.java`. The module graph remains acyclic.

---

## Consequences

**Positive**
- Module graph stays acyclic; JPMS boundary is enforced at compile time.
- The DB UNIQUE constraint provides a hard correctness guarantee regardless of call site.
- `capsa.items` remains independently testable with no Capture infrastructure.

**Negative**
- The `captureId` parameter is a weakly typed `UUID`; callers can pass any UUID without a compile-time hint.
- The semantic contract ("this UUID must identify a Capture") is expressed only in the method name, Javadoc, and DB constraint — not the type.

---

## Alternatives Considered

### A. Use `CaptureId` from `capsa.capture.api`

`capsa.items` declares `requires capsa.capture`. This creates a compile-time cycle (`capture → items → capture`). JPMS rejects cyclic module graphs. **Rejected.**

### B. Extract `CaptureId` into a shared `capsa.shared` module

Introduces a new module for a single record. The module graph gains an extra vertex with no domain meaning. Premature abstraction for v0.1. **Rejected.**

### C. DB constraint only, no application-layer check

Relying solely on a DB unique-constraint violation means the application must parse a `SQLException` to distinguish idempotency from other errors. Application-layer pre-check in `CaptureResolutionService` makes the intent explicit and gives a clean error path. **Rejected** as the sole mechanism; the DB constraint is retained as the safety net.

---

## Relationship to Architecture

This decision is reflected in:

- `docs/knowledge/architecture/capsa-arch-001-modular-monolith-v0.1.md` §3 (Items public surface), §12 (Transactions — idempotency enforcement)
- `docs/knowledge/engineering-plan/capsa-plan-001-v0.1.md` S-03 (Items) and S-05 (Capture)
