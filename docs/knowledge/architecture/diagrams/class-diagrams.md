# Class Diagrams

**Status:** v0.1 (current implementation)
**Scope:** One class diagram per Maven/JPMS module, showing public API,
internal domain, internal services, persistence entities and repositories,
and (for capability modules) the per-module REST resource.

Diagrams cover **only the types that exist in source today**. Visibility
markers (`+`, `-`) follow Java access: `-` private field, `+` public method.
Internal collaborator types are shown without their own packages to keep each
diagram focused; cross-module references appear with their fully qualified
name (e.g. `users.api.UserId`).

Conventions used in every diagram:

- **Aggregate roots** appear in `internal/domain` (or, for `capture`, as the
  `Capture` record in `internal/domain`).
- **Public service** is the interface in `*.api`; the concrete implementation
  is `@ApplicationScoped` in `internal/service` and named `<Name>ServiceImpl`.
- **REST resources** live in `internal/rest` inside each capability module
  and are discovered via CDI index from `capsa-runtime`.
- **Persistence** types (`*Entity`, `*Repository`) are package-private to
  their module's `internal.persistence.*` packages (not exported).

---

## 1. `capsa-users`

`User` aggregate + OIDC-provisioned identity + request-scoped `CurrentUser`
binding. The leaf of the capability graph — depends on no other capability.

```mermaid
classDiagram
    namespace users.api {
        class UserId {
            <<record>>
            +UUID value
        }
        class UserView {
            <<record>>
            +UserId userId
            +String email
            +String name
        }
        class CurrentUser {
            <<interface>>
            +UserId userId()
        }
        class UserService {
            <<interface>>
            +UserView findOrProvision(String oidcSubject, String email, String name)
            +UserView findById(UserId userId)
        }
        class UserNotFoundException {
            +UserNotFoundException(String)
        }
    }

    namespace users.internal.domain {
        class User {
            -UUID id
            -String oidcSubject
            -String email
            -String name
            -Instant createdAt
            +User create(String oidcSubject, String email, String name)
            +User reconstitute(UUID, String, String, String, Instant)
            +UUID id()
            +String oidcSubject()
            +String email()
            +String name()
            +Instant createdAt()
        }
    }

    namespace users.internal.persistence.entity {
        class UserEntity {
            -UUID id
            -String oidcSubject
            -String email
            +String name
            -Instant createdAt
            +UUID getId()
            +String getOidcSubject()
            +String getEmail()
            +String getName()
            +Instant getCreatedAt()
        }
    }

    namespace users.internal.persistence.repository {
        class UserRepository {
            -EntityManager em
            +Optional~UserEntity~ findByOidcSubject(String)
            +Optional~UserEntity~ findById(UUID)
            +UserEntity save(UserEntity)
        }
    }

    namespace users.internal.service {
        class UserServiceImpl {
            -UserRepository userRepository
            +UserView findOrProvision(String, String, String)
            +UserView findById(UserId)
        }
        class UserConverter {
            <<utility>>
            +UserView toView(User)
            +UserView toView(UserEntity)
            +UserEntity toEntity(User)
        }
    }

    UserService <|.. UserServiceImpl
    UserServiceImpl --> UserRepository
    UserServiceImpl --> UserConverter
    UserConverter --> User
    UserConverter --> UserEntity
    UserService ..> UserNotFoundException : throws
    UserService ..> UserView
    UserService ..> UserId
```

**Notes**

- `User` is immutable; factory `create(...)` enforces invariants
  (`oidcSubject`, `email` non-blank), `reconstitute(...)` is for repository
  rehydration only.
- `UserServiceImpl.findOrProvision` is idempotent on `oidcSubject` — a second
  call for the same subject returns the existing `UserView`.
- `CurrentUser` is provided by `capsa-runtime`'s `OidcCurrentUser` and is
  injected by capability REST resources.

---

## 2. `capsa-lists`

Owns `CapsaList`. Holds the owner check that other capabilities call before
mutating a list's contents (`verifyContributionAccess`).

