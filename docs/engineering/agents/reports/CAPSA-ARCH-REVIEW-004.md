# CAPSA-ARCH-REVIEW-004 — Auth Bootstrap Backend/Frontend Integration Review

**Owner:** Deep
**Type:** Architecture / integration review (no implementation)
**Input authority:** implemented code (`server/`, `web/`), `CAPSA-ARCH-REVIEW-003`, `CAPSA-AUTH-BE-001-report`, `CAPSA-AUTH-FE-001-report`, `CAPSA-AUTH-FE-001` task, Flyway migrations V008/V009, `application.properties`, docker-compose files, `.env.example`
**Status:** Review complete
**Verdict:** PROCEED WITH FIXES

---

## 1. Executive Assessment

**PROCEED WITH FIXES.**

The backend and frontend slices have implemented the `CAPSA-ARCH-REVIEW-003`
recommendations substantially and correctly. The four structural problems from
the prior review are resolved:

- identity is now keyed on `(issuer, subject)` with a compound DB constraint;
- a server-owned `Role { USER, ADMIN }` exists and provisioning defaults to `USER`;
- user provisioning is concurrency-safe via `INSERT … ON CONFLICT DO NOTHING`;
- the `ownerEmail` setup model is gone, replaced by a `capsa-bootstrap` module
  with a token-protected, singleton-guarded atomic first-admin claim.

The bootstrap **security invariant** — *at most one first ADMIN, enforced at the
database level, safe across instances* — is correctly implemented (singleton
row `CHECK (id = 1)` + `ON CONFLICT(id) DO NOTHING`, all within a single
transaction). All ten explicit security questions resolve to `NO`/`PARTIALLY`
with no `YES` (see §8).

Capsa is **ready to proceed** to the invitation domain and real Google/OIDC
wiring. Three fixes should be scheduled alongside, not as blockers:

1. **Wire `CAPSA_BOOTSTRAP_TOKEN` into deployment** (§ F-1, HIGH) — today a
   production operator has no documented/injected way to supply the token, so
   the first admin cannot bootstrap in a real deployment.
2. **Design the "authenticated but not ADMIN" frontend state** (§ F-2, MEDIUM) —
   the gate currently sends a losing 409 claimant straight into `AdminShell`.
3. **Close the concurrency test gap** (§ F-3, MEDIUM) — the singleton invariant
   is correct by construction but not exercised under a real first-claim race.

These do not require a foundational identity/auth redesign.

---

## 2. Architecture Delta (since CAPSA-ARCH-REVIEW-003)

| Concern | Review-003 state | Now |
| --- | --- | --- |
| Identity key | `oidc_subject` only | `(oidc_issuer, oidc_subject)` compound UNIQUE (V008); `ExternalIdentity { issuer, subject, email, displayName }` |
| Role | absent | `Role { USER, ADMIN }` in `capsa.users.api`; `users.role` column; provisioning defaults `USER` |
| Provisioning | check-then-insert (race) | native `INSERT … ON CONFLICT (oidc_issuer, oidc_subject) DO NOTHING` + re-query |
| Setup ownership | `POST /api/setup` + hardcoded `ownerEmail` | `capsa-setup` removed from reactor; `capsa-bootstrap` added |
| Bootstrap claim | nonexistent | `POST /capsa/api/bootstrap`, token-guarded, singleton `bootstrap_state` (`CHECK id = 1`), `ON CONFLICT(id) DO NOTHING` |
| Status | `/api/setup/status` | `GET /capsa/api/bootstrap/status` (`permit`) |
| Error mappers | `CAPSA_SETUP_ALREADY_INITIALIZED` only | `CAPSA_BOOTSTRAP_ALREADY_CLAIMED` (409), `CAPSA_BOOTSTRAP_INVALID_TOKEN` (403) |
| Frontend | `/admin-ui` rendered `AdminShell` directly, no auth | `AdminGateOutlet` gate before `AdminShell`; `AuthProvider` seam; centralized `apiCall` |

