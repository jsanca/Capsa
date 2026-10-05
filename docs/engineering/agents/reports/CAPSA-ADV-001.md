# CAPSA-ADV-001 — Adversarial System Review

**Role:** Adversarial Reviewer
**Task:** CAPSA-ADV-001
**Input authority:** current source, migrations, `application.properties`, tests, and reconciled docs (`capsa-arch-001`, `capsa-domain-model-v0.1`, `capsa-obs-001`, CAPSA-ARCH-REVIEW-002, CAPSA-ARCH-FIX-002)
**Status:** Review complete
**Verdict:** READY WITH NON-BLOCKING REMEDIATIONS

---

## 1. Executive Verdict

Capsa v0.1 survived a focused adversarial pass with **no CRITICAL and no HIGH
finding**. The strongest invariants — exactly-one-Item-per-Capture, user-scoped
classification memory, ownership checks before item mutation, and the
transaction-aware observability contract (CAPSA-ARCH-FIX-002) — all held under
direct attack, in several cases enforced by database constraints rather than by
hope.

What the review did find is a cluster of **MEDIUM** defects that share a common
theme: *correctness that is enforced at the wrong layer or surfaced with the
wrong status code*. Specifically:

1. The authentication→internal-user identity anchor is the mutable
   `Principal.getName()` (`upn`/`preferred_username`), not the stable OIDC
   `sub` claim (ADV-1).
2. Two known check-then-act races (CAPSA-ARCH-REVIEW-002 M-2/M-3) were
   *confirmed with executable evidence* — the loser surfaces as a raw
   `PSQLException` → HTTP 500 (ADV-2, ADV-3).
3. The non-blank-content/name invariants are enforced only at the REST layer;
   the service/domain layer accepts blank Capture content and throws unmapped
   `IllegalArgumentException`/`NullPointerException` on direct calls (ADV-4).

These are bounded-impact, single-user-relevant defects, but they are exactly
the kind that will bite at the first concurrent client or non-REST caller. They
should be reconciled in parallel with or shortly after S-07 begins; none blocks
starting.

---

## 2. Test-Suite Baseline

Command run from `capsa/`:

```bash
./mvnw test
```

Result: **BUILD SUCCESS — 84 tests, 0 failures, 0 errors, 0 skipped.**

| Module | Tests |
|---|---|
| capsa-observability | 26 (8 event + 18 adapter) |
| capsa-users | 7 |
| capsa-lists | 7 |
| capsa-classification | 5 |
| capsa-capture | 0 |
| capsa-items | 0 |
| capsa-runtime | 44 (`@QuarkusTest` + RestAssured) |

Docker was running; Quarkus Dev Services provisioned PostgreSQL; Flyway V001–V006
ran clean. No production code was modified. The same suite was re-run green after
the adversarial probes were removed.

---

## 3. Attack Surface Reviewed

- **Domain invariants:** User ownership, List access, Item lifecycle, Capture
  lifecycle, exactly-one-Item-per-Capture, classification resolution,
  ClassificationMemory ownership/evidence, state transitions.
- **Concurrency/idempotency:** simultaneous resolution, repeated resolution,
  simultaneous provisioning, duplicate item-per-capture, repeated completion,
  check-then-act sequences.
- **Transaction boundaries:** UC-03 TX-1 → classification → TX-2, partial-state
  after failure, cross-capability atomicity, non-DB work inside transactions.
- **Observability:** the CAPSA-ARCH-FIX-002 transaction-aware buffer (buffer →
  commit → async dispatch → JSON-B → sink; rollback → discard).
- **Authentication/authorization:** OIDC→internal-user mapping, missing claims,
  user isolation, cross-user IDs, enumeration, service APIs outside REST.
- **HTTP/API:** every endpoint; malformed/null bodies, invalid UUIDs, unknown
  IDs, foreign-user IDs, invalid status, replays, wrong Content-Type.
