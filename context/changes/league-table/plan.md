# League Table (S-05) Implementation Plan

## Overview

Add a public **"Tabela"** tab showing the current league standings for Płomień
Kostrze, scraped from 90minut.pl — mirroring how S-03 scrapes the match result and
how S-01 serves public reads. The backend exposes a cached, unauthenticated
`GET /api/league-table` that scrapes the configured league page; the Angular SPA
renders it as the first guest-facing content tab.

## Current State Analysis

- **Scraping precedent (S-03):** `backend/.../news/NinetyMinutClient.java` is a jsoup
  `@Component` that fetches one static 90minut page (`Jsoup.connect(url).timeout(10s).get()`),
  relies on jsoup's charset auto-detection for `ISO-8859-2`, externalizes config as
  `app.ninetyminut.*` (`application.properties:30-33`), and wraps fetch failures in an
  unchecked `MatchDataUnavailableException`. This fetch/timeout/charset/exception
  skeleton transfers; its match-page selectors (`td.mecze2`, sibling-walk) do not.
  jsoup `1.22.2` is already a dependency (`backend/pom.xml:112-117`).
- **Public read precedent (S-01):** `web/NewsPostController.java:77-91` exposes a public
  `GET` that talks straight to a repository and returns `record` DTOs via static
  `from(...)`. Security **fails closed** — `security/SecurityConfig.java:79`
  (`anyRequest().denyAll()`) — so a public GET must be explicitly allow-listed at
  `SecurityConfig.java:71`-style. CORS covers `/api/**` already (`web/CorsConfig.java:25`).
  There is **no `@ControllerAdvice`**; errors are raised with `ResponseStatusException`.
- **Frontend precedent (S-01):** Angular 22, standalone components, signals, `inject()`,
  `@if`/`@for`, lazy `loadComponent` routes. `news/news-api.ts` wraps `HttpClient`
  against `environment.apiBaseUrl`; `news/news-list.ts` holds signal state and flips
  `busy`/`error` on subscribe. Routes in `app.routes.ts`; the header/nav shell is
  `app.html`. **No guest tab bar and no `<table>` styling exist yet** — this slice
  introduces both.
- **Live-source facts (verified 2026-07-12):** Both league pages return HTTP 200. The
  **current 2026/27 season** page is `http://www.90minut.pl/liga/1/liga14875.html`
  (Płomień was promoted to *Klasa okręgowa*); the 2025/26 page is `liga14612.html`
  (*Klasa A*). Standings rows are `<tr>` inside the standings table (header cells
  `Nazwa`, `M.`, `Pkt.`, group headers `RAZEM`/`DOM`/`WYJAZD`); each data row has a
  team link `a.main[href*="skarb.php?id_klub="]`. Cell order: `td[0]`=position
  (`"1."`, **can be blank pre-season**), `td[1]`=team (anchor text), `td[2]`=matches
  played, `td[3]`=points (bold). As of today the current-season table is a **pre-season
  all-zeros placeholder** (teams alphabetical, 0 points).

## Desired End State

A guest visiting the SPA sees a **"Tabela"** tab in the header. Clicking it shows the
current league standings — position, team, matches played, points — scraped live from
90minut.pl and cached ~15 min server-side. When the source is unreachable or unparseable,
the tab shows a clear error message instead of a broken table. Verify by: loading
`/tabela` in the browser against a locally-run backend and confirming rows match the
live 90minut page; and `curl /api/league-table` returning `200` JSON without any auth.

### Key Discoveries:

- Reuse boundary is the fetch, not the parse: copy the jsoup connect/timeout/charset/
  exception skeleton from `news/NinetyMinutClient.java:65-86`; write new selectors.
- Public GET must be allow-listed at `security/SecurityConfig.java` (~line 71) or it
  401s — the chain fails closed (`:79`).
- Compact columns need only `td[0..3]` per standings row; ignore the RAZEM/DOM/WYJAZD
  W-D-L/goals cells.
- Płomień's club id in the standings link is `3154` (same id the match scraper uses as
  `team-id`) — not needed now (no highlight), but confirms the source identity.

## What We're NOT Doing

- **No database / migration / entity** — standings are transient (scrape + in-memory
  cache), not persisted. No `V7__…sql`.
- **No scheduled refresh / cron**, no `@EnableCaching`/`@Scheduled` — a tiny manual TTL
  cache suffices.