New module `capsa-bootstrap` (`com.capsa.bootstrap`) is a leaf depending only on
`capsa.users` + Jakarta/MicroProfile standards — consistent with the modular
monolith rules. `capsa.users` lost its now-unused `internal.domain.User` class
(the aggregate was creation-only; see REVIEW-002 M-4).

---

## 3. What Was Implemented Well

Treat these as stable decisions:

1. **Singleton-row bootstrap claim.** `V009` (`id INTEGER PRIMARY KEY CHECK (id = 1)`)
   plus `BootstrapRepository.claim` (`INSERT … ON CONFLICT(id) DO NOTHING`) is the
   correct, minimal, multi-instance-safe atomic claim. No `adminCount == 0` guard.
2. **Single-transaction bootstrap.** Token check → `findOrProvision` → claim →
   `assignRole(ADMIN)` all run in one `@Transactional` unit; any failure rolls
   back the claim, so a partial bootstrap cannot brick initialization.
3. **Compound identity.** `ExternalIdentity` as the seam; `OidcCurrentUser`
   confines `JsonWebToken`/`Principal` to `runtime`; `CurrentUser.identity()`
   exposes claims without provisioning.
4. **Concurrency-safe provisioning.** The `ON CONFLICT DO NOTHING` + re-query
   pattern is genuinely race-free and is *actually* tested under concurrency
   (`UserIdentityTest.concurrentProvisioning_sameIdentity_resolvesToSameUser`, 5 threads).
5. **Server-owned role taxonomy.** `Role` lives in `capsa.users.api`; the
   frontend's `domain/roles.ts` is explicitly a presentation-only mirror and
   makes no authorization decision.
6. **Token hygiene.** Constant-time `MessageDigest.isEqual`, never logged, never
   persisted, never returned; frontend holds it in `useState` only and clears it
   after every submission (`type="password"`, `autoComplete="off"`).
7. **Centralized frontend HTTP seam.** `api/apiCall.ts` is the single outbound
   boundary; bearer injection and error parsing are not duplicated in pages.
8. **Gate before shell.** `/admin-ui` renders `AdminGateOutlet`, not `AdminShell`;
   the shell cannot appear before the server's `required` state resolves.
9. **Status does not leak token state.** `BootstrapStatus { required, claimedAt }`
   carries no token/secret/identity fields, and a test asserts the absence of
   `token`/`secret`/`configured` in the body.

---

## 4. Findings

### BLOCKER

None.

### HIGH

#### F-1 — `CAPSA_BOOTSTRAP_TOKEN` is not wired into any deployment surface

- **Evidence:** `capsa.bootstrap.token` is read via `@ConfigProperty` in
  `BootstrapServiceImpl.java:28-29` and defaults to `""` when absent
  (`configuredToken.orElse("")`, line 52). `server/capsa-runtime/.../application.properties`
  sets it only under `%test` (line 51). The root `.env.example` lists only
  `CAPSA_DB_*` and `CAPSA_OIDC_*` (no `CAPSA_BOOTSTRAP_TOKEN`), and
  `docker-compose.prod.yml` does not pass it to the `capsa` service.
- **Consequence:** In a real (Coolify) deployment the token is unset, so
  `configured.isBlank()` is true and **every** claim returns 403
  (`CAPSA_BOOTSTRAP_INVALID_TOKEN`). The status endpoint still reports
  `required: true`, so the UI shows the bootstrap form forever but no one can
  become the first admin. This is an operational bricking of first-run
  bootstrap — recoverable only by setting the env var and restarting, but
  undocumented.
- **Recommendation:** Add `CAPSA_BOOTSTRAP_TOKEN` to `.env.example` and to the
  `capsa` service environment in `docker-compose.prod.yml` (and ideally a
  startup warning when `required == true` and the token is blank — see §10).

### MEDIUM

#### F-2 — Frontend has no "authenticated but not ADMIN" state; 409 → `ready` shows admin UI to non-admins

