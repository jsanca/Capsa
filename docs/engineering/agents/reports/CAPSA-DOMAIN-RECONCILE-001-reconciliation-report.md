# CAPSA-DOMAIN-RECONCILE-001 — Domain Model Reconciliation Report

## Status

COMPLETED

## Scope

Reconcile `docs/knowledge/domain/capsa-domain-model-v0.1.md` with all findings from `CAPSA-DOMAIN-REVIEW-001`. The reconciled model is ready as input to architecture design.

## Sources

| Source | Role |
|---|---|
| `docs/engineering/agents/intent/intent1.md` | Product authority |
| `docs/knowledge/use-cases/uc-01` … `uc-06` | Behavioral authority |
| `docs/knowledge/domain/capsa-domain-model-v0.1.md` | Reconciled artifact |
| `docs/engineering/agents/reviews/CAPSA-DOMAIN-REVIEW-001-domain-model-review.md` | Review findings |

---

## Finding Dispositions

### F-01 — HIGH — Capture / ClassificationAttempt boundary invariant

**Review finding:** The model incorrectly attributed the cross-aggregate guarantee "a resolved Capture produces exactly one Item" as an invariant enforceable by the Capture aggregate root. `Item` is a separate aggregate; the Capture root cannot guarantee this alone.

**Action: ACCEPTED — model corrected.**

Changes applied:
- Capture §2 invariants split into two distinct sections: **aggregate-internal invariants** (enforceable at the root) and **cross-aggregate guarantee** (application-enforced).
- Aggregate-internal invariants now state: *a Capture accepts at most one ClassificationResolution; pipelineResult is append-only.*
- Cross-aggregate guarantee documents enforcement mechanism: application transactional coordination + idempotency + persistence uniqueness constraint on `Item.captureId`.
- `ClassificationAttempt` §2 classification note updated to reflect corrected rationale.
- §3 Aggregate Analysis rationale updated accordingly.
- §8 Domain Invariants Capture section restructured to match.

`ClassificationAttempt` placement within the Capture aggregate is unchanged — confirmed correct by the review.

---

### F-02 — MEDIUM — ClassificationResult cannot express execution failure

**Review finding:** `ClassificationOutcome` (CLASSIFIED | NEEDS_RESOLUTION) has no representation for execution failure. A strategy that times out must not be conflated with a strategy that ran and was genuinely uncertain.

**Action: ACCEPTED — error contract documented.**

Selected approach: execution failures propagate as exceptions from the strategy, bypassing `ClassificationResult`. Application layer catches and maps to `Capture.processingStatus = FAILED`.

Changes applied:
- `ClassificationResult` §2: added **Error contract** note.
- `ClassificationStrategy` §2 (Ports): added error contract to port specification.
- `ClassificationStrategy` §7: added error contract.
- `ClassificationPipeline` §7: added note that execution exceptions from strategies propagate out of the pipeline; application maps to `FAILED`.
- `ClassificationOutcome` remains `CLASSIFIED | NEEDS_RESOLUTION` only — keeps execution failure out of candidates/confidence semantics as required.

---

### F-03 — MEDIUM — ClassificationMemory aggregate has empty consistency boundary

**Review finding:** `ClassificationMemory` has no cross-entry invariants (conflicts are valid, entries append-only, history unbounded). Modeling it as an Aggregate Root implies a consistency boundary that does not exist.

**Action: ACCEPTED — reclassified.**

Changes applied:
- `ClassificationMemory` §2 section split into two distinct concepts:
  - **`ClassificationMemoryEntry`** — Entity; independently stored evidence; its own unit of identity and persistence.
  - **`ClassificationMemory`** — Domain Capability (port); the interface for recording and querying evidence; not an aggregate root.
- §3 Aggregate Analysis table updated: removed `ClassificationMemory` as aggregate root; added explanatory note.
- §3 rationale updated: explains why aggregate root label was incorrect.
- §7 ClassificationMemory section updated to reflect capability model.
- §10 Future Evolution updated.
- Principle preserved: *ClassificationMemory is an evidence store, not a cache of absolute truths.*

---

### F-04 — MEDIUM — CaptureNormalizer is not a replaceable infrastructure port

**Review finding:** Normalization is deterministic mechanical behavior. Its stability is a correctness requirement for Known Classification. Making it a swappable infrastructure port introduces variance risk exactly where determinism is required.

**Action: ACCEPTED — reclassified as domain-owned.**

Changes applied:
- `CaptureNormalizer` moved from the Ports section to a new **Domain Service** section.
- Rationale documented: stability is a correctness requirement; making it swappable would undermine Known Classification determinism.
- Future consideration noted: if normalization rules ever need versioning, that is an identified strategy within the domain service — introduced only with a concrete behavioral requirement.
- `CaptureInterpreter` remains a port (non-deterministic, provider-dependent) with explicit note distinguishing it from normalization.

---

### F-05 — MEDIUM — ClassificationMemoryEntry lacks User scoping

**Review finding:** Two Users may classify identical normalized text differently. A global exact-match lookup would leak evidence across Users. `userId` is a domain concern, not merely a repository-query detail.

**Action: ACCEPTED — open question #5 resolved.**

Changes applied:
- `ClassificationMemoryEntry.userId` added as a required field.
- §2 ClassificationMemoryEntry: note explaining why `userId` is required.
- §3 Cross-References: `ClassificationMemoryEntry.userId → User aggregate` added.
- §4 Mermaid diagram: `userId` added to `ClassificationMemoryEntry`.
- §7 ClassificationMemory: queries now use `userId + normalizedContent`.
- §8 Classification invariants: `ClassificationMemoryEntry` records are scoped to `userId`.
- §9 Open Questions: question #5 moved from open to **resolved**.

