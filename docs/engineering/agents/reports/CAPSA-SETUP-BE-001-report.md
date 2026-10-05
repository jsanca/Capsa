# CAPSA-SETUP-BE-001 — Initial Setup REST API — Implementation Report

**Task:** CAPSA-SETUP-BE-001
**Status:** DONE
**Date:** 2026-10-04

---

## Initialization Invariant

Capsa is **initialized** when exactly one row exists in the `setup_state` table. That row is the authoritative record of initialization. No scattered state or configuration flag determines this — the service calls `SetupRepository.findSetupState()` and checks presence.

The transition is one-way: `UNINITIALIZED → INITIALIZED`. There is no revert, re-run, or reset path.

## New Module: `capsa-setup`

A new capability module `capsa-setup` owns the setup domain. It follows the standard JPMS modular monolith conventions: only `com.capsa.setup.api` is exported; internal packages are opened for Hibernate and CDI reflection only.

`capsa-setup` has no dependency on any other `capsa.*` capability module. It is a leaf module.

## API Contract

### `GET /api/setup/status`

No authentication required. Returns the current initialization state.

**Response 200 — uninitialized:**
```json
{ "initialized": false, "initializedAt": null }
```

**Response 200 — initialized:**
```json
{ "initialized": true, "initializedAt": "2026-10-04T04:43:40.345Z" }
```

### `POST /api/setup`

No authentication required. Initializes a fresh Capsa installation.

**Request body:**
```json
{ "ownerEmail": "owner@example.com" }
```

**Response 201 — success:**
```json
{ "initialized": true, "initializedAt": "2026-10-04T04:43:40.345Z" }
```

**Response 409 — already initialized:**
```json
{ "code": "CAPSA_SETUP_ALREADY_INITIALIZED", "message": "Capsa is already initialized" }
```

**Response 422 — validation failure:**
```json
{ "code": "CAPSA_VALIDATION_ERROR", "message": "ownerEmail is required" }
```

Both setup endpoints are declared public via `quarkus.http.auth.permission.setup.policy=permit` in `application.properties`. This allows unauthenticated access even when OIDC is enabled in production.

## Minimum Setup Data

`ownerEmail` is the only required field. It is stored in `setup_state` as a reference to the intended installation owner. User provisioning still occurs through the normal OIDC flow; the setup email is not a user account credential.

## Idempotency Behavior

A repeated `POST /api/setup` call returns **409 Conflict** with code `CAPSA_SETUP_ALREADY_INITIALIZED`. The existing initialized state is not modified. This choice was made over silently returning the existing state because 409 makes it unambiguous to the caller that a second initialization was attempted — the caller can distinguish a successful first-run from an accidental repeat.

## Transactionality

`SetupServiceImpl.initialize` is annotated `@Transactional` (default `REQUIRED`). The guard check and persist happen in a single transaction. A failure after the guard but before commit leaves no row in `setup_state`, so the system remains `UNINITIALIZED` and the endpoint is safe to retry.

## Persistence

Migration `V007__setup_initial.sql` adds one table:

```sql
CREATE TABLE setup_state (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    initialized_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    owner_email    VARCHAR(255) NOT NULL
);
```

No unique constraint is needed beyond the primary key because the service enforces the single-row invariant in code before persisting.

## Tests

Eight integration tests in `SetupResourceTest` cover all six acceptance criteria. Tests are ordered with `@TestMethodOrder` because setup state is global within a single test run. DevServices provisions a fresh PostgreSQL instance per test run, so state resets between `./mvnw test` invocations.

| # | Test | Criterion |
|---|------|-----------|
| TC-SETUP-001 | `freshInstallReportsNotInitialized` | Fresh install → `initialized: false` |
| TC-SETUP-004 | `missingOwnerEmailIsRejectedWith422` | Invalid input → 422 |
| TC-SETUP-004b | `nullBodyIsRejectedWith422` | Null body → 422 |
| TC-SETUP-006 | `invalidInputDoesNotChangeInitializationState` | No partial state after failure |
| TC-SETUP-002 | `validSetupInitializesSystem` | Successful setup → 201 |
| TC-SETUP-003 | `statusAfterSetupReportsInitialized` | Status reports `initialized: true` |
| TC-SETUP-005 | `repeatedSetupIsRejectedWith409` | Repeat → 409 |
| TC-SETUP-005b | `repeatedSetupDoesNotCorruptState` | State survives repeat attempt |

All 52 tests pass (`./mvnw test -pl capsa-setup,capsa-runtime`).
