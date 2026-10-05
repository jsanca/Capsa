# Sequence Diagrams — Business Flows

**Status:** v0.1 (current implementation)
**Scope:** End-to-end flows for UC-01 through UC-06, plus the cross-cutting
authentication step that precedes every authenticated request. Each diagram
shows the public API contracts and the major internal collaborators
sufficient to understand the flow; persistence is collapsed into the
repository step.

Diagrams reflect the **actual** request path in the code today. The contracts
shown are the public services in each module's `api` package, not REST
resources (which sit in `internal.rest` and are the only HTTP entry point).

Use case sources:

- [UC-01 Create List](../../use-cases/uc-01-create-list.md)
- [UC-02 Add Item to List](../../use-cases/uc-02-add-item-to-list.md)
- [UC-03 Automatically Classify Capture](../../use-cases/uc-03-automatically-classify-capture.md)
- [UC-04 Resolve Ambiguous Classification](../../use-cases/uc-04-resolve-ambiguous-classification.md)
- [UC-05 View List](../../use-cases/uc-05-view-list.md)
- [UC-06 Complete Item](../../use-cases/uc-06-complete-item.md)

---

## 0. Authenticated request — `CurrentUser` resolution

Every authenticated request resolves the `CurrentUser` once. The `OidcCurrentUser`
bean is `@RequestScoped`; `userId()` is lazy.

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Client
    participant Resource as Capability<br/>REST Resource
    participant CU as OidcCurrentUser<br/>(capsa-runtime)
    participant US as UserService<br/>(capsa-users)
    participant DB as users table

    Client->>Resource: HTTP request (Bearer JWT)
    Resource->>CU: currentUser.userId()
    alt first access in this request
        CU->>US: findOrProvision(sub, email, name)
        US->>DB: SELECT user WHERE oidc_subject = ?
        alt found
            DB-->>US: UserEntity
        else not found
            US->>DB: INSERT user
            DB-->>US: UserEntity
        end
        US-->>CU: UserView(userId, email, name)
    end
    CU-->>Resource: UserId
```

`UserService.findOrProvision` is idempotent on `oidcSubject`. The
`UserView` value is cached on the bean for subsequent `currentUser.userId()`
calls in the same request.

---

## 1. UC-01 — Create List

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Client
    participant LR as ListResource<br/>(capsa-lists)
    participant CU as OidcCurrentUser
    participant LS as ListService<br/>(capsa-lists)
    participant Domain as CapsaList.create(...)
    participant Conv as ListConverter
    participant Repo as ListRepository
    participant Obs as Observability
    participant DB as lists table

    Client->>LR: POST /capsa/api/lists {name, purpose?}
    LR->>LR: validate name non-blank
    LR->>CU: currentUser.userId()
    CU-->>LR: UserId
    LR->>LS: create(userId, CreateListCommand)
    LS->>Domain: CapsaList.create(userId, name, purpose)
    Domain-->>LS: CapsaList (id=new)
    LS->>Conv: toEntity(list)
    Conv-->>LS: ListEntity
    LS->>Repo: save(entity)
    Repo->>DB: INSERT lists
    LS->>Obs: emit(ListCreated event)
    LS-->>LR: ListView
    LR-->>Client: 201 Created + ListView
```

Failure paths (not shown): blank name → 422 validation; OIDC disabled in
`%dev`/`%test` so the test profile uses `@TestSecurity` instead of a JWT.

---

## 2. UC-02 — Add Item to List (direct)

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Client
    participant IR as ItemResource<br/>(capsa-items)
    participant CU as OidcCurrentUser
    participant IS as ItemService<br/>(capsa-items)
    participant LS as ListService<br/>(capsa-lists)
    participant Item as Item.create(...)
    participant Conv as ItemConverter
    participant Repo as ItemRepository
    participant Obs as Observability
    participant DB as items table

    Client->>IR: POST /capsa/api/items {listId, name, notes?}
    IR->>IR: validate listId, name non-blank
    IR->>CU: currentUser.userId()
    CU-->>IR: UserId
    IR->>IS: create(userId, CreateItemCommand)
    IS->>LS: verifyContributionAccess(userId, listId)
    LS->>Repo: findById(listId)
    Repo-->>LS: ListEntity
    LS-->>IS: ok (or throws ListAccessDeniedException / ListNotFoundException)
    IS->>Item: Item.create(listId, name, notes)
    Item-->>IS: Item (id=new, captureId=null)
    IS->>Conv: toEntity(item)
    Conv-->>IS: ItemEntity
    IS->>Repo: save(entity)
    Repo->>DB: INSERT items
    IS->>Obs: emit(ItemCreated event, captureId=null)
    IS-->>IR: ItemView
    IR-->>Client: 201 Created + ItemView
