---
date: 2026-07-11T14:50:27+02:00
researcher: Radek Kulig
git_commit: 5ca7b86203ebb7fc79c3d9324e389864b6fb5520
branch: master
repository: plomienkostrze
topic: "Grounding Phase 1 test rollout — backend access gate (#1), publish gate (#2), CORS/security (#4)"
tags: [research, codebase, backend, security, authorization, cors, mockmvc, testing]
status: complete
last_updated: 2026-07-11
last_updated_by: Radek Kulig
---

# Research: Grounding Phase 1 test rollout — access gate (#1), publish gate (#2), CORS/security (#4)

**Date**: 2026-07-11T14:50:27+02:00
**Researcher**: Radek Kulig
**Git Commit**: 5ca7b86203ebb7fc79c3d9324e389864b6fb5520 (pushed to origin/master — permalinks derivable)
**Branch**: master
**Repository**: plomienkostrze

## Research Question

Ground rollout Phase 1 of `context/foundation/test-plan.md` ("Backend harness + bramka
dostępu i publikacji") for risks #1 (authorization: gate checks "logged in" not "your
role"), #2 (publish gate: draft reaches public without admin approval), #4 (CORS/security
regression). For each risk: anchor the real failure path in code, verify or correct the
response guidance, locate existing tests, and identify the cheapest useful test layer.

## Summary

**All three risks are groundable, and the cheapest useful layer for every one is a
`@WebMvcTest` MockMvc slice** — full `@SpringBootTest`/Testcontainers is not required for
Phase 1. Three load-bearing facts shape the plan:

1. **Authorization is centralized and currently correct.** Every rule lives in one
   `SecurityConfig.authorizeHttpRequests` block (`SecurityConfig.java:66-79`); there is no
   method security anywhere. Every admin-intent endpoint uses `hasRole("ADMIN")`, not
   merely `.authenticated()`. **Risk #1 is a regression-guard, not a live bug** — the
   role-matrix test locks the boundary so a future edit can't silently downgrade an ADMIN
   matcher to `authenticated`.

2. **Risk #2 must be reframed — there is NO `DRAFT` status and NO persisted draft state.**
   `NewsPostStatus` has a single value, `PUBLISHED`. The generation endpoint
   (`POST /api/news-posts/generate`) **persists nothing** — it returns an in-memory
   `ProposalDraft` (HTTP 200) and never calls `repository.save`. The guardrail "system
   never publishes by itself" holds *architecturally*: there is no code path that stores
   unapproved content. The testable gate is therefore: (a) public read returns
   PUBLISHED-only, and (b) generation writes nothing. **This is a §2 backport** (see
   "Backport candidates").

3. **`spring-security-test` is MISSING from `backend/pom.xml`** — the one hard prerequisite.
   Without it, `jwt()` / `@WithMockUser` post-processors don't resolve, and the role matrix
   can't be written. Adding it (test scope) is the first implementation step.

Stack: **Spring Boot 4.1.0, Spring Security 7.1.0, Java 21, JUnit 6.0.3**.

## Detailed Findings

### Risk #1 — Authorization gate (admin vs authenticated vs anonymous)

**Where it lives:** one block, `SecurityConfig.java:66-79`. No `@PreAuthorize`, no
`@EnableMethodSecurity`, no security annotations on any controller (grep for
`hasRole|hasAuthority|@PreAuthorize|GrantedAuthority` hits only `SecurityConfig.java`).

Full HTTP authorization matrix (endpoint → rule → enforcing line):

| Method + Path | Rule | Enforced at | Controller |
|---|---|---|---|
| `GET /api/ping` | permitAll | `SecurityConfig.java:72` | `PingController.java:15` |
| `GET /api/news-posts` | permitAll | `SecurityConfig.java:71` | `NewsPostController.java:77` |
| `GET /api/news-posts/{id}` | permitAll | `SecurityConfig.java:71` | `NewsPostController.java:104` |
| `GET /actuator/health` | permitAll | `SecurityConfig.java:73` | — |
| `GET /api/me` | **authenticated** | `SecurityConfig.java:74` | `MeController.java:19` |
| `POST /api/news-posts` | **ADMIN** | `SecurityConfig.java:75` | `NewsPostController.java:97` |
| `POST /api/news-posts/generate` | **ADMIN** | `SecurityConfig.java:76` | `NewsGenerationController.java:39` |
| `PUT /api/news-posts/{id}` | **ADMIN** | `SecurityConfig.java:77` | `NewsPostController.java:117` |
| `DELETE /api/news-posts/{id}` | **ADMIN** | `SecurityConfig.java:78` | `NewsPostController.java:129` |
| anything else | **denyAll** | `SecurityConfig.java:79` | — |

