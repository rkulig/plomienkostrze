---
date: 2026-07-12T12:49:43+02:00
researcher: Radek Kulig
git_commit: ca84305d491fe352ade73754928ad6e3c5314fcb
branch: master
repository: rkulig/plomienkostrze
topic: "S-05 league-table — grounding the plan in the existing 90minut.pl scraper + public read patterns"
tags: [research, codebase, league-table, scraping, 90minut, public-read-api, angular]
status: complete
last_updated: 2026-07-12
last_updated_by: Radek Kulig
---

# Research: S-05 `league-table` — reuse of the scraper + public-read patterns

**Date**: 2026-07-12T12:49:43+02:00
**Researcher**: Radek Kulig
**Git Commit**: ca84305
**Branch**: master
**Repository**: rkulig/plomienkostrze

## Research Question

Slice **S-05 `league-table`** adds a public "Tabela" tab showing the current league
standings, scraped from 90minut.pl "tak jak scrapujemy wynik". Before planning, map
exactly how the three patterns it reuses are actually built in the live code:
1. the 90minut.pl scraper from S-03 (`gated-news-generation`),
2. the public (unauthenticated) read-API from S-01 (`public-news-reading`),
3. the public Angular view + navigation from S-01.

So the plan can copy conventions instead of inventing them, and so the genuinely
*new* work (a standings page has a different URL, different selectors, different
shape) is isolated.

## Summary

- **The scraper skeleton transfers cleanly; the parsing does not.** S-03's
  `NinetyMinutClient` is a single jsoup `@Component` — `Jsoup.connect(...).timeout(10s).get()`,
  charset auto-detected (ISO-8859-2 via headers), config externalized as
  `app.ninetyminut.*`, failures wrapped in `MatchDataUnavailableException`. All of
  that is reusable. But it targets the **team match-list page**
  (`mecze_druzyna.php?id=3154&id_sezon=107`) and pivots parsing off the score cell
  `td.mecze2` — none of which exists on a standings page. The league table lives at a
  **different 90minut URL** (`/liga/1/liga14612.html`, per the S-03 scraping research)
  with different columns, so URL + selectors + row model + DTO are all new code.
- **The public read-API pattern is simple and prescriptive.** Controller →
  repository directly (no service layer, no MapStruct), entity + `record` DTOs with
  static `from(...)`, a Flyway `V7__…sql` migration, and — critically — a
  `permitAll()` line in `SecurityConfig` because the chain **fails closed**
  (`anyRequest().denyAll()`). Forget the allow-list line and the endpoint 401s.
- **The frontend is Angular 22, modern idioms**: standalone components, signals,
  `inject()`, `@if`/`@for`, lazy `loadComponent` routes, plain per-component SCSS
  (no Tailwind/Material). A new tab = add a `tabela/` feature (service + model +
  component) and touch two shared files (`app.routes.ts`, `app.html`).
- **Two decisions the plan must make** (neither is settled by precedent): (a)
  **transient vs. persisted** standings — S-03's scrape is transient, S-01's read
  serves from Postgres; the table is scraped *and* public, so it's a genuine fork;
  and (b) **no scraper test exists** in the repo — S-03's parsing is entirely
  untested, so a standings parser adds parsing logic with no precedent to copy.

## Detailed Findings

### Area 1 — The S-03 scraper (what to reuse, what to replace)

All under `backend/src/main/java/com/plomienkostrze/`.

**Reusable skeleton** — `news/NinetyMinutClient.java`:
- Fetch: `Jsoup.connect(baseUrl + "/mecze_druzyna.php?id=" + teamId + "&id_sezon=" + seasonId).timeout(TIMEOUT_MILLIS).get()` (`NinetyMinutClient.java:68-70`); `TIMEOUT_MILLIS = 10_000` (`:30`); jsoup default user-agent (none set).
- Encoding: **not set in code** — jsoup auto-detects ISO-8859-2 from the HTTP charset header (class Javadoc `:20`). Transfers unchanged to any other 90minut page.
- Errors: `IOException` → `MatchDataUnavailableException` (`:71-73`); an unchecked `RuntimeException` (`news/MatchDataUnavailableException.java:7`).
- Config: `@Value` constructor injection (`:39-45`) backed by `application.properties:30-33`:
  `app.ninetyminut.base-url=${NINETYMINUT_BASE_URL:http://www.90minut.pl}`,
  `app.ninetyminut.team-id=${…:3154}`, `app.ninetyminut.season-id=${…:107}`
  (`107`=2025/26, `109`=2026/27). Cleartext HTTP is an accepted risk (site has no HTTPS).
