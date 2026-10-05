# CAPSA-ARCH-001 — Capsa Modular Monolith Architecture v0.1

## Role

Use the **Product Architect** role and the applicable architecture/boundary skills.

This is an architecture design task.

Do not implement production code.

## Objective

Design Capsa's initial server architecture from the reconciled product intent, use cases, and domain model.

The target is a:

> Java 25 / Quarkus modular monolith organized primarily by business capability, with JPMS enforcing meaningful module boundaries.

The architecture must support UC-01 through UC-06 without designing future features prematurely.

The principal question is no longer which technologies to select.

The principal question is:

> **Which capability owns each responsibility, what does it expose, and who may depend on whom?**

The resulting architecture must be concrete enough to support an Engineering Plan and implementation slices after review.

---

# Required Reading

Read the current source-of-truth Capsa documentation before designing.

At minimum:

- product intent;
- UC-01 through UC-06;
- reconciled `capsa-domain-model-v0.1.md`;
- `CAPSA-DOMAIN-001`;
- `CAPSA-DOMAIN-REVIEW-001`;
- `CAPSA-DOMAIN-RECONCILE-001`;
- related review and reconciliation reports.

Locate artifacts using actual repository paths.

Use the reconciled domain model and use cases as authoritative input.

Do not silently reopen reconciled domain decisions unless architecture exposes a concrete contradiction.

If a contradiction is discovered, document it rather than silently redesigning the domain.

---

# Repository Context

Capsa is intended as a monorepo approximately shaped as:

```text
capsa/
├── server/
├── clients/
│   ├── android/
│   ├── ios/
│   └── desktop/       # optional/future
├── deployment/
└── docs/
```

This task focuses primarily on `server/`.

Client architecture is out of scope except where a server boundary must accommodate clients.

---

# Architectural Style

Prefer a **modular monolith organized by capability** rather than generic horizontal modules such as:

```text
domain
application
infrastructure
```

Candidate server capabilities currently include:

```text
users
lists
capture
classification
items
runtime
```

These names and boundaries are hypotheses.

Validate them against behavior and dependencies.

Merge, split, or rename a capability only when there is a concrete semantic or dependency reason.

Avoid vague module names such as:

```text
core
common
util
shared
service
infrastructure
```

unless a specific responsibility genuinely justifies one.

---

# JPMS

Use Java Platform Module System boundaries as an architectural enforcement mechanism.

A module should expose only its intended public surface.

Conceptually:

```text
capsa.items
├── public API
└── internal implementation
```

with:

```java
module capsa.items {
    exports capsa.items.api;
}
```

as the general direction.

Internal service implementations, repositories, persistence entities, converters, and implementation details should not be exported unless architecture demonstrates a need.

The exact package names are part of this design task.

Do not write production `module-info.java` files yet.

---

# Fundamental Cross-Module Rule

A capability must never access another capability through its repository or persistence implementation.

For example:

```text
CaptureService ──► ItemService             VALID

CaptureService ──► ItemRepository          INVALID
```

Cross-capability collaboration occurs through the other capability's public service/port.

JPMS should make invalid access structurally difficult or impossible.

---

# Capability Anatomy

Evaluate a capability structure approximately like:

```text
items/
├── api/
│   ├── public models
│   └── public service/port
│
└── internal/
    ├── domain/
    ├── service/
    └── persistence/
        ├── entity/
        ├── repository/
        └── converter/
```

This is a design hypothesis, not mandatory directory structure.

Determine whether it works consistently for the Capsa capabilities.

Do not introduce layers merely for symmetry when a capability does not need them.

---

# Public Services / Ports

A capability's public service represents its supported operations to other modules.

Other modules should depend on semantic operations rather than another module's storage representation.

For example:

```text
capture
   │
   ├── ClassificationService
   └── ItemService
```

rather than:

```text
capture
   │
   ├── ClassificationRepository
   └── ItemRepository
```

Determine the minimal public surface required for UC-01 through UC-06.

Avoid creating public interfaces merely because interfaces are architecturally fashionable.

---

# Service Responsibilities

Services are the primary use-case/business orchestration boundary.

A service may be responsible for:

- business validation;
- transaction boundaries;
- coordination of one or more repositories owned by its capability;
- communication with public services/ports of other capabilities;
- local caching where justified;
- logging;
- asynchronous application/domain signals where justified;
- orchestration required by the use case.

Transactions belong at the service/use-case boundary rather than inside individual repositories.

Do not require every service to use cache, events, or multiple repositories.

These are permitted responsibilities, not mandatory ceremony.

---

# Repository Responsibilities

Repositories should remain deliberately boring.

Responsibilities include:

- persistence;
- persistence queries;
- validation required specifically to satisfy the persistence driver/provider;
- persistence-related logging.

Repositories must not become business orchestration services.

Avoid:

```text
Repository A → Repository B
```

for cross-capability business behavior.

Queries may express persistence selection criteria, but business decisions should remain outside repositories.

---

# Persistence Model

Separate persistence representation from the domain/service representation.

Conceptually:

```text
Item
   │
   ▼
ItemConverter
   │
   ▼
ItemEntity
   │
   ▼
ItemRepository
   │
   ▼
Jakarta Persistence
```

and the reverse direction when loading.

Use explicit conversion between domain/service models and persistence entities.

The additional ceremony is accepted intentionally to prevent persistence requirements from dictating domain representation.

Do not collapse domain objects and JPA entities in this architecture.

---

# Persistence Technology

Use:

```text
Jakarta Persistence
        ↓
Hibernate ORM provider
        ↓
PostgreSQL
```

Prefer Jakarta Persistence APIs over Hibernate-specific APIs where the standard is sufficient.

Repository implementations may use concepts such as:

```text
jakarta.persistence.EntityManager
```

rather than exposing Hibernate-specific APIs.

Hibernate ORM is the selected provider, not the domain contract.

Use Flyway for schema migrations.

PostgreSQL extensions may be used where justified, notably `pgvector`.

---

# Jakarta-First Technology Principle

For this project, apply the following preference:

```text
Does Jakarta provide a sufficient standard?
        │
        ├── yes → prefer Jakarta
        │
        └── no
             ↓
Does Quarkus provide a useful abstraction/integration?
        │
        ├── yes → prefer Quarkus
        │
        └── no → use an external library
```

This is a project learning/design principle, not an absolute rule.

Document exceptions where another choice is materially better.

---

# Selected Server Stack

Treat the following as current architectural constraints unless a concrete incompatibility is discovered.

## Platform

```text
Java 25
Maven multi-module
JPMS
Quarkus
```

## Dependency Injection

```text
Jakarta CDI / Quarkus ArC
```

## HTTP

```text
Jakarta REST / Quarkus REST
```

## JSON

Use:

```text
Jakarta JSON-B
```

Do not introduce Jackson for Capsa v0.1.

JAXB is not required unless XML becomes a real requirement.

## Validation

```text
Jakarta Validation
```

## Transactions

```text
Jakarta Transactions
```

Transaction boundaries belong primarily to services/use cases.

## Persistence

```text
Jakarta Persistence
Hibernate ORM
PostgreSQL
Flyway
```

## Cache

Use Quarkus Cache with its local Caffeine backend.

Capsa v0.1 assumes:

```text
one deployment
one node
one application instance
```

No distributed cache is required.

Caching is opt-in per service/use case where evidence justifies it.

Do not introduce Redis or another distributed cache.

## Classification / AI

Classification follows the reconciled strategy/pipeline design.

Prefer integration through:

```text
Argonaut
LangChain4j
```

rather than custom provider-specific HTTP infrastructure where existing abstractions are sufficient.

External decision/model providers currently available include:

```text
Jev
DeepSeek
```

Keep provider details outside the core classification semantics.

## Embeddings

Use local embeddings initially:

```text
short text
   ↓
ONNX embedding model
   ↓
vector
   ↓
PostgreSQL pgvector
```

This avoids requiring an external embedding API for v0.1.

The architecture should permit evaluation/replacement later without coupling domain semantics to ONNX.

## Testing

The intended testing strategy includes:

```text
domain unit tests
service/use-case tests
Quarkus integration tests
PostgreSQL integration tests
Karate API contract/integration tests
```

The architecture should make these levels independently testable where practical.

---

# Classification Architecture

The reconciled model includes concepts approximately like:

```text
ClassificationService
        │
        ▼
ClassificationPipeline
        │
        ├── Known Classification
        ├── Embedding Classification
        └── System One
```

with unresolved classification producing a resolution flow rather than a persisted Item.

The architecture review previously deferred the exact placement of `ClassificationPipeline`.

Resolve or recommend the placement now.

Consider:

- whether it is internal to the `classification` capability;
- whether it is application orchestration;
- whether any other capability needs to know it exists;
- how strategies are configured/composed;
- how Quarkus/CDI discovers or supplies strategies;
- how provider adapters remain internal.

Prefer keeping the pipeline implementation private to the classification capability unless another module genuinely needs it.

---

# Classification Memory

Classification Memory is User-scoped evidence.

The reconciled model treats:

```text
ClassificationMemoryEntry
```

as independently stored evidence and:

```text
ClassificationMemory
```

as a capability/port for recording/querying evidence.

