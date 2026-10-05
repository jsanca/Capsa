# CAPSA-ARCH-REVIEW-003 — Admin UI, Initial Setup and First-Admin Bootstrap Review

**Owner:** Deep
**Type:** Architecture review / design review (no implementation)
**Input authority:** implemented code under `server/` and `web/`, `capsa-arch-001`, `capsa-domain-model-v0.1`, `CAPSA-SETUP-BE-001-report`, `CAPSA-WEB-001-report`, `CAPSA-REPO-001`, `CAPSA-ARCH-REVIEW-002`, `S-06-report`, `application.properties`, Flyway migrations V001–V007, docker-compose files
**Status:** Review complete
**Verdict:** REWORK REQUIRED (of the setup / identity / authorization model) before the auth + bootstrap slices are written

---

## 1. Executive Assessment

**REWORK REQUIRED.**

The repository restructure is sound: `server/` is a clean Quarkus modular
monolith, `web/` is a genuinely independent client that talks only to the
public HTTP contract, and the JPMS capability boundaries remain enforced. On
those points the direction is correct and should be preserved.

The direction is **not** structurally sound for the next two slices because the
current "initial setup" and identity/authorization foundations are the wrong
shape for a secure first-admin bootstrap:

1. **First-admin bootstrap does not exist.** `POST /api/setup` is a public
   (`permit`), unauthenticated endpoint that accepts a hardcoded `ownerEmail`
   and writes a `setup_state` row. There is no OIDC identity, no bootstrap
   token, no "no administrator exists" check, and no concept of an ADMIN at
   all. This is precisely the hardcoded-email anti-pattern the brief forbids.

2. **The single-row initialization invariant is not enforced at the database.**
   `SetupServiceImpl.initialize` is a check-then-insert with no unique
   constraint, no locking, and no atomic upsert. Two concurrent requests both
   pass the guard and insert two rows. This is the exact
   `if (adminCount == 0) createAdmin()` failure the brief flags.

3. **Identity is keyed on OIDC `subject` only, not `issuer + subject`.** The
   `users` table has no issuer column, and `findOrProvision(oidcSubject, …)`
   ignores the token issuer.

4. **There is no role/authorization model.** `User` conflates external identity
   with profile; `Role`, `Invitation`, and `ADMIN` exist nowhere server-side.
   The web client invented a `"USER" | "ADMIN"` string union.

These four are model decisions, not code defects. They must be settled before
"Google/OIDC + secure first-admin bootstrap" is implemented, otherwise the
bootstrap slice will build on the `ownerEmail` setup flow and the
subject-only identity key and require a second rework immediately after.

The good news: the rework is small and contained. The recommended model is in
§5–§6, and the two implementation slices it unblocks are in §7–§8.

---

## 2. Current Architecture (as implemented)

### Component / interaction diagram

```text
            ┌─────────────────────────────────────────────────────────────┐
            │  web/  (independent React 18 + Vite + TS client)           │
            │                                                             │
            │  AppRoutes  ──► /admin-ui (AdminShell)                     │
            │                  └── /admin-ui/invitations/new              │
            │        │                                                   │
            │        │  fetch  (no auth header today)                    │
            │        ▼                                                   │
            │  api/invitations.ts ──► POST {VITE_CAPSA_API_URL}/capsa/api/│invitations
            └─────────────────────────────────────────────────────────────┘
                                        │  (endpoint does NOT exist yet)
                                        ▼
            ┌─────────────────────────────────────────────────────────────┐
            │  server/  (Quarkus 3, Java 25, JPMS modular monolith)      │
            │                                                             │
            │  ┌──────────────┐   OIDC (off in dev/test/docker-dev)      │
            │  │ capsa-runtime │ ── OidcCurrentUser ──► UserService       │
            │  └──────────────┘        .findOrProvision(subject,email,name)
            │        │  exception mappers · Flyway V001–V007             │
            │        ▼                                                   │
            │  capabilities: users · lists · items · capture ·           │
            │                classification · observability · setup      │
            │                                                             │
            │  users:  User { id, oidcSubject(UNIQUE), email, name,      │
            │                    createdAt }   ← no issuer, no role      │
            │  setup:  setup_state { id, initialized_at, owner_email }   │
            │          GET /api/setup/status   (permit)                 │
            │          POST /api/setup  {ownerEmail}   (permit)          │
            │  lists/items/captures:  /capsa/api/...                     │
            └─────────────────────────────────────────────────────────────┘
```

