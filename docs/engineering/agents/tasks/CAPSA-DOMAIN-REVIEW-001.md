# CAPSA-DOMAIN-REVIEW-001 — Engineering Review of Capsa v0.1 Domain Model

## Role

Use the **`engineering-reviewer`** role and its applicable review skills.

This is an adversarial engineering/domain-boundary review.

Do not implement code.

## Objective

Review the proposed Capsa v0.1 domain model before application architecture or implementation begins.

The purpose is to determine whether the proposed model:

- faithfully represents UC-01 through UC-06;
- places responsibilities at appropriate boundaries;
- protects actual domain invariants;
- avoids unnecessary coupling;
- supports foreseeable evolution without speculative design;
- distinguishes domain concerns from application and infrastructure concerns.

Do not assume the current model is correct.

Do not redesign it merely because another design is possible.

Report findings only where there is a concrete correctness, boundary, evolutionary, or conceptual reason for change.

## Required Reading

Read the current Capsa source-of-truth documentation before reviewing:

- product intent;
- UC-01 through UC-06;
- `docs/knowledge/domain/capsa-domain-model-v0.1.md`;
- `CAPSA-DOMAIN-001` task/report artifacts where relevant.

Use the actual repository paths.

The use cases and intent are authoritative over the derived domain-model draft.

## Current Model

The draft currently proposes five aggregate roots:

```text
User
List
Item
Capture
ClassificationMemory
```

with:

```text
Capture
  └── ClassificationAttempt
        ├── ClassificationResult
        └── ClassificationResolution
```

and transient:

```text
ItemDraft
```

The proposed smart-capture flow is approximately:

```text
                     Capture
                        │
                   normalization
                        │
              ┌─────────┴─────────┐
              ▼                   ▼
        interpretation       classification
              │                   │
              ▼                   ▼
          ItemDraft              List
              │                   │
              └─────────┬─────────┘
                        ▼
                       Item
```

Classification is modeled as a configurable strategy pipeline.

Classification Memory stores historical evidence rather than immutable truth.

## Review Areas

Perform the normal `engineering-reviewer` analysis first.

The following areas deserve particular scrutiny, but they are **review questions, not predetermined findings**.

### 1. Aggregate Boundaries

Evaluate whether each proposed Aggregate Root exists because of a real consistency/lifecycle boundary rather than merely because it is an important noun.

Review especially:

```text
User
List
Item
Capture
ClassificationMemory
```

Determine whether the boundaries support UC-01 through UC-06 without requiring unnecessarily large aggregates or cross-aggregate invariants that cannot actually be enforced by the proposed root.

### 2. List / Item Separation

Review the recommendation that:

```text
List does NOT contain Collection<Item>
```

and Items instead reference:

```text
ListId
```

Consider lifecycle independence, historical growth, querying, consistency requirements, and foreseeable shared Lists.

Do not reject or accept this merely based on DDD convention.

### 3. ClassificationMemory Boundary

Challenge the proposal that `ClassificationMemory` is an Aggregate Root containing memory entries.

Consider whether the actual domain may instead be better represented as something such as:

```text
ClassificationMemoryEntry
```

with independently persisted evidence and:

```text
ClassificationMemory
```

as a domain/application capability for querying and recording evidence.

Consider:

- append behavior;
- potentially large history;
- conflicting evidence;
- query patterns;
- future curation;
- transactional invariants.

Recommend change only if the aggregate semantics materially benefit from it.

### 4. Capture / ClassificationAttempt Boundary

Review the decision to place `ClassificationAttempt` inside the `Capture` aggregate.

Determine whether its lifecycle and invariants genuinely belong there.

In particular, analyze the stated invariant:

> A resolved Capture produces exactly one Item.

`Item` is currently modeled as a separate Aggregate Root.

Determine what the Capture aggregate can actually guarantee itself and what requires application-level transactional coordination, persistence constraints, idempotency, or another mechanism.

Distinguish a **domain invariant** from the **mechanism used to enforce it**.

### 5. ClassificationPipeline Placement

The draft currently models `ClassificationPipeline` as a Domain Service.

Challenge that placement.

The pipeline:

- executes configured strategies;
- has configurable ordering;
- has configurable thresholds/policies;
- contains no provider-specific implementation.

Determine whether this represents domain behavior or application orchestration/policy.

Consider whether moving it would improve the domain/application boundary or merely relocate code without architectural benefit.

### 6. ClassificationStrategy Boundary

Review whether `ClassificationStrategy` is appropriately modeled as a port.

The domain must remain independent from:

- embedding providers;
- System One;
- Jev;
- LLMs;
- lexical engines;
- other concrete classification technologies.

Determine where the abstraction naturally belongs.

### 7. CaptureNormalizer Boundary

The draft treats `CaptureNormalizer` as a port implemented by infrastructure.

Challenge this assumption.