- jsoup dependency: `backend/pom.xml:112-117` — `org.jsoup:jsoup:1.22.2`.

**Match-specific, must be rewritten for standings:**
- URL path/params hardcoded to `mecze_druzyna.php?id=…&id_sezon=…` (`:68`). The **standings page is a different URL** (`/liga/1/liga14612.html` — see Area 4), keyed by **league/group, not team-id** — so the team-id config split doesn't map.
- Anchor selector `td.mecze2` (`:75`) is the **score cell** of a match row; it does not exist in a standings table.
- Sibling-walk row model `date | competition | hosts | score | guests` (`:89-96`) is the match layout; standings have `position | team | played | W/D/L | goals | points` and want `<tr>` iteration.
- The `b > u` "our team is bold-underlined" home/away heuristic (`:123`), the `SCORE`/`PENALTIES` regexes (`:31-32`), the datetime parse (`:108-118`), the `MatchResult` record + `Outcome` enum (`:56-58`), and the "last played match" selection loop (`:74-85`) are **all** match semantics — none apply.

**No test exists** for `NinetyMinutClient` — every backend test mocks
`NewsGenerationService` (`AuthorizationMatrixTest.java:77`, `PublishGateTest.java:102-105`).
The fetch/parse logic is untested by precedent.

### Area 2 — The public read-API pattern (what a new public GET must do)

Embodied by **news-posts**; the controller talks straight to the repository (no
service, no MapStruct).

- **Controller**: `web/NewsPostController.java` — `@RequestMapping("/api/news-posts")` (`:37`); public list `GET /api/news-posts` (`:77-91`) with manual `page`/`size` validation (`:80-86`, `size` max 50), fixed `publishedAt DESC` sort (`:88`), custom `ListResponse(List<…> items, long total)` (`:57`) — **not** Spring's `Page`/`Pageable` resolver. Detail `GET /api/news-posts/{id}` (`:104-110`) 404s via `ResponseStatusException(NOT_FOUND)`.
- **DTOs**: nested `public record`s with static `from(entity)` factories (`:49-64`).
- **Entity**: `news/NewsPost.java` — `@Entity @Table("news_posts")` (`:19-20`), no public setters, factory + `@PrePersist` (`:55-81`).
- **Repository**: `news/NewsPostRepository.java` — `extends JpaRepository<NewsPost, Long>` + derived `findByStatus(...)` (`:7-9`).
- **Flyway**: `backend/src/main/resources/db/migration/` — convention `V<n>__snake_case.sql`, PostgreSQL-specific, additive. Latest is `V6`; **next is `V7__…sql`**. `V2__create_news_posts.sql` shows table + index style; `V3__seed_news_posts.sql` ships seed rows as a migration.
- **Security (critical)**: `security/SecurityConfig.java:66-79` — public reads opened with `.requestMatchers(HttpMethod.GET, "/api/news-posts/**").permitAll()` (`:71`); chain **fails closed** with `.anyRequest().denyAll()` (`:79`). A new public GET **must** add its own `permitAll()` matcher or it returns 401/403.
- **CORS**: `web/CorsConfig.java` maps `/api/**` (`:25`) — a new `/api/...` endpoint is covered automatically, no change.
- **Error handling**: no `@ControllerAdvice` anywhere; raise `ResponseStatusException` (default Spring error JSON). `ddl-auto=validate` (`application.properties:14`) means entity column types/lengths must exactly match the migration.
- **Tests**: `web/AuthorizationMatrixTest.java` — the authz matrix; a new public GET must be added here as PERMIT_ALL (the `GET /api/unknown` DENY_ALL row `:132-133` pins the fail-closed default). `@WebMvcTest` slice tests, H2, `spring.flyway.enabled=false` in `src/test/resources/application.properties` (schema from entities, **not** Flyway).

### Area 3 — The public frontend view + navigation

Angular **22** (`frontend/package.json:15-20`), standalone components, signals,
`inject()`, `@if`/`@for`, lazy routes, per-component SCSS (no UI lib).

