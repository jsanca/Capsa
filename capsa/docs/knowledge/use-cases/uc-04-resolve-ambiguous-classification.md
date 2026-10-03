# UC-04 — Resolve Ambiguous Classification

## Intent

Allow a User to resolve a Capture that Capsa could not classify with sufficient confidence.

Capsa presents the most relevant candidate Lists, the User selects the appropriate destination, and Capsa preserves that decision as classification evidence.

The resolution completes the Capture processing lifecycle and creates the corresponding Item.

## Actor

**User**

## Trigger

UC-03 — Automatically Classify Capture finishes with:

```text id="cf2h9s"
NEEDS_RESOLUTION
```

At this point:

```text id="7lmj8c"
Capture exists
Classification attempt exists
Item does not exist
```

## User Experience

Capsa should make resolution inexpensive.

Conceptually:

```text id="xfr7bc"
"I need to buy the Quarkus book."

Capsa is not sure where this belongs:

○ Shopping
○ Reading
○ Quarkus
○ Another List
```

The User selects the appropriate List and continues.

The interaction should not require re-entering the original Capture.

## Endpoint Candidate

```http id="a7e5n1"
POST /capsa/api/captures/{captureId}/resolution
```

Conceptually:

```json id="rv57f3"
{
  "listId": "..."
}
```

The endpoint is provisional until the HTTP API is formally designed.

The User is obtained from the authenticated execution context.

## Preconditions

- The current User exists.
- The Capture exists.
- The Capture belongs to the current User.
- The Capture is in `NEEDS_RESOLUTION`.
- The selected List exists.
- The User is allowed to add Items to the selected List.

## Input

```text id="60vzks"
captureId    required
listId       required
```

The original Capture content is not resubmitted.

The resolution operates on the Capture already preserved by UC-03.

## Happy Path

1. User receives an ambiguous classification result from UC-03.
2. Capsa presents candidate Lists.
3. User selects the appropriate List.
4. Capsa loads the unresolved Capture.
5. Capsa verifies that the Capture is awaiting resolution.
6. Capsa verifies that the User may contribute to the selected List.
7. Capsa records the User's resolution.
8. Capsa creates a new `PENDING` Item occurrence in the selected List.
9. Capsa marks the Capture as successfully resolved.
10. Capsa records the resolution as Classification Memory evidence.
11. Capsa returns the resulting Item and resolution.

Conceptually:

```text id="ivokz3"
Capture
NEEDS_RESOLUTION
      │
      ▼
User selects List
      │
      ├──────────────► Classification Resolution
      │
      ├──────────────► Item
      │
      └──────────────► Classification Memory
```

## Candidate Lists

UC-03 may provide ranked candidate Lists.

For example:

```text id="rcfk5m"
Shopping   0.61
Reading    0.58
Quarkus    0.54
```

The UI may use these candidates to reduce resolution friction.

However, the User is not restricted to the proposed candidates.

The User may select another accessible List.

For example:

```text id="gzvby7"
Capsa suggests:

Shopping
Reading
Quarkus

User chooses:

Work
```

This is particularly valuable evidence because it indicates that the classification pipeline's candidate set itself may have been incorrect.

## User Resolution Is Evidence

A User resolution is treated as stronger evidence than the unsuccessful automatic classification attempt.

Conceptually:

```text id="fr6gr5"
Capture:
"buy Quarkus book"

Automatic candidates:
Shopping  0.61
Reading   0.58
Quarkus   0.54

User selected:
Reading
```

Capsa preserves both:

```text id="hqpjx7"
what the classifier believed
        +
what the User selected
```

The original automatic classification attempt should not be overwritten.

## Classification Memory

Successful User resolution contributes evidence to Classification Memory.

Conceptually:

```text id="6bf2zn"
UC-04 resolution
       │
       ▼
Classification Memory
       │
       │ future Capture
       ▼
Known Classification Strategy
```

Example:

```text id="w1zql8"
First occurrence:

"buy Quarkus book"
        ↓
ambiguous
        ↓
User → Reading


Future occurrence:

"buy Quarkus book"
        ↓
Known Classification
        ↓
Reading
```

This creates Capsa's initial learning loop without requiring model training.

> When Capsa asks and the User teaches it, Capsa should preserve enough evidence to avoid asking the same question unnecessarily in the future.

Classification Memory remains an evidence store rather than a source of immutable truth.

## Item Creation

Resolution creates a new Item occurrence.

Conceptually:

```text id="p6mopb"
Capture
"I need soap"

       ↓ resolved as

Shopping

       ↓

Item
name: "I need soap"
status: PENDING
list: Shopping
```

The exact transformation from raw Capture content to the final Item representation remains subject to domain refinement.

Classification and Item content interpretation are related but distinct concerns.

## Postconditions

After successful resolution:

- the original Capture remains preserved;
- the original classification attempt remains preserved;
- the User's resolution is recorded;
- the selected List is known;
- a new `PENDING` Item exists;
- the Item belongs to the selected List;
- the Capture is no longer awaiting resolution;
- Classification Memory contains evidence of the User's decision.

## Alternate Paths

### Capture does not exist

Resolution fails.

### Capture is not awaiting resolution

Resolution fails.

A Capture must not produce multiple Items through repeated resolution requests.

### Selected List does not exist

Resolution fails.

### User cannot contribute to selected List

Resolution fails with an authorization outcome.

### User selects a List outside the suggested candidates

Resolution succeeds provided the User is allowed to contribute to that List.

The difference between suggested candidates and actual selection is preserved as classification evidence.

## Idempotency / Duplicate Resolution

A Capture must produce at most one Item through classification resolution.

Repeated or concurrent attempts to resolve the same Capture must not create duplicate Item occurrences.

The exact concurrency mechanism belongs to architecture and persistence design.

The domain invariant is:

> An unresolved Capture may transition to resolved once.

## Initial Invariants

```text id="9njs1k"
resolution.capture != null
resolution.selectedList != null
resolution.resolvedBy == USER
```

A successfully resolved Capture:

```text id="00rz94"
NEEDS_RESOLUTION → RESOLVED
```

A resolved Capture has exactly one resulting Item.

The resulting Item begins in:

```text id="i99z49"
PENDING
```

Automatic classification evidence is never replaced by User resolution evidence.

Both remain available for future evaluation.

## Relationship to UC-03

UC-03 owns automatic classification.

UC-04 owns human resolution of uncertainty.

```text id="z6vltr"
             UC-03
               │
        ┌──────┴──────┐
        │             │
   CLASSIFIED   NEEDS_RESOLUTION
        │             │
        ▼             ▼
      Item          UC-04
                      │
                      ▼
                    Item
```

Both paths ultimately produce the same domain concept.

The difference is how the destination List was determined.

## Out of Scope

UC-04 does not:

- retrain classification models;
- curate Classification Memory;
- modify List purposes automatically;
- configure classification thresholds;
- retry failed classification infrastructure;
- create new Lists as part of resolution.

These capabilities may be introduced independently if future evidence demonstrates a need.