- **Persistence:** migrations, JPA mappings, nullability vs. uniqueness, FKs,
  enum/string representation.
- **Classification:** user-scoped memory, normalized-content matching,
  empty/stale/conflicting evidence, latest-USER_CONFIRMED semantics.
- **Normalization/serialization:** whitespace, Unicode, composed/decomposed,
  control chars, escaping.
- **Failure mapping:** every failure followed to the HTTP boundary.

---

## 4. Findings (ordered by severity)

### ADV-1 — Identity anchor is `Principal.getName()` (upn/preferred_username), not the stable OIDC `sub`

- **Severity:** MEDIUM
- **Confidence:** HIGH
- **Affected use case:** all authenticated operations (user provisioning).
- **Attack/trigger:** A valid JWT whose `sub` differs from `upn`/`preferred_username`
  (the normal case with modern IdPs: `sub` = opaque stable id, `upn` = email),
  followed later by a change to that `upn`/email.
- **Expected:** Capsa binds identity to the stable `sub`; `findOrProvision`
  re-finds the same `User` regardless of display-name/email changes.
- **Actual:** `OidcCurrentUser.userId()` sets
  `oidcSubject = principal.getName()`. For a MicroProfile `JsonWebToken`,
  `getName()` returns `upn` if present, else `preferred_username`, else `sub`
  (confirmed: MicroProfile JWT defines `upn` as "the preferred claim for
  identifying the Principal"; SmallRye `require-named-principal` documents the
  `upn`/`preferred_username`/`sub` fallback). The `sub` claim is never read,
  even though the code already holds a `JsonWebToken` in the `instanceof`
  branch and could call `jwt.getSubject()`.
- **Evidence:** `capsa-runtime/.../OidcCurrentUser.java:25`
  (`String oidcSubject = principal.getName();`), with `jwt.getClaim("email")`
  and `jwt.getClaim("name")` read from the same token — the stable identifier is
  conspicuously *not* read from the token. `users.oidc_subject` is then keyed on
  whatever `getName()` returned (`V001__users_initial.sql`).
- **Impact:** If the IdP issues `upn`/`preferred_username`, the internal identity
  anchor is a mutable value. A username/email change silently provisions a
  **new** `User`, orphaning the previous user's lists/items (data-availability
  loss). No cross-user exposure, but a silent identity break at the core of the
  security boundary.
- **Why existing tests missed it:** OIDC is disabled in `%test`
  (`%test.quarkus.oidc.enabled=false`); `@TestSecurity` injects a plain test
  principal whose `getName()` returns the test username, so the JWT branch is
  never exercised.
- **Minimal remediation direction:** Read the stable subject explicitly —
  `jwt.getSubject()` (MicroProfile) or `jwt.getClaim("sub")` — and use it as the
  provisioning key; keep `principal.getName()` only as a fallback for the
  non-JWT test path.
- **Known finding relationship:** NEW.

### ADV-2 — Concurrent capture resolution surfaces as a raw `PSQLException` → HTTP 500 (not a graceful 409)

- **Severity:** MEDIUM
- **Confidence:** HIGH
- **Affected use case / endpoint:** UC-04 `POST /capsa/api/captures/{id}/resolution`.
- **Attack/trigger:** Two requests resolve the same `NEEDS_RESOLUTION` capture
  concurrently (client retry with a stale view, or a double-tap that races). Both
  pass the `if (!"NEEDS_RESOLUTION".equals(...))` check and both call
  `ItemService.createFromCapture`.
- **Expected:** The domain contract "exactly one Item per resolved Capture" is
  preserved, and the loser is told so — a clean 409, or idempotent return.
- **Actual:** The loser's `INSERT` into `items` (same `capture_id`) violates
  `uq_items_capture_id`; there is no `@Version`/optimistic lock and no
  application-level idempotency at the item layer. The exception chain is
  `PSQLException (duplicate key value violates unique constraint
  "uq_items_capture_id")` → `ConstraintViolationException` →
  `ArcUndeclaredThrowableException` → `FallbackExceptionMapper` → **HTTP 500
  `CAPSA_INTERNAL_ERROR`**.
