# Capsa — Web Client

## Status

**Implemented.** Bootstrap complete with an administrative slice under `/admin-ui`
(invitation creation) and a first-run bootstrap gate (CAPSA-AUTH-FE-001).

## Stack

- TypeScript (strict)
- React 18
- Vite 5 (build + dev server)
- `react-router-dom` v6 (routing — `BrowserRouter` + `<Routes>`)
- Vitest + `@testing-library/react` + `@testing-library/user-event` + MSW v2 (testing)

No SSR, no meta-framework, no global state library. Styling is plain CSS in `src/styles.css`.

## Commands

All commands run from `web/`:

```bash
npm install
npm run dev          # vite dev server on http://localhost:5173
npm test             # vitest run (one-shot)
npm run test:watch   # vitest in watch mode
npm run build        # tsc -b && vite build → production assets in dist/
npm run preview      # preview the production build
```

## Environment configuration

The Capsa Server base URL is read from `VITE_CAPSA_API_URL`. Copy `.env.example` to `.env`
and adjust for local / deployed environments. The variable must be set — the app throws at
startup if it is missing.

```bash
VITE_CAPSA_API_URL=http://localhost:8080                   # local docker-compose stack
VITE_CAPSA_API_URL=https://capsa.example.com              # production

# Development/stub OIDC access token used by the stub AuthProvider.
# The stub provider does NOT verify this token; the backend only trusts it when
# OIDC is disabled (%dev / %test profiles). Leave blank in %prod.
VITE_CAPSA_STUB_ACCESS_TOKEN=
```

`.env`, `.env.local`, and `node_modules` are gitignored.

## Admin entry gate

Every request to `/admin-ui` is processed by an entry gate before any shell
chrome is rendered. On mount the gate calls:

```text
GET /capsa/api/bootstrap/status
```

and selects one of four behaviors:

| Server response              | UI                                              |
| -------------------------- | ---------------------------------------------- |
| `required: true`           | First-run bootstrap flow                       |
| `required: false`          | The existing `AdminShell` (with nested routes) |
| Network failure            | Recoverable error with retry                   |
| Any other server failure      | Recoverable error with retry                   |

The first-run flow contains:

1. A **Sign in with Google** button that delegates to the active
   `AuthProvider`. Real Google OIDC SDK wiring is **not** included in this
   slice; the development stub resolves the call without I/O so the UI can
   drive the bootstrap flow.
2. A password-style **Bootstrap token** field with a hint that the value is
   configured server-side, sent once, and not stored in the browser.
3. An **Initialize Capsa** submit that POSTs `{ "token": "..." }` to
   `/capsa/api/bootstrap` through the centralized API seam.

Result mapping:

| Server response | UI transition |
| --------------- | ------------- |
| 201             | Re-query `/capsa/api/bootstrap/status`; transition to `AdminShell` on `required: false`. |
| 401             | "Authentication required" — prompt to sign in again. |
| 403             | "Invalid token" — the server rejected the bootstrap secret. |
| 409             | "Already claimed" — transition directly to `AdminShell`. |
| 422             | "Server error" — recoverable. |
| Network failure | "Network error" — recoverable. |

The bootstrap token is **local form state only**; it is cleared on every
response, never written to `sessionStorage`, `localStorage`, or any persistent
store, and never logged.

## Routes

The application root redirects to the administrative slice:

- `/admin-ui` — entry gate; resolves to `AdminHome` once bootstrap is closed.
- `/admin-ui/invitations/new` — create an invitation.
- Any other path is redirected to `/admin-ui`.

## Authentication seam

```text
AuthProvider (interface)
  ├── StubAuthProvider     # default; no I/O, no persistence
  └── (future: GoogleOidcAuthProvider / Keycloak / etc.)

providerHolder  ──► apiCall  ──► fetch + "Authorization: Bearer …"
              (singleton)              (only seam that builds headers)
```

The `AuthProvider` interface exposes:

- `getAccessToken(): Promise<string | null>`
- `signIn(): Promise<void>`
- `signOut(): Promise<void>`

Presentation code never constructs the `Authorization` header directly; the
`apiCall` helper is the single outbound HTTP boundary. Swapping the development
stub for a real OIDC SDK is a single-file change in `auth/StubAuthProvider.ts`.

## Current capability

The initial administrative slice is **creating an invitation**. The form posts to
`/capsa/api/invitations` on the configured Capsa Server through the same
centralized API client and therefore receives the bearer token whenever the
provider has one. The server endpoint is **not yet implemented**; the
contract targeted here is documented in
[`docs/engineering/agents/reports/CAPSA-WEB-001-report.md`](../../docs/engineering/agents/reports/CAPSA-WEB-001-report.md).

