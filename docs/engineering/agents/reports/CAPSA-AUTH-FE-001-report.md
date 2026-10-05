# CAPSA-AUTH-FE-001 — Admin Bootstrap Gate and Authentication Client Seam — Report

## Status

Complete. The web boundary now satisfies the frontend half of the architecture
recommendation in [CAPSA-ARCH-REVIEW-003](CAPSA-ARCH-REVIEW-003.md) §7–§8
(M-3, H-2, frontend recommendation §8). The Capsa Server side is captured in
[`server/capsa-bootstrap/`](../../../../server/capsa-bootstrap/) (see §"Server-side contract"
below); the slice ships the frontend described there.

## Objective

Per task input:

- Place a gate before `AdminShell` that consults the server for bootstrap state.
- Provide a first-run flow that submits `{ "token": "..." }` to the public
  Capsa bootstrap endpoint through a centralized API/auth seam.
- Make the existing invitation UI ready to receive a bearer token without
  page-level changes.
- Centralize the role taxonomy so the frontend does not own it independently.
- Cover all 13 acceptance scenarios.

## Summary

`AdminShell` no longer renders at `/admin-ui` directly. A new
`AdminGateOutlet` consults the authoritative server state via
`GET /capsa/api/bootstrap/status`. When bootstrap is required, the gate
presents the first-run flow. When bootstrap is closed, the gate renders
`AdminShell`, which itself wraps the existing `/admin-ui/invitations/new`
route.

A new `AuthProvider` seam (`auth/AuthProvider.ts`) provides
`getAccessToken() / signIn() / signOut()` to the single outbound HTTP
boundary in `api/apiCall.ts`. The default implementation
`StubAuthProvider` resolves immediately with `VITE_CAPSA_STUB_ACCESS_TOKEN`
so the UI can drive the bootstrap flow without a real OIDC SDK. Swapping
the stub for `GoogleOidcAuthProvider` (or equivalent) is a single-file
change; presentation code never constructs the `Authorization` header.

The invitation page already routes its POST through `createInvitation` →
`apiCall`, so the existing slice inherits the bearer-token injection
automatically once a real provider is wired.

The role taxonomy has been moved out of the invitations client into a
single domain module (`domain/roles.ts`), explicitly marked as
server-owned. The frontend references the constants only to label the
user-facing dropdown; no authorization decision is ever made locally.

## Route / gate structure

```text
                          ┌────────────────────────────────────────────────────────┐
   /admin-ui/...          │ <AdminGateOutlet>                                       │
   ─────────────────►     │   useBootstrapGate() → state ∈ {checking, required,     │
                          │                       ready, error}                    │
                          │                                                        │
                          │   state.kind === "required"                            │
                          │     → <AdminEntryGate>  (first-run BootstrapPanel)    │
                          │   state.kind === "ready"                               │
                          │     → <AdminShell>                                     │
                          │         → <Outlet/> → AdminHome | InvitationNewPage  │
                          │   state.kind === "checking" | "error"                 │
                          │     → loading / recoverable error panel                │
                          └────────────────────────────────────────────────────────┘
```

`AdminShell` is no longer the route element for `/admin-ui`; `AdminGateOutlet`
is. The decision to render the shell or the gate lives entirely outside the
shell, per Deep's §8 recommendation.

## Auth-token seam

```text
┌───────────────────────────────┐
│ AuthProvider  (interface)     │
│  • getAccessToken()           │   ┌──────────────────────────────┐
│  • signIn()                   │   │ StubAuthProvider  (default)  │
│  • signOut()                  │   │   no I/O, no persistence     │
└───────────────────────────────┘   │   token ← env var             │
                ▲                    └──────────────────────────────┘
                │ uses (singleton)
                │
┌───────────────────────────────┐
│ api/apiCall.ts                │   ←  single outbound HTTP boundary
│  - reads token from provider  │
│  - adds "Authorization: …"   │
│  - parses {code, message}     │
│  - throws typed CapsaApiError │
└───────────────────────────────┘
                ▲
                │ used by
                │
   ┌────────────┴────────────┐
   │                         │
api/bootstrap.ts      api/invitations.ts
```

`apiCall` is the **only** place that constructs outbound HTTP. Presentation
components import `createInvitation` / `claimBootstrap` and never read or
attach `Authorization`. Test #13 enforces this structurally by checking the
source of `InvitationNewPage` for `Authorization`, `Bearer`,
`getAccessToken`, `signIn`, or `signOut` (none appear).

## Bootstrap API interaction

```text
GET  /capsa/api/bootstrap/status    →  200 { required: boolean, claimedAt: string | null }
POST /capsa/api/bootstrap           →  201 { required: false, claimedAt: "..." } | 401 | 403 | 409 | 422
```

`getBootstrapStatus` is sent **without** `Authorization` (the server
permit-policies this endpoint). `claimBootstrap` is sent **with** the bearer
token acquired from the active `AuthProvider`. The request body is exactly
`{ "token": "..." }`; no email, role, or administrator identity is sent from
the frontend — the server derives identity from the verified OIDC token.

