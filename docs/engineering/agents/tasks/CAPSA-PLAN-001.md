# CAPSA-PLAN-001 — Engineering Plan for Capsa v0.1

## Role

product-architect

## Status

DONE

## Objective

Transform the reconciled Capsa product, domain, architecture, and QA artifacts into an executable Engineering Plan composed of small, coherent implementation slices providing a safe path from the current Quarkus scaffold to a working Capsa v0.1.

## Sources

| Artifact | Role |
|---|---|
| `docs/engineering/agents/intent/intent1.md` | Product authority |
| `docs/knowledge/use-cases/uc-01` … `uc-06` | Behavioral authority |
| `docs/knowledge/domain/capsa-domain-model-v0.1.md` | Domain invariant authority |
| `docs/knowledge/architecture/capsa-arch-001-modular-monolith-v0.1.md` | Architecture authority (reconciled) |
| `docs/engineering/agents/reviews/CAPSA-ARCH-REVIEW-001-architecture-review.md` | Architecture review |
| `docs/engineering/agents/reports/CAPSA-ARCH-RECONCILE-001-reconciliation-report.md` | Architecture reconciliation |
| `docs/knowledge/testing/capsa-qa-001-acceptance-test-cases-v0.1.md` | Acceptance tests (43 TCs) |

## Deliverable

`docs/knowledge/engineering-plan/capsa-plan-001-v0.1.md`

## Constraints

- Do not implement production code.
- Do not create the Engineering Plan in an active worktree.
- Plan must follow the reconciled architecture, not the pre-review draft.
- Set the plan to REVIEW when complete.

## Completion Criteria

1. All 8 planning quality checks pass (see plan §14).
2. Every UC maps to at least one slice.
3. All 43 acceptance test cases map to a slice.
4. Slice dependency graph is acyclic.
5. No production code written.