```mermaid
classDiagram
    namespace lists.api {
        class ListId {
            <<record>>
            +UUID value
        }
        class CreateListCommand {
            <<record>>
            +String name
            +String purpose
        }
        class ListView {
            <<record>>
            +ListId listId
            +String name
            +String purpose
            +UserId ownerId
        }
        class ListService {
            <<interface>>
            +ListView create(UserId ownerId, CreateListCommand)
            +ListView getById(UserId requestingUser, ListId listId)
            +List~ListView~ getByUser(UserId ownerId)
            +void verifyContributionAccess(UserId userId, ListId listId)
        }
        class ListNotFoundException
        class ListAccessDeniedException
    }

    namespace lists.internal.domain {
        class CapsaList {
            -ListId id
            -UserId ownerId
            -String name
            -String purpose
            -Instant createdAt
            +CapsaList create(UserId, String name, String purpose)
            +CapsaList reconstitute(ListId, UserId, String, String, Instant)
            +ListId id()
            +UserId ownerId()
            +String name()
            +String purpose()
            +Instant createdAt()
        }
    }

    namespace lists.internal.persistence.entity {
        class ListEntity {
            -UUID id
            -UUID ownerId
            -String name
            -String purpose
            -Instant createdAt
        }
    }

    namespace lists.internal.persistence.repository {
        class ListRepository {
            -EntityManager em
            +Optional~ListEntity~ findById(UUID)
            +List~ListEntity~ findByOwnerId(UUID)
            +ListEntity save(ListEntity)
        }
    }

    namespace lists.internal.service {
        class ListServiceImpl {
            -ListRepository listRepository
            -Observability observability
            +ListView create(UserId, CreateListCommand)
            +ListView getById(UserId, ListId)
            +List~ListView~ getByUser(UserId)
            +void verifyContributionAccess(UserId, ListId)
        }
        class ListConverter {
            <<utility>>
            +ListView toView(CapsaList)
            +ListView toView(ListEntity)
            +ListEntity toEntity(CapsaList)
        }
    }

    namespace lists.internal.rest {
        class ListResource {
            +Response create(CreateListCommand)
            +ListView getById(UUID id)
        }
    }

    ListService <|.. ListServiceImpl
    ListServiceImpl --> ListRepository
    ListServiceImpl --> ListConverter
    ListServiceImpl --> Observability
    ListConverter --> CapsaList
    ListConverter --> ListEntity
    ListResource --> ListService
    ListResource --> CurrentUser
    ListService ..> ListNotFoundException
    ListService ..> ListAccessDeniedException
```

**Notes**

- `CapsaList` does **not** hold its items in memory; `Item` is a separate
  aggregate queried by `listId + status` via `ItemRepository`.
- `verifyContributionAccess(...)` is the single authorization gate used by
  `capsa-items` and `capsa-capture` before any mutation on a list's contents.
- On successful create, `ListServiceImpl` emits `observability.ListCreated`.

---

## 3. `capsa-items`

Owns `Item`. Handles UC-02 (direct add), UC-04 (item from capture),
UC-05 (list-filtered reads), UC-06 (completion).

