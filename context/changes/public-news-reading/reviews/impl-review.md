<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Publiczna lista i widok opublikowanych aktualności

- **Plan**: context/changes/public-news-reading/plan.md
- **Scope**: Phase 3 of 3 (full plan — all phases complete)
- **Date**: 2026-07-05
- **Verdict**: APPROVED
- **Findings**: 0 critical, 2 warnings, 5 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

## Evidence summary

- **Drift**: all 11 planned items MATCH (migrations V2–V4, entity/enum/repo, controller, NewsApi, list/detail components, routing/app shell, probe deletions). Only diff content outside the plan: tool-managed CLAUDE.md lesson block and a 2-line `app.scss` consequence of the planned header link. No "What We're NOT Doing" violations.
- **Automated criteria re-run at review time**: `./mvnw test` green; `npm run build` green (lazy chunks news-list/news-detail present); probe grep zero hits.
- **Production spot-check (curl)**: `/api/test-messages` → 404, `/api/ping` → `{"status":"ok"}`, `/api/news-posts?size=2` serves PUBLISHED items with excerpts. Corroborates manual items 3.6/3.7. Item 3.5 (min-instances=1) not verifiable from this machine — user-attested.
- **Security**: clean — no SQL injection surface (derived queries only), no `innerHTML`/sanitizer bypass (interpolation only), CORS property-driven and non-wildcard, detail endpoint hides non-PUBLISHED behind uniform 404 (no draft-existence leakage for S-02).

## Findings

### F1 — Detail view collapses every error into "Nie znaleziono wpisu"

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: frontend/src/app/news/news-detail.ts:43
- **Detail**: The error callback sets `notFound` for *every* failure. A backend 500 or network outage (status 0) renders "Nie znaleziono wpisu." — factually wrong and misleading. `news-list.ts:46-48` distinguishes statuses (`HTTP ${err.status}`), so the change is also internally inconsistent in error-message style.
- **Fix**: Treat `err.status === 404` (and 400) as not-found; everything else sets a generic error state ("Nie udało się pobrać wpisu"), mirroring the news-list pattern.
- **Decision**: FIXED — status-aware error handling + error branch in template applied during triage.

### F2 — No wildcard route: unknown deep links throw NG04002

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: frontend/src/app/app.routes.ts
- **Detail**: Firebase Hosting rewrites everything to `index.html`, so any unknown path (`/foo`, typoed shared URL) reaches the router, which throws `NG04002: Cannot match any routes` and leaves an empty outlet.
- **Fix**: Add `{ path: '**', redirectTo: '' }` as the last route.
- **Decision**: FIXED — wildcard redirect added during triage.

### F3 — Nothing enforces published_at NOT NULL for PUBLISHED rows

- **Severity**: 💬 OBSERVATION
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: backend/src/main/resources/db/migration/V2__create_news_posts.sql:10
- **Detail**: `published_at` is nullable by design (future drafts), but a PUBLISHED row with NULL `published_at` would sort *first* (Postgres `DESC` defaults to NULLS FIRST) and render an empty date in the SPA. Zero impact today — the only writer is the V3 seed — but S-02 admin tools will start inserting rows.
- **Fix A ⭐ Recommended**: Defer — record the invariant as a requirement for the S-02 plan (publish action must set `published_at`).
  - Strength: No new migration in a merged slice; S-02 owns the write path and is the natural place to enforce it.
  - Tradeoff: Invariant lives in a plan note, not the schema, until S-02.
  - Confidence: HIGH — no code path can violate it today.
  - Blind spot: S-02 plan doesn't exist yet; the note must actually make it in.
- **Fix B**: Add `V5__news_posts_published_check.sql` with `CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL)` now.
  - Strength: Schema enforces the invariant regardless of future code.
  - Tradeoff: An extra migration + deploy for a condition nothing can currently trigger.
  - Confidence: MEDIUM — safe, but premature relative to slice scope.
  - Blind spot: H2 test profile compatibility of the CHECK syntax unverified.
- **Decision**: FIXED via Fix A — invariant recorded in `follow-ups/review-fixes.md` as a requirement for the S-02 plan.

### F4 — "Pokaż starsze" can append a duplicate on page drift

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: frontend/src/app/news/news-list.ts:34
- **Detail**: Offset pagination — if a new post is published between clicks, the next page re-serves the last item of the previous page; with `track post.id` a duplicate triggers `NG0955`. Double-click is already guarded by `busy()`. Low likelihood on a low-churn club site.
- **Fix**: Dedupe by id when appending (`items.update(cur => [...cur, ...page.items.filter(p => !cur.some(c => c.id === p.id))])`).
- **Decision**: FIXED — dedupe-by-id applied during triage.

### F5 — /news/abc burns a doomed HTTP request

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: frontend/src/app/news/news-detail.ts:37
- **Detail**: `Number('abc')` → `NaN` → request to `/api/news-posts/NaN` → backend 400 → not-found message. End behavior is acceptable; the round-trip is just wasted.
- **Fix**: Guard `Number.isInteger(id) && id > 0` before calling the API; else set `notFound` directly.
- **Decision**: FIXED — client-side id guard applied during triage.

### F6 — No retry path after initial list-load failure

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: frontend/src/app/news/news-list.ts:46
- **Detail**: On first-page error `items=0, total=0`, so `hasMore()` is false and the "Pokaż starsze" button never renders — the user's only recourse is a full page reload.
- **Fix**: Show a "Spróbuj ponownie" button in the error state that calls `loadMore()` again.
- **Decision**: SKIPPED — conscious low-impact skip; full page reload is an acceptable recourse for now.

### F7 — List query hydrates full content to compute excerpts

- **Severity**: 💬 OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: backend/src/main/java/com/plomienkostrze/web/NewsPostController.java:68
- **Detail**: The list endpoint loads full `content` (up to 10 KB/row, capped at 50 rows) to derive a 200-char excerpt in Java. Matches the plan (server-computed excerpts) and is fine at this scale; a JPA projection would avoid shipping full bodies if volume grows.
- **Fix**: Accept as-is; revisit with a projection if post volume grows.
- **Decision**: ACCEPTED — matches plan and scale; revisit with a JPA projection if post volume grows.