- **Evidence:** Deterministic probe — two sequential
  `itemService.createFromCapture(..., sameCaptureId, ...)` calls produce the
  constraint violation on the second. Captured output:

  ```
  PROBE dup-capture: io.quarkus.arc.ArcUndeclaredThrowableException cause=org.postgresql.util.PSQLException (ERROR: duplicate key value violates unique constraint "uq_items_capture_id")
  ```

  `FallbackExceptionMapper` (`capsa-runtime/.../error/FallbackExceptionMapper.java`)
  maps any unmapped `Exception` to 500. The sequential 409 test
  (`CaptureResourceTest.TC-UC04-003`) passes only because the first resolution
  has already committed before the second reads.
- **Impact:** No duplicate data (the DB constraint holds the invariant), but the
  client receives an opaque 500 for a foreseeable retry/race. Confirms and
  sharpens CAPSA-ARCH-REVIEW-002 M-2.
- **Why existing tests missed it:** `TC-UC04-003` is strictly sequential, so the
  status check catches it before the constraint fires; no test exercises the
  concurrent window.
- **Minimal remediation direction:** Add optimistic locking (`@Version`) or a
  `SELECT … FOR UPDATE` on the capture status transition, and map the resulting
  constraint/optimistic-lock failure to `CaptureNotAwaitingResolutionException`
  (409). Alternatively translate `ConstraintViolationException` on the
  capture-id path to 409.
- **Known finding relationship:** REFINES M-2 (concrete chain + confirmed 500).

### ADV-3 — Concurrent first-time provisioning surfaces as a raw `PSQLException` → HTTP 500

- **Severity:** MEDIUM
- **Confidence:** HIGH
- **Affected use case:** user provisioning on any authenticated request.
- **Attack/trigger:** Two requests for a brand-new OIDC subject arrive
  concurrently (e.g. a mobile client firing several requests on first launch).
  Both `findByOidcSubject` find nothing, both `create` + `persist`.
- **Expected:** Provisioning is idempotent on `oidcSubject`; both callers get the
  same `User`.
- **Actual:** The loser violates `users_oidc_subject_key` (UNIQUE). Chain:
  `PSQLException (duplicate key value violates unique constraint
  "users_oidc_subject_key")` → no specific mapper → `FallbackExceptionMapper` →
  **HTTP 500**.
- **Evidence:** Concurrent probe with a `CyclicBarrier`:

  ```
  PROBE prov-race: successes=1 failure=org.postgresql.util.PSQLException: ERROR: duplicate key value violates unique constraint "users_oidc_subject_key"
  ```

  `UserServiceImpl.findOrProvision` (`capsa-users/.../UserServiceImpl.java:29`)
  is a plain find-then-insert; `UserProvisioningTest.findOrProvisionIsIdempotent`
  is sequential only.
- **Impact:** Transient 500 on a realistic first-login burst; the DB constraint
  prevents duplicate users, but the failure is not recovered gracefully.
- **Why existing tests missed it:** No concurrency; idempotency is asserted
  sequentially.
- **Minimal remediation direction:** Catch the unique-constraint violation and
  re-query (or use `INSERT … ON CONFLICT DO NOTHING` + select) so both callers
  converge on the same `User`.
- **Known finding relationship:** REFINES M-3 (concrete chain + confirmed 500).

### ADV-4 — Non-blank content/name invariants are enforced only at REST; direct service calls persist empty Captures or throw unmapped exceptions

- **Severity:** MEDIUM
- **Confidence:** HIGH
- **Affected use case / endpoint:** UC-03 `submit`, UC-02 `create` — and any
  future non-REST caller (MCP, scheduled job, in-process reuse).