- **Evidence:** `web/src/auth/useBootstrapGate.ts:59-63` transitions to
  `{ kind: "ready" }` on HTTP 409, and `AdminEntryGate` renders `AdminShell`
  for `ready`. There is no `GateState` variant for "authenticated but
  unauthorized", and the frontend has no `/me`-style role knowledge.
- **Consequence:** After the first admin claims bootstrap, a *different*
  authenticated user who submits the (now-used) token receives 409 and is
  shown `AdminShell`. The backend remains authoritative (their admin actions
  will 403), so this is not a privilege escalation — but it is a misleading
  UX and an authorization-modeling gap that will surface when invitation
  creation requires `ADMIN`.
- **Recommendation:** Introduce a distinct gate outcome for "bootstrap not
  required but caller is not an ADMIN" (either a `role` claim on the bootstrap
  status for authenticated callers, or a future `/capsa/api/me`). Until then,
  at minimum do not silently render the full shell on 409 — re-query status
  and let the invitation page's 403 handling be the visible behavior. Design
  this **before** shipping invitation UI to non-admin users.

#### F-3 — The bootstrap concurrency test does not exercise the first-claim race

- **Evidence:** `BootstrapResourceTest.concurrentBootstrap_exactlyOneSucceeds`
  (`:148-178`) runs three parallel POSTs *after* bootstrap was already claimed
  at `@Order(5)`, and asserts all return 409. Its own comment states: "For true
  first-claim concurrency, the ON CONFLICT DO NOTHING guarantee is validated by
  the DB constraint — tested here as sequential 409s."
- **Consequence:** The highest-priority invariant (exactly one **first** claim
  succeeds under concurrency, exactly one user ends with `ADMIN`) is correct by
  construction but not demonstrated by any test. The brief explicitly forbids
  accepting a green suite as proof when critical concurrency behavior is
  untested. (Contrast: provisioning concurrency *is* genuinely tested in
  `UserIdentityTest`.)
- **Recommendation:** Add an ordered test placed *before* any prior claim that
  fires N concurrent valid-token claims from N distinct identities and asserts
  exactly one 201, the rest 409, and exactly one row with `role = ADMIN`.

### LOW

#### F-4 — FE error taxonomy omits `CAPSA_BOOTSTRAP_INVALID_TOKEN`

- **Evidence:** `web/src/api/errors.ts:14-21` (`CapsaErrorCode`,
  `KNOWN_ERROR_CODES`) lists `CAPSA_BOOTSTRAP_ALREADY_CLAIMED` but not
  `CAPSA_BOOTSTRAP_INVALID_TOKEN`. The server emits
  `CAPSA_BOOTSTRAP_INVALID_TOKEN` (403) from
  `BootstrapTokenInvalidExceptionMapper`. `apiCall.parseErrorResponse` falls
  back via `mapStatusToFallbackCode(403) → CAPSA_FORBIDDEN`.
- **Consequence:** Functionally correct (403 → "invalid token" banner via
  `phaseForError`), but the FE's error contract is not 1:1 with the BE, and the
  specific invalid-token code is discarded.
- **Recommendation:** Add `CAPSA_BOOTSTRAP_INVALID_TOKEN` to the FE error
  union and known set.

#### F-5 — `useBootstrapGate` re-check on sign-in is ineffective (`provider.toString()`)

- **Evidence:** `useBootstrapGate.ts:30-31` computes
  `const providerKey = provider.toString()` and uses it as the `useEffect`
  dependency. `StubAuthProvider` does not override `toString()`, so `providerKey`
  is the constant `"[object Object]"` regardless of provider identity.
- **Consequence:** The intended "re-run the gate after sign-in" does not fire on
  provider change. Harmless with the stub (no-op `signIn`), but a latent defect
  for real OIDC where post-sign-in status re-check matters.
- **Recommendation:** Drive the effect off a stable provider identity (or the
  auth-change event counter) rather than `toString()`.

#### F-6 — Stale `capsa-setup` tree and `V007` migration remain on disk

