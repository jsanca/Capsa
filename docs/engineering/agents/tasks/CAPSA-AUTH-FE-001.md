# CAPSA-AUTH-FE-001 — Admin Bootstrap Gate and Authentication Client Seam

## Role

software-engineer

## Status

DONE

## Objective

Introduce the minimum frontend architecture required to protect `/admin-ui`
behind a first-run bootstrap/authentication gate:

- gate before `AdminShell` consulting `GET /capsa/api/bootstrap/status`;
- first-run flow submitting the bootstrap token via
  `POST /capsa/api/bootstrap` through a centralized API/auth seam;
- `AuthProvider` interface with bearer-token injection;
- centralize the role contract in one server-owned type location;
- preserve the existing invitation UI without embedding auth logic into
  presentation components.

## Sources

| Artifact | Role |
| --- | --- |
| `server/capsa-bootstrap/src/main/java/com/capsa/bootstrap/...` | Bootstrap resource + status shape |
| `server/capsa-users/src/main/java/com/capsa/users/api/Role` (planned) | Server-owned role taxonomy |
| `docs/engineering/agents/reports/CAPSA-ARCH-REVIEW-003.md` | Architectural authority (§5, §7, §8) |
| `docs/engineering/agents/reports/CAPSA-WEB-001-report.md` | Prior web slice (existing API seam) |

## Deliverables

- `web/src/auth/` — `AuthProvider` interface, `StubAuthProvider`,
  singleton holder, `useActiveAuthProvider`, `useBootstrapGate`.
- `web/src/api/apiCall.ts` — centralized outbound HTTP boundary with
  bearer-token injection and `{code, message}` parsing.
- `web/src/api/errors.ts` — wire-level error types shared by every API client.
- `web/src/api/bootstrap.ts` — `getBootstrapStatus` / `claimBootstrap`.
- `web/src/domain/roles.ts` — server-owned role taxonomy, marked as such.
- `web/src/components/AdminEntryGate.tsx` — gate presentation.
- `web/src/components/AdminGateOutlet.tsx` — gate ↔ shell connector.
- `web/src/AppRoutes.tsx` — `/admin-ui` route uses the gate outlet.
- `web/src/pages/InvitationNewPage.tsx` — role union imported from
  `domain/roles`; no auth logic added.
- `web/src/test/admin-entry-gate.test.tsx` — 15 new tests covering all 13
  acceptance scenarios.
- `web/.env.example` — documented `VITE_CAPSA_STUB_ACCESS_TOKEN`.
- `web/README.md` — implementation description.
- `docs/engineering/agents/reports/CAPSA-AUTH-FE-001-report.md` — completion report.
- Entry in `docs/engineering/ENGINEERING_LOG.md`.

## Acceptance criteria (verbatim from task input)

1. `/admin-ui` evaluates the authoritative bootstrap state first.
2. If bootstrap is required, the user is presented with an
   authentication/bootstrap gate rather than the administrative shell.
3. An authenticated user can submit the one-time bootstrap token through
   the centralized API/authentication seam.
4. After successful bootstrap, the frontend transitions into the existing
   administrative UI.
5. No administrator email or authorization rule is encoded in the frontend.

## Non-goals

- invitation redemption; invitation listing; invitation backend; `/me`
  endpoint; general session-management framework; public Capsa UI; final
  visual design; generalized frontend authorization framework; provider-
  specific identity/domain decisions; persistence of authentication
  tokens beyond what the eventual OIDC client requires.

## Notes

The Capsa Server implements `GET /capsa/api/bootstrap/status` and
`POST /capsa/api/bootstrap` (CAPSA-AUTH-BE-001). The frontend consumes both
endpoints directly. The server currently lacks `ExceptionMapper`s for
`BootstrapAlreadyClaimedException` and `BootstrapTokenInvalidException`; the
frontend handles the documented `403` / `409` codes when present and falls
back to a "Server error" panel otherwise.