**How "admin" is represented.** Authority string is exactly `"ROLE_ADMIN"`, but it is
*derived from a UID allowlist at token-conversion time*, not read from a JWT claim/scope:

- `application.properties:16` → `app.admin.uids=${ADMIN_UIDS:}` (empty default). Test copy
  `src/test/resources/application.properties:14` → `app.admin.uids=` (**empty → nobody is admin**).
- Read in `SecurityConfig.java:49-55` (constructor `@Value`, comma-split into `Set<String> adminUids`).
- Granted in the authorities converter, `SecurityConfig.java:96-102`:
  ```java
  converter.setJwtGrantedAuthoritiesConverter(jwt -> adminUids.contains(jwt.getSubject())
          ? List.<GrantedAuthority>of(new SimpleGrantedAuthority("ROLE_ADMIN"))
          : List.of());
  ```
  The JWT `sub` (Firebase UID) is looked up; present → `ROLE_ADMIN`, else no authorities.
  `hasRole("ADMIN")` auto-prepends `ROLE_`, matching exactly. `MeController.java:21-22`
  independently re-checks the same string to build its `{admin:boolean}` response.

This matches the `lessons.md` prior: `ADMIN_UIDS` wires the admin role; the
authenticated-non-admin 403 case is reproduced locally by restarting with `ADMIN_UIDS=""`.

**Audit verdict: the code distinguishes correctly — no leak.** The only `.authenticated()`
endpoint is `GET /api/me` (intent genuinely "any signed-in user" —
`MeController.java:7-12`). Every mutating endpoint is `hasRole("ADMIN")`, including
`/generate` (`SecurityConfig.java:76`). So risk #1's failure mode ("gate checks logged-in,
not role") is **not present today**; the test's job is to *pin* it against regression.

### Risk #2 — Publish gate (REFRAMED: no DRAFT status exists)

**`NewsPostStatus` has one value** — `NewsPostStatus.java:7-9`:
```java
public enum NewsPostStatus {
	PUBLISHED
}
```
The class comment anticipated `DRAFT`/`PROPOSAL` for later slices; they were never added.

**Public read is PUBLISHED-only, enforced at the controller (two separate places):**
- List — `NewsPostController.java:77-91`: passes the status into the repo query
  `repository.findByStatus(NewsPostStatus.PUBLISHED, PageRequest...)` (`:87`).
- Detail — `NewsPostController.java:104-110`: post-fetch `.filter(post -> post.getStatus()
  == PUBLISHED)` → 404 otherwise (`:107`).
- Repository — `NewsPostRepository.java:9`: derived query
  `Page<NewsPost> findByStatus(NewsPostStatus status, Pageable pageable)`. **The repo itself
  is not status-filtered** — `findById` (used by detail/update/delete) returns any status;
  the PUBLISHED gate is controller-side.
- Security: `GET /api/news-posts/**` is `permitAll` (`SecurityConfig.java:71`).

**Generation persists nothing / never auto-publishes:**
- `NewsGenerationService.generateFromLastMatch()` returns an in-memory record
  `ProposalDraft(String title, String content)` (`NewsGenerationService.java:35,39-45`).
  There is **no `repository.save`** in the service or the generation controller.
- `NewsGenerationController` `POST /api/news-posts/generate` returns a `ProposalResponse`
  body with default **HTTP 200** (`NewsGenerationController.java:39-56`) — not 201.
- Comments confirm intent: "The proposal is never persisted — accepting it is a plain
  POST /api/news-posts by the admin" (`NewsGenerationService.java:15-16`); "Persists
  nothing: the returned draft lives in the admin's browser until published via the existing
  POST /api/news-posts" (`NewsGenerationController.java:18-19`).

