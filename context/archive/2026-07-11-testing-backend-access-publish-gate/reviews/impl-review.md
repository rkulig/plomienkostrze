<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Backend Test Harness — Access Gate, Publish Gate & CORS/Security

- **Plan**: context/changes/testing-backend-access-publish-gate/plan.md
- **Scope**: Full plan (Phases 1–5 of 5)
- **Date**: 2026-07-11
- **Verdict**: APPROVED
- **Findings**: 0 critical, 1 warning, 2 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | WARNING |
| Safety & Quality | PASS |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

## Findings

### F1 — p1 commit bundled CLAUDE.md + 4 frontend files outside the change scope

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Scope Discipline
- **Location**: commit 27813eb (CLAUDE.md, frontend/angular.json, frontend/eslint.config.js, frontend/package.json, frontend/package-lock.json)
- **Detail**: The plan's "What We're NOT Doing" scopes production changes to "the pom.xml test-dependency add and doc edits." Commit 27813eb (Phase 1) also carries a CLAUDE.md rewrite (10x-cli lesson block) and four frontend files (~1900 lines, Vitest/eslint setup). These are pre-existing dirty paths unrelated to the test harness. The commit body documents this explicitly ("Also bundled at user request … unrelated to p1"), so it was a conscious, user-approved "Stage all" during the dirty-path prompt — not hidden creep. Already in history; nothing breaks.
- **Fix**: None required — accept as documented. For future phases, prefer the "stage only the planned set" option so unrelated dirty paths stay out of change commits.
- **Decision**: ACCEPTED — already in history and documented in the commit body; no action.

### F2 — HarnessSmokeTest retained though the plan said its assertion "need not survive"

- **Severity**: 🔵 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: backend/src/test/java/com/plomienkostrze/web/HarnessSmokeTest.java:37
- **Detail**: The plan (Phase 1 §2) states the smoke assertion "is folded into Phase 2's matrix and need not survive as its own test." Implementation kept HarnessSmokeTest as a standing class, so the anonymous-write→401 assertion now exists in both HarnessSmokeTest and AuthorizationMatrixTest (the ANONYMOUS → POST /api/news-posts row). Benign — its javadoc frames it as a fail-fast harness guard, which is a defensible choice, not drift. Minor redundancy only.
- **Fix**: Keep it (fail-fast harness guard) or delete it as the plan permitted — reviewer's call; no action needed.
- **Decision**: FIXED — removed HarnessSmokeTest.java (git rm); anon-write→401 stays covered by AuthorizationMatrixTest. Suite green: 33 tests, BUILD SUCCESS.

### F3 — anyRequest().denyAll() fall-through is not asserted by a truly-unmatched path

- **Severity**: 🔵 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Scope Discipline
- **Location**: backend/src/main/java/com/plomienkostrze/security/SecurityConfig.java:79 (guard); AuthorizationMatrixTest.java (coverage)
- **Detail**: SecurityConfig ends with `anyRequest().denyAll()`. The plan claims this is "implicitly covered by /api/me for anon → 401," but `/api/me` matches the explicit `.authenticated()` rule (line 74) and never reaches denyAll — so the rationale is slightly inexact and no test exercises a path that actually falls through to denyAll (e.g. GET /api/unknown). This is an intentionally-narrow Phase 1 scope, not a defect; a future write endpoint added without a rule would still be caught only if it happens to fall through, which is untested today.
- **Fix**: Optionally add one matrix row for an unmatched path (e.g. anonymous GET /api/does-not-exist → 401/403) to pin the denyAll default; low priority, defer to a later phase.
- **Decision**: FIXED — added a `GET /api/unknown` row with a new `DENY_ALL` access class (401 anon, 403 for any token incl. admin). Matrix now 27 rows (9 endpoints × 3 principals); suite 36 tests, BUILD SUCCESS. The denyAll-denies-admin behavior is now asserted, not assumed.
