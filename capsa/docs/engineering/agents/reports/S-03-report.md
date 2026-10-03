# S-03 Engineering Report — Items Capability + UC-02 + UC-05 + UC-06

**Status:** DONE  
**Date:** 2026-10-03  
**Depends on:** S-02

---

## Goal

Introduce the `capsa-items` capability and its REST surface. After this slice, four of six use cases are fully working: UC-01, UC-02, UC-05, UC-06.

---

## Scope Delivered

### `capsa.items.api` (new public exports)

| Type | Role |
|---|---|
| `ItemId` | Strongly typed item identifier |
| `ItemView` | Read projection: itemId, listId, captureId, name, notes, status, createdAt, completedAt |
| `CreateItemCommand` | REST input DTO: listId (UUID), name, notes |
| `ItemStatusFilter` | Enum: ACTIVE, HISTORY, ALL |
| `ItemService` | `create`, `createFromCapture`, `complete`, `getByList` |
| `ItemNotFoundException` | Per-capability exception (mapper in S-06) |
| `ItemAccessDeniedException` | Per-capability exception (mapper in S-06) |

### `capsa.items.internal`

| Component | Responsibility |
|---|---|
| `Item` domain object | Lifecycle (PENDING → DONE), `create`, `createFromCapture`, `complete` (idempotent) |
| `ItemEntity` | `@Entity @Table("items")` |
| `ItemRepository` | `findById`, `findByListId`, `findByListIdAndStatus`, `save` |
| `ItemConverter` | Domain ↔ entity ↔ view mapping |
| `ItemServiceImpl` | Orchestrates: validates name, calls `listService.verifyContributionAccess()`, persists |
| `ItemResource` | REST endpoints (see below) |

### REST endpoints

| Method | Path | UC | Behavior |
|---|---|---|---|
| `POST` | `/capsa/api/items` | UC-02 | Create PENDING Item; 400 on blank name |
| `GET` | `/capsa/api/items?listId=&status=` | UC-05 | Query by list; `status=active` (default) → PENDING only; `status=history` → DONE only; `status=all` → all |
| `POST` | `/capsa/api/items/{id}/completion` | UC-06 | PENDING → DONE (idempotent: already-DONE returns 200 with current state) |

### `capsa-runtime`

- Flyway `V003__items_initial.sql` — `items` table with `uq_items_capture_id` UNIQUE constraint; indexes on `list_id` and `(list_id, status)`
- `application.properties` — `quarkus.index-dependency.items.*` added
- `module-info.java` — already had `requires capsa.items`

---

## Key Design Decisions

**`@Consumes(MediaType.WILDCARD)` on `complete()`** — the class-level `@Consumes(APPLICATION_JSON)` would reject the no-body completion request with 415. Overridden per-method.

**Idempotent completion** — `ItemServiceImpl.complete()` returns the current state if the item is already DONE. No exception thrown. Consistent with TC-UC06-007.

**`createFromCapture()` implemented but not tested in S-03** — the signature uses raw `UUID captureId` per ADR (prevents `items→capture` JPMS cycle). S-05 provides the integration test.

**Cross-user error codes** — `ListAccessDeniedException` and `ListNotFoundException` propagate as 500 until S-06 adds exception mappers. Tests assert `not(equalTo(2xx))` rather than exact error codes.

**UC-05 via two endpoints (Option C)** — list metadata from `GET /capsa/api/lists/{id}`, items from `GET /capsa/api/items?listId=`. No `lists→items` dependency introduced.

---

## Test Results

```
Tests run: 28, Failures: 0, Errors: 0, Skipped: 0
```

`ItemResourceTest` covers 17 test cases across UC-02, UC-05, and UC-06:
- All TC-UC02-001 through TC-UC02-007
- TC-UC05-001, 002, 003, 004, 006
- TC-UC06-001, 002, 003, 004, 005, 006, 007, 008

---

## Files Changed

| File | Change |
|---|---|
| `capsa-items/pom.xml` | Added all dependencies |
| `capsa-items/…/module-info.java` | Exports + opens + requires |
| `capsa-items/…/api/*.java` | 7 new API types |
| `capsa-items/…/internal/domain/Item.java` | Domain object |
| `capsa-items/…/internal/persistence/entity/ItemEntity.java` | JPA entity |
| `capsa-items/…/internal/persistence/repository/ItemRepository.java` | Repository |
| `capsa-items/…/internal/service/ItemConverter.java` | Converter |
| `capsa-items/…/internal/service/ItemServiceImpl.java` | Service implementation |
| `capsa-items/…/internal/rest/ItemResource.java` | REST resource |
| `capsa-runtime/…/db/migration/V003__items_initial.sql` | `items` table |
| `capsa-runtime/…/application.properties` | items index-dependency |
| `capsa-runtime/…/ItemResourceTest.java` | 17 integration tests |