Bootstrap token lifecycle:

- held in local React `useState` only;
- `setToken("")` after every server response (success or failure);
- never written to `sessionStorage`, `localStorage`, or any persistent store;
- never logged (`apiCall` does not log headers or bodies; the form input is
  `type="password"` with `autoComplete="off"` and `spellCheck="false"`).

Test #12 enforces this by setting the input to a sentinel value, submitting,
and asserting that the sentinel does not appear in the rendered DOM, the
input is unmounted, and `localStorage` / `sessionStorage` remain empty.

## State transitions

`useBootstrapGate` exposes a `state.kind` discriminated union with these
transitions:

```text
        ┌──────────┐  ok({required:true})   ┌──────────┐
   on   │ checking ├────────────────────────►│ required │
  mount └────┬─────┘                         └────┬─────┘
       │   └──────┐                                 │
       │          │ fetch error (network/server)  │ submitToken(token)
       │          ▼                                 ▼
       │   ┌──────────┐                    ┌──────────────┐
       │   │  error   │◄────── 5xx ────────│  submitting  │
       │   └────┬─────┘                    └──────┬───────┘
       │        │ retry                              │
       │        ▼                                    │
       │   back to checking                          │
       │                                             │
       │   ┌──────────────────────────────┐         │
       └──►│  ready   (AdminShell renders)│◄────────┘
           └──────────────────────────────┘    201 (re-queries status, may transition
                                                directly to ready from required=false)
                                              409 (bootstrap closed — direct transition
                                                   to ready; server is authoritative)
```

The 409 → ready path is intentional. CAPSA-ARCH-REVIEW-003 §5 stipulates
"already bootstrapped → 409, no state change." For the gate UX the
authoritative meaning is "bootstrap is closed" — the user must see the
admin shell. The gate therefore transitions directly to `ready` on 409
rather than entering a dead-end loop of `required → claim → 409 → required`.

## Files added or modified

- `web/src/auth/AuthProvider.ts`, `StubAuthProvider.ts`, `providerHolder.ts`,
  `useActiveAuthProvider.ts`, `useBootstrapGate.ts`, `authEvents.ts`, `index.ts`
  — created.
- `web/src/api/apiCall.ts`, `api/errors.ts`, `api/bootstrap.ts`,
  `api/invitation.types.ts` — created.
- `web/src/api/invitations.ts` — refactored to use `apiCall`; no longer
  touches `fetch` directly.
- `web/src/api/invitations.types.ts` — slimmed to resource-specific shapes
  (`InvitationId`); request/response/error shapes moved to dedicated modules.
- `web/src/domain/roles.ts` — created; centralized server-owned role taxonomy.
- `web/src/components/AdminEntryGate.tsx`, `AdminGateOutlet.tsx` — created.
- `web/src/AppRoutes.tsx` — `/admin-ui` now uses `AdminGateOutlet`.
- `web/src/pages/InvitationNewPage.tsx` — role union imported from
  `domain/roles`; no auth logic added.
- `web/src/test/handlers.ts` — added `GET /capsa/api/bootstrap/status` and
  `POST /capsa/api/bootstrap` MSW handlers with status/claim overrides and
  Authorization-header recording.
- `web/src/test/admin-entry-gate.test.tsx` — 15 new tests covering the gate.
- `web/src/test/admin-routing.test.tsx` — `renderAt` updated to await the
  gate's initial status transition before assertions.
- `web/.env.example` — documented `VITE_CAPSA_STUB_ACCESS_TOKEN`.
- `web/README.md` — replaced with the implementation description.
- `docs/engineering/agents/tasks/CAPSA-AUTH-FE-001.md` — task record.
- `docs/engineering/agents/reports/CAPSA-AUTH-FE-001-report.md` — this file.
- `docs/engineering/ENGINEERING_LOG.md` — row added.

## Tests

```text
$ npm test   # 31 passed (3 files)
```

Mapping to acceptance criteria:

| # | Scenario | Test |
| --- | --- | --- |
| 1 | `/admin-ui` enters the gate before rendering `AdminShell` | `enters the gate before rendering AdminShell` |
| 2 | Bootstrap status loading state | `renders the loading state while the bootstrap status is in flight` |
| 3 | `required = true` renders the first-run flow | `renders the first-run flow when status.required is true` |
| 4 | `required = false` renders the normal admin shell | `renders the admin shell when status.required is false` |
| 5 | Bootstrap token submitted to the correct endpoint | `submits the bootstrap token to the correct endpoint` |
| 6 | Bearer token attached through the centralized API seam | `attaches the bearer token from the AuthProvider through the centralized seam` |
| 7 | 403 displays an invalid-token error | `displays an invalid-token error on 403` |
| 8 | 409 transitions to admin shell | `displays a bootstrap-already-claimed state on 409 and transitions out` |
| 9 | 401 returns the user to authentication-required state | `returns the user to the authentication-required state on 401` |
| 10 | Network/server error recoverable | `surfaces a recoverable error on network failure` (and `surfaces a recoverable error when bootstrap status fetch fails`) |
| 11 | Successful bootstrap transitions into admin shell | `transitions into the admin shell on a successful bootstrap` |
| 12 | Bootstrap token not persisted client-side | `does not persist the bootstrap token in storage or state after submission` |
| 13 | Invitation presentation components do not acquire direct auth/header logic | `does not let presentation components construct Authorization headers directly` (structural: source of `InvitationNewPage` never references `Authorization`, `Bearer`, or `AuthProvider`) |