```mermaid
classDiagram
    namespace items.api {
        class ItemId {
            <<record>>
            +UUID value
        }
        class CreateItemCommand {
            <<record>>
            +UUID listId
            +String name
            +String notes
        }
        class ItemStatusFilter {
            <<enum>>
            ACTIVE
            HISTORY
            ALL
        }
        class ItemView {
            <<record>>
            +ItemId itemId
            +UUID listId
            +UUID captureId
            +String name
            +String notes
            +String status
            +Instant createdAt
            +Instant completedAt
        }
        class ItemService {
            <<interface>>
            +ItemView create(UserId userId, CreateItemCommand)
            +ItemView createFromCapture(UserId, ListId, UUID captureId, String name, String notes)
            +ItemView complete(UserId userId, ItemId itemId)
            +List~ItemView~ getByList(UserId, ListId, ItemStatusFilter)
        }
        class ItemNotFoundException
        class ItemAccessDeniedException
    }

    namespace items.internal.domain {
        class Item {
            -UUID id
            -UUID listId
            -UUID captureId
            -String name
            -String notes
            -ItemStatus status
            -Instant createdAt
            -Instant completedAt
            +Item create(ListId, String name, String notes)
            +Item createFromCapture(ListId, UUID captureId, String, String notes)
            +void complete()
            +UUID getId()
            +UUID getListId()
            +UUID getCaptureId()
            +String getName()
            +String getNotes()
            +ItemStatus getStatus()
            +Instant getCreatedAt()
            +Instant getCompletedAt()
        }
        class ItemStatus {
            <<enum>>
            PENDING
            DONE
        }
    }

    namespace items.internal.persistence.entity {
        class ItemEntity {
            -UUID id
            -UUID listId
            -UUID captureId
            -String name
            -String notes
            -String status
            -Instant createdAt
            -Instant completedAt
        }
    }

    namespace items.internal.persistence.repository {
        class ItemRepository {
            -EntityManager em
            +Optional~ItemEntity~ findById(UUID)
            +List~ItemEntity~ findByListId(UUID)
            +List~ItemEntity~ findByListIdAndStatus(UUID, String)
            +void save(ItemEntity)
        }
    }

    namespace items.internal.service {
        class ItemServiceImpl {
            -ItemRepository itemRepository
            -ListService listService
            -Observability observability
            +ItemView create(UserId, CreateItemCommand)
            +ItemView createFromCapture(UserId, ListId, UUID, String, String)
            +ItemView complete(UserId, ItemId)
            +List~ItemView~ getByList(UserId, ListId, ItemStatusFilter)
        }
        class ItemConverter {
            <<utility>>
            +ItemEntity toEntity(Item)
            +ItemView toView(ItemEntity)
        }
    }

    namespace items.internal.rest {
        class ItemResource {
            +Response create(CreateItemCommand)
            +Response getByList(UUID listId, String statusParam)
            +ItemView complete(UUID id)
        }
    }

    ItemService <|.. ItemServiceImpl
    ItemServiceImpl --> ItemRepository
    ItemServiceImpl --> ItemConverter
    ItemServiceImpl --> ListService
    ItemServiceImpl --> Observability
    ItemConverter --> Item
    ItemConverter --> ItemEntity
    ItemResource --> ItemService
    ItemResource --> CurrentUser
    Item ..> ItemStatus
    ItemService ..> ItemNotFoundException
    ItemService ..> ItemAccessDeniedException
```

**Notes**

- `Item.complete()` is idempotent: a second call on an already-`DONE` item is
  a no-op; no second `ItemCompleted` observability event is emitted.
- `createFromCapture` is invoked by `capsa-capture` after either an automatic
  classification (UC-03) or a user resolution (UC-04). It records `captureId`
  on the item for traceability.
- `getByList` enforces ownership via `ListService.verifyContributionAccess`
  before the repository query runs.

---

## 4. `capsa-capture`

The orchestration module for the smart-capture path (UC-03 / UC-04). Splits
into three internal services: `CaptureCreationService` persists the raw
capture and emits `CaptureSubmitted`; `CaptureServiceImpl` (the public API)
calls the `Classifier` and dispatches on outcome; `CaptureResolutionService`
mutates the capture and creates the downstream `Item`.

