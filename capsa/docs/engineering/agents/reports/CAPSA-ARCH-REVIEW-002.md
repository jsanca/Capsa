# CAPSA-ARCH-REVIEW-002 — Integrated Architecture Review

**Role:** Architecture Reviewer
**Task:** CAPSA-ARCH-REVIEW-002
**Input authority:** implemented code (evidence of record), `capsa-arch-001`, `capsa-domain-model-v0.1`, `capsa-obs-001`, CAPSA-ARCH-REVIEW-001, CAPSA-ARCH-RECONCILE-001
**Status:** Review complete
**Verdict:** READY FOR S-07 WITH NON-BLOCKING FINDINGS

---

## 1. Executive Assessment

The implemented system preserves the intended architecture. The JPMS module
graph is acyclic, capability boundaries remain meaningful, `capsa-runtime`
is still a composition root rather than a business module, and the
two-transaction capture flow (TX-1 → classification → TX-2) is present and
correct in the code. The `capsa-observability` module is a well-placed leaf
with a sound port/contract/adapter split.

The dominant theme of this review is **documentation drift, not structural
damage.** The architecture was refined during implementation (GARDEN-001,
GARDEN-002, DI-HYGIENE-001, S-06) but the canonical documents
(`capsa-arch-001`, `capsa-domain-model-v0.1`, `capsa-obs-001`) were not
updated to match. Several findings below are disagreements between the
documents and the code, reported as evidence.

The secondary theme is **incomplete realization of the domain layer as a
true aggregate-root layer.** Domain objects are used as creation/validation
factories; state transitions (item completion, capture resolution) mutate
persistence entities directly with raw string status values. This is not a
safety defect, but it means the "aggregate root enforces invariants" model
documented in `capsa-domain-model-v0.1` is only partially realized.

No CRITICAL or HIGH findings. All findings are MEDIUM or below and none
block the start of S-07 deployment work.

---

## 2. Current Module / Dependency Model

The actual graph, derived from `module-info.java` and the Maven reactor
(`pom.xml` module order), is:

```
runtime[capsa.runtime]
   ├── capture, classification, items, lists, users, observability
capture
   ├── classification, items, lists, users, observability
items
   ├── lists, users, observability
lists
   ├── users, observability
classification
   ├── users, observability
users        (leaf)
observability (leaf)
```

This matches `capsa-arch-001` §4 with one addition: `capsa-observability`
is a new leaf that `lists`, `items`, `classification`, `capture`, and
`runtime` all depend on. The graph is acyclic. `users` and `observability`
depend on no other `capsa.*` module.

Observed deviations from the documented graph/justifications:

- **`capture → lists` is now type-only.** `capsa.capture` imports only
  `com.capsa.lists.api.ListId`; it never calls `ListService`. The
  documented "capture fetches candidate lists via `getByUser`" and "capture
  calls `verifyContributionAccess`" do not happen (list access is delegated
  to `items`; classification is invoked with an empty candidate list). See
  §3 M-4/L-5.
- **`Classifier` shrank.** After GARDEN-002, `Classifier` has only
  `classify(ClassificationRequest)`; `recordUserResolution` moved to
  `ClassificationService`. `capsa-arch-001` §5/§8 still show both methods
  on `Classifier`.

The `exports`/`opens` surface is otherwise as designed: each capability
exports only its `api` package, and `internal.*` packages are `opens`
(targeted) or unopened.

---

## 3. Findings (ordered by severity)

### M-1 — Observability emission runs inside the business transaction, before commit

- **Severity:** MEDIUM
- **Location:** `capsa-capture/.../CaptureCreationService.java:54`,
  `CaptureResolutionService.java:85,110,160`,
  `capsa-items/.../ItemServiceImpl.java:51,70,98`,
  `capsa-lists/.../ListServiceImpl.java:45`,
  `capsa-classification/.../ClassificationServiceImpl.java:50`
