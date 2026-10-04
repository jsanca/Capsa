# CAPSA-OBS-001 Engineering Report — Observability Event Foundation

**Status:** DONE
**Date:** 2026-10-03
**Depends on:** S-06

---

## Goal

Introduce the minimal observability-event foundation for Capsa v0.1: a
port, a canonical event contract, an asynchronous SLF4J sink, and a
representative set of wired events. Establish the seam so future
infrastructure (Grafana, Loki, OpenTelemetry, Kafka) can replace the
current sink without touching capability services.

---

## Scope Delivered

### New module `capsa-observability`

A leaf JPMS module that capability modules depend on and that the
runtime wires. Public exports are `com.capsa.observability.api` (port +
canonical contract) and `com.capsa.observability.api.events` (event
records). Implementation lives in `com.capsa.observability.internal` and
is not exported.

### Canonical event contract

`ObservabilityEvent` is a sealed interface. Every permitted subtype is a
record with three mandatory fields plus variant-specific data:

- `eventId` — `UUID`, stable identity for sinks.
- `occurredAt` — `Instant`, set by the capability service when the event
  is created.
- `actor` — `EventActor` (sealed). v0.1 has one variant: `EventActor.User`
  carrying only the user's stable UUID. No email, no display name, no
  OIDC subject.

`actor` may be `null` only when no authenticated user is involved
(reserved for future system actors).

### Initial event set

Eight variants covering UC-01 through UC-06:

| Event | Source |
|---|---|
| `ListCreated` | `ListServiceImpl.create` |
| `ItemCreated` | `ItemServiceImpl.create` and `createFromCapture` |
| `ItemCompleted` | `ItemServiceImpl.complete` (only when the status transitions) |
| `CaptureSubmitted` | `CaptureCreationService.createCapture` |
| `CaptureClassified` | `CaptureResolutionService.resolveCapture` |
| `CaptureNeedsResolution` | `CaptureResolutionService.storeNeedsResolution` |
| `CaptureResolved` | `CaptureResolutionService.resolveFromUser` |
| `ClassificationRecorded` | `ClassificationServiceImpl.recordResolution` |

### Current sink — dedicated structured SLF4J logger

`Slf4jObservability` is the default `@ApplicationScoped` binding for
`Observability`. It writes one deterministic JSON line per event to the
logger category `capsa.observability`. Output schema:

```json
{"eventId":"...","type":"ListCreated","occurredAt":"2026-10-03T...","actor":{"kind":"User","userId":"..."},"listId":"...","name":"..."}
```

The category is configured in `capsa-runtime/src/main/resources/application.properties`
via `quarkus.index-dependency.observability.*` for CDI bean discovery.
Future operators can route this category to a separate file or sink
without changing capability code.

### Asynchronous dispatch via virtual threads

`Executors.newVirtualThreadPerTaskExecutor()` is the dispatcher. The
caller thread returns from `emit()` immediately; the virtual thread
serializes the event and writes to the sink. No queues, no retry, no
distributed messaging.

### Best-effort failure semantics

`emit()` never throws. Two modes:

- **Enqueue failure** (dispatcher rejects): caught, logged via the
  application logger at `ERROR`. `emit()` returns normally.
- **Sink failure** (logger throws while writing): caught on the virtual
  thread, logged via the application logger at `ERROR`. Event is
  dropped.

Failures are reported through the **standard application logger**, not
through the observability port. Observability failures do not recurse
into the observability path. There is no transactional coupling between
business persistence and observability delivery.

### Wiring

`capsa-observability` is added as a `requires` to `capsa-lists`,
`capsa-items`, `capsa-classification`, `capsa-capture`, and
`capsa-runtime`. CDI discovery for runtime is configured in
`application.properties`.

Each capability service gains `private final Observability observability`
plus constructor injection. Events are emitted **after** the business
write, so a rolled-back transaction does not emit.

---

## What Was Deliberately Not Built

- **No Grafana / Loki / OpenTelemetry / Kafka / Prometheus.** The
  directive excludes these. The seam is in place; the integrations are
  future work.
- **No transactional outbox.** A durable outbox may be introduced later
  if evidence shows the best-effort model is insufficient.
- **No metrics.** No counters, gauges, or histograms. Quarkus produces
  standard JVM metrics by default.
- **No trace context propagation.** v0.1 is single-node; the event id
  is the only correlation handle.
- **No general `Map<String,Object>` payload.** Every variant is a typed
  record with a fixed schema. `ObservabilityEventTest.sensitiveFieldsAreNotPartOfTheCanonicalContract`
  enforces the absence of sensitive fields by reflection.

---

## Key Design Decisions

**Sealed `ObservabilityEvent` rather than a tagged-union record.** A
sealed interface with permitted records gives compile-time exhaustiveness
in the `switch` over the variant and type-safe access to variant-specific
fields. Adding a new event is a new record + a switch case in the
serializer. Capability services do not need to learn a new pattern.

**Hand-rolled JSON serializer rather than JSON-B.** The set of permitted
variants is fixed at compile time. A single `switch` over the runtime
type is exhaustive and produces a deterministic schema. No provider
dependency in this module; no risk of provider upgrades changing the
output schema for a v0.1 contract.