---

### F-06 — LOW — completedAt == null iff PENDING invariant is fragile

**Review finding:** Adding `ON_HOLD` or other non-DONE states would break the "iff PENDING" form. The "iff DONE" form is the robust canonical invariant.

**Action: ACCEPTED — invariant corrected.**

Changes applied:
- `Item` §2 invariants: replaced `completedAt == null iff PENDING` + `completedAt != null iff DONE` with the stable pair:
  - `completedAt != null iff status == DONE`
  - `completedAt == null for all non-DONE states`
- §8 Domain Invariants: same correction applied.

---

### F-07 — LOW — ClassificationPipeline placement weakly justified

**Review finding:** The pipeline's only behavior is executing an ordered strategy loop; it owns no domain invariant beyond that. "Domain Service" label is weakly justified. Acceptable as application orchestration with the stated constraints.

**Action: ACCEPTED — placement deferred to architecture; constraints preserved.**

Changes applied:
- §2 header renamed from "Domain Services" to "Application Orchestration *(placement deferred to architecture)*".
- §7 pipeline label updated to "*(placement: architecture decision — see §9)*".
- Placement note in §2 and §7 states: acceptable in either domain or application layer, provided (a) depends only on `ClassificationStrategy` port; (b) ordering/thresholds are injected.
- §9 Open Questions: added as an explicit architecture decision.

---

### F-08 — OBSERVATION — strategy port accepts normalized content only

**Review finding:** No current requirement for richer input. If a future strategy needs interpreted information, evolve the port input rather than coupling interpretation into classification.

**Action: NO CHANGE — recorded as evolutionary consideration.**

Changes applied:
- §9 Open Questions: added under "Evolutionary considerations" with explicit note on how to evolve if needed.

---

### F-09 — OBSERVATION — authorization is application policy

**Review finding:** "A User may only contribute to a List they are authorized to access" is application/security policy, not a domain invariant. Domain vocabulary is preserved; enforcement location should be clarified.

**Action: ACCEPTED — moved to application policy.**

Changes applied:
- §8 Cross-Cutting invariants: authorization statement removed from domain invariants.
- New **Application-layer policy** note added to §8 documenting that ownership check is enforced in the application layer, with the reason the vocabulary is preserved in the domain (future shared Lists).

---

## Positive Findings Preserved (P-01 through P-07)

All positive findings from CAPSA-DOMAIN-REVIEW-001 survived reconciliation unchanged:

| ID | Decision | Status |
|---|---|---|
| P-01 | `List` and `Item` remain separate aggregates | Preserved |
| P-02 | `ClassificationStrategy` as a port | Preserved |
| P-03 | `CaptureInterpreter` as a port + transient `ItemDraft` | Preserved |
| P-04 | Interpretation / classification independence | Preserved |
| P-05 | Ranked-candidate result + metadata separation | Preserved |
| P-06 | Item lifecycle + occurrence model + domain-significant timestamps | Preserved |
| P-07 | Overall evolutionary fitness | Preserved; updated for F-03/F-05 improvements |

---

## Remaining Open Questions

Product decisions that remain unresolved (not within scope of reconciliation):

1. Duplicate List names (UC-01)
2. List archival / deletion (UC-01)
3. Capture-to-Item name derivation (UC-04)
4. InferredPurpose representation (UC-01)
5. FAILED Capture recovery behavior (UC-03)

Architecture decisions deferred:

- ClassificationPipeline placement (domain vs. application layer) — F-07
- `normalizedContent` storage vs. on-demand computation
- Confidence threshold ownership

---

## UC-01 Through UC-06 Support Verification

All use cases remain expressible with the reconciled model:

| Use Case | Verification |
|---|---|
| UC-01 Create List | User + List (ownerId, name, explicitPurpose) — unchanged |
| UC-02 Add Item to List | User + List + Item; no Capture or classification — unchanged |
| UC-03 Automatically Classify Capture | Capture + CaptureNormalizer + CaptureInterpreter + ClassificationPipeline + ClassificationStrategy + ClassificationMemory + ClassificationAttempt + Item — error contract added; no behavioral change |
| UC-04 Resolve Ambiguous Classification | Capture (NEEDS_RESOLUTION → RESOLVED) + ClassificationResolution(USER) + ClassificationMemoryEntry(USER_CONFIRMED + userId) + Item — userId added; no behavioral change |
| UC-05 View List | Items queried by listId + status — unchanged |
| UC-06 Complete Item | Item.status PENDING → DONE; completedAt recorded — invariant wording improved; no behavioral change |

No Quarkus, JPA, Panache, PostgreSQL, Flyway, Jackson, CDI, or provider-specific dependency was introduced.

---

## Limitations

- Document-level reconciliation only; no code exists to inspect.
- No dynamic execution; invariant verification is by analysis.
- Out of scope: Quarkus, JPMS, JPA, REST DTOs, concrete providers, deployment.

## Related Records

- Task: `docs/engineering/agents/tasks/CAPSA-DOMAIN-RECONCILE-001.md`
- Review: `docs/engineering/agents/reviews/CAPSA-DOMAIN-REVIEW-001-domain-model-review.md`
- Reconciled artifact: `docs/knowledge/domain/capsa-domain-model-v0.1.md`
