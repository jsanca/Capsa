# CAPSA-ARCH-REVIEW-001 — Engineering Review of Modular Monolith Architecture v0.1 — Review

## Status

Complete

## Review Outcome

**REVISE** — the architecture is directionally sound and internally coherent in most respects, but it contains two concrete JPMS-enforceability defects that would prevent the design from compiling as drawn, plus several consistency and scope issues that must be resolved before reconciliation. No CRITICAL finding. The architecture remains in `REVIEW`.

## Scope

Independent adversarial engineering review of `docs/knowledge/architecture/capsa-arch-001-modular-monolith-v0.1.md` against the product intent, UC-01 through UC-06, the reconciled domain model, and the domain review/reconciliation records. No code was written; the architecture document was not modified.

## Sources Reviewed

- `docs/engineering/agents/intent/intent1.md`
- `docs/knowledge/use-cases/uc-01` … `uc-06`
- `docs/knowledge/domain/capsa-domain-model-v0.1.md` (reconciled)
- `docs/engineering/agents/reviews/CAPSA-DOMAIN-REVIEW-001-domain-model-review.md`
- `docs/engineering/agents/reports/CAPSA-DOMAIN-RECONCILE-001-reconciliation-report.md`
- `docs/engineering/agents/tasks/CAPSA-ARCH-001—ModularMonolithArchitectureV0.1.md`
- `docs/knowledge/architecture/capsa-arch-001-modular-monolith-v0.1.md` (reviewed artifact)

---

## Findings

### A-01 — `HIGH` — REST resources cannot compile against a runtime-owned auth context

- **Area:** 2 (JPMS), 9 (Authentication), 10 (REST placement)
- **Evidence:** §3 `capsa.runtime` "Exports nothing" and §5 "Allowed dependents: none (runtime is the leaf)"; §3 `capsa.users` "Allowed dependents: runtime only — no other capability depends on users at compile time." Yet §10 shows `ListResource` (in `capsa.lists.internal.rest`) declaring `@Inject CurrentUserContext currentUser;`, and §9 states the injection "does not require a compile-time `requires` on runtime from capability modules" and "JPMS cannot prevent CDI injection at runtime (CDI operates dynamically)."
- **Problem:** CDI resolves the *instance* at runtime, but the resource's own source must compile against the `CurrentUserContext` *type*. A field/parameter reference requires a `requires` edge on the module that exports the type. As drawn, that module is either `runtime` (a leaf that exports nothing) or `users` (which no capability may depend on). The claim that "no compile-time requires is needed" is false.
- **Impact:** The vertical-REST-resources pattern (§10) is not implementable under the stated JPMS graph. Either the graph or the auth-context type placement must change, and that change ripples into the dependency edges and the "no capability depends on users" claim.
- **Recommendation:** Decide, in this architecture (not deferred to the Engineering Plan), where the auth-context type lives and which modules require it. The clean options are: (a) a minimal shared identity module (only if the `common`-module concern is explicitly weighed and accepted); (b) place the `CurrentUserContext` interface in `capsa.users.api` and accept that capability modules `requires users` for the interface only; or (c) have REST resources inject a CDI-qualified `UUID userId` produced by runtime, avoiding a cross-module type entirely. Correct §9's claim about CDI bypassing compile-time visibility.
- **Disposition:** OPEN

---

### A-02 — `HIGH` — Strategy-list producer in runtime references internal classification types

- **Area:** 2 (JPMS), 7 (Classification)
- **Evidence:** §8 places `ClassificationStrategyProducer` in `capsa-runtime` and its constructor injects the concrete types `KnownClassificationStrategy`, `EmbeddingClassificationStrategy`, `SystemOneClassificationStrategy`; §3/§8 declare these strategies internal to `capsa.classification` ("all implemented inside this capability"; §3 "Internal ports").
- **Problem:** For the producer to compile, `runtime` must import concrete classes that `capsa.classification` does not export. JPMS rejects this. The ordering requirement (`List.of(known, embedding, systemOne)`) forces the producer to name the concrete strategies, which conflicts with the "internal" claim.
- **Impact:** The strategy-wiring mechanism — the sole point where strategy order is composed — cannot exist in `runtime` as drawn. This is the same class of defect as A-01: composition in `runtime` repeatedly requires compile-time access to capability internals.
- **Recommendation:** Move default strategy composition into `capsa.classification` (which can see its own internals), exposing either an ordered `List<ClassificationStrategy>` bean or a pipeline entrypoint. Let `runtime` control only *configuration* (which strategies are enabled, thresholds, model path) via properties, not by naming internal classes. Alternatively, export a minimal strategy SPI, but that is heavier than necessary.
- **Disposition:** OPEN

---