- **Attack/trigger:** Call `CaptureService.submit(userId, "   ")`,
  `submit(userId, null)`, or `ItemService.create(userId, blankNameCommand)`
  directly (bypassing the REST validation).
- **Expected:** Domain invariant "Capture.content is non-blank" and
  "Item.name is non-blank" are enforced by the service/domain.
- **Actual:**
  - `submit(userId, "   ")` normalizes to `""` and **persists an empty Capture**
    (`originalContent = "   "`, `normalizedContent = ""`), returning
    `NeedsResolution`. No validation anywhere in
    `CaptureServiceImpl`/`Capture`/`CaptureNormalizer` (`normalize(null)` returns
    `""`).
  - `submit(userId, null)` throws `NullPointerException` at
    `CaptureServiceImpl.java:43` (`content.length()` in a debug log).
  - `ItemService.create` with a blank name throws `IllegalArgumentException`
    from `Item.create` — which has **no exception mapper**, so it becomes 500.
- **Evidence:** Probe output:

  ```
  PROBE blank-content: accepted; result=NeedsResolution
  PROBE null-content: java.lang.NullPointerException (no mapper => 500)
  PROBE blank-name: java.lang.IllegalArgumentException (no mapper => 500)
  ```

  The REST resources (`ListResource.java:39-42`, `ItemResource.java:44-53`,
  `CaptureResource.java:40-43`) hand-validate and return 422, which is why the
  happy-path HTTP tests pass.
- **Impact:** The invariant is enforced at the wrong layer. A non-REST caller
  (or a future endpoint) can persist empty Captures, and blank input surfaces as
  500 rather than a validation error. Confirms and sharpens M-4 (creation-only
  domain) and M-6 (split validation architecture).
- **Why existing tests missed it:** All tests go through the REST layer, which
  does its own validation; no test calls the services directly with blank input.
- **Minimal remediation direction:** Move non-blank validation into the domain
  factories (`Capture`, `Item`) and/or service methods, and add an
  `IllegalArgumentException`/`ConstraintViolationException` mapper so
  domain-validation failures become 4xx, not 500.
- **Known finding relationship:** REFINES M-4 and M-6.

### ADV-5 — Normalization instability breaks Known-classification identity (NBSP, composed/decomposed Unicode)

- **Severity:** LOW
- **Confidence:** HIGH
- **Affected use case:** UC-03 Known classification.
- **Attack/trigger:** Capture content that is "the same" to a user but
  normalizes differently, e.g. `"a\u00A0\u00A0b"` (non-breaking spaces) vs
  `"a  b"`, or `"caf\u00e9"` (composed) vs `"cafe\u0301"` (decomposed).
- **Expected:** Known classification matches on the semantic string; two
  visually/semantically identical inputs classify identically.
- **Actual:** `CaptureNormalizer.normalize` = `strip().toLowerCase()
  .replaceAll("\\s+"," ")`. `\s` is ASCII-only, so non-breaking space is **not**
  collapsed, and there is no Unicode normalization. Executable check:

  ```
  NBSP   -> [a\u00A0\u00A0b]  equals 'a b'? false
  composed   -> [café]
  decomposed -> [cafe\u0301]
  equal? false
  ```

  So a user who first resolves `"caf\u00e9"` and later captures
  `"cafe\u0301"` gets `NEEDS_RESOLUTION` again instead of `CLASSIFIED`.
- **Impact:** Degraded learning loop for accented/Unicode text; not a crash or
  corruption.
- **Why existing tests missed it:** No test exercises non-ASCII whitespace or
  Unicode equivalence.
- **Minimal remediation direction:** Add Unicode normalization (NFKC/NFC) and
  collapse Unicode whitespace, or document the exact ASCII-only normalization
  contract and its stability guarantee.
- **Known finding relationship:** REFINES L-8.

### ADV-6 — `CaptureResult.NeedsResolution.candidates` is always empty in v0.1

