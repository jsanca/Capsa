# CAPSA-ARCH-RECONCILE-001 — Architecture Review Reconciliation Report

## Status

COMPLETED

## Scope

Reconcile `docs/knowledge/architecture/capsa-arch-001-modular-monolith-v0.1.md` with all findings from `CAPSA-ARCH-REVIEW-001`. The reconciled architecture is ready as input to Engineering Plan design.

## Sources

| Source | Role |
|---|---|
| `docs/engineering/agents/intent/intent1.md` | Product authority |
| `docs/knowledge/use-cases/uc-01` … `uc-06` | Behavioral authority |
| `docs/knowledge/domain/capsa-domain-model-v0.1.md` | Domain invariant authority |
| `docs/engineering/agents/reviews/CAPSA-ARCH-REVIEW-001-architecture-review.md` | Review findings (primary input) |
| `docs/engineering/agents/reports/CAPSA-DOMAIN-RECONCILE-001-reconciliation-report.md` | Prior reconciliation |
| `docs/knowledge/architecture/capsa-arch-001-modular-monolith-v0.1.md` | Reconciled artifact |

---

## Finding Dispositions

### A-01 — HIGH — REST resources cannot compile against a runtime-owned auth context

**Severity:** HIGH
**Disposition:** ACCEPTED

**Problem from review:** `CurrentUserContext` was owned by `runtime`, which exports nothing. REST resources in capability modules cannot compile against a type from a module that exports nothing. The claim that "CDI bypasses the compile-time visibility requirement" is false — CDI resolves the instance at runtime, but the field/parameter type must be resolvable at compile time.

**Changes applied:**

1. `capsa.users.api` now owns two new public types:
   - `UserId` — `public record UserId(UUID value) {}` — the canonical typed User identity
   - `CurrentUser` — interface: `UserId userId()` — the request-scoped auth context abstraction

2. `OidcCurrentUser` in `capsa.runtime` implements `CurrentUser`. All Quarkus OIDC types (`JsonWebToken`, `io.quarkus.oidc`) remain confined to `runtime`.

3. Capability REST resources (`ListResource`, `ItemResource`, `CaptureResource`) inject `CurrentUser` (from `capsa.users.api`) — JPMS-compilable. The capability modules declare `requires capsa.users` in their `module-info.java`.

4. The §9 claim that "CDI resolves injection without a compile-time requires edge" has been removed and corrected.

5. `capsa.lists`, `capsa.items`, `capsa.capture`, `capsa.classification` are identified as genuine semantic dependents of `capsa.users` (they operate on User-owned resources). Module graph updated accordingly.

**Dependency on `users` is not a convenience workaround; it reflects the real semantic fact that these capabilities operate on resources owned by a Capsa User.**

---

### A-02 — HIGH — Strategy-list producer in runtime references internal classification types

**Severity:** HIGH
**Disposition:** ACCEPTED — with refinement beyond original recommendation

**Problem from review:** `ClassificationStrategyProducer` in `runtime` injected concrete strategy types (`KnownClassificationStrategy`, `EmbeddingClassificationStrategy`, `SystemOneClassificationStrategy`). These types are unexported internals of `capsa.classification`. `runtime` cannot reference them under JPMS.

**Changes applied:**

1. The public classification API is restructured around a `Classifier` interface:
   ```java
   // capsa.classification.api
   public interface Classifier {
       ClassificationResult classify(ClassificationRequest request);
       void recordUserResolution(UserId userId, String normalizedContent, UUID selectedListId);
   }
   ```
   `ClassificationService` is removed from the public surface. `Classifier` is the complete public contract.

2. `ClassificationRequest` is introduced as a public input type (replaces the `normalizedContent, userId, candidates` parameter list).

3. `DefaultClassifier` (internal) implements `Classifier` and owns the pipeline composition internally. `runtime` does not name, instantiate, or order internal strategy classes.

4. `ClassificationStrategy` remains an internal SPI — not exported. Future externally-supplied strategies would require a deliberate promotion to a public SPI, which is not currently justified.

5. `ClassificationStrategyProducer` in `runtime` is removed.

6. Strategy ordering/composition configuration belongs to `capsa.classification.internal`. `runtime` provides external configuration values (model paths, thresholds) via `application.properties`.

**Architecture principle recorded:** Public capability APIs express business capabilities, not internal architectural patterns.

