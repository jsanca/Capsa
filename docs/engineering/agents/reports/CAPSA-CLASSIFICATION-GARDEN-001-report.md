# CAPSA-CLASSIFICATION-GARDEN-001 Engineering Report

**Task:** Simplify Classifier Composition  
**Status:** DONE  
**Date:** 2026-10-03

---

## Motivation

`DefaultClassifier` existed solely to bridge CDI injection into `ClassificationPipeline`. It held no independent invariants: its `classify()` allocated a new `ClassificationPipeline` and a new `KnownClassificationStrategy` on every call, and its `recordUserResolution()` was a verbatim mapping from domain object to entity. The indirection added a class with no semantic identity.

---

## Changes

### `ClassificationPipeline` — promoted to CDI bean, implements `Classifier`

**Before:** package-private class, constructor-takes-`List<ClassificationStrategy>`, exposes `execute()`.  
**After:** `@ApplicationScoped public class ClassificationPipeline implements Classifier`.

- CDI constructor: `@Inject ClassificationPipeline(KnownClassificationStrategy, ClassificationMemoryEntryRepository)` — receives its single v0.1 strategy as an injected collaborator rather than constructing it on each invocation.
- Package-private `ClassificationPipeline(List<ClassificationStrategy>)` retained for unit tests; CDI will ignore it (only the `@Inject`-annotated constructor is used by ArC).
- `execute()` renamed to `classify()` (satisfies `Classifier`).
- `recordUserResolution()` moved here from `DefaultClassifier`, injecting `ClassificationMemoryEntryRepository` directly. No intermediate service extracted: the mapping is trivial and a `ClassificationMemoryService` would be a naming artefact with no behaviour of its own. This disposition is noted here; S-05 may revisit if the mapping grows.

### `KnownClassificationStrategy` — CDI bean

- Added `@ApplicationScoped`.
- Constructor annotated `@Inject` (was already constructor injection, now CDI-owned).

### `DefaultClassifier` — deleted

No remaining responsibility after the above.

### `ClassificationPipelineTest` — updated

Three call sites changed from `.execute(...)` to `.classify(...)`. No structural change; the test continues to exercise the pipeline short-circuit and empty-pipeline invariants via the test-only constructor.

---

## `recordUserResolution` Disposition

Injecting `ClassificationMemoryEntryRepository` directly into `ClassificationPipeline` is intentional for v0.1. The alternatives considered:

| Option | Verdict |
|---|---|
| Minimal `ClassificationMemoryService` wrapper | Deferred — adds a class with no behaviour beyond delegation; premature for one operation |
| Leave in `DefaultClassifier` | Rejected — `DefaultClassifier` no longer has a role |
| Direct repository injection into `ClassificationPipeline` | **Chosen** — minimal, correct, revisable in S-05 |

---

## Test Results

```
Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
```

All modules: capsa-users, capsa-lists, capsa-items, capsa-capture, capsa-classification, capsa-runtime.

---

## Files Changed

| File | Change |
|---|---|
| `capsa-classification/…/service/ClassificationPipeline.java` | Rewritten: `@ApplicationScoped`, implements `Classifier`, CDI + test constructors |
| `capsa-classification/…/service/KnownClassificationStrategy.java` | Added `@ApplicationScoped`, `@Inject` on constructor |
| `capsa-classification/…/service/DefaultClassifier.java` | Deleted |
| `capsa-classification/…/service/ClassificationPipelineTest.java` | `execute()` → `classify()` at three call sites |