- **No fixtures/terminarz** — that is S-06; this plan only leaves a reusable fetch seam.
- **No W-D-L, goals, home/away splits, or goal difference** — compact columns only.
- **No Płomień row highlight.**
- **No new automated tests** (no scraper unit test, no new controller slice test). The
  existing backend test suite must still pass.
- **No `laczynaspilka.pl`** rich data (gated behind reCAPTCHA — see S-03 research).

## Implementation Approach

Backend first (Phase 1), then the frontend tab that consumes it (Phase 2). The backend
introduces a second 90minut client scoped to the league page, structured so the
page-fetch is a reusable seam for S-06. A thin caching service owns the ~15 min TTL and
propagates scrape failures (no last-good fallback — the empty/error decision is
"show what's scraped, error on failure"). The controller maps a scrape failure to a
`502` so the SPA can render an error state exactly like the news views do.

## Critical Implementation Details

- **Standings table selection is the one non-obvious parse step.** The page has ~60
  tables; the standings table is the one whose header row contains the cells `Nazwa`
  and `Pkt.` under the `RAZEM` group. Within it, data rows are the `<tr>` that contain a
  team link `a.main[href*="skarb.php?id_klub="]`; read `td[0]`=position (strip trailing
  `.`, may be empty → null), team = the `a.main` text, `td[2]`=played, `td[3]`=points.
  Bad/short rows return null and are skipped (mirror `NinetyMinutClient.parseRow`).
- **Charset:** do not set encoding in code — jsoup auto-detects `ISO-8859-2` from the
  response, exactly as the match scraper relies on. Polish team names must round-trip
  (e.g. "Płomień Kostrze (Kraków)").
- **Cache semantics:** the TTL cache is a performance optimization only. On a miss the
  service scrapes; if the scrape throws, the exception propagates (cache is **not**
  updated and stale data is **not** served) → controller returns 502.

## Phase 1: Backend — league standings scrape + cached public endpoint

### Overview

A new `league` package with a 90minut league-page client (reusable fetch seam +
standings parser), a caching service, a public read controller, config keys, and the
security allow-list entry.

### Changes Required:

#### 1. League scraper client

**File**: `backend/src/main/java/com/plomienkostrze/league/NinetyMinutLeagueClient.java` (new)

**Intent**: Fetch the configured 90minut league page and parse the standings into a list
of rows. Parallel to the match-scraping `NinetyMinutClient`, but for the league page.
Structure the page fetch as its own method so S-06 (fixtures) can reuse it.

**Contract**: `@Component` with `List<StandingRow> fetchStandings()`. A private
`Document fetchPage()` encapsulates `Jsoup.connect(leagueUrl).timeout(TIMEOUT_MILLIS).get()`
with `IOException` → `LeagueDataUnavailableException` (the reusable seam). `leagueUrl`
injected via `@Value("${app.ninetyminut.league-url}")`. Parsing follows the selector
contract in Critical Implementation Details; unparseable rows are skipped. Charset is
left to jsoup auto-detection (no explicit encoding).

#### 2. Standings row model

**File**: `backend/src/main/java/com/plomienkostrze/league/StandingRow.java` (new)

**Intent**: The parsed shape of one standings line (compact columns).

**Contract**: `record StandingRow(Integer position, String team, int played, int points)`.
`position` is nullable (pre-season blank). Transient — not a JPA entity.

#### 3. Scrape-failure exception

**File**: `backend/src/main/java/com/plomienkostrze/league/LeagueDataUnavailableException.java` (new)

**Intent**: Signal that the league page could not be fetched or parsed. Mirrors
`news/MatchDataUnavailableException`.

**Contract**: unchecked `RuntimeException` with a message + optional cause.

#### 4. Caching service

**File**: `backend/src/main/java/com/plomienkostrze/league/LeagueService.java` (new)

**Intent**: Serve standings from a short in-memory TTL cache, scraping via the client on
a miss. Owns the "real logic" that justifies a service layer (vs. the news read path,
which has none).

**Contract**: `@Service` with `List<StandingRow> getStandings()`. Holds a `volatile`
cached snapshot (rows + `Instant fetchedAt`); returns it when `age < ttl`, otherwise
calls `client.fetchStandings()`, updates the cache, and returns. On scrape failure the
exception propagates and the cache is not updated. TTL from
`@Value("${app.ninetyminut.league-cache-ttl}")` as a `Duration`.

#### 5. Public controller

**File**: `backend/src/main/java/com/plomienkostrze/web/LeagueTableController.java` (new)

**Intent**: Expose the standings to guests as a public read endpoint, mapping scrape
failures to a gateway error the SPA can render.

