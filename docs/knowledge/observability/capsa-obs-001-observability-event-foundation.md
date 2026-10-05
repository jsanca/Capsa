# Observability Knowledge — CAPSA-OBS-001

**Module:** `capsa-observability`
**Status:** Current for v0.1 (updated by CAPSA-ARCH-FIX-002)
**Depends on:** [capsa-arch-001-modular-monolith-v0.1.md](../architecture/capsa-arch-001-modular-monolith-v0.1.md)

---

## 1. Purpose

Capability services emit a small, structured signal when a meaningful
business event occurs — a list is created, an item completes, a capture
is classified. They do not know where that signal goes, what transports
it, or which future observability platform consumes it.

The observability subsystem is the seam that separates "something
relevant happened" from "how we record that something relevant happened".

## 2. Architectural Direction

```
Capability Service  (@Transactional)
       │
       │  observability.emit(event)
       ▼
Transaction-aware buffer
       │
   ┌───┴───────────┐
COMMIT          ROLLBACK
   │                │
   │             discard
   ▼
Dispatcher (virtual thread per task)
       │
       ▼
JSON-B serialization
       │
       ▼
Sink adapter (SLF4J today)
       │
       ▼
Dedicated logger: capsa.observability
```

**Properties:**

- Capability services depend on the port interface
  (`com.capsa.observability.api.Observability`) only. JPMS `requires
  capsa.observability` is the enforced dependency direction.
- The port has one method: `void emit(ObservabilityEvent event)`. It
  returns immediately; it never throws.
- A CDI bean (`Slf4jObservability`) is the default implementation. A
  future Grafana / Loki / OpenTelemetry / Kafka adapter can be bound
  in `runtime` without changes to capability services.

## 3. Module Placement

`capsa-observability` is a leaf module that capability modules depend on.
It does not depend on any other `capsa.*` module. Its public surface is:

- `com.capsa.observability.api` — port + canonical event contract.
- `com.capsa.observability.api.events` — typed event records.

The internal adapter (`com.capsa.observability.internal`) is not
exported. Capability code does not need to know the implementation
exists.

## 4. Canonical Event Contract

`ObservabilityEvent` is a sealed interface. Every permitted subtype is a
record with three mandatory metadata fields plus variant-specific data:

| Field | Type | Reason |
|---|---|---|
| `eventId` | `UUID` | Stable identity; deduplication / correlation in downstream sinks. |
| `occurredAt` | `Instant` | Source of truth for ordering. The capability service sets it when the event is created — not when it is written to the sink. |
| `actor` | `EventActor` (sealed) | Who initiated the activity. Null only for future system-actor events. |

`EventActor` is sealed and currently has one variant: `EventActor.User`
carrying the user's stable identifier — never email, name, or OIDC
subject.

### Variant Inventory (v0.1)

| Event | Use case | Source service |
|---|---|---|
| `ListCreated` | UC-01 | `ListServiceImpl.create` |
| `ItemCreated` | UC-02 / UC-04 | `ItemServiceImpl.create` / `createFromCapture` |
| `ItemCompleted` | UC-06 | `ItemServiceImpl.complete` |
| `CaptureSubmitted` | UC-03 | `CaptureCreationService.createCapture` |
| `CaptureClassified` | UC-03 (auto path) | `CaptureResolutionService.resolveCapture` |
| `CaptureNeedsResolution` | UC-03 (uncertain path) | `CaptureResolutionService.storeNeedsResolution` |
| `CaptureResolved` | UC-04 | `CaptureResolutionService.resolveFromUser` |
| `ClassificationRecorded` | UC-04 | `ClassificationServiceImpl.recordResolution` |

Adding a new event requires: a record in `events/`, a `@JsonbSubtype`
entry on `ObservabilityEvent`, and a unit test covering serialization.
Capability services do not need to change.

### Forbidden Fields

The event contract deliberately excludes:

- OIDC tokens, raw authorization headers, credentials.
- Raw or normalized capture content.
- Email addresses, display names, OIDC subjects.
- Anything else carrying personally identifying or sensitive data.

`ObservabilityEventTest#sensitiveFieldsAreNotPartOfTheCanonicalContract`
enforces this by reflecting on every record component.

## 5. Transaction-Aware Dispatch

The dispatch decision is made from `txRegistry.getTransactionStatus()`
on every `emit()` call:

| Jakarta Transaction status | `emit()` action |
|---|---|
| `STATUS_ACTIVE`            | Buffer the event. Register an interposed `Synchronization` once per transaction; `afterCompletion(STATUS_COMMITTED)` releases buffered events for async dispatch in emission order. |
| `STATUS_NO_TRANSACTION`    | Dispatch the event asynchronously (existing best-effort behavior). |
| Any other status           | Silently discard the event. |

The "any other status" branch covers every status that is not
provably "no transaction yet" or "transaction in flight and not doomed":
notably `STATUS_MARKED_ROLLBACK`, but also the transitional/completion
states `STATUS_PREPARING`, `STATUS_PREPARED`, `STATUS_COMMITTING`,
`STATUS_COMMITTED`, `STATUS_ROLLING_BACK`, `STATUS_ROLLEDBACK`, and
`STATUS_UNKNOWN`. Discarding in these states implements the invariant:

> An event associated with business work that has not successfully
> committed must never be emitted as if that work occurred.

