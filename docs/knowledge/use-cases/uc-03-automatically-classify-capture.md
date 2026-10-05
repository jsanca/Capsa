# UC-03 — Automatically Classify Capture

## Intent

Automatically determine the List to which an unclassified Capture most likely belongs while minimizing cost, latency, and unnecessary model usage.

Capsa should use the cheapest and strongest available evidence first and escalate to progressively more sophisticated classification mechanisms only when uncertainty remains.

> Use the cheapest available evidence first; escalate classification only when uncertainty remains.

## Actor

**User**

Secondary capability:

**Classifier**

The Classifier represents Capsa's classification capability and is independent of any particular implementation, model, or provider.

## Endpoint Candidate

```http
POST /capsa/api/captures
```

Conceptually:

```json
{
  "content": "I need soap for the shower"
}
```

The User is obtained from the authenticated execution context and is not supplied as part of the Capture request.

## Preconditions

- The current User exists.
- The Capture contains supported, meaningful content.
- The User has at least one List that may participate in classification.

## Happy Path

1. The User submits an unclassified Capture.
2. Capsa persists the original Capture.
3. Capsa normalizes the Capture for classification purposes without modifying the original evidence.
4. Capsa evaluates the Capture through the configured Classification Pipeline.
5. A strategy produces a sufficiently unambiguous classification.
6. Capsa records the classification attempt and its evidence.
7. Capsa creates a new Item occurrence in the selected List.
8. The Capture is marked as successfully processed.
9. Capsa returns the resulting classification and Item.

Conceptually:

```text
Capture
   ↓
Normalize
   ↓
Classification Pipeline
   ↓
CLASSIFIED
   ↓
Classification
   ↓
Item
```

## Capture Preservation

The original Capture is preserved regardless of normalization or classification.

For example:

```text
original:
"Mae, necesito acordarme de comprar jabón para la ducha."

normalized:
classification-specific representation
```

Normalization must not destroy the original user input.

The Capture represents evidence of what Capsa actually received.

## Classification Pipeline

Classification is implemented as an ordered pipeline of strategies.

Initial intended pipeline:

```text
Capture
   │
   ▼
Normalize
   │
   ▼
Known Classification
   │
   │ unresolved
   ▼
Embedding Classification
   │
   │ unresolved
   ▼
System One Classification
   │
   │ unresolved
   ▼
NEEDS_RESOLUTION
```

The exact strategies and their order are configuration concerns rather than domain invariants.

A possible runtime configuration may eventually resemble:

```properties
capsa.classification.pipeline=known,embedding,system-one
```

The domain and application layers must not depend on Quarkus configuration APIs.

## Classification Strategy

Each classification mechanism participates through a common strategy abstraction.

Conceptually:

```text
ClassificationStrategy
        │
        ▼
ClassificationResult
```

A result may contain ranked candidates rather than reducing classification to a single confidence value.

Conceptually:

```text
ClassificationResult
 ├── candidates
 └── metadata

ClassificationCandidate
 ├── listId
 ├── confidence
 └── explanation
```

The exact Java representation remains an architectural decision.

Example:

```text
Shopping
confidence: 0.91
explanation:
"Semantic similarity with the List purpose."

Home
confidence: 0.63
explanation:
"Related to household concepts."
```

Strategy-specific information may be retained as metadata.

Examples include:

```text
strategy
provider
model
model revision
memory evidence
similarity metric
execution time
```

Metadata should support observability and future evaluation without becoming required domain semantics.

## Strategy 1 — Known Classification

Capsa first attempts to reuse previously established classification knowledge.

Example:

```text
Previous:

"buy soap for the shower"
    → Shopping

New:

"buy soap for the shower"
```

When sufficiently equivalent prior evidence exists, Capsa may classify the Capture without invoking more expensive strategies.

The initial implementation should favor simple deterministic matching.

For example:

```text
normalize
    +
exact match
    +
previous user-confirmed classification
```

More sophisticated lexical retrieval may later use PostgreSQL full-text search, trigrams, Lucene, or another mechanism if real evidence demonstrates the need.

Such mechanisms are not v0.1 requirements.

## Classification Memory

Classification Memory stores historical evidence rather than absolute classification truths.

> Classification Memory is an evidence store, not a cache of absolute truths.

Evidence may eventually include:

```text
original Capture
normalized representation
selected List
classification source
user resolution
timestamp
classifier metadata
```

Conflicting evidence is valid.

For example:

```text
"buy Quarkus book" → Shopping
"buy Quarkus book" → Reading
```

Capsa must not assume that historical classifications can never change or conflict.

The initial Known Classification strategy may use a deliberately simple policy such as the latest user-confirmed exact normalized match.

