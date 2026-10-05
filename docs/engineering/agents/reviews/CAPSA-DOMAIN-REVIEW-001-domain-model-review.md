# CAPSA-DOMAIN-REVIEW-001 — Engineering Review of Capsa v0.1 Domain Model — Review

## Status

Complete

## Verdict

**CHANGES REQUIRED** — the model is fundamentally sound and expresses UC-01 through UC-06, but findings F-01 through F-05 must be reconciled before application architecture and implementation begin. No BLOCKER was identified. The domain model remains in `REVIEW`.

## Scope

Adversarial engineering/domain-boundary review of `docs/knowledge/domain/capsa-domain-model-v0.1.md` against the product intent and UC-01 through UC-06. No code, persistence, Quarkus, or provider concerns were designed. No implementation was performed.

## Sources Reviewed

- `docs/engineering/agents/intent/intent1.md` — product intent and technical direction
- `docs/knowledge/use-cases/uc-01-create-list.md` … `uc-06-complete-item.md`
- `docs/knowledge/domain/capsa-domain-model-v0.1.md` — reviewed artifact
- `docs/engineering/agents/tasks/CAPSA-DOMAIN-001.md` — derivation task

The use cases and intent are treated as authoritative over the derived draft.

## Method

Applied architecture-review (dependency direction, port placement), boundary-review (aggregate/port ownership), and adversarial-analysis (invariant falsification) disciplines. Challenge-first: each aggregate boundary and port placement was tested for a real consistency/lifecycle/coupling reason to exist, and each invariant was tested for a counterexample. Findings are reported only where a concrete correctness, boundary, evolutionary, or conceptual reason for change exists.

---

## Findings

### F-01 — `HIGH` — Capture / ClassificationAttempt boundary

**Type:** domain-model correctness (invariant attribution).

**Area:** Review Area 4.

**Evidence:** Model §3 states *"ClassificationAttempt belongs within the Capture aggregate… The invariant 'a resolved Capture produces exactly one Item' must be enforced at the Capture aggregate root."* UC-04 requires *"A Capture must produce at most one Item through classification resolution"* and calls out concurrent/duplicate resolution as a failure mode.

**Why it matters:** `Item` is a separate Aggregate Root. The Capture aggregate can enforce its **own** invariants — at most one `ClassificationResolution`, append-only `pipelineResult`, never overwrite the resolution — but it cannot guarantee that exactly one `Item` was created, because the `Item` is outside its consistency boundary. If implementers trust the stated boundary, duplicate resolution (concurrent requests, retries) bypasses the "invariant" and produces duplicate Items.

**Recommendation:** Split the statement into two distinct claims:
1. **Capture-internal invariant** (correctly enforced at the aggregate root): *a Capture accepts at most one `ClassificationResolution`; `pipelineResult` and `resolution` are append-only.*
2. **Cross-aggregate guarantee** (NOT enforceable inside Capture): *exactly one `Item` per resolved Capture* — enforced by the application via a transaction spanning Capture-resolution + Item-creation, idempotency, and a persistence uniqueness constraint (e.g., unique index on `Item.captureId`).

Keep `ClassificationAttempt` inside `Capture` (the placement is correct); correct the rationale so the model distinguishes a domain invariant from the mechanism used to enforce it.

---

### F-02 — `MEDIUM` — ClassificationResult cannot express execution failure

**Type:** domain-model correctness (gap).

**Area:** Review Area 10; UC-03 "Failure Considerations".

**Evidence:** `ClassificationOutcome` is defined as `CLASSIFIED | NEEDS_RESOLUTION` (model §2, §7). UC-03 requires distinguishing *classification uncertainty* from *classification execution failure* (provider timeout, DB failure), and the model states `NEEDS_RESOLUTION` and `FAILED` are distinct Capture states.

**Why it matters:** The `ClassificationResult`/`ClassificationOutcome` type has no representation for "the strategy failed to execute." A strategy that times out must not be conflated with a strategy that ran and was genuinely uncertain. The model records `FAILED` on `Capture.processingStatus` but never defines how a strategy signals execution failure back to the pipeline.

**Recommendation:** Specify the `ClassificationStrategy` port's error contract. Either (a) add a `FAILED`/`ERROR` outcome to `ClassificationOutcome` (or wrap the result), or (b) document that execution failures propagate as an error/exception channel from the strategy, bypassing `ClassificationResult`, and are mapped to `Capture.processingStatus = FAILED` by the application. Keep `FAILED` out of the "candidates/confidence" semantics.

---

