# CAPSA-ARCH-FIX-002 — Transaction-aware Observability Dispatch — Report

## Status

Complete

## Objective

Remediate CAPSA-ARCH-REVIEW-002 findings M-1, L-1, and L-2 in
`capsa-observability`:

- **M-1** — Events emitted inside `@Transactional` methods were dispatched
  immediately, before the enclosing transaction committed, so a rollback could
  produce phantom observability events for business writes that never persisted.
- **L-1** — JSON-B serialization occurred on the caller/business thread before
  the event was handed to the virtual-thread executor, contradicting the
  documented "serialize on the async side" contract.
- **L-2** — The `ExecutorService` created in `Slf4jObservability` was never
  shut down; no `@PreDestroy` existed.

The fix must preserve the existing `observability.emit(event)` call site in all
capability services — they must not acquire any knowledge of transactions,
commit, or rollback.

## Summary

`Slf4jObservability` was rewritten to use
`jakarta.transaction.TransactionSynchronizationRegistry` (CDI-injectable in
Quarkus) as the transaction-lifecycle observer. No capability service was
changed.

**Transaction-aware buffering (M-1).**
`emit()` checks `txRegistry.getTransactionStatus() == STATUS_ACTIVE`. When
inside an active transaction, the event is stored in a
`List<ObservabilityEvent>` via `txRegistry.putResource(PENDING_KEY, list)` and
a `Synchronization` is registered via
`txRegistry.registerInterposedSynchronization(sync)` — once per transaction.
`Synchronization.afterCompletion(STATUS_COMMITTED)` releases the buffered events
for async dispatch in emission order. Any other completion status (rollback,
unknown) silently discards them. When called outside a transaction,
`emit()` dispatches immediately, preserving existing best-effort behavior.
Callers never need to know whether a transaction is active.

**Async serialization (L-1).**
The previous `emit()` called `jsonb.toJson(event)` on the caller thread and
submitted only the resulting `String` to the executor. In the new
implementation, the raw `ObservabilityEvent` is submitted to the executor
and `serializeAndSink(event)` — which calls `jsonb.toJson(event)` and then
invokes the sink writer — runs entirely inside the virtual thread task. Both
serialization failures and sink failures are caught inside that task and logged
via the standard application logger; neither propagates to the caller.

**Executor lifecycle (L-2).**
`@PreDestroy public void onDestroy()` calls `dispatcher.close()` (the
`ExecutorService.close()` default method, which calls `shutdown()` +
`awaitTermination`) and `jsonb.close()`.

**Constructor change.**
The production constructor is now `@Inject public Slf4jObservability(TransactionSynchronizationRegistry txRegistry)`.
The full test-seam constructor is
`public Slf4jObservability(Logger, ExecutorService, Consumer<String>, TransactionSynchronizationRegistry, Jsonb)`.
The previous 3-argument test constructor is removed; existing tests were
updated to use the new signature.

**JPMS (L-3 incidentally fixed).**
`opens com.capsa.observability.internal` was added to `module-info.java`,
making the CDI/ArC `opens` pattern consistent with every other capability
module (CAPSA-ARCH-REVIEW-002 L-3 was not in scope but was a one-line fix in
the same file).

**Documentation.**
`capsa-obs-001-observability-event-foundation.md` was updated: the flow diagram
now shows the transaction buffer; §5 (transaction-aware dispatch) is a new
section; §8 corrects "hand-rolled `EventJson`" to JSON-B/Yasson and notes
alphabetical field ordering. The stale "declaration order" Javadoc in
`ObservabilityEvent.java` was corrected to "alphabetical order (Yasson
default)".

**Test infrastructure.**
`TestTransactionRegistry` — a new in-memory `TransactionSynchronizationRegistry`
implementation — supports `begin()`, `commit()`, `rollback()` for unit tests
without CDI or JTA. `Slf4jObservabilityTest` was rewritten from 4 tests to 15
behavioural tests covering the full transaction lifecycle, failure modes, output
contract, and executor shutdown.

## Files Changed

- `capsa-observability/pom.xml` — added `jakarta.transaction-api` dependency
  (version managed by Quarkus BOM).
