# CAPSA-CLASSIFICATION-GARDEN-002 Engineering Report

**Task:** Separate Classification from Resolution Learning  
**Status:** DONE  
**Date:** 2026-10-03

---

## Motivation

After GARDEN-001, `Classifier` still exposed `recordUserResolution(...)` — a write operation with no relationship to classification. This conflated two distinct responsibilities:

- "Given this input, how should it be classified?" (`Classifier`)
- "Persist knowledge learned from a confirmed resolution." (resolution learning)

`ClassificationPipeline` inherited both: it carried `ClassificationMemoryEntryRepository`, transaction semantics, and domain-to-entity mapping alongside its classification loop. Removing that coupling makes each boundary legible and gives the future Capture workflow a clean injection point for recording resolutions independently.

---

## Changes

### `Classifier` — classification only

Removed `recordUserResolution(...)`. The interface is now:

```java
public interface Classifier {
    ClassificationResult classify(ClassificationRequest request);
}
```

### `ClassificationService` — new API

New public interface in `com.capsa.classification.api`:

```java
public interface ClassificationService {
    void recordResolution(UserId userId, String normalizedContent, UUID selectedListId);
}
```

The Capture orchestration workflow will inject this when it needs to persist a confirmed user resolution. This task does not implement that orchestration.

### `ClassificationServiceImpl` — new internal implementation

`@ApplicationScoped class ClassificationServiceImpl implements ClassificationService` in `internal.service`.

Owns:
- `ClassificationMemoryEntry` domain object creation
- domain-to-entity mapping
- `ClassificationMemoryEntryRepository` (write)
- `@Transactional`

### `ClassificationPipeline` — stripped to classification only

Removed:
- `ClassificationMemoryEntryRepository` dependency
- `recordUserResolution()` implementation
- `@Transactional`
- the `null` repository assignment in the test constructor

CDI constructor now: `@Inject ClassificationPipeline(KnownClassificationStrategy)`.  
Test constructor unchanged: `ClassificationPipeline(List<ClassificationStrategy>)`.

### Repository ownership after this change

```
ClassificationPipeline
        └── strategies
                └── KnownClassificationStrategy
                        └── ClassificationMemoryEntryRepository (READ)

ClassificationServiceImpl
        └── ClassificationMemoryEntryRepository (WRITE)
```

Both internal components share the same classification-owned repository. The repository remains package-private to `capsa.classification`.

### `ClassificationTest` — updated

`Classifier.recordUserResolution(...)` → `ClassificationService.recordResolution(...)`.  
`ClassificationService` injected alongside `Classifier`. No assertions weakened.

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
| `capsa-classification/…/api/Classifier.java` | Removed `recordUserResolution` |
| `capsa-classification/…/api/ClassificationService.java` | Created — `recordResolution` |
| `capsa-classification/…/service/ClassificationServiceImpl.java` | Created — owns resolution persistence + `@Transactional` |
| `capsa-classification/…/service/ClassificationPipeline.java` | Removed repository dependency, `recordUserResolution`, `@Transactional`, null workaround |
| `capsa-runtime/…/ClassificationTest.java` | Inject `ClassificationService`, call `recordResolution` |
