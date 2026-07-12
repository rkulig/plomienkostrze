# Fixtures Schedule (S-06) — Plan Brief

> Full plan: `context/changes/fixtures-schedule/plan.md`

## What & Why

Give guests a **"Terminarz"** tab showing Płomień Kostrze's season fixtures — played
results and upcoming matches — scraped live from 90minut.pl. It's the sibling of the
S-05 "Tabela" tab: the roadmap sequenced S-06 right after it so the two share one scraper
seam and the same public-tab pattern.

## Starting Point

S-05 shipped a `league/` backend package whose `NinetyMinutLeagueClient.fetchPage()` was
deliberately left as a reusable seam for S-06, plus a TTL-cache → public-endpoint → tab
scaffold and a compact-table frontend feature. The header nav already holds one guest tab
("Tabela"). Nothing shows fixtures yet.

## Desired End State

A guest clicks "Terminarz" and sees Płomień's fixtures in one chronological table: round/
date label, opponent, home-or-away, and the result (or a dash for upcoming matches). Data
is cached ~15 min server-side; a scrape failure renders a clean error, not a broken table.

## Key Decisions Made

| Decision              | Choice                                                     | Why (1 sentence)                                                                 | Source |
| --------------------- | ---------------------------------------------------------- | -------------------------------------------------------------------------------- | ------ |
| Data source           | Whole-league page `liga14875.html`, filtered to Płomień    | Reuses the S-05 `fetchPage()` seam exactly as the roadmap intended.               | Plan   |
| Presentation          | Płomień-centric compact row (opponent, home/away, result)  | It's a club site — a fan wants "our next match", not a wall of all-team fixtures. | Plan   |
| Layout                | Single chronological table, results inline                 | Simplest; mirrors the S-05 single-table view and the source's own round order.   | Plan   |
| Columns               | Data · Przeciwnik · Miejsce · Wynik                        | Compact, mobile-readable, matches the S-05 table.                                | Plan   |
| Date granularity      | Per-round label ("Kolejka 1 - 15-16 sierpnia")             | The league terminarz only carries round date *ranges*, not exact per-match dates. | Plan   |
| Score display         | Normalized to Płomień's perspective                        | Reads naturally next to a home/away column (mirrors S-03's `MatchResult`).        | Plan   |
| Persistence / tests   | None — transient scrape + in-memory cache, no new tests    | Identical scope call to S-05.                                                     | Plan   |

## Scope

**In scope:**
- Backend: `fetchFixtures()` on the existing league client (reuses `fetchPage()`), a
  `FixtureRow` model, a `FixturesService` TTL cache, a public `GET /api/fixtures`
  controller, a security allow-list entry, one cache-TTL config key.
- Frontend: a `fixtures/` feature (api + component + template + styles), a `/terminarz`
  route, and a second nav tab.

**Out of scope:**
- Team-page source (kept the league page); all-teams view; exact per-match dates.
- W/D/L colour badges, scorers, lineups (gated data — see S-03 research).
- Any database/migration; new automated tests; a new source URL (reuses `league-url`).

## Architecture / Approach

Backend-first, then the tab that consumes it. Phase 1 walks the league page's terminarz —
a sequence of `<b><u>Kolejka N …</u></b>` round headers each followed by a
`table.main[width=600]` of `host · score · guest` rows — picks Płomień's row per round
(text match), derives opponent + home/away + Płomień-perspective score, caches ~15 min, and
serves it public at `/api/fixtures` (502 on scrape failure). Phase 2 clones the `league/`
frontend feature into `fixtures/`, adds the route and tab, and reuses the compact-table SCSS.

## Phases at a Glance

| Phase                        | What it delivers                                          | Key risk                                                        |
| ---------------------------- | -------------------------------------------------------- | -------------------------------------------------------------- |
| 1. Backend fixtures endpoint | Cached public `GET /api/fixtures` of Płomień's fixtures  | Novel terminarz parse; team matched by name-text (no links).   |
| 2. Frontend Terminarz tab    | `/terminarz` tab rendering the compact fixtures table    | Low — near-clone of the S-05 "Tabela" feature.                 |

**Prerequisites:** S-05 (done) — its `league/` client, seam, and frontend pattern must exist.
**Estimated effort:** ~1 session across 2 phases (a tight mirror of S-05).

## Open Risks & Assumptions

- **Parse brittleness:** the terminarz has no team links, so Płomień's row is found by
  name-text match — more fragile than the standings' `a.main` link. A source-markup change
  breaks it; the empty-result guard (0 rows → 502) turns a silent break into a visible error.
- **Date is a per-round range, sometimes blank** (late rounds). Rows still render with round
  number + opponent + venue; the date cell is just empty. Accepted as the cost of the
  league-page source.
- **Assumption:** Płomień appears exactly once per round (verified 26/26 today) and stays a
  single-division, double round-robin season.

## Success Criteria (Summary)

- A guest sees a working "Terminarz" tab whose rows match Płomień's line in each round of
  the live 90minut terminarz, with correct home/away and results.
- `GET /api/fixtures` returns cached JSON with no auth; a source failure yields 502 and a
  clean SPA error state — no regressions to news or "Tabela".
