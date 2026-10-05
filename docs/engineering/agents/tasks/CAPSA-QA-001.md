# CAPSA-QA-001 — Acceptance Test Cases for UC-01 through UC-06

## Role

qa-engineer

## Status

DONE

## Objective

Derive a coherent set of behavioral acceptance test cases for Capsa UC-01 through UC-06.

The test cases describe observable behavior that demonstrates Capsa satisfies its current use cases and domain invariants. They remain independent of implementation details and serve as the traceability anchor from use cases to the Engineering Plan and automated test design.

## Sources

| Artifact | Role |
|---|---|
| `docs/engineering/agents/intent/intent1.md` | Product authority |
| `docs/knowledge/use-cases/uc-01` … `uc-06` | Behavioral authority |
| `docs/knowledge/domain/capsa-domain-model-v0.1.md` | Domain invariant authority |
| `docs/engineering/agents/reviews/CAPSA-DOMAIN-REVIEW-001-domain-model-review.md` | Review findings |
| `docs/engineering/agents/reports/CAPSA-DOMAIN-RECONCILE-001-reconciliation-report.md` | Reconciliation dispositions |
| `docs/knowledge/architecture/capsa-arch-001-modular-monolith-v0.1.md` | Context only — not behavioral authority |

## Deliverable

`docs/knowledge/testing/capsa-qa-001-acceptance-test-cases-v0.1.md`

## Scope

- UC-01 — Create List
- UC-02 — Add Item to List
- UC-03 — Automatically Classify Capture
- UC-04 — Resolve Ambiguous Classification
- UC-05 — View List
- UC-06 — Complete Item

## Constraints

- Do not implement automated tests.
- Do not modify production code.
- Do not modify the architecture under review.
- Do not invent product features.
- Architecture is under review; it must not become the source of product behavior.
- If architecture and domain/UC artifacts disagree, record the discrepancy rather than silently adopting the architecture.

## Completion Criteria

1. UC-01 through UC-06 have meaningful behavioral coverage.
2. Important reconciled domain invariants are represented.
3. Ambiguity and execution failure are tested as distinct outcomes.
4. Ownership/user isolation is represented where applicable.
5. Tests remain implementation-agnostic.
6. No future feature has silently become a current requirement.
7. Uncovered requirement ambiguities are explicit open questions.
8. Stable test identifiers exist for Engineering Plan traceability.
9. No automated tests or production code were written.
