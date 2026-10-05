# S-05 Report — Capture Capability + UC-03 + UC-04

## Summary

Implemented the `capsa-capture` module: the two-transaction orchestration flow that accepts raw user input, classifies it, and either creates an Item automatically (UC-03 CLASSIFIED) or surfaces candidates for user resolution (UC-03 NEEDS_RESOLUTION → UC-04).

## Files Created

### `capsa-capture` module bootstrap (from prior session)
- `pom.xml` — dependencies: capsa-users, capsa-lists, capsa-items, capsa-classification, jakarta APIs
- `module-info.java` — exports `com.capsa.capture.api`; opens all internal packages for CDI/Hibernate reflection

### API
- `CaptureId.java` — UUID wrapper record
- `SubmitCaptureCommand.java` — record with `content`
- `CaptureResult.java` — sealed interface: `Classified(ItemView)` | `NeedsResolution(CaptureId, List<ClassificationCandidate>)`
- `CaptureService.java` — `submit(UserId, String)` + `resolve(UserId, CaptureId, ListId)`
- `CaptureNotFoundException.java`
- `CaptureNotAwaitingResolutionException.java`

### Internal Domain
- `Capture.java` — domain record: `(UUID captureId, String originalContent, String normalizedContent)`
- `CaptureNormalizer.java` — static: `strip().toLowerCase().replaceAll("\\s+", " ")`

### Internal Persistence
- `CaptureEntity.java` — `@Entity` for `captures` table
- `ClassificationAttemptEntity.java` — `@Entity` for `classification_attempts` table
- `ClassificationResolutionEntity.java` — `@Entity` for `classification_resolutions` table
- `CaptureRepository.java` — `persist()` + `findById()`
- `ClassificationAttemptRepository.java` — `persist()` + `findByCaptureId()`
- `ClassificationResolutionRepository.java` — `persist()`

### Internal Service
- `CaptureCreationService.java` — `@Transactional` TX-1: persists `CaptureEntity` (PROCESSING) + `ClassificationAttemptEntity`
- `CaptureResolutionService.java` — `@Transactional` TX-2: `resolveCapture()`, `storeNeedsResolution()`, `markFailed()`, `resolveFromUser()` (UC-04)
- `CaptureServiceImpl.java` — `@ApplicationScoped` NOT `@Transactional`; orchestrates TX-1 → classify() → TX-2

### Internal REST
- `CaptureResource.java` — `POST /capsa/api/captures` (submit) + `POST /capsa/api/captures/{id}/resolution` (resolve)

### Flyway Migrations
- `V005__captures_initial.sql` — `captures` table + indexes
- `V006__classification_attempts_initial.sql` — `classification_attempts` + `classification_resolutions` tables

### Runtime Configuration
- `application.properties` — added `quarkus.index-dependency.capture.*`

### Tests
- `CaptureResourceTest.java` — 14 tests: TC-UC03-001..006, TC-UC04-001..008

## Key Decisions

**Two-transaction pattern**: `CaptureServiceImpl` is intentionally NOT `@Transactional`. TX-1 persists the Capture before classification. The classifier call is outside any transaction. TX-2 resolves the outcome. This separates capture preservation from classification failure.

**Capture ownership check**: `resolveFromUser()` throws `CaptureNotFoundException` if the requesting user's ID doesn't match the capture's `user_id`. This avoids leaking capture existence to other users. Specific HTTP status codes for cross-user scenarios deferred to S-06.

**`captureId` in `ItemView`**: When a capture is auto-classified (CLASSIFIED), the captureId is surfaced via `item.captureId()` in the response. This enables tests and callers to verify the Capture→Item trace. Exposing `captureId` explicitly in the CLASSIFIED response body is deferred to S-06.

**Candidates serialization**: `classification_attempts.candidates` is stored as a simple JSON string without an extra library. Manual serialization sufficient for v0.1.

## Test Results

All 42 tests pass (14 new + 28 existing):
- `CaptureResourceTest`: 14/14
- `ItemResourceTest`: 17/17
- `ClassificationTest`: 2/2
- `ListResourceTest`: 5/5
- `UserProvisioningTest`: 3/3

## Limitations / Deferred to S-06

- CLASSIFIED response does not include `captureId` in the HTTP response body — only in `ItemView.captureId`
- HTTP-level test for "resolve CLASSIFIED capture" uses service injection (not pure HTTP) because there is no direct HTTP way to obtain the `captureId` from a 201/CLASSIFIED response
- Cross-user error codes return 404 (not 403) to avoid leaking capture existence — exact S-06 security policy TBD
- `CaptureResource` catches `Exception` broadly on submit and returns 500 — fine for v0.1