```

---

## 3. UC-03 — Submit Capture (happy path: auto-classified)

This is the "smart capture" path. The `Classifier` is the
`capsa-classification` public interface; the `KnownClassificationStrategy` is the
only strategy currently registered in `ClassificationPipeline`.

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Client
    participant CR as CaptureResource<br/>(capsa-capture)
    participant CU as OidcCurrentUser
    participant CS as CaptureService<br/>(capsa-capture)
    participant NORM as CaptureNormalizer
    participant CRE as CaptureCreationService
    participant Repo as CaptureRepository<br/>+ ClassificationAttemptRepository
    participant CL as Classifier<br/>(ClassificationPipeline)
    participant KN as KnownClassificationStrategy
    participant Mem as ClassificationMemoryEntryRepository
    participant CRS as CaptureResolutionService
    participant IS as ItemService<br/>(capsa-items)
    participant LS as ListService
    participant Obs as Observability
    participant DB as captures / items

    Client->>CR: POST /capsa/api/captures {content}
    CR->>CU: currentUser.userId()
    CU-->>CR: UserId
    CR->>CS: submit(userId, content)
    CS->>NORM: normalize(content)
    NORM-->>CS: normalizedContent
    CS->>CRE: createCapture(userId, original, normalized)
    CRE->>Repo: persist(CaptureEntity{status=PROCESSING})
    CRE->>Repo: persist(ClassificationAttemptEntity)
    CRE->>Obs: emit(CaptureSubmitted)
    CRE-->>CS: Capture(captureId, originalContent, normalizedContent)
    CS->>CL: classify(ClassificationRequest)
    CL->>KN: classify(request)
    KN->>Mem: findLatestUserConfirmed(userId, normalizedContent)
    Mem-->>KN: entry hit (previous user-confirmed)
    KN-->>CL: ClassificationResult(CLASSIFIED, selectedListId)
    CL-->>CS: ClassificationResult(CLASSIFIED)
    CS->>CRS: resolveCapture(userId, capture, result)
    CRS->>IS: createFromCapture(userId, listId, captureId, name, notes)
    IS->>LS: verifyContributionAccess(userId, listId)
    IS->>DB: INSERT items
    IS-->>CRS: ItemView
    CRS->>Repo: update attempt outcome=CLASSIFIED
    CRS->>Repo: persist(ClassificationResolutionEntity{resolvedBy=AUTO})
    CRS->>DB: UPDATE captures SET processing_status=CLASSIFIED
    CRS->>Obs: emit(CaptureClassified)
    CRS-->>CS: ItemView
    CS-->>CR: CaptureResult.Classified(item)
    CR-->>Client: 201 Created + ItemView
```

Notes:

- `Item.createFromCapture` records `captureId` on the item, making the
  Capture ↔ Item traceability permanent.
- A `ClassificationResolutionEntity` with `resolvedBy=AUTO` is persisted.
  `ClassificationMemoryEntry` is **not** written for auto-classified captures
  (the memory entry is written only on user-confirmed resolution, UC-04).
- Pipeline exceptions are not represented in `CaptureResult`. The
  `CaptureServiceImpl.submit` catch block calls `CaptureResolutionService.markFailed`
  and rethrows.

---

## 4. UC-03 — Submit Capture (uncertain → `NEEDS_RESOLUTION`)

