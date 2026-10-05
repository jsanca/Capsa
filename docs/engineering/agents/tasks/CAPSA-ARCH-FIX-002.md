# CAPSA-ARCH-FIX-002 — Transaction-aware Observability Dispatch

**Role:** SWE
**Agent:** Clio
**Status:** OPEN
**Depends on:** CAPSA-ARCH-REVIEW-002 (finding M-1 + L-1 + L-2)

---

## Context

CAPSA-ARCH-REVIEW-002 identified M-1:

> Observability events are currently emitted from `@Transactional` service
> methods before the enclosing transaction has committed.

This creates a semantic inconsistency: an event may reach the observability
sink even if the transaction later rolls back.

The current observability flow also serializes the event with JSON-B on the
caller/business thread before handing the serialized `String` to the
virtual-thread executor.

We want to correct both issues while preserving the intentionally small v0.1
observability architecture.

Capsa is not an event platform. Do not generalize this into one.

---

## Reference

The Codex project contains a `DeferredEventDispatcher` implementing the same
conceptual pattern with its own manually-managed transaction abstractions:

- events emitted inside a transaction are accumulated per transaction;
- commit releases them;
- rollback discards them;
- events outside a transaction are dispatched immediately.

Use this **only as a conceptual reference**.

Do **NOT** port Codex's `TransactionContext`, `TransactionCallback`,
`DispatchMode`, `EventEnvelope`, metrics infrastructure, or event framework
into Capsa.

Capsa should use the transaction lifecycle already provided by Jakarta
Transactions / Quarkus.

---

## Goal

Make `Observability` transaction-aware so that an event emitted inside a
transaction becomes externally observable only after that transaction
successfully commits.

At the same time, move JSON-B serialization to the asynchronous side of the
dispatch boundary.

### Desired conceptual flow

```
@Transactional service
       |
       +-- DB mutations
       |
       +-- observability.emit(event)
                    |
                    v
             defer until commit
                    |
          +---------+---------+
        commit             rollback
          |                   |
          v                   X
      dispatcher
          |
     virtual thread
          |
       JSON-B
          |
      sink adapter
          |
       SLF4J
```

---

## Semantics

### 1. Inside an active transaction

`observability.emit(event)` MUST NOT immediately submit the event to the
asynchronous dispatcher.

The event must be associated with the current transaction and retained until
its outcome is known.

Multiple events emitted during the same transaction must be retained in
emission order.

On successful commit:
- release the pending events for asynchronous dispatch.

On rollback:
- discard the pending events.

An event belonging to a rolled-back transaction must never reach the
observability sink.

### 2. Outside a transaction

`observability.emit(event)` should preserve the current best-effort behavior
and dispatch asynchronously without introducing an artificial transaction.

Do not require callers to know whether a transaction exists.

### 3. Async boundary

The business/caller thread should hand off the immutable `ObservabilityEvent`.

JSON-B serialization and sink invocation should occur after the asynchronous
boundary.

Desired shape:

```
emit(event)
    -> transaction-aware deferral
    -> after commit
    -> dispatcher / virtual thread
    -> JSON-B serialization
    -> sink adapter
    -> SLF4J
```

Do not serialize the event to `String` before submitting asynchronous work.

### 4. Best-effort semantics

Observability remains best-effort.

After a successful business commit:

- serialization failure must not affect the already-committed business
  operation;
- sink failure must not affect the business operation;
- failures should be logged through the normal application logger.

Do NOT introduce durable delivery.

Specifically do not introduce:

- outbox tables
- Kafka
- queues
- retry infrastructure
- OpenTelemetry
- Loki
- event persistence
- delivery guarantees

A process crash after commit may still lose an observability event in v0.1.
This is acceptable.

### 5. Transaction integration

Use the smallest standard Jakarta Transactions / Quarkus mechanism that allows
observing the current transaction outcome and registering after-completion
behavior.

Do not implement a parallel transaction framework.

