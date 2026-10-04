# Engineering Log

This file is the compact, current index of material engineering work. Detailed task, report, review, and checkpoint records live under `agents/`; this index links their relationship rather than repeating their evidence.

| Task | Description | Status | Depends On | Task File | Report | Review | Fix / Checkpoint | Knowledge |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| CAPSA-DOMAIN-001 | Derive Capsa v0.1 domain model from UC-01–UC-06 | DONE | — | [task](agents/tasks/CAPSA-DOMAIN-001.md) | — | [review](agents/reviews/CAPSA-DOMAIN-REVIEW-001-domain-model-review.md) | [reconciliation](agents/reports/CAPSA-DOMAIN-RECONCILE-001-reconciliation-report.md) | [domain model](../knowledge/domain/capsa-domain-model-v0.1.md) |
| CAPSA-DOMAIN-REVIEW-001 | Adversarial engineering/domain-boundary review of v0.1 domain model | DONE | CAPSA-DOMAIN-001 | [task](agents/tasks/CAPSA-DOMAIN-REVIEW-001.md) | [review](agents/reviews/CAPSA-DOMAIN-REVIEW-001-domain-model-review.md) | — | — | — |
| CAPSA-DOMAIN-RECONCILE-001 | Reconcile domain model with CAPSA-DOMAIN-REVIEW-001 findings | DONE | CAPSA-DOMAIN-REVIEW-001 | [task](agents/tasks/CAPSA-DOMAIN-RECONCILE-001.md) | [report](agents/reports/CAPSA-DOMAIN-RECONCILE-001-reconciliation-report.md) | — | — | [domain model](../knowledge/domain/capsa-domain-model-v0.1.md) |

| CAPSA-ARCH-001 | Capsa modular monolith architecture v0.1 | DONE | CAPSA-DOMAIN-RECONCILE-001 | [task](agents/tasks/CAPSA-ARCH-001—ModularMonolithArchitectureV0.1.md) | — | [review](agents/reviews/CAPSA-ARCH-REVIEW-001-architecture-review.md) | [reconciliation](agents/reports/CAPSA-ARCH-RECONCILE-001-reconciliation-report.md) | [architecture doc](../knowledge/architecture/capsa-arch-001-modular-monolith-v0.1.md) |
| CAPSA-ARCH-REVIEW-001 | Adversarial review of modular monolith architecture v0.1 | DONE | CAPSA-ARCH-001 | [task](agents/tasks/CAPSA-ARCH-REVIEW-001.md) | [review](agents/reviews/CAPSA-ARCH-REVIEW-001-architecture-review.md) | — | — | — |
| CAPSA-ARCH-RECONCILE-001 | Reconcile architecture with CAPSA-ARCH-REVIEW-001 findings | DONE | CAPSA-ARCH-REVIEW-001 | [task](agents/tasks/CAPSA-ARCH-RECONCILE-001—ArchitectureReviewReconciliation.md) | [report](agents/reports/CAPSA-ARCH-RECONCILE-001-reconciliation-report.md) | — | — | [architecture doc](../knowledge/architecture/capsa-arch-001-modular-monolith-v0.1.md) |

| CAPSA-QA-001 | Acceptance test cases for UC-01–UC-06 | DONE | CAPSA-DOMAIN-RECONCILE-001 | [task](agents/tasks/CAPSA-QA-001.md) | — | — | — | [test cases](../knowledge/testing/capsa-qa-001-acceptance-test-cases-v0.1.md) |

| CAPSA-PLAN-001 | Engineering Plan for Capsa v0.1 | REVIEW | CAPSA-ARCH-RECONCILE-001, CAPSA-QA-001 | [task](agents/tasks/CAPSA-PLAN-001.md) | — | — | — | [engineering plan](../knowledge/engineering-plan/capsa-plan-001-v0.1.md) |

| S-00 | Repository Restructure + Quarkus+JPMS Validation Spike | DONE | CAPSA-PLAN-001 | — | — | — | — | — |
| S-01 | Users Capability | DONE | S-00 | — | — | — | — | — |
| S-02 | Lists Capability + UC-01 | DONE | S-01 | — | — | — | — | — |
| S-04 | Classification Capability (Known Strategy) | DONE | S-01 | — | — | — | — | — |
| CAPSA-CLASSIFICATION-GARDEN-001 | Simplify Classifier Composition — eliminate DefaultClassifier, promote ClassificationPipeline to CDI Classifier | DONE | S-04 | — | [report](agents/reports/CAPSA-CLASSIFICATION-GARDEN-001-report.md) | — | — | — |
| CAPSA-CLASSIFICATION-GARDEN-002 | Separate Classification from Resolution Learning — extract ClassificationService, strip Classifier to classify() only | DONE | CAPSA-CLASSIFICATION-GARDEN-001 | — | [report](agents/reports/CAPSA-CLASSIFICATION-GARDEN-002-report.md) | — | — | — |

| S-03 | Items Capability + UC-02 + UC-05 + UC-06 | DONE | S-02 | — | [report](agents/reports/S-03-report.md) | — | — | — |
| S-05 | Capture Capability + UC-03 + UC-04 | DONE | S-03, S-04, CAPSA-CLASSIFICATION-GARDEN-002 | — | [report](agents/reports/S-05-report.md) | — | — | — |
| CAPSA-DI-HYGIENE-001 | Constructor injection convention — convert all internal components; document in CLAUDE.md | DONE | S-05 | — | [report](agents/reports/CAPSA-DI-HYGIENE-001-report.md) | — | — | — |

| S-06 | Error Model + OIDC Hardening | DONE | S-05 | — | [report](agents/reports/S-06-report.md) | — | — | — |

| CAPSA-OBS-001 | Observability Event Foundation | DONE | S-06 | — | [report](agents/reports/CAPSA-OBS-001-report.md) | — | — | [observability knowledge](../knowledge/observability/capsa-obs-001-observability-event-foundation.md) |

Use `—` where a relationship does not exist. Keep cells brief; the linked durable record carries evidence, limitations, unresolved issues, and validation. Add a knowledge link when work establishes or changes reusable current understanding.