Same shape as the happy path, but the `Classifier` returns
`Outcome.NEEDS_RESOLUTION`. No `Item` is created.

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Client
    participant CR as CaptureResource
    participant CU as OidcCurrentUser
    participant CS as CaptureService
    participant NORM as CaptureNormalizer
    participant CRE as CaptureCreationService
    participant Repo as Capture + Attempt Repository
    participant CL as Classifier
    participant KN as KnownClassificationStrategy
    participant Mem as Memory Repository
    participant CRS as CaptureResolutionService
    participant Obs as Observability
    participant DB as captures

    Client->>CR: POST /capsa/api/captures {content}
    CR->>CS: submit(userId, content)
    CS->>NORM: normalize(content)
    CS->>CRE: createCapture(userId, original, normalized)
    CRE->>Repo: persist CaptureEntity{status=PROCESSING} + AttemptEntity
    CRE->>Obs: emit(CaptureSubmitted)
    CS->>CL: classify(request)
    CL->>KN: classify(request)
    KN->>Mem: findLatestUserConfirmed(userId, normalizedContent)
    Mem-->>KN: miss
    KN-->>CL: ClassificationResult(NEEDS_RESOLUTION)
    CL-->>CS: ClassificationResult(NEEDS_RESOLUTION)
    CS->>CRS: storeNeedsResolution(capture, result)
    CRS->>Repo: update attempt outcome=NEEDS_RESOLUTION, candidates=...
    CRS->>DB: UPDATE captures SET processing_status=NEEDS_RESOLUTION
    CRS->>Obs: emit(CaptureNeedsResolution)
    CRS-->>CS: ok
    CS-->>CR: CaptureResult.NeedsResolution(captureId, candidates)
    CR-->>Client: 200 OK + {captureId, status, candidates[]}
```

The `candidates` list is empty for the current `KnownClassificationStrategy`;
it is the contract seam where future strategies will provide ranked
suggestions. The client is not required to pick from `candidates` — UC-04
allows the user to select any list they own.

---

## 5. UC-04 — Resolve ambiguous classification

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Client
    participant CR as CaptureResource<br/>(capsa-capture)
    participant CU as OidcCurrentUser
    participant CS as CaptureService
    participant CRS as CaptureResolutionService
    participant Repo as Capture + Resolution Repository
    participant IS as ItemService<br/>(capsa-items)
    participant LS as ListService<br/>(capsa-lists)
    participant CL as ClassificationService<br/>(capsa-classification)
    participant Mem as ClassificationMemoryEntryRepository
    participant Obs as Observability
    participant DB as captures / items / memory

    Client->>CR: POST /capsa/api/captures/{captureId}/resolution {listId}
    CR->>CU: currentUser.userId()
    CR->>CS: resolve(userId, captureId, listId)
    CS->>CRS: resolveFromUser(userId, captureId, listId)
    CRS->>Repo: findById(captureId)
    Repo-->>CRS: CaptureEntity
    alt capture not found
        CRS-->>CR: CaptureNotFoundException → 404
    end
    alt not owner
        CRS-->>CR: CaptureNotFoundException (no info leak) → 404
    end
    alt status != NEEDS_RESOLUTION
        CRS-->>CR: CaptureNotAwaitingResolutionException → 422
    end
    CRS->>IS: createFromCapture(userId, listId, captureId, name, notes)
    IS->>LS: verifyContributionAccess(userId, listId)
    IS->>DB: INSERT items
    IS-->>CRS: ItemView
    CRS->>Repo: persist(ClassificationResolutionEntity{resolvedBy=USER})
    CRS->>CL: recordResolution(userId, normalizedContent, listId)
    CL->>Mem: save(ClassificationMemoryEntry{Source=USER_CONFIRMED})
    Mem->>DB: INSERT classification_memory_entries
    CL->>Obs: emit(ClassificationRecorded)
    CRS->>DB: UPDATE captures SET processing_status=RESOLVED
    CRS->>Obs: emit(CaptureResolved)
    CRS-->>CS: ItemView
    CS-->>CR: ItemView
    CR-->>Client: 201 Created + ItemView
```

The `ClassificationResolutionEntity` has a `UNIQUE(capture_id)` constraint;
a concurrent second resolve for the same capture fails with a constraint
violation rather than producing a duplicate `Item`. The
`ClassificationMemoryEntry` row is what makes the next identical capture
classifiable by the `KnownClassificationStrategy`.

---

## 6. UC-05 — View List

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Client
    participant IR as ItemResource<br/>(capsa-items)
    participant CU as OidcCurrentUser
    participant IS as ItemService
    participant LS as ListService<br/>(capsa-lists)
    participant LR as ListRepository
    participant Repo as ItemRepository
    participant DB as lists / items

    Client->>IR: GET /capsa/api/items?listId={listId}&status=active
    IR->>CU: currentUser.userId()
    IR->>IS: getByList(userId, listId, ACTIVE)
    IS->>LS: verifyContributionAccess(userId, listId)
    LS->>LR: findById(listId)
    LR->>DB: SELECT lists WHERE id=?
    alt not owner
        LS-->>IS: ListAccessDeniedException → 403
    end
    IS->>Repo: findByListIdAndStatus(listId, "PENDING")
    Repo->>DB: SELECT items WHERE list_id=? AND status='PENDING' ORDER BY created_at ASC
    Repo-->>IS: List<ItemEntity>
    IS-->>IR: List<ItemView>
    IR-->>Client: 200 OK + ItemView[]
