# CAPSA-ARCH-REVIEW-001 — Engineering Review of Modular Monolith Architecture v0.1

## Role

Use the **engineering-reviewer** role/skill.

Perform an independent, adversarial engineering review.

Do not assume that the architecture is correct because it has already passed domain reconciliation.

Do not implement fixes.

---

## Objective

Review:

`docs/knowledge/architecture/capsa-arch-001-modular-monolith-v0.1.md`

for architectural correctness, internal consistency, enforceability, implementability, and evolutionary safety.

The architecture is currently in **REVIEW**.

Determine whether it is sufficiently sound to proceed to reconciliation and subsequently to an Engineering Plan.

---

## Required Reading

Before reviewing, read the relevant Capsa source-of-truth artifacts, including:

- product intent;
- UC-01 through UC-06;
- reconciled domain model;
- CAPSA-DOMAIN-001;
- domain engineering review;
- domain reconciliation;
- CAPSA-ARCH-001 task;
- the architecture draft under review.

Use repository artifacts as authoritative context.

Do not infer requirements that are not supported by those artifacts.

---

# Review Posture

Review the architecture independently.

Do not optimize for agreement with the author.

Attempt to falsify architectural assumptions.

Pay particular attention to cases where the document claims that a boundary is enforced but the proposed implementation may not actually enforce it.

Distinguish:

- correctness problems;
- architectural inconsistencies;
- boundary leaks;
- implementation risks;
- premature complexity;
- reasonable tradeoffs;
- deferred decisions that are legitimately deferred.

Do not flag stylistic preferences as architectural defects.

---

# Review Areas

## 1. Capability Boundaries

Evaluate the proposed capabilities:

```text
users
lists
items
capture
classification
runtime
```

Determine whether:

- ownership is coherent;
- responsibilities overlap;
- capabilities own the data they claim to own;
- public operations correspond to real use cases;
- implementation details leak across boundaries;
- any capability is acting as an accidental generic application layer.

---

## 2. JPMS Enforcement

Verify that the proposed JPMS structure can actually enforce the architectural rules claimed by the document.

Review:

- exported packages;
- internal packages;
- compile-time dependencies;
- runtime dependencies;
- CDI interaction with JPMS;
- Jakarta REST resources inside capability modules;
- framework discovery/reflection implications.

Identify any case where the design relies on runtime behavior to bypass a boundary that JPMS is intended to enforce.

---

## 3. Dependency Graph

Validate the proposed module DAG.

For every edge:

- identify the semantic reason;
- verify it is necessary;
- verify dependency direction;
- determine whether the public API exposed for the edge is minimal.

Attempt to identify hidden dependencies not represented in the graph.

Look specifically for dependencies introduced indirectly through:

- public DTOs;
- identifiers;
- authentication context;
- CDI injection;
- REST resources;
- persistence mappings;
- transactions;
- configuration.

Do not introduce a `common`, `core`, or `shared` module merely to eliminate an uncomfortable dependency.

---

## 4. Cross-Capability Collaboration

Review the rule:

> Cross-capability collaboration occurs through public semantic services, never repositories.

Verify that all described flows obey it.

Determine whether services are exposing semantic operations or merely acting as repository façades.

Check whether any public service is broader than current use cases require.

---

## 5. Persistence Ownership

Review:

- domain object vs JPA entity separation;
- repository ownership;
- converter responsibilities;
- table ownership;
- foreign-key/reference semantics across capabilities;
- pgvector / semantic-profile persistence;
- Flyway placement.

Determine whether the statement:

> A capability is the only reader and writer of its own persistence tables.

is actually preserved throughout the design.

Pay attention to data that derives from another capability's state.

---

## 6. Transaction Model

Review the proposed service-level transaction model.

Analyze:

- nested cross-capability service calls;
- Jakarta Transactions propagation;
- atomicity expectations;
- ownership implications;
- rollback semantics;
- classification memory writes;
- Capture + Item creation;
- external AI calls occurring within transaction boundaries.

Determine whether transaction boundaries align with business consistency requirements.

Identify any transaction that could remain open across unnecessarily expensive or unreliable operations.

---

## 7. Classification Architecture

Review:

```text
Capture
   ↓
ClassificationService
   ↓
ClassificationPipeline
   ├── Known
   ├── Embedding
   └── System One
```

Evaluate:

- placement of `ClassificationPipeline`;
- strategy visibility;
- strategy ordering/composition;
- ClassificationMemory ownership;
- ClassificationTarget representation;
- ONNX embedding adapter placement;
- pgvector persistence;
- Jev/DeepSeek integration;
- distinction between uncertainty and execution failure.

Verify compatibility with the reconciled domain model.

---

