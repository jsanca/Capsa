# UC-06 — Complete Item

## Intent

Allow a User to mark a pending Item as completed while preserving its history.

Completion is a lifecycle transition.

It does not delete, move, or replace the Item.

> Nothing is destroyed by completion. The Item becomes historical through its state.

## Actor

**User**

## Endpoint Candidate

The exact HTTP representation remains an API design decision.

A possible representation is:

```http
PATCH /capsa/api/items/{itemId}
```

with:

```json
{
  "status": "DONE"
}
```

Another possibility is an explicit domain action:

```http
POST /capsa/api/items/{itemId}/completion
```

The use case does not depend on either HTTP design.

## Preconditions

- The current User exists.
- The Item exists.
- The User is allowed to modify the Item's List.
- The Item is currently in a state from which completion is valid.

For v0.1:

```text
PENDING → DONE
```

## Happy Path

1. User marks an Item as completed.
2. Capsa obtains the User from the authenticated execution context.
3. Capsa loads the Item.
4. Capsa verifies that the User may modify the Item.
5. Capsa validates the lifecycle transition.
6. Capsa changes the Item state from `PENDING` to `DONE`.
7. Capsa records the completion timestamp.
8. Capsa persists the updated Item.
9. Capsa returns the completed Item.

Conceptually:

```text
Item

status: PENDING
createdAt: 2026-09-10
completedAt: null

        ↓ complete

Item

status: DONE
createdAt: 2026-09-10
completedAt: 2026-09-12
```

## Completion Is Not Deletion

Completing an Item must never implicitly delete it.

The Item remains associated with:

- its original List;
- its creation time;
- its completion time;
- its originating Capture, when one exists;
- any classification evidence associated with that Capture.

Conceptually:

```text
Shopping
   │
   ├── ACTIVE
   │     └── Light bulbs
   │
   └── HISTORY
         ├── Soap
         ├── Coffee
         └── Rice
```

`HISTORY` or `ARCHIVE` represents a view over Item state.

It is not a destination List.

## Item Lifecycle

The initial lifecycle is deliberately small:

```text
       complete
PENDING ────────► DONE
```

The domain should represent lifecycle using an explicit status rather than a boolean.

Initial states:

```text
PENDING
DONE
```

Possible future states may include:

```text
ON_HOLD
ARCHIVED
CANCELLED
```

These states are not implemented until actual requirements justify them.

Adding a state should preferably extend the lifecycle model rather than require replacing the representation of Item completion.

## Completion Time

Successful completion records:

```text
completedAt
```

This timestamp is domain history rather than merely audit metadata.

It may eventually support capabilities such as:

- purchase-pattern detection;
- recurrence analysis;
- behavioral suggestions;
- historical reporting.

For example:

```text
Soap

Sep 12  DONE
Oct 11  DONE
Nov 09  DONE
Dec 10  DONE
```

may eventually provide evidence of an approximately monthly pattern.

## Repeated Real-World Needs

Completing an Item closes that specific occurrence.

It does not represent the permanent completion of the underlying real-world concept.

Therefore:

```text
Item #101
Soap
DONE Sep 12
```

followed later by another need for soap produces:

```text
Item #205
Soap
PENDING
```

rather than reopening Item #101.

> Completing an Item closes an occurrence, not a concept.

This preserves historical evidence.

## Pattern Detection

Pattern detection is outside UC-06 and outside the initial MVP.

However, UC-06 preserves the information necessary for a future `PatternDetector`.

Conceptually:

```text
Historical Item occurrences
          │
          ▼
    PatternDetector
          │
          ▼
Possible recurring pattern
          │
          ▼
"Soap may be needed soon."
```

An initial future implementation may use simple statistical evidence such as average or median intervals between completed occurrences.

More sophisticated mechanisms should only be introduced if real usage demonstrates a need.

UC-06 must not invoke a PatternDetector synchronously as part of completion.

## Historical Queries

Completed Items remain queryable through their original List.

For example:

```text
Shopping

ACTIVE
□ Light bulbs

HISTORY
✓ Soap       Sep 12
✓ Coffee     Sep 19
```

The historical view is derived from state:

```text
status == DONE
```

rather than from moving Items into another collection.

## Postconditions

After successful completion:

```text
Item.status == DONE
Item.completedAt != null
```

The Item:

- still exists;
- still belongs to its original List;
- remains historically queryable;
- retains its creation timestamp;
- retains its relationship to its originating Capture when applicable.

## Alternate Paths

### Item does not exist

Completion fails.

### User cannot modify Item

Completion fails with an authorization outcome.

### Item is already DONE

No second completion occurrence is created.

The API should behave idempotently from the perspective of Item lifecycle.

The exact HTTP response for repeated completion remains an API design decision.

### Invalid Lifecycle Transition

The transition fails.

This becomes more relevant if additional Item states are introduced in the future.

## Initial Invariants

A `PENDING` Item has:

```text
completedAt == null
```

A `DONE` Item has:

```text
completedAt != null
```

Completion never changes the Item's List.

Completion never deletes the Item.

Completion never reuses another historical Item occurrence.

An Item occurrence has at most one effective completion transition.

## Evolution

The lifecycle representation should allow additional states and transitions to be introduced without replacing the fundamental Item model.

Future lifecycle behavior should be added through explicit transitions rather than accumulating unrelated boolean flags.

For example, prefer:

```text
ItemStatus
PENDING
ON_HOLD
DONE
ARCHIVED
```

over:

```text
completed = true
archived = true
onHold = false
...
```

The simplest currently justified lifecycle is implemented while preserving a stable boundary for future evolution.