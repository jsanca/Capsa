# CAPSA-QA-001 — Acceptance Test Cases v0.1

**Role:** qa-engineer (CAPSA-QA-001)
**Status:** Draft — ready for Engineering Plan input
**Source authority:** UC-01–UC-06, reconciled domain model, CAPSA-DOMAIN-RECONCILE-001
**Architecture note:** Architecture (CAPSA-ARCH-001) is under review. Where architecture and domain/UC artifacts differ, the domain/UC artifacts are authoritative here.

---

## 1. Scope

Behavioral acceptance test cases for:

- UC-01 — Create List
- UC-02 — Add Item to List
- UC-03 — Automatically Classify Capture
- UC-04 — Resolve Ambiguous Classification
- UC-05 — View List
- UC-06 — Complete Item

These tests describe **observable behavior** — what a User or system observes after an action — not how it is implemented.

---

## 2. Source Artifacts

| Artifact | Purpose |
|---|---|
| `docs/engineering/agents/intent/intent1.md` | Product authority |
| `docs/knowledge/use-cases/uc-01-create-list.md` | UC-01 behavioral specification |
| `docs/knowledge/use-cases/uc-02-add-item-to-list.md` | UC-02 behavioral specification |
| `docs/knowledge/use-cases/uc-03-automatically-classify-capture.md` | UC-03 behavioral specification |
| `docs/knowledge/use-cases/uc-04-resolve-ambiguous-classification.md` | UC-04 behavioral specification |
| `docs/knowledge/use-cases/uc-05-view-list.md` | UC-05 behavioral specification |
| `docs/knowledge/use-cases/uc-06-complete-item.md` | UC-06 behavioral specification |
| `docs/knowledge/domain/capsa-domain-model-v0.1.md` | Domain invariants |
| `docs/engineering/agents/reports/CAPSA-DOMAIN-RECONCILE-001-reconciliation-report.md` | Reconciled invariants |

---

## 3. Coverage Principles

For each use case the following categories were considered where supported by current requirements:

- happy path
- invalid input
- ownership / user isolation
- missing referenced data
- lifecycle invariants
- repeated operation
- ambiguous outcome
- execution failure
- persistence / history preservation

Tests are created only where current behavioral requirements support the claim. Categories without evidence are omitted rather than invented.

---

## 4. Test Cases

### UC-01 — Create List

---

#### TC-UC01-001 — Happy path: create List with name and purpose

```
Given:  An authenticated User exists.
When:   The User creates a List with a valid non-blank name and a valid non-blank purpose.
Then:   A new List is persisted.
        The List has a generated, non-null identifier.
        The List is owned by the requesting User.
        The List name is the supplied name.
        The List purpose is the supplied purpose.
        The created List is returned in the response.
```

---

#### TC-UC01-002 — Create List without purpose

```
Given:  An authenticated User exists.
When:   The User creates a List with a valid non-blank name and no purpose.
Then:   A new List is persisted with no explicit purpose.
        The List is owned by the requesting User.
        The List is immediately available to receive manually assigned Items.
```

*Source: UC-01 §List without Purpose — "A List MAY exist without an explicit purpose."*

---

#### TC-UC01-003 — Reject blank name

```
Given:  An authenticated User exists.
When:   The User attempts to create a List with a blank or whitespace-only name.
Then:   List creation fails with a validation outcome.
        No List is persisted.
```

---

#### TC-UC01-004 — Reject unknown owner

```
Given:  No User exists with the specified owner identifier.
When:   A create-List request is made with that owner identifier.
Then:   List creation fails.
        No List is persisted.
```

---

#### TC-UC01-005 — Ownership isolation

```
Given:  User A has created a List named "Shopping".
        User B has created a List named "Shopping".
When:   User A retrieves their Lists.
Then:   User A sees only their own "Shopping" List.
        User A does not see User B's "Shopping" List.
```

*Note: This test does not assert that duplicate names are rejected — UC-01 explicitly leaves that policy unresolved.*

---

### UC-02 — Add Item to List

---

#### TC-UC02-001 — Happy path: add Item to owned List