```

`status` query parameter values: `active` (default → `PENDING`), `history`
(→ `DONE`), `all` (no status filter). `HISTORY` returns `DONE` items still
associated with the original list (no archive list exists — see UC-02/UC-06
notes in the use case documents).

---

## 7. UC-06 — Complete Item

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Client
    participant IR as ItemResource<br/>(capsa-items)
    participant CU as OidcCurrentUser
    participant IS as ItemService
    participant LS as ListService
    participant Repo as ItemRepository
    participant Obs as Observability
    participant DB as items

    Client->>IR: PUT /capsa/api/items/{itemId}/completion
    IR->>CU: currentUser.userId()
    IR->>IS: complete(userId, itemId)
    IS->>Repo: findById(itemId)
    Repo-->>IS: ItemEntity
    alt not found
        IS-->>IR: ItemNotFoundException → 404
    end
    IS->>LS: verifyContributionAccess(userId, listId)
    alt not owner
        LS-->>IS: ListAccessDeniedException → 403
    end
    alt status == DONE
        IS-->>IR: ItemView (idempotent, no event)
    end
    IS->>DB: UPDATE items SET status='DONE', completed_at=NOW() WHERE id=?
    IS->>Obs: emit(ItemCompleted)
    IS-->>IR: ItemView
    IR-->>Client: 200 OK + ItemView
```

`complete` is idempotent: a second `PUT` against an already-`DONE` item
returns the same `ItemView` without emitting a second `ItemCompleted` event.
The item is never moved to a different list and never deleted; `list_id` is
immutable from creation.

---

## 8. Cross-flow — Observability emission

Observability events are emitted in the service layer **after** the
persistence operation succeeds but **before** the service method returns.
The `Observability.emit(...)` call returns immediately (virtual-thread
dispatch); a sink failure is logged to the standard application logger and
never propagated to the caller. This makes observability best-effort and
out of any synchronous business transaction.

```mermaid
sequenceDiagram
    autonumber
    participant Svc as Capability<br/>Service
    participant Repo as Repository
    participant DB
    participant Obs as Observability<br/>(port)
    participant Impl as Slf4jObservability
    participant Exec as Virtual-Thread<br/>Executor
    participant Sink as SLF4J logger<br/>"capsa.observability"
    participant AppLog as Standard application<br/>logger

    Svc->>Repo: persist(entity)
    Repo->>DB: SQL
    DB-->>Repo: ok
    Repo-->>Svc: entity
    Svc->>Obs: emit(event)
    Obs->>Impl: emit(event)
    Impl->>Impl: jsonb.toJson(event) + newline
    Impl->>Exec: execute(sinkWriter)
    Impl-->>Svc: void (returns immediately)
    Exec->>Sink: info(serialized line)
    alt sink throws
    Impl->>AppLog: error("Observability sink failed", e)
    end
```

Capability services do not couple to a specific sink. The default sink is the
dedicated SLF4J logger `capsa.observability` (logger name constant
`Slf4jObservability.LOGGER_NAME`); future adapters (Kafka, OpenTelemetry,
durable outbox) bind a different `Observability` bean at composition time in
`capsa-runtime` without any change to capability code.

---

## 9. Failure mode — Pipeline exception during UC-03

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Client
    participant CR as CaptureResource
    participant CS as CaptureService
    participant CRE as CaptureCreationService
    participant DB as captures
    participant CL as Classifier

    Client->>CR: POST /capsa/api/captures {content}
    CR->>CS: submit(userId, content)
    CS->>CRE: createCapture(...)
    CRE->>DB: INSERT captures (status=PROCESSING) + attempt
    CS->>CL: classify(request)
    CL--xCS: throws (provider timeout, etc.)
    CS->>DB: UPDATE captures SET status=FAILED
    CS--xCR: rethrows
    CR-->>Client: 5xx (mapped by FallbackExceptionMapper)
```

The capture is left in `FAILED` so the user can be notified or retry;
no `Item` is created. `ClassificationRecovery` distinguishes this from
`NEEDS_RESOLUTION` (uncertainty vs. execution failure).