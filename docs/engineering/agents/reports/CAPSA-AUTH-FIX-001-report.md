# CAPSA-AUTH-FIX-001 — Post-Review Bootstrap Hardening — Implementation Report

**Task:** CAPSA-AUTH-FIX-001
**Status:** DONE
**Date:** 2026-10-05
**Driven by:** CAPSA-ARCH-REVIEW-004

---

## Backend / Clio (A1–A4)

### A1 — CAPSA_BOOTSTRAP_TOKEN deployment wiring

`CAPSA_BOOTSTRAP_TOKEN` is now documented and wired into production deployment surfaces:

- **`.env.example`**: added `CAPSA_BOOTSTRAP_TOKEN=` with comment describing usage, generation (`openssl rand -hex 32`), Coolify secret configuration, and the constraint that a real value must never be committed.
- **`docker-compose.prod.yml`**: added `CAPSA_BOOTSTRAP_TOKEN: ${CAPSA_BOOTSTRAP_TOKEN}` to the `capsa` service environment block with inline documentation.

Quarkus config key: `capsa.bootstrap.token`. Read at startup via MicroProfile Config; never logged or persisted. The token is inert after bootstrap is complete.

### A2 — First-claim concurrency integration test

`BootstrapResourceTest` now includes a true first-claim concurrency test at Order 5 (`firstClaimConcurrency_exactlyOneAdminEmerges`), which replaces the old single-HTTP-claim test.

**What it tests:**
- 4 threads are started simultaneously, each holding a distinct `ExternalIdentity` (`concurrent-claimer-0..3`).
- All 4 call `bootstrapService.claim()` with the valid token at the same time via `CountDownLatch`.
- **Assertion:** exactly 1 call returns a `BootstrapStatus`; the other 3 throw `BootstrapAlreadyClaimedException`.
- **Assertion:** exactly 1 user holds `Role.ADMIN` across all 4 provisioned identities.

This directly exercises the `INSERT INTO bootstrap_state ... ON CONFLICT(id) DO NOTHING` invariant. The `CHECK (id = 1)` constraint is the authority; the application-level code merely observes who won.

After Order 5, bootstrap is claimed for the remainder of the test run. Orders 6–8 verify the post-claim behavior (status `required=false`, HTTP 409 on repeat, concurrent HTTP 409s).

### A3 — Stale capsa-setup artifact removal

`server/capsa-setup/` and `server/capsa-runtime/src/main/resources/db/migration/V007__setup_initial.sql` were both confirmed as untracked working-tree files (no git history for either, post-`capsa/` layout move). Both removed.

Flyway migration history does not reference V007 (it was never committed), so no immutable history was modified.

### A4 — Legacy issuer migration note

`V008__users_add_issuer_and_role.sql` now documents the `'legacy'` backfill:

> `rows with oidc_issuer='legacy' are pre-auth development records and are NOT intended to match any real OIDC issuer. On first real-OIDC login, these users will be re-provisioned as new rows keyed on (real-issuer, subject). No production account migration path is guaranteed for v0.1 pre-auth data.`

No code or schema change. Comment only.

---

## Frontend / Mini (B1–B5)

### B1/B2 — bootstrap-closed gate state

`useBootstrapGate` now has a `{ kind: "bootstrap-closed" }` state. The key behavior change:

| Trigger | Old state | New state |
|---------|-----------|-----------|
| `GET /status` → `required: false` | `ready` | `bootstrap-closed` |
| `POST /bootstrap` → 409 | `ready` | `bootstrap-closed` |
| `POST /bootstrap` → 201 (success) | `ready` | `ready` (unchanged) |

**`{ kind: "ready" }` is now only reachable via a successful 201 claim response.** This is the only situation where the frontend can be certain the caller is an ADMIN.

`AdminEntryGate` renders a `<BootstrapClosedPanel>` (`data-testid="gate-bootstrap-closed"`) for the `bootstrap-closed` state:

```
Capsa is already initialized.
Your account is authenticated but does not currently have confirmed administrative access.
```

This state is structurally ready for `/me` — once server-side role lookup exists, the gate can re-check and transition to `ready` if the caller is ADMIN.

### B3 — Auth-provider change detection

`useBootstrapGate` previously used `const providerKey = provider.toString()` as a `useEffect` dependency. Since `StubAuthProvider` does not override `toString()`, this always evaluated to `[object Object]` and the effect never re-ran on auth changes.

Fixed: the `providerKey` variable is removed; the `provider` object reference itself is used as the dependency. `useSyncExternalStore` returns a stable reference until `setActiveAuthProvider` is called, so the effect correctly re-fires on auth change.

### B4 — CAPSA_BOOTSTRAP_INVALID_TOKEN error code

Added `"CAPSA_BOOTSTRAP_INVALID_TOKEN"` to `CapsaErrorCode` and `KNOWN_ERROR_CODES` in `api/errors.ts`. Updated `phaseForError` in `AdminEntryGate` to map `CAPSA_BOOTSTRAP_INVALID_TOKEN` → `"invalid-token"` phase (alongside `CAPSA_FORBIDDEN`).

### B5 — Stale FE report correction

`CAPSA-AUTH-FE-001-report.md` previously noted that `BootstrapAlreadyClaimedExceptionMapper` and `BootstrapTokenInvalidExceptionMapper` were absent from the server. Both mappers were added in CAPSA-AUTH-BE-001. The report is marked `[RESOLVED in CAPSA-AUTH-FIX-001]`.

---

## Test results

### Backend — 56/56 ✅

All 8 `BootstrapResourceTest` tests pass including the new first-claim concurrency test.

### Frontend — 34/34 ✅

`admin-entry-gate.test.tsx` (18 tests): new tests added for `bootstrap-closed` on `required=false`, `bootstrap-closed` on 409, `CAPSA_BOOTSTRAP_INVALID_TOKEN` mapping, auth-provider change re-evaluation, and `ready` state only via 201 success.

`admin-routing.test.tsx` (12 tests): `renderAt` updated to drive through the full claim flow (required=true → POST → 201) before testing routing/invitation behavior, since `required=false` no longer opens `AdminShell` directly.

`invitations-api.test.ts` (4 tests): unchanged, still pass.

---

## Deployment requirements

A fresh Capsa deployment requires:

```
CAPSA_BOOTSTRAP_TOKEN=<strong-random-secret>
```

Set in Coolify as a secret environment variable before the first `POST /capsa/api/bootstrap` request. After bootstrap is claimed the variable is inert but must remain set (blank token would reject all future claim attempts with the existing empty-token guard).

## Remaining deferred concerns

- `/capsa/api/me` endpoint for server-side role confirmation — frontend currently shows `bootstrap-closed` for all sessions where bootstrap was previously completed; ADMIN UX requires implementing `/me` and wiring it into the gate.
- Rate limiting on `POST /capsa/api/bootstrap` — not implemented; deferred to infrastructure layer.
- Real OIDC SDK integration — stub provider is still the default; `StubAuthProvider` remains the implementation.
