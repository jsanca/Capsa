# CAPSA-WEB-001 — Bootstrap React Admin UI and Invitation Creation — Report

## Status

Complete. The web boundary now contains a working Vite + React + TypeScript application that
serves an administrative shell at `/admin-ui` with one implemented slice: invitation
creation.

## Objective

Per task [CAPSA-WEB-001](../../tasks/CAPSA-WEB-001.md):

- Bootstrap `web/` with TypeScript, React, and Vite — smallest conventional setup sufficient
  for the slice.
- Stand up an administrative shell under `/admin-ui`.
- Implement an invitation creation flow that consumes the Capsa Server public REST API.
- Cover the eight acceptance scenarios with component/integration tests.

## Summary

The web client is a Vite + React 18 + TypeScript application. Routing uses
`react-router-dom` v6 with the classic `BrowserRouter` + `<Routes>` API. Testing uses
Vitest, `@testing-library/react` + `@testing-library/user-event`, and MSW v2 for HTTP
interception. Styling is plain CSS — no design system.

The administrative shell renders at `/admin-ui`, lists one navigation entry (Invitations), and
hosts the invitation creation page at `/admin-ui/invitations/new`. The page posts to
`POST /capsa/api/invitations` against the configured server base URL.

**The Capsa Server does not yet expose an invitation endpoint.** The frontend targets the
proposed contract documented below. Tests run against MSW handlers that emulate the
contract. Once the server ships the endpoint, the frontend is expected to work without
changes. The proposal is captured here as a missing-contract record.

## Project structure

```text
web/
├── index.html
├── package.json
├── tsconfig.json
├── tsconfig.node.json
├── vite.config.ts
├── .env.example
├── .gitignore
├── public/
└── src/
    ├── main.tsx
    ├── App.tsx                  # BrowserRouter wrapper
    ├── AppRoutes.tsx            # route definitions (testable in isolation)
    ├── config.ts                # reads VITE_CAPSA_API_URL
    ├── styles.css
    ├── api/
    │   ├── invitations.ts        # createInvitation() — typed REST client
    │   └── invitations.types.ts  # request / response / error shapes
    ├── components/
    │   └── AdminShell.tsx
    ├── pages/
    │   ├── AdminHomePage.tsx
    │   └── InvitationNewPage.tsx
    └── test/
        ├── setup.ts              # vitest + jest-dom + MSW lifecycle
        ├── handlers.ts           # MSW handlers + helpers
        ├── admin-routing.test.tsx
        └── invitations-api.test.ts
```

## Bootstrap choices

- **Build tool:** Vite 5. Picked over Webpack / Turbopack for the lowest-cost modern tooling
  with native ESM, fast HMR, and a built-in Vitest integration via `vite.config.ts`.
- **Language:** TypeScript with `strict`, `noUnusedLocals`, `noUnusedParameters`,
  `noFallthroughCasesInSwitch`, and `noUncheckedIndexedAccess` enabled.
- **Framework:** React 18 with the new JSX runtime; `React.StrictMode` is enabled in
  development.
- **Routing:** `react-router-dom` v6.26 using the classic `BrowserRouter` + `<Routes>`
  pattern. The data router (`createBrowserRouter`) was deliberately not used: it eagerly
  constructs `Request` objects via `@remix-run/router`, which collides with MSW v2's
  `ClientRequest` interceptor under `jsdom`. The classic API produces no such calls and
  tests run without unhandled rejections.
- **State:** `useState` only. No state-management framework.
- **Styling:** Plain CSS in `src/styles.css`. No CSS framework or design system. The
  structure is intentionally minimal so a future Capsa visual system can replace it
  without touching behavior.
- **Testing:** Vitest (jsdom env) + `@testing-library/react` +
  `@testing-library/user-event` + MSW v2 (`msw/node` for jsdom interception).

## Routing

| Path | Component |
| --- | --- |
| `/` | `<Navigate to="/admin-ui" replace />` |
| `/admin-ui` | `AdminShell` with `AdminHomePage` as index |
| `/admin-ui/invitations/new` | `AdminShell` with `InvitationNewPage` |
| `*` | `<Navigate to="/admin-ui" replace />` |