### A-03 — `MEDIUM` — Single transaction spans external provider calls

- **Area:** 6 (Transactions), 12, 14
- **Evidence:** §12 example marks `CaptureService.submit()` `@Transactional` and comments "Capture save + classification + Item creation — all in one transaction"; §19 "Recommended" reaffirms "Capture, Item creation, and memory evidence within a single transaction in `CaptureService.submit()`." §8/§15 note the pipeline may invoke external providers (Jev/DeepSeek) with timeouts.
- **Problem:** The same transaction that persists the Capture stays open across the entire classification pipeline, which may make an HTTP call to an external model provider. Holding a DB transaction (and any locks) open across a slow/unreliable network call is a well-known operational anti-pattern.
- **Impact:** Long-held transactions and connection-pool exhaustion under provider latency or failure; the exact failure the design itself anticipates ("provider unavailable", "System One timeout", §8/§15).
- **Recommendation:** Split transaction spans: (1) persist Capture + ClassificationAttempt, commit; (2) run classification (external calls) outside any transaction; (3) persist the resulting Item + memory evidence in a second transaction. If a single-transaction atomicity for the whole flow is genuinely required, that must be justified and the external calls explicitly excluded from the transactional span.
- **Disposition:** OPEN

---

### A-04 — `MEDIUM` — Reconciled one-Item enforcement mechanism not carried forward

- **Area:** 6 (Transactions), 8 (Capture orchestration)
- **Evidence:** CAPSA-DOMAIN-RECONCILE-001 (F-01) documented the cross-aggregate guarantee "a resolved Capture produces exactly one Item" as enforced by "application transactional coordination + idempotency + persistence uniqueness constraint on `Item.captureId`." §12 and §19 of the architecture discuss atomicity/rollback only; neither idempotency nor a uniqueness constraint on `Item.captureId` appears.
- **Problem:** The architecture's transaction model addresses atomicity but silently drops the idempotency and uniqueness-constraint components of the enforcement mechanism. UC-04's "at most one Item through classification resolution" invariant (§12 of uc-04) has no architectural home for concurrent/duplicate resolution.
- **Impact:** Duplicate `Item` creation under concurrent or repeated resolution remains possible; the reconciliation-level guarantee regresses.
- **Recommendation:** Re-introduce the full mechanism explicitly: a unique constraint (or equivalent) on `Item.captureId`, plus idempotent resolution semantics for UC-04, stated in the persistence and transaction sections.
- **Disposition:** OPEN

---

### A-05 — `MEDIUM` — UC-05 Option A would create a module cycle

- **Area:** 3 (Dependency graph), 11 (UC-05)
- **Evidence:** §4 graph has `items → lists` (for `verifyContributionAccess`). §10/§19 present UC-05 Option A as "`lists` depends on `items`" for view assembly, without noting the cycle this introduces.
- **Problem:** Option A adds `lists → items`, producing a direct cycle (`items → lists` and `lists → items`). The document elsewhere claims "All dependencies are acyclic" (§4) and does not flag the conflict.
- **Impact:** If Option A is chosen at reconciliation, the acyclic-DAG invariant — a core architectural property — is broken, and JPMS would reject the cyclic requires.
- **Recommendation:** Reject Option A. Recommend Option C (two endpoints; client assembles) as simplest, or Option B (runtime assembles) if a single response is a product requirement. Record the cycle explicitly so the acyclic claim is not contradicted by a later choice.
- **Disposition:** OPEN

---

### A-06 — `MEDIUM` — Semantic-profile cache and table derive from lists state the classification capability cannot observe

- **Area:** 5 (Persistence ownership), 13 (Cache)
- **Evidence:** §3 `capsa.classification` owns `list_semantic_profiles` (embeddings computed from `List.name` + `explicitPurpose`); §13 says the semantic-profile cache should "invalidate when a List's purpose changes." `capsa.classification` has no dependency on `capsa.lists` and there is no event mechanism.
- **Problem:** This is data derived from another capability's state, owned and cached by a capability that cannot observe the source change. The invalidation rule references a signal (`List` purpose change) that `classification` structurally cannot receive. Latent in v0.1 (no list-update UC), but the stated rule is unsupported by the dependency graph.
- **Impact:** If list updates are introduced later, stale embeddings silently produce wrong classification until a mechanism is added; the current design would require a new cross-capability dependency or event bus.
- **Recommendation:** Either document that semantic profiles are immutable in v0.1 (lists are create-only) and explicitly defer the invalidation mechanism, or define how `classification` learns of list changes (callback/event/outbox) as a first-class seam. Do not assert an invalidation path that does not exist.
- **Disposition:** OPEN

---