```
Given:  An authenticated User exists.
        The User owns a List.
When:   The User adds an Item with a valid non-blank name to the List.
Then:   A new Item is persisted.
        The Item has a generated, non-null identifier.
        The Item belongs to the specified List.
        The Item status is PENDING.
        The Item creation timestamp is recorded and non-null.
        The Item completedAt is null.
        The created Item is returned.
```

---

#### TC-UC02-002 — Add Item with optional notes

```
Given:  An authenticated User owns a List.
When:   The User adds an Item with a valid name and non-blank notes.
Then:   The Item is persisted with the notes preserved.
```

---

#### TC-UC02-003 — Add Item without notes

```
Given:  An authenticated User owns a List.
When:   The User adds an Item with a valid name and no notes.
Then:   The Item is persisted without notes.
        The absence of notes is not a validation error.
```

---

#### TC-UC02-004 — Reject blank Item name

```
Given:  An authenticated User owns a List.
When:   The User attempts to add an Item with a blank or whitespace-only name.
Then:   Item creation fails with a validation outcome.
        No Item is persisted.
```

---

#### TC-UC02-005 — Reject add to non-existent List

```
Given:  An authenticated User exists.
        No List exists with the specified identifier.
When:   The User attempts to add an Item to the non-existent List.
Then:   Item creation fails.
        No Item is persisted.
```

---

#### TC-UC02-006 — Reject add to List the User does not own

```
Given:  User A owns List L.
        User B is authenticated and does not own List L.
When:   User B attempts to add an Item to List L.
Then:   Item creation fails with an authorization outcome.
        No Item is persisted.
```

---

#### TC-UC02-007 — Repeated Item creates a new occurrence

```
Given:  An authenticated User owns a List.
        The List contains a completed historical Item named "Soap" with a recorded completedAt timestamp.
When:   The User adds a new Item named "Soap" to the same List.
Then:   A new PENDING Item occurrence is created.
        The historical completed Item is unchanged and still present.
        The historical completedAt timestamp is not modified.
        The List now contains both the historical DONE Item and the new PENDING Item.
```

*Source: UC-02 §Repeated Items — "Adding an Item... creates a new occurrence."*

---

### UC-03 — Automatically Classify Capture

---

#### TC-UC03-001 — Happy path: confident classification produces a PENDING Item

```
Given:  An authenticated User exists with at least one List that participates in classification.
        The classification pipeline produces a sufficiently confident result.
When:   The User submits a Capture with valid non-blank content.
Then:   The original Capture content is persisted.
        A classification attempt is recorded with supporting evidence.
        The Capture is associated with the resulting classification.
        A new PENDING Item is created in the selected List.
        The Capture and the resulting Item are traceable to each other.
        The Capture is marked as successfully processed.
```

---

#### TC-UC03-002 — Original Capture is preserved regardless of normalization

```
Given:  An authenticated User submits a Capture.
When:   The Capture is normalized and processed by the pipeline.
Then:   The original, pre-normalization content submitted by the User is still stored
        and accessible as part of the Capture record.
        The normalized form used for classification does not replace the original.
```

*Source: UC-03 §Capture Preservation.*

---

#### TC-UC03-003 — Ambiguous classification: no Item created, Capture requires resolution

```
Given:  An authenticated User exists with classifiable Lists.
        The classification pipeline exhausts all strategies without achieving
        sufficient confidence.
When:   The User submits a Capture.
Then:   No Item is created.
        The Capture is persisted.
        The Capture is marked as requiring User resolution (NEEDS_RESOLUTION).
        Classification evidence from the pipeline run is preserved.
        The response indicates that resolution is required, with candidate Lists
        available to the User.
```

*Source: UC-03 §Ambiguous Classification and §Postconditions — Ambiguous.*

---

#### TC-UC03-004 — Provider/strategy execution failure is distinct from ambiguity