**Virtual-thread dispatch rather than a custom executor.** Java 25 makes
this the lightest unit of async work. No thread-pool sizing, no
reactive streams, no custom executor framework. The advisory against
custom executor frameworks in the task is observed.

**Dedicated SLF4J category, not a separate file or sink.** Routing
decisions (file, Loki, Kafka) are deployment concerns, not module
concerns. The dedicated category name `capsa.observability` is the
contract that future routing configures against.

**Emit after the business write, not before.** A rolled-back transaction
must not emit. Events are constructed only after the capability has its
result, on the same line where the result is returned.

**Actor carries only the UUID.** Email, name, OIDC subject are excluded
deliberately. The architecture doc (§14) already requires that capture
content and item names not appear in default logs. The same rule applies
to actor data.

**No `actor` required when there is no user.** Reserved for future
system-actor variants. Sealed so the addition is type-safe.

---

## Test Results

```
Tests run: 54, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Test inventory:

| Test class | Module | Coverage |
|---|---|---|
| `ObservabilityEventTest` | `capsa-observability` | Canonical fields populated; all 8 variants serialize; string escaping; sensitive-field absence by reflection. |
| `Slf4jObservabilityTest` | `capsa-observability` | Sink failure does not propagate; dispatcher rejection does not propagate; one-line-per-event; null actor handling. |
| `ObservabilityWiringTest` | `capsa-runtime` | CDI deployment succeeds; capability service emits through the SLF4J logger end-to-end; business result returns. |
| Pre-existing capability + REST tests | various | Unchanged — confirm the wiring does not regress business behavior. |

Total: 10 new tests in `capsa-observability` + 1 integration test in
`capsa-runtime` + 43 pre-existing tests unchanged.

---

## Files Changed

| File | Change |
|---|---|
| `pom.xml` | Added `capsa-observability` module and dependencyManagement entry. |
| `capsa-observability/pom.xml` | New module. |
| `capsa-observability/src/main/java/module-info.java` | New JPMS module. |
| `capsa-observability/src/main/java/com/capsa/observability/api/Observability.java` | New port. |
| `capsa-observability/src/main/java/com/capsa/observability/api/ObservabilityEvent.java` | New sealed event contract. |
| `capsa-observability/src/main/java/com/capsa/observability/api/EventActor.java` | New sealed actor type. |
| `capsa-observability/src/main/java/com/capsa/observability/api/events/*.java` | 8 new event records. |
| `capsa-observability/src/main/java/com/capsa/observability/internal/EventJson.java` | New hand-rolled deterministic JSON serializer. |
| `capsa-observability/src/main/java/com/capsa/observability/internal/Slf4jObservability.java` | New default adapter (`@ApplicationScoped`). |
| `capsa-observability/src/main/java/com/capsa/observability/internal/SynchronousSinkExecutor.java` | Test seam for deterministic sink writes. |
| `capsa-observability/src/test/...` | New unit tests. |
| `capsa-lists/pom.xml`, `capsa-lists/src/main/java/module-info.java`, `.../ListServiceImpl.java` | Dependency + constructor injection + `ListCreated` emit. |
| `capsa-items/pom.xml`, `capsa-items/src/main/java/module-info.java`, `.../ItemServiceImpl.java` | Dependency + `ItemCreated` and `ItemCompleted` emits. |
| `capsa-classification/pom.xml`, `capsa-classification/src/main/java/module-info.java`, `.../ClassificationServiceImpl.java` | Dependency + `ClassificationRecorded` emit. |
| `capsa-capture/pom.xml`, `capsa-capture/src/main/java/module-info.java`, `.../CaptureCreationService.java`, `.../CaptureResolutionService.java` | Dependency + `CaptureSubmitted`, `CaptureClassified`, `CaptureNeedsResolution`, `CaptureResolved` emits. |
| `capsa-runtime/pom.xml`, `capsa-runtime/src/main/java/module-info.java` | Dependency + `requires capsa.observability`. |
| `capsa-runtime/src/main/resources/application.properties` | `quarkus.index-dependency.observability.*`. |
| `capsa-runtime/src/test/java/com/capsa/runtime/ObservabilityWiringTest.java` | New integration test. |
| `docs/knowledge/observability/capsa-obs-001-observability-event-foundation.md` | New knowledge page. |
| `docs/knowledge/observability/README.md` | New area navigation. |
| `docs/engineering/ENGINEERING_LOG.md` | Indexed CAPSA-OBS-001. |

---

## Limitations / Open Questions

- **No durable outbox.** If the process crashes between the business
  commit and the virtual thread dispatching, the event is lost. Document
  as best-effort.
- **No category-level routing config in `application.properties`.**
  Operators must add `quarkus.log.category."capsa.observability".*`
  entries when they want to redirect the events stream. This is
  deployment configuration and intentionally not committed.
- **No metrics, no tracing.** Quarkus produces JVM metrics by default;
  application-level metrics and tracing are out of scope until
  required.
- **`actor` is currently always `User`.** When a system actor
  (e.g., a future scheduler) is added, an `EventActor.System` variant
  is the natural extension.

---

## Validation

- `mvn install -DskipTests` — green.
- `mvn test` — 54/54 green.
- `mvn javadoc:javadoc` — green.
- CDI bean discovery confirmed end-to-end: the integration test prints a
  real JSON event line to the `capsa.observability` logger category.