`App.tsx` wraps `AppRoutes` in a `BrowserRouter` for production. Tests import
`AppRoutes` directly and wrap it in `MemoryRouter`, avoiding nested-router errors.

## REST integration

The client targets a single endpoint:

```text
POST {VITE_CAPSA_API_URL}/capsa/api/invitations
Content-Type: application/json

Request:
{ "role": "USER" | "ADMIN", "expiresAt": "2026-12-31T23:59:59Z" }   // expiresAt optional

Response 201:
{
  "invitationId": { "value": "uuid-string" },
  "role": "USER" | "ADMIN",
  "expiresAt": "ISO-8601" | null,
  "token": "string",
  "acceptUrl": "string"
}

Error responses (the existing Capsa pattern):
422  { "code": "CAPSA_VALIDATION_ERROR",  "message": "..." }
403  { "code": "CAPSA_FORBIDDEN",         "message": "..." }
409  { "code": "CAPSA_INVITATION_CONFLICT","message": "..." }
```

The request/response/error shapes live in `src/api/invitations.types.ts`. `createInvitation`
in `src/api/invitations.ts` is the single client entry point. It never constructs endpoint
paths from presentation components.

Error mapping:

| HTTP | Body code | Thrown `CapsaApiError.code` |
| --- | --- | --- |
| 201 | — | (no throw) |
| 422 | `CAPSA_VALIDATION_ERROR` | `CAPSA_VALIDATION_ERROR` |
| 403 | `CAPSA_FORBIDDEN` | `CAPSA_FORBIDDEN` |
| 401 | `CAPSA_UNAUTHORIZED` | `CAPSA_UNAUTHORIZED` |
| 409 | `CAPSA_INVITATION_CONFLICT` | `CAPSA_INVITATION_CONFLICT` |
| any | unknown / non-JSON | `CAPSA_VALIDATION_ERROR` with `httpStatus` set |
| network failure | — | `NETWORK_ERROR` with `httpStatus: 0` |

## Invitation flow

`/admin-ui/invitations/new` renders a form (`role` select, optional `expiresAt`
`datetime-local`) and a result panel.

State machine:

```text
idle
  → submit → validate (role required, expiresAt valid Date.parse)
  → invalid → stay idle, surface field errors, do NOT call API
  → valid → submitting (disable submit, change label to "Creating…")
  → 201    → idle (render returned id, role, expiresAt, token, acceptUrl)
  → 4xx    → idle (render server message via role="alert")
  → network→ idle (render "Network error: …")
```

On any failure the form values remain in state. The next edit clears both the field error
and the prior alert so the user can retry without losing their input.

Accessibility highlights:

- `<label>` elements tied to inputs via `htmlFor` (React `useId` for stable IDs).
- `aria-invalid` + `aria-describedby` for fields in error states.
- `role="alert"` for error messages; `aria-live="polite"` for the success summary.
- Visually hidden "(required)" annotation on the role label.
- Disabled submit button + label change while submitting.
- Visible focus rings on every interactive element.

The result panel never invents fields: only `invitationId.value`, `role`, `expiresAt` (when
present and non-null), `token`, and `acceptUrl` are rendered, exactly as returned by the
server.

## Environment configuration

- `VITE_CAPSA_API_URL` — base URL of the Capsa Server, with no trailing slash. The app
  throws at startup if unset. Default for tests: `http://localhost:8080`.
- `.env.example` documents the variable. `.env` is gitignored.

## Files changed / added

- `web/package.json`, `web/package-lock.json` — created.
- `web/tsconfig.json`, `web/tsconfig.node.json` — created.
- `web/vite.config.ts` — created with React plugin + Vitest config + test env defaults.
- `web/index.html`, `web/.env.example`, `web/.gitignore` — created.
- `web/src/main.tsx`, `web/src/App.tsx`, `web/src/AppRoutes.tsx`, `web/src/config.ts`,
  `web/src/styles.css` — created.