**The only publish path** is `POST /api/news-posts` (`NewsPostController.java:97-102`), which
saves `NewsPost.published(...)` (`NewsPost.java:55-62`, sets `status=PUBLISHED` +
`publishedAt=now`) and returns **201**. It is ADMIN-gated (`SecurityConfig.java:75`). So a
post is born PUBLISHED in one authenticated admin step; there is no DRAFT→PUBLISHED transition.

**Oracle sourcing (avoid the oracle-problem trap).** The requirement being grounded is
*"content is publicly visible only in PUBLISHED state, and generation never makes anything
public."* That oracle comes from the controller filter + the "generate saves nothing" fact —
**not** from copying `NewsPost.published()`. The V5 constraint
(`V5__news_posts_published_check.sql:5-7`,
`CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL)`) only guarantees a PUBLISHED row
has a timestamp; it does **not** enforce the draft-never-public gate, so it is not the #2
oracle. **Concrete trap:** `POST /generate` returns 200 with a full `{title, content}` body
but writes nothing — asserting "generate returned 200" proves nothing about visibility.
Conversely `POST /api/news-posts` returns 201 and *does* publish. Do not conflate the two
success codes.

### Risk #4 — CORS / security filter interaction

- CORS registered via `WebMvcConfigurer` (`CorsConfig.java:15`), mapping `/api/**`
  (`CorsConfig.java:25-28`): `allowedOrigins` from `@Value("${app.cors.allowed-origins}")`
  (dev/test `http://localhost:4200`), `allowedMethods("GET","POST","PUT","PATCH","DELETE","OPTIONS")`,
  `allowedHeaders("*")`. **`allowCredentials` is never set** → defaults to `false`.
- Wired into the security chain via `.cors(withDefaults())` (`SecurityConfig.java:64`); CSRF
  disabled (`:63`), sessions STATELESS (`:65`). The `CorsFilter` runs early, so a preflight
  `OPTIONS` is answered before authorization (no `permitAll` needed for OPTIONS).