### Facts of record

- **`server/`** is a Maven multi-module project (`groupId com.capsa`,
  `artifactId capsa`), 8 modules: `capsa-observability`, `capsa-users`,
  `capsa-lists`, `capsa-items`, `capsa-capture`, `capsa-classification`,
  `capsa-setup` (all `jar`), and `capsa-runtime` (the only `quarkus` module).
- **REST resources live vertically inside each capability's `internal.rest`**
  (`ListResource`, `ItemResource`, `CaptureResource`, `SetupResource`).
  Exception mappers, `OidcCurrentUser`, and Flyway live in `capsa-runtime`.
- **Public REST namespace** is `/capsa/api/...` for lists/items/captures.
  Setup is the exception: `/api/setup/...` (outside the namespace, `permit`).
- **`capsa-users`**: `User` aggregate = `id`, `oidcSubject` (unique),
  `email`, `name`, `createdAt`. `UserService.findOrProvision(oidcSubject,
  email, name)` provisions lazily. `CurrentUser` exposes `UserId`.
- **`capsa-runtime` `OidcCurrentUser`**: reads `principal.getName()` (subject)
  and, when the principal is a `JsonWebToken`, `email`/`name` claims — then
  calls `findOrProvision`. The issuer (`jwt.getIssuer()`) is **not** used.
- **`capsa-setup`**: leaf module, no `capsa.*` deps. `setup_state` table =
  `id`, `initialized_at`, `owner_email`. `GET /api/setup/status` and
  `POST /api/setup` are both public. Idempotency = 409 on repeat.
- **`web/`**: React 18 + Vite + TS, strict. `BrowserRouter` + `<Routes>`.
  `AdminShell` at `/admin-ui`; invitation creation page posts to
  `POST /capsa/api/invitations`, a **proposed** contract the server does not
  yet expose. No auth state, no OIDC token, no bootstrap handling.
  `VITE_CAPSA_API_URL` is required at startup.
- **Deployment**: `docker-compose.yml` (dev, OIDC off via `docker-dev`
  profile), `docker-compose.prod.yml` (prod, OIDC via env).
  `.env.example` inventories DB + OIDC vars only. No bootstrap token var.
- **No `Role`, `Invitation`, `Admin`, `bootstrap`, or `permission` concept
  exists anywhere in `server/`** (grep of `*.java`/`*.sql`/`*.properties`
  returns no matches beyond the `permit` policy name and observability
  "identity" wording).

---

## 3. What Is Sound

Preserve these decisions:

1. **Product-level boundaries as siblings** (`server/`, `web/`, `android/`,
   `ios/`, `mcp/`). `web/` is a real independent client with no import of
   `server/` internals — it knows only `fetch` + a typed API client.
2. **Capability-organized JPMS monolith with an acyclic graph.** Cross-module
   collaboration goes through each capability's `api` package only.
3. **`CurrentUser` port owned by `users.api`, implemented in `runtime`.**
   Quarkus/OIDC types are confined to `runtime`; capabilities see only
   `CurrentUser` + `UserId`. This is the right seam to extend for the
   authenticated bootstrap.
4. **Flyway owns the schema** (`database.generation=none`); migrations are
   namespaced and live only in `runtime`. New auth/bootstrap schema changes
   have an obvious home (`V008+`).
5. **Frontend API-client seam.** `api/invitations.ts` is the single place that
   builds requests; presentation code does not. This is where the bearer-token
   header should be added later, without touching pages.
6. **Error model** (`{code, message}` + per-capability exception mappers) is a
   stable shape the bootstrap/invitation endpoints should reuse.