More sophisticated policies should be introduced only when usage demonstrates a need.

## Future Classification Memory Maintenance

Classification Memory should be designed so that a future maintenance process can be introduced without changing the classification use case.

A future `ClassificationMemoryCurator` may perform operations such as:

```text
merge duplicate evidence
detect conflicts
expire stale evidence
compact historical records
repair indexes
rebuild derived representations
```

The Curator is not part of the synchronous classification request path and is not a v0.1 requirement.

## Strategy 2 — Embedding Classification

When no sufficiently strong known classification exists, Capsa may perform semantic classification using embeddings.

The first useful semantic representation already exists in the List:

```text
List.name
List.purpose
```

Example:

```text
Shopping

"Products, groceries, household supplies,
personal-care products and other things
I need to purchase."
```

Capsa may compare the Capture embedding against semantic representations of candidate Lists.

Conceptually:

```text
Capture embedding
        ↓
similarity
        ↓
List purpose embeddings
```

Example result:

```text
Shopping    0.91
Home        0.63
Reading     0.14
Quarkus     0.08
```

Classification history may eventually contribute to a richer semantic profile for each List, but this is not required initially.

The first implementation may rely solely on List purpose.

## Strategy 3 — System One

If semantic similarity remains ambiguous, Capsa may escalate classification to a decision model.

The model receives the Capture and candidate Lists with their semantic descriptions.

Conceptually:

```text
Capture:
"I need soap for the shower"

Candidates:

Shopping
"Things I need to purchase..."

Home
"Repairs, maintenance and household tasks..."

Personal
"Personal activities and reminders..."
```

The decision model returns ranked classification candidates with confidence and explanatory evidence when available.

System One is one intended implementation strategy.

The Capsa domain must not depend on System One, Jev, or any specific provider.

## Pipeline Termination

Any strategy may terminate the pipeline when its result satisfies the configured classification policy.

A strategy that cannot make a sufficiently reliable decision passes control to the next strategy.

Conceptually:

```text
known
  │
  ├── classified ─────────────► STOP
  │
  └── unresolved
          ↓
      embeddings
          │
          ├── classified ─────► STOP
          │
          └── unresolved
                  ↓
              system-one
                  │
                  ├── classified ─► STOP
                  │
                  └── unresolved
                          ↓
                  NEEDS_RESOLUTION
```

Confidence thresholds and strategy-specific decision policies belong to classification configuration rather than the core domain.

## Successful Classification

When classification succeeds:

```text
Capture
   ↓
Classification
   ↓
selected List
   ↓
new Item occurrence
```

The Item begins in `PENDING` state.

The Capture, Classification evidence, and resulting Item remain traceable.

## Ambiguous Classification

If the pipeline finishes without a sufficiently reliable classification:

```text
Capture
   ↓
NEEDS_RESOLUTION
```

The Capture remains persisted.

**No Item is created.**

Capsa returns enough information for the User to resolve the ambiguity.

Conceptually:

```json
{
  "captureId": "...",
  "status": "NEEDS_RESOLUTION",
  "candidates": [
    {
      "listId": "...",
      "name": "Shopping"
    },
    {
      "listId": "...",
      "name": "Home"
    }
  ]
}
```

Resolution belongs to UC-04.

## Postconditions — Classified

- The original Capture remains stored.
- Classification evidence is preserved.
- Exactly one destination List is selected.
- A new `PENDING` Item occurrence exists.
- The Item belongs to the selected List.
- The Capture can be traced to the resulting Item.

## Postconditions — Ambiguous

- The original Capture remains stored.
- No Item exists for the unresolved Capture.
- Classification evidence is preserved.
- The Capture is marked as requiring User resolution.
- Candidate Lists may be presented to the User.

## Failure Considerations

Infrastructure failure is different from classification uncertainty.

For example:

```text
embedding provider unavailable
System One timeout
database failure
```

must not automatically mean:

```text
NEEDS_RESOLUTION
```

Capsa should distinguish:

```text
classification uncertainty
```

from:

```text
classification execution failure
```

Exact retry and failure policies remain architectural decisions.

## Initial Invariants

A Capture is preserved before classification produces an Item.

An Item is never created from an unresolved classification.

Every automatically classified Item belongs to exactly one selected List.

Classification evidence remains associated with the Capture.

The classification pipeline is replaceable and configurable.

The Capsa domain does not depend on a particular classification provider.

## Future Considerations

Possible future improvements include:

- lexical similarity;
- richer List semantic profiles;
- historical Item embeddings;
- hybrid retrieval;
- classifier evaluation datasets;
- memory curation;
- strategy cost budgets;
- provider rate limiting;
- classification caching;
- local classification models.

These capabilities should be introduced only when evidence demonstrates their value.