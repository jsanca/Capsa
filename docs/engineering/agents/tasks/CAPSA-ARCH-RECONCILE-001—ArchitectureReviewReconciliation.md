# CAPSA-ARCH-RECONCILE-001 — Architecture Review Reconciliation

## Role

Act as the architecture author/implementer responsible for reconciling the engineering review of:

`CAPSA-ARCH-001 — Capsa Modular Monolith Architecture v0.1`

Use the applicable architecture and reconciliation skills.

This is a reconciliation task.

Do not implement production code.

Do not create the Engineering Plan.

---

## Objective

Reconcile every finding from `CAPSA-ARCH-REVIEW-001` against the architecture draft.

Update the architecture so that:

- accepted findings are incorporated;
- JPMS boundaries are actually compilable and enforceable;
- transaction boundaries do not span external model calls;
- Capture → Item idempotency guarantees are restored;
- v0.1 complexity follows current evidence;
- public capability APIs expose semantic concepts rather than implementation mechanisms.

Every review finding must receive an explicit disposition.

---

# Required Reading

Read:

- Capsa product intent;
- UC-01 through UC-06;
- reconciled domain model;
- domain review and reconciliation;
- `CAPSA-ARCH-001`;
- current architecture draft;
- `CAPSA-ARCH-REVIEW-001` and its complete engineering-review report.

The engineering review is the primary input to this reconciliation.

Do not silently discard findings.

---

# Reconciliation Principle

Preserve the fundamental architecture where the review confirmed it:

- modular monolith by capability;
- JPMS enforcement;
- capability-owned persistence;
- cross-capability collaboration through public semantic services;
- domain/persistence entity separation;
- classification isolated through public contracts;
- authentication distinct from authorization;
- runtime as Quarkus composition root;
- evolutionary seams without premature implementation.

Modify the architecture where review exposed concrete contradictions or unnecessary complexity.

---

# A-01 — Current User / JPMS Boundary

**Disposition direction: ACCEPT, with architectural refinement.**

The current design incorrectly relies on CDI to avoid a compile-time JPMS dependency on `CurrentUserContext`.

CDI does not eliminate Java's need to access the injected type.

Replace this with an explicit public semantic abstraction owned by the `users` capability.

Preferred direction:

```text
capsa.users
└── api
    ├── UserId
    ├── CurrentUser
    ├── User
    └── UserService
```

`UserId` should be a strongly typed public concept, approximately:

```java
public record UserId(UUID value) {}
```

Do not reduce User identity to a raw `UUID` merely to avoid a dependency.

Capabilities that semantically operate on a User's resources may explicitly depend on the public API of `users`.

For example:

```text
lists ────────┐
items ────────┤
capture ──────┼──► users.api
              │
              ▼
           UserId
         CurrentUser
```

Determine exactly which capabilities genuinely require this dependency.

Runtime owns the OIDC-specific implementation:

```text
runtime
    OidcCurrentUser
        implements CurrentUser
```

The public abstraction must not expose:

- OIDC types;
- Quarkus security types;
- provider-specific claims;
- runtime implementation details.

Update the module graph accordingly.

The dependency on `users` is acceptable when it represents the real semantic fact that the capability operates on resources owned by a Capsa User.

---

# A-02 — Classification Composition / JPMS

**Disposition direction: ACCEPT, with refinement beyond the original recommendation.**

The architecture currently requires runtime to reference internal classification strategy implementations that classification intentionally does not export.

That violates JPMS encapsulation.

More importantly:

> The public classification contract must describe the capability, not its internal pipeline architecture.

Prefer a public API approximately like:

```text
capsa.classification
│
├── api
│   ├── Classifier
│   ├── ClassificationRequest
│   ├── ClassificationResult
│   └── ClassificationCandidate
│
└── internal
    ├── DefaultClassifier
    ├── ClassificationPipeline
    │
    ├── strategy/
    │   ├── ClassificationStrategy
    │   ├── KnownClassificationStrategy
    │   ├── EmbeddingClassificationStrategy
    │   └── SystemOneClassificationStrategy
    │
    └── memory/
```

The exact package layout may vary, but preserve the visibility rule.

The public semantic contract should be conceptually:

```java
public interface Classifier {
    ClassificationResult classify(ClassificationRequest request);
}
```

Consumers such as `capture` know only `Classifier`.

They must not know:

- `ClassificationPipeline`;
- strategy ordering;
- `ClassificationStrategy`;
- concrete strategy implementations.

The pipeline is an implementation technique and must remain private unless future evidence requires otherwise.

Likewise, `ClassificationStrategy` should remain a private/internal SPI for v0.1.

Do not export it merely to enable runtime composition.

If future requirements require externally supplied classification strategies, it may later be promoted deliberately to a public SPI.