7. **`capsa-setup` being a leaf module** is correct in principle — setup should
   not depend on users. The problem is what it does (hardcoded email), not its
   placement.

---

## 4. Findings

### BLOCKER

#### B-1 — First-admin bootstrap is absent; current setup is an unauthenticated, email-keyed operation

- **Evidence:** `SetupResource` (`server/capsa-setup/.../internal/rest/SetupResource.java`)
  exposes `POST /api/setup` with body `{ownerEmail}`; `application.properties`
  declares `quarkus.http.auth.permission.setup.policy=permit`. The service
  (`SetupServiceImpl.initialize`) only validates `ownerEmail` and persists a
  `setup_state` row. No OIDC identity, no `CAPSA_BOOTSTRAP_TOKEN`, no
  "no administrator exists" check, no ADMIN role exists anywhere.
  `CAPSA-SETUP-BE-001-report.md` §"Minimum Setup Data" states `ownerEmail` is
  "stored in `setup_state` as a reference to the intended installation owner."
- **Architectural consequence:** The entire first-admin bootstrap is greenfield
  and must not build on the `ownerEmail` flow. The current design hardcodes an
  email as the installation anchor — exactly what the brief forbids. It also
  conflates "application initialized" with "owner known", leaving no seam for
  "authenticated identity + token + no-admin-exists → first ADMIN".
