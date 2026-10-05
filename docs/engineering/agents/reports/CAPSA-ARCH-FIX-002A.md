# CAPSA-ARCH-FIX-002A — Doomed Transaction Observability Guard — Report

## Status

Complete.

## Objective

Close the v0.1 limitation recorded in
[CAPSA-ARCH-FIX-002](CAPSA-ARCH-FIX-002.md) §"Limitations":

> **`STATUS_MARKED_ROLLBACK` dispatches immediately.** `isTransactionActive()`
> returns `false` for any status other than `STATUS_ACTIVE` (including
> `STATUS_MARKED_ROLLBACK`). An event emitted while a transaction is already
> doomed is dispatched immediately rather than buffered. This is a conservative
> choice to avoid `putResource` raising `IllegalStateException` on a marked
> transaction.

`isTransactionActive()` was a binary check. The dispatch decision must
instead recognize that a transaction marked for rollback (or otherwise
past `STATUS_ACTIVE` and not yet `STATUS_NO_TRANSACTION`) cannot commit.
Under the invariant

> An event associated with business work that has not successfully
> committed must never be emitted as if that work occurred.

such events must be silently discarded — neither buffered nor dispatched.

This follow-up does **not** refactor the observability architecture, does
not extract abstractions, and touches no capability service.

## Behavior changed

Before: `emit()` checked `STATUS_ACTIVE` only. Anything else —
`STATUS_MARKED_ROLLBACK`, `STATUS_ROLLING_BACK`, `STATUS_ROLLEDBACK`,
`STATUS_COMMITTING`, `STATUS_COMMITTED`, `STATUS_PREPARING`,
`STATUS_PREPARED`, `STATUS_UNKNOWN` — fell through to immediate
asynchronous dispatch.

After: `emit()` switches on `txRegistry.getTransactionStatus()`:

| Status                        | Action |
|-------------------------------|--------|
| `STATUS_ACTIVE`               | Buffer the event; existing per-transaction `Synchronization` releases on `STATUS_COMMITTED`. |
| `STATUS_NO_TRANSACTION`        | Dispatch asynchronously (existing best-effort behavior). |
| Any other status              | Silently discard. Do not buffer. Do not dispatch. |

Registry exception behavior is preserved: `DISPATCH` is the safer default
than guessing.

The semantics of `STATUS_ACTIVE` + `COMMITTED` and `STATUS_ACTIVE` +
`ROLLBACK` are unchanged. `STATUS_NO_TRANSACTION` dispatch is unchanged.
Only the "active-ish but not committable" statuses change behavior.

## Files Changed

- `capsa-observability/src/main/java/com/capsa/observability/internal/Slf4jObservability.java`
  — replaced `isTransactionActive()` boolean with `currentDispatchMode()`
  switch over `txRegistry.getTransactionStatus()`. Added a private
  three-valued `enum DispatchMode { BUFFER, DISCARD, DISPATCH }`.
  Updated class Javadoc §"Transaction-aware dispatch" to enumerate the
  three branches and the statuses covered by the `DISCARD` branch.
  No public API change. `Observability.emit(event)` signature unchanged.
  Async dispatch, JSON-B serialization, and `@PreDestroy` lifecycle
  unchanged.
- `capsa-observability/src/test/java/com/capsa/observability/TestTransactionRegistry.java`
  — added a single package-private `markRollbackOnly()` helper that
  transitions the registry to `STATUS_MARKED_ROLLBACK`. No other change.
- `capsa-observability/src/test/java/com/capsa/observability/Slf4jObservabilityTest.java`
  — added a new section "Doomed transaction guard (CAPSA-ARCH-FIX-002A)"
  with three behavioral tests. See "Tests added" below.
- `docs/knowledge/observability/capsa-obs-001-observability-event-foundation.md`
  §5 — replaced the prose "emit checks STATUS_ACTIVE" paragraph with a
  status-to-action table; added the doomed-transaction flow diagram.
- `docs/engineering/agents/reports/CAPSA-ARCH-FIX-002.md` — removed the
  `STATUS_MARKED_ROLLBACK dispatches immediately` bullet from
  §"Limitations".
- `docs/engineering/ENGINEERING_LOG.md` — added the
  `CAPSA-ARCH-FIX-002A` row.
- `docs/engineering/agents/reports/CAPSA-ARCH-FIX-002A.md` — this file.

No capability service file changed. No `capsa-runtime` source file
changed. No `pom.xml`, `module-info.java`, or JPMS declaration changed.

## Transaction statuses handled

