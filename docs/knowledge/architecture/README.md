# Architecture Knowledge

System architecture documents for Capsa. Each document covers a named architectural version and is immutable once accepted.

| Document | Version | Status | Depends On |
|---|---|---|---|
| [capsa-arch-001-modular-monolith-v0.1.md](capsa-arch-001-modular-monolith-v0.1.md) | v0.1 | REVIEW | CAPSA-DOMAIN-RECONCILE-001 |
| [diagrams/module-diagram.md](diagrams/module-diagram.md) | v0.1 | Current | capsa-arch-001 |
| [diagrams/class-diagrams.md](diagrams/class-diagrams.md) | v0.1 | Current | capsa-arch-001 |
| [diagrams/sequence-diagrams.md](diagrams/sequence-diagrams.md) | v0.1 | Current | capsa-arch-001 |

The three diagram documents visualize the v0.1 implementation: module
dependencies and JPMS layering, the per-module public API / internal domain /
internal service / persistence layout, and the end-to-end flows for UC-01
through UC-06.