---

### A-03 — MEDIUM — Single transaction spans external provider calls

**Severity:** MEDIUM
**Disposition:** ACCEPTED

**Problem from review:** `CaptureService.submit()` was `@Transactional` and included "Capture save + classification + Item creation — all in one transaction." Classification may invoke external AI providers (Jev, DeepSeek) with potential latency/failure. Holding a DB connection open across such calls is an operational anti-pattern.

**Changes applied:**

1. The `CaptureService` monolith is refactored into three collaborating services within `capsa.capture.internal`:
   - `CaptureOrchestrator` — not `@Transactional`; coordinates the flow
   - `CaptureCreationService` — `@Transactional`; persists Capture + initial ClassificationAttempt; COMMIT
   - `CaptureResolutionService` — `@Transactional`; persists resolution + creates Item + records evidence; COMMIT

2. Classification (`Classifier.classify()`) executes between TX-1 and TX-2 with no encompassing DB transaction.

3. If classification raises an execution exception, `CaptureOrchestrator` catches it and calls a separate `@Transactional` method to mark the Capture as `FAILED`.

4. UC-04's `CaptureResolutionService.resolveFromUser()` is a single transaction that verifies state, creates the Item, records the resolution, and records evidence.

5. §12 of the architecture rewritten to document this two-transaction pattern explicitly, with annotated pseudocode.

6. The §19 "Recommended" entry for "single transaction in CaptureService.submit()" is replaced by the two-transaction decision.

---

### A-04 — MEDIUM — Reconciled one-Item enforcement mechanism not carried forward

**Severity:** MEDIUM
**Disposition:** ACCEPTED

**Problem from review:** CAPSA-DOMAIN-RECONCILE-001 (F-01) required both idempotent application logic AND a `UNIQUE Item.captureId` persistence constraint. The architecture discussed atomicity/rollback only; neither idempotency nor the uniqueness constraint appeared.

**Changes applied:**

1. `ItemService.createFromCapture(UserId, ListId, CaptureId, name, notes)` is a distinct operation from `ItemService.create(...)` — it accepts a `CaptureId` and enforces uniqueness.

2. The `items` table has a UNIQUE constraint on `capture_id` (where non-null), enforced by Flyway migration.

3. `CaptureResolutionService` checks Capture state before creating the Item (application-level idempotency).

4. §7 documents the constraint under "Item.captureId Uniqueness Constraint."

5. §12 documents both enforcement levels in "Idempotency and Duplicate Item Prevention."

**Both mechanisms together restore the full domain reconciliation guarantee from F-01.**

---

### A-05 — MEDIUM — UC-05 Option A would create a module cycle

**Severity:** MEDIUM
**Disposition:** ACCEPTED

**Problem from review:** Option A proposed `lists → items` for view assembly. This creates a cycle with the existing `items → lists` edge. JPMS rejects cyclic `requires`. The document presented Option A without flagging this contradiction.

**Changes applied:**

1. **Option A is explicitly rejected** in §10 with the reason documented: it introduces `lists → items`, forming a cycle with `items → lists`.

2. **Option C is selected** as the v0.1 disposition: two separate endpoints; the client assembles the composite view.
   - `GET /capsa/api/lists/{id}` — List metadata (lists capability)
   - `GET /capsa/api/items?listId={id}` — Items for the list (items capability)

3. Option B (runtime assembles) is noted as a future option if a composite endpoint becomes required without touching existing capabilities.

4. §19 records UC-05 Option C as a decided architecture decision.

---

### A-06 — MEDIUM — Semantic-profile cache and table derive from lists state classification cannot observe

**Severity:** MEDIUM
**Disposition:** ACCEPTED — simplified per current evidence

**Problem from review:** §13 claimed the semantic-profile cache should "invalidate when a List's purpose changes." Classification has no dependency on lists and there is no event mechanism. The invalidation path was architecturally unsupported.

**Changes applied:**

1. The invalidation claim is removed from §13.

2. The reconciled position: **Lists are create-only in UC-01–UC-06.** There is no List-update operation in the current requirements. Therefore, semantic profiles (when activated) are immutable in v0.1. No synchronization mechanism is needed now.

3. §18 documents the evolutionary concern: when List mutation is introduced, a synchronization/invalidation mechanism (callback or outbox event) must be designed before the Embedding strategy is activated. The architecture explicitly defers this, not omits it.