## 8. Capture Orchestration

Trace UC-03 and UC-04 end-to-end.

Verify that:

- normalization;
- interpretation;
- classification;
- ambiguity;
- resolution;
- Item creation;
- evidence recording

have coherent ownership and sequencing.

Check that failure at each stage has a defined architectural outcome.

---

## 9. Authentication and User Context

Review the OIDC → Capsa User mapping and request identity propagation.

Determine whether:

- runtime ownership is appropriate;
- capability REST resources can consume authenticated identity without violating module dependency rules;
- compile-time and runtime dependencies agree;
- user provisioning belongs at the proposed boundary;
- authentication and authorization remain distinct.

Do not redesign authentication unless the proposed design contains an architectural problem.

---

## 10. REST/API Placement

Review the decision to place REST resources vertically inside capability modules.

Evaluate it against:

- capability ownership;
- JPMS;
- authentication context;
- error mapping;
- JSON-B;
- Quarkus/Jakarta coupling;
- future alternative inbound adapters such as MCP.

Determine whether a centralized API module would materially improve the design or merely add ceremony.

---

## 11. UC-05 Composition

Review the unresolved UC-05 view assembly decision.

Evaluate the documented alternatives against:

- capability ownership;
- dependency direction;
- API semantics;
- client complexity;
- module cycles;
- unnecessary orchestration.

Do not select an option solely because it produces the fewest dependencies.

Recommend a disposition only if architectural evidence supports it.

---

## 12. Error Model

Review:

- capability-owned error codes;
- `ExceptionMapper`;
- development diagnostics;
- production-safe errors;
- logging behavior.

Check whether the proposed error model creates unwanted compile-time dependencies between runtime and every capability.

Determine whether that is acceptable for the composition root.

---

## 13. Cache Design

Review the proposed Quarkus Cache / Caffeine usage.

For each proposed cache, ask:

- Is there demonstrated need?
- What is the source of truth?
- What invalidates it?
- Can the owning capability observe every state change that requires invalidation?
- Does caching derived cross-capability state introduce consistency problems?

Distinguish:

> architecture permits caching

from:

> architecture should implement this cache in v0.1.

Flag premature caches where appropriate.

---

## 14. External Provider Boundary

Review Jev / DeepSeek / Argonaut / LangChain4j / ONNX placement.

Check:

- provider leakage;
- timeout behavior;
- failure propagation;
- bounded invocation;
- API-key protection;
- transaction interaction;
- startup failure behavior;
- whether rate/cost protection can be introduced later without architectural restructuring.

---

## 15. Observability and Activity Evidence

Verify that the architecture meaningfully distinguishes:

- technical logging;
- diagnostics;
- durable business/application activity.

Review whether PostgreSQL activity storage is actually required by v0.1 or merely permitted evolution.

Check privacy implications of Capture/Item content in logs.

---

## 16. Evolutionary Safety

Evaluate whether foreseeable evolution can occur without destructive architectural refactoring:

- shared Lists;
- ClassificationMemory curation;
- new Item states;
- PatternDetector;
- MCP;
- push notifications;
- geolocation;
- multiple deployment instances.

Do not require implementation of these features.

The criterion is whether current boundaries provide reasonable extension seams.

---

## 17. Simplicity

Capsa v0.1 is intentionally small.

Identify architecture that is:

- speculative;
- unused by current UCs;
- prematurely generalized;
- unnecessarily abstract;
- operationally excessive.

Also identify places where apparent simplicity creates likely destructive refactoring later.

The target is **minimum architecture with stable seams**, not minimum number of classes.

---

# Required Finding Format

Every finding must include:

```text
ID
Severity
Area
Evidence
Problem
Impact
Recommendation
Disposition
```

Use severity consistently:

```text
CRITICAL
HIGH
MEDIUM
LOW
OBSERVATION
```

`Disposition` in the initial review should normally be:

```text
OPEN
```

unless the finding is informational only.

Separate architectural defects from observations.

---

# Required Review Summary

At the end provide:

## Findings Summary

Count findings by severity.

## Strengths

Identify architectural decisions that survived adversarial review particularly well.

## Open Architecture Decisions

Identify decisions that genuinely require reconciliation or product/architecture input.

## Review Outcome

Choose one:

```text
ACCEPT
ACCEPT WITH CHANGES
REVISE
BLOCK
```

Explain the outcome briefly.

Do not modify the architecture document.

Do not implement fixes.

---

# OSK Discipline

Create/update the appropriate:

- task artifact;
- engineering-review report;
- engineering log entry.

The architecture itself remains in **REVIEW** until reconciliation is complete.

After producing the review, stop.

The next step is reconciliation by the architecture author/implementer, not implementation.
