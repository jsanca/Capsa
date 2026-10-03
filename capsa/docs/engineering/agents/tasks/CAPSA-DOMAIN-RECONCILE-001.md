# CAPSA-DOMAIN-RECONCILE-001 — Reconcile Capsa v0.1 Domain Model

## Role

Use the appropriate **software-engineer / documentation reconciliation** discipline.

This is a reconciliation task.

Do not implement production code.

## Objective

Reconcile `capsa-domain-model-v0.1.md` with the findings from `CAPSA-DOMAIN-REVIEW-001`.

The result should be a coherent reviewed v0.1 domain model suitable as input to the next architecture phase.

Do not redesign areas that survived review.

Do not begin Quarkus, JPMS, persistence, REST, or infrastructure design.

## Required Reading

Read:

- Capsa product intent: `docs/engineering/agents/intent/intent1.md`
- UC-01 through UC-06: `docs/knowledge/use-cases/`
- `docs/engineering/agents/tasks/CAPSA-DOMAIN-001.md`
- `docs/knowledge/domain/capsa-domain-model-v0.1.md`
- `docs/engineering/agents/tasks/CAPSA-DOMAIN-REVIEW-001.md`
- `docs/engineering/agents/reviews/CAPSA-DOMAIN-REVIEW-001-domain-model-review.md`

Treat product intent and use cases as authoritative.

Treat accepted review findings as corrections/refinements to the derived domain model.

## Required Reconciliation

### F-01 — Capture / Item Invariant

Keep `ClassificationAttempt` within the `Capture` aggregate boundary.

Correct the stated invariant. Split into:

- **Capture-internal invariant** (enforceable at aggregate root): a Capture accepts at most one `ClassificationResolution`; `pipelineResult` and `resolution` are append-only.
- **Cross-aggregate guarantee** (requires application coordination): exactly one `Item` per resolved Capture — enforced by application via transaction, idempotency, and a persistence uniqueness constraint (e.g. unique index on `Item.captureId`).

Do not design the persistence mechanism.

### F-02 — Classification Execution Failure

Specify the `ClassificationStrategy` port's error contract.

Document that execution failures (timeout, provider failure, unavailable service) propagate as an exception/error channel from the strategy, bypassing `ClassificationResult`, and are mapped to `Capture.processingStatus = FAILED` by the application.

Keep `ClassificationOutcome` as `CLASSIFIED | NEEDS_RESOLUTION` only.

Keep `FAILED` out of the candidates/confidence semantics.

### F-03 — ClassificationMemory Aggregate Boundary

Reclassify:

- `ClassificationMemoryEntry` — independently stored evidence; its own unit of identity and persistence.
- `ClassificationMemory` — a domain/application capability for recording and querying evidence; not an aggregate root.

The principle that Classification Memory is an evidence store and that conflicting entries are valid must be preserved.

### F-04 — CaptureNormalizer Placement

Reclassify `CaptureNormalizer` from infrastructure port to domain-owned service or pure function.

Normalization is deterministic core behavior. Its stability is a correctness requirement for Known Classification.

Preserve the distinction between normalization (deterministic) and interpretation (non-deterministic, provider-dependent port).

### F-05 — ClassificationMemory User Scope

Resolve ClassificationMemory as **User-scoped**.

Add `userId` to `ClassificationMemoryEntry` (or equivalent User-scoping mechanism) and state it explicitly.

### F-06 — Item Lifecycle Invariant

Replace:

```
completedAt == null iff status == PENDING
completedAt != null iff status == DONE
```

with:

```
completedAt != null iff status == DONE
completedAt == null for all non-DONE states
```

### F-07 — ClassificationPipeline Placement

Do not force a domain-vs-application placement decision.

Either label it application orchestration or record placement as an architecture decision.

Preserve: (a) depends only on `ClassificationStrategy` port; (b) ordering and thresholds are injected.

### F-08 — Strategy Port Observation

No change. Record as evolutionary consideration in the model.

### F-09 — Authorization as Application Policy

Keep "User may contribute to List" in the domain vocabulary.

Move the ownership check out of the domain invariants section; document it as application-layer policy.

### Remaining Open Questions

Preserve correctly deferred open questions. Close question #5 (ClassificationMemory scope) as resolved to per-User. Mark architecture decisions explicitly as such.

## Preserve Positive Findings

Do not disturb P-01 through P-07 from the review unless a concrete reconciliation finding requires it.

## Deliverables

1. Update `docs/knowledge/domain/capsa-domain-model-v0.1.md`.
2. Create reconciliation report at `docs/engineering/agents/reports/CAPSA-DOMAIN-RECONCILE-001-reconciliation-report.md`.
3. Update `docs/engineering/ENGINEERING_LOG.md`.

## Completion Criteria

1. Every review finding has an explicit disposition.
2. Accepted findings are reflected in the domain model.
3. Deferred architecture questions remain visibly deferred.
4. No implementation or infrastructure design has been introduced.
5. UC-01 through UC-06 remain supported.
6. The domain model is internally coherent.
7. The reconciled model is ready to serve as input to architecture design.