- **Evidence:** `capsa-setup` was removed from `server/pom.xml` (module list now
  omits it), yet `server/capsa-setup/` and
  `server/capsa-runtime/.../db/migration/V007__setup_initial.sql` still exist as
  untracked working-tree files. `V007` creates `setup_state`; `V009` drops it, so
  a build of the current working tree runs a create-then-drop cycle.
- **Consequence:** Not a security issue, but a cleanliness/traceability issue:
  dead source on disk and a vestigial migration that a fresh clone would not
  contain (V007 is untracked), creating a committed-vs-working-tree divergence.
- **Recommendation:** Delete `server/capsa-setup/` and the untracked `V007`
  migration; keep `V009` as the sole removal authority.

#### F-7 — V008 `'legacy'` issuer backfill will orphan pre-existing dev users

- **Evidence:** `V008__users_add_issuer_and_role.sql:5` sets
  `oidc_issuer = 'legacy'` for existing rows. Any user provisioned before the
  auth slice now has identity `('legacy', subject)` and will be re-provisioned
  as a *new* user on their next real login `(issuer, subject)`.
- **Consequence:** For the current pre-production single-user data this is
  harmless; for any environment with real users it silently orphans accounts.
  Documented here as a migration caveat, not a defect.
- **Recommendation:** Acceptable for v0.1 pre-production; note in the migration
  that `'legacy'` rows are intentionally abandoned, or delete them if no
  production data exists.

#### F-8 — FE report's "server lacks ExceptionMappers" claim is now stale

- **Evidence:** `CAPSA-AUTH-FE-001-report.md` §"Server-side contract" and
  §"Limitations" state the server lacks mappers for
  `BootstrapAlreadyClaimedException`/`BootstrapTokenInvalidException` and
  therefore surfaces 500s. Both mappers now exist
  (`server/capsa-runtime/.../error/BootstrapAlreadyClaimedExceptionMapper.java`,
  `BootstrapTokenInvalidExceptionMapper.java`).
- **Consequence:** The FE report understates the current integration status; the
  403/409 paths actually work end-to-end today.
- **Recommendation:** No code change; correct the FE report's limitation note.

---

## 5. Backend Assessment

- **Identity.** Correct: `oidc_issuer` + `oidc_subject` compound UNIQUE (V008),
  `ExternalIdentity` seam, `findOrProvision(issuer, subject, email, name)`.
  Quarkus/JWT types stay in `OidcCurrentUser`. The `issuer` fallback to
  `"unknown"` exists only on the non-`JsonWebToken` branch (`OidcCurrentUser.java:48-56`),
  which is the `@TestSecurity` path; production always has a JWT with `iss`.
- **Role.** Correct: `Role` enum, `users.role` column default `USER`,
  `assignRole` is the only promotion path and is called only from the bootstrap
  claim. Bootstrap privilege (token + singleton) is cleanly separate from the
  `ADMIN` role.
- **Provisioning.** Correct and race-free (`UserRepository.insertIfAbsent` +
  re-query). Genuinely tested under concurrency.
- **Bootstrap persistence.** Correct: singleton `CHECK (id = 1)`,
  `ON CONFLICT(id) DO NOTHING`, single transaction with rollback. The claim
  correctly returns 409 (not 500) on the losing side because the exception is
  thrown *inside* the transaction after the atomic insert returns 0 rows.
- **API.** `GET /capsa/api/bootstrap/status` is `permit`;
  `POST /capsa/api/bootstrap` falls under the default authenticated policy.
  Both are under `/capsa/api/...` (namespace reconciled). No `/api/setup`
  endpoint remains in the reactor.
- **Error mapping.** 409/403 mappers present and correct; 422 for missing token
  is still hand-built inline JSON in `BootstrapResource` (consistent with the
  pre-existing validation pattern noted in REVIEW-002 M-6, not introduced here).
- **Security.** Token never persisted/logged; constant-time compare; status does
  not disclose token state.

**Backend verdict:** sound; the only real gap is operational token wiring (F-1)
and test coverage of the first-claim race (F-3).

---