4. Semantic profiles and the `list_semantic_profiles` table are marked as "not required for v0.1" in §7 and §16.

---

### A-07 — MEDIUM — Three live strategies + ONNX + pgvector + external providers exceed v0.1 evidence

**Severity:** MEDIUM
**Disposition:** ACCEPTED

**Problem from review:** The architecture activated the full three-tier pipeline (Known + Embedding + SystemOne), ONNX, pgvector, and external AI providers immediately. This violates the "complexity follows evidence" principle for a single-user v0.1.

**Changes applied:**

1. **v0.1 activates Known strategy only.** `DefaultClassifier` in v0.1 composes `ClassificationPipeline(List.of(known))`.

2. The architectural seam is preserved: `EmbeddingClassificationStrategy` and `SystemOneClassificationStrategy` exist in `internal` but are inactive.

3. ONNX, pgvector, Jev, DeepSeek, Argonaut, LangChain4j are all marked **not required for v0.1** in §8 and §16.

4. §8 documents the activation path: adding Embedding requires only changes inside `capsa.classification.internal`; no consumer module changes.

5. The Engineering Plan will determine the appropriate slice for strategy activation.

---

### A-08 — LOW — Identifier representation is internally inconsistent

**Severity:** LOW
**Disposition:** ACCEPTED — with explicit alignment

**Problem from review:** §5 exported typed IDs (`ListId`, `ItemId`, `CaptureId`, `UserId` "optional"). §19 recommended "Use UUID at cross-module public API boundaries." The two sections contradicted each other.

**Changes applied:**

1. **`UserId` is a required typed public ID** — it is the one ID that genuinely crosses module boundaries as part of the `CurrentUser` interface and `ClassificationRequest`. It cannot be reduced to a raw UUID.

2. Other capability IDs (`ListId`, `ItemId`, `CaptureId`) remain in their respective `api` packages as typed identifiers available to consumers that already depend on the owning module. They are not passed as raw UUIDs in cross-module calls when the consuming module already has the dependency.

3. The §19 "Recommended" UUID-at-boundaries entry is removed and replaced by the explicit `UserId` decision.

4. `capsa.classification.api` uses `UUID` for `selectedListId` in `recordUserResolution()` and `listId` in `ClassificationTarget`/`ClassificationCandidate` — this is correct, as `classification` has no dependency on `lists` and these IDs are opaque references.

5. §5 aligns with actual usage.

---

### A-09 — LOW — Schema DDL lives in runtime, splitting table ownership

**Severity:** LOW
**Disposition:** ACCEPTED — ownership nuance documented

**Problem from review:** Tables are owned by capability modules (entities, repositories), but their DDL lives in `runtime`. The "capability owns its tables" rule needed clarification.

**Changes applied:**

1. §7 now includes an explicit "Flyway and Schema Ownership" section that distinguishes:
   - **Capabilities own their data model** (entities, repositories, domain invariants, schema design)
   - **Runtime owns DDL execution** (Flyway classpath constraint)

2. The pragmatic rationale is documented: Flyway requires a unified classpath and must run in the module that produces the executable.

3. The "A capability is the only reader and writer of its own persistence tables" rule (§1, §7) is preserved without contradiction.

---

### A-10 — LOW — `activity_log` table has no owner in the capability model

**Severity:** LOW
**Disposition:** ACCEPTED

**Problem from review:** §14 proposed "a simple `activity_log` table" but identified no capability as its single writer, violating the single-writer rule.

**Changes applied:**

1. **The `activity_log` table is removed from v0.1.** No dedicated activity/audit persistence is introduced.

2. **v0.1 uses domain-significant timestamps already on domain objects** (`Item.createdAt`, `Item.completedAt`, `Capture` timestamps, classification attempt timestamps). These satisfy pattern-detection and history requirements.

3. §14 "Application Activity" updated: per-entity timestamps are the v0.1 mechanism; a dedicated activity store is deferred until a specific use case requires it.

4. When a durable activity store is introduced, an owning capability or a new `capsa.activity` module must be designated before implementation.

---

### A-11 — OBSERVATION — Shared `CapsaException` type has no defined home

**Severity:** OBSERVATION
**Disposition:** ACCEPTED

