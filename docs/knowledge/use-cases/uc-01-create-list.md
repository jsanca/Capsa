# UC-01 — Create List

## Intent

Allow a User to create a semantic collection in which Capsa can organize Items.

A List is not merely a visual grouping. It may also describe the semantic criteria Capsa uses to determine whether a Capture belongs to it.

## Actor

**User**

## Endpoint candidate

```http
POST /capsa/api/lists
```

`lists` is preferred over `list` because the resource represents the Lists collection.

This endpoint is provisional until the HTTP API is formally designed.

## Input

```text
ownerId  required
name     required
purpose  optional
```

Conceptually:

```json
{
  "ownerId": "...",
  "name": "Shopping",
  "purpose": "Things I need to purchase, including groceries and household supplies."
}
```

## Preconditions

- The owner must exist.
- The caller must eventually be authorized to create Lists for that owner.
- `name` must contain meaningful non-blank content.
- `purpose`, when supplied, must contain meaningful non-blank content after normalization.

Authentication and authorization mechanisms are outside the current use-case design.

## Happy Path

1. User requests creation of a List.
2. Capsa validates the owner.
3. Capsa validates and normalizes the name.
4. Capsa accepts the supplied purpose, when present.
5. Capsa creates the List.
6. Capsa persists the List.
7. Capsa returns the created List.

## Postconditions

A new List exists belonging to the specified User.

The List can immediately receive manually assigned Items regardless of whether a purpose exists.

If a purpose exists, the List may participate in automatic classification.

## List without Purpose

A List MAY exist without an explicit purpose.

Example:

```text
name:
Shopping

purpose:
<undefined>
```

Capsa should not invent a permanent semantic definition merely because the field was omitted.

As Items accumulate, Capsa may later infer a candidate purpose from the List's history.

For example:

```text
Shopping

- soap
- coffee
- shampoo
- rice
- toothpaste
```

Capsa could propose:

> "This List appears to contain groceries, personal-care products and household items that need to be purchased."

The inferred purpose should initially be treated as a **suggestion**, not silently promoted to user-defined truth.

This distinction should be preserved:

```text
explicit purpose
      ≠
inferred purpose
```

The exact representation of inferred semantics remains unresolved.

## Alternate Paths

### Owner does not exist

Creation fails.

### Name is absent or blank

Creation fails validation.

### Purpose is absent

The List is created without an explicit semantic purpose.

Automatic classification may ignore the List, use weaker evidence from its Item history, or request clarification. The exact classification policy belongs to UC-03.

### Duplicate name

Policy remains unresolved.

Two Lists with the same name may eventually be meaningful, so uniqueness should not be imposed without a demonstrated domain requirement.

## Initial Invariants

```text
List.id != null after creation
List.owner != null
List.name != blank
```

`purpose` is optional.

A List belongs to a User.

A List's lifecycle is independent from the lifecycle of its completed Items.

## Open Questions

- Can Lists themselves be archived?
- Can two active Lists owned by the same User have the same name?
- Should inferred purpose be stored separately from explicit purpose?
- At what point is there enough history to infer a List's purpose?
- Should the User explicitly accept an inferred purpose?