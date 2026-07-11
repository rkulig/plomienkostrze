# Backend Test Harness — Access Gate, Publish Gate & CORS (Phase 1) — Plan Brief

> Full plan: `context/changes/testing-backend-access-publish-gate/plan.md`
> Research: `context/changes/testing-backend-access-publish-gate/research.md`

## What & Why

Rollout Phase 1 of the test plan: stand up the backend integration-test runner and pin the
project's three cheapest-to-protect top risks — **#1 authorization** (guest/fan must not
reach admin ops), **#2 publish gate** (nothing goes public without an explicit admin
publish), **#4 CORS/security orthogonality** (a legal Origin must not bypass the token) — at
the cheapest useful layer, a `@WebMvcTest` MockMvc slice. Also wire the "backend tests" CI
gate so these regressions block a merge.

## Starting Point

Authorization is centralized and **correct today** (one `authorizeHttpRequests` block,
`SecurityConfig.java:66-79`); there is **no DRAFT status** and `/generate` **persists
nothing**; public read is PUBLISHED-only at the controller. The only existing test is a bare
`contextLoads()`. `spring-security-test` is **missing** from `pom.xml`, and CI already runs
`./mvnw -B verify` on every PR. So these tests are **regression guards**, not bug fixes.

## Desired End State

Three slice test classes run under `./mvnw -B verify` (locally + PR CI) and fail if the authz
matrix is weakened, public read leaks non-PUBLISHED content, `/generate` starts persisting, or
a CORS change lets a request skip the token. The test-plan doc reflects the shipped state
(gate active, risk #2 reframed, cookbook filled).

## Key Decisions Made

| Decision | Choice | Why | Source |
| --- | --- | --- | --- |
| Test layer | `@WebMvcTest` slice, no DB/Firebase | Cheapest layer that gives real signal; `jwt()` bypasses the decoder | Research |
| Test layout | Split by risk (3 classes) | 1:1 with risk map; clean failure attribution + cookbook examples | Plan |
| Role matrix form | Parameterized (`@MethodSource`) | Matrix is data; structurally hard to omit anon-401/non-admin-403 rows | Plan |
| No-save oracle (#2) | `verifyNoInteractions(repository)` | DB-observable "list unchanged" is a Phase-2 property; mock-verify is the right Phase-1 oracle | Plan |
| Risk #2 backport | Apply reframe to §2 now | Research recommends — it changes what the tests assert | Research/Plan |
| PUBLISHED filter at DB | Deferred to Phase 2 | Repo derived-query needs Testcontainers PG | Research |
| CORS depth | Preflight headers **and** orthogonality, same slice | Cheap to assert both; header-alone is the anti-pattern | Research |
| CI gate | Doc activation, no YAML edit | `verify` already runs Surefire on PR and fails on test failure | Plan |

## Scope

**In scope:** add `spring-security-test`; 3 slice test classes (authz matrix, publish gate,
CORS orthogonality); activate §5 gate; §2 backport; cookbook §6.1/§6.2/§6.6.

**Out of scope:** `@DataJpaTest`/Testcontainers (Phase 2), `@SpringBootTest`, any production
code change, workflow YAML edits, risks #3/#5/#6/#7, asserting generated prose.

## Architecture / Approach

Three `@WebMvcTest` classes, each importing the real `SecurityConfig` (+ `CorsConfig` for #4)
and `@MockBean`-ing the repository/generation service. Principals via
`SecurityMockMvcRequestPostProcessors`: anonymous (none), non-admin (`jwt()`), admin
(`jwt().authorities("ROLE_ADMIN")` — injected directly, since test props leave the admin
allowlist empty). Risk #1 is a parameterized principal × endpoint matrix; #2 asserts the
`findByStatus(PUBLISHED)` call + `verifyNoInteractions` after `/generate` (with a 201-publishes
contrast row); #4 pairs a preflight-header assertion with a protected-path 401/403.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Harness prereq | `spring-security-test` + shared slice/jwt helpers, smoke assertion | Boot 4 modular test deps resolve as expected |
| 2. Authz matrix (#1) | `AuthorizationMatrixTest` — anon/non-admin/admin × all writes | Missing the anon-401 / non-admin-403 rows |
| 3. Publish gate (#2) | `PublishGateTest` — PUBLISHED-only read + generate-no-save | Oracle trap: 200-no-save vs 201-publishes |
| 4. CORS (#4) | `CorsOrthogonalityTest` — preflight + orthogonal rejection | Asserting the CORS header alone |
| 5. Gate + docs | §5 gate active, §2 reframe, cookbook §6.1/6.2/6.6 | Editing the frozen §1–§5 strategy block |

**Prerequisites:** JDK 21 + `./mvnw` (per `backend/CLAUDE.md`); no GCP/Firebase access needed.
**Estimated effort:** ~1–2 sessions across 5 sub-phases (small, mostly additive).

## Open Risks & Assumptions

- Assumes `spring-security-test`'s version is managed by the Boot 4.1 parent BOM (no explicit
  version needed) — verify on first `test-compile`.
- The "non-published row exists → 404" branch of the detail filter is unreachable today
  (enum has only `PUBLISHED`); its DB-level assertion is deferred to Phase 2.
- Editing the frozen §1–§5 strategy block (backport #1) is deliberate and research-endorsed.

## Success Criteria (Summary)

- `./mvnw -B verify` green locally and in the PR `build-test` job; the three suites would fail
  under a real regression (authz downgrade, non-PUBLISHED leak, generate-persists, CORS bypass).
- Test-plan §5 gate active, §2 risk #2 reframed, cookbook §6.1/§6.2/§6.6 filled — Phase 1
  status `complete`, ready to resume at rollout Phase 2.