- **View to copy**: `frontend/src/app/news/news-list.ts` (standalone, signals `items`/`busy`/`error`, `inject(NewsApi)`, load in constructor) + template `news-list.html` (`@if` states + `@for (… ; track …)`). A read-only table needs **only a list-style component**, no detail counterpart.
- **Service to copy**: `frontend/src/app/news/news-api.ts` — `@Injectable({providedIn:'root'})`, `inject(HttpClient)`, returns `Observable<T>`; base URL `environment.apiBaseUrl` (`news-api.ts:38`) defined in `frontend/src/environments/environment.ts:4` (dev `http://localhost:8080`) and `environment.production.ts:4` (Cloud Run). Response interfaces co-located in the service file. **No env change needed** — origin already configured. Components subscribe and flip signals for loading/error (`news-list.ts:39-53`).
- **Routing**: `frontend/src/app/app.routes.ts` — flat, all `loadComponent`. Add before the `**` wildcard (`:20`):
  `{ path: 'tabela', loadComponent: () => import('./tabela/tabela').then(m => m.Tabela) }`.
- **Navigation (shared touch-point)**: `frontend/src/app/app.html` — there is **no guest tab bar yet**; only the title link (`:2`) and an auth-only `nav.app-auth` (`:4-13`). "Tabela" would be the **first public content tab**: add `<a routerLink="/tabela">Tabela</a>` in the header (mirror `app-auth`), extend `frontend/src/app/app.scss:1-29` (`.app-header` flex `space-between`). `RouterLink` already imported in `app.ts:9` — no TS change to the shell.
- **Styling**: no `<table>` precedent exists anywhere — all lists are `<ul>/<li>`. A `tabela.scss` needs `<table>` styling from scratch, reusing tokens: 40rem centered column (`news-list.scss:2-3`), grey `#666`, error `#b00020`, `1px solid #eee` dividers.
- **Tests**: **no `*.spec.ts` exist** in the frontend at all — no precedent to copy; root `CLAUDE.md` points at `/10x-e2e` for browser tests.

### Area 4 — Where the standings actually live on 90minut.pl

From S-03's scraping research (`context/archive/2026-07-08-gated-news-generation/research-scraping-90minut.md:33-40`):

- **League page**: `http://www.90minut.pl/liga/1/liga14612.html` (Klasa A 2025/26 gr.
  Kraków III; 2026/27 = `liga14875.html`). It contains **both the standings table and
  the fixtures/terminarz** with results — so S-05 (`league-table`) and S-06
  (`fixtures-schedule`) scrape **the same page**, which is exactly why the roadmap
  marks them a sequential pair sharing the scraper.
- Same tech as the match page: Apache/PHP, server-rendered HTML, `ISO-8859-2`, no
  `robots.txt`, no auth → jsoup is sufficient (`:24-28`).
- Each result cell on that page links **out** to `laczynaspilka.pl` (gated) — but the
  **table and terminarz text itself is in the 90minut HTML**, which is all S-05/S-06 need.