**Contract**: `@RestController @RequestMapping("/api/league-table")`, `@GetMapping` →
`TableResponse(List<RowResponse> rows)` where `RowResponse(Integer position, String team,
int played, int points)` has a static `from(StandingRow)` (mirror news DTOs). Catches
`LeagueDataUnavailableException` → `ResponseStatusException(HttpStatus.BAD_GATEWAY, …)`
(pattern from `web/NewsGenerationController.java:44-54`).

#### 6. Security allow-list

**File**: `backend/src/main/java/com/plomienkostrze/security/SecurityConfig.java`

**Intent**: Permit anonymous GET to the new endpoint; without this the fail-closed chain
returns 401/403.

**Contract**: add `.requestMatchers(HttpMethod.GET, "/api/league-table").permitAll()`
alongside the existing public-GET matchers (near `:71`), before `anyRequest().denyAll()`.

#### 7. Configuration

**File**: `backend/src/main/resources/application.properties`

**Intent**: Externalize the league page URL (season/division rollover = env change) and
the cache TTL.

**Contract**: add
`app.ninetyminut.league-url=${NINETYMINUT_LEAGUE_URL:http://www.90minut.pl/liga/1/liga14875.html}`
and `app.ninetyminut.league-cache-ttl=${NINETYMINUT_LEAGUE_CACHE_TTL:PT15M}`, grouped
with the existing `app.ninetyminut.*` keys.

### Success Criteria:

#### Automated Verification:

- Backend compiles: `mvn -q -f backend/pom.xml compile`
- Existing test suite still passes: `mvn -q -f backend/pom.xml test`

#### Manual Verification:

- With the backend running locally, `curl -s localhost:8080/api/league-table` returns
  `200` and a JSON `rows` array whose entries match the live 90minut standings (Polish
  characters intact).
- The endpoint is reachable with **no** auth token (public).
- A second request within the TTL does not re-scrape (observed via timing/logs).
- Forcing a failure (temporarily point `NINETYMINUT_LEAGUE_URL` at a bad URL) yields
  `502`, not `500` or a stack trace.

**Implementation Note**: After Phase 1 automated verification passes, I will stand up a
local backend myself (per `context/foundation/lessons.md` — no user login needed for a
public read) and run the manual checks, then pause for confirmation before Phase 2.

---

## Phase 2: Frontend — public "Tabela" tab

### Overview

A new `league/` Angular feature (API service + model + standalone component), a `/tabela`
lazy route, the first guest nav tab in the header, and the first `<table>` styling.

### Changes Required:

#### 1. API service + model

**File**: `frontend/src/app/league/league-api.ts` (new)

**Intent**: Single point of HTTP contact for the standings, mirroring `news/news-api.ts`.

**Contract**: `@Injectable({providedIn:'root'})`, `inject(HttpClient)`,
`getTable(): Observable<LeagueTable>` → `GET ${environment.apiBaseUrl}/api/league-table`.
Co-located interfaces `StandingRow { position: number | null; team: string; played:
number; points: number }` and `LeagueTable { rows: StandingRow[] }`.

#### 2. Standings component

**File**: `frontend/src/app/league/league-table.ts` (new)

**Intent**: Render the standings with loading/error/empty states, following the
`news-list.ts` signal pattern.

**Contract**: standalone `LeagueTable` component, `inject(LeagueApi)`, signals `rows`,
`busy`, `error`; loads in the constructor and flips signals on `subscribe` next/error
(Polish error string incl. HTTP status, as in `news-list.ts:39-53`).

#### 3. Component template

**File**: `frontend/src/app/league/league-table.html` (new)

**Intent**: A `<table>` of the standings with `@if` guards for busy/error/empty.

**Contract**: `@if (busy())` / `@if (error())` / `@else` blocks; a `<table>` with header
`Poz · Drużyna · M · Pkt` and `@for (row of rows(); track row.team)` body rows. Show a
"brak danych" empty state when `rows()` is empty and not busy/errored.

#### 4. Component styles

**File**: `frontend/src/app/league/league-table.scss` (new)

**Intent**: First `<table>` styling in the repo; keep it readable on mobile.

**Contract**: table within the established ~40rem centered column; reuse existing tokens
(`#666` muted text, `1px solid #eee` row dividers); ensure the compact 4-column table
does not overflow small screens (constrain widths / allow horizontal scroll).

#### 5. Route

**File**: `frontend/src/app/app.routes.ts`

**Intent**: Register the lazy `/tabela` route.

