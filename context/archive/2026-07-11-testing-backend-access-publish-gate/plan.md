# Backend Test Harness — Access Gate, Publish Gate & CORS/Security (Phase 1) Implementation Plan

## Overview

This is **rollout Phase 1** of `context/foundation/test-plan.md` ("Backend harness +
bramka dostępu i publikacji"). It stands up the backend integration-test runner and pins
three of the project's top risks at the **cheapest useful layer — a `@WebMvcTest` MockMvc
slice** (no DB, no live Firebase):

- **#1 (High × High) — authorization.** Guest and logged-in fan get 401/403 on every
  write / generate / publish / delete; only admin passes.
- **#2 — publish gate (reframed).** Public read returns PUBLISHED-only; generation
  persists nothing.
- **#4 — CORS/security orthogonality.** A legal Origin passes preflight, yet the protected
  endpoint still demands a valid admin token.

The plan is broken into **five sub-phases** (modeled as plan Phases below), ordered by
**cost × signal then risk priority**: the harness prerequisite first (cheapest, unblocks
everything), then risks #1 → #2 → #4, then CI-gate activation + test-plan doc updates.

> Terminology: throughout this plan, "Phase N" refers to a **sub-phase of test-plan
> rollout Phase 1** — not a new rollout phase. Rollout Phases 2–4 remain `not started`.

## Current State Analysis

- **Authorization is centralized and correct today.** Every rule lives in one
  `SecurityConfig.authorizeHttpRequests` block (`SecurityConfig.java:66-79`); there is no
  method security anywhere (`grep hasRole|@PreAuthorize` hits only `SecurityConfig.java`).
  Every mutating endpoint uses `hasRole("ADMIN")`, not `.authenticated()`. **Risk #1 is a
  regression guard, not a live bug** — the matrix test locks the boundary so a future edit
  can't silently downgrade an ADMIN matcher to `authenticated`.
- **There is NO `DRAFT` status and NO persisted draft state.** `NewsPostStatus` has a
  single value `PUBLISHED` (`NewsPostStatus.java:7-9`). `POST /api/news-posts/generate`
  **persists nothing** — it returns an in-memory `ProposalDraft` with HTTP 200 and never
  calls `repository.save` (`NewsGenerationController.java:39-56`,
  `NewsGenerationService.java:15-16`). The publish guardrail holds by *absence of a
  persistence path*, not by a status flag. The only writer is `POST /api/news-posts` →
  `NewsPost.published(...)` → 201 (`NewsPostController.java:97-102`).
- **Public read is PUBLISHED-only at the controller.** List calls
  `repository.findByStatus(PUBLISHED, ...)` (`NewsPostController.java:87`); detail
  post-filters `.filter(status == PUBLISHED)` → 404 (`NewsPostController.java:107`). The
  repository's `findById` is *not* status-filtered — the gate is controller-side.
- **CORS is orthogonal to authz.** Registered via `WebMvcConfigurer` (`CorsConfig.java`),
  wired with `http.cors(withDefaults())` (`SecurityConfig.java:64`); `allowCredentials`
  unset (defaults false). Preflight `OPTIONS` is answered before authorization; a legal
  Origin never lets a request skip the token.
- **`spring-security-test` is MISSING from `pom.xml`.** `spring-boot-starter-webmvc-test`
  is present (`pom.xml:125-129`, brings JUnit 6 + AssertJ + Mockito + MockMvc); H2 is
  test-scoped. But `SecurityMockMvcRequestPostProcessors.jwt()` / `@WithMockUser` do **not**
  resolve without `spring-security-test`. This is the one hard prerequisite.
- **The only existing test** is `PlomienKostrzeApiApplicationTests` — a bare
  `@SpringBootTest contextLoads()`. Test props (`src/test/resources/application.properties`)
  shadow main: H2, Flyway disabled, `ddl-auto=create-drop`, `app.admin.uids=` **empty**
  (→ nobody is admin), JWKS URI present but fetched lazily (offline at context load).
- **CI already runs the tests.** `.github/workflows/backend.yml` runs `./mvnw -B verify`
  on every PR (`build-test` job) and on push to master (`deploy` job). Surefire runs
  `*Test` classes during `verify` and fails the build on any test failure. **The "backend
  tests" gate mechanism already exists** — activating it (test-plan §5) is a documentation
  change, not a workflow edit.

## Desired End State

`./mvnw -B verify` (run locally and in the PR `build-test` job) executes three new
`@WebMvcTest` slice test classes that all pass, and would **fail** if:

- any admin-gated matcher is weakened to `authenticated` or a new endpoint falls through to
  `denyAll` incorrectly (risk #1);
- public read starts returning non-PUBLISHED content, or `/generate` starts persisting
  (risk #2);
- a CORS change let a request bypass the authz matrix, or authz was loosened while CORS
  masked it (risk #4).

The test-plan reflects the shipped state: §5 "backend tests" gate marked active, §2 risk #2
reframed, §6.1/§6.2/§6.6 cookbook filled in.

### Key Discoveries

- Single authz block: `SecurityConfig.java:66-79` — one slice covers the whole surface.
- Admin authority is exactly `"ROLE_ADMIN"`, minted from a UID allowlist
  (`SecurityConfig.java:96-102`); test props leave the allowlist empty, so tests must
  **inject the authority directly** via `jwt().authorities(...)`, not rely on the converter.
- `jwt()` post-processor injects the `Authentication` and **bypasses the `JwtDecoder`** — no
  live Firebase/JWKS fetch, no DB needed.
- Oracle for #2 is the controller filter + the "generate saves nothing" fact — **not**
  `NewsPost.published()`, and **not** the V5 `published_check` constraint (which only
  guarantees PUBLISHED ⇒ `published_at NOT NULL`).
- `generate`=200-no-save vs `create`=201-publishes — the two success codes must not be
  conflated (`NewsGenerationController.java:40` vs `NewsPostController.java:98`).

## What We're NOT Doing

- **No `@DataJpaTest` / repository-level DB assertion in this phase.** Proving the
  `findByStatus` derived query actually filters at the database belongs to **rollout Phase 2
  (Testcontainers PG)**. Phase 1 asserts the *controller contract* only (that the controller
  requests `findByStatus(PUBLISHED, ...)` and 404s a non-published `findById`).
- **No `@SpringBootTest` / full-context tests, no Testcontainers, no live Firebase.** All
  three risks are covered by the slice.
- **No production-code changes.** The code is correct today; these are regression guards.
  (The one exception is the `pom.xml` test-dependency add and doc edits.)
- **No workflow YAML edit.** `verify` already runs the tests; the gate is activated in the
  test-plan doc.
- **Not touching risks #3, #5, #6, #7** — later rollout phases.
- **Not asserting generated prose content** (oracle problem — see test-plan §7).

## Implementation Approach

Three focused `@WebMvcTest` slice classes, **one per risk** (clean 1:1 with the risk map;
each doubles as a cookbook example). Each slice imports `SecurityConfig`, `@MockBean`s the
service/repository beans, and supplies the three constructor `@Value`s via the shadowed test
`application.properties` (already present) — no per-class `@TestPropertySource` needed since
those keys already exist there. Principals are simulated with `SecurityMockMvcRequestPostProcessors`:
anonymous (no post-processor), authenticated-non-admin (`jwt()`), admin
(`jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))`).

The role matrix (#1) is expressed as a **parameterized test** (`@ParameterizedTest` +
`@MethodSource` yielding `(principal, method, path, expectedStatus)` tuples) so the matrix is
data — adding an endpoint is one row, and it is structurally hard to omit the anon-401 /
non-admin-403 rows. The no-save oracle (#2) is **`verifyNoInteractions` / `never().save(...)`
on the mocked repository** (the DB-observable "list unchanged" property is a Phase-2 concern).

## Critical Implementation Details

- **`app.admin.uids` is empty in test props by design** — do NOT try to mint admin through
  the production allowlist converter. Inject `ROLE_ADMIN` directly with
  `jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))`. The authority string must be
  **exactly `"ROLE_ADMIN"`** to satisfy `hasRole("ADMIN")` (which auto-prepends `ROLE_`).
- **CSRF is disabled** (`SecurityConfig.java:63`) and sessions are STATELESS, so
  MockMvc POST/PUT/DELETE need no CSRF token — but a `@WebMvcTest` slice still imports
  `SecurityConfig` explicitly (via `@Import`) or Spring's default test security applies
  instead of the real chain. Import the real `SecurityConfig` **and** `CorsConfig` so the
  slice exercises the production filter chain, not Boot's test default.
- **`GET /error` dispatch:** the matrix's error-path statuses (e.g. a 404 from detail) flow
  through the container ERROR dispatch, which `SecurityConfig.java:70` permits. Assert the
  final HTTP status the client sees, not intermediate dispatches.
- **Status ordering for anon vs non-admin:** anonymous → **401** (no credentials);
  authenticated-non-admin → **403** (authenticated but lacks role). These are distinct rows
  and both must appear for every write.

---

## Phase 1: Test harness prerequisite

### Overview

Add the missing test dependency and establish the shared `@WebMvcTest` slice pattern +
principal helpers, proving one trivial secured assertion runs green. Cheapest sub-phase;
unblocks all three risk suites.

### Changes Required:

#### 1. Add `spring-security-test`

**File**: `backend/pom.xml`

**Intent**: Bring in `SecurityMockMvcRequestPostProcessors` (`jwt()`, `.authorities(...)`) so
slices can simulate principals. Without it the risk suites don't compile.

**Contract**: New `<dependency>` `org.springframework.security:spring-security-test`, `<scope>test</scope>`,
added alongside the existing `spring-boot-starter-webmvc-test` block (`pom.xml:125-129`).
Version is managed by the Boot parent BOM — no explicit `<version>`.

#### 2. Shared slice test support

**File**: `backend/src/test/java/com/plomienkostrze/web/` (new package-local test helpers)

**Intent**: A single place for the principal post-processors so the three risk classes don't
duplicate them. Behavior asserted here: the real `SecurityConfig` chain loads in a slice and
distinguishes an admin from an anonymous caller on one representative endpoint.

**Contract**: A small helper (static factory or nested constants) exposing `adminJwt()` →
`jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))` and `userJwt()` → `jwt()`.
A minimal `@WebMvcTest` that `@Import`s `SecurityConfig` + `CorsConfig`, `@MockBean`s
`NewsPostRepository` + `NewsGenerationService`, and asserts one line (e.g. `POST /api/news-posts`
anonymous → 401) to prove the harness resolves. This assertion is folded into Phase 2's
matrix and need not survive as its own test.

- **Regression caught**: harness rot — if `spring-security-test` or the real chain import
  regresses, every downstream suite fails fast here.
- **Research source**: research.md "Test harness — feasibility & the missing dependency";
  MockMvc simulation recipe table.
- **Edge/boundary**: empty `app.admin.uids` in test props — proves admin is injected, not
  derived.
- **Anti-pattern avoided**: over-mocking the security filter (the real `SecurityConfig` is
  imported, not stubbed).

### Success Criteria:

#### Automated Verification:

- Dependency resolves and project compiles: `cd backend && ./mvnw -B test-compile`
- Harness smoke assertion passes: `cd backend && ./mvnw -B -Dtest='*Test' test` (green)

#### Manual Verification:

- `jwt()` / `.authorities(...)` imports resolve in the IDE with no unresolved symbols.

**Implementation Note**: After automated verification passes, pause for human confirmation
before Phase 2.

---

## Phase 2: Risk #1 — Authorization role matrix

### Overview

The highest-priority risk (High × High). Prove the guest and logged-in-fan lockout across the
entire mutating surface with a parameterized matrix; admin passes.

### Changes Required:

#### 1. `AuthorizationMatrixTest`

**File**: `backend/src/test/java/com/plomienkostrze/web/AuthorizationMatrixTest.java` (new)

**Intent**: Lock the authorization boundary so a future edit can't silently downgrade an
`hasRole("ADMIN")` matcher to `authenticated` or let a new endpoint fall through. Data-driven
matrix over principals × endpoints.

**Contract**: `@WebMvcTest` importing `SecurityConfig` (+ `CorsConfig`), `@MockBean`
`NewsPostRepository` + `NewsGenerationService`. A `@ParameterizedTest` fed by `@MethodSource`
yielding `(principal, HttpMethod, path, expectedStatus)` rows:

  - **anonymous** → `POST /api/news-posts` 401, `POST /api/news-posts/generate` 401,
    `PUT /api/news-posts/1` 401, `DELETE /api/news-posts/1` 401, `GET /api/me` 401;
    permitAll GETs (`/api/news-posts`, `/api/news-posts/1`, `/api/ping`) → not 401/403.
  - **authenticated non-admin** (`userJwt()`) → all four writes + `/generate` **403**;
    `GET /api/me` → 200.
  - **admin** (`adminJwt()`) → all four writes + `/generate` **not 401/403** (2xx/4xx
    business status, e.g. create 201, generate 200; a mocked repo/service supplies benign
    returns); `GET /api/me` → 200.

- **Behavior asserted**: every admin-gated endpoint rejects anon (401) and non-admin (403)
  and admits admin; the sole `.authenticated()` endpoint (`/api/me`) admits any signed-in
  user.
- **Regression caught**: a matcher downgraded ADMIN→authenticated (non-admin would wrongly
  pass), a new write endpoint added without a rule (falls to `denyAll` or, worse, permitAll).
- **Research source**: research.md Risk #1 matrix table + MockMvc simulation recipe;
  `SecurityConfig.java:71-79`.
- **Edge/error/boundary**: the 401-vs-403 boundary (anonymous vs authenticated-without-role)
  asserted as separate rows; `denyAll` fall-through implicitly covered by `/api/me` for
  anon → 401.
- **Anti-pattern avoided**: admin-only happy path — the matrix *requires* the anon and
  non-admin rows; over-mocking the filter (real `SecurityConfig` imported).

### Success Criteria:

#### Automated Verification:

- Matrix passes: `cd backend && ./mvnw -B -Dtest=AuthorizationMatrixTest test`
- All parameterized cases execute (row count == principals × endpoints, no silent skips) —
  visible in Surefire output.

#### Manual Verification:

- Sanity-check by temporarily downgrading one `hasRole("ADMIN")` to `authenticated` locally
  and confirming the non-admin-403 row fails; revert.

**Implementation Note**: Pause for human confirmation before Phase 3.

---

## Phase 3: Risk #2 — Publish gate (public read PUBLISHED-only + generate persists nothing)

### Overview

Prove the reframed guardrail: content is publicly visible only as PUBLISHED, and generation
never makes anything public (no persistence path).

### Changes Required:

#### 1. `PublishGateTest`

**File**: `backend/src/test/java/com/plomienkostrze/web/PublishGateTest.java` (new)

**Intent**: Pin the two halves of the gate — the controller's PUBLISHED-only read contract,
and the "generate saves nothing" invariant.

**Contract**: `@WebMvcTest` importing `SecurityConfig`, `@MockBean` `NewsPostRepository` +
`NewsGenerationService`. Cases:

  - **List is PUBLISHED-only**: `GET /api/news-posts` → verify the controller calls
    `repository.findByStatus(NewsPostStatus.PUBLISHED, <Pageable>)` (Mockito `verify` /
    `ArgumentCaptor` on the status arg); stub returns an empty page; assert 200. The oracle
    is *which status the controller asks for*, not the response body.
  - **Detail 404s non-published**: stub `findById(id)` to return a `NewsPost` whose status is
    forced non-PUBLISHED (or assert the `.filter(status == PUBLISHED)` path by stubbing a
    published one → 200 and an empty optional → 404). Since the enum has only `PUBLISHED`,
    assert the published happy path (200) **and** the not-found path (empty `findById` → 404)
    to pin the filter's presence; note the "non-published exists → 404" branch is
    unreachable-by-construction today and is asserted at DB level in Phase 2.
  - **Generate persists nothing**: stub `generationService.generateFromLastMatch()` to return
    a `ProposalDraft("t","c")`; `POST /api/news-posts/generate` with `adminJwt()` → **200**
    body `{title,content}`; then `verifyNoInteractions(repository)` (or
    `verify(repository, never()).save(any())`).
  - **Create publishes (contrast row)**: `POST /api/news-posts` with `adminJwt()` and a valid
    body → **201**, and `repository.save(...)` **is** called — documenting the 200-no-save
    vs 201-publishes distinction so the two are never conflated.

- **Behavior asserted**: public read requests PUBLISHED-only; generate returns a body but
  writes nothing; create is the only path that persists.
- **Regression caught**: list widened to fetch all statuses; detail filter removed;
  `/generate` gaining a `save` call (auto-publish); create losing its persist.
- **Research source**: research.md Risk #2 (reframed) + Oracle sourcing paragraph;
  `NewsPostController.java:87,107,100`, `NewsGenerationController.java:40`,
  `NewsGenerationService.java:15-16`.
- **Edge/error/boundary**: detail not-found (404) path; the enum-has-one-value boundary
  (non-published-exists branch deferred to Phase 2).
- **Anti-pattern avoided**: asserting "generate 200 = publicly visible" (the oracle trap);
  assertion copied from `NewsPost.published()` status logic; asserting generated prose
  verbatim.

### Success Criteria:

#### Automated Verification:

- Passes: `cd backend && ./mvnw -B -Dtest=PublishGateTest test`
- `findByStatus` captor confirms `PUBLISHED` argument; `verifyNoInteractions(repository)`
  holds after `/generate`.

#### Manual Verification:

- Confirm the contrast is legible: the class makes the 200-no-save vs 201-publishes
  distinction obvious to a future reader.

**Implementation Note**: Pause for human confirmation before Phase 4.

---

## Phase 4: Risk #4 — CORS/security orthogonality

### Overview

Prove CORS and authorization are orthogonal: a legal Origin passes preflight, yet the
protected path still rejects a tokenless / non-admin call.

### Changes Required:

#### 1. `CorsOrthogonalityTest`

**File**: `backend/src/test/java/com/plomienkostrze/web/CorsOrthogonalityTest.java` (new)

**Intent**: Guard against a CORS change silently loosening (or a header masking a loosened)
authz gate. Pairs a preflight assertion with a protected-path rejection.

**Contract**: `@WebMvcTest` importing `SecurityConfig` **and** `CorsConfig`, `@MockBean`
service/repository. Cases:

  - **Preflight passes for a legal Origin**: `OPTIONS /api/news-posts` with
    `Origin: http://localhost:4200` + `Access-Control-Request-Method: POST` → 200/2xx with
    `Access-Control-Allow-Origin: http://localhost:4200` present (from `CorsConfig`
    allowed-origins). No auth needed for preflight.
  - **Orthogonality — tokenless still 401**: `POST /api/news-posts` with the same legal
    `Origin` header but **no** `jwt()` → **401** (the CORS header does not grant access).
  - **Orthogonality — non-admin still 403**: same `POST` with `userJwt()` + legal `Origin`
    → **403**.

- **Behavior asserted**: preflight for a legal Origin succeeds *and* the protected write
  still enforces the token/role regardless of Origin.
- **Regression caught**: someone "fixing CORS" by relaxing the authz matcher; a permitAll
  creeping onto a write; `allowCredentials` toggled in a way that changes the gate.
- **Research source**: research.md Risk #4 + orthogonality paragraph; `CorsConfig.java:15-28`,
  `SecurityConfig.java:64,71-79`.
- **Edge/error/boundary**: OPTIONS-answered-before-auth (no permitAll on OPTIONS needed);
  the legal-Origin-but-no-token combination.
- **Anti-pattern avoided**: asserting the CORS header alone — every case pairs the header
  with a protected-path status assertion.

### Success Criteria:

#### Automated Verification:

- Passes: `cd backend && ./mvnw -B -Dtest=CorsOrthogonalityTest test`
- Full suite green: `cd backend && ./mvnw -B verify`

#### Manual Verification:

- Confirm the preflight assertion checks the actual allowed Origin value, not just header
  presence.

**Implementation Note**: Pause for human confirmation before Phase 5.

---

## Phase 5: CI gate activation + test-plan documentation

### Overview

Activate the "backend tests" quality gate (already executed by `verify`) and bring
`context/foundation/test-plan.md` in sync with the shipped state: apply research backport #1
(risk #2 reframe) and backfill the cookbook.

### Changes Required:

#### 1. Activate the backend-tests gate

**File**: `context/foundation/test-plan.md` (§5 Quality Gates)

**Intent**: Record that the "unit + integration (backend)" gate is now enforced. No workflow
edit — `./mvnw -B verify` already runs Surefire on every PR (`backend.yml` `build-test`) and
fails on any test failure.

**Contract**: In §5, the row `unit + integration (backend) | local + CI | required after §3
Phase 1 | ...` — annotate as active/enforced now that Phase 1 shipped (e.g. drop "after §3
Phase 1", or mark ✅). Add a one-line note that `verify` is the executing mechanism.

#### 2. Apply backport #1 (risk #2 reframe)

**File**: `context/foundation/test-plan.md` (§2 Risk Map row #2 + Risk Response Guidance row #2)

**Intent**: Correct the mechanism wording — generation persists **nothing** (no DRAFT
status, no `save`); the guardrail holds by absence of a persistence path.

**Contract**: Edit risk #2's scenario/Source wording from "Propozycja / draft trafia..." /
"generacja tworzy draft, niczego nie publikując" to reflect "publiczny odczyt zwraca wyłącznie
PUBLISHED; generacja niczego nie persystuje (proposal żyje w przeglądarce admina)." Keep V5
`published_check` as data-integrity evidence, not the visibility gate. (Backport #2 — the V5
citation nuance — is deferred to `--refresh` per research.)

#### 3. Backfill cookbook

**File**: `context/foundation/test-plan.md` (§6.1, §6.2, §6.6)

**Intent**: Turn the "TBD — see §3 Phase 1" placeholders into real how-to guidance grounded
in the three classes just written.

**Contract**:
  - §6.1 "Adding a backend unit test" — the `@WebMvcTest` slice recipe (import `SecurityConfig`,
    `@MockBean` beans, test-props already carry the `@Value`s).
  - §6.2 "Adding a backend integration test (MockMvc / role matrix)" — the principal
    post-processors (`adminJwt()`/`userJwt()`, exact `"ROLE_ADMIN"` string), the parameterized
    matrix pattern, and the 401-vs-403 distinction; point at `AuthorizationMatrixTest`.
  - §6.6 "Per-rollout-phase notes" — 2–3 lines: `spring-security-test` added; slices live in
    `backend/src/test/java/com/plomienkostrze/web/`; no-save oracle = `verifyNoInteractions`;
    DB-level PUBLISHED filter deferred to Phase 2.

#### 4. Advance rollout status

**File**: `context/foundation/test-plan.md` (§3 Phased Rollout table) and
`context/changes/testing-backend-access-publish-gate/change.md`

**Intent**: Move Phase 1 status toward `complete`; stamp `change.md`.

**Contract**: §3 row 1 Status → `complete` (once Progress is all `[x]`); `change.md`
`status: complete`, `updated: <today>`.

### Success Criteria:

#### Automated Verification:

- Full suite green: `cd backend && ./mvnw -B verify`
- Test-plan has no remaining "TBD — see §3 Phase 1" in §6.1/§6.2:
  `! grep -n 'TBD — see §3 Phase 1' context/foundation/test-plan.md`

#### Manual Verification:

- §2 risk #2 wording no longer implies a persisted draft; §5 gate reads as active.
- Cookbook §6.2 is copy-pasteable enough for the next contributor to add a matrix row.

**Implementation Note**: Final sub-phase — after verification, the rollout Phase 1 is
`complete`; re-running `/10x-test-plan` resumes at Phase 2.

---

## Testing Strategy

### Unit Tests

- None added — the risks are HTTP-boundary behaviors; the slice is the right layer.

### Integration Tests

- `AuthorizationMatrixTest` — parameterized principal × endpoint matrix (#1).
- `PublishGateTest` — PUBLISHED-only read contract + generate-no-save (#2).
- `CorsOrthogonalityTest` — preflight + orthogonal authz rejection (#4).

### Manual Testing Steps

1. `cd backend && ./mvnw -B verify` → all green.
2. Temporarily weaken one `hasRole("ADMIN")` to `authenticated` → `AuthorizationMatrixTest`
   non-admin-403 row fails; revert.
3. Temporarily add a `repository.save` in the generate path → `PublishGateTest`
   `verifyNoInteractions` fails; revert.

## Performance Considerations

Slice tests boot only the web layer + security chain (no JPA, no DB) — sub-second each;
negligible CI impact.

## Migration Notes

None — additive test code + one test-scoped dependency + doc edits. No schema, no runtime
behavior change.

## References

- Research: `context/changes/testing-backend-access-publish-gate/research.md`
- Strategy: `context/foundation/test-plan.md` (§2 risk map, §3 rollout, §5 gates, §6 cookbook)
- Authz matrix: `backend/src/main/java/com/plomienkostrze/security/SecurityConfig.java:66-79,96-102`
- Publish gate: `backend/src/main/java/com/plomienkostrze/web/NewsPostController.java:87,100,107`;
  `backend/src/main/java/com/plomienkostrze/web/NewsGenerationController.java:39-56`
- CORS: `backend/src/main/java/com/plomienkostrze/web/CorsConfig.java:15-28`
- CI: `.github/workflows/backend.yml:43` (`./mvnw -B verify` on PR)

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Test harness prerequisite

#### Automated

- [x] 1.1 Dependency resolves and project compiles (`./mvnw -B test-compile`) — 27813eb
- [x] 1.2 Harness smoke assertion passes (`./mvnw -B -Dtest='*Test' test`) — 27813eb

#### Manual

- [x] 1.3 `jwt()` / `.authorities(...)` imports resolve with no unresolved symbols — 27813eb

### Phase 2: Risk #1 — Authorization role matrix

#### Automated

- [x] 2.1 `AuthorizationMatrixTest` passes (`./mvnw -B -Dtest=AuthorizationMatrixTest test`) — 76dfed4
- [x] 2.2 All parameterized cases execute (row count == principals × endpoints, no skips) — 76dfed4

#### Manual

- [x] 2.3 Downgrading one `hasRole("ADMIN")` to `authenticated` fails the non-admin-403 row (then revert) — 76dfed4

### Phase 3: Risk #2 — Publish gate

#### Automated

- [x] 3.1 `PublishGateTest` passes (`./mvnw -B -Dtest=PublishGateTest test`) — 2feb33a
- [x] 3.2 `findByStatus` captor confirms `PUBLISHED`; `verifyNoInteractions(repository)` holds after `/generate` — 2feb33a

#### Manual

- [x] 3.3 The 200-no-save vs 201-publishes distinction is legible in the class — 2feb33a

### Phase 4: Risk #4 — CORS/security orthogonality

#### Automated

- [x] 4.1 `CorsOrthogonalityTest` passes (`./mvnw -B -Dtest=CorsOrthogonalityTest test`) — 13d91a0
- [x] 4.2 Full suite green (`./mvnw -B verify`) — 13d91a0

#### Manual

- [x] 4.3 Preflight assertion checks the actual allowed Origin value, not just header presence — 13d91a0

### Phase 5: CI gate activation + test-plan documentation

#### Automated

- [x] 5.1 Full suite green (`./mvnw -B verify`) — de24ebd
- [x] 5.2 No remaining "TBD — see §3 Phase 1" in §6.1/§6.2 (`! grep ...`) — de24ebd

#### Manual

- [x] 5.3 §2 risk #2 wording no longer implies a persisted draft; §5 gate reads as active — de24ebd
- [x] 5.4 Cookbook §6.2 is copy-pasteable for the next contributor — de24ebd