- **Severity:** LOW
- **Confidence:** HIGH
- **Affected use case / endpoint:** UC-03 ambiguous path
  (`POST /capsa/api/captures` → 200).
- **Attack/trigger:** Submit any content with no prior user-confirmed memory.
- **Expected:** The client receives ranked candidate lists to render a chooser.
- **Actual:** `KnownClassificationStrategy` returns `NEEDS_RESOLUTION` with
  `List.of()` (empty candidates); `CaptureServiceImpl.submit` passes
  `List.of()` as the request candidates too. The 200 response body is always
  `{ "captureId": …, "candidates": [] }`.
- **Evidence:** `KnownClassificationStrategy.java:38-46`,
  `CaptureServiceImpl.java:49`.
- **Impact:** The user-chooser has no suggestions; the client must fetch lists
  separately. Bounded UX/contract gap, not a correctness bug.
- **Why existing tests missed it:** `TC-UC03-004` asserts only that a
  `captureId` is present, never that candidates are non-empty.
- **Minimal remediation direction:** Either feed `ListService.getByUser` into
  classification so candidates are populated, or document that v0.1 Known-only
  returns empty candidates.
- **Known finding relationship:** REFINES L-5.

### ADV-7 — Missing foreign keys on capture/item/memory reference columns

- **Severity:** LOW
- **Confidence:** HIGH
- **Affected use case:** persistence integrity across `items`, `captures`,
  `classification_memory_entries`.
- **Attack/trigger:** A `selected_list_id` in classification memory (or an
  `items.capture_id`) that references a now-deleted or foreign row.
- **Expected:** Referential integrity prevents dangling references; the
  auto-classify path re-validates the list before use.
- **Actual:** `items.capture_id` (`V003`), `classification_memory_entries.
  selected_list_id` (`V004`), and `classification_resolutions.selected_list_id`
  (`V006`) have **no** `REFERENCES`. Auto-classification
  (`resolveCapture`) uses `result.selectedListId()` straight from memory with
  no re-check; if it does not exist/owned, `createFromCapture`'s
  `verifyContributionAccess` throws, rolling back TX-2 — but TX-1 already
  committed the Capture in `PROCESSING`, leaving it orphaned (no recovery
  sweep).
- **Impact:** Latent in v0.1 (lists are create-only, no deletes), but the
  missing FKs plus the un-revalidated classify path would turn a stale list id
  into an orphaned `PROCESSING` capture.
- **Why existing tests missed it:** No list-delete path exists; no test seeds
  memory with a nonexistent list id via a production path.
- **Minimal remediation direction:** Add the FKs (and validate list existence
  before creating an Item from a classified result), or document the deferral.
- **Known finding relationship:** NEW (persistence-level).

### ADV-8 — Cross-user item completion leaks existence (403 vs 404), inconsistent with capture's 404 masking

- **Severity:** LOW
- **Confidence:** HIGH
- **Affected use case / endpoint:** UC-06 `PUT /capsa/api/items/{id}/completion`.
- **Attack/trigger:** `complete` a random UUID vs. a UUID known to belong to
  another user.
- **Expected:** Uniform anti-enumeration (as `resolveFromUser` does by returning
  `CaptureNotFoundException` for foreign captures).
- **Actual:** `ItemServiceImpl.complete` does `findById` (→ 404 if absent) then
  `verifyContributionAccess` (→ 403 `ListAccessDeniedException` if owned by
  another user). A 403 therefore reveals "the item exists but isn't yours".
- **Evidence:** `ItemServiceImpl.java:85-91`; contrast with
  `CaptureResolutionService.java:134-138`.
- **Impact:** Minor enumeration of item existence (UUID-guarded, low value).
- **Why existing tests missed it:** `TC-UC06-005` asserts only `not(equalTo(200))`.
- **Minimal remediation direction:** Return 404 for non-owned items (consistent
  anti-enumeration) or document the intentional 403.