```
Service (inside @Transactional, transaction ACTIVE)
   -> observability.emit(event)
   -> transaction-aware buffer (not dispatched yet)
   -> COMMIT
   -> events released for async dispatch

Service (inside @Transactional, transaction ACTIVE)
   -> observability.emit(event)
   -> transaction-aware buffer
   -> ROLLBACK
   -> events discarded

Service (inside @Transactional, transaction marked for rollback)
   -> observability.emit(event)
   -> discarded (not buffered, not dispatched)
```

The buffer is managed via `jakarta.transaction.TransactionSynchronizationRegistry`:

- On the first emit under `STATUS_ACTIVE`, a `List<ObservabilityEvent>` is
  stored via `putResource` and a `Synchronization` is registered via
  `registerInterposedSynchronization`.
- `Synchronization.afterCompletion(STATUS_COMMITTED)` releases events;
  any other completion status discards them.
- An emit under `STATUS_MARKED_ROLLBACK` (or any non-active, non-empty
  status) neither buffers nor dispatches.

Events emitted outside a transaction (`STATUS_NO_TRANSACTION`) are
dispatched immediately, preserving existing best-effort behavior.
Callers never need to know whether a transaction is active.

## 6. Asynchronous Dispatch

After commit (or immediately when outside a transaction) each event is
submitted to a `Executors.newVirtualThreadPerTaskExecutor()`. The
business/caller thread returns as soon as the event is submitted; the
virtual thread performs JSON-B serialization and the sink write.

Virtual threads were chosen because:

- Java 25 makes them the lightest unit of async work — no custom
  executor tuning is needed.
- They block cheaply if the underlying logger is synchronous.
- They are removed when the task completes; no thread pool sizing is
  required.

The executor is closed via CDI `@PreDestroy` when the application shuts
down.

## 7. Failure Semantics — Best Effort

Observability is **best effort** in v0.1.

```
business transaction commits
        +
observability serialization or sink fails
        │
        ▼
failure logged at ERROR via the standard application logger
        │
        ▼
business result remains successful; committed data is unaffected
```

Four failure modes are absorbed:

1. **Buffer failure** — if the transaction registry raises unexpectedly,
   the event falls back to immediate dispatch with a `WARN` log.
2. **Enqueue failure** — the dispatcher executor rejects the task. The
   exception is logged via the application logger and `emit()` returns
   normally.
3. **Serialization failure** — JSON-B raises inside the virtual thread.
   The exception is logged via the application logger and the event is
   dropped.
4. **Sink failure** — the sink raises while writing. Logged and dropped.

Observability failures are reported through the **standard application
logger**, not through the observability port. Failures must not recurse
through the observability path.

A process crash after a successful business commit, before the virtual
thread writes to the sink, may lose the observability event. This is
accepted behavior in v0.1. A durable outbox is not in scope.

## 8. Current Sink: Dedicated Structured SLF4J Logger

**SLF4J is the current adapter, not the observability abstraction.**

The default sink writes one event per line to the dedicated logger
category `capsa.observability`. The format is compact JSON serialized by
Jakarta JSON-B (Yasson). Field ordering within the JSON object is
alphabetical (Yasson's default), preceded by the `"type"` discriminator.
Do not rely on field ordering in tests or downstream consumers.

Example output:

```json
{"type":"ListCreated","actor":{"kind":"User","userId":"bfa1c54d-..."},"eventId":"1fbf365a-...","listId":"7102e10d-...","name":"Observability smoke","occurredAt":"2026-10-03T20:12:32.438317Z"}
```

A dedicated category lets operators route observability traffic separately
from ordinary application logs. It does not introduce a separate file by
default — the routing decision is made by the deployment platform.

## 9. Threading Boundary

| Concern | Thread |
|---|---|
| Business persistence transaction | Caller/request thread (`@Transactional` method) |
| `observability.emit(event)` | Caller thread — returns immediately |
| Transaction buffer accumulation | Caller thread (inside the active transaction) |
| `Synchronization.afterCompletion` callback | Caller thread (invoked by JTA after commit/rollback) |
| Event dispatch submission | Caller thread (via `executor.execute(...)`) |
| JSON-B serialization | Virtual thread |
| Sink write | Virtual thread |

## 10. Evolution Seam

Future adapters slot in by replacing `Slf4jObservability` in `runtime`:

- A `LokiObservability` could push events to Loki over HTTP.
- An `OpenTelemetryObservability` could map each variant to a span.
- A `KafkaObservability` could publish to an events topic.
- A `NullObservability` could be used in tests where event emission is
  noise.

Capability services never change. The change is local to `runtime`'s
bean wiring.

## 11. What Observability Is Not

- **It is not an audit log.** The audit trail for a given business
  entity is the entity itself (`Item.createdAt`, `CaptureEntity.capturedAt`, etc.).
- **It is not metrics.** No counters, gauges, or histograms are emitted
  in v0.1. Quarkus produces JVM metrics by default.
- **It is not tracing.** No trace ids or span ids. The `eventId` is the
  only correlation handle in v0.1.
- **It is not application logging.** Errors, warnings, and runtime
  diagnostics continue to use the standard application logger.

## 12. References

- Source authority: use-case specs in `docs/knowledge/use-cases/`.
- Architectural constraints: §14 of
  [capsa-arch-001-modular-monolith-v0.1.md](../architecture/capsa-arch-001-modular-monolith-v0.1.md).
- Engineering evidence: CAPSA-OBS-001 report and CAPSA-ARCH-FIX-002 report
  under `docs/engineering/agents/reports/`.