## 6. Frontend Assessment

- **Routing gate.** Correct: `AdminGateOutlet` renders the gate (`checking` /
  `error` / `required`) and only yields to `AdminShell` on `ready`. The shell
  cannot render before the server resolves `required`.
- **Token seam.** Correct: `apiCall` is the sole `fetch` boundary;
  `createInvitation` and `claimBootstrap` route through it; pages never build
  `Authorization` headers (test #13 enforces this structurally).
- **Bootstrap UX.** Loading/required/ready/error states all covered. 401 → auth
  required, 403 → invalid token, 409 → (see F-2) direct `ready`, network/5xx →
  recoverable. Token cleared after every submission and not persisted (test #12).
- **Secret handling.** Token held in `useState` only, `type="password"`,
  `autoComplete="off"`, never in storage/URL/DOM/logs. `VITE_CAPSA_STUB_ACCESS_TOKEN`
  is documented as dev-only and blank by default.
- **Role modeling.** `domain/roles.ts` mirrors the server taxonomy for labels
  only; no local authorization decision.

**Frontend verdict:** sound for the stub phase; F-2 (not-authorized state) and
F-5 (sign-in re-check) are the gaps to close before/with real OIDC.

---

## 7. FE ↔ BE Contract Check

| Concern | Backend (actual) | Frontend (actual) | Match |
| --- | --- | --- | --- |
| Status path | `GET /capsa/api/bootstrap/status` | `getBootstrapStatus` → `statusPath` | ✓ |
| Status auth | `permit` (no bearer) | `{ anonymous: true }` | ✓ |
| Status success | `200 { required, claimedAt }` | `BootstrapStatus { required, claimedAt: string\|null }` | ✓ |
| Claim path | `POST /capsa/api/bootstrap` | `claimBootstrap` → `bootstrapPath` | ✓ |
| Claim auth | authenticated (default policy) | bearer via `apiCall` | ✓ |
| Claim request | `{ "token": "…" }` | `{ token }` (no email/role/identity) | ✓ |
| Claim success | `201 { required: false, claimedAt }` | parsed as `BootstrapStatus` | ✓ |
| Missing token | `422 CAPSA_VALIDATION_ERROR` | (not specifically tested; falls to generic error) | ⚠ |
| Invalid token | `403 CAPSA_BOOTSTRAP_INVALID_TOKEN` | FE maps 403 → `CAPSA_FORBIDDEN` → "invalid token" (F-4) | ⚠ |
| Already claimed | `409 CAPSA_BOOTSTRAP_ALREADY_CLAIMED` | FE treats 409 → `ready` (F-2) | ⚠ |
| Unauthenticated | `401` | FE treats `CAPSA_UNAUTHORIZED` → auth-required | ✓ |

No path/body mismatch. The three `⚠` are semantic, not structural: the FE does
not yet carry `CAPSA_BOOTSTRAP_INVALID_TOKEN` as a first-class code (F-4), and
the 409→`ready` transition is deliberate but authorization-blind (F-2).

---

## 8. Security Answers

1. **Can an unauthenticated caller become ADMIN?** NO — only `/status` is
   `permit`; `POST /capsa/api/bootstrap` is under the default authenticated
   policy. (Caveat: in `docker-dev` OIDC is disabled, but that profile is not
   production; worth a note in the deployment docs.)
2. **Can an authenticated caller become ADMIN without the bootstrap secret?**
   NO — token is validated first (`BootstrapServiceImpl.java:52-58`), before any
   provisioning/claim; `assignRole` has no other caller.
3. **Can two callers become first ADMIN concurrently?** NO — singleton
   `bootstrap_state` (`CHECK id = 1`) + `ON CONFLICT(id) DO NOTHING` guarantees
   one insert; losers get 409 and roll back. (Not yet demonstrated by a
   first-claim concurrency test — F-3.)
4. **Can a valid bootstrap token be reused after bootstrap completes?** NO —
   the claim insert returns 0 rows once claimed → 409, no state change. The
   token can be *submitted* again but has no effect.
5. **Can the frontend decide someone is ADMIN without backend authority?**
   PARTIALLY — the frontend renders `AdminShell` to a 409 claimant (F-2), but it
   cannot authorize any action; every admin operation is 403-gated server-side.
   This is a UX gap, not a privilege escalation.
6. **Can email affect authorization?** NO — email is profile only; identity is
   `(issuer, subject)`; role is a separate column; no email-based check remains
   (`owner_email` removed).
7. **Can a subject collision occur across issuers?** NO —
   `UNIQUE (oidc_issuer, oidc_subject)` (V008), verified by
   `UserIdentityTest.sameSubjectDifferentIssuers_distinctUsers`.
8. **Can the bootstrap token leak through logs, responses, URLs, storage, or
   reports?** NO — backend logs omit the token (`BootstrapServiceImpl.java:56,74`);
   `BootstrapStatus` carries only `required`/`claimedAt`; frontend holds it in
   `useState` only and clears it; reports contain only test fixture values.
9. **Can multiple application instances violate the singleton invariant?** NO —
   PostgreSQL is the coordinator; the `CHECK (id = 1)` row + `ON CONFLICT` is
   instance-agnostic.
10. **Can failure halfway through bootstrap permanently brick initialization?**
    NO for code failures — provision/claim/assignRole share one transaction, so
    a mid-flow failure rolls back and leaves bootstrap claimable. YES for
    **misconfiguration** — an unset `CAPSA_BOOTSTRAP_TOKEN` makes every claim 403
    while status still says `required: true` (F-1); recoverable by setting the
    env var.

---

## 9. Required Fixes Before Invitations

Only these should gate the invitation-domain slice:

- **F-1 (HIGH):** wire `CAPSA_BOOTSTRAP_TOKEN` into `.env.example` and
  `docker-compose.prod.yml` so the first admin can actually bootstrap in
  deployment.
- **F-2 (MEDIUM, design):** decide how the frontend represents "authenticated
  but not ADMIN" so the invitation UI does not render optimistically for a
  non-admin. At minimum, the invitation backend must enforce `ADMIN` on create;
  the frontend must surface 403 without implying the user is an admin.

Everything else (F-3 through F-8) can ride along or be deferred; none blocks the
invitation domain.

---

## 10. Deferred Improvements

- Startup warning when `required == true` and `capsa.bootstrap.token` is blank
  (cheap operational safety for F-1).
- Real first-claim concurrency test (F-3).
- FE `CAPSA_BOOTSTRAP_INVALID_TOKEN` in the error union (F-4).
- Sign-in → gate re-check wiring (F-5).
- Delete stale `capsa-setup/` + `V007` (F-6).
- `/capsa/api/me` or a role claim on bootstrap status for authenticated callers
  (to enable the F-2 state properly).
- Invitation token hashing-at-rest and client-composed `acceptUrl` (already
  flagged in REVIEW-003 M-2; invitation-slice concern).
- Replace `StubAuthProvider` with a real `GoogleOidcAuthProvider`.

---

## 11. Recommendation for Next Slice

Proceed to the **invitation domain**, split as:

1. **Backend — invitation capability** (`capsa-invitations`): `Invitation`
   entity (hashed token, `role`, `expiresAt`, one-time `redeemed` flag),
   `POST /capsa/api/invitations` gated on `ADMIN`, plus a redemption seam
   (authenticated identity + token). This now has a sound `Role` model and
   `CurrentUser.identity()` to build on.
2. **Backend — invitation authorization**: enforce `ADMIN` on invitation
   creation via the existing `Role`; decide whether non-admins may ever create
   invitations (currently the frontend offers both `USER` and `ADMIN` roles).
3. **Frontend — real OIDC**: replace `StubAuthProvider` with a Google OIDC
   provider (client id env var), wire sign-in → gate re-check (F-5), and add the
   not-authorized state (F-2).

Ordering: F-1 and F-2 should land before or with these slices; the invitation
*domain* itself is unblocked today.