- **Known finding relationship:** NEW (refines the security-boundary dimension).

### ADV-9 — `ItemView.status` is a raw String; invalid `status` query silently defaults to ACTIVE

- **Severity:** OBSERVATION
- **Confidence:** HIGH
- **Affected use case / endpoint:** UC-05 `GET /capsa/api/items`.
- **Attack/trigger:** `?status=bogus` (or `?status=`).
- **Expected:** A validation error or explicit default.
- **Actual:** `ItemResource.getByList` switches with `default -> ACTIVE`, so
  unknown status silently returns active items. `ItemView.status` is a raw
  String; the `items.status` column has no CHECK constraint.
- **Impact:** Minor contract looseness; no data hazard.
- **Known finding relationship:** NEW (HTTP/API dimension).

### ADV-10 — Observability event ordering: `ItemCreated` is buffered before `CaptureClassified`

- **Severity:** OBSERVATION
- **Confidence:** HIGH
- **Affected use case:** UC-03 auto-classification event stream.
- **Attack/trigger:** Auto-classify a known capture; observe `capsa.observability`.
- **Expected:** `CaptureClassified` precedes `ItemCreated` (classification
  causes the item).
- **Actual:** `resolveCapture` calls `itemService.createFromCapture` (which
  emits `ItemCreated`) *before* emitting `CaptureClassified`; `occurredAt` of
  `ItemCreated` is therefore earlier than `CaptureClassified`.
- **Impact:** Cosmetic ordering in the best-effort event stream.
- **Known finding relationship:** NEW (observability dimension).

---

## 5. Existing-Review Findings — Confirmed / Refined / Rejected

| CAPSA-ARCH-REVIEW-002 finding | Adversarial disposition |
|---|---|
| M-2 capture resolution race | **REFINED (ADV-2)** — confirmed 500 via `uq_items_capture_id` → `PSQLException` → fallback mapper. |
| M-3 provisioning race | **REFINED (ADV-3)** — confirmed 500 via `users_oidc_subject_key` → `PSQLException`. |
| M-4 creation-only domain / lifecycle bypass | **REFINED (ADV-4)** — blank Capture accepted; blank Item name → unmapped 500. |
| M-5 `Source.AUTO` dead / no AUTO learning | **CONFIRMED** — no AUTO evidence path; not re-exploited beyond documentation inconsistency. |
| M-6 split validation/error architecture | **REFINED (ADV-4)** — concrete 500 chains for `IllegalArgumentException`/`NPE`; inline JSON 422s confirmed. |
| L-4 dead code | **CONFIRMED** — `getByUser`, `reconstitute`, `ItemAccessDeniedException` unreferenced (no behavioral exploit). |
| L-5 empty classification candidates | **REFINED (ADV-6)** — `NeedsResolution.candidates` always `[]` in v0.1. |
| L-6 capture entity style | **CONFIRMED** — no behavioral impact. |
| L-7 manual candidate JSON | **CONFIRMED** — drops `explanation`; no exploit (UUID/double only). |
| L-8 normalization/doc mismatch | **REFINED (ADV-5)** — executable demonstration of NBSP/Unicode instability. |
| L-9 `OidcCurrentUser` injection/lifecycle | **REFINED (ADV-1)** — the more serious defect is the identity-anchor (`getName()`) issue, superseding the injection-style note. |
| M-1 / L-1 / L-2 observability (fixed in CAPSA-ARCH-FIX-002) | **HELD** — the transaction-aware buffer is sound; see §6. No distinct new observability bug found. |

The old "emit-before-commit" M-1 is **not** re-reported: CAPSA-ARCH-FIX-002
correctly buffers on `STATUS_ACTIVE` and dispatches on `afterCompletion
(STATUS_COMMITTED)`. The `STATUS_MARKED_ROLLBACK` edge is tracked separately
(CAPSA-ARCH-FIX-002A) and is out of scope here.