## Server-side contract

The server already implements:

- `GET /capsa/api/bootstrap/status` (`server/capsa-bootstrap/.../BootstrapResource.java:34-39`)
  → `200 { required, claimedAt }`. This endpoint is `permit`-policy.
- `POST /capsa/api/bootstrap` (`BootstrapResource.java:41-52`)
  → `201 { required: false, claimedAt: "..." }` on success, `422` on missing
  token, default Quarkus-401 on missing/invalid bearer token. Other exceptions
  (`BootstrapAlreadyClaimedException`, `BootstrapTokenInvalidException`)
  currently fall through to `FallbackExceptionMapper` → `500`.

Per CAPSA-ARCH-REVIEW-003 §5, `POST /capsa/api/bootstrap` should return `403`
on an invalid token and `409` on an already-claimed singleton. No
`ExceptionMapper` for `BootstrapTokenInvalidException` or
`BootstrapAlreadyClaimedException` is present in `server/capsa-runtime/.../error/`.
This is a **documented limitation** of the current server: the frontend
handles `403` and `409` correctly when they appear, but until mappers are
added, claim errors surface as "Server error" in the gate. The
remediation is server-side and outside this slice's scope.
**[RESOLVED in CAPSA-AUTH-FIX-001]** Exception mappers now exist in `capsa-runtime`. `BootstrapTokenInvalidException` maps to `403` with code `CAPSA_BOOTSTRAP_INVALID_TOKEN`; `BootstrapAlreadyClaimedException` maps to `409` with code `CAPSA_BOOTSTRAP_ALREADY_CLAIMED`. The frontend handles both codes correctly.

## Security notes

- The bootstrap token is a password input (`type="password"`,
  `autoComplete="off"`, `spellCheck="false"`). It is not logged, not
  reflected in the DOM after submission, and not persisted.
- `VITE_CAPSA_STUB_ACCESS_TOKEN` is a literal string in `import.meta.env`
  for the development stub. Vite embeds `VITE_*` values in the production
  bundle at build time; **the stub token must not be set in production
  builds**. The `.env.example` documents this explicitly.
- The frontend never reads `bootstrap_state.owner_email` (which is no
  longer a thing — see CAPSA-ARCH-REVIEW-003 H-3) and never decides who
  is `ADMIN` locally. The server derives identity from the OIDC token.
- No administrator email is hardcoded; no `Authorization: rule` header is
  constructed locally; `CAPSA_BOOTSTRAP_TOKEN` is not exposed to the
  frontend by either reading the attribute or accepting a derived value.
- The gate never returns a 200/201 vs other distinction locally — the
  server is the only source of truth for `required`.

## Intentional deviations from the recommendation

None. The slice implements §7–§8 of CAPSA-ARCH-REVIEW-003 verbatim:

- Entry gate before the shell (not inside it) ✓
- First-run flow with "Sign in with Google" placeholder + token entry ✓
- Token-provider seam in the API client (single `apiCall` boundary) ✓
- Role taxonomy centralized in `domain/roles.ts` and marked as
  server-owned ✓
- Real Google OIDC SDK wiring explicitly **out of scope** per §8 ✓

## Limitations and follow-ups

- ~~The server lacks `ExceptionMapper`s for `BootstrapAlreadyClaimedException`
  and `BootstrapTokenInvalidException`. Once added, the existing `403`/`409`
  handlers light up automatically.~~ **[RESOLVED in CAPSA-AUTH-FIX-001]** Exception mappers now exist in `capsa-runtime`.
- `GET /capsa/api/bootstrap/status` currently returns `{ required, claimedAt }`
  regardless of whether a token is configured server-side. Per
  CAPSA-ARCH-REVIEW-003 §5, the status response must not disclose whether
  `capsa.bootstrap.token` is set. The frontend does not assume anything
  about that, but a future hardening should ensure the server omits
  token-state information from this payload.
- The invitation slice continues to display `token` and `acceptUrl` in the
  result panel as documented in CAPSA-WEB-001 §"REST integration". Per
  CAPSA-ARCH-REVIEW-003 M-2, the server should hash the invitation token
  at rest and the client should compose `acceptUrl` from its own base URL.
  This is an invitation slice concern, not an auth slice concern, and is
  deferred to the invitation server-side slice.
- When a real OIDC provider is wired, replace `StubAuthProvider` with
  `GoogleOidcAuthProvider` (or equivalent) and add an env var for the
  client id. No other code changes are required.