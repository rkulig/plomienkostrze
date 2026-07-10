<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: News Post Management (edit + delete)

- **Plan**: context/changes/news-post-management/plan.md
- **Scope**: Phases 1–2 of 3 (Phase 3 — production deploy — pending)
- **Date**: 2026-07-10
- **Verdict**: APPROVED
- **Findings**: 0 critical · 0 warnings · 3 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | PASS |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

## Automated verification

- `cd backend && ./mvnw -B verify` → PASS (exit 0)
- `cd frontend && npm run build` → PASS (exit 0; post-edit lazy chunk emitted)

## Findings

### F1 — `update`/`delete` don't filter by PUBLISHED status while `get` does

- **Severity**: 🔵 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Architecture
- **Location**: backend/src/main/java/com/plomienkostrze/web/NewsPostController.java:118, 131
- **Detail**: `get` (line 104-110) filters `post.getStatus() == PUBLISHED` before returning; `update` and `delete` operate on any post by id with no status filter. Harmless today because `NewsPostStatus` only ever holds `PUBLISHED` (the sole write path is `NewsPost.published()`); no DRAFT row is ever persisted. Becomes relevant only if a DRAFT/unpublished write path is added later.
- **Fix**: No change needed for S-04. When a non-PUBLISHED state is introduced, revisit whether edit/delete should be status-scoped to match `get`.
- **Decision**: SKIPPED — accepted as correct for this slice

### F2 — TOCTOU between `existsById` and `deleteById` in `delete`

- **Severity**: 🔵 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: backend/src/main/java/com/plomienkostrze/web/NewsPostController.java:132-135
- **Detail**: Two concurrent DELETEs could both pass `existsById` and then both call `deleteById`; in Spring Data the second is a benign no-op (worst case a redundant 204 instead of 404). This is the idiomatic check-then-delete pattern used to return a clean 404 and matches the inline-404 style elsewhere in the file. The product is single-admin, so the race is effectively unreachable.
- **Fix**: No change needed; flagged for completeness.
- **Decision**: SKIPPED — accepted as benign in a single-admin product

### F3 — Benign extras beyond plan text (defensive, consistent)

- **Severity**: 🔵 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Scope Discipline
- **Location**: backend/.../NewsPostController.java:133; frontend/src/app/admin/post-edit.ts:62-68
- **Detail**: Two small additions not literally spelled out in the plan: (a) `DELETE` reuses the `"news post not found"` 404 message (plan specified it only for `PUT`); (b) `post-edit.ts` adds a load-error branch (404/400 → notFound, else → error signal) for the initial `NewsApi.get(id)`. Both are defensive and mirror the existing `news-detail.ts` 404/400 handling — they tighten behavior rather than expand scope.
- **Fix**: Accept as-is; no action.
- **Decision**: SKIPPED — accepted as defensive and consistent

## Notes

- Manual success criteria for Phases 1–2 are checked in the plan's Progress section (commits af442c1, 8522dff) and are supported by the code: security matchers gate PUT/DELETE (`SecurityConfig.java:77-78`), inline 404s cover the not-found paths, and the frontend gates actions on resolved `isAdmin() === true`.
- Phase 3 (production deploy + prod e2e) is intentionally out of scope for this review — it runs after merge to `master`.