```mermaid
classDiagram
    namespace capture.api {
        class CaptureId {
            <<record>>
            +UUID value
        }
        class SubmitCaptureCommand {
            <<record>>
            +String content
        }
        class ClassificationCandidate {
            <<record>>
            +UUID listId
            +double confidence
            +String explanation
        }
        class CaptureResult {
            <<sealed interface>>
        }
        class CaptureResult_Classified {
            <<record>>
            +ItemView item
        }
        class CaptureResult_NeedsResolution {
            <<record>>
            +CaptureId captureId
            +List~ClassificationCandidate~ candidates
        }
        class CaptureService {
            <<interface>>
            +CaptureResult submit(UserId, String content)
            +ItemView resolve(UserId, CaptureId, ListId)
        }
        class CaptureNotFoundException
        class CaptureNotAwaitingResolutionException
    }

    namespace capture.internal.domain {
        class Capture {
            <<record>>
            +UUID captureId
            +String originalContent
            +String normalizedContent
        }
        class CaptureNormalizer {
            <<utility>>
            +String normalize(String)
        }
    }

    namespace capture.internal.persistence.entity {
        class CaptureEntity {
            +UUID id
            +UUID userId
            +String originalContent
            +String normalizedContent
            +String processingStatus
            +Instant capturedAt
        }
        class ClassificationAttemptEntity {
            +UUID id
            +UUID captureId
            +String outcome
            +String candidates
            +Instant attemptedAt
        }
        class ClassificationResolutionEntity {
            +UUID id
            +UUID captureId
            +UUID selectedListId
            +String resolvedBy
            +Instant resolvedAt
        }
    }

    namespace capture.internal.persistence.repository {
        class CaptureRepository {
            -EntityManager em
            +void persist(CaptureEntity)
            +Optional~CaptureEntity~ findById(UUID)
        }
        class ClassificationAttemptRepository {
            -EntityManager em
            +void persist(ClassificationAttemptEntity)
            +Optional~ClassificationAttemptEntity~ findByCaptureId(UUID)
        }
        class ClassificationResolutionRepository {
            -EntityManager em
            +void persist(ClassificationResolutionEntity)
        }
    }

    namespace capture.internal.service {
        class CaptureServiceImpl {
            -CaptureCreationService captureCreationService
            -CaptureResolutionService captureResolutionService
            -Classifier classifier
            +CaptureResult submit(UserId, String)
            +ItemView resolve(UserId, CaptureId, ListId)
        }
        class CaptureCreationService {
            -CaptureRepository captureRepository
            -ClassificationAttemptRepository attemptRepository
            -Observability observability
            +Capture createCapture(UserId, String originalContent, String normalizedContent)
        }
        class CaptureResolutionService {
            -CaptureRepository captureRepository
            -ClassificationAttemptRepository attemptRepository
            -ClassificationResolutionRepository resolutionRepository
            -ItemService itemService
            -ClassificationService classificationService
            -Observability observability
            +ItemView resolveCapture(UserId, Capture, ClassificationResult)
            +void storeNeedsResolution(Capture, ClassificationResult)
            +void markFailed(UUID captureId)
            +ItemView resolveFromUser(UserId, CaptureId, ListId)
        }
    }

    namespace capture.internal.rest {
        class CaptureResource {
            +Response submit(SubmitCaptureCommand)
            +Response resolve(UUID id, ResolveRequest)
        }
        class ResolveRequest {
            <<record>>
            +UUID listId
        }
    }

    CaptureService <|.. CaptureServiceImpl
    CaptureServiceImpl --> CaptureCreationService
    CaptureServiceImpl --> CaptureResolutionService
    CaptureServiceImpl --> Classifier
    CaptureCreationService --> CaptureRepository
    CaptureCreationService --> ClassificationAttemptRepository
    CaptureCreationService --> CaptureNormalizer
    CaptureResolutionService --> CaptureRepository
    CaptureResolutionService --> ClassificationAttemptRepository
    CaptureResolutionService --> ClassificationResolutionRepository
    CaptureResolutionService --> ItemService
    CaptureResolutionService --> ClassificationService
    CaptureServiceImpl ..> CaptureNormalizer : normalize()
    CaptureResult <|-- CaptureResult_Classified
    CaptureResult <|-- CaptureResult_NeedsResolution
    CaptureResource --> CaptureService
    CaptureResource --> CurrentUser
    CaptureService ..> CaptureNotFoundException
    CaptureService ..> CaptureNotAwaitingResolutionException
```

**Notes**

