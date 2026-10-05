# CAPSA-AUTH-BE-001 — OIDC Identity Model, Role System, and Bootstrap API — Implementation Report

**Task:** CAPSA-AUTH-BE-001
**Status:** DONE
**Date:** 2026-10-05
**Driven by:** CAPSA-ARCH-REVIEW-003

---

## Summary

This task implements the recommendations from CAPSA-ARCH-REVIEW-003. It corrects the OIDC identity model to key users on `(issuer, subject)` instead of `subject` alone, introduces a `Role` model (`USER`/`ADMIN`), makes user provisioning concurrency-safe via native SQL `ON CONFLICT DO NOTHING`, removes the broken email-based setup ownership model, and replaces `capsa-setup` with a new `capsa-bootstrap` module that exposes an atomic first-admin claim endpoint protected by an externally-configured secret token.

---

## Identity Model Correction

### Before

`UserService.findOrProvision(String oidcSubject, ...)` keyed users by OIDC `sub` claim alone. A unique constraint on `oidc_subject` would conflict between users from different identity providers sharing the same subject value (e.g. both returning `"user123"`).

### After

`UserService.findOrProvision(String issuer, String subject, ...)` takes the full OIDC `(issuer, subject)` pair. The database constraint `UNIQUE (oidc_issuer, oidc_subject)` is the authority. A new column `oidc_issuer VARCHAR(255) NOT NULL` was added via migration V008.

The `ExternalIdentity` record is the clean seam:

```java
public record ExternalIdentity(String issuer, String subject, String email, String displayName) {}
```

`CurrentUser` gained an `identity()` method so callers can access the full external identity without coupling to the OIDC implementation. `OidcCurrentUser` in `capsa-runtime` extracts the issuer from `JsonWebToken.getIssuer()` when the principal is a JWT; falls back to `"unknown"` otherwise (e.g. `@TestSecurity` tests that set a plain username).

---

## Role Model

A `Role` enum (`USER`, `ADMIN`) was added to `capsa-users.api`. New users are provisioned with `Role.USER`. The `users` table gained a `role VARCHAR(20) NOT NULL DEFAULT 'USER'` column (V008 migration).

`UserService.assignRole(UserId, Role)` promotes a user to `ADMIN`. It is called once during bootstrap claim.

`UserView` now includes the `role` field so callers can inspect the current role.

---

## Concurrency-Safe Provisioning

The old check-then-insert pattern had a race window: two concurrent requests for the same new user could both pass the "does not exist" check and then one would fail with a constraint violation.

The new pattern:

1. Query `findByIssuerAndSubject` — if found, return immediately (fast path).
2. Attempt `INSERT ... ON CONFLICT (oidc_issuer, oidc_subject) DO NOTHING`.
3. Re-query — the row is now guaranteed to exist, inserted by either this thread or a concurrent one.

The database constraint is the authority. No application-level locking is needed.

---

## Bootstrap Module (`capsa-bootstrap`)

`capsa-setup` is removed. Its replacement, `capsa-bootstrap`, owns the "first ADMIN claim" domain.

### Bootstrap State Singleton

`V009__bootstrap_state.sql` creates:

```sql
CREATE TABLE bootstrap_state (
    id                  INTEGER     PRIMARY KEY CHECK (id = 1),
    claimed_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    claimed_by_user_id  UUID        NOT NULL REFERENCES users(id)
);
```

The `CHECK (id = 1)` constraint enforces at most one row. The `INSERT ... ON CONFLICT(id) DO NOTHING` pattern makes the claim atomic across multiple application instances.

### Bootstrap Token

`BootstrapServiceImpl` reads `capsa.bootstrap.token` via MicroProfile Config (`Optional<String>`). The token is never logged. Comparison uses `MessageDigest.isEqual()` for constant-time evaluation. If the config is absent or blank, all claim attempts are rejected.

In tests, `%test.capsa.bootstrap.token=test-bootstrap-token` is set in `application.properties`.

### Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/capsa/api/bootstrap/status` | Public | Returns `{ "required": true/false, "claimedAt": "..." }` |
| `POST` | `/capsa/api/bootstrap` | Authenticated | Claims bootstrap with `{ "token": "..." }` body |

`GET /capsa/api/bootstrap/status` is explicitly permitted via `quarkus.http.auth.permission.bootstrap-status.*` so it is accessible without authentication.

### Error Codes

| Exception | HTTP | Code |
|-----------|------|------|
| `BootstrapAlreadyClaimedException` | 409 | `CAPSA_BOOTSTRAP_ALREADY_CLAIMED` |
| `BootstrapTokenInvalidException` | 403 | `CAPSA_BOOTSTRAP_INVALID_TOKEN` |

---

## Flyway Migrations

| Migration | Purpose |
|-----------|---------|
| V008 | Add `oidc_issuer` column; drop single-column unique on `oidc_subject`; add composite unique on `(oidc_issuer, oidc_subject)`; add `role` column with default `USER` |
| V009 | Drop `setup_state` table; create `bootstrap_state` table with singleton constraint |

V007 (`setup_state`) is removed from the build. V009 drops it at migration time so existing databases upgrade cleanly.

---

## Modules Changed

| Module | Change |
|--------|--------|
| `capsa-bootstrap` | **New** — owns bootstrap domain |
| `capsa-setup` | **Removed** |
| `capsa-users` | `(issuer, subject)` identity; `Role`; `ExternalIdentity`; `assignRole`; concurrency-safe provisioning |
| `capsa-runtime` | `OidcCurrentUser` updated; error mappers for bootstrap exceptions; module-info; application.properties; migrations |

---

## Test Coverage

**`BootstrapResourceTest`** (8 ordered tests):
1. Status returns `required: true` before claim
2. Blank token rejected with 403
3. Wrong token rejected with 403
4. Unauthenticated claim rejected with 401
5. Valid token + authenticated user claims bootstrap → 200
6. Status returns `required: false` after claim
7. Second claim attempt returns 409
8. Concurrent second claim also returns 409

**`UserIdentityTest`** (4 tests):
1. Same `(issuer, subject)` returns same user ID across calls
2. Same `subject` with different issuers creates distinct users
3. Newly provisioned users have role `USER`
4. Concurrent provisioning for same identity resolves to one user

**`UserProvisioningTest`** updated to 4-arg `findOrProvision`.

All existing tests updated to pass `issuer` parameter. **56/56 tests pass.**

---

## Invariants Established

- Every user in the system has exactly one `(oidc_issuer, oidc_subject)` pair.
- At most one row exists in `bootstrap_state` (enforced by `CHECK (id = 1)` + primary key).
- Exactly one user holds `role = ADMIN` after bootstrap (enforced by the claim flow: `assignRole` is called only on successful insert into `bootstrap_state`).
- The bootstrap token is never persisted or logged.