### F-03 — `MEDIUM` — ClassificationMemory aggregate has an empty consistency boundary

**Type:** boundary concern.

**Area:** Review Area 3.

**Evidence:** Model §2 models `ClassificationMemory` as an "Aggregate Root" containing a `ClassificationMemoryEntry` collection; §2/§3 also state entries are append-only, conflicting entries are valid evidence, and history is unbounded.

**Why it matters:** An aggregate root exists to enforce consistency invariants across a boundary. `ClassificationMemory` has no cross-entry invariant (conflicts are valid, entries are never overwritten), and its unbounded growth conflicts with the "load the aggregate" mental model a root implies. The aggregate-root label promises a consistency boundary that does not exist.

**Recommendation:** Model `ClassificationMemoryEntry` as the unit of identity/persistence (its own record/aggregate), and treat `ClassificationMemory` as a domain/application **capability** for querying and recording evidence — i.e., a port/repository, not an aggregate root containing the entries. This aligns with the model's own statements that curation operates independently and read/write patterns are independent.

---

### F-04 — `MEDIUM` — CaptureNormalizer is not a replaceable infrastructure port

**Type:** boundary concern.

**Area:** Review Area 7.

**Evidence:** Model §2 models `CaptureNormalizer` as a port with *"Domain defines the interface; infrastructure provides the implementation,"* while describing it as *"Deterministic: same input always produces the same output"* and *"Implementation detail: lowercase, trim, punctuation normalization."* UC-03 Known Classification performs *"normalize + exact match + previous user-confirmed classification."*

**Why it matters:** Normalization is deterministic mechanical behavior whose **stability is a correctness requirement**: Known Classification depends on `normalizedContent` being identical across all consumers. Making it a swappable infrastructure port introduces variance risk exactly where determinism is required, with no justified reason for replaceability (unlike the classifier).

**Recommendation:** Own normalization in the domain (a domain service or pure function with a single canonical algorithm). Reserve port status for genuinely replaceable, non-deterministic, provider-dependent capabilities: `CaptureInterpreter` and `ClassificationStrategy`.

---

### F-05 — `MEDIUM` — ClassificationMemoryEntry lacks a User scoping dimension

**Type:** open question confirmed + data-model gap.

**Area:** Review Area 11.

**Evidence:** The intent requires *"the domain must not assume a singleton user"*; Lists belong to Users; Known Classification is per-User. The model's `ClassificationMemoryEntry` candidate state (`id`, `normalizedContent`, `selectedListId`, `resolutionSource`, `captureId`, `recordedAt`, `metadata`) omits any User dimension. The model already lists scope as open question #5.

**Why it matters:** Two Users may classify identical normalized text differently; a global exact-match lookup would leak evidence across Users. The entry shape as drafted cannot express per-User scoping, and it is a domain concern (who owns the evidence), not merely a repository-query detail.

**Recommendation:** Resolve before persistence design. Recommend adding a `userId` to the entry (or committing to per-User scoping) and stating it explicitly. Keep the open question marked until a product decision, but flag the missing field so it is not silently defaulted.

---

### F-06 — `LOW` — `completedAt == null iff PENDING` invariant is fragile

**Type:** evolutionary fitness.

**Area:** Review Area 12.

**Evidence:** Model §8 states `completedAt == null iff status == PENDING` and `completedAt != null iff status == DONE`. Future states `ON_HOLD`/`ARCHIVED` are foreseen (§6, §10).

**Why it matters:** The first iff holds only while `PENDING` is the sole non-`DONE` state. Adding `ON_HOLD` (where `completedAt == null` but `status != PENDING`) breaks it. The second iff is the robust one.

**Recommendation:** State the canonical invariant as `completedAt != null iff status == DONE`, and treat `completedAt` as `null` for every non-`DONE` state. Drop the redundant, fragile "iff PENDING" form.

---

### F-07 — `LOW` — ClassificationPipeline placement is weakly justified

**Type:** boundary concern (minor).

**Area:** Review Area 5.

**Evidence:** Model §2/§7 classify `ClassificationPipeline` as a Domain Service whose *"strategy list is wired by application/infrastructure"* and whose order/thresholds are *"configuration, not a domain invariant."*

**Why it matters:** The pipeline's only behavior is "execute ordered strategies until `CLASSIFIED` or exhaust"; it owns no domain invariant beyond that loop, and both order and thresholds are configuration. The essential constraint is narrower than the "Domain Service" label implies.

**Recommendation:** Keep it wherever convenient as long as (a) it depends only on the `ClassificationStrategy` port, and (b) ordering/thresholds are injected, not hard-coded. Consider labeling it application orchestration rather than a domain service; either is acceptable if those two constraints hold.