## Role taxonomy

The `"USER" | "ADMIN"` literal was extracted from the invitation client into
[`src/domain/roles.ts`](src/domain/roles.ts). It is marked as server-owned
(the authoritative definition lives at `com.capsa.users.api.Role`); the
frontend only references it to label the user-facing dropdowns. Authorization
decisions are never made locally from these strings.

## Relationship to Capsa Server

This client is an independent consumer of the Capsa Server's public HTTP/REST
contract. It does **not** import from `server/`, JPA entities, internal
capability modules, or any Maven artifact. Communication happens exclusively
over `fetch` against `/capsa/api/...`, with the bearer token (when present)
attached by the centralized API seam.

Required server endpoints (current Capsa Server status):

| Endpoint                                | Status                              |
| --------------------------------------- | ----------------------------------- |
| `GET /capsa/api/bootstrap/status`       | **Implemented** (`capsa-bootstrap`) |
| `POST /capsa/api/bootstrap` (token)     | **Implemented**; error mappers for `CAPSA_BOOTSTRAP_ALREADY_CLAIMED` and `CAPSA_FORBIDDEN` are not yet present server-side — claim errors may surface as `500` until mappers are added. The frontend already handles the documented `403` / `409` codes; the report calls this out. |
| `POST /capsa/api/invitations`           | **Not yet implemented** — see `CAPSA-WEB-001-report.md`. |

## Layout

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
    ├── App.tsx                    # BrowserRouter wrapper
    ├── AppRoutes.tsx              # route definitions
    ├── config.ts                  # reads VITE_CAPSA_API_URL
    ├── styles.css
    ├── api/
    │   ├── apiCall.ts             # centralized fetch + Authorization injection
    │   ├── errors.ts              # CapsaErrorBody, CapsaApiError, code map
    │   ├── bootstrap.ts           # bootstrap status / claim client
    │   ├── invitation.types.ts    # request / response shapes
    │   ├── invitations.ts         # invitation client (now uses apiCall)
    │   └── invitations.types.ts   # resource-specific shapes (InvitationId)
    ├── auth/
    │   ├── AuthProvider.ts        # interface
    │   ├── StubAuthProvider.ts    # default development provider
    │   ├── authEvents.ts          # pub-sub for provider changes
    │   ├── providerHolder.ts      # active provider singleton
    │   ├── useActiveAuthProvider.ts
    │   ├── useBootstrapGate.ts    # status / submit state machine
    │   └── index.ts
    ├── domain/
    │   └── roles.ts               # server-owned role taxonomy (USER / ADMIN)
    ├── components/
    │   ├── AdminShell.tsx
    │   ├── AdminGateOutlet.tsx    # connects gate to AdminShell
    │   └── AdminEntryGate.tsx     # presentation for the gate
    ├── pages/
    │   ├── AdminHomePage.tsx
    │   └── InvitationNewPage.tsx
    └── test/
        ├── setup.ts               # vitest + jest-dom + MSW lifecycle
        ├── handlers.ts            # MSW handlers + helpers
        ├── admin-routing.test.tsx
        ├── admin-entry-gate.test.tsx
        └── invitations-api.test.ts
```

## Accessibility and UX

The bootstrap panel provides:

- explicit `<label>` elements tied to inputs via `htmlFor` (React `useId`);
- a visually hidden "(required)" annotation plus `aria-invalid` /
  `aria-describedby` on the token input when the server rejects it;
- `role="alert"` on every error banner; `role="status"` on the loading panel;
- a disabled submit button and label change while in flight;
- keyboard-accessible tab order; visible focus rings on every interactive
  element;
- automatic clearing of the token from local state on every response.

The invitation page already meets the same standards (CAPSA-WEB-001) and
inherits the bearer-token injection from the API seam without further changes.

## Testing

Tests cover, in addition to the existing invitation scenarios:

1. The gate always renders before `AdminShell` on entry to `/admin-ui`.
2. The loading state is observable while the status endpoint is in flight.
3. `required: true` renders the first-run flow with sign-in, token, and submit controls.
4. `required: false` renders the admin shell.
5. The bootstrap token is POSTed to `/capsa/api/bootstrap` with the right body.
7. 403 renders an "invalid token" error.
8. 409 transitions into the admin shell.
10. Network failures surface as a "network error" panel.
11. 201 transitions to the admin shell.
12. The bootstrap token does not leak into storage, the DOM, or rendered HTML after submission.
13. The `InvitationNewPage` source never references `Authorization`, `Bearer`, or `AuthProvider` directly.

API-level tests assert that `createInvitation` returns a typed `CapsaApiError`
with `code`, `message`, and `httpStatus` for 422, 403, and network failures.