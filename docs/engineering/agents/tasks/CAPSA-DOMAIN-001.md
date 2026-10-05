# CAPSA-DOMAIN-001 — Derive Capsa v0.1 Domain Model

## Objective

Derive and document the first Capsa v0.1 domain model from the existing product intent and use cases.

This is a **domain modeling task**, not an implementation task.

Do not write production Java, Quarkus, JPA, REST resources, database schemas, migrations, or infrastructure code.

The resulting model will be reviewed before Capsa's application and technical architecture are designed.

## Required Reading

Read the existing Capsa documentation first, including:

- `INTENT.md`
- `uc-01-create-list.md`
- `uc-02-add-item-to-list.md`
- `uc-03-automatically-classify-capture.md`
- `uc-04-resolve-ambiguous-classification.md`
- `uc-05-view-list.md`
- `uc-06-complete-item.md`

Locate these documents in their actual repository paths rather than assuming their location.

Treat these documents as the current source of truth.

## Context

Capsa is a personal capture and organization system.

Its core principle is:

> Capture intent with minimal interruption. Let Capsa understand where it belongs.

The v0.1 domain currently revolves around:

- Users;
- semantic Lists;
- Items;
- Captures;
- Capture normalization;
- Capture interpretation;
- automatic classification;
- classification resolution;
- classification memory;
- Item lifecycle/history.

Capsa is initially used by one real user, but the domain must not assume that only one User can exist.

Future shared Lists are foreseeable but are **not** a v0.1 feature.

## Important Existing Decisions

Preserve the decisions already established by the use cases.

### Capture and Item are different concepts

A Capture preserves what Capsa originally received.

An Item represents the useful thing eventually stored in a List.

Smart capture follows approximately:

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

An explicitly created Item does not require a synthetic Capture.

### Normalization and interpretation are distinct

Mechanical normalization is deterministic and useful for comparison/matching.

Interpretation extracts the useful Item representation from natural-language input.

Do not collapse these concepts without explaining why.

### Classification is a pipeline

The intended initial progression is:

```text
Known Classification
        ↓ unresolved
Embedding Classification
        ↓ unresolved
System One
        ↓ unresolved
NEEDS_RESOLUTION
```

Strategies should share a common conceptual result containing ranked candidates, confidence, explanation, and strategy-specific metadata where appropriate.

The domain must not depend on Jev, System One, embeddings, or another concrete provider.

### Classification Memory stores evidence

Classification Memory is:

> an evidence store, not a cache of absolute truths.

Historical classifications may conflict.

User resolutions are important evidence.

A future curator may clean, merge, expire, compact, or otherwise maintain this evidence, but no curator is required in v0.1.

### Items represent occurrences

Repeated real-world needs create distinct Item occurrences.

Example:

```text
Soap #101 → DONE Sep 12
Soap #205 → DONE Oct 11
Soap #319 → PENDING
```

Do not reopen historical Items merely because a semantically similar need appears again.

### Completion preserves history

Completion is initially:

```text
PENDING → DONE
```

A completed Item remains associated with its original List.

Archive/history is a **view over Item state**, not a special List.

Preserve at least the information necessary to distinguish creation and completion times.

Future states such as `ON_HOLD`, `ARCHIVED`, or `CANCELLED` are foreseeable but not v0.1 requirements.

### Pattern detection is future scope

Historical Item occurrences may eventually feed a `PatternDetector`.

The first future implementation could be as simple as statistics over occurrence/completion intervals.

Do not implement or fully model PatternDetector now.

Ensure only that v0.1 does not unnecessarily destroy the historical information it would require.

## Design Principle

Apply:

> Implement the simplest behavior justified by current requirements while preserving stable boundaries around areas expected to evolve.

Do not implement speculative features.

At the same time, avoid modeling shortcuts that would make foreseeable evolution unnecessarily destructive.

Examples:

- explicit Item status rather than a `completed` boolean;
- ownership rather than assuming a singleton User;
- classification strategies rather than coupling the domain to a provider;
- historical Item occurrences rather than destructive completion.

Favor smooth extension over speculative abstraction.

## Analysis Required

Derive the domain model from behavior rather than from persistence concerns.

For each proposed concept, determine whether it is best understood as one of:

- Entity;
- Value Object;
- Aggregate / Aggregate Root;
- Domain Service;
- Domain Policy;
- Domain Event;
- Port / required capability;
- Application concern rather than domain concern.

Do not force every concept into DDD terminology if the distinction does not provide value.

Explicitly analyze at least:

- `User`
- `List`
- `Item`
- `Capture`
- `ItemDraft`
- `Classification`
- `ClassificationCandidate`
- `ClassificationResult`
- `ClassificationMemory`

And the capabilities around:

- normalization;
- interpretation;
- classification pipeline;
- classification strategies.

## Questions to Resolve

The draft should explicitly reason about:

1. Aggregate boundaries.

2. Whether `List` should directly contain an in-memory collection of all Items or whether the relationship should be represented differently.

3. Ownership relationships and how to avoid preventing future shared Lists without implementing sharing now.

4. The lifecycle of `Capture`.

5. The lifecycle of `Item`.

6. The relationship between:
   - Capture;
   - ItemDraft;
   - Classification;
   - Item.

7. Whether Classification should be an Entity, Value Object, historical record, or another concept.

8. What information Classification Memory actually needs to preserve in v0.1.

9. Which timestamps are domain-significant versus infrastructure/audit metadata.

10. Identity strategy at the domain level. Do not choose database-specific generation mechanisms yet.

11. Domain invariants and where they should be enforced.

12. Which operations require domain services/policies rather than methods on entities/value objects.

13. Which future capabilities are sufficiently foreseeable that a stable extension point is justified now, without implementing the capability.

## Avoid Premature Decisions

Do NOT select or design around:

- JPA/Hibernate mappings;
- Panache;
- PostgreSQL table structures;
- Flyway migrations;
- REST DTOs;
- Jackson annotations;
- Quarkus CDI annotations;
- concrete embedding providers;
- concrete System One/Jev clients;
- Lucene;
- PostgreSQL FTS/trigrams;
- MCP implementation;
- Android/iOS implementation;
- deployment infrastructure.

Those decisions belong to later architecture and implementation work.

## Java / JPMS Context

Capsa is expected to use:

- Java 25;
- Quarkus;
- Jakarta APIs where appropriate;
- JPMS;
- PostgreSQL;
- Flyway.

The intended architecture is a modular monolith.

Core domain code should remain independent of Quarkus and infrastructure where practical.

JPMS will eventually be used to enforce meaningful module boundaries.

Do **not** design the JPMS module graph as part of this task, but identify natural domain/application boundaries that may inform it later.

## Deliverables

Create a domain design document under the appropriate Capsa documentation area.

Suggested name:

```text
capsa-domain-model-v0.1.md
```

Adapt the exact path to the repository's existing documentation conventions.

The document should contain:

### 1. Domain Overview

Concise description of the model and its central relationships.

### 2. Domain Concepts

For each important concept:

- responsibility;
- classification (Entity / Value Object / Service / etc.);
- candidate state;
- lifecycle where relevant;
- invariants.

### 3. Aggregate Analysis

Proposed aggregate boundaries with rationale.

### 4. Relationship Model

Show relationships between the major concepts.

Include at least one Mermaid or equivalent textual diagram.

### 5. Smart Capture Flow

Model:

```text
Capture
  → normalize
  → interpret + classify
  → ItemDraft + selected List
  → Item
```

including the ambiguous path through User resolution.

### 6. Item Lifecycle

Document the initial lifecycle and historical preservation rules.

### 7. Classification Model

Describe:

- ClassificationPipeline;
- ClassificationStrategy;
- ClassificationResult;
- ClassificationCandidate;
- ClassificationMemory.

Keep provider-specific mechanisms outside the domain.

### 8. Domain Invariants

Provide a consolidated list of invariants derived from UC-01 through UC-06.

### 9. Open Questions

Do not silently resolve uncertainty.

Separate:

- decisions supported by current requirements;
- reasonable recommendations;
- questions requiring further product/architecture discussion.

### 10. Future Evolution

Briefly demonstrate how the proposed boundaries could accommodate, without implementing:

- shared Lists;
- additional Item states;
- ClassificationMemory curation;
- PatternDetector;
- richer classification strategies.

The purpose is to test evolutionary fitness, not to design these features.

## Validation

Before finishing:

1. Trace every proposed major domain concept back to at least one existing use case or explicit future constraint.
2. Verify that no Quarkus/JPA/provider-specific dependency leaked into the domain model.
3. Verify that UC-01 through UC-06 can be expressed with the proposed model.
4. Identify any contradiction found in the existing use cases rather than silently choosing one interpretation.
5. Avoid speculative abstractions that have no current behavioral justification.

## Execution

Produce the domain-model draft only.

Do not begin implementation.

Do not modify the existing use cases unless a contradiction must be documented.

Record assumptions and unresolved questions explicitly for subsequent review.
