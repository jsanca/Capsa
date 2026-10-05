# CAPSA-WEB-001 — Bootstrap React Admin UI and Invitation Creation

## Role

software-engineer

## Status

DONE

## Objective

Bootstrap the Capsa web client with TypeScript + React + Vite and implement the first
administrative slice: invitation creation through the Capsa Server public REST API, served
under `/admin-ui`.

## Sources

| Artifact | Role |
| --- | --- |
| `web/README.md` (empty boundary stub) | Boundary authority |
| `server/capsa-lists/src/main/java/com/capsa/lists/internal/rest/ListResource.java` | Server REST pattern reference |
| `server/capsa-items/src/main/java/com/capsa/items/internal/rest/ItemResource.java` | Server REST pattern reference |
| `docs/knowledge/architecture/capsa-arch-001-modular-monolith-v0.1.md` | Capability ownership model |

## Deliverables

- `web/` — Vite + React + TypeScript application with administrative routes
  (`/admin-ui`, `/admin-ui/invitations/new`) and a typed invitation API client.
- `web/README.md` — replaced empty stub with implementation description.
- `docs/engineering/agents/reports/CAPSA-WEB-001-report.md` — completion report.
- Entry in `docs/engineering/ENGINEERING_LOG.md`.

## Acceptance criteria (verbatim from task input)

1. A developer can enter `web/`, install dependencies, start the application, navigate to
   `/admin-ui`, and access a working invitation creation experience.
2. Submitting valid data calls the existing Capsa Server public REST API using the
   documented invitation contract.
3. Successful responses are represented clearly in the UI.
4. Validation, backend errors, and network failures are handled gracefully.
5. The project builds and tests successfully.
6. The structure provides a small foundation for future Capsa admin and public web slices
   without introducing unnecessary framework complexity.

## Non-goals

- public Capsa UI; mobile UI; authentication redesign; invitation acceptance; listing;
  user/role management; complete admin dashboard; final visual design system; SSR; server
  changes unrelated to making the existing invitation contract usable.

## Notes

The Capsa Server does not currently expose an invitation endpoint. The frontend targets a
proposed `POST /capsa/api/invitations` contract documented in the report; tests run against
MSW handlers that emulate the contract. No server-side code is modified by this task.