### A-07 — `MEDIUM` — Three live strategies + ONNX + pgvector + external providers exceed v0.1 evidence

- **Area:** 17 (Simplicity), 7, 13
- **Evidence:** §8 wires `Known`, `Embedding`, `SystemOne` strategies as active; §15/§16 commit ONNX, pgvector, and Jev/DeepSeek adapters. UC-03's principle is "cheapest available evidence first; escalate only when uncertainty remains," and the Known strategy (deterministic `userId + normalizedContent` exact match) satisfies the classification loop for a single user without any provider.
- **Problem:** The architecture implements the full three-tier pipeline in v0.1 rather than distinguishing "architecture permits a strategy port" from "architecture should run embeddings + decision models in v0.1." This is operationally excessive (model loading, pgvector, external API keys/rate limits) before evidence demonstrates need.
- **Impact:** Unnecessary v0.1 complexity and external dependencies; the intent's own "complexity follows evidence" principle is not applied to the classification tiers.
- **Recommendation:** Make `Known` the v0.1 default strategy; keep `Embedding` and `SystemOne` as inactive-but-wired slots behind the `ClassificationStrategy` port (or defer entirely). Preserve the seam (which the design already has) without activating the infrastructure in v0.1.
- **Disposition:** OPEN

---

### A-08 — `LOW` — Identifier representation is internally inconsistent

- **Area:** 3 (Dependency graph), 5 (Persistence)
- **Evidence:** §5 exports typed identifiers (`ListId`, `ItemId`, `CaptureId`, `UserId` "optional"); §19 recommends "Use `UUID` (not typed ID wrappers) at cross-module public API boundaries"; §3 service signatures use `UUID userId` throughout.
- **Problem:** The public-surface model and the recommended boundary convention disagree on whether identifiers are typed wrappers or `UUID`s.
- **Impact:** Implementers face ambiguous ID types at every boundary; if resolved late, it produces avoidable churn across all `api` packages.
- **Recommendation:** Pick one and align §5 with §19. The `UUID`-at-boundary choice is the lower-coupling option; if typed IDs are retained internally, state explicitly that they do not cross module boundaries.
- **Disposition:** OPEN

---

### A-09 — `LOW` — Schema DDL lives in runtime, splitting table ownership

- **Area:** 5 (Persistence ownership)
- **Evidence:** §3/§7 place all Flyway migrations in `capsa-runtime` (`V001__users_initial.sql`, …), while §7 states "A capability is the only reader and writer of its own persistence tables" and entities/repositories live in each capability.
- **Problem:** The tables' schema is defined outside the module that owns them. A schema change to `items` requires editing a file in `runtime` while the entity/repository live in `capsa.items`.
- **Impact:** Two-place change for any schema evolution and a mild weakening of the "capability owns its tables" rule (the rule concerns runtime data access, not DDL, but the split is real).
- **Recommendation:** Acceptable as a pragmatic choice (Flyway needs one classpath), but state the ownership nuance explicitly: capabilities own the *model*; runtime owns the *DDL* — or consider per-capability migration locations. Do not claim full table ownership without the caveat.
- **Disposition:** OPEN

---

### A-10 — `LOW` — `activity_log` table has no owner in the capability model

- **Area:** 15 (Observability)
- **Evidence:** §14 proposes "a simple `activity_log` table (or per-entity timestamp fields already on domain objects)."
- **Problem:** If the `activity_log` table is adopted, no capability is identified as its single writer, conflicting with the single-writer rule; if the per-entity timestamps alternative is used instead, the table is unnecessary.
- **Impact:** Half-specified cross-capability persistence that would either violate the ownership rule or need a new capability/owner decision.
- **Recommendation:** Use the per-entity timestamp fields (already on domain objects) for v0.1 and defer a dedicated activity store. If a durable event log is later required, assign it an owner (or a dedicated outbox/event capability) before introducing it.
- **Disposition:** OPEN

---

### A-11 — `OBSERVATION` — Shared `CapsaException` type has no defined home