- `capsa-observability/src/main/java/module-info.java` — added
  `requires jakarta.transaction`; added `opens com.capsa.observability.internal`.
- `capsa-observability/src/main/java/com/capsa/observability/internal/Slf4jObservability.java`
  — full rewrite: `@Inject TSR` constructor; transaction-aware buffer;
  `serializeAndSink` on virtual thread; `@PreDestroy onDestroy`.
- `capsa-observability/src/main/java/com/capsa/observability/api/ObservabilityEvent.java`
  — Javadoc: "declaration order" → "alphabetical order (Yasson default)".
- `capsa-observability/src/test/java/com/capsa/observability/TestTransactionRegistry.java`
  — new in-memory TSR test helper.
- `capsa-observability/src/test/java/com/capsa/observability/Slf4jObservabilityTest.java`
  — rewritten: 15 tests (was 4); updated constructor calls.
- `docs/knowledge/observability/capsa-obs-001-observability-event-foundation.md`
  — flow diagram, §5 (new), §6, §7, §8, §9 updated; serializer corrected.
- `docs/engineering/agents/reports/CAPSA-ARCH-FIX-002.md` — this file.

No capability service files changed (`capsa-capture`, `capsa-lists`,
`capsa-items`, `capsa-classification`, `capsa-users`). No `capsa-runtime`
source files changed.

## Evidence

**Transaction-aware buffer — key implementation path (`Slf4jObservability.java`):**

```java
@Override
public void emit(ObservabilityEvent event) {
    Objects.requireNonNull(event, "event");
    if (isTransactionActive()) {
        bufferInTransaction(event);
    } else {
        dispatchAsync(event);
    }
}

private boolean isTransactionActive() {
    try {
        return txRegistry.getTransactionStatus() == Status.STATUS_ACTIVE;
    } catch (Exception e) {
        return false;
    }
}

@SuppressWarnings("unchecked")
private void bufferInTransaction(ObservabilityEvent event) {
    try {
        var pending = (List<ObservabilityEvent>) txRegistry.getResource(PENDING_KEY);
        if (pending == null) {
            pending = new ArrayList<>();
            txRegistry.putResource(PENDING_KEY, pending);
            final List<ObservabilityEvent> toRelease = pending;
            txRegistry.registerInterposedSynchronization(new Synchronization() {
                @Override public void beforeCompletion() {}
                @Override public void afterCompletion(int status) {
                    if (status == Status.STATUS_COMMITTED) {
                        for (ObservabilityEvent e : toRelease) {
                            dispatchAsync(e);
                        }
                    }
                }
            });
        }
        pending.add(event);
    } catch (RuntimeException e) {
        errorLog.warn("Observability transaction buffering failed; dispatching immediately", e);
        dispatchAsync(event);
    }
}
```

**Async serialization — `serializeAndSink` runs entirely on the virtual thread:**

```java
private void dispatchAsync(ObservabilityEvent event) {
    try {
        dispatcher.execute(() -> serializeAndSink(event));
    } catch (RejectedExecutionException e) {
        errorLog.error("Observability dispatcher rejected event {}", event.eventId(), e);
    } catch (RuntimeException e) {
        errorLog.error("Observability enqueue failed for event {}", event.eventId(), e);
    }
}

private void serializeAndSink(ObservabilityEvent event) {
    String line;
    try {
        line = jsonb.toJson(event) + '\n';
    } catch (RuntimeException e) {
        errorLog.error("Observability event serialization failed", e);
        return;
    }
    try {
        sinkWriter.accept(line);
    } catch (RuntimeException e) {
        errorLog.error("Observability sink failed; dropping event", e);
    }
}
```

**Executor lifecycle:**

```java
@PreDestroy
public void onDestroy() {
    dispatcher.close();
    try { jsonb.close(); } catch (Exception ignored) { }
}
```

**Test coverage — behavioural test for rollback discards events:**

```java
@Test
void rollbackDiscardsBufferedEvents() {
    List<String> captured = new ArrayList<>();
    var txReg = new TestTransactionRegistry();
    var obs = obs(captured, txReg);

    txReg.begin();
    obs.emit(sampleEvent());
    obs.emit(sampleEvent());
    txReg.rollback();

    assertEquals(0, captured.size(), "rolled-back events must not reach the sink");
}
```

