# UC-02 — Add Item to List

## Intent

Allow a User to explicitly add a new Item to a known List.

This use case represents the case where classification is unnecessary because the User has already supplied the destination.

For example, the User may open the `Shopping` List and enter:

> "Soap for the shower"

The selected List itself provides the classification.

## Actor

**User**

## Endpoint Candidate

```http
POST /capsa/api/items
```

The endpoint is provisional until the HTTP API is formally designed.

## Input

The request identifies the destination List and provides the information required to create the Item.

Conceptually:

```json
{
  "listId": "...",
  "name": "Soap for the shower",
  "notes": "The usual one"
}
```

Candidate input:

```text
listId    required
name      required
notes     optional
```

The request does **not** provide:

```text
id
userId
status
createdAt
completedAt
```

These values belong to Capsa rather than the client.

## User Context

The User is obtained from the authenticated execution context rather than accepted as Item input.

Conceptually:

```text
Authenticated User
        +
Item request
        ↓
Add Item use case
```

The supplied `listId` identifies the destination, not its owner.

Capsa must determine whether the current User is allowed to add Items to that List.

In v0.1 this may simply mean that the User owns the List.

Future shared Lists may allow additional Users to contribute without changing the fundamental use case.

## Preconditions

- The current User exists.
- The destination List exists.
- The current User is allowed to add Items to the destination List.
- The Item name contains meaningful non-blank content.

## Happy Path

1. User selects or otherwise identifies a List.
2. User provides Item content.
3. Capsa obtains the User from the execution context.
4. Capsa loads the destination List.
5. Capsa verifies that the User may contribute to the List.
6. Capsa validates the Item information.
7. Capsa creates a new Item occurrence.
8. The Item begins in `PENDING` state.
9. Capsa persists the Item.
10. Capsa returns the created Item.

## Postconditions

A new Item occurrence exists in the selected List.

Conceptually:

```text
Item
 ├── id            generated
 ├── list          supplied destination
 ├── name          supplied
 ├── notes         optional
 ├── status        PENDING
 ├── createdAt     generated
 └── completedAt   null
```

## Repeated Items

Adding an Item whose meaning resembles a previously completed Item creates a **new occurrence**.

Example:

```text
Shopping

Item #101
Soap
created: Sep 10
completed: Sep 12

Item #205
Soap
created: Oct 09
status: PENDING
```

Capsa does not reopen or overwrite the historical Item.

This preserves the history required for future capabilities such as pattern detection.

## Completion History

Completed Items remain associated with their original List.

`Archive` is considered a view over historical/completed Items rather than a special List to which Items are moved.

Therefore:

```text
Shopping
 ├── PENDING
 │    └── Light bulbs
 │
 └── COMPLETED
      ├── Soap
      ├── Coffee
      └── Rice
```

preserves more semantic information than moving completed Items into a generic Archive List.

## Relationship to Smart Capture

This use case does **not** perform automatic classification.

It represents:

> "I know where this Item belongs."

Smart capture represents a different flow:

```text
Raw Capture
    ↓
Classification
    ↓
List selected
    ↓
Item created
```

That behavior belongs to UC-03.

Both flows ultimately produce the same domain concept: an Item belonging to a List.

## Alternate Paths

### List does not exist

Creation fails.

### User cannot contribute to List

Creation fails with an authorization outcome.

### Name is blank

Creation fails validation.

### Similar historical Item exists

A new Item occurrence is still created.

Similarity does not imply identity.

Potential duplicate detection for simultaneously active Items is outside v0.1 unless real usage demonstrates a need.

## Initial Invariants

```text
Item.id != null after creation
Item.list != null
Item.name != blank
Item.status == PENDING on creation
Item.createdAt != null
Item.completedAt == null on creation
```

Every Item belongs to exactly one List in v0.1.

A completed historical Item is never implicitly reopened when a similar Item is created.

## Future Considerations

Historical occurrences may eventually be analyzed by a `PatternDetector`.

A first implementation may use simple statistics such as average or median completion intervals.

More sophisticated pattern detection should only be introduced when real evidence demonstrates a need.

Pattern detection is not part of UC-02 or the v0.1 scope.