- `Capture` is a Java `record` holding `originalContent` (preserved exactly)
  and `normalizedContent` (the result of `CaptureNormalizer.normalize` —
  `strip`, lowercase, collapse whitespace). Both forms are stored so that
  historical classification behavior is anchored to the normalization rules
  in effect at capture time.
- `CaptureEntity.processingStatus` transitions: `PROCESSING → CLASSIFIED |
  NEEDS_RESOLUTION → RESOLVED` (or `FAILED` on pipeline exception).
- `CaptureResolutionEntity` has `unique (capture_id)` — a Capture resolves at
  most once (UC-04 idempotency invariant).
- Pipeline exceptions are not represented in `CaptureResult`; they propagate
  out and the capture is marked `FAILED` before the throw.

---

## 5. `capsa-classification`

Owns the `Classifier` capability abstraction and the `KnownClassificationStrategy`
(the only strategy currently wired into `ClassificationPipeline`). `Classifier`
is the only classification type the rest of the system depends on; concrete
strategies and the pipeline are internal.

```mermaid
classDiagram
    namespace classification.api {
        class ClassificationTarget {
            <<record>>
            +UUID listId
            +String name
            +String explicitPurpose
        }
        class ClassificationRequest {
            <<record>>
            +UserId userId
            +String normalizedContent
            +List~ClassificationTarget~ candidates
        }
        class ClassificationCandidate {
            <<record>>
            +UUID listId
            +double confidence
            +String explanation
        }
        class ClassificationResult {
            <<record>>
            +Outcome outcome
            +List~ClassificationCandidate~ candidates
            +UUID selectedListId
            +Map~String,String~ metadata
        }
        class Outcome {
            <<enum>>
            CLASSIFIED
            NEEDS_RESOLUTION
        }
        class Classifier {
            <<interface>>
            +ClassificationResult classify(ClassificationRequest)
        }
        class ClassificationService {
            <<interface>>
            +void recordResolution(UserId, String normalizedContent, UUID selectedListId)
        }
    }

    namespace classification.internal.domain {
        class ClassificationMemoryEntry {
            -UUID id
            -UserId userId
            -String normalizedContent
            -UUID selectedListId
            -Source source
            -Instant recordedAt
            +ClassificationMemoryEntry create(UserId, String, UUID, Source)
            +ClassificationMemoryEntry reconstitute(UUID, UserId, String, UUID, Source, Instant)
        }
        class Source {
            <<enum>>
            AUTO
            USER_CONFIRMED
        }
    }

    namespace classification.internal.persistence.entity {
        class ClassificationMemoryEntryEntity {
            -UUID id
            -UUID userId
            -String normalizedContent
            -UUID selectedListId
            -String source
            -Instant recordedAt
        }
    }

    namespace classification.internal.persistence.repository {
        class ClassificationMemoryEntryRepository {
            -EntityManager em
            +Optional~ClassificationMemoryEntryEntity~ findLatestUserConfirmed(UserId, String)
            +void save(ClassificationMemoryEntryEntity)
        }
    }

    namespace classification.internal.service {
        class ClassificationPipeline {
            <<implements Classifier>>
            -List~ClassificationStrategy~ strategies
            +ClassificationResult classify(ClassificationRequest)
        }
        class ClassificationStrategy {
            <<interface, package-private>>
            +ClassificationResult classify(ClassificationRequest)
        }
        class KnownClassificationStrategy {
            -ClassificationMemoryEntryRepository repository
            +ClassificationResult classify(ClassificationRequest)
        }
        class ClassificationServiceImpl {
            -ClassificationMemoryEntryRepository repository
            -Observability observability
            +void recordResolution(UserId, String, UUID)
        }
    }

    Classifier <|.. ClassificationPipeline
    ClassificationPipeline o-- ClassificationStrategy
    ClassificationStrategy <|.. KnownClassificationStrategy
    KnownClassificationStrategy --> ClassificationMemoryEntryRepository
    ClassificationService <|.. ClassificationServiceImpl
    ClassificationServiceImpl --> ClassificationMemoryEntryRepository
    ClassificationServiceImpl --> Observability
    ClassificationResult ..> Outcome
    ClassificationMemoryEntry ..> Source
```