---

### F-08 — `OBSERVATION` — strategy port accepts normalized content only

**Type:** stable extension point.

**Area:** Review Area 9.

**Evidence:** `ClassificationStrategy.classify(normalizedContent, candidateLists, memoryQuery)` takes `normalizedContent`, not interpreted info. Future richer strategies (e.g., System One) could benefit from interpreted content.

**Why it matters:** No current requirement (UC-03 System One uses Capture + candidate Lists). If a future strategy needs the interpretation, the port signature changes.

**Recommendation:** No change now. If a future strategy needs interpreted information, evolve the port input (a richer input type carrying both normalized and interpreted forms) rather than coupling interpretation into classification. A deliberate extension point, not a defect.

---

### F-09 — `OBSERVATION` — authorization is application policy, not a domain invariant

**Type:** boundary concern (authorization).

**Area:** Review Area 1; cross-cutting invariants.

**Evidence:** Model §8 lists under "Cross-Cutting" domain invariants: *"A User may only contribute to a List they are authorized to access (in v0.1: User must own the List)."*

**Why it matters:** Authorization is application/security policy, not a domain invariant of any aggregate; it cannot be enforced by domain entities and belongs to the application layer.

**Recommendation:** Keep the *"User may contribute to List"* abstraction in the domain vocabulary (it supports future shared Lists), but document that the ownership check is enforced in the application layer, not as a domain invariant.

---

## Positive Findings (challenged and confirmed)

| ID | Decision | Rationale |
| --- | --- | --- |
| P-01 | `List` and `Item` remain separate aggregates (Area 2) | UC-05 queries by `listId + status`; UC-02/UC-06 create/complete Items without touching the `List` aggregate; unbounded history and independent lifecycle justify separation. Correct, not merely DDD convention. |
| P-02 | `ClassificationStrategy` as a port (Area 6) | Keeps the domain independent of embeddings, System One, Jev, LLMs, and lexical engines. Correct boundary. |
| P-03 | `CaptureInterpreter` as a port + transient `ItemDraft` (Area 8) | Interpretation is non-deterministic/provider-dependent, so the port is justified; `ItemDraft` transient is justified (no independent persistence requirement). |
| P-04 | Interpretation/classification independence (Area 9) | Matches intent and UC-03/UC-04 ("related but distinct concerns"). Sound. |
| P-05 | Ranked-candidate result + metadata separation (Area 10) | Preserves ranked candidates for UC-04, keeps strategy-specific data in non-semantic `metadata`, and preserves `pipelineResult` alongside `resolution`. Correct. |
| P-06 | Item lifecycle + occurrence model + domain-significant timestamps (Area 12) | Preserves `PatternDetector`-relevant history without implementing recurrence; completion is not deletion; archive is a view. Correct. |
| P-07 | Overall evolutionary fitness (Area 13) | Shared lists (ownership abstraction), additional states (enum), richer strategies (port), curation (separate memory), PatternDetector (timestamps) are all reachable by extension — subject to F-06 and F-08. |

## Unresolved Product Decisions (left open by design)

Correctly captured in model §9; this review does not resolve them:

1. Duplicate List names (UC-01).
2. List archival/deletion (UC-01).
3. `normalizedContent` storage vs on-demand computation.
4. Capture-content → Item-name derivation (UC-04).
5. ClassificationMemory scope (per-User vs system-wide) — see F-05.
6. Confidence-threshold ownership.
7. `InferredPurpose` representation.
8. `FAILED` Capture recovery behavior.

## Limitations

- Document-level review only: no code, persistence, or Quarkus artifacts exist to inspect.
- No dynamic/adversarial test execution (no implementation to run); invariant challenges are by construction and analysis, not execution.
- Out of scope per task: Quarkus, CDI, JPMS, JPA/Hibernate, Panache, PostgreSQL, Flyway, REST DTOs, concrete providers, deployment, clients, MCP, PatternDetector, and ClassificationMemoryCurator.

## Related Records

- Task: `CAPSA-DOMAIN-REVIEW-001` — this review.
- Reviewed task: `CAPSA-DOMAIN-001` (`docs/engineering/agents/tasks/CAPSA-DOMAIN-001.md`).
- Reviewed artifact: `docs/knowledge/domain/capsa-domain-model-v0.1.md` (not modified).
- Sources: `docs/engineering/agents/intent/intent1.md`, `docs/knowledge/use-cases/uc-01`…`uc-06`.