- `web/src/api/invitations.ts`, `web/src/api/invitations.types.ts` — created.
- `web/src/components/AdminShell.tsx` — created.
- `web/src/pages/AdminHomePage.tsx`, `web/src/pages/InvitationNewPage.tsx` — created.
- `web/src/test/setup.ts`, `web/src/test/handlers.ts`,
  `web/src/test/admin-routing.test.tsx`, `web/src/test/invitations-api.test.ts` —
  created.
- `web/README.md` — replaced the empty-boundary stub with the implementation description.
- `docs/engineering/ENGINEERING_LOG.md` — added the CAPSA-WEB-001 row.
- `docs/engineering/agents/reports/CAPSA-WEB-001-report.md` — this file.

## Validation

```text
$ npm install        # 230 packages, no audit warnings beyond standard deprecations
$ npm test           # 16 passed (2 files)
$ npm run build      # tsc -b && vite build → dist/ produced
```

Test files:

- `src/test/admin-routing.test.tsx` (12 tests) — covers scenarios 1, 2, 3, 4, 5, 6, 7, 8
  from the task acceptance criteria.
- `src/test/invitations-api.test.ts` (4 tests) — covers `createInvitation` happy path, 422,
  403, and network failure at the API layer.

Mapping to task acceptance criteria:

| # | Scenario | Test |
| --- | --- | --- |
| 1 | Admin application renders under `/admin-ui` | `admin-routing > renders the admin shell under /admin-ui` |
| 2 | Invitation screen can be reached | `admin-routing > renders the invitation screen at /admin-ui/invitations/new` |
| 3 | Required fields are validated | `InvitationNewPage > requires a role to be selected before submission` |
| 4 | Valid form data generates the correct API request | `InvitationNewPage > submits a valid form, sends the expected request body…` |
| 5 | Submission enters a pending state | `InvitationNewPage > disables the submit button while the request is in flight` |
| 6 | Successful invitation creation displays confirmation | same test as 4 (asserts result fields) |
| 7 | Backend validation error is presented | `InvitationNewPage > displays backend 422 validation errors and preserves the form state` |
| 8 | Network/server failure without destroying form state | `InvitationNewPage > displays a network error…` and `displays an unexpected server failure and recovers` |

## Backend-contract limitations discovered

1. **The invitation endpoint does not exist on the server.** A grep of `server/` for
   "invitation" / "Invitat" returns no matches. Existing public endpoints are
   `/capsa/api/lists`, `/capsa/api/items`, and `/capsa/api/captures`. The frontend targets
   a proposed `POST /capsa/api/invitations` contract described above. The proposed shapes
   mirror existing server patterns (`POST` → 201, validation failures → 422 with
   `{ "code": "CAPSA_VALIDATION_ERROR", "message": "..." }`).
2. **No domain definition for invitations.** `docs/knowledge/domain/capsa-domain-model-v0.1.md`
   covers Users, Lists, Items, Captures, and Classification but does not include an
   Invitation concept. The proposed `role` is encoded as a string literal union
   (`"USER" | "ADMIN"`) in the frontend to avoid premature commitment to a server-side
   taxonomy.
3. **No OIDC bearer-token header is sent.** The frontend uses unauthenticated `fetch` only.
   When the server ships the endpoint and OIDC is enforced, the client will need to
   acquire an access token and attach it as `Authorization: Bearer …`. The API client is
   the single place to add this; presentation code does not construct requests.

These limitations are tracked as the slice's contract surface, not as server changes. No
server-side code is modified by this task.

## Non-goals confirmed

The task explicitly excluded invitation acceptance, listing, user/role management, the
public Capsa UI, mobile UI, authentication redesign, and a complete admin dashboard. None
of these were introduced.

## Limitations and follow-ups

- The frontend does not yet acquire an OIDC access token. When the server adds the endpoint
  and requires auth, extend `createInvitation` to read a token from an injectable source
  and forward it as a Bearer header.
- `Accept` and `invitations/list` flows are intentionally out of scope for this slice.
- The `AdminShell` is intentionally minimal — it can host additional navigation entries
  via the existing `AdminNav` pattern without further structural changes.
- A future task should add the server-side `capsa-invitations` capability module (or
  equivalent) and migrate the proposed contract from this report into a canonical public
  REST contract under `docs/knowledge/architecture/`.