Determine where this belongs within the classification capability and which pieces, if any, must be public.

Preserve future curation without designing a curator.

---

# Runtime / Composition Root

Use `runtime` as the application composition root.

Do not build a custom runtime/composite abstraction merely to avoid Quarkus.

The purpose of Capsa includes learning Quarkus.

Framework-specific composition is therefore explicitly acceptable here.

`runtime` may contain or coordinate:

- Quarkus bootstrap;
- CDI composition;
- producers where necessary;
- configuration;
- strategy wiring;
- OIDC/security integration;
- cache configuration;
- provider configuration;
- runtime-specific adapters.

Quarkus-specific concepts are allowed here without apology.

Avoid leaking them into capability internals when there is no semantic reason.

The architecture should show which module ultimately produces the executable Quarkus application.

---

# Authentication

Capsa should not own username/password authentication in v0.1.

Do not persist user passwords.

Use external OAuth/OIDC identity providers.

Foreseeable providers include:

```text
Google
Microsoft
Facebook
```

A Capsa `User` stores application/domain information about the user, not authentication credentials.

Conceptually:

```text
External Identity Provider
          ↓
       OIDC/OAuth
          ↓
authenticated identity
          ↓
Capsa User mapping
```

Invitation-based onboarding is foreseeable:

```text
invitation link
      ↓
external authentication
      ↓
Capsa User creation/activation
```

Do not fully design invitation workflows unless required by the current use cases.

Determine the boundary between runtime/security concerns and the `users` capability.

---

# Error Contract

Capsa APIs should expose stable error codes.

Conceptually:

```json
{
  "code": "CAPSA_LIST_NOT_FOUND",
  "message": "List was not found."
}
```

Use Jakarta REST `ExceptionMapper` or the appropriate equivalent at the HTTP boundary.

Internal exceptions must be logged.

Client responses differ by environment:

```text
development:
    code
    message
    diagnostics / stack trace where appropriate

production:
    code
    safe message
```

Production responses must not expose internal stack traces or sensitive implementation details.

Error codes should evolve from real use cases rather than from a speculative global catalog.

Determine where error-code ownership belongs.

Prefer capability-specific ownership with centralized HTTP mapping if this keeps boundaries cleaner.

---

# Logging and Observability

Keep ordinary application logging separate from richer observability/activity evidence where useful.

Initial deployment should remain simple.

Use normal application logs for:

- errors;
- diagnostics;
- runtime information.

A separate observability/activity log may capture relevant application activity.

Do not introduce a large observability stack in v0.1.

PostgreSQL may be used for durable application/audit events where persistence has semantic value.

Distinguish:

```text
technical logs / metrics / traces
```

from:

```text
application/domain activity or audit evidence
```

Do not treat them as the same concept.

Design only the boundaries needed now.

---

# External Provider Protection

External providers such as Jev and DeepSeek use API keys and have finite rate/cost limits.

The architecture must not permit unbounded provider invocation from a single capture.

Consider architectural placement for:

- timeout;
- bounded number of external calls;
- provider failure;
- rate/cost protection.

Do not build a complete resilience platform.

Classification uncertainty and provider execution failure must remain semantically distinct, as established by the domain reconciliation.

---

# Future Adapters — Do Not Implement

The architecture should not block but must not implement:

## MCP

A future MCP adapter may allow:

```text
"Elo, necesito jabón para la ducha"
          ↓
MCP
          ↓
Capsa public capability
```

MCP must not bypass service boundaries.

## Push Notifications

Future mobile behavior may require:

```text
NotificationService
      ↓
FCM / APNs
```

No current UC requires push notification delivery.

Do not introduce notification infrastructure yet.

## Geolocation

Geolocation/reminders are expected around a later stage (approximately v1.5 conceptually), not UC-01 through UC-06.

Do not design them now.

---

# Capability Ownership Analysis

Analyze at minimum:

```text
users
lists
capture
classification
items
runtime
```

For each capability identify:

1. responsibility;
2. concepts/entities owned;
3. public operations;
4. internal services;
5. repositories owned;
6. persistence entities owned;
7. external adapters owned;
8. dependencies on other capabilities;
9. capabilities allowed to depend on it.

Do not force symmetrical internal structures.

---

# Dependency Graph

Produce an explicit proposed JPMS/module dependency graph.

For example only:

```text
capture
   ├──► classification
   └──► items
```

Do not copy that example without deriving it.

The graph must be justified from UC-01 through UC-06.

Identify:

- direct dependencies;
- prohibited dependencies;
- potential cycles;
- how cycles are avoided;
- public surfaces required by each edge.

Acyclic dependencies are strongly preferred.