**Notes**

- `Classifier.classify` is the only contract `capsa-capture` depends on.
- The pipeline executes strategies in order; the first strategy that returns
  `Outcome.CLASSIFIED` terminates the pipeline. If all return `NEEDS_RESOLUTION`,
  the pipeline returns the last result.
- `ClassificationMemoryEntry` is append-only. The `KnownClassificationStrategy`
  queries by `(userId, normalizedContent, source = USER_CONFIRMED)` and returns
  the most recent `USER_CONFIRMED` entry.
- `recordResolution` always records with `Source.USER_CONFIRMED`; the capture
  module calls it only after a user resolution (UC-04). Auto-classified
  outcomes are persisted as a `ClassificationResolutionEntity` in
  `capsa-capture`, not as a memory entry.

---

## 6. `capsa-observability`

Capability-facing port for emitting structured events. The implementation
dispatches asynchronously on a virtual-thread executor and serializes via
JSON-B; a `SynchronousSinkExecutor` exists for synchronous test observation.

```mermaid
classDiagram
    namespace observability.api {
        class Observability {
            <<interface>>
            +void emit(ObservabilityEvent event)
        }
        class ObservabilityEvent {
            <<sealed interface>>
            +UUID eventId()
            +Instant occurredAt()
            +EventActor actor()
        }
        class EventActor {
            <<sealed interface>>
        }
        class EventActor_User {
            <<record>>
            +UUID userId
        }
    }

    namespace observability.api.events {
        class ListCreated {
            <<record>>
            +UUID eventId
            +Instant occurredAt
            +EventActor actor
            +UUID listId
            +String name
        }
        class ItemCreated {
            <<record>>
            +UUID eventId
            +Instant occurredAt
            +EventActor actor
            +UUID itemId
            +UUID listId
            +UUID captureId
        }
        class ItemCompleted {
            <<record>>
            +UUID eventId
            +Instant occurredAt
            +EventActor actor
            +UUID itemId
        }
        class CaptureSubmitted {
            <<record>>
            +UUID eventId
            +Instant occurredAt
            +EventActor actor
            +UUID captureId
        }
        class CaptureClassified {
            <<record>>
            +UUID eventId
            +Instant occurredAt
            +EventActor actor
            +UUID captureId
            +UUID selectedListId
        }
        class CaptureNeedsResolution {
            <<record>>
            +UUID eventId
            +Instant occurredAt
            +EventActor actor
            +UUID captureId
        }
        class CaptureResolved {
            <<record>>
            +UUID eventId
            +Instant occurredAt
            +EventActor actor
            +UUID captureId
            +UUID selectedListId
        }
        class ClassificationRecorded {
            <<record>>
            +UUID eventId
            +Instant occurredAt
            +EventActor actor
            +UUID listId
        }
    }

    namespace observability.internal {
        class Slf4jObservability {
            <<implements Observability>>
            -Logger errorLog
            -ExecutorService dispatcher
            -Consumer~String~ sinkWriter
            -Jsonb jsonb
            +void emit(ObservabilityEvent)
        }
        class SynchronousSinkExecutor {
            <<ExecutorService, test-only>>
            +void execute(Runnable)
        }
    }

    Observability <|.. Slf4jObservability
    ObservabilityEvent <|-- ListCreated
    ObservabilityEvent <|-- ItemCreated
    ObservabilityEvent <|-- ItemCompleted
    ObservabilityEvent <|-- CaptureSubmitted
    ObservabilityEvent <|-- CaptureClassified
    ObservabilityEvent <|-- CaptureNeedsResolution
    ObservabilityEvent <|-- CaptureResolved
    ObservabilityEvent <|-- ClassificationRecorded
    EventActor <|-- EventActor_User
```

**Notes**

- The port is best-effort and asynchronous. `emit(...)` returns to the calling
  business thread immediately and never throws. Sink failures are logged via the
  standard application logger (`Slf4jObservability.errorLog`).