| `Status` constant          | int | `emit()` action |
|---------------------------|----:|-----------------|
| `STATUS_ACTIVE`           |   0 | Buffer; release on `STATUS_COMMITTED` |
| `STATUS_MARKED_ROLLBACK`  |   1 | **Discard** |
| `STATUS_PREPARED`         |   2 | Discarded |
| `STATUS_COMMITTED`        |   3 | Discarded |
| `STATUS_ROLLEDBACK`       |   4 | Discarded |
| `STATUS_UNKNOWN`          |   5 | Discarded |
| `STATUS_NO_TRANSACTION`   |   6 | Dispatch immediately |
| `STATUS_PREPARING`        |   7 | Discarded |
| `STATUS_COMMITTING`       |   8 | Discarded |
| `STATUS_ROLLING_BACK`     |   9 | Discarded |

`STATUS_COMMITTED` discarded by `emit()` is consistent: when the JTA
transition fires `afterCompletion(STATUS_COMMITTED)`, the registered
`Synchronization` is what releases the buffered events for async
dispatch. A subsequent `emit()` is not on a transaction at all (the
registry has reset to `STATUS_NO_TRANSACTION`), so the new behavior
matches reality. The same reasoning applies to the other completion
statuses.

## Tests added

Three tests in `Slf4jObservabilityTest`:

- `markedRollbackStatusDiscardsEvent` — `markRollbackOnly()` on a fresh
  registry, then `emit(event)`; the sink is not written.
- `markedRollbackStatusDiscardsEventEvenAfterRollback` — same setup
  followed by `rollback()`; still no sink write.
- `emitAfterTransactionMarkedRollbackIsDiscarded` — `begin()`,
  `emit(event1)` (buffered), `markRollbackOnly()`,
  `emit(event2)` (discarded), `rollback()`; both events are discarded
  (the first by the synchronization's `STATUS_ROLLEDBACK` branch that
  was already in place; the second by the new `DISCARD` branch).

Existing tests already cover the other required scenarios:

- `STATUS_NO_TRANSACTION` still dispatches — `emitOutsideTransaction_dispatchedImmediately`.
- `STATUS_ACTIVE` + commit still dispatches — `commitReleasesBufferedEvent`.
- `STATUS_ACTIVE` + rollback still discards — `rollbackDiscardsBufferedEvents`.

## Documentation updated

- §5 "Transaction-Aware Dispatch" of
  `capsa-obs-001-observability-event-foundation.md` now describes the
  three-branch decision and includes a "transaction marked for rollback
  → discard" flow alongside the existing "commit → release" and
  "rollback → discard" flows.
- The "STATUS_MARKED_ROLLBACK dispatches immediately" bullet was
  removed from CAPSA-ARCH-FIX-002.md §"Limitations". No other sections
  of that report changed.
- No other observability documentation referenced the previous
  `isTransactionActive()` implementation in a way that became stale.

## Validation

```bash
./mvnw -pl capsa-observability test
```

```
[INFO] Tests run: 26, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

```bash
./mvnw test
```

```
[INFO] Tests run: 44, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Per-module breakdown (post-fix):

| Module                | Tests | Delta vs. CAPSA-ARCH-FIX-002 |
|-----------------------|------:|-----------------------------:|
| `capsa-observability` |    26 |                            +3 |
| `capsa-users`         |     7 |                              0 |
| `capsa-lists`         |     7 |                              0 |
| `capsa-classification`|     5 |                              0 |
| `capsa-items`         |     0 |                              0 |
| `capsa-capture`       |     0 |                              0 |
| `capsa-runtime`       |    44 |                              0 |
| **Total (per-module)**|    89 |                            +3 |

JPMS module graph unchanged. `capsa-runtime` `@QuarkusTest`
integration tests (`ObservabilityWiringTest`,
`ListResourceTest`, `ItemResourceTest`) continue to pass end-to-end:
real CDI deployment, real `TransactionSynchronizationRegistry`,
committed business writes produce sink output.

## Limitations deliberately retained

None introduced by this change. Existing v0.1 limitations
("Best-effort after commit", "No ordering guarantee across concurrent
requests", "No end-to-end rollback integration test") from
CAPSA-ARCH-FIX-002 still apply and remain out of scope.

## Related Records

- Source review: [CAPSA-ARCH-REVIEW-002](CAPSA-ARCH-REVIEW-002.md) — finding L-3 (incidental) and the closed limitation in CAPSA-ARCH-FIX-002
- Predecessor: [CAPSA-ARCH-FIX-002](CAPSA-ARCH-FIX-002.md) — transaction-aware dispatch foundation
- Knowledge: [capsa-obs-001-observability-event-foundation.md](../../knowledge/observability/capsa-obs-001-observability-event-foundation.md) §5 updated
- Engineering log: [ENGINEERING_LOG.md](../../ENGINEERING_LOG.md) — row added