- **Evidence:** Every `observability.emit(...)` call sits inside a
  `@Transactional` method, before the method returns (hence before the
  transaction commits). The documented contract states the opposite —
  CAPSA-OBS-001 report ("Emit after the business write, not before. A
  rolled-back transaction must not emit.") and `capsa-obs-001` §8
  ("`emit()` is always called **after** the capability method has done its
  work … If the business transaction rolls back, no event has been
  emitted yet."). In `CaptureResolutionService.resolveCapture` and
  `resolveFromUser`, multiple events are emitted per transaction
  (`CaptureClassified`/`CaptureResolved` plus the nested
  `ItemCreated` from `ItemServiceImpl.createFromCapture`, plus
  `ClassificationRecorded`).
- **Why it matters:** On commit failure, an event for a business write that
  never persisted can still be logged (phantom event). Observability is
  documented as best-effort and non-audit, so there is no data-integrity
  impact, but the documented invariant is not honored and a downstream
  future sink that assumes "event ⇒ committed write" would be wrong.
- **Recommended direction:** Move `emit()` after the transaction boundary —
  e.g. emit in the non-transactional orchestrator (`CaptureServiceImpl.submit`)
  after `createCapture`/`resolveCapture` return, or introduce an
  after-commit hook. At minimum, correct the documentation to state the
  actual semantics ("emit before commit, best-effort").
- **Blocks S-07:** No.

### M-2 — Capture resolution is check-then-act without optimistic locking; concurrent duplicate resolve fails as 500

- **Severity:** MEDIUM
- **Location:** `capsa-capture/.../CaptureResolutionService.java:130-171`
- **Evidence:** `resolveFromUser` reads the capture, checks
  `"NEEDS_RESOLUTION".equals(entity.processingStatus)`, then creates the
  item. There is no `@Version`/optimistic lock or
  `SELECT … FOR UPDATE`. Two concurrent `POST /captures/{id}/resolution`
  calls can both read `NEEDS_RESOLUTION` and both proceed. The second
  insert trips `uq_items_capture_id` (`V003__items_initial.sql`) or
  `uq` on `classification_resolutions.capture_id`
  (`V006__classification_attempts_initial.sql`) and surfaces as an
  unhandled `PersistenceException` → 500 via `FallbackExceptionMapper`,
  not the intended 409 `CAPSA_CAPTURE_NOT_AWAITING_RESOLUTION`.
- **Why it matters:** The two-level "exactly one Item per resolved Capture"
  guarantee (CAPSA-DOMAIN-RECONCILE-001 F-01) is preserved — the DB
  constraint prevents duplication — but the failure mode is an opaque 500
  rather than a graceful idempotent/409 response. The architecture §12
  claims "idempotent resolution"; the idempotency is enforced by the
  database, not the application.
- **Recommended direction:** Either add optimistic locking to the capture
  status transition and translate the resulting constraint/optimistic-lock
  failure to a 409, or explicitly document that concurrent resolution
  yields a 500 (unacceptable for an API). Low effort: catch the
  `PersistenceException`/`ConstraintViolationException` on the
  `createFromCapture` path and rethrow as
  `CaptureNotAwaitingResolutionException`.
- **Blocks S-07:** No (narrow window, single-user v0.1), but should be
  reconciled before any multi-client rollout.

### M-3 — User provisioning is check-then-insert with no race recovery

- **Severity:** MEDIUM
- **Location:** `capsa-users/.../UserServiceImpl.java:27-42`,
  `UserRepository.findByOidcSubject`
- **Evidence:** `findOrProvision` does `findByOidcSubject` → `orElseGet`
  (create + persist). The `users.oidc_subject` UNIQUE constraint
  (`V001__users_initial.sql`) is the only guard. Two concurrent first
  requests for a new OIDC subject both see "not found" and both insert; the
  loser gets an unhandled constraint violation → 500.
  `UserProvisioningTest.findOrProvisionIsIdempotent` only exercises
  sequential calls, not concurrency.
- **Why it matters:** A first-login burst (e.g., a mobile client firing
  several requests on first launch) can produce a transient 500. The
  documented contract — "provisioning is idempotent on oidcSubject" — holds
  only for sequential calls.
- **Recommended direction:** Catch the unique-constraint failure and
  re-query (idempotent-retry), or use an atomic `INSERT … ON CONFLICT DO
  NOTHING` + select. Low effort; deferrable but recommended before S-07.
- **Blocks S-07:** No.

### M-4 — The domain layer is creation-only; lifecycle state transitions bypass the aggregates

- **Severity:** MEDIUM
- **Location:** `capsa-items/.../ItemServiceImpl.java:92-105` vs
  `Item.java:107-111`; `capsa-capture/.../Capture.java` (3-field record);
  `CaptureResolutionService.java` (entity mutation)
- **Evidence:**
  - `Item.complete()` (the domain transition method) is **never called**;
    `ItemServiceImpl.complete` mutates `ItemEntity` directly
    (`entity.setStatus("DONE"); entity.setCompletedAt(...)`).
  - `ItemStatus` is an enum only inside `Item`; it is flattened to a raw
    `String` in `ItemEntity.status` and in `ItemView.status`, so the enum
    provides no type safety at the persistence or API boundary.
  - `Capture` is a 3-field record (`captureId`, `originalContent`,
    `normalizedContent`) with no `userId`, `status`, or `capturedAt`; all
    real capture state and transitions live on `CaptureEntity`.
  - `User.reconstitute`, `CapsaList.reconstitute`, and
    `ClassificationMemoryEntry.reconstitute` are dead code (never called —
    converters go straight from entity to view).
- **Why it matters:** `capsa-domain-model-v0.1` describes aggregate roots
  that enforce their own invariants. In practice the domain objects are
  validation factories; the persistence entities are the real mutable
  aggregates, and lifecycle rules are duplicated (or only present) in the
  service layer. This is a coherence gap, not a correctness bug, but it
  means the "domain invariant" documentation overstates what the code
  enforces.
- **Recommended direction:** Either (a) route the completion transition
  through the `Item` domain object and stop storing status as a raw
  string, or (b) consciously downscope the domain model to "domain value
  objects used for creation + validation" and update the documentation. Do
  not add a converter layer solely for symmetry.
- **Blocks S-07:** No.

### M-5 — Automatic classification never records learning evidence; `Source.AUTO` is dead and the docs claim otherwise

- **Severity:** MEDIUM
- **Location:** `capsa-classification/.../ClassificationServiceImpl.java:35-41`,
  `capsa-capture/.../CaptureResolutionService.java:64-96`,
  `capsa-classification/.../domain/ClassificationMemoryEntry.java:39`
- **Evidence:**
  - `ClassificationServiceImpl.recordResolution` hardcodes
    `Source.USER_CONFIRMED`; the `ClassificationService` javadoc says "The
    Source recorded is determined by the caller", which is false — the
    caller cannot choose.
  - Only `CaptureResolutionService.resolveFromUser` (UC-04) calls
    `recordResolution`. `resolveCapture` (UC-03 auto path) does not.
  - `KnownClassificationStrategy` queries only
    `findLatestUserConfirmed` (no fallback to `AUTO`, contrary to the
    domain model's "latest USER_CONFIRMED, fall back to AUTO" policy).
  - `Source.AUTO` is defined but never produced anywhere.
  - `ClassificationRecorded` event javadoc claims it is emitted for "UC-04
    … and UC-03 auto-classified"; in reality it is emitted only from
    `recordResolution` (UC-04 only).
- **Why it matters:** The learning loop described in
  `capsa-domain-model-v0.1` §2 ("fed by both AUTO and USER_CONFIRMED") and
  `capsa-arch-001` §12 (UC-03 records evidence) is not implemented. In
  effect the system learns only from explicit user confirmations. This is a
  defensible, arguably safer design (never auto-learn from auto-decisions),
  but it contradicts the documents, and `Source.AUTO` being dead code means
  a reader will believe auto-learning exists when it does not.
- **Recommended direction:** Decide and reconcile: either implement AUTO
  evidence recording on the UC-03 CLASSIFIED path (with the AUTO→USER
  fallback policy), or explicitly document the USER_CONFIRMED-only design
  and remove the misleading `Source.AUTO` value, javadoc, and event
  doc. Flag this as a product/architecture decision, not a silent fix.
- **Blocks S-07:** No (behavior is internally consistent; it is the
  documentation that misleads).

### M-6 — Error architecture is split between exception mappers and hand-built JSON in REST resources

- **Severity:** MEDIUM
- **Location:** `capsa-lists/.../ListResource.java:41`,
  `capsa-items/.../ItemResource.java:46,52,69`,
  `capsa-capture/.../CaptureResource.java:42,71` vs
  `capsa-runtime/.../error/*.java`
- **Evidence:** Domain exceptions flow through a clean
  `ExceptionMapper` + `ErrorResponse` mechanism in `runtime`. Validation
  errors, however, are produced by each REST resource inline as a raw JSON
  string literal
  (`"{\"code\":\"CAPSA_VALIDATION_ERROR\",\"message\":\"name is required\"}"`).
  The `CAPSA_VALIDATION_ERROR` code is duplicated across three resources,
  there is no shared validator, and Jakarta Bean Validation (`@Valid`,
  `@NotBlank`) is not used despite being specified in `capsa-arch-001` §10
  and §16. `ErrorResponse` is bypassed for these paths.
- **Why it matters:** REST resources are independently inventing part of
  the error contract. The validation path diverges from the mapper path,
  making the error surface inconsistent and harder to evolve. This is
  exactly the class of drift the review was asked to look for.
- **Recommended direction:** Introduce a single
  `ConstraintViolationException`/`IllegalArgumentException` mapper (or adopt
  `@NotBlank` + `@Valid` and map violations), and route all validation
  errors through `ErrorResponse`. Small, mechanical change in `runtime` +
  resources.
- **Blocks S-07:** No.

### L-1 — Observability serialization placement and serializer drift

- **Severity:** LOW
- **Location:** `capsa-observability/.../Slf4jObservability.java:101-117`;
  `ObservabilityEvent.java:52-61`
- **Evidence:**
  - `emit()` performs `jsonb.toJson(event)` on the **caller thread** and
    dispatches only the sink write to the virtual thread. CAPSA-OBS-001
    report (§"Asynchronous dispatch") and `capsa-obs-001` §5 state the
    virtual thread "serializes the event and writes to the sink".
  - The documented hand-rolled `EventJson` serializer does not exist; the
    implementation uses Jakarta JSON-B (`@JsonbTypeInfo`/`@JsonbSubtype`
    annotations in the public `api` package). The CAPSA-OBS-001 report's
    "Hand-rolled JSON serializer rather than JSON-B" decision and its
    rationale ("no provider dependency … deterministic schema") were
    reversed in implementation without updating the documents.
  - JSON-B (Yasson) emits fields alphabetically (observed output:
    `type, actor, eventId, itemId, occurredAt`), not declaration order; the
    `ObservabilityEvent` javadoc claims "record fields in declaration
    order."
- **Why it matters:** The serialization contract is now coupled to a JSON-B
  provider's behavior (ordering, null handling, discriminator), which
  weakens the "versioned, provider-independent schema" rationale. The
  caller-thread serialization is defensible (deterministic, catches
  serialization errors synchronously, no JSON-B thread-safety concern), but
  it is not what the documents describe and it means `emit()` is not fully
  non-blocking.
- **Recommended direction:** Independent conclusion — the placement
  (serialize on caller, sink on virtual thread) is acceptable; do not
  change it. Update the knowledge page and OBS report to reflect JSON-B
  (and its field-ordering behavior) instead of `EventJson`, and correct the
  "serializes on the virtual thread" claim.
- **Blocks S-07:** No.

### L-2 — Virtual-thread executor has no lifecycle shutdown

- **Severity:** LOW
- **Location:** `capsa-observability/.../Slf4jObservability.java:73-79`
- **Evidence:** `Slf4jObservability` is `@ApplicationScoped` and constructs
  `Executors.newVirtualThreadPerTaskExecutor()` but has no `@PreDestroy` to
  `close()`/`shutdown()` it. `SynchronousSinkExecutor.isShutdown()` even
  returns `true` unconditionally, masking the pattern.
- **Why it matters:** A virtual-thread-per-task executor holds no pooled OS
  threads, so the leak is negligible in practice, but the resource is
  nonetheless never released and the contract is inconsistent.
- **Recommended direction:** Add a `@PreDestroy` that closes the executor,
  or document that virtual-thread executors are intentionally left to GC.
- **Blocks S-07:** No.

### L-3 — `capsa.observability` does not `opens` its internal package

- **Severity:** LOW
- **Location:** `capsa-observability/src/main/java/module-info.java`
- **Evidence:** Every capability module explicitly `opens` its
  `internal.service`/`internal.persistence`/`internal.rest` packages for
  CDI/ArC/Hibernate reflection. `capsa.observability` does **not** `opens
  com.capsa.observability.internal`, where its `@ApplicationScoped`
  `Slf4jObservability` lives. It works in JVM/dev/test (Quarkus's
  `AddOpensProcessor` compensates), but the module boundary is inconsistent
  with the rest of the codebase.
- **Why it matters:** Fragility: native-image or a stricter CDI runtime may
  not auto-open the package, and the inconsistency is a footgun for the
  next module.
- **Recommended direction:** Add `opens
  com.capsa.observability.internal;` to match the established pattern.
- **Blocks S-07:** No.

### L-4 — Dead code: `getByUser`, `reconstitute`, `ItemAccessDeniedException`

- **Severity:** LOW
- **Location:** `capsa-lists/.../ListService.java:49`,
  `capsa-lists/.../CapsaList.java:70`, `capsa-users/.../User.java:63`,
  `capsa-classification/.../ClassificationMemoryEntry.java:91`,
  `capsa-items/.../ItemAccessDeniedException.java`
- **Evidence:** `ListService.getByUser` is never called by any module or
  resource (capture passes `List.of()` candidates instead). The three
  `reconstitute` methods are never invoked. `ItemAccessDeniedException` is
  declared in `ItemService` javadoc and has a mapper, but `ItemServiceImpl`
  never throws it (it propagates `ListAccessDeniedException` from
  `verifyContributionAccess`), so `ItemAccessDeniedExceptionMapper` is
  unreachable.
- **Why it matters:** These are speculative/duplicated abstractions that do
  not currently pay for themselves; they mislead readers about actual
  behavior.
- **Recommended direction:** Remove or wire them deliberately. In
  particular, either use `getByUser` to feed classification candidates (as
  documented) or delete it; either throw `ItemAccessDeniedException` and map
  it or remove it.
- **Blocks S-07:** No.

### L-5 — Capture classification is invoked with an empty candidate list

- **Severity:** LOW
- **Location:** `capsa-capture/.../CaptureServiceImpl.java:49`
- **Evidence:** `classifier.classify(new ClassificationRequest(userId,
  normalized, List.of()))`. The `candidates`/`ClassificationTarget` path
  documented in `capsa-arch-001` §3/§4 (capture fetches user lists to build
  candidates) is not exercised. It works only because v0.1 activates the
  Known strategy, which ignores candidates.
- **Why it matters:** The `ClassificationTarget` type and the
  `capture → lists` candidate-fetching seam are effectively dormant. When
  the Embedding/SystemOne strategies activate, this will need to be built,
  and the current code gives no evidence the seam is exercised.
- **Recommended direction:** Document as a v0.1 simplification (Known-only
  ignores candidates) or feed `getByUser` candidates now to exercise the
  seam.
- **Blocks S-07:** No.

### L-6 — Inconsistent persistence-entity style in `capsa-capture`

- **Severity:** LOW
- **Location:** `capsa-capture/.../entity/{Capture,ClassificationAttempt,ClassificationResolution}Entity.java`
- **Evidence:** Capture entities use `public` fields, `@GeneratedValue`
  (no explicit id), and no getters/setters; every other module's entities
  use private fields + getters/setters + explicit `setId` with
  `updatable = false`.
- **Why it matters:** Divergent persistence conventions within the same
  codebase; `@GeneratedValue` with a `DEFAULT gen_random_uuid()` column is
  ambiguous (DB-generated vs Hibernate-generated id).
- **Recommended direction:** Normalize to the private-field +
  explicit-id-set style used elsewhere.
- **Blocks S-07:** No.

### L-7 — Hand-rolled JSON string for candidate serialization in capture

- **Severity:** LOW
- **Location:** `capsa-capture/.../CaptureResolutionService.java:195-200`
- **Evidence:** `serializeCandidates` builds a JSON array by string
  concatenation for the `classification_attempts.candidates` TEXT column.
  Fields today are a UUID and a double (safe), but the pattern is
  fragile if `explanation` is ever included.
- **Why it matters:** Manual JSON generation risks escaping/correctness
  issues as the schema grows; the project already has JSON-B available.
- **Recommended direction:** Use JSON-B for this field, or keep it but
  note the constraint.
- **Blocks S-07:** No.

### L-8 — `CaptureNormalizer` omits documented Unicode/punctuation normalization

- **Severity:** LOW
- **Location:** `capsa-capture/.../CaptureNormalizer.java:7-10`
- **Evidence:** `normalize` = `strip().toLowerCase().replaceAll("\\s+"," ")`.
  `capsa-domain-model-v0.1` §2 lists "Unicode normalization, punctuation
  handling" as required behavior.
- **Why it matters:** The Known strategy depends on exact match of
  `normalizedContent`. The current normalizer is deterministic and
  self-consistent (so exact matching works within v0.1), but it does not do
  what the domain model specifies, and future evidence recorded under a
  changed normalizer would break matching.
- **Recommended direction:** Document the actual normalization algorithm
  and its stability contract; add Unicode normalization if non-ASCII input
  is realistic.
- **Blocks S-07:** No.

### L-9 — `OidcCurrentUser` deviates from DI hygiene and documented lifecycle

- **Severity:** LOW
- **Location:** `capsa-runtime/.../OidcCurrentUser.java:14-19`
- **Evidence:** Field injection (`@Inject Principal principal; @Inject
  UserService userService;`), non-final fields, and lazy resolution in
  `userId()`. CAPSA-DI-HYGIENE-001 mandates constructor injection for
  internal components, and `capsa-arch-001` §9 shows `@PostConstruct`
  resolution.
- **Why it matters:** Lazy resolution is arguably better (no provisioning
  unless `userId()` is called), but the field-injection style is an
  unacknowledged exception to the stated convention.
- **Recommended direction:** Constructor-inject where feasible, or document
  the exception.
- **Blocks S-07:** No.

### O-1 — Cross-user capture resolve is masked as 404

- **Severity:** OBSERVATION
- **Location:** `capsa-capture/.../CaptureResolutionService.java:134-138`
- **Evidence:** A resolve attempt on another user's capture returns
  `CaptureNotFoundException` (404), not a 403. This is a sound
  anti-enumeration choice, but inconsistent with `ItemServiceImpl`, which
  surfaces cross-user access as 403 via `ListAccessDeniedException`.
- **Recommended direction:** Leave as-is; note the deliberate asymmetry.

### O-2 — Error messages embed internal UUIDs

- **Severity:** OBSERVATION
- **Location:** e.g. `ListNotFoundException`/`ItemNotFoundException`
  messages ("Item not found: " + id)
- **Evidence:** Mappers copy `e.getMessage()` (which includes the raw UUID)
  into the 404 body. UUIDs are internal identifiers, not secrets, so this
  is not a security issue, but it is not strictly minimal exposure.
- **Recommended direction:** Consider codes-only bodies; not required now.

### O-3 — `Capture` domain record is under-specified, forcing redundant reads

- **Severity:** OBSERVATION
- **Location:** `capsa-capture/.../CaptureResolutionService.java:99-118,173-177`
- **Evidence:** Because `Capture` lacks `userId`, `storeNeedsResolution`
  re-reads the entity twice (`findById` at line 100 and again via
  `userIdOf` at line 174) to recover the owner.
- **Recommended direction:** Carry `userId` on the `Capture` record (or
  pass it from `submit`), removing the redundant query.

---

## 4. Transaction and Consistency Assessment

The reconciled TX-1 → classification → TX-2 model **is true in the
implementation**:

- `CaptureServiceImpl.submit` (orchestrator) is **not** `@Transactional`.
- `CaptureCreationService.createCapture` is `@Transactional` (TX-1): persists
  Capture + initial ClassificationAttempt, then commits.
- `Classifier.classify` runs **outside** a transaction (correct: no DB
  connection held across provider work).
- `CaptureResolutionService.resolveCapture` (TX-2, CLASSIFIED path),
  `storeNeedsResolution` (NEEDS_RESOLUTION path), and `resolveFromUser`
  (UC-04) are each `@Transactional`; `markFailed` is its own transaction.
- `ItemService.createFromCapture` joins the enclosing TX-2 (REQUIRED),
  giving a single consistency boundary for "item + capture status +
  resolution".

Expensive/external work is not performed while holding a transaction
(v0.1 classification is a local query, but the boundary is correct).

**Idempotency / race assessment:**

- **Item.captureId uniqueness** is enforced by `uq_items_capture_id`
  (`V003`). **ClassificationResolution uniqueness** by `uq` on
  `classification_resolutions.capture_id` (`V006`). Both halves of the
  "exactly one item per resolved capture" guarantee are present.
- **`ItemService.complete`** is genuinely idempotent (already-DONE returns
  without emitting or mutating). Minor race: two concurrent completes both
  pass the check and both set `completedAt`; harmless.
- **Capture resolution** and **user provisioning** rely on the DB
  uniqueness constraints for concurrency safety but degrade to 500 on the
  losing side rather than a graceful 409/idempotent-return (M-2, M-3).

No CRITICAL transaction defect was found. The boundary placement is the
strongest part of the codebase.

---

## 5. Observability Assessment

The observability subsystem is well-placed and directionally correct:

- **Port placement:** correct — a leaf module; capability services depend
  only on `Observability.emit(ObservabilityEvent)`.
- **Event contract:** correct — sealed `ObservabilityEvent`, sealed
  `EventActor.User(UUID)` (no email/name/OIDC subject), three mandatory
  metadata fields. The sensitive-field reflection test
  (`ObservabilityEventTest`) is a genuine guard.
- **Dependency direction:** correct — capabilities `requires
  capsa.observability`; the adapter is `internal` and not exported.
- **Async dispatch:** virtual-thread-per-task; caller returns before sink
  I/O. Best-effort, never-throws contract is honored (enqueue failure and
  sink failure both absorbed and logged via the standard logger).
- **JSON-B serialization:** the implementation uses JSON-B (with
  `@JsonbTypeInfo` discriminators) rather than the documented hand-rolled
  `EventJson`. This is a deliberate-looking reversal, but it is
  **undocumented**, and it contradicts the OBS-001 report's stated rationale
  ("no provider dependency, deterministic schema"). See L-1.
- **Serialization/sink boundary:** serialization happens on the caller
  thread; the sink write happens on the virtual thread. Independent
  conclusion: this placement is **acceptable** — serialization of 8 small
  records is cheap, and doing it on the caller thread makes serialization
  failures synchronous and deterministic. It is not a defect; only the
  documentation describing it is wrong (L-1).
- **Failure semantics:** best-effort is correctly isolated; failures never
  recurse through the observability port.
- **Lifecycle:** the virtual-thread executor is never closed (L-2).
- **Module hygiene:** `internal` package is not `opens`-declared (L-3).

The one substantive cross-cutting concern is M-1: emission occurs before
commit, inside the transaction, so the "rolled-back transaction must not
emit" contract is not actually implemented.

No case for Kafka/OpenTelemetry/Loki/outbox is present in the evidence;
the current seam is sufficient for v0.1.

---

## 6. Quarkus / CDI / JPMS Assessment

- **Constructor injection** is consistently applied across services and
  repositories (`private final` + `@Inject` constructor), except
  `OidcCurrentUser` (L-9) and the documented REST-resource exemption.
- **Bean discovery:** the `quarkus.index-dependency.*` block in
  `application.properties` covers all seven modules including
  `capsa-observability`; the force-indexing pattern is correctly mirrored.
- **Scopes:** services/repositories are `@ApplicationScoped`; resources are
  `@RequestScoped` (slightly different from the `@ApplicationScoped`
  resource shown in `capsa-arch-001` §10, but correct given request-scoped
  `CurrentUser`).
- **Module indexing/opens:** correct except L-3.
- **REST resources:** thin and delegated correctly; but see M-6 for the
  validation-error path.
- **Exception mappers:** correct and centralized in `runtime`; broad
  `FallbackExceptionMapper` handles unexpected errors without leaking
  internals (returns a fixed `CAPSA_INTERNAL_ERROR` body).
- **OIDC/current-user adaptation:** `OidcCurrentUser` confines
  MicroProfile-JWT/`Principal` types to `runtime`; capability code sees only
  `CurrentUser`. Lazy provisioning is a reasonable improvement over the
  documented `@PostConstruct`.
- **JSON-B:** used for observability serialization and REST bodies; no
  Jackson. Consistent with the Jakarta-first principle.
- **Lifecycle-managed resources:** the one lifecycle gap is the observability
  executor (L-2).

Framework concerns are, on the whole, kept out of capability/domain code.

---

## 7. Complexity Assessment

The codebase is appropriately lean for v0.1. The following do **not**
currently pay for themselves and should be pruned or explicitly wired:

- `reconstitute` methods on `User`, `CapsaList`, `ClassificationMemoryEntry`
  (dead code).
- `ListService.getByUser` (dead — capture passes empty candidates).
- `ItemAccessDeniedException` + its mapper (declared/registered but never
  thrown).
- `Source.AUTO` on `ClassificationMemoryEntry` (never produced).
- `Item.complete()` domain transition (never invoked; duplicated in service).

The `Classifier`/`ClassificationService` split (GARDEN-002) is a real seam,
not needless layering: it separates the classification contract from
evidence recording, and both are genuine capabilities. The
`ClassificationStrategy` internal SPI is a legitimate port with a single
current implementation (Known) and is justified by the planned Embedding
and SystemOne strategies — do not flag it for removal.

No speculative extension points of concern beyond those already documented
as deferred (embeddings, pgvector, caching, `CaptureInterpreter`).

---

## 8. Positive Decisions to Preserve

1. **Capability-organized JPMS modules with an acyclic graph.** Enforced by
   `module-info` `exports`/`requires`; no capability reaches another's
   `internal` package.
2. **`runtime` remains a genuine composition root.** It wires capabilities,
   provides `OidcCurrentUser`, registers exception mappers, and runs Flyway —
   with no business logic.
3. **Two-transaction capture flow.** `CaptureServiceImpl.submit` is a
   non-transactional orchestrator; classification runs between TX-1 and
   TX-2. This is the correct, non-negotiable boundary.
4. **Dual enforcement of "one item per resolved capture"**
   (`uq_items_capture_id` + `uq` on `classification_resolutions.capture_id`).
5. **`CurrentUser` port owned by `users.api`, implemented in `runtime`.** The
   Quarkus/OIDC types stay out of capability modules.
6. **`Classifier`/`ClassificationService` split** and the internal
   `ClassificationStrategy` SPI — the strategy graph can change without
   affecting `capture`.
7. **Sealed, typed observability event model with a sensitive-field
   reflection test** — a real, tested guard against PII leakage.
8. **Best-effort observability with non-recursive failure reporting** —
   observability failures go to the standard logger, never the port.
9. **Authorization consistently funneled through
   `ListService.verifyContributionAccess`** before every item mutation.
10. **Idempotent `ItemService.complete`** (already-DONE is a no-op, no
    duplicate event).
11. **Flyway owns the schema** (`database.generation=none`); migrations are
    namespaced per capability and live only in `runtime`.

---

## 9. S-07 Readiness Assessment

The system is architecturally sound and testable; the build is green (see
§Validation below). No finding is CRITICAL or HIGH. The findings that touch
deployment-adjacent behavior (M-2, M-3 — concurrency failure modes) are
narrow and single-user in likelihood; they should be scheduled as
reconciliation items, not as blockers.

The main outstanding risk is **documentation drift** (M-5, M-1, L-1) —
reconciling the knowledge pages before S-07 so operators and future work
have accurate contracts.

S-07 can begin. Schedule M-1/M-5 documentation reconciliation and the M-2/M-3
concurrency handling as parallel or early work.

---

## 10. Recommended Reconciliation Order

1. **Documentation reconciliation (cheap, high leverage):** update
   `capsa-obs-001` (JSON-B not `EventJson`; caller-thread serialization),
   `capsa-arch-001` (Classifier/ClassificationService split; capture
   candidate lists), and `capsa-domain-model-v0.1` (USER_CONFIRMED-only
   learning decision — M-5). This is the highest-value low-cost work.
2. **M-6 error-path unification:** single validation mapper + `ErrorResponse`;
   remove inline JSON strings.
3. **M-2 / M-3 concurrency handling:** translate constraint/optimistic-lock
   failures to graceful 409 / idempotent re-query.
4. **M-1 emission-after-commit** (or explicit re-documentation of
   emit-before-commit).
5. **Prune dead code (L-4):** `getByUser`, `reconstitute` methods,
   `ItemAccessDeniedException`, `Source.AUTO`.
6. **Hygiene (L-2, L-3, L-6, L-7, L-8, L-9):** executor shutdown, module
   `opens`, entity style normalization, JSON-B for candidates, normalizer
   documentation, `OidcCurrentUser` DI.

---

## Validation

Commands run from `capsa/` (the Maven root):

```bash
./mvnw test
```

Result: **BUILD SUCCESS** — 75 tests, 0 failures, 0 errors, 0 skipped.

Per-module surefire totals:

| Module | Tests |
|---|---|
| capsa-observability | 12 |
| capsa-users | 7 |
| capsa-lists | 7 |
| capsa-classification | 5 |
| capsa-capture | 0 (no unit tests) |
| capsa-items | 0 (no unit tests) |
| capsa-runtime | 44 (`@QuarkusTest` + RestAssured) |

Docker was running; Quarkus Dev Services provisioned a PostgreSQL container
for the `@QuarkusTest` integration tests. Flyway migrations ran
(`V001`–`V006`) with no failures. No production code was modified; no test
was altered to make it pass.

---

READY FOR S-07 WITH NON-BLOCKING FINDINGS