Runtime may supply configuration and external dependencies, but it must not name or instantiate non-exported classification implementation classes.

The `classification` capability is responsible for composing its own implementation of `Classifier`.

This should allow the internal implementation to evolve from:

```text
Classifier
    ↓
Known classification
```

to:

```text
Classifier
    ↓
private pipeline
    ├── Known
    ├── Embedding
    └── System One
```

without requiring changes to consumers.

Capture should not change if the internal classification algorithm changes.

Record the architectural principle:

> Public capability APIs express business capabilities, not internal architectural patterns.

---

# A-03 — Long Transaction Across External Classification

**Disposition direction: ACCEPT.**

Do not hold a database transaction open while performing potentially slow or unreliable external classification/model calls.

Refactor the architectural flow toward:

```text
CaptureOrchestrator
        │
        ├── TX-1
        │    Capture creation / initial attempt persistence
        │
        ├── Classification
        │    NO database transaction spanning the call
        │
        └── TX-2
             resolution / Item creation / evidence persistence
```

Prefer explicit collaborating services/beans rather than relying on self-invocation to establish transaction boundaries.

Conceptually:

```text
CaptureOrchestrator
       │
       ├── CaptureCreationService
       │       @Transactional
       │
       ├── Classifier
       │       no encompassing DB transaction
       │
       └── CaptureResolutionService
               @Transactional
```

Exact class names are not mandatory.

The architectural requirement is the transaction separation.

The orchestrator itself should not require one transaction spanning the whole workflow.

---

# A-04 — Capture → Item Idempotency Guarantee

**Disposition direction: ACCEPT.**

Restore the guarantee established during domain reconciliation.

Atomicity alone is insufficient once classification occurs outside the transaction.

The architecture must preserve both:

```text
application-level idempotent resolution
```

and:

```text
database-level uniqueness for Item.captureId
```

Conceptually:

```text
CaptureResolutionService
        │
        ├── verify current Capture resolution state
        ├── resolve idempotently
        ├── create resulting Item
        ├── preserve classification evidence
        └── persist resolution
```

with a database constraint equivalent to:

```text
UNIQUE Item.captureId
```

where applicable to the persistence model.

Retries or repeated resolution attempts must not create duplicate Items.

Preserve the distinction between:

- aggregate-local invariants;
- cross-aggregate guarantees enforced by application + persistence constraints.

---

# A-05 — UC-05 Composition Cycle

**Disposition direction: ACCEPT.**

The reviewed Option A would introduce:

```text
items → lists → items
```

and violate the intended acyclic module graph.

Remove or reject that option unless the dependency structure changes for another justified reason.

For v0.1 prefer the simplest behavior-compatible design that preserves the DAG.

A client-composed representation using separate capability-owned operations/endpoints is acceptable.

Do not introduce an orchestration module merely to make UC-05 return one combined transport object unless there is demonstrated need.

Preserve the acceptance behavior:

> The User can view an owned List and its pending Items.

Do not confuse this behavior with a requirement that both must be returned by one endpoint.

Document the chosen v0.1 disposition explicitly.

---

# A-06 — List Semantic Profile Synchronization

**Disposition direction: ACCEPT / simplify according to current evidence.**

The reviewed architecture introduced derived classification state based on List state without a reliable mechanism for observing changes to the source List.

Current UC-01 through UC-06 do not require List editing.

Therefore do not build synchronization/invalidation machinery for behavior that cannot currently occur.

For v0.1:

- keep ownership explicit;
- avoid speculative cross-capability invalidation;
- document the future synchronization concern if semantic profiles become durable derived state and Lists become mutable.

Do not introduce events solely to solve a future mutation scenario.

The architecture should remain able to add such a mechanism later without destructive restructuring.

---

# A-07 — Classification Complexity

**Disposition direction: ACCEPT.**

Apply:

> Complexity follows evidence.

The architecture may preserve the strategy/pipeline seam, but v0.1 implementation must not require all classification mechanisms immediately.

Prefer an incremental implementation sequence.

Initial classification may begin with:

```text
Classifier
    ↓
KnownClassificationStrategy
```

while retaining the internal seam for future:

```text
EmbeddingClassificationStrategy
SystemOneClassificationStrategy
```

Do not require v0.1 to immediately deploy all of:

- ONNX;
- pgvector classification;
- Jev;
- DeepSeek;
- external-provider rate limiting;
- multiple strategy orchestration.

These technologies remain selected/foreseeable for the classification roadmap where evidence justifies them.

Do not delete the architectural seams that make their later introduction smooth.

The Engineering Plan will later determine appropriate slices.

---

# Remaining Review Findings

Reconcile every remaining LOW and OBSERVATION finding individually.

Do not omit them because the major findings are addressed above.

In particular inspect the review's findings around:

- typed IDs versus raw UUIDs;
- Flyway ownership versus schema ownership;
- durable activity/audit storage;
- shared/common exception hierarchies;
- speculative ClassificationMemory caching;
- API key sourcing and logging.

Use the following guidance where consistent with the review:

## Typed IDs

Prefer semantic typed IDs when they are genuine public domain/capability concepts.

`UserId` is explicitly such a concept.

Do not introduce raw UUIDs solely to reduce module dependencies.

Do not create a generic shared-ID module.

## Flyway

Distinguish:

```text
runtime owns migration execution
```

from:

```text
capabilities conceptually own their persistence schema/data
```

Document the relationship clearly.

Do not require each capability to independently run Flyway.

## Activity / Audit Persistence

Do not create durable PostgreSQL activity/audit infrastructure without a current behavioral requirement.

Keep the seam/documented distinction between:

- technical logs;
- application/domain evidence.

Implement durable activity storage later when a use case requires it.

## Exceptions

Avoid introducing a generic shared `CapsaException` hierarchy solely for centralized error mapping.

Capability-specific errors may remain capability-owned.

The HTTP/runtime boundary can map public capability failures to stable API error codes without forcing capabilities to depend on a shared technical exception model.

## ClassificationMemory Cache

Do not cache ClassificationMemory merely because caching is available.

Quarkus Cache/Caffeine remains the selected local caching mechanism when evidence demonstrates value.

No cache is mandatory for v0.1.

## Secrets / API Keys

External provider credentials must come from runtime configuration/secrets/environment.

They must not:

- appear in public capability APIs;
- be persisted as domain data;
- be committed to source control;
- be emitted to logs.

---

# Architecture Graph Revalidation

After applying the reconciliation, regenerate and validate the module dependency graph.

Check especially the new explicit dependencies on `users`.

Verify:

1. the graph remains acyclic;
2. every edge represents a semantic dependency;
3. no capability accesses another capability's repository;
4. runtime may depend on capabilities;
5. capabilities do not depend on runtime;
6. classification consumers depend only on its public semantic API;
7. internal classification implementation remains inaccessible through JPMS.

If reconciliation creates a cycle, do not hide it behind `common`, `shared`, or reflection.

Document and resolve it explicitly.

---

# Transaction Flow Revalidation

Trace UC-02, UC-03 and UC-04 through the reconciled architecture.

Show the transaction boundaries.

At minimum distinguish:

```text
TX-1
    initial Capture persistence

NO LONG-LIVED DB TX
    classification

TX-2
    resolution
    resulting Item
    classification evidence
```

Verify retry/idempotency behavior around the boundary between classification and TX-2.

---

# Classification Encapsulation Validation

Explicitly verify that this remains possible:

```text
capture
   │
   ▼
Classifier
```

while everything below remains private:

```text
DefaultClassifier
ClassificationPipeline
ClassificationStrategy
KnownClassificationStrategy
EmbeddingClassificationStrategy
SystemOneClassificationStrategy
```

The consumer must not need recompilation because the internal strategy graph changes, except where the public classification contract itself changes.

---

# Required Outputs

Update:

`docs/knowledge/architecture/capsa-arch-001-modular-monolith-v0.1.md`

Create the appropriate reconciliation report.

Update the task/engineering log according to OSK conventions.

The reconciliation report must include a table containing every review finding:

```text
Finding
Severity
Disposition
Change
Rationale
```

Use explicit dispositions such as:

```text
ACCEPTED
ACCEPTED WITH MODIFICATION
DEFERRED
REJECTED
```

A rejected finding requires concrete justification.

---

# Final Architecture Status

After reconciliation:

- if all HIGH/MEDIUM findings are satisfactorily resolved and no new blocking contradiction appears, mark the architecture as ready for final acceptance according to existing OSK lifecycle conventions;
- otherwise leave it in REVIEW and identify remaining blockers.

Do not create the Engineering Plan yet.

QA acceptance-test design may be occurring concurrently and does not need to be modified by this task unless reconciliation discovers a direct product/domain contradiction.

---

# Completion Validation

Before stopping verify:

1. A-01 compiles conceptually under JPMS.
2. `UserId` has one clear public owner.
3. capabilities do not depend on runtime for current-user identity.
4. runtime does not reference private classification implementations.
5. `Classifier` expresses the public capability rather than `ClassificationPipeline`.
6. classification strategies remain internal unless evidence requires a public SPI.
7. external classification does not occur inside a long-lived DB transaction.
8. Capture resolution is idempotent.
9. duplicate Items are prevented at the persistence boundary.
10. UC-05 does not introduce a module cycle.
11. speculative synchronization/cache complexity has been removed or clearly deferred.
12. every engineering-review finding has an explicit disposition.
13. the final module graph remains acyclic.
14. no production code was implemented.
15. no Engineering Plan was created.

Stop after reconciliation.