If a cycle appears semantically legitimate, analyze it explicitly rather than hiding it through a `common` module.

---

# API Boundary

Determine whether HTTP/Jakarta REST endpoints should:

1. live in a centralized inbound `api` module; or
2. live vertically with their owning capability.

Do not assume either answer.

Evaluate:

- JPMS boundaries;
- ownership;
- error mapping;
- authentication;
- discoverability;
- Quarkus coupling;
- future MCP adapter;
- cross-capability endpoints.

Recommend one approach for Capsa v0.1 with rationale.

Avoid creating an `api` module merely because other projects use one.

---

# Technology Placement Map

Produce a map showing where framework/library dependencies are allowed.

At minimum address:

```text
Jakarta CDI
Jakarta REST
JSON-B
Jakarta Validation
Jakarta Transactions
Jakarta Persistence
Hibernate ORM
Quarkus APIs
Quarkus Cache
Caffeine
PostgreSQL
pgvector
Flyway
Argonaut
LangChain4j
ONNX
OIDC
Karate
```

For each, identify whether it belongs in:

- public capability API;
- internal capability implementation;
- persistence;
- adapter;
- runtime/composition;
- tests only.

The purpose is to prevent accidental framework leakage.

---

# Architecture Principles to Preserve

The architecture should embody:

> Capabilities own their data and behavior.

> Cross-capability collaboration occurs through public semantic services, never repositories.

> JPMS enforces boundaries where Java can enforce them.

> Services own business validation and transaction boundaries.

> Repositories persist; they do not orchestrate business behavior.

> Persistence entities are not domain models.

> Prefer Jakarta standards when sufficient.

> Quarkus-specific composition belongs naturally at the runtime/boundary.

> Implement current behavior simply while preserving stable seams around foreseeable evolution.

---

# Required Deliverables

Create an architecture document following existing Capsa/OSK documentation conventions.

Suggested identity:

```text
CAPSA-ARCH-001
```

The architecture document should contain at least:

## 1. Architecture Overview

Concise architectural style and rationale.

## 2. Repository Layout

Proposed monorepo/server organization.

## 3. Capability Model

Responsibilities and ownership for each capability.

## 4. Module Dependency Graph

Include Mermaid or equivalent diagram.

## 5. Public Surface Model

What each JPMS module exports and what remains internal.

## 6. Capability Internal Structure

Service/repository/entity/converter conventions.

## 7. Persistence Architecture

Entity/domain separation, JPA/Hibernate/PostgreSQL/Flyway placement.

## 8. Classification Architecture

Pipeline, strategies, memory, embeddings, Argonaut/provider placement.

## 9. Runtime / Composition

How Quarkus/CDI assembles the application.

## 10. API Architecture

REST ownership, JSON-B, authentication context, errors.

## 11. Security Boundary

OIDC identity to Capsa User mapping.

## 12. Transactions

Where transaction boundaries live and cross-capability implications.

## 13. Caching

Quarkus Cache/Caffeine placement and constraints.

## 14. Observability / Activity

Technical logging vs durable application evidence.

## 15. External Provider Boundary

Jev/DeepSeek/ONNX/Argonaut responsibilities and failure/rate protection.

## 16. Technology Placement Map

Allowed dependency locations.

## 17. Testing Boundaries

Unit, service, Quarkus, PostgreSQL and Karate placement.

## 18. Evolutionary Fitness

Briefly validate compatibility with:

- shared Lists;
- ClassificationMemory curation;
- additional Item states;
- PatternDetector;
- MCP;
- geolocation;
- push notifications;
- multiple deployment instances.

Do not design these features.

## 19. Architecture Decisions / Open Questions

Clearly separate:

- decided;
- recommended;
- deferred;
- unresolved.

---

# Validation

Before completing:

1. Trace every capability to current behavior.
2. Verify every cross-module dependency has a semantic reason.
3. Verify no module requires another module's repository.
4. Verify the dependency graph is acyclic or explicitly justify exceptions.
5. Verify persistence entities remain internal.
6. Verify Quarkus-specific dependencies are deliberately placed.
7. Verify UC-01 through UC-06 can execute through the proposed boundaries.
8. Verify classification failure and classification ambiguity remain distinct.
9. Verify no future feature was accidentally promoted into v0.1.
10. Verify the design is implementable as a Maven multi-module JPMS project.
11. Identify architecture decisions that should later become ADRs.
12. Do not begin implementation.

---

# Completion

Produce the architecture draft and associated OSK task/report records.

Set the architecture to **REVIEW**.

Do not create the Engineering Plan yet.

Do not implement Java.

Stop after the architecture draft is ready for engineering review.