**End-to-end integration test (pre-existing, unmodified, passes):**
`capsa-runtime/src/test/java/com/capsa/runtime/ObservabilityWiringTest.java`
— CDI deployment succeeds; `ListService.create` emits through `Slf4jObservability`
end-to-end; business result returned. Observed log output in test run:

```
INFO  [capsa.observability] () {"type":"ListCreated","actor":{"kind":"User",
"userId":"3ec65f21-..."},"eventId":"21615187-...","listId":"affa0aef-...",
"name":"Observability smoke","occurredAt":"2026-10-04T16:19:42.785266Z"}
```

## Validation

```bash
./mvnw -pl capsa-observability test
```

```
Tests run: 23, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

```bash
./mvnw test
```

```
Tests run: 44, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Per-module breakdown:

| Module | Tests | Delta |
|---|---|---|
| `capsa-observability` | 23 | +11 (was 12) |
| `capsa-users` | 7 | 0 |
| `capsa-lists` | 7 | 0 |
| `capsa-classification` | 5 | 0 |
| `capsa-items` | 0 | 0 |
| `capsa-capture` | 0 | 0 |
| `capsa-runtime` | 44 | 0 |

JPMS module graph: `capsa-observability` compiles with `requires jakarta.transaction`
and `opens com.capsa.observability.internal`; all downstream modules compile
unchanged; `capsa-runtime` `@QuarkusTest` integration tests pass with the new
`@Inject TransactionSynchronizationRegistry` CDI constructor.

## Limitations

- **Best-effort after commit.** If the JVM process crashes after a successful
  transaction commit but before the dispatched virtual thread writes to the
  sink, the observability event is lost. This is accepted behavior in v0.1;
  a durable outbox is explicitly out of scope.
- **No ordering guarantee across concurrent requests.** `afterCompletion` runs
  on the committing thread; the virtual-thread submission is non-deterministic
  across concurrent requests. Emission order is guaranteed only within a single
  transaction.
- **No end-to-end rollback integration test.** The transaction-aware buffer is
  fully covered by unit tests using `TestTransactionRegistry`. An integration
  test forcing a real JTA rollback and asserting no event was logged was not
  added; it would require either a capturing log appender or inspecting
  `capsa.observability` logger output inside a `@QuarkusTest`.

## Open Follow-Up

None for this task. The following CAPSA-ARCH-REVIEW-002 findings are
**not** addressed here and remain open:

- M-2 — Capture resolution check-then-act race (concurrent resolve yields 500).
- M-3 — User provisioning check-then-insert race.
- M-4 — Domain layer is creation-only; lifecycle transitions bypass aggregates.
- M-5 — `Source.AUTO` is dead; auto-classification never records learning evidence.
- M-6 — Validation errors use hand-built JSON strings in REST resources.
- L-4 — Dead code: `getByUser`, `reconstitute`, `ItemAccessDeniedException`.
- L-5 — Capture classification invoked with an empty candidate list.
- L-6 — Inconsistent persistence-entity style in `capsa-capture`.
- L-7 — Hand-rolled JSON for candidate serialization.
- L-8 — `CaptureNormalizer` omits documented Unicode/punctuation normalization.
- L-9 — `OidcCurrentUser` deviates from constructor injection convention.

## Related Records

- **Task:** [CAPSA-ARCH-FIX-002](../tasks/CAPSA-ARCH-FIX-002.md)
- **Source review:** [CAPSA-ARCH-REVIEW-002](CAPSA-ARCH-REVIEW-002.md) — findings M-1, L-1, L-2
- **Foundation report:** [CAPSA-OBS-001-report](CAPSA-OBS-001-report.md) — original observability implementation
- **Knowledge:** [capsa-obs-001-observability-event-foundation.md](../../knowledge/observability/capsa-obs-001-observability-event-foundation.md) — updated by this task
- **Engineering log:** [ENGINEERING_LOG.md](../../ENGINEERING_LOG.md)
