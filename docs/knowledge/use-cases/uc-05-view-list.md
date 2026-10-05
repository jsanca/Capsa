# UC-05 — View List

## Intent

Allow a User to view the Items belonging to a List.

The primary purpose of this use case is to present the Items that currently require attention.

Lists are expected to remain relatively small during normal usage. Advanced querying capabilities should not be introduced unless real usage demonstrates a need.

## Actor

**User**

## Endpoint Candidate

```http
GET /capsa/api/lists/{listId}
```

The endpoint is provisional until the HTTP API is formally designed.

## Preconditions

- The current User exists.
- The List exists.
- The User is allowed to view the List.

In v0.1, access may simply mean that the User owns the List.

Future shared Lists may extend this rule without changing the fundamental use case.

## Happy Path

1. User requests a List.
2. Capsa obtains the User from the authenticated execution context.
3. Capsa loads the requested List.
4. Capsa verifies that the User may view the List.
5. Capsa retrieves the active Items belonging to the List.
6. Capsa returns the List and its Items.

Conceptually:

```text
Shopping

□ Soap
□ Coffee
□ Light bulbs
```

## Default View

The default List view contains Items that currently require attention.

For v0.1 this means:

```text
status == PENDING
```

Completed Items are not deleted but do not need to appear in the default active view.

## Historical View

Capsa preserves completed Items as part of the List's history.

Conceptually:

```text
Shopping

ACTIVE

□ Light bulbs


HISTORY

✓ Soap          Sep 12
✓ Coffee        Sep 19
✓ Rice          Sep 27
```

`HISTORY` or `ARCHIVE` is a view over historical Items.

It is **not** a special List.

Completed Items remain associated with the List in which they originally existed.

The exact HTTP representation of history remains an API design decision.

Possible designs include:

```http
GET /capsa/api/lists/{listId}
GET /capsa/api/lists/{listId}/history
```

or a simple semantic query parameter.

This should be decided when the HTTP contract is designed rather than as a domain requirement.

## Returned Information

The active List view should expose enough information for a client to display and act upon the Items.

Conceptually:

```text
List
 ├── id
 ├── name
 ├── purpose
 └── items
      ├── id
      ├── name
      ├── notes
      ├── status
      └── createdAt
```

The API representation does not need to mirror persistence entities.

## Ordering

No sophisticated ordering system is required for v0.1.

A stable, intuitive default ordering should be selected during API design.

For example:

```text
createdAt ascending
```

would naturally preserve the order in which Items were added.

User-defined ordering, priorities, ranking, and sorting options are outside v0.1.

## Pagination

Pagination is not required for v0.1.

Capsa Lists are expected to contain relatively small numbers of active Items.

Introducing pagination before evidence demonstrates a need would add unnecessary complexity to clients and the API.

If real usage eventually produces Lists large enough to justify pagination, it can be added at that time.

## Filtering

General-purpose filtering is not required for v0.1.

Capsa should initially support semantic views required by actual behavior rather than exposing an arbitrary filtering language.

The known useful distinction is:

```text
ACTIVE
HISTORY
```

Future Item states such as:

```text
ON_HOLD
ARCHIVED
```

may eventually introduce additional useful views.

Those states are not current requirements.

## Searching

Search within a List is outside v0.1.

If real usage demonstrates that Lists become difficult to navigate, search may later be introduced.

Historical data may eventually make search more useful than active List size alone would suggest, but this should be driven by evidence.

## Postconditions

This is a read-only use case.

No List, Item, Capture, Classification, or Classification Memory state changes as a consequence of viewing a List.

## Alternate Paths

### List does not exist

The request returns a not-found outcome.

### User cannot view List

The request returns an authorization outcome.

The API should not expose information about inaccessible Lists beyond what is necessary.

### List contains no active Items

The List is returned with an empty active Item collection.

An empty List is a valid state.

## Initial Invariants

Every returned Item belongs to the requested List.

The User must be authorized to view the requested List.

Viewing a List does not modify its state.

Completed Items remain historically associated with their original List.

## Complexity Policy

Capsa deliberately avoids introducing:

- pagination;
- arbitrary filtering;
- full-text search;
- configurable sorting;
- user-defined ordering;
- priority ranking;

until real usage demonstrates that these capabilities solve an actual problem.

> Complexity should follow evidence, not anticipation.

The architecture should permit these capabilities to be added later without requiring them in v0.1.