**Contract**: add `{ path: 'tabela', loadComponent: () => import('./league/league-table')
.then(m => m.LeagueTable) }` **before** the `**` wildcard.

#### 6. Navigation tab

**File**: `frontend/src/app/app.html` (+ `frontend/src/app/app.scss`)

**Intent**: Add the first guest content tab to the header. `RouterLink` is already
imported in `app.ts` — no shell TS change.

**Contract**: add `<a routerLink="/tabela" routerLinkActive="active">Tabela</a>` in the
header (a small `nav` next to the `app-title`, mirroring how `app-auth` is structured);
add minimal styling in `app.scss` for the nav/active state.

### Success Criteria:

#### Automated Verification:

- Frontend production build succeeds: `cd frontend && npm run build`

#### Manual Verification:

- A "Tabela" tab is visible in the header for a guest (not logged in) and routes to
  `/tabela`.
- The table renders `Poz · Drużyna · M · Pkt` with data matching the backend response;
  Polish names render correctly.
- Loading state shows briefly; stopping the backend produces the error message (not a
  blank/broken table).
- The compact table is readable at mobile width.
- No regressions: news list/detail and existing header controls still work.

**Implementation Note**: After Phase 2 automated verification, I will stand up the full
local stack myself (backend + `npm start`) and run the manual checks — no user login is
required for this public tab.

---

## Testing Strategy

Per the agreed scope, **no new automated tests** are added in this slice.

### Manual Testing Steps:

1. Run the backend locally; `curl localhost:8080/api/league-table` → `200` JSON; confirm
   a few rows match the live 90minut page (position, team, M, Pkt).
2. Confirm anonymous access (no `Authorization` header) works.
3. Hit the endpoint twice quickly; confirm the second is served from cache.
4. Point `NINETYMINUT_LEAGUE_URL` at a bad URL; confirm `502` and a clean error.
5. Run `npm start`; open `/tabela` via the new header tab; confirm rendered rows match
   the backend and Polish characters are intact.
6. Stop the backend; reload `/tabela`; confirm the error state renders.
7. Confirm news views and existing header controls are unaffected.

## Performance Considerations

The ~15 min in-memory TTL cache keeps guest traffic off 90minut and makes cached reads
effectively instant. A cache miss pays one jsoup fetch (10 s timeout). Concurrent misses
may double-scrape briefly (acceptable at this scale). No pagination — a league table is
~16–18 rows.

## Migration Notes

None — nothing is persisted.

## References

- Related research: `context/changes/league-table/research.md`
- 90minut structure + league URLs: `context/archive/2026-07-08-gated-news-generation/research-scraping-90minut.md`
- Scraper skeleton to mirror: `backend/src/main/java/com/plomienkostrze/news/NinetyMinutClient.java:65-86`
- Public read + security patterns: `backend/src/main/java/com/plomienkostrze/web/NewsPostController.java:77-91`, `backend/src/main/java/com/plomienkostrze/security/SecurityConfig.java:71,79`
- Scrape-failure → HTTP mapping: `backend/src/main/java/com/plomienkostrze/web/NewsGenerationController.java:44-54`
- Frontend patterns to mirror: `frontend/src/app/news/news-api.ts`, `frontend/src/app/news/news-list.ts`, `frontend/src/app/app.routes.ts`, `frontend/src/app/app.html`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Backend — league standings scrape + cached public endpoint

#### Automated

- [x] 1.1 Backend compiles (`mvn -q -f backend/pom.xml compile`)
- [x] 1.2 Existing test suite still passes (`mvn -q -f backend/pom.xml test`)

#### Manual

- [x] 1.3 `curl /api/league-table` returns 200 JSON matching live standings (Polish intact)
- [x] 1.4 Endpoint reachable with no auth token (public)
- [x] 1.5 Second request within TTL served from cache (no re-scrape)
- [x] 1.6 Bad `NINETYMINUT_LEAGUE_URL` yields 502, not 500/stack trace

### Phase 2: Frontend — public "Tabela" tab

#### Automated

- [ ] 2.1 Frontend production build succeeds (`cd frontend && npm run build`)

#### Manual

- [ ] 2.2 "Tabela" tab visible for a guest and routes to `/tabela`
- [ ] 2.3 Table renders Poz · Drużyna · M · Pkt matching backend; Polish names correct
- [ ] 2.4 Loading state shows; backend down produces the error message
- [ ] 2.5 Compact table readable at mobile width
- [ ] 2.6 No regressions in news views or existing header controls