Keep transaction-awareness internal to the observability implementation.
Capability services must continue to use:

```java
observability.emit(event)
```

They must NOT:

- register transaction callbacks themselves;
- explicitly flush events;
- know about commit/rollback;
- move event emission into orchestrators merely to avoid this problem.

Preserve the existing capability boundaries.

### 6. Scope / architecture

Keep the implementation intentionally small.

Do not reproduce the generalized Codex event architecture.

No `DispatchMode`.
No `EventEnvelope`.
No generic domain-event framework.
No custom `TransactionContext`.
No observability metrics for this mechanism unless required by existing code.
No speculative abstraction for future sinks.

Prefer a small transaction-aware dispatcher/buffer internal to
`capsa-observability`.

If the standard transaction API naturally provides transaction-scoped storage,
use it. Otherwise implement the minimum state required to associate pending
events with the active transaction.

Pay attention to cleanup: committed and rolled-back transactions must not
leave pending event state behind.

### 7. Executor lifecycle

CAPSA-ARCH-REVIEW-002 also noted the lifecycle of the virtual-thread executor
as an architectural concern (L-2).

While modifying this area, verify that the executor is correctly closed when
the application shuts down.

Use normal CDI/Jakarta lifecycle facilities if cleanup is required.

Do not introduce an executor-management abstraction solely for this.

---

## Tests

Add/update tests covering at minimum:

- emit outside a transaction → asynchronously dispatched;
- emit inside transaction → not dispatched before commit;
- commit → event dispatched;
- multiple events in one transaction → dispatched in emission order;
- rollback → events discarded;
- serialization happens on the asynchronous execution side;
- serialization failure does not propagate into business code;
- sink failure does not propagate into business code;
- existing event JSON structure remains compatible;
- all eight current `ObservabilityEvent` variants still serialize;
- executor/resource lifecycle where reasonably testable.

Prefer behavioral tests over implementation-detail tests.

Do not make tests depend on JSON property ordering.

---

## Documentation Reconciliation

Update documentation affected by this change.

At minimum inspect and reconcile:

- `docs/knowledge/observability/capsa-obs-001-observability-event-foundation.md` —
  observability architecture description, async dispatch section, serialization section;
- `docs/knowledge/architecture/capsa-arch-001-modular-monolith-v0.1.md` —
  observability/transaction sections;
- module diagrams if implementation responsibilities change;
- sequence diagrams, especially the cross-flow observability emission diagram;
- relevant Javadoc in `capsa-observability`.

The sequence documentation must show the new actual behavior:

```
Service
   -> Observability.emit(event)
   -> transaction-aware buffer
   -> COMMIT
   -> asynchronous dispatcher
   -> JSON-B serialization
   -> SLF4J sink
```

and:

```
Service
   -> Observability.emit(event)
   -> transaction-aware buffer
   -> ROLLBACK
   -> discard
```

Also correct stale documentation discovered during CAPSA-ARCH-REVIEW-002 when
it directly concerns this implementation, including descriptions that still
claim serialization occurs before async dispatch.

Do not rewrite unrelated architecture documentation in this task.

Documentation must describe the implementation after the fix, not the intended
design before it.

---

## Validation

Run the relevant module tests and the full Maven test suite.

Verify:

- JPMS boundaries remain intact;
- capability services require no transaction-awareness changes;
- no event from a rolled-back transaction reaches the sink;
- events released after commit preserve their emission order;
- existing JSON contract remains compatible;
- build/tests are green.

---

## Required Report

Create: `docs/engineering/agents/reports/CAPSA-ARCH-FIX-002.md`

Record:

- implementation approach;
- transaction mechanism selected and why;
- files changed;
- tests added/changed;
- documentation reconciled;
- validation commands/results;
- any limitation deliberately retained for v0.1.

Do not address the other CAPSA-ARCH-REVIEW-002 findings in this task.

This task remediates M-1 and the closely related async-serialization
observation (L-1) only. L-2 (executor lifecycle) is in scope as part of the
same area.