- **Recommended correction:** Split the responsibility: (a) a minimal
  *initialization* state (a row/flag) meaning "this install has been
  initialized", and (b) a separate *first-admin bootstrap* capability
  (`POST /capsa/api/bootstrap`, authenticated + token) that creates the first
  ADMIN User and closes bootstrap. Drop `owner_email` as an
  authorization-relevant field (keep at most as an audit/contact field, and
  even then prefer deriving from the authenticated identity's profile).

#### B-2 — Single-row initialization / "exactly one ADMIN" invariant is not enforced at the database

- **Evidence:** `SetupServiceImpl.initialize` (`server/capsa-setup/.../internal/service/SetupServiceImpl.java:45-54`)
  does `findSetupState().isPresent()` → throw, else `save(...)`. `V007__setup_initial.sql`
  creates `setup_state` with **no** uniqueness constraint beyond the primary
  key (which is a random UUID, so it cannot prevent two rows). There is no
  `SELECT … FOR UPDATE`, no advisory lock, no `INSERT … ON CONFLICT`.
  `CAPSA-SETUP-BE-001-report.md` §"Persistence" even asserts "No unique
  constraint is needed beyond the primary key because the service enforces the
  single-row invariant in code." The integration tests are ordered
  (`@TestMethodOrder`) and only exercise sequential calls.
- **Architectural consequence:** Two concurrent `POST /api/setup` (or, later,
  two concurrent bootstrap claims) both pass the guard and both insert. The
  "exactly one first ADMIN" invariant would be violated across application
  instances. This is the naive check-then-act the brief calls out, and it is
  the highest-severity defect for the planned bootstrap.
- **Recommended correction:** Enforce at the data layer with a **singleton
  row**: a fixed, constant primary key (or a `CHECK (id = 1)`-style constraint)
  plus an atomic `INSERT … ON CONFLICT DO NOTHING` / `SELECT … FOR UPDATE`.
  Only the transaction that wins the claim proceeds to create the ADMIN. See
  §5 for the concrete model.

### HIGH

#### H-1 — Identity is keyed on OIDC `subject` only, not `issuer + subject`

- **Evidence:** `users.oidc_subject` is the sole identity key
  (`V001__users_initial.sql`, `UserEntity.oidcSubject` UNIQUE);
  `UserService.findOrProvision(String oidcSubject, String email, String name)`
  takes no issuer; `OidcCurrentUser` reads `principal.getName()` (subject) and
  never `jwt.getIssuer()`. The `User` domain object documents
  "oidcSubject — the authentication anchor" with no issuer concept.
- **Architectural consequence:** Subject (`sub`) is only stable and
  unambiguous **within** an issuer. If Capsa ever migrates issuer, supports a
  second provider, or a misconfigured client-id yields a different subject,
  subject-only keying risks collisions or a forced migration. Google's `sub`
  is stable per user, but the correct persistent key is the pair
  `(issuer, subject)`.
- **Recommended correction:** Add an `issuer` to the external identity model
  and persist `(issuer, subject)` as the unique key. Introduce an
  `ExternalIdentity { issuer, subject }` value type (or add `oidc_issuer`
  column to `users` with a UNIQUE(`oidc_issuer`, `oidc_subject`) constraint).
  Keep `email`/`name` as profile, not identity.

#### H-2 — No role / authorization model exists; `User` conflates identity with profile; `ADMIN` is undefined

- **Evidence:** No `role` column, no `Role` type, no `Invitation` entity
  anywhere in `server/` (grep returns nothing). The `User` aggregate carries
  `oidcSubject` (identity), `email` and `name` (profile) with no separation.
  The web client invented `InvitationRole = "USER" | "ADMIN"` as a string
  union in `web/src/api/invitations.types.ts`, with no server counterpart.
- **Architectural consequence:** Invitation role assignment, first-admin
  creation, and admin-only endpoints have no target model. The bootstrap
  privilege is at risk of being modeled as a permanent `ADMIN` flag rather than
  a distinct one-time mechanism. The frontend is already hard-coding a role
  taxonomy the domain does not own.
- **Recommended correction:** Add a minimal role model **before** auth work:
  a `Role` enum (`USER`, `ADMIN`) exposed through `capsa.users.api`, stored on
  `users` (or a small `user_roles` table), defaulting to `USER`. Model the
  bootstrap privilege **separately** from the `ADMIN` role (see §5). Move the
  role taxonomy out of the frontend string union into a shared, server-owned
  contract.

#### H-3 — `setup_state.owner_email` is a hardcoded-email placeholder that must not anchor bootstrap

- **Evidence:** `InitializeSetupCommand(String ownerEmail)` and
  `setup_state.owner_email NOT NULL` record "the intended installation owner"
  (`CAPSA-SETUP-BE-001-report.md` §"Minimum Setup Data"). The value is not
  linked to any `users` row and plays no role in authorization.
- **Architectural consequence:** Persisting an email as the install anchor
  invites exactly the hardcoded-email anti-pattern the brief forbids, and it
  cannot drive the "no administrator exists" check. It is also a PII field on
  a public-permit endpoint's write path.
- **Recommended correction:** Remove `owner_email` from the initialization
  contract. Initialization should be a bare state transition
  (initialized/not), with the owner established as a real `User` with `ADMIN`
  role via the authenticated bootstrap flow.

### MEDIUM

#### M-1 — Setup endpoints live outside the `/capsa/api/...` namespace

- **Evidence:** `SetupResource` uses `@Path("/api/setup")` while every other
  capability uses `@Path("/capsa/api/...")` (lists/items/captures). Setup is
  the only `permit`-policy endpoint and the only one outside the namespace.
- **Architectural consequence:** The public contract surface is split: clients
  (like `web/`, which hardcodes `apiNamespace = "/capsa/api"`) cannot reach
  setup through the same base+namespace rule and must special-case it. The
  bootstrap/session/invitation resources planned next will amplify this
  inconsistency if not reconciled now.
- **Recommended correction:** Decide and document one rule. Simplest: move
  setup/bootstrap status under `/capsa/api/...` (e.g. `/capsa/api/setup`,
  `/capsa/api/bootstrap`) so `web/`'s `apiNamespace` composes uniformly, and
  keep only the policy (`permit` vs authenticated) varying per endpoint.

#### M-2 — Invitation is a frontend-only contract with no domain, and the proposed response leaks token + acceptUrl

- **Evidence:** `web/src/api/invitations.ts` posts to
  `POST /capsa/api/invitations`, which does not exist server-side
  (`CAPSA-WEB-001-report.md` §"Backend-contract limitations"). The proposed
  response includes a raw `token` and an `acceptUrl`. No invitation domain
  concept exists in `docs/knowledge/domain` or `server/`.
- **Architectural consequence:** The invitation lifecycle (create → authenticate
  → redeem → activate user) is entirely undefined server-side, and the
  proposed shape implies the server knows its own public base URL to mint
  `acceptUrl` (a coupling to deployment that the server should not own). The
  role is a string union in the client, not a server type.
- **Recommended correction:** Before implementing invitations, define them as
  a server capability: invitation token stored **hashed** (not cleartext), with
  role, expiry, and one-time redemption; the client composes `acceptUrl` from
  its own base URL rather than receiving it from the server.

#### M-3 — Frontend has no authentication/bootstrap gate; `/admin-ui` renders unconditionally

- **Evidence:** `web/src/AppRoutes.tsx` maps `/admin-ui` directly to
  `AdminShell` with no auth guard, no OIDC client, and no bootstrap branch.
  `web/src/config.ts` reads only the API URL. `CAPSA-WEB-001-report.md`
  §"Backend-contract limitations" confirms "No OIDC bearer-token header is
  sent."
- **Architectural consequence:** When auth/bootstrap lands, the shell must be
  restructured to sit behind a gate, and the API client must gain token
  injection. None of that seam exists today, so the frontend slice will be a
  rework of routing + client rather than an additive change unless a gate seam
  is introduced first.
- **Recommended correction:** Decide gate placement now (entry gate **before**
  the shell, not inside it) and introduce an auth-context/token-provider seam
  (even stubbed) so the token can be attached in the API client later without
  touching pages.

#### M-4 — User provisioning race (`findOrProvision`) remains unresolved and is on the bootstrap hot path

- **Evidence:** `UserServiceImpl.findOrProvision` is check-then-insert
  (`findByOidcSubject` → `orElseGet` create), guarded only by the
  `oidc_subject` UNIQUE constraint. This was already flagged as M-3 in
  `CAPSA-ARCH-REVIEW-002` and is unchanged.
- **Architectural consequence:** First-login bursts, invitation redemption, and
  the first-admin creation all funnel through `findOrProvision`. A losing
  concurrent insert surfaces as an unhandled `PersistenceException` → 500,
  which for the bootstrap path becomes a confusing failure rather than a
  clean "already claimed" response.
- **Recommended correction:** Use an atomic `INSERT … ON CONFLICT DO NOTHING`
  + re-query (or catch-and-retry on unique violation) so provisioning is
  truly idempotent under concurrency. Fold this into the bootstrap slice since
  the first-admin creation depends on it.

---

## 5. First-Admin Bootstrap Recommendation

Recommended model (simplest secure form that works across multiple instances):

**Identity**
- Persistent identity = `(issuer, subject)`. Add `oidc_issuer` to `users` and
  a UNIQUE(`oidc_issuer`, `oidc_subject`) constraint. Keep `email`/`name` as
  profile fields.

**Bootstrap secret (`CAPSA_BOOTSTRAP_TOKEN`)**
- Exists **only** as external configuration. It is **never persisted** to the
  database and **never logged**. Compare against the configured value using a
  constant-time equals. Prefer configuring a **hash** of the token over the
  raw token, but a plain env value with constant-time compare is acceptable for
  v0.1.
- **Not** "removed after bootstrap" in the sense of deleting the env var; it
  simply becomes unusable because the "no ADMIN exists" guard fails. This is
  the zero-machinery choice.

**Bootstrap state (singleton row — the concurrency anchor)**
- Use a singleton row, e.g. `bootstrap_state(id INTEGER PRIMARY KEY CHECK (id = 1), claimed_at, claimed_by_user_id)`, or reuse a corrected `setup_state`
  with a fixed singleton key. The fixed key + a UNIQUE/CHECK constraint is
  what makes atomic claiming possible across instances.

**First-ADMIN creation (single transaction, atomic claim)**
```text
POST /capsa/api/bootstrap            (authenticated OIDC required)
  body: { "token": "..." }

  1. require authenticated identity → (issuer, subject) from the verified JWT
  2. constant-time verify token == CAPSA_BOOTSTRAP_TOKEN   (reject 403 otherwise)
  3. atomic claim:  INSERT INTO bootstrap_state(id) VALUES (1) ON CONFLICT DO NOTHING
       — if 0 rows inserted, another request already claimed it → 409 (idempotent)
  4. within the SAME transaction: create/find the User for (issuer, subject)
       and assign role = ADMIN
  5. commit
```

**Concurrency behavior**
- The `ON CONFLICT DO NOTHING` on the singleton row is the invariant. Exactly
  one transaction can insert the row; every other concurrent request (even on a
  different app instance, since PostgreSQL is the single coordinator) sees 0
  rows affected and returns 409. No `SELECT COUNT(*) WHERE role = 'ADMIN'`
  read-then-write is used as the guard — the row claim is the guard.

**Bootstrap closure**
- Bootstrap is closed **by the presence of an ADMIN** and the claimed
  singleton row. A subsequent authenticated request either has no admin
  privileges (rejected by normal authorization) or is already an ADMIN.
  `GET /capsa/api/bootstrap/status` reports `{ "required": true|false }` where
  `required = (no ADMIN exists)`. It must **not** disclose whether a token is
  configured or valid.

**Failure modes / idempotency**
- Wrong token → 403, no state change. Already bootstrapped → 409, no state
  change. Concurrent claims → exactly one 201, the rest 409. If step 4 fails
  after the row claim, the transaction rolls back the claim (row insert is in
  the same transaction), leaving the install still bootstrappable.

**Deployment / operations (see §9)**
- Warn at startup if `CAPSA_BOOTSTRAP_TOKEN` is set but bootstrap is already
  consumed; warn if it is unset while no ADMIN exists. Do not require the
  token to be present post-bootstrap.

---

## 6. Auth / Invitation Interaction

Recommended lifecycle:

```text
Bootstrap (one-time, §5):
  OIDC identity (issuer,sub) + valid token + no ADMIN
    → create User(ADMIN) → close bootstrap

Normal onboarding:
  ADMIN ──creates──► Invitation { token(hash), role, expiresAt }
                         │
  Invitee ──Google login──► authenticated (issuer, sub)   [User may not exist yet]
                         │
  Invitee ──redeems invitation──► verify token (hash) · not expired · not used
                         │
                         ▼
                 provision User for (issuer, sub)
                 assign role from invitation
                 mark invitation consumed (one-time)
```

Key separations to preserve:
- **Authentication** (OIDC) is orthogonal to **authorization** (role) and to
  **onboarding** (invitation redemption). A person can authenticate before
  holding any Capsa role.
- **Invitation carries role**, not identity. It must **not** be tied to a
  specific email or a pre-bound Google identity; redemption binds the
  invitation to whoever authenticates and presents the valid token.
- **Invitation token is hashed** at rest; redemption is one-time and
  atomic (unique token + consumed flag/row delete in a transaction).
- **First admin** is a `User` with `ADMIN` role, not a special flag. The
  bootstrap privilege (token + no-admin) is separate from the `ADMIN` role.

---

## 7. Backend Follow-up Slice

**Scope for the next small backend task (model foundation + bootstrap claim):**

1. **Identity/role model change** (in `capsa-users`, migration `V008`):
   add `oidc_issuer` to `users` + UNIQUE(`oidc_issuer`, `oidc_subject`); add a
   `Role` (`USER`/`ADMIN`) to the User model (`users.role`, default `USER`),
   exposed via `capsa.users.api`. Update `UserService.findOrProvision` to key
   on `(issuer, subject)` and make it concurrency-safe (atomic upsert).
2. **Replace `capsa-setup`'s `ownerEmail` flow**: split "initialized" state
   from bootstrap. Correct the singleton-row concurrency invariant (fixed key +
   `ON CONFLICT DO NOTHING`).
3. **Add the bootstrap claim endpoint** `POST /capsa/api/bootstrap`
   (authenticated, token check, atomic claim, create first ADMIN) and
   `GET /capsa/api/bootstrap/status`, both under the `/capsa/api/` namespace,
   reusing the existing error model and `CurrentUser` seam.

**Explicitly out of scope for this slice:** Google/OIDC provider configuration
beyond reading the existing JWT issuer/subject (Quarkus OIDC already validates
the token); invitation endpoints; session/current-user endpoint; frontend
changes.

The Google login itself does not need backend work — Quarkus OIDC already
validates the Bearer token; the backend slice only needs to *consume* the
verified `(issuer, subject)`.

---

## 8. Frontend Follow-up Slice

**Scope for the next small frontend task (admin auth + first-run bootstrap UX):**

1. **Introduce an entry gate before the shell** (not inside it): a
   `BootstrapGate`/`AuthProvider` that, on load, calls
   `GET /capsa/api/bootstrap/status`. If `required === true`, route to a
   first-run flow; otherwise render the existing `AdminShell`.
2. **First-run flow** (UI only): "Sign in with Google" (OIDC redirect or a
   stub until the provider is wired) → token entry field → submit
   `POST /capsa/api/bootstrap {token}`. Surface 403/409 from the existing
   error model; on 201, transition into the normal shell.
3. **Token-provider seam in the API client**: add the place where a Bearer
   token will be attached to requests (extend `createInvitation`'s fetch), so
   invitation creation works once the server ships its endpoint and auth is
   enforced — without touching presentation code.
4. **Stop hard-coding the role taxonomy**: read `Role` from the server-owned
   contract instead of the `"USER" | "ADMIN"` string union where feasible (at
   minimum centralize it in one type file pending the server contract).

**Explicitly out of scope:** real Google OIDC SDK integration (can be a stub),
invitation redemption/accept UI, invitation listing, session management.

---

## 9. Deferred Concerns (do NOT solve in the next two slices)

- **Email/domain allow-listing** for the first admin (beyond the bootstrap
  token) — not required by the brief; the token is the gate.
- **Multi-tenant / multiple issuers** — model `(issuer, subject)` now so this
  is possible later, but do not build multi-issuer support yet.
- **Session/current-user REST endpoint** — authentication state lives in the
  JWT on each request; a `/me` endpoint is a later convenience, not a
  prerequisite.
- **Invitation emailing, accept links, rate limiting, token rotation** —
  invitations are a later slice; only the *model* must be decided now.
- **Keycloak/self-hosted IdP vs Google** — the server is already provider
  agnostic via Quarkus OIDC; defer any provider-specific wiring.
- **Vault / K8s secret infra** — `CAPSA_BOOTSTRAP_TOKEN` via env/Coolify is
  sufficient for v0.1; no secret infrastructure.
- **Startup warnings** about unused/missing bootstrap token — cheap and useful,
  but can land after the core bootstrap works.

---

## Validation

No code was modified as part of this review. Findings are grounded in:

- `server/capsa-setup/.../SetupServiceImpl.java`, `SetupRepository.java`,
  `SetupResource.java`, `InitializeSetupCommand.java`, `SetupStatus.java`
- `server/capsa-runtime/.../application.properties`,
  `.../security/OidcCurrentUser.java`, `.../error/*Mapper.java`
- `server/capsa-runtime/src/main/resources/db/migration/V001__users_initial.sql`,
  `V007__setup_initial.sql`
- `server/capsa-users/.../User.java`, `UserEntity.java`, `UserServiceImpl.java`,
  `UserService.java`, `CurrentUser.java`, `UserView.java`, `module-info.java`
- `web/src/App.tsx`, `AppRoutes.tsx`, `config.ts`, `components/AdminShell.tsx`,
  `api/invitations.ts`, `api/invitations.types.ts`
- `docs/knowledge/architecture/capsa-arch-001-modular-monolith-v0.1.md`,
  `docs/knowledge/domain/capsa-domain-model-v0.1.md`
- `docs/engineering/agents/reports/CAPSA-SETUP-BE-001-report.md`,
  `CAPSA-WEB-001-report.md`, `CAPSA-REPO-001.md`, `CAPSA-ARCH-REVIEW-002.md`,
  `S-06-report.md`
- `docker-compose.yml`, `docker-compose.prod.yml`, `.env.example`