- **Area:** 12 (Error model)
- **Evidence:** §9 registers `@Provider ExceptionMapper<CapsaException>` implementations in `runtime`; each capability defines its own error-code constants (§9) and, presumably, its own exception types.
- **Problem:** A single `ExceptionMapper<CapsaException>` implies a shared base type across capabilities, but the architecture explicitly avoids a `common` module. Where `CapsaException` lives is unspecified.
- **Impact:** Either a shared exception module is introduced (against §4's guidance) or runtime registers N per-capability mappers — a decision the doc leaves open.
- **Recommendation:** Prefer per-capability exception types with per-capability (or per-code) mappers in `runtime`, avoiding a shared base type. State the choice explicitly.
- **Disposition:** OPEN (informational)

---

### A-12 — `OBSERVATION` — Classification-memory cache invalidation omits AUTO writes

- **Area:** 13 (Cache)
- **Evidence:** §13 invalidates the memory cache "when `recordUserResolution()` is called"; §3 `capsa.classification` also writes AUTO evidence "internally when classified" during `classify()`.
- **Problem:** AUTO evidence writes also change the memory the Known strategy queries, but the invalidation rule only names USER resolution.
- **Impact:** A stale Known-Classification cache could serve an outdated classification immediately after an AUTO write; "short TTL" (§13) is the only stated mitigation.
- **Recommendation:** Invalidate (or bypass) the memory cache on both AUTO and USER writes, or make the Known lookup non-cached by default and add caching only with measured justification.
- **Disposition:** OPEN (informational)

---

### A-13 — `OBSERVATION` — External-provider API-key/secrets handling is unaddressed

- **Area:** 14 (External provider boundary)
- **Evidence:** §15 covers timeout, bounded invocation, failure isolation, and rate/cost deferral, but does not address how Jev/DeepSeek API keys are stored, injected, and kept out of logs/source.
- **Problem:** API-key protection is listed in the review scope and is absent from the provider boundary.
- **Impact:** Secrets management is left entirely to implementation, which is a risk in the one module that must touch providers.
- **Recommendation:** State that provider keys are runtime-injected configuration (e.g., environment/secret store), never in `application.properties` committed to source, and that key values are never logged.
- **Disposition:** OPEN (informational)

---

## Findings Summary

| Severity | Count | IDs |
| --- | --- | --- |
| CRITICAL | 0 | — |
| HIGH | 2 | A-01, A-02 |
| MEDIUM | 5 | A-03, A-04, A-05, A-06, A-07 |
| LOW | 3 | A-08, A-09, A-10 |
| OBSERVATION | 3 | A-11, A-12, A-13 |

## Strengths

Decisions that survived adversarial review particularly well:

- **Capability-organized modular monolith** over microservices and horizontal layers — well-justified against a single-user v0.1.
- **`capsa.classification` is dependency-free** — `ClassificationTarget` (listId/name/explicitPurpose) as a local type, with `capture` converting `ListView → ClassificationTarget`, is a clean isolation seam that removes the otherwise natural `classification → lists` edge.
- **Cross-capability collaboration strictly through services** — the §4 collaboration table contains no repository access; §7 shows the structural impossibility. Enforced, not just asserted.
- **Execution-failure vs classification-uncertainty** (§8/§15) is carried forward correctly from reconciliation (F-02): exceptions propagate, never conflated with `NEEDS_RESOLUTION`.
- **Entity/domain separation with converters** (§7) is consistently applied; JPA/JSON-B/CDI annotations are confined per the §16 map.
- **Authentication vs authorization kept distinct** (§11): OIDC for identity, `verifyContributionAccess` for ownership; the abstraction cleanly supports future shared Lists.
- **Evolutionary seams (§18)** are genuinely extension-friendly without premature implementation — shared Lists, curation, new Item states, PatternDetector, MCP all have defined extension points.
- **Jakarta-first principle with explicit exception** (§16: Quarkus cache annotations) is honest and prevents accidental framework leakage.
- **Technology placement map (§16)** is concrete and enforceable; "if it's not in `api`, JPMS prevents coupling" is the right posture.

## Open Architecture Decisions

Requiring reconciliation or product/architecture input:

1. **Auth-context type placement and its module dependency** (A-01) — must be decided in this architecture, not deferred.
2. **Strategy-composition ownership** (A-02) — move default ordering into `classification`; runtime configures, does not name internal types.
3. **UC-05 view assembly** (A-05) — reject Option A (cycle); choose C (simplest) or B.
4. **Transaction boundaries around external provider calls** (A-03) — split spans.
5. **v0.1 classification strategy scope** (A-07) — Known-first vs full three-tier.
6. **Semantic-profile invalidation mechanism** (A-06) — immutable-in-v0.1 vs an explicit change signal.
7. Deferred items the document already lists (retain as deferred): FAILED retry policy, rate/cost protection, Karate placement, confidence thresholds, InferredPurpose (product).

## Related Records

- Task: `CAPSA-ARCH-REVIEW-001` (this review)
- Reviewed task: `CAPSA-ARCH-001` (`docs/engineering/agents/tasks/CAPSA-ARCH-001—ModularMonolithArchitectureV0.1.md`)
- Reviewed artifact: `docs/knowledge/architecture/capsa-arch-001-modular-monolith-v0.1.md` (not modified)
- Upstream: `CAPSA-DOMAIN-RECONCILE-001`, `CAPSA-DOMAIN-REVIEW-001`
