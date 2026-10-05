# Capsa

## Intent

Capsa is a personal capture and organization system designed to minimize the friction between remembering something and reliably storing it where it belongs.

The core idea is simple:

> Capture intent with minimal interruption. Let Capsa understand where it belongs.

A user should not need to open a specific list, decide how an item should be categorized, type a carefully structured task, and save it.

For example:

> "I need soap for the shower."

should be enough for Capsa to capture the intent, classify it into an appropriate list such as `Shopping`, and create the corresponding pending item.

Capsa is initially a personal tool, but its domain should not assume that only one user can exist. Future versions may support shared lists between users.

---

## Problem

Traditional TODO and note applications impose friction at two important moments:

### Capture friction

The user must often:

1. remember to open the application;
2. locate the appropriate list;
3. create an item;
4. type or structure it;
5. save it.

Capsa aims to reduce this interaction to expressing the intent.

### Organization friction

The user should not need to decide where every captured item belongs.

Lists in Capsa carry semantic meaning that can be used by a classifier to automatically organize captures.

Example:

**Shopping**

> Physical products, groceries, household supplies, or other things I need to purchase.

**Reading**

> Articles, documentation, papers, books, or other material I want to read later.

Given:

> "I need soap for the shower."

Capsa should be able to infer:

`Shopping → Buy soap for the shower`

When Capsa cannot classify a capture reliably, it asks the user instead of silently making an uncertain decision.

The user's resolution is preserved as evidence that may improve future classification.

---

# Product Principles

## Capture should be cheap

Capturing something should require as little interruption as possible.

Clients may provide different capture mechanisms:

- text;
- speech-to-text;
- structured content;
- URLs;
- external integrations.

Capsa itself does not need to know how text was captured.

---

## Clients capture; Capsa interprets

Speech recognition, keyboards, geofencing, and device-specific capabilities belong primarily to clients.

The Capsa backend receives captures and owns their interpretation, classification, persistence, and lifecycle.

This allows Android, iOS, web, MCP, and future clients to share identical classification behavior.

---

## Classification is a capability, not a provider

The Capsa domain must not depend on a particular classification technology.

Possible implementations may include:

- rules;
- embeddings;
- System One decision models;
- LLMs;
- combinations of several strategies.

Clients must not know which mechanism performs classification.

---

## Preserve evidence

The original capture should not be discarded after an Item is produced.

For example:

```text
Capture:
"I need to remember to buy soap because we're almost out."

Interpretation:
"Buy soap"

Classification:
Shopping

Resolution:
Automatically classified
```

Likewise, user corrections should be preserved.

This history may later provide a real dataset for evaluating and improving Capsa's classification behavior.

---

## Uncertainty is explicit

Capsa should not pretend to know something when classification is ambiguous.

Given:

```text
"Review the new architecture"
```

Capsa may determine:

```text
Quarkus?
Reading?
```

Rather than silently selecting one, Capsa asks the user.

The resulting correction becomes part of the classification history.

---

# v0.1 — Smart Capture

The purpose of v0.1 is to answer one question:

> Can Capsa make capturing and organizing a pending item meaningfully easier than manually maintaining TODO lists?

The initial version supports:

- users;
- semantic lists;
- text captures;
- automatic classification;
- explicit ambiguity resolution;
- viewing list contents;
- completing items.

Speech-to-text belongs to clients and therefore does not need to be implemented by the Capsa API itself.

---

# Actors

## User

A person who owns lists, submits captures, resolves ambiguous classifications, views pending items, and completes them.

v0.1 may initially be operated by a single real user, but the domain must not assume a singleton user.

A future version may allow multiple users to share lists.

## Classifier

A capability used by Capsa to determine which candidate List best matches a Capture.

The Classifier is not a domain entity and is not tied to any particular AI provider or implementation.

---

# v0.1 Use Cases

## UC-01 — Create List

A User creates a semantic List.

A List initially contains:

- identifier;
- owner;
- name;
- purpose.

The purpose describes the semantic intent of the List and may be consumed by classification mechanisms.

Example:

```text
Name:
Shopping

Purpose:
Physical products, groceries, household supplies,
and other things I need to purchase.
```

---

## UC-02 — Capture Item

A User submits content representing something they want Capsa to remember.

Example:

```text
"I need soap for the shower."
```

Capsa preserves the submitted content as a Capture before classification.

The capture mechanism is irrelevant to the backend.

It may originate from:

- Android;
- iOS;
- web;
- MCP;
- another future client.

---

## UC-03 — Automatically Classify Capture

Capsa evaluates a Capture against the Lists available to the User.

When classification is sufficiently unambiguous, Capsa assigns the resulting Item to the selected List.

The domain must not require a particular classification algorithm.

---

## UC-04 — Resolve Ambiguous Classification

When Capsa cannot confidently determine the appropriate List, it exposes the ambiguity to the User.

The User selects the appropriate List.

Capsa preserves the classification attempt and the User's resolution.

---

## UC-05 — View List

A User views the pending Items belonging to a List.

Advanced filtering, searching, pagination, and ordering are not requirements of the initial version unless implementation experience demonstrates a need.

---

## UC-06 — Complete Item

A User marks an Item as completed.

Completion does not delete the Item.

Its lifecycle and completion time should be preserved.

---

# Preliminary Domain

## User

Represents a Capsa user.

Initial candidate attributes:

```text
User
 ├── id
 ├── email
 ├── passwordHash
 ├── nickname
 └── photo reference
```

Passwords are never stored reversibly.