> Not yet verified in this research: the exact HTML structure / CSS selectors of the
> standings table on `liga14612.html` (the S-03 doc confirms the table *exists* on the
> page but didn't capture its markup). The plan should fetch the page once and inspect
> it to pin the selectors — see Open Questions.

## Code References

- `backend/src/main/java/com/plomienkostrze/news/NinetyMinutClient.java:68-70` — jsoup fetch (URL/timeout) to reuse
- `backend/src/main/java/com/plomienkostrze/news/NinetyMinutClient.java:75,89-96` — match-specific `td.mecze2` selector + sibling-walk (do NOT reuse for standings)
- `backend/src/main/java/com/plomienkostrze/news/MatchDataUnavailableException.java:7` — unchecked exception pattern for scrape failures
- `backend/src/main/resources/application.properties:30-33` — `app.ninetyminut.*` externalized config to mirror
- `backend/pom.xml:112-117` — jsoup 1.22.2 dependency (already present)
- `backend/src/main/java/com/plomienkostrze/web/NewsPostController.java:77-91` — public list endpoint shape to copy
- `backend/src/main/java/com/plomienkostrze/security/SecurityConfig.java:71,79` — `permitAll()` for public GET + fail-closed `denyAll()`
- `backend/src/main/resources/db/migration/V2__create_news_posts.sql` — table+index migration style; next file is `V7__…sql`
- `backend/src/test/java/com/plomienkostrze/web/AuthorizationMatrixTest.java:132-133` — where a new public GET must be added as PERMIT_ALL
- `frontend/src/app/news/news-api.ts:35-42` — HttpClient service pattern
- `frontend/src/app/news/news-list.ts` — standalone/signals list component to copy
- `frontend/src/app/app.routes.ts:20` — insert `tabela` route before `**`
- `frontend/src/app/app.html:2-13` — header/nav shared touch-point
- `context/archive/2026-07-08-gated-news-generation/research-scraping-90minut.md:33-40` — league page URL + season IDs

## Architecture Insights

- **Reuse boundary is the fetch, not the parse.** The jsoup connect/timeout/charset/
  exception-wrapping skeleton and the `app.ninetyminut.*` config convention are the
  transferable assets. Everything downstream of "I have the page HTML" (selectors, row
  model, DTO) is standings-specific new code. A `NinetyMinutLeagueClient` (or a second
  method on a shared client) that returns `List<StandingRow>` is the natural shape.
- **Config keying changes.** The table is keyed by **league page id**
  (`liga14612`/`liga14875`), not `team-id`+`season-id`. Introduce a new config key
  (e.g. `app.ninetyminut.league-url` or `league-id`) rather than overloading the
  match-page keys. Season rollover = swapping that id, same as the match page.
- **Backend convention says "no service unless there's real logic."** News reads go
  controller→repository directly. If standings are persisted, a thin service that owns
  the scrape+refresh is justified; if transient, the controller can call the client
  directly. This ties into the transient-vs-persisted decision below.
- **Fail-closed security is a footgun and a safety net.** Easy to forget the
  `permitAll()` line; `AuthorizationMatrixTest` will catch it if the new endpoint is
  added to the matrix. Add both together.
- **`ddl-auto=validate` + Flyway-off-in-tests** means the entity is the single source
  of truth in tests but must exactly match the migration in prod. Only relevant if the
  table is persisted.

## Historical Context (from prior changes)

- `context/archive/2026-07-08-gated-news-generation/research-scraping-90minut.md` —
  the definitive source on 90minut's structure, encoding, the league page URL, and why
  richer data (`laczynaspilka.pl`) is gated behind reCAPTCHA and out of scope. The
  standings/terminarz are the *reachable* part of 90minut, so S-05/S-06 sit squarely in
  the "realistic" zone that research identified.
- `context/foundation/lessons.md:5-17` — two standing lessons: for manual backend/
  frontend verification, **stand up the stack yourself** (Docker Postgres `plomien`,
  backend sourcing `.env.local`, `npm start`), leave the user only clicks needing their
  Firebase login. A public read-only table needs **no admin login**, so verification of
  S-05 should be fully self-serviceable (scrape a live page, hit the public GET, load
  the tab) — no user-in-the-loop token needed.
- S-04 archive (`context/archive/2026-07-10-news-post-management/`) — most recent
  proof the news CRUD + security matrix pattern is stable and current.

## Related Research

- `context/archive/2026-07-08-gated-news-generation/research.md` and
  `research-libraries.md` — S-03's full research (scraper + Spring AI); the scraper
  half is the relevant precedent for S-05.

## Open Questions

1. **Transient vs. persisted standings?** — the core design fork.
   - *Transient*: `GET /api/league-table` triggers a live scrape and returns rows; no
     entity, no migration, no `V7`. Simplest; but response latency + availability are
     coupled to 90minut, and every guest hit re-scrapes (add a short in-memory cache).
   - *Persisted + refresh*: scrape into a `league_standings` table (needs `V7`
     migration + entity), serve from Postgres, refresh on a schedule (`@Scheduled`) or
     on-demand. More robust and fast, but adds a refresh trigger and staleness policy.
   - Recommendation to settle in `/10x-plan`: given one small public page and MVP goals,
     **transient with a short TTL cache** is likely the lighter, sufficient choice —
     but confirm expected traffic and how fresh the table must be.
2. **Exact standings selectors on `liga14612.html`** — not captured yet. Plan should
   fetch the page once (jsoup or curl with ISO-8859-2) and pin the table selector +
   column order before writing the parser.
3. **League id config & season rollover** — introduce `app.ninetyminut.league-*`;
   decide whether S-06 (`fixtures-schedule`) shares one "league page" fetch (same URL)
   with S-05, since both parse the same document (argues for one client that fetches
   once and exposes two parse methods).
4. **First scraper test?** — no scraper is currently tested. S-05 is a chance to add
   the first parser unit test (saved-HTML fixture → assert rows), which S-06 would then
   reuse. Worth a plan line given parsing is the riskiest, most drift-prone code.
5. **Empty/pre-season state** — what the tab shows when the table isn't published yet
   or the scrape returns nothing (mirror `MatchDataUnavailableException` → a clear
   empty/error state in the UI).
