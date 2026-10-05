# CAPSA-REQ-002 — Compound Capture / Multi-Item Capture

## Status

DISCOVERED / DEFERRED

Not part of Capsa v0.1 implementation scope.

## Motivation

A single user capture may naturally express more than one actionable item.

Example:

> "Comprar leche, huevos, café y jabón."

The desired UX is that the user speaks naturally once and Capsa derives
multiple independently classifiable items.

This requirement emerged from the intended conversational/voice capture
experience and must not be lost when evolving the current v0.1 model.

## Current v0.1 assumption

Capsa currently models approximately:

    Capture
        ↓
    Classification
        ↓
    zero or one Item

The persistence model reinforces this with the exactly-one-Item-per-Capture
invariant and the UNIQUE constraint on Item.captureId.

This behavior is intentional for v0.1.

Do NOT remove or weaken that invariant until Compound Capture is explicitly
designed and implemented.

## Desired future behavior

A Capture may contain one or more independently actionable intents/items.

Example:

    Capture:
    "Comprar leche, huevos, café y jabón"

                ↓ decomposition

        ┌───────┼───────┬───────┐
        ▼       ▼       ▼       ▼
      leche   huevos   café    jabón

        ↓       ↓       ↓       ↓
      classify independently

        ↓       ↓       ↓       ↓
       Item    Item    Item    Item

Each extracted item may:

- classify to a different List;
- require independent user resolution;
- succeed/fail independently depending on the eventual workflow;
- retain provenance to the original Capture.

## Important design consequence

This requirement invalidates the assumption:

    one Capture -> at most one Item

but does NOT necessarily imply:

    Capture -> List<Item>

directly.

A future design should explicitly model the decomposition/interpretation
step rather than merely removing the current UNIQUE constraint.

Possible concepts to investigate include:

    Capture
       ↓
    decomposition
       ↓
    extracted intents / capture parts
       ↓
    classification
       ↓
    Items

Names and exact domain types are intentionally NOT decided by this
requirement.

## Provenance requirement

It must remain possible to answer:

    Which original Capture produced this Item?

and, if decomposition becomes an explicit domain concept:

    Which extracted portion/intent of that Capture produced this Item?

This matters for:

- debugging;
- observability;
- correction;
- learning/classification feedback;
- future conversational UX.

## Resolution UX

Partial ambiguity must be supported conceptually.

Example:

    "Comprar leche, huevos y tornillos"

    leche      -> groceries ✓
    huevos     -> groceries ✓
    tornillos  -> ambiguous ?

The system should not necessarily force the entire Capture into one
classification decision because one extracted item is ambiguous.

Exact interaction semantics are deferred.

## Relationship to conversational capture

This requirement is especially relevant to the future conversational /
assistant-driven Capsa experience where natural language is the primary
input mechanism.

The conversational layer may interpret:

    "Necesito comprar leche, café y jabón"

without requiring the user to manually submit three separate captures.

Whether decomposition belongs to Capsa itself, an AI interpretation layer,
or another capability remains an architectural decision for the future.

## Deferred questions

Do not answer these as part of this requirement:

- What domain type represents an extracted item/intention?
- Is decomposition deterministic, LLM-based, or hybrid?
- Does decomposition happen before persistence or after Capture creation?
- How are partial failures represented?
- How are ambiguous extracted items resolved?
- Does classification memory learn per Capture or per extracted intent?
- How does idempotency work for replayed compound captures?
- What replaces the current Item.captureId UNIQUE invariant?
- How are corrections to decomposition represented?
- What is the exact REST/MCP/conversational API?

These require a dedicated design slice.

## Compatibility requirement

v0.1 remains valid:

    one Capture -> zero/one Item

Compound Capture must be introduced through an explicit future design and
migration.

Existing v0.1 semantics must not be accidentally weakened in anticipation
of this feature.

## Future work

Create a dedicated discovery/design task before implementation:

    CAPSA-DESIGN-COMPOUND-CAPTURE-001

That task should revisit:

- domain model;
- capture lifecycle;
- classification pipeline;
- resolution workflow;
- persistence constraints;
- API contract;
- observability events;
- idempotency/concurrency;
- migration from the v0.1 model.