- **Orthogonality (the risk-#4 property):** CORS controls which browser origin may read a
  response; it never lets a request skip the token. A legal origin passing preflight still
  hits the authorization matrix — writes still need `ROLE_ADMIN` (`:75-78`), unlisted paths
  still `denyAll` (`:79`). Anti-pattern to avoid: asserting a CORS header alone; the test
  must *also* assert the protected path still rejects a tokenless/non-admin call.

### Test harness — feasibility & the missing dependency

- **Versions:** Spring Boot 4.1.0 (`pom.xml:8`), Java 21 (`pom.xml:30`, `.sdkmanrc`),
  Spring Security 7.1.0, JUnit 6.0.3 (from dependency tree).
- **Present:** `spring-boot-starter-webmvc-test` (`pom.xml:125-129`, Boot 4 modular rename of
  `starter-test`) → JUnit 5/6, AssertJ, Mockito, Spring Test, MockMvc transitively. H2
  test-scoped (`pom.xml:99-103`).
- **MISSING — must add (test scope):** `org.springframework.security:spring-security-test`.
  Without it `SecurityMockMvcRequestPostProcessors.jwt()` / `@WithMockUser` do not resolve.
  Also **missing:** Testcontainers (belongs to Phase 2, not needed here).
- **Existing test:** `PlomienKostrzeApiApplicationTests` is a bare `@SpringBootTest`
  `contextLoads()`. Test props (`src/test/resources/application.properties`) shadow main:
  H2 in-memory (`:7-8`), **Flyway disabled** (`:10`), `ddl-auto=create-drop` (`:11`),
  `app.admin.uids=` empty (`:14`). The `NimbusJwtDecoder` is built from a JWKS URI and
  fetches keys **lazily on first token** (`SecurityConfig.java:37-39,85-94`), so the context
  loads offline with no Firebase creds.

**Feasibility verdict — `@WebMvcTest` slice is sufficient for all Phase 1 tests:**
- Import `SecurityConfig` into the slice, `@MockBean` the service/repository beans (keeps
  JPA/DataSource out → no DB needed).
- Satisfy `SecurityConfig`'s constructor `@Value`s via `@TestPropertySource`:
  `app.admin.uids`, `app.firebase.project-id`, `app.firebase.jwk-set-uri`.
- The `jwt()` post-processor injects the `Authentication` directly, **bypassing the
  `JwtDecoder`** → no live Firebase/JWKS fetch.
- Because `app.admin.uids` is empty in test props, do **not** rely on the production
  converter to mint admin — inject the authority directly.

## MockMvc simulation recipe (per principal)

| Principal | Post-processor | Passes | Fails |
|---|---|---|---|
| **anonymous** | none | permitAll GETs (`:71-73`) | `/api/me` → 401 (`:74`); writes → 401 (`:75-78`) |
| **authenticated non-admin** | `.with(jwt())` (no `ROLE_ADMIN`) | `/api/me` → 200 `{admin:false}` (`:74`) | all four writes → **403** (`:75-78`) |
| **admin** | `.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))` | writes pass (`:75-78`) | — |

Authority string must be **exactly `"ROLE_ADMIN"`** to satisfy `hasRole("ADMIN")`.

## Response-guidance verification (verify, not blindly accept)

| Risk | Plan's guidance | Verdict |
|---|---|---|
| #1 | "Guest & logged-in fan get 403 on every write/generate/publish/delete; only admin passes; challenge 'admin happy-path proves fan is cut off'; avoid testing only admin path / over-mocking the security filter." | **Verified & sharpened.** Code is correct today → test is a regression guard. The real anti-pattern here is the admin-only happy path; the matrix MUST include anon-401 and non-admin-403 rows for all four writes. Cheapest layer = `@WebMvcTest` slice (consistent with plan's "integration MockMvc"). |
| #2 | "Public read returns only PUBLISHED; generation creates a draft, publishing nothing; challenge '201/200 from generation = publicly visible'; avoid assertion copied from status logic." | **Corrected.** Generation creates **no draft at all** (persists nothing) — "generation creates a draft" is inaccurate. Reframe: *public read is PUBLISHED-only AND generation writes nothing*. Oracle from the controller filter + no-save fact, not from `NewsPost.published()`. The "201/200" caution is exactly right and now has a concrete anchor (generate=200-no-save vs create=201-publishes). |
| #4 | "Preflight/CORS lets a legal origin through, protected endpoint still requires a valid token; challenge 'CORS header set = auth works'; avoid asserting the header alone." | **Verified.** CORS (`WebMvcConfigurer` + `http.cors`) is orthogonal to the authz matrix. Test must pair a preflight-header assertion with a protected-path rejection (401 tokenless / 403 non-admin). |

## Backport candidates (test-plan §2 — Source/wording/guidance only, no anchors)

1. **Risk #2 wording & guidance.** The plan and its Risk Response Guidance say generation
   "tworzy draft, niczego nie publikując." In this codebase generation **persists nothing**
   (no DRAFT status, no `save`). The guardrail still holds, but for a different reason.
   Suggested §2 correction: reframe the *how* to "publiczny odczyt zwraca wyłącznie
   PUBLISHED; generacja niczego nie persystuje (proposal żyje w przeglądarce admina)."
2. **Risk #2 Source citation.** §2 cites "migracja V5 published_check (status wpisu)" as
   evidence. V5 only enforces `PUBLISHED ⇒ published_at NOT NULL`; it does **not** enforce
   the draft-never-public gate. Keep V5 as data-integrity evidence but don't imply it is the
   visibility gate. (Minor — not misleading enough to force a rewrite; note for `--refresh`.)

Neither backport adds a file anchor; both stay in the Source/wording/guidance cells
(principle #3 preserved). Recommend applying #1 now (it changes what the tests assert),
deferring #2 to `--refresh`.

## Code References

- `backend/src/main/java/com/plomienkostrze/security/SecurityConfig.java:66-79` — the entire authorization matrix (single source of truth)
- `backend/src/main/java/com/plomienkostrze/security/SecurityConfig.java:96-102` — UID-allowlist → `ROLE_ADMIN` converter
- `backend/src/main/java/com/plomienkostrze/security/SecurityConfig.java:85-94` — lazy `NimbusJwtDecoder` (JWKS URI, offline at context load)
- `backend/src/main/java/com/plomienkostrze/security/SecurityConfig.java:63-65` — CSRF off, `cors(withDefaults())`, STATELESS
- `backend/src/main/java/com/plomienkostrze/web/CorsConfig.java:15-28` — CORS via `WebMvcConfigurer`, `allowCredentials` unset
- `backend/src/main/java/com/plomienkostrze/web/NewsPostController.java:77-91` — public list, `findByStatus(PUBLISHED,...)`
- `backend/src/main/java/com/plomienkostrze/web/NewsPostController.java:104-110` — public detail, `.filter(PUBLISHED)` → 404
- `backend/src/main/java/com/plomienkostrze/web/NewsPostController.java:97-102` — admin create, `NewsPost.published(...)`, 201
- `backend/src/main/java/com/plomienkostrze/web/NewsGenerationController.java:39-56` — generate, returns 200 body, no save
- `backend/src/main/java/com/plomienkostrze/news/NewsGenerationService.java:15-16,35,39-45` — `ProposalDraft`, persists nothing
- `backend/src/main/java/com/plomienkostrze/news/NewsPostStatus.java:7-9` — only `PUBLISHED`
- `backend/src/main/java/com/plomienkostrze/news/NewsPostRepository.java:9` — `findByStatus` derived query
- `backend/src/main/java/com/plomienkostrze/web/MeController.java:19-22` — `/api/me`, re-checks `ROLE_ADMIN`
- `backend/src/main/resources/db/migration/V5__news_posts_published_check.sql:5-7` — `PUBLISHED ⇒ published_at NOT NULL`
- `backend/pom.xml:125-129` — test deps (add `spring-security-test` here); `:99-103` H2
- `backend/src/test/resources/application.properties:7-16` — H2, Flyway off, empty admin uids, JWKS URI
- `backend/src/test/java/com/plomienkostrze/PlomienKostrzeApiApplicationTests.java` — existing `@SpringBootTest contextLoads`

## Architecture Insights

- **Centralized authorization, zero method security.** One `authorizeHttpRequests` block owns
  every rule. This is ideal for a role-matrix test: one MockMvc slice against `SecurityConfig`
  covers the whole surface, and any future drift (a matcher weakened to `authenticated`, a new
  endpoint falling through to `denyAll`) is caught in one place.
- **The publish guardrail is enforced by *absence of a persistence path*, not a status flag.**
  Generation returns a transient proposal; the only writer creates PUBLISHED rows. This is a
  deliberate design (gated-news-generation slice) and the strongest possible form of "system
  never publishes by itself" — but it means the #2 test asserts a *negative* (generate leaves
  the public list unchanged), not a state transition.
- **Auth is stateless bearer-JWT with a UID allowlist for elevation.** Admin is not a Firebase
  custom claim; it's a server-side allowlist lookup on `sub`. Tests inject `ROLE_ADMIN`
  directly rather than simulating the allowlist.

## Historical Context (from prior changes)

- `context/archive/2026-07-08-gated-news-generation/` — the slice that built `/generate` as a
  non-persisting proposal endpoint; explains why no DRAFT status exists (proposal lives in the
  admin's browser until an explicit `POST /api/news-posts`).
- `context/archive/2026-07-06-manual-news-publishing/` — introduced admin-gated create/publish.
- `context/archive/2026-07-05-public-news-reading/` — introduced the PUBLISHED-only public read.
- `context/archive/2026-07-10-news-post-management/` — edit/delete (PUT/DELETE) admin endpoints.

## Open Questions

- **Phase 1 vs Phase 2 boundary for the PUBLISHED-only filter.** The controller-level gate is
  fully testable in a `@WebMvcTest` slice with a mocked repository (assert the controller
  requests `findByStatus(PUBLISHED,...)` and 404s a non-published `findById`). Proving the
  *repository derived query* actually filters at the DB belongs to Phase 2 (Testcontainers PG).
  `/10x-plan` should decide whether Phase 1 asserts only the controller contract or also adds a
  light `@DataJpaTest`. Recommendation: controller contract only in Phase 1; repo query → Phase 2.
- **CORS assertion depth.** Whether Phase 1 asserts the preflight `OPTIONS` response headers
  (via MockMvc `options()`) or only the orthogonality property (protected path still 401/403
  regardless of Origin). Recommendation: both, cheaply, in the same slice.
