# Observability Knowledge — CAPSA-OBS-001

**Module:** `capsa-observability`
**Status:** Current for v0.1
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
Capability Service
       │
       │  observability.emit(event)
       ▼
Observability port (interface)
       │
       ▼
Dispatcher (virtual thread per task)
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
| `ClassificationRecorded` | UC-04 / UC-03 | `ClassificationServiceImpl.recordResolution` |

Adding a new event is two pieces: a record in `events/`, and a case in
the dispatcher in `internal.EventJson`. Capability services do not need
to change.

### Forbidden Fields

The event contract deliberately excludes:

- OIDC tokens, raw authorization headers, credentials.
- Raw or normalized capture content.
- Email addresses, display names, OIDC subjects.
- Anything else carrying personally identifying or sensitive data.

`ObservabilityEventTest#sensitiveFieldsAreNotPartOfTheCanonicalContract`
enforces this by reflecting on every record component and asserting no
field name matches a sensitive keyword.

## 5. Dispatch Semantics

**Asynchronous, virtual-thread-per-task.** The default adapter wraps an
`Executors.newVirtualThreadPerTaskExecutor()`. The caller thread returns
immediately; the virtual thread serializes the event and writes to the
sink.

Virtual threads were chosen because:

- Java 25 makes them the lightest unit of async work — no custom
  executor tuning is needed.
- They block cheaply if the underlying logger is synchronous; we are not
  protecting scarce OS threads.
- They are removed when the task completes; no thread pool sizing is
  required.

## 6. Failure Semantics — Best Effort

Observability is **best effort** in v0.1.

```
business operation succeeds
        +
observability sink fails
        │
        ▼
failure logged at ERROR via the standard application logger
        │
        ▼
business result remains successful
```

Two failure modes are absorbed:

1. **Enqueue failure** — the dispatcher executor rejects the task
   (shutdown or saturated). The exception is logged via the application
   logger and `emit()` returns normally.
2. **Sink failure** — the sink raises while writing. The exception is
   logged via the application logger and dropped.

Observability failures are reported through the **standard application
logger**, not through the observability port. Failures must not recurse
through the observability path.

There is **no transactional coupling** between business persistence and
observability delivery. A durable outbox (transactional coupling) may be
introduced later if evidence shows the best-effort model is insufficient.
The current contract does not promise exactly-once or guaranteed delivery.

## 7. Current Sink: Dedicated Structured SLF4J Logger

**SLF4J is the current adapter, not the observability abstraction.**

The default sink writes one event per line to the dedicated logger
category `capsa.observability`. The format is deterministic JSON:

```json
{"eventId":"1fbf365a-...","type":"ListCreated","occurredAt":"2026-10-03T20:12:32.438317Z","actor":{"kind":"User","userId":"bfa1c54d-..."},"listId":"7102e10d-...","name":"Observability smoke"}
```

A dedicated category lets operators route observability traffic separately
from ordinary application logs (`application.log`). It does not
introduce a separate file by default — the routing decision is made by
the deployment platform (Kubernetes, Nomad, log forwarder).

The output schema is independent of any JSON-B provider. The serializer
(`com.capsa.observability.internal.EventJson`) is hand-rolled because
the variant set is closed at compile time by the sealed interface, and
the schema is a versioned contract.

## 8. Threading Boundary

| Concern | Where it lives |
|---|---|
| Business persistence transaction | capability service method, `@Transactional` |
| Observability event creation | capability service method, **after** the business return value is known |
| Observability dispatch | virtual thread spawned by `Slf4jObservability.emit` |
| Sink write | dedicated logger, run on the virtual thread |

`emit()` is always called **after** the capability method has done its
work and produced the result. If the capability method throws, the event
is not emitted. If the business transaction rolls back, no event has
been emitted yet.

## 9. Evolution Seam

Future adapters slot in by replacing `Slf4jObservability` in `runtime`:

- A `LokiObservability` could push events to Loki over HTTP.
- An `OpenTelemetryObservability` could map each variant to a span.
- A `KafkaObservability` could publish to an events topic.
- A `NullObservability` could be used in tests where event emission is a
  noise.

Capability services never change. The change is local to `runtime`'s
bean wiring and to a new module under `com.capsa.observability.internal`
(or a new module entirely).

When a new event type is added:

1. Add a record to `capsa-observability/api/events/`.
2. Add it to `ObservabilityEvent`'s `permits` clause.
3. Add the variant switch case in `EventJson` and the type-name switch
   case.
4. Add a unit test covering serialization for the new variant.
5. Emit the event from the appropriate capability service method.

## 10. What Observability Is Not

- **It is not an audit log.** The audit trail for a given business
  entity is the entity itself (`Item.createdAt`, `Item.completedAt`,
  `CaptureEntity.capturedAt`, `ClassificationAttemptEntity.attemptedAt`).
  Observability events are evidence that an event happened, not a
  substitute for the domain history.
- **It is not metrics.** No counters, gauges, or histograms are emitted
  in v0.1. Quarkus produces JVM metrics by default; dedicated metrics
  are not in scope.
- **It is not tracing.** No trace ids, span ids, or parent-context
  propagation. The v0.1 deployment is single-node; correlation is
  enough at the source.
- **It is not application logging.** Errors, warnings, and runtime
  diagnostics continue to use the standard application logger. The
  observability port is for structured business activity only.

## 11. References

- Source authority: use-case specs in `docs/knowledge/use-cases/`.
- Architectural constraints: §14 of
  [capsa-arch-001-modular-monolith-v0.1.md](../architecture/capsa-arch-001-modular-monolith-v0.1.md).
- Engineering evidence: CAPSA-OBS-001 report under
  `docs/engineering/agents/reports/`.