- JSON-B serialization uses a discriminator property `type` for
  `ObservabilityEvent` variants and `kind` for `EventActor` variants.
- `capsa-observability` depends only on `capsa-users` for `UserId` (used
  inside `EventActor.User`).

---

## 7. `capsa-runtime`

Composition root. Wires every capability, provides the OIDC-backed
`CurrentUser` implementation, owns the exception mappers, and integrates
Flyway/Hibernate/Postgres. No `api` package.

```mermaid
classDiagram
    namespace runtime.security {
        class OidcCurrentUser {
            <<implements users.api.CurrentUser>>
            -Principal principal
            -UserService userService
            -UserId resolvedId
            +UserId userId()
        }
    }

    namespace runtime.error {
        class ErrorResponse {
            <<record>>
            +String code
            +String message
        }
        class FallbackExceptionMapper
        class UserNotFoundExceptionMapper
        class ListNotFoundExceptionMapper
        class ListAccessDeniedExceptionMapper
        class ItemNotFoundExceptionMapper
        class ItemAccessDeniedExceptionMapper
        class CaptureNotFoundExceptionMapper
        class CaptureNotAwaitingResolutionExceptionMapper
    }

    OidcCurrentUser --> UserService : findOrProvision
    FallbackExceptionMapper --> ErrorResponse
    UserNotFoundExceptionMapper --> ErrorResponse
    ListNotFoundExceptionMapper --> ErrorResponse
    ListAccessDeniedExceptionMapper --> ErrorResponse
    ItemNotFoundExceptionMapper --> ErrorResponse
    ItemAccessDeniedExceptionMapper --> ErrorResponse
    CaptureNotFoundExceptionMapper --> ErrorResponse
    CaptureNotAwaitingResolutionExceptionMapper --> ErrorResponse
```

**Notes**

- `OidcCurrentUser` is `@RequestScoped` and `lazy`-resolves the
  `UserId` by calling `UserService.findOrProvision` once per request, reading
  `sub`, `email`, `name` from the `JsonWebToken`.
- One dedicated exception mapper per public exception type (not-found and
  access-denied for `User`, `List`, `Item`, `Capture`). A
  `FallbackExceptionMapper` handles anything that escapes the capability
  services.
- `application.properties` declares `quarkus.index-dependency.*` for every
  capability JAR so Quarkus discovers their CDI beans and entities without
  module-path scanning.

---

## Cross-module type usage

The following non-trivial cross-module references exist today (verified from
source). Anything not listed is contained within a single module.

| Type                              | Used by                                              |
|-----------------------------------|------------------------------------------------------|
| `users.api.UserId`                | `lists.api.ListService`, `items.api.ItemService`, `capture.api.CaptureService`, `classification.api.ClassificationRequest`, `observability.api.EventActor.User` |
| `users.api.CurrentUser`           | `lists.internal.rest.ListResource`, `items.internal.rest.ItemResource`, `capture.internal.rest.CaptureResource`, `runtime.security.OidcCurrentUser` (implements) |
| `users.api.UserService`           | `runtime.security.OidcCurrentUser`                   |
| `users.api.UserView`               | `users.internal.service.UserServiceImpl` (returns)   |
| `lists.api.ListService`           | `items.internal.service.ItemServiceImpl`, `capture.internal.service.CaptureResolutionService` |
| `lists.api.ListId`                | `items.internal.domain.Item` (in `create` / `createFromCapture`), `capture.api.CaptureService.resolve` |
| `items.api.ItemService`           | `capture.internal.service.CaptureResolutionService`  |
| `items.api.ItemView`              | `capture.api.CaptureResult.Classified` (returned)    |
| `classification.api.Classifier`   | `capture.internal.service.CaptureServiceImpl`        |
| `classification.api.ClassificationService` | `capture.internal.service.CaptureResolutionService` |
| `observability.api.Observability` | `lists.internal.service.ListServiceImpl`, `items.internal.service.ItemServiceImpl`, `capture.internal.service.*`, `runtime` (wires `Slf4jObservability`) |