```
Given:  An authenticated User exists.
        A classification strategy fails to execute (e.g., provider unavailable,
        timeout, or infrastructure error).
When:   The User submits a Capture.
Then:   The Capture is marked as failed (FAILED), not as requiring User resolution.
        The Capture is NOT placed in NEEDS_RESOLUTION state.
        No Item is created.
        The failure is distinguishable from classification uncertainty
        in the observable outcome.
```

*Source: UC-03 §Failure Considerations; domain reconciliation F-02.*
*Note: Exact retry behavior is an architecture decision outside this test scope.*

---

#### TC-UC03-005 — Known Classification: prior User-confirmed evidence resolves a new Capture

```
Given:  An authenticated User has previously confirmed that a normalized Capture
        content maps to List L (via UC-04 resolution or a prior successful classification).
        That evidence is stored in Classification Memory under the User's identity.
When:   The User submits a new Capture whose normalized content matches that prior evidence.
Then:   The Capture is classified using the known evidence.
        An Item is created in List L.
        No expensive downstream strategies are required to produce this classification.
```

*Source: UC-03 §Strategy 1 — Known Classification and UC-04 §Classification Memory.*

---

#### TC-UC03-006 — User isolation: one User's classification evidence does not affect another User's classification

```
Given:  User A has confirmed that normalized content "buy quarkus book" maps to
        List "Reading" (User A's List).
        User B submits a Capture with the same content "buy quarkus book".
        User B has no prior classification evidence for this content.
When:   Capsa classifies User B's Capture.
Then:   User A's Classification Memory evidence is not used to classify User B's Capture.
        User B's Capture is classified (or sent to NEEDS_RESOLUTION) using only
        evidence scoped to User B.
```

*Source: domain reconciliation F-05 — ClassificationMemoryEntry is User-scoped.*

---

#### TC-UC03-007 — A resolved Capture does not produce duplicate Items

```
Given:  An authenticated User has submitted a Capture that was successfully classified
        and resulted in a PENDING Item.
When:   Any mechanism attempts to re-process or re-classify the same Capture.
Then:   No second Item is created for the same Capture.
        The existing Item is not modified.
```

*Source: domain reconciliation F-01 — cross-aggregate application guarantee; UC-03 §Postconditions — Classified: "Exactly one destination List is selected."*

---

### UC-04 — Resolve Ambiguous Classification

---

#### TC-UC04-001 — Happy path: User resolves ambiguous Capture

```
Given:  An authenticated User has a Capture in NEEDS_RESOLUTION state.
        Classification evidence from the pipeline run is preserved.
        The User owns the target List they select for resolution.
When:   The User selects a target List and submits the resolution.
Then:   The Capture transitions from NEEDS_RESOLUTION to RESOLVED.
        A new PENDING Item is created in the selected List.
        The original Capture content is still present.
        The original classification attempt and its evidence are preserved.
        The User's resolution is recorded as classification evidence.
        The resolution evidence is stored under the resolving User's identity.
        The new Item is traceable to the Capture.
```

---

#### TC-UC04-002 — User may resolve to a List not in the suggested candidates

```
Given:  An authenticated User has a Capture in NEEDS_RESOLUTION state with
        suggested candidates: Shopping, Reading, Quarkus.
        The User owns List "Work" which was not among the candidates.
When:   The User selects "Work" as the resolution target.
Then:   The resolution succeeds.
        A PENDING Item is created in "Work".
        Both the original candidate set and the User's off-candidate selection
        are preserved as classification evidence.
```

*Source: UC-04 §Candidate Lists — "the User is not restricted to the proposed candidates."*

---

#### TC-UC04-003 — Resolving an already-resolved Capture fails

```
Given:  An authenticated User has a Capture that is already in RESOLVED state,
        having previously produced a PENDING Item.
When:   The User attempts to submit a resolution for the same Capture.
Then:   The resolution fails.
        No second Item is created.
        The existing Item and its Capture trace are not modified.
```

*Source: UC-04 §Alternate Paths — "A Capture must not produce multiple Items through repeated resolution requests."*

---

#### TC-UC04-004 — Reject resolution of non-existent Capture

```
Given:  No Capture exists with the specified identifier.
When:   The User submits a resolution for that Capture identifier.
Then:   Resolution fails.
        No Item is created.
```

---

#### TC-UC04-005 — Reject resolution when Capture is not awaiting resolution

```
Given:  An authenticated User has a Capture that is not in NEEDS_RESOLUTION state
        (e.g., it is in PROCESSING, CLASSIFIED, or FAILED).
When:   The User attempts to submit a resolution for that Capture.
Then:   Resolution fails.
        No Item is created.
```

---

#### TC-UC04-006 — Reject resolution to a List the User cannot contribute to

```
Given:  An authenticated User has a Capture in NEEDS_RESOLUTION state.
        The User specifies a List they are not authorized to contribute to.
When:   The User submits the resolution.
Then:   Resolution fails with an authorization outcome.
        No Item is created.
        The Capture remains in NEEDS_RESOLUTION.
```

---

#### TC-UC04-007 — Reject resolution to a non-existent List

```
Given:  An authenticated User has a Capture in NEEDS_RESOLUTION state.
When:   The User submits a resolution specifying a non-existent List identifier.
Then:   Resolution fails.
        No Item is created.
        The Capture remains in NEEDS_RESOLUTION.
```

---

#### TC-UC04-008 — Resolution records User evidence without overwriting pipeline evidence

```
Given:  An authenticated User has a Capture in NEEDS_RESOLUTION state.
        The classification pipeline recorded candidate Lists with confidence scores.
When:   The User resolves the Capture by selecting a List.
Then:   The original pipeline candidates and confidence scores remain accessible.
        The User's resolution is recorded as a distinct, additive evidence entry.
        The pipeline's evidence is not replaced or overwritten.
```

*Source: UC-04 §User Resolution Is Evidence — "The original automatic classification attempt should not be overwritten."*

---

#### TC-UC04-009 — Resolution evidence contributes to Known Classification for future Captures

```
Given:  User resolves a Capture "buy quarkus book" to List "Reading".
        This resolution is stored in Classification Memory under the User's identity.
When:   The same User later submits a new Capture with the same normalized content.
Then:   The Known Classification strategy can use the recorded evidence to classify
        the new Capture without requiring pipeline escalation.
```

*Source: UC-04 §Classification Memory learning loop.*

---

### UC-05 — View List

---

#### TC-UC05-001 — Happy path: view owned List with active Items

```
Given:  An authenticated User owns a List that contains PENDING Items.
When:   The User requests the List.
Then:   The List is returned.
        The active Items belonging to the List are included.
        All returned Items belong to the requested List.
        The response includes List metadata (name, purpose) and each Item's
        identity, name, notes (if any), status, and creation timestamp.
```

---

#### TC-UC05-002 — Active view contains only PENDING Items

```
Given:  An authenticated User owns a List containing both PENDING and DONE Items.
When:   The User requests the active view of the List.
Then:   The returned active Items are all in PENDING state.
        DONE Items do not appear in the active view.
        The DONE Items still exist — they are not deleted.
```

*Source: UC-05 §Default View — "status == PENDING".*

---

#### TC-UC05-003 — Empty active List is valid

```
Given:  An authenticated User owns a List that contains no PENDING Items
        (either empty or all Items are DONE).
When:   The User requests the List.
Then:   The List is returned with an empty active Item collection.
        This is not an error.
```

---

#### TC-UC05-004 — Completed Items remain historically associated with their original List

```
Given:  An authenticated User owns a List that contains DONE Items.
When:   The User requests the historical or archive view of the List.
Then:   The DONE Items belonging to the List are returned.
        Each DONE Item shows its completion timestamp.
        The DONE Items are associated with the List in which they were originally created,
        not moved to a separate archive location.
```

*Source: UC-05 §Historical View — "It is not a special List."*

---

#### TC-UC05-005 — Reject access to non-existent List

```
Given:  No List exists with the specified identifier.
When:   The User requests that List.
Then:   A not-found outcome is returned.
        No other List or Item information is exposed.
```

---

#### TC-UC05-006 — Reject access to another User's List

```
Given:  User A owns List L.
        User B is authenticated and does not own List L.
When:   User B requests List L.
Then:   An authorization outcome is returned.
        No information about List L is exposed to User B
        beyond what is necessary to indicate access is denied.
```

---

#### TC-UC05-007 — Viewing a List does not modify any state

```
Given:  An authenticated User owns a List with Items.
When:   The User requests the List.
Then:   No List, Item, Capture, Classification, or Classification Memory state is modified
        as a consequence of the view operation.
```

*Source: UC-05 §Postconditions — "This is a read-only use case."*

---

### UC-06 — Complete Item

---

#### TC-UC06-001 — Happy path: complete a PENDING Item

```
Given:  An authenticated User owns a List containing a PENDING Item.
When:   The User marks the Item as completed.
Then:   The Item status changes to DONE.
        The Item completedAt is set to the completion timestamp (non-null).
        The completed Item is returned.
```

---

#### TC-UC06-002 — Completion does not delete the Item

```
Given:  An authenticated User has completed a PENDING Item.
When:   The User or system observes the List.
Then:   The Item still exists.
        The Item is still associated with its original List.
        The Item retains its creation timestamp.
        The Item is observable in the List's historical/completed view.
```

*Source: UC-06 §Completion Is Not Deletion.*

---

#### TC-UC06-003 — Lifecycle invariant: DONE iff completedAt is set

```
Given:  An Item transitions to DONE.
Then:   Item.completedAt is non-null.

Given:  An Item is in PENDING state.
Then:   Item.completedAt is null.
```

*Source: domain reconciliation F-06 — corrected invariant pair.*

---

#### TC-UC06-004 — DONE Item does not appear in the List's active view

```
Given:  An Item transitions to DONE.
When:   The User requests the active view of the Item's List.
Then:   The DONE Item does not appear in the active Item set.
        Other PENDING Items in the same List are unaffected.
```

---

#### TC-UC06-005 — Reject completion of another User's Item

```
Given:  User A owns a List with a PENDING Item.
        User B is authenticated and does not own that List.
When:   User B attempts to complete User A's Item.
Then:   The completion fails with an authorization outcome.
        The Item remains PENDING.
```

---

#### TC-UC06-006 — Reject completion of a non-existent Item

```
Given:  No Item exists with the specified identifier.
When:   The User attempts to complete that Item.
Then:   The operation fails.
        No state is modified.
```

---

#### TC-UC06-007 — Idempotent behavior for already-completed Item

```
Given:  An Item is already in DONE state with a recorded completedAt.
When:   The User attempts to complete the same Item again.
Then:   No second completion occurrence is created.
        The Item lifecycle is not changed.
        The original completedAt is preserved.
```

*Source: UC-06 §Alternate Paths — "The API should behave idempotently from the perspective of Item lifecycle."*
*Note: The exact response code/body for repeated completion is an API design decision not covered here.*

---

#### TC-UC06-008 — Completing an Item does not reopen historical Items

```
Given:  A List has a historical DONE Item "Soap" (Item #101) completed on a past date.
When:   The User adds a new Item named "Soap" and later completes it.
Then:   Item #101 remains in its original DONE state with its original completedAt.
        A new Item occurrence is created; it is completed separately.
        The two occurrences are distinct items with different identifiers and timestamps.
```

*Source: UC-06 §Repeated Real-World Needs.*

---

## 5. Domain Invariant Coverage

The following reconciled domain invariants are explicitly represented in the test cases above.

| Invariant | Source | Covered By |
|---|---|---|
| A resolved Capture produces at most one Item (cross-aggregate application guarantee) | CAPSA-DOMAIN-RECONCILE-001 F-01 | TC-UC03-007, TC-UC04-003 |
| Classification uncertainty ≠ execution failure | CAPSA-DOMAIN-RECONCILE-001 F-02 | TC-UC03-003, TC-UC03-004 |
| ClassificationMemoryEntry is User-scoped | CAPSA-DOMAIN-RECONCILE-001 F-05 | TC-UC03-006, TC-UC04-008, TC-UC04-009 |
| `completedAt != null` iff `status == DONE` | CAPSA-DOMAIN-RECONCILE-001 F-06 | TC-UC06-003 |
| Item completion does not delete the Item | UC-06 | TC-UC06-002 |
| Original Capture is preserved regardless of normalization | UC-03 | TC-UC03-002 |
| Classification pipeline evidence is additive; User resolution does not overwrite pipeline evidence | UC-04 | TC-UC04-008 |
| Authorization is application policy (Users cannot operate on resources they do not own) | CAPSA-DOMAIN-RECONCILE-001 F-09 | TC-UC01-005, TC-UC02-006, TC-UC04-006, TC-UC05-006, TC-UC06-005 |
| Item belongs to exactly one List | UC-02, domain model | TC-UC02-001, TC-UC04-001 |
| Viewing a List does not modify state | UC-05 | TC-UC05-007 |

---

## 6. Traceability Matrix

| Use Case | Test Cases |
|---|---|
| UC-01 — Create List | TC-UC01-001, TC-UC01-002, TC-UC01-003, TC-UC01-004, TC-UC01-005 |
| UC-02 — Add Item to List | TC-UC02-001, TC-UC02-002, TC-UC02-003, TC-UC02-004, TC-UC02-005, TC-UC02-006, TC-UC02-007 |
| UC-03 — Automatically Classify Capture | TC-UC03-001, TC-UC03-002, TC-UC03-003, TC-UC03-004, TC-UC03-005, TC-UC03-006, TC-UC03-007 |
| UC-04 — Resolve Ambiguous Classification | TC-UC04-001, TC-UC04-002, TC-UC04-003, TC-UC04-004, TC-UC04-005, TC-UC04-006, TC-UC04-007, TC-UC04-008, TC-UC04-009 |
| UC-05 — View List | TC-UC05-001, TC-UC05-002, TC-UC05-003, TC-UC05-004, TC-UC05-005, TC-UC05-006, TC-UC05-007 |
| UC-06 — Complete Item | TC-UC06-001, TC-UC06-002, TC-UC06-003, TC-UC06-004, TC-UC06-005, TC-UC06-006, TC-UC06-007, TC-UC06-008 |

**Total test cases:** 43

---

## 7. Open Product / Test Questions

### Q-01 — UC-03: Behavior when User has no classifiable Lists

**Related UC:** UC-03

**Scenario:** A User submits a Capture for automatic classification but has no Lists that participate in classification (e.g., all Lists lack a purpose, or no Lists exist yet).

**Why a decision is needed:** UC-03 states "The User has at least one List that may participate in classification" as a precondition, but does not specify what happens when that precondition is violated. Does Capsa reject the Capture immediately? Does it proceed through the pipeline and inevitably produce NEEDS_RESOLUTION? Is NEEDS_RESOLUTION meaningful when there are no candidate Lists to present?

**Alternatives:**
- Reject the Capture at submission time with a descriptive outcome.
- Classify to NEEDS_RESOLUTION and surface the empty candidate set.
- Produce FAILED (no classifiable Lists is not a provider failure, so this seems incorrect).

---

### Q-02 — UC-03/UC-04: Behavior for a FAILED Capture entering UC-04

**Related UC:** UC-03, UC-04

**Scenario:** A Capture is in FAILED state (execution failure during classification). The User later attempts to submit a resolution for it. UC-04 preconditions require `NEEDS_RESOLUTION`; FAILED is not NEEDS_RESOLUTION.

**Why a decision is needed:** The domain requires distinguishing execution failure from classification uncertainty. But if a Capture fails due to an infrastructure problem, the User may still know where the content belongs. Is there a recovery path, or is a FAILED Capture permanently unresolvable through UC-04?

**Alternatives:**
- FAILED Captures are not resolvable via UC-04; a separate retry or recovery mechanism handles them.
- Allow resolution of FAILED Captures as if they were NEEDS_RESOLUTION.

---

### Q-03 — UC-01: Duplicate List name policy

**Related UC:** UC-01

**Scenario:** A User creates two Lists with the same name (e.g., two "Shopping" Lists).

**Why a decision is needed:** UC-01 explicitly leaves this unresolved. Without a policy, no test can be written for or against uniqueness enforcement.

*This is a known open question in UC-01 and is reproduced here for Engineering Plan visibility.*

---

### Q-04 — UC-05: History view access mechanism

**Related UC:** UC-05

**Scenario:** TC-UC05-004 verifies that completed Items are historically accessible via the List. UC-05 notes that the HTTP representation of history is an API design decision (separate endpoint vs. query parameter vs. included in the default response).

**Why a decision is needed:** The test cannot specify a concrete interaction path without this decision. TC-UC05-004 expresses the behavioral requirement abstractly; the Engineering Plan will need to resolve the mechanism before it can translate to automated tests.

*This is explicitly deferred to API design in UC-05.*

---

### Q-05 — UC-06: Behavior when attempting to complete an Item that is not PENDING

**Related UC:** UC-06

**Scenario:** Future Item states (ON_HOLD, ARCHIVED, CANCELLED) are not current requirements. But the lifecycle section implies that "invalid lifecycle transition" is an error. In the current two-state model (PENDING → DONE), an Item can only be PENDING or DONE. Completing a DONE Item is handled by TC-UC06-007 (idempotent). Are there other invalid starting states possible in v0.1?

**Why a decision is needed:** In the current model, this is only relevant for the DONE → DONE case (already covered). No other states exist. If future states are added, this test category will need to expand. No new test is required now, but the Engineering Plan should flag this as a lifecycle test area.

---

## 8. Explicitly Excluded Future Behavior

The following capabilities are *not* covered because they are not part of current UC-01–UC-06 requirements. They must not be silently included in Engineering Plan test slices without explicit product authorization.

| Excluded Capability | Source |
|---|---|
| Automatic purpose inference for Lists | UC-01 §List without Purpose — "inferred purpose should initially be treated as a suggestion, not silently promoted" |
| List archival or deletion | UC-01 §Open Questions |
| Duplicate List name uniqueness enforcement | UC-01 §Duplicate name — "policy remains unresolved" |
| Pagination of List Items | UC-05 §Pagination — "not required for v0.1" |
| Filtering or sorting Items | UC-05 §Filtering / §Ordering |
| Full-text search within a List | UC-05 §Searching |
| Item ON_HOLD, ARCHIVED, CANCELLED states | UC-06 §Item Lifecycle — "not implemented until actual requirements justify them" |
| PatternDetector / recurrence detection | UC-06 §Pattern Detection — "outside UC-06 and outside the initial MVP" |
| Shared Lists (multi-user access) | UC-02 §User Context — "future shared Lists may allow additional Users" |
| Notification / push alerts | Not referenced in any current UC |
| Geolocation | Not referenced in any current UC |
| ClassificationMemory curation (merge, expire, compact) | UC-03 §Future Classification Memory Maintenance |
| Strategy-specific confidence threshold configuration | UC-03 — "configuration concerns rather than domain invariants" |
| Lexical / trigram / full-text Known Classification | UC-03 — "not v0.1 requirements" |
| ONNX, pgvector, Jev, DeepSeek, Argonaut, LangChain4j behavior | UC-03 — test cases must not depend on a particular provider |
| Classifier retry policies | UC-03 §Failure Considerations — "remain architectural decisions" |
| Creating new Lists during UC-04 resolution | UC-04 §Out of Scope |

---

## 9. Limitations

- No automated test code exists; these are design-level specifications.
- No dynamic execution; behavioral verification is by analysis against source artifacts.
- Exact HTTP representations (status codes, payload shape, endpoint paths) are provisional and belong to API design — they are intentionally absent from test preconditions and assertions.
- UC-05 history view mechanism (one endpoint or two) remains an architecture decision; TC-UC05-004 expresses the requirement abstractly.
- Confidence threshold values for classification are intentionally absent — they are configuration concerns.

---

## Related Records

- Task: `docs/engineering/agents/tasks/CAPSA-QA-001.md`
- Engineering Log: `docs/engineering/ENGINEERING_LOG.md`