---

## 6. Adversarial Tests / Reproductions Performed

Temporary probes were added under `capsa-runtime/src/test/...`, executed, and
removed (they assert buggy behavior and are not desired-behavior regression
tests). Recorded results:

1. **blank-content accepted** — `captureService.submit(userId, "   ")` returned
   `NeedsResolution` (empty Capture persisted).
2. **null-content** — `submit(userId, null)` threw `NullPointerException`.
3. **blank-name** — `itemService.create(..., blankName)` threw
   `IllegalArgumentException` (unmapped → 500).
4. **duplicate captureId** — second `createFromCapture` with the same captureId
   threw `ArcUndeclaredThrowableException` → `PSQLException`
   (`uq_items_capture_id`).
5. **concurrent provisioning** — 2 threads, one success, one `PSQLException`
   (`users_oidc_subject_key`).
6. **normalization** — standalone check confirmed NBSP not collapsed and
   composed/decomposed Unicode diverge (ADV-5).

---

## 7. Positive Findings — Invariants That Resisted Attack

1. **Exactly-one-Item-per-Capture** is genuinely two-level: application status
   check + `uq_items_capture_id` + `uq` on `classification_resolutions.capture_id`.
   The DB constraint reliably stops duplicates even under the race (the defect is
   the *status code*, not the invariant).
2. **User-scoped classification memory** is correctly isolated: the Known query
   filters `userId + normalizedContent + USER_CONFIRMED`; the
   `differentUser_sameContent` tests prove no cross-user evidence leakage.
3. **Ownership checks** (`verifyContributionAccess`) gate every item
   create/complete/query path; cross-user add/complete/read all fail.
4. **Cross-user capture resolution** is deliberately masked as 404
   (anti-enumeration), and it held.
5. **Item completion is genuinely idempotent** (already-DONE is a no-op, no
   duplicate `ItemCompleted` in the sequential case).
6. **Transaction-aware observability** (CAPSA-ARCH-FIX-002) correctly buffers on
   ACTIVE, discards on rollback, and serializes on the async side; the rollback
   unit test and the end-to-end `ListCreated` integration test both hold.
7. **Schema** is Flyway-owned with `database.generation=none`; no drift.

---

## 8. Residual Risks / Accepted v0.1 Limitations

- Process crash between TX-1 (Capture persisted, `PROCESSING`) and TX-2 leaves a
  `PROCESSING` capture with no recovery sweep — accepted (no async capture
  channel; documented FAILED-retry open question).
- Process crash after DB commit but before observability sink delivery loses the
  event — accepted (best-effort; no outbox).
- `STATUS_MARKED_ROLLBACK` observability edge — tracked separately
  (CAPSA-ARCH-FIX-002A).
- Empty classification candidates in v0.1 (Known-only) — accepted simplification.

---

## 9. Recommended Reconciliation Order

1. **ADV-1 (identity anchor)** — highest leverage; correct before enabling
   production OIDC. Use `sub`/`jwt.getSubject()`.
2. **ADV-2 / ADV-3 (concurrency 500s)** — translate unique-violation /
   optimistic-lock failures to graceful 409 / idempotent return.
3. **ADV-4 (invariants at service/domain + a validation mapper)** — move
   non-blank checks out of REST and map `IllegalArgumentException` to 4xx.
4. **ADV-7 (FKs + list re-validation on classify)** — close the dangling
   reference hole.
5. **ADV-5 (normalization), ADV-6 (candidates), ADV-8 (enumeration), ADV-9,
   ADV-10** — low-priority hardening/documentation.

---

## 10. Final Verdict

READY WITH NON-BLOCKING REMEDIATIONS

No CRITICAL or HIGH finding. The MEDIUM findings (ADV-1 through ADV-4) are
bounded in impact for a single-user v0.1 but should be remediated in parallel
with or immediately after the start of S-07, with ADV-1 required before
production OIDC is enabled.
