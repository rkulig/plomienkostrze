# Fixtures Schedule (S-06) Implementation Plan

## Overview

Add a public **"Terminarz"** tab showing Płomień Kostrze's season fixtures — played
results and upcoming matches — scraped from 90minut.pl and served from a short
server-side cache. Płomień's matches are **filtered out of the whole-league terminarz**
on the same league page S-05 already scrapes (`liga14875.html`), reusing the
`NinetyMinutLeagueClient.fetchPage()` seam that S-05 deliberately left for this slice.
The backend exposes a cached, unauthenticated `GET /api/fixtures`; the Angular SPA
renders it as the second guest-facing content tab, next to "Tabela".

## Current State Analysis

- **S-05 seam is ready (roadmap intent):** `league/NinetyMinutLeagueClient.java:65-71`
  has a private `Document fetchPage()` — `Jsoup.connect(leagueUrl).timeout(10s).get()`
  with `IOException` → `LeagueDataUnavailableException`. Its own Javadoc names S-06 as
  the intended reuser (`:27-28`). The client is injected with
  `@Value("${app.ninetyminut.league-url}")` (`:37`) — the exact page fixtures come from.
- **Scaffold to mirror (S-05):** `LeagueService.java` (volatile `Snapshot` TTL cache,
  scrape-on-miss, exception propagates on failure so the cache is not poisoned) →
  `web/LeagueTableController.java` (public `@GetMapping`, record DTOs via static
  `from(...)`, `LeagueDataUnavailableException` → `ResponseStatusException(502)`). This
  whole chain transfers structurally; only the parse and the DTO shape change.
- **Fixtures parse is proven-adjacent (S-03):** `news/NinetyMinutClient.parseRow` already
  reads Płomień's matches (home/away via a marker, opponent, score, "-" = unplayed) — but
  from the **team page** (`mecze_druzyna.php`), not the league page. The league-page
  terminarz has a different, simpler markup (below), so the S-03 selectors do **not**
  transfer; the home/away + opponent + result *derivation logic* does.
- **Security fails closed:** `security/SecurityConfig.java:80` (`anyRequest().denyAll()`).
  Public GETs are explicitly allow-listed (`:71-74`); `/api/league-table` is already
  there (`:72`). `/api/fixtures` needs the same treatment or it 401s.
- **Frontend precedent (S-05):** `league/league-api.ts` (root `@Injectable`, `inject
  (HttpClient)`, typed `getTable()`), `league/league-table.ts` (signals `rows`/`busy`/
  `error`, load in constructor, Polish error incl. HTTP status), `league/league-table.html`
  (`@if busy / @else if error / @else if empty / @else table`), `league/league-table.scss`
  (40rem centered column, `.table-scroll` overflow-x, compact table). The header nav
  `.app-nav` (`app.html:4-6`, `app.scss:16-32`) is already a multi-tab flex row holding
  "Tabela" — a second tab drops in with no shell TS change.
- **Live-source facts (verified 2026-07-12):** `http://www.90minut.pl/liga/1/liga14875.html`
  (current 2026/27 season, *VI liga*) carries a full round-by-round terminarz: **26 rounds**,
  each introduced by `<b><u>Kolejka N - <date range></u></b>` (e.g. "Kolejka 1 - 15-16
  sierpnia"), followed by a `<table class="main" width="600">` with one `<tr>` per match:
  `td[0]`=host, `td[1]`=score, `td[2]`=guest (all plain text, no team links, no `td.mecze2`).
  Score cell is `"-"` pre-match, `"X-Y"` once played. Verified: **every one of the 26 rounds
  contains exactly one Płomień match**; Płomień is host in odd rounds, guest in even. Late
  rounds (25, 26) currently carry **no date range** in the header. As of today the whole
  season is upcoming (all scores `"-"`, first match 2026-08-15).

## Desired End State

A guest visiting the SPA sees a **"Terminarz"** tab in the header next to "Tabela".
Clicking it shows Płomień's fixtures in one chronological table (round order = calendar
order): the round/date label, the opponent, whether the match is home or away, and the
result (Płomień-perspective score) or a dash for matches not yet played. Data is scraped
live from 90minut.pl and cached ~15 min server-side. When the source is unreachable or
unparseable, the tab shows a clear error message instead of a broken table. Verify by:
loading `/terminarz` against a locally-run backend and confirming rows match Płomień's
line in each round of the live 90minut terminarz; and `curl /api/fixtures` returning
`200` JSON without any auth.

### Key Discoveries:

- Reuse boundary is the **page fetch**, not the parse: S-06 calls the existing
  `NinetyMinutLeagueClient.fetchPage()` (same URL as standings) and adds a new parser.
- The league-page terminarz is a **sequence of round blocks**, each a `<b><u>Kolejka N …</u></b>`
  header followed by a `table.main[width=600]` of `host · score · guest` rows — nothing
  like the standings table or the S-03 team page.
- Płomień's row per round is found by **team-name text match** (`"Płomień Kostrze"`) — the
  terminarz has no team links to match on (brittleness noted in Open Risks).
- Home/away is positional: **Płomień in `td[0]` ⇒ home**, in `td[2]` ⇒ away. Opponent is
  the other cell. Score `"-"` ⇒ upcoming; `"X-Y"` ⇒ host-guest, normalized to Płomień's
  perspective for display (mirrors S-03's `MatchResult` convention).
- The header date label may be **empty** for unscheduled late rounds — the row still
  renders (round number + opponent + venue), the date cell just shows nothing.

## What We're NOT Doing

- **No database / migration / entity** — fixtures are transient (scrape + in-memory
  cache), exactly like S-05 standings.
- **No scheduled refresh / cron**, no `@EnableCaching`/`@Scheduled` — a manual TTL cache
  (copied from `LeagueService`) suffices.
- **No team-page source** (`mecze_druzyna.php`) — decided to keep the league-page source
  and reuse the S-05 seam, accepting per-round date *ranges* instead of exact dates.
- **No whole-league (all-teams) view** — the terminarz is filtered to Płomień only, shown
  Płomień-centrically (opponent, home/away).
- **No W/D/L colour badges, no goal scorers, no lineups** — compact columns only; rich
  match data is gated behind reCAPTCHA (see S-03 research).
- **No new automated tests** (no scraper unit test, no new controller slice test). The
  existing backend test suite must still pass. Matches the S-05 scope decision.
- **No new external config for the URL** — fixtures reuse `app.ninetyminut.league-url`.

## Implementation Approach

Backend first (Phase 1), then the frontend tab that consumes it (Phase 2), mirroring
S-05. Phase 1 extends the existing `NinetyMinutLeagueClient` with a `fetchFixtures()`
method that reuses its private `fetchPage()` (fulfilling the seam), plus a `FixtureRow`
model. A `FixturesService` owns a ~15 min TTL cache identical in shape to `LeagueService`
(scrape-on-miss, propagate-on-failure, no last-good fallback). A `FixturesController`
maps a scrape failure to `502` so the SPA renders an error state exactly like "Tabela".
Phase 2 clones the `league/` frontend feature into `fixtures/`, adds a `/terminarz` route
and a second nav tab, and reuses the compact-table styling.

## Critical Implementation Details

- **The terminarz parse is the one novel step.** The league page has ~60 tables; the
  fixtures live in a *sequence* of round blocks, not one table. Walk the document in order,
  tracking the current round: each `<u>` (or `<b><u>`) whose text matches `^\s*Kolejka\s+\d+`
  is a round header (its full text, e.g. "Kolejka 1 - 15-16 sierpnia", is the round label);
  the round's matches are in the **immediately following** `table.main[width=600]`. In that
  table, the `<tr>` whose text contains `"Płomień Kostrze"` is Płomień's match — read
  `td[0]`=host, `td[1]`=score, `td[2]`=guest (mind the leading/trailing whitespace 90minut
  pads cells with; `strip()` everything). One Płomień row per round (verified 26/26).
- **Home/away + opponent + score normalization:** Płomień is home iff `td[0]` text contains
  `"Płomień Kostrze"`; opponent is the other cell's text; `score` cell `"-"` (or empty) ⇒
  upcoming (`played=false`, no goals). A played score parses as `host-guest`; store
  Płomień-perspective `goalsFor`/`goalsAgainst` by swapping when Płomień is the guest
  (mirror `news/NinetyMinutClient.parseRow:123-129`).
- **Charset:** inherited for free — `fetchPage()` already lets jsoup auto-detect `ISO-8859-2`.
  Polish names ("Płomień Kostrze (Kraków)", "Grębałowianka Kraków") must round-trip.
- **Cache semantics:** identical to `LeagueService` — on a miss the service scrapes; if the
  scrape throws, the exception propagates, the cache is **not** updated, stale data is
  **not** served, and the controller returns 502.
- **Empty-result guard:** if the parse yields **zero** rows (e.g. the terminarz markup
  changed and the Płomień match match failed everywhere), throw
  `LeagueDataUnavailableException` rather than caching an empty list — an empty terminarz
  is indistinguishable from a silent parse break and should surface as 502, not "brak danych".

## Phase 1: Backend — Płomień fixtures scrape + cached public endpoint

### Overview

Extend the existing league-page client with a fixtures parser (reusing its `fetchPage()`
seam), add a `FixtureRow` model, a TTL-cache service, a public read controller, the
security allow-list entry, and one cache-TTL config key.

### Changes Required:

#### 1. Fixtures parser on the league client

**File**: `backend/src/main/java/com/plomienkostrze/league/NinetyMinutLeagueClient.java`

**Intent**: Add Płomień-fixtures parsing to the existing league-page client, reusing its
private `fetchPage()` — this is the reuse the S-05 seam was built for. Standings parsing
is untouched.

**Contract**: add `public List<FixtureRow> fetchFixtures()`. It calls the existing private
`fetchPage()`, walks the terminarz round blocks per the Critical Implementation Details,
and returns one `FixtureRow` per round that contains a Płomień match (chronological, i.e.
round order). If the walk yields zero rows, throw `LeagueDataUnavailableException` (empty-
result guard). Unparseable individual rows are skipped. Add a hardcoded team-name constant
(e.g. `"Płomień Kostrze"`) used for the text match — no new config key.

#### 2. Fixture row model

**File**: `backend/src/main/java/com/plomienkostrze/league/FixtureRow.java` (new)

**Intent**: The parsed shape of one Płomień fixture (compact, Płomień-centric).

**Contract**: `record FixtureRow(String round, String opponent, boolean home, boolean played,
Integer goalsFor, Integer goalsAgainst)`. `round` is the header label (e.g. "Kolejka 1 -
15-16 sierpnia", possibly just "Kolejka 25" when the date range is absent). `goalsFor`/
`goalsAgainst` are null when `played` is false, and are Płomień-perspective when true.
Transient — not a JPA entity.

#### 3. Caching service

**File**: `backend/src/main/java/com/plomienkostrze/league/FixturesService.java` (new)

**Intent**: Serve fixtures from a short in-memory TTL cache, scraping via the client on a
miss. Structural clone of `LeagueService` (minus the pre-season shuffle, which is
standings-only).

**Contract**: `@Service` with `List<FixtureRow> getFixtures()`. Holds a `volatile` cached
snapshot (rows + `Instant fetchedAt`); returns it when `age < ttl`, else calls
`client.fetchFixtures()`, updates the cache, and returns. On scrape failure the exception
propagates and the cache is not updated. TTL from
`@Value("${app.ninetyminut.fixtures-cache-ttl}")` as a `Duration`.

#### 4. Public controller

**File**: `backend/src/main/java/com/plomienkostrze/web/FixturesController.java` (new)

**Intent**: Expose the fixtures to guests as a public read endpoint, mapping scrape
failures to a gateway error the SPA can render. Structural clone of `LeagueTableController`.

**Contract**: `@RestController @RequestMapping("/api/fixtures")`, `@GetMapping` →
`FixturesResponse(List<RowResponse> rows)` where `RowResponse(String round, String opponent,
boolean home, boolean played, Integer goalsFor, Integer goalsAgainst)` has a static
`from(FixtureRow)`. Catches `LeagueDataUnavailableException` →
`ResponseStatusException(HttpStatus.BAD_GATEWAY, …)` and logs a warning (mirror
`LeagueTableController:55-58`).

#### 5. Security allow-list

**File**: `backend/src/main/java/com/plomienkostrze/security/SecurityConfig.java`

**Intent**: Permit anonymous GET to the new endpoint; without this the fail-closed chain
returns 401/403.

**Contract**: add `.requestMatchers(HttpMethod.GET, "/api/fixtures").permitAll()` alongside
the existing public-GET matchers (next to the `/api/league-table` line, `:72`), before
`anyRequest().denyAll()`.

#### 6. Configuration

**File**: `backend/src/main/resources/application.properties`

**Intent**: Externalize the fixtures cache TTL. The league page URL is **reused** from the
existing `app.ninetyminut.league-url` — no new URL key.

**Contract**: add
`app.ninetyminut.fixtures-cache-ttl=${NINETYMINUT_FIXTURES_CACHE_TTL:PT15M}`, grouped with
the existing `app.ninetyminut.*` keys.

### Success Criteria:

#### Automated Verification:

- Backend compiles: `./mvnw -q -f backend/pom.xml compile` (JDK 21; see `backend/CLAUDE.md`)
- Existing test suite still passes: `./mvnw -q -f backend/pom.xml test`

#### Manual Verification:

- With the backend running locally, `curl -s localhost:8080/api/fixtures` returns `200` and
  a JSON `rows` array whose entries match Płomień's line in each round of the live 90minut
  terminarz (Polish characters intact); home/away and any played scores are correct from
  Płomień's perspective.
- The endpoint is reachable with **no** auth token (public).
- A second request within the TTL does not re-scrape (observed via timing/logs).
- Forcing a failure (temporarily point `NINETYMINUT_LEAGUE_URL` at a bad URL) yields `502`,
  not `500` or a stack trace.

**Implementation Note**: After Phase 1 automated verification passes, I will stand up a
local backend myself (per `context/foundation/lessons.md` — no user login needed for a
public read) and run the manual checks, then pause for confirmation before Phase 2.

---

## Phase 2: Frontend — public "Terminarz" tab

### Overview

A new `fixtures/` Angular feature (API service + model + standalone component), a
`/terminarz` lazy route, the second guest nav tab, and a compact table reusing the S-05
table styling.

### Changes Required:

#### 1. API service + model

**File**: `frontend/src/app/fixtures/fixtures-api.ts` (new)

**Intent**: Single point of HTTP contact for the fixtures, mirroring `league/league-api.ts`.

**Contract**: `@Injectable({providedIn:'root'})`, `inject(HttpClient)`,
`getFixtures(): Observable<Fixtures>` → `GET ${environment.apiBaseUrl}/api/fixtures`.
Co-located interfaces `FixtureRow { round: string; opponent: string; home: boolean; played:
boolean; goalsFor: number | null; goalsAgainst: number | null }` and `Fixtures { rows:
FixtureRow[] }`.

#### 2. Fixtures component

**File**: `frontend/src/app/fixtures/fixtures.ts` (new)

**Intent**: Render the fixtures with loading/error/empty states, following the
`league-table.ts` signal pattern.

**Contract**: standalone `Fixtures` component (`selector: 'app-fixtures'`,
`templateUrl`/`styleUrl`), `inject(FixturesApi)`, signals `rows`, `busy`, `error`; loads in
the constructor and flips signals on `subscribe` next/error (Polish error string incl. HTTP
status, e.g. `Nie udało się pobrać terminarza (HTTP ${err.status})`).

#### 3. Component template

**File**: `frontend/src/app/fixtures/fixtures.html` (new)

**Intent**: A compact `<table>` of Płomień's fixtures with `@if` guards for busy/error/empty.

**Contract**: `@if (busy())` / `@else if (error())` / `@else if (empty)` / `@else` blocks
mirroring `league-table.html`. Columns: **Data** (the `round` label), **Przeciwnik**
(`opponent`), **Miejsce** (home ⇒ "dom", away ⇒ "wyjazd"), **Wynik** (`played` ⇒
`{{goalsFor}}–{{goalsAgainst}}`, else "–"). `@for (row of rows(); track $index)` (rounds are
unique but `round` text can repeat if a range is blank — track by index). Empty state "Brak
danych" when `rows()` is empty and not busy/errored.

#### 4. Component styles

**File**: `frontend/src/app/fixtures/fixtures.scss` (new)

**Intent**: Compact 4-column table matching "Tabela". Reuse the S-05 styling wholesale.

**Contract**: same structure as `league/league-table.scss` — a `.fixtures` wrapper (40rem
centered column, `.note`/`.error`, `.table-scroll { overflow-x:auto }`, `border-collapse`
table with `1px solid #eee` dividers, `#666` header text). Opponent column gets the
`.team`-style flexible/wrapping width; Miejsce/Wynik stay compact.

#### 5. Route

**File**: `frontend/src/app/app.routes.ts`

**Intent**: Register the lazy `/terminarz` route.

**Contract**: add `{ path: 'terminarz', loadComponent: () => import('./fixtures/fixtures')
.then(m => m.Fixtures) }` alongside the `tabela` route, **before** the `**` wildcard.

#### 6. Navigation tab

**File**: `frontend/src/app/app.html`

**Intent**: Add the second guest content tab to the header. `.app-nav` already exists and is
a flex row — no `app.ts` change, no new `app.scss` (the existing `.app-nav a` / `.active`
rules cover it).

**Contract**: add `<a routerLink="/terminarz" routerLinkActive="active">Terminarz</a>`
inside the existing `<nav class="app-nav">` (`app.html:4-6`), after the "Tabela" link.

### Success Criteria:

#### Automated Verification:

- Frontend production build succeeds: `cd frontend && npm run build` (run `nvm use` first —
  Node 24.18.0, see `frontend/CLAUDE.md`)

#### Manual Verification:

- A "Terminarz" tab is visible in the header for a guest (not logged in) and routes to
  `/terminarz`; "Tabela" still works.
- The table renders Data · Przeciwnik · Miejsce · Wynik with data matching the backend
  response; Polish names render correctly; upcoming matches show "–", the round-only rows
  (no date range) still render.
- Loading state shows briefly; stopping the backend produces the error message (not a
  blank/broken table).
- The compact table is readable at mobile width (scrolls rather than overflowing).
- No regressions: news list/detail, "Tabela", and existing header controls still work.

**Implementation Note**: After Phase 2 automated verification, I will stand up the full
local stack myself (backend + `npm start`) and run the manual checks — no user login is
required for this public tab.

---

## Testing Strategy

Per the agreed scope (matching S-05), **no new automated tests** are added in this slice.

### Manual Testing Steps:

1. Run the backend locally; `curl localhost:8080/api/fixtures` → `200` JSON; confirm a few
   rows match Płomień's line in the live 90minut terminarz (round, opponent, home/away, and
   any played score from Płomień's perspective).
2. Confirm anonymous access (no `Authorization` header) works.
3. Hit the endpoint twice quickly; confirm the second is served from cache.
4. Point `NINETYMINUT_LEAGUE_URL` at a bad URL; confirm `502` and a clean error.
5. Run `npm start`; open `/terminarz` via the new header tab; confirm rendered rows match
   the backend and Polish characters are intact.
6. Stop the backend; reload `/terminarz`; confirm the error state renders.
7. Confirm news views, "Tabela", and existing header controls are unaffected.

## Performance Considerations

The ~15 min in-memory TTL cache keeps guest traffic off 90minut and makes cached reads
effectively instant. A cache miss pays one jsoup fetch (10 s timeout). Standings and
fixtures each hold an independent cache over the *same* page — a concurrent miss on both
double-fetches the page briefly (acceptable at this scale, same tradeoff S-05 accepted). No
pagination — a season is ~26 rows.

## Migration Notes

None — nothing is persisted.

## References

- Roadmap slice: `context/foundation/roadmap.md` (S-06)
- S-05 seam + scaffold to reuse: `backend/src/main/java/com/plomienkostrze/league/NinetyMinutLeagueClient.java:65-71`, `.../league/LeagueService.java`, `.../web/LeagueTableController.java`
- Home/away + score normalization to mirror: `backend/src/main/java/com/plomienkostrze/news/NinetyMinutClient.java:123-129`
- Public read + security patterns: `backend/src/main/java/com/plomienkostrze/security/SecurityConfig.java:72,80`
- Frontend patterns to clone: `frontend/src/app/league/league-api.ts`, `league/league-table.ts`, `league/league-table.html`, `league/league-table.scss`, `app.routes.ts`, `app.html:4-6`
- 90minut structure + league URL: `context/archive/2026-07-08-gated-news-generation/research-scraping-90minut.md`, `context/archive/2026-07-12-league-table/plan.md`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Backend — Płomień fixtures scrape + cached public endpoint

#### Automated

- [x] 1.1 Backend compiles (`./mvnw -q -f backend/pom.xml compile`) — 7a2543f
- [x] 1.2 Existing test suite still passes (`./mvnw -q -f backend/pom.xml test`) — 7a2543f

#### Manual

- [x] 1.3 `curl /api/fixtures` returns 200 JSON matching Płomień's line per round (Polish intact, home/away + score correct) — 7a2543f
- [x] 1.4 Endpoint reachable with no auth token (public) — 7a2543f
- [x] 1.5 Second request within TTL served from cache (no re-scrape) — 7a2543f
- [x] 1.6 Bad `NINETYMINUT_LEAGUE_URL` yields 502, not 500/stack trace — 7a2543f

### Phase 2: Frontend — public "Terminarz" tab

#### Automated

- [x] 2.1 Frontend production build succeeds (`cd frontend && npm run build`) — 6c91113

#### Manual

- [x] 2.2 "Terminarz" tab visible for a guest and routes to `/terminarz`; "Tabela" still works — 6c91113
- [x] 2.3 Table renders Data · Przeciwnik · Miejsce · Wynik matching backend; Polish names correct; upcoming show "–" — 6c91113
- [x] 2.4 Loading state shows; backend down produces the error message — 6c91113
- [x] 2.5 Compact table readable at mobile width — 6c91113
- [x] 2.6 No regressions in news views, "Tabela", or existing header controls — 6c91113