**Problem from review:** §9 referenced `ExceptionMapper<CapsaException>` implying a shared base type, but no common module exists and the doc left the question open.

**Changes applied:**

1. **No shared `CapsaException` hierarchy.** Each capability defines its own exception types.

2. Each capability's `api` package defines string error-code constants.

3. `runtime` registers per-capability `ExceptionMapper` implementations. No shared base type is needed.

4. §9 updated to document this: "There is no shared `CapsaException` base type; each capability defines its own exception hierarchy."

---

### A-12 — OBSERVATION — Classification-memory cache invalidation omits AUTO writes

**Severity:** OBSERVATION
**Disposition:** ACCEPTED — resolved by A-07 + v0.1 scope decision

**Problem from review:** §13 only invalidated the memory cache on USER writes, missing AUTO writes that also change what the Known strategy observes.

**Changes applied:**

1. The ClassificationMemory cache is **not introduced in v0.1.** This makes the invalidation question moot for now.

2. §13 documents: Known Classification queries the database directly in v0.1. If usage demonstrates a performance problem, the cache can be added with invalidation on both AUTO and USER evidence writes.

3. Per A-07, Known is the only v0.1 strategy. The load level that would justify a cache is unlikely at single-user scale.

---

### A-13 — OBSERVATION — External-provider API-key/secrets handling is unaddressed

**Severity:** OBSERVATION
**Disposition:** ACCEPTED

**Problem from review:** §15 covered timeout, bounded invocation, and failure isolation but did not address how provider API keys are stored and protected.

**Changes applied:**

1. §15 now includes an explicit "Secrets and API keys" subsection:
   - Provider credentials come from runtime environment/secrets manager, referenced as `${ENV_VAR}` in `application.properties`
   - Never committed to source control
   - Never emitted to logs (technical or application)
   - Never exposed in public capability APIs or domain objects

2. §9 (Runtime/Composition) includes the same principle in the "Provider Configuration" section.

---

## Architecture Graph Revalidation

Updated module graph (acyclic; verified):

```
runtime → {users, lists, items, capture, classification}
capture → {classification, items, lists, users}
items   → {lists, users}
lists   → {users}
classification → {users}
users   → (none)
```

**Verification against required properties:**

| Property | Status |
|---|---|
| Graph remains acyclic | ✓ — no cycle introduced by new `→ users` edges |
| Every edge has a semantic reason | ✓ — all documented in §4 Dependency Justification |
| No capability accesses another's repository | ✓ — JPMS structural enforcement via unexported `internal.persistence` |
| Runtime may depend on capabilities | ✓ |
| Capabilities do not depend on runtime | ✓ — `CurrentUser` is now in `users.api`, not `runtime` |
| Classification consumers depend only on `Classifier` | ✓ — `Classifier` is the sole public interface |
| Internal classification implementation inaccessible through JPMS | ✓ — `DefaultClassifier`, `ClassificationPipeline`, `ClassificationStrategy`, all strategies are in unexported `internal` packages |

---

## Transaction Flow Revalidation

UC-02 (direct add), UC-06 (complete item): single transaction; no external calls; no concern.

UC-03 two-transaction flow:

```
TX-1  [CaptureCreationService @Transactional]
      ├── persist Capture (content, userId, RECEIVED state)
      ├── persist initial ClassificationAttempt (PENDING)
      └── COMMIT

      (no DB transaction)
      Classifier.classify(request)
          — may invoke KnownClassificationStrategy (DB read, no open tx required for external strategies)
          — execution exception → CaptureOrchestrator catches → FAILED path (separate TX)

TX-2  [CaptureResolutionService @Transactional]
      ├── re-load Capture (verify still in expected state — idempotency check)
      ├── ItemService.createFromCapture() — creates Item; DB enforces UNIQUE capture_id
      ├── update ClassificationAttempt with result
      ├── update Capture status (CLASSIFIED or NEEDS_RESOLUTION)
      └── COMMIT
```

Concurrent/retry protection: if TX-2 is attempted twice for the same `captureId`, the application-level state check (Capture already CLASSIFIED) prevents the second Item creation. If the state check is bypassed by a race, the UNIQUE constraint on `capture_id` prevents the duplicate at the DB level.

UC-04:

```
TX  [CaptureResolutionService @Transactional]
    ├── load Capture; verify NEEDS_RESOLUTION (idempotency)
    ├── verifyContributionAccess(userId, selectedListId)
    ├── ItemService.createFromCapture() — UNIQUE constraint enforced
    ├── persist ClassificationResolution (USER)
    ├── Classifier.recordUserResolution() — evidence to ClassificationMemory
    ├── update Capture status (RESOLVED)
    └── COMMIT
```

`Classifier.recordUserResolution()` is a DB write within the UC-04 transaction. This is acceptable because `recordUserResolution()` does not invoke external providers; it is a local persistence operation.

---

## Classification Encapsulation Validation

The following encapsulation is confirmed:

```
capsa.capture
    │
    ▼ (depends on)
capsa.classification.api
    Classifier  ─────────────────────────────────────────────────► accessible ✓
    ClassificationRequest  ──────────────────────────────────────► accessible ✓
    ClassificationResult  ───────────────────────────────────────► accessible ✓
    ClassificationCandidate  ────────────────────────────────────► accessible ✓
    ClassificationTarget  ───────────────────────────────────────► accessible ✓

capsa.classification.internal  (JPMS: not exported)
    DefaultClassifier  ──────────────────────────────────────────► inaccessible ✓
    ClassificationPipeline  ─────────────────────────────────────► inaccessible ✓
    ClassificationStrategy  ─────────────────────────────────────► inaccessible ✓
    KnownClassificationStrategy  ────────────────────────────────► inaccessible ✓
    EmbeddingClassificationStrategy  ────────────────────────────► inaccessible ✓
    SystemOneClassificationStrategy  ────────────────────────────► inaccessible ✓
```

Changing the internal strategy graph (e.g., activating Embedding) does not require `capture` recompilation, unless the `Classifier` interface itself changes.

---

## UC-01 Through UC-06 Support Verification

All use cases remain expressible through the reconciled architecture:

| Use Case | Verification |
|---|---|
| UC-01 Create List | `ListService.create(UserId, name, purpose)` — users dependency via UserId ✓ |
| UC-02 Add Item to List | `ItemService.create(UserId, ListId, name, notes)` — lists.verifyContributionAccess ✓ |
| UC-03 Automatically Classify Capture | Two-transaction flow; `Classifier.classify()`; `ItemService.createFromCapture()` — idempotency enforced ✓ |
| UC-04 Resolve Ambiguous Classification | Single TX; `CaptureResolutionService`; UNIQUE constraint; `Classifier.recordUserResolution()` ✓ |
| UC-05 View List | Two endpoints (Option C); `ListService.getById()` + `ItemService.getByList()` — no module cycle ✓ |
| UC-06 Complete Item | `ItemService.complete(UserId, ItemId)` — lists.verifyContributionAccess ✓ |

No production code implemented. No Engineering Plan created.

---

## Remaining Open Questions

Preserved from prior reconciliation and review; no new open product questions introduced:

1. FAILED Capture retry policy (product decision — QA-001 Q-02)
2. Confidence threshold default values (configuration decision during implementation)
3. InferredPurpose storage (open product question from domain reconciliation)
4. Semantic profile cache + List mutation synchronization (deferred until Embedding strategy activated)
5. UC-05 composite endpoint (if client assembly proves problematic, `capsa-api` adapter module)
6. Karate test module placement (during test strategy)
7. Rate/cost protection for external providers (when external providers are activated)

---

## Final Architecture Status

All 13 review findings received explicit dispositions (13 ACCEPTED, 0 REJECTED, 0 DEFERRED without resolution). Both HIGH findings are resolved with compilable JPMS boundaries verified. All MEDIUM findings are resolved with the architecture graph remaining acyclic and all transaction boundaries sound.

**Architecture status: Reconciled — ready for Engineering Plan.**

---

## Limitations

- Document-level reconciliation only; no code exists to inspect.
- JPMS compilability is verified by analysis, not by an actual Java compiler.
- No dynamic execution; transaction and idempotency behavior is verified by analysis.

## Related Records

- Task: `docs/engineering/agents/tasks/CAPSA-ARCH-RECONCILE-001—ArchitectureReviewReconciliation.md`
- Review: `docs/engineering/agents/reviews/CAPSA-ARCH-REVIEW-001-architecture-review.md`
- Reconciled artifact: `docs/knowledge/architecture/capsa-arch-001-modular-monolith-v0.1.md`