Normalization is currently described as deterministic mechanical behavior such as:

```text
trim
case normalization
Unicode normalization
punctuation/whitespace normalization
```

Determine whether this is:

- domain behavior;
- application/core utility;
- a replaceable port;
- infrastructure;
- or something else.

Do not create an abstraction merely because an implementation could theoretically vary.

### 8. CaptureInterpreter Boundary

Review `CaptureInterpreter` separately from normalization.

Interpretation may involve semantic/AI processing and produces:

```text
ItemDraft
```

Determine whether the proposed boundary is appropriate and whether `ItemDraft` being transient is justified by current requirements.

### 9. Interpretation vs Classification

The agreed conceptual flow deliberately branches after normalization:

```text
Normalized Capture
      │
      ├──► Interpretation ──► ItemDraft
      │
      └──► Classification ──► selected List
```

The results later join to create the Item.

Review whether this independence is sound.

Also consider whether some classification strategies may legitimately benefit from interpreted information.

Do not unnecessarily couple interpretation and classification merely because sharing information might improve classification.

If useful, recommend a boundary that permits richer strategies later without requiring a destructive refactor.

### 10. Classification Results

Review:

```text
ClassificationResult
ClassificationCandidate
confidence
explanation
metadata
```

The design intentionally follows a ranked-candidate style rather than:

```text
Classifier → single ListId
```

Determine whether the proposed result representation keeps strategy-specific information appropriately separated from required semantics.

### 11. Classification Memory Scope

Determine whether classification evidence should be scoped:

```text
system-wide
per User
per List namespace
other
```

Lists currently belong to Users, and different Users may legitimately classify equivalent natural-language input differently.

Treat this as a domain question where possible rather than merely a repository-query detail.

### 12. Item Lifecycle

Review:

```text
PENDING → DONE
```

and the decision that:

- completion does not delete;
- completed Items remain in their original List;
- history/archive is a view;
- repeated needs create new Item occurrences;
- `createdAt` and `completedAt` are domain-significant.

Determine whether this model preserves enough information for the stated v0.1 behavior and foreseeable `PatternDetector` without prematurely implementing recurrence/pattern features.

### 13. Evolutionary Fitness

Evaluate whether the proposed model can accommodate, through reasonably smooth extension:

- shared Lists;
- additional Item states;
- richer classification strategies;
- ClassificationMemory curation;
- PatternDetector.

Do not design these features.

Identify only places where a current decision would likely force unnecessary destructive refactoring later.

## Review Principles

Apply the following principles:

> Implement the simplest behavior justified by current requirements while preserving stable boundaries around areas expected to evolve.

And:

> Future capabilities should preferably be introduced by extension rather than replacement.

However:

- do not add abstractions solely for hypothetical flexibility;
- do not mistake YAGNI for ignoring foreseeable boundary problems;
- do not optimize persistence before persistence architecture exists;
- do not introduce Quarkus/JPA concerns into the domain review.

## Out of Scope

Do not design or implement:

- Quarkus architecture;
- CDI wiring;
- JPMS module graph;
- JPA/Hibernate mappings;
- Panache;
- PostgreSQL schemas;
- Flyway;
- REST DTOs;
- concrete classifier providers;
- deployment;
- Android/iOS clients;
- MCP;
- PatternDetector implementation;
- ClassificationMemoryCurator implementation.

Architecture work begins only after this review is reconciled.

## Findings Format

For every finding provide:

```text
ID
Severity
Area
Evidence
Why it matters
Recommendation
```

Use severity proportional to actual impact.

Prefer:

```text
BLOCKER
HIGH
MEDIUM
LOW
OBSERVATION
```

Do not manufacture findings to populate every severity.

Explicitly distinguish:

- domain-model correctness issue;
- boundary concern;
- architecture decision deferred appropriately;
- optional improvement;
- no issue found.

## Positive Findings

Also identify decisions that were specifically challenged but that survive review.

For example, if analysis confirms that `List` and `Item` should remain separate aggregates, say so and explain why.

The review should therefore tell us both:

```text
what should change
```

and:

```text
what is worth preserving
```

## Deliverable

Create an engineering review report following existing repository conventions.

Suggested identity:

```text
CAPSA-DOMAIN-REVIEW-001
```

Do not modify `capsa-domain-model-v0.1.md` as part of the review.

Do not implement fixes.

The domain model remains in `REVIEW` until findings are reconciled.

## Completion Criteria

The task is complete when:

1. intent and UC-01 through UC-06 have been read;
2. the domain-model draft has been reviewed against those sources;
3. aggregate boundaries have been challenged;
4. domain/application/infrastructure responsibility boundaries have been challenged;
5. cross-aggregate invariants have been examined;
6. evolutionary fitness has been evaluated;
7. findings and positive conclusions are documented;
8. unresolved product decisions remain explicitly unresolved;
9. no production implementation has been performed.

Stop after producing the review.