Authentication details remain subject to architectural design.

---

## List

A semantic collection of Items.

```text
List
 ├── id
 ├── owner
 ├── name
 └── purpose
```

A List's purpose is domain information used to describe what belongs in the List.

Location/context activation is deliberately excluded from v0.1.

---

## Capture

Immutable or effectively append-only evidence of input received by Capsa.

Candidate attributes:

```text
Capture
 ├── id
 ├── user
 ├── content
 ├── contentType
 ├── capturedAt
 ├── source?
 └── processingStatus
```

`source` is considered metadata rather than essential domain information.

A Capture is not an Item.

---

## Item

An actionable or retainable thing produced from a Capture.

Candidate attributes:

```text
Item
 ├── id
 ├── list
 ├── name
 ├── notes
 ├── status
 ├── createdAt
 └── completedAt?
```

The necessity of an explicit Item `type` remains unresolved. List semantics may already provide sufficient classification.

Status should be modeled explicitly rather than as a boolean.

Initially:

```text
PENDING
DONE
```

Possible future states include:

```text
ON_HOLD
ARCHIVED
```

These future states are not v0.1 requirements.

---

## Classification

Classification is currently a domain concept rather than a committed persistence entity.

It represents the decision connecting a Capture with a List.

Relevant information may include:

```text
Classification
 ├── capture
 ├── candidate lists
 ├── selected list
 ├── classifier
 ├── confidence?
 ├── resolution
 └── classifiedAt
```

Capsa should retain enough information to distinguish automatic classification from user-resolved classification.

---

# Future Scope

## v0.1.x — MCP Adapter

An optional MCP adapter may allow external assistants to capture items.

Example:

```text
"Elo, I need soap for the shower. Add it to Capsa."
```

The MCP adapter submits the Capture.

It does not own classification logic.

---

## v0.1.5 — Context Awareness

Capsa may associate Lists with contexts such as physical places.

Example:

```text
Enter supermarket
      ↓
Shopping becomes relevant
      ↓
Pending shopping items are surfaced
```

Geofencing belongs primarily to mobile clients.

The domain model for Context, Place, Trigger, and their relationship to Lists remains intentionally undefined.

---

## v0.2 — Multiple Users and Shared Lists

Multiple Users may collaborate on Lists.

Example:

```text
User A ─┐
        ├── Household Shopping
User B ─┘
```

v0.1 should avoid assumptions that would prevent this evolution, but sharing itself is outside its scope.

---

## Later

Possible capabilities include:

- recurring items;
- reminders;
- calendar integration;
- richer contextual activation;
- notifications;
- classification learning;
- search;
- archival;
- additional capture types.

None are v0.1 requirements.

---

# Technical Direction

These are current architectural intentions rather than domain requirements.

## Backend

Capsa will initially use:

- Java 25;
- Quarkus;
- Jakarta APIs where appropriate;
- PostgreSQL;
- Flyway;
- JPMS;
- Docker.

The backend will be implemented as a modular monolith.

Internal domain and application layers should remain independent of Quarkus where practical. Quarkus acts primarily as the runtime and infrastructure/application adapter environment.

JPMS boundaries should protect meaningful architecture rather than create modules merely for organizational purposes.

---

## Clients

The intended clients are:

```text
Android — Kotlin
iOS     — Swift
Web     — optional
MCP     — optional
```

Clients communicate with a headless Capsa API.

Device-specific capabilities such as speech-to-text and, later, geofencing belong to the corresponding clients.

---

## API

The API contract should be treated as a first-class artifact.

OpenAPI will describe the HTTP API.

Karate is intended for API contract and integration testing.

---

## Testing

The intended testing strategy includes:

```text
Domain
└── unit tests

Application
└── use-case tests

API
└── Karate contract/integration tests

Quarkus
└── integration tests

Persistence
└── PostgreSQL integration tests
```

Testcontainers may be used where appropriate for infrastructure integration testing.

---

## Observability

Application/domain code should depend on a small observability abstraction rather than a specific telemetry implementation.

The initial implementation may emit structured logs.

Sensitive or potentially private Capture/Item content should not be logged by default.

Future implementations may use OpenTelemetry or another observability backend without changing core use cases.

---

## Classification Infrastructure

Classifier providers are infrastructure.

Potential implementations include System One/Jev, embeddings, rules, LLMs, or hybrid pipelines.

External classification providers must eventually be protected by appropriate:

- rate limits;
- concurrency limits;
- timeouts;
- cost/request budgets;
- fallback behavior.

These mechanisms are not part of the v0.1 domain.

---

# Deployment

Deployment is intentionally a late MVP milestone.

The initial target is a small VPS running:

```text
Internet
   ↓
TLS / Reverse Proxy
   ↓
Capsa
   ↓
PostgreSQL
```

A Quarkus native executable/container is the intended deployment artifact.

Kubernetes is explicitly unnecessary for the initial system.

Capsa may be used as a case study for reproducible deployment of a small native Java service without Kubernetes, including:

- TLS;
- secrets;
- database migrations;
- rollback;
- deployment automation;
- minimal downtime;
- observability.

The specific deployment technology remains undecided.

---

# Next Discovery Step

Before defining modules, REST resources, persistence mappings, or implementation tasks, refine each v0.1 use case in terms of:

```text
Actor
Preconditions
Input / Command
Happy Path
Alternate Paths
Postconditions
Invariants
```

The resulting behavior should drive the domain model.

Only after this step should Capsa define its application architecture and implementation plan.