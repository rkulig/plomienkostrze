# Fans Forum (S-07) Implementation Plan

## Overview

Add a login-gated club forum where any authenticated Firebase user ("fan") can read
and create discussion threads and post flat, chronological replies. This is the first
slice to activate the **fan tier** of the access model: writes and reads alike are
gated on `.authenticated()` rather than `hasRole("ADMIN")`. Scope is deliberately
narrow — **threads + posts only**. No edit, no delete, no moderation, no reactions,
no comments (all parked as fast-follow per the roadmap).

## Current State Analysis

The auth and domain foundations are fully reusable — the forum builds new domain
tables and views, not new infrastructure:

- **Auth is already fan-capable.** `signInWithGoogle()` (`frontend/src/app/auth/auth-service.ts:32`)
  logs in *any* Google user. "Admin" is orthogonal: a server-side UID allowlist
  (`ADMIN_UIDS` → `ROLE_ADMIN`) in `backend/.../security/SecurityConfig.java`. The
  backend already recognises three caller tiers — anonymous, authenticated-non-admin
  ("fan"), and admin. There is **no user table**; identity is the Firebase UID
  (`jwt.getSubject()`).
- **The auth interceptor already attaches the token** to any `environment.apiBaseUrl`
  request (`frontend/src/app/auth/auth-interceptor.ts:14`) — authenticated forum reads
  and writes need no interceptor change.
- **Domain pattern is crisp:** immutable entity + static factory + `@PrePersist`
  (`backend/.../news/NewsPost.java`), `JpaRepository`, controller with nested `record`
  DTOs and `@Valid` request records (`backend/.../web/NewsPostController.java`),
  `ResponseStatusException` for errors, authorization in `SecurityConfig` URL matchers
  (never `@PreAuthorize`).
- **Migrations start at V7.** Latest is `V6__news_posts_updated_at.sql`. Tests use H2
  with Flyway disabled + `ddl-auto=create-drop`, so entity `@Column` definitions MUST
  match the migration exactly (production runs `ddl-auto=validate`).
- **Frontend mirrors the `news/` feature:** one root `@Injectable` API service per
  domain (DTOs declared at top), signal-based standalone components, lazy routes in
  `frontend/src/app/app.routes.ts`, nav links in `frontend/src/app/app.html`. Login-
  gating is done via an in-component `effect()` redirect, not a route guard (see
  `admin-panel.ts:46`).
- **A ready-made authorization test harness exists:** `AuthorizationMatrixTest` sweeps
  anon/fan/admin × every endpoint; `MockPrincipals.userJwt()` already mocks a signed-in
  fan; `MockPrincipals.adminJwt()` an admin.

### Key Discoveries:

- Fan display name is NOT on `Authentication.getName()` (that returns the UID `sub`).
  It comes from JWT claims — inject `@AuthenticationPrincipal Jwt jwt` and read
  `jwt.getClaimAsString("name")` / `"email"`. (`backend/.../web/MeController.java:20`
  shows the `Authentication` injection pattern; the forum needs the `Jwt` variant to
  reach the name/email claims.)
- `SecurityConfig` has no existing matcher for "any authenticated fan can POST" — the
  only `.authenticated()` example today is a GET (`/api/me`). New matchers are required
  for the forum's authenticated write AND read endpoints.
- `@WebMvcTest` component-scans ALL controllers, so every controller's service
  collaborator must exist as a `@MockitoBean` or the context fails to load. Adding a
  `ForumService`-backed controller means adding that mock to the existing
  `@WebMvcTest` classes (`AuthorizationMatrixTest`, `PublishGateTest`,
  `CorsOrthogonalityTest`).
- Forum reads being `.authenticated()` is a genuinely new endpoint class — every
  existing GET is `permitAll`. This is intentional (PRD: "całe forum jest za logowaniem").

## Desired End State

A logged-in fan opens a "Forum" tab, sees a list of threads ordered by most-recent
activity, opens a thread to read its opening post and all replies in chronological
order, starts a new thread (title + opening message), and replies to any thread. A
guest (not logged in) who navigates to the forum is redirected to login and sees no
forum content — neither the API nor the SPA exposes threads/posts to anonymous callers.
The forum starts empty with a friendly "brak wątków — załóż pierwszy" state.

Verify: with a logged-in fan, `POST` a thread then a reply, confirm the thread bubbles
to the top of the list and its `post_count` reflects the reply; as an anonymous caller,
confirm every forum endpoint returns 401 and the SPA redirects `/forum` to login.

## What We're NOT Doing

- **No edit or delete** of threads/posts — not by the author, not by admin. Append-only.
- **No moderation** (FR-016) — parked as fast-follow.
- **No reactions / thumbs (FR-011) or news comments (FR-012–FR-013)** — out of scope.
- **No nested/threaded replies** — flat chronological only.
- **No fan-identity table** — author name is a denormalized snapshot from the JWT.
- **No seed data** — the forum launches empty.
- **No Facebook login** — Google sign-in already in place is sufficient; the fan tier
  works with the existing provider.
- **No route guard** — login-gating uses the established in-component `effect()` redirect.

## Implementation Approach

Vertical feature delivered database → API → client, mirroring how every prior slice
was built. The one deviation from the news pattern: forum writes are multi-row
transactions (create thread + opening post atomically; reply + bump `last_activity_at`
+ `post_count` atomically), so this slice introduces a `@Transactional ForumService`
rather than injecting the repository straight into the controller. Author identity is
captured from the JWT at write time and snapshotted onto every row, so rendering never
needs a name-resolution step (there is no Admin SDK to resolve other users' names).

## Critical Implementation Details

- **Display name source.** The author display name must be read from JWT claims
  (`jwt.getClaimAsString("name")`, fallback `"email"`), NOT from `Authentication.getName()`
  which returns the Firebase UID. Snapshot BOTH `author_uid` (UID, for future ownership
  features) and `author_display_name` onto each row at write time. Server stores the raw
  name/email; the "name → email local-part → 'Kibic'" fallback is applied when rendering
  (frontend), and the server must never emit the full email in a list/detail response —
  store email only if needed for the local-part fallback, and derive the safe label
  before it leaves the API. Simplest safe contract: compute the display label
  server-side at write time (`name`, else local-part of `email`, else `"Kibic"`) and
  store only that in `author_display_name` — the raw email never persists and never
  ships to other fans.
- **Activity bump ordering.** A reply must, in one transaction: insert the post, set the
  parent thread's `last_activity_at = now()`, and increment `post_count`. Thread
  creation sets `last_activity_at = created_at` and `post_count = 1` (the opener counts).
- **ddl-auto=validate parity.** Every entity `@Column(length=…, nullable=…)` must match
  the V7/V8 migration column definitions exactly, or production deploy fails validation.

## Phase 1: Backend domain & persistence

### Overview

Create the two JPA entities, their repositories, and the Flyway migrations. No web layer
yet. Establishes the schema the rest of the slice builds on.

### Changes Required:

#### 1. ForumThread entity

**File**: `backend/src/main/java/com/plomienkostrze/forum/ForumThread.java`

**Intent**: The thread aggregate root — title + author snapshot + activity metadata.
Mirror the `NewsPost` conventions (immutable, static factory, `@PrePersist`, getters
only, protected no-arg ctor for JPA).

**Contract**: `@Entity @Table(name="forum_threads")`. Columns: `id` (IDENTITY),
`title VARCHAR(200) NOT NULL`, `author_uid VARCHAR(128) NOT NULL`,
`author_display_name VARCHAR(100) NOT NULL`, `created_at TIMESTAMPTZ NOT NULL`
(stamped in `@PrePersist`), `last_activity_at TIMESTAMPTZ NOT NULL`,
`post_count INT NOT NULL`. Static factory `openedBy(title, authorUid, displayName)`
initialises `last_activity_at = created_at` and `post_count = 1`. A package-visible
domain method `registerReply(Instant at)` sets `last_activity_at = at` and increments
`post_count` (called by the service inside the reply transaction).

#### 2. ForumPost entity

**File**: `backend/src/main/java/com/plomienkostrze/forum/ForumPost.java`

**Intent**: A single message in a thread — the opener is just the first post. Same
entity conventions.

**Contract**: `@Entity @Table(name="forum_posts")`. Columns: `id` (IDENTITY),
`thread_id BIGINT NOT NULL` (FK → forum_threads), `author_uid VARCHAR(128) NOT NULL`,
`author_display_name VARCHAR(100) NOT NULL`, `body VARCHAR(10000) NOT NULL`,
`created_at TIMESTAMPTZ NOT NULL` (`@PrePersist`). Model the `thread_id` link as a
plain `Long` column (matching the news codebase's avoidance of heavy JPA associations)
or a `@ManyToOne` — follow whichever the news/league entities use; a plain FK column
keeps the read queries explicit. Static factory `in(threadId, authorUid, displayName, body)`.

#### 3. Repositories

**File**: `backend/src/main/java/com/plomienkostrze/forum/ForumThreadRepository.java`,
`backend/src/main/java/com/plomienkostrze/forum/ForumPostRepository.java`

**Intent**: Spring Data repositories with the derived queries the API needs.

**Contract**: `ForumThreadRepository extends JpaRepository<ForumThread, Long>` with
`Page<ForumThread> findAllByOrderByLastActivityAtDesc(Pageable)`.
`ForumPostRepository extends JpaRepository<ForumPost, Long>` with
`Page<ForumPost> findByThreadIdOrderByCreatedAtAsc(Long threadId, Pageable)` and
`List<ForumPost> findByThreadIdOrderByCreatedAtAsc(Long threadId)` (or a single
paginated variant — pick per how the detail endpoint paginates in Phase 2).

#### 4. Flyway migrations

**File**: `backend/src/main/resources/db/migration/V7__create_forum_threads.sql`,
`backend/src/main/resources/db/migration/V8__create_forum_posts.sql`

**Intent**: Create the two tables, PostgreSQL-specific, matching the entity columns
exactly. Back the hot queries with indexes.

**Contract**: V7 creates `forum_threads` (`BIGSERIAL PRIMARY KEY`, the columns above,
`post_count INT NOT NULL DEFAULT 1`) with an index on `last_activity_at DESC`. V8
creates `forum_posts` with a FK `thread_id REFERENCES forum_threads(id)` and an index
on `(thread_id, created_at)`. Forward-only, backward-compatible (new tables only — safe
against the previous app revision).

### Success Criteria:

#### Automated Verification:

- Backend compiles: `cd backend && ./mvnw -q compile`
- Migrations apply and schema validates against entities (context boots with H2
  create-drop in tests): `cd backend && ./mvnw -q test -Dtest=PlomienKostrzeApiApplicationTests`
- Full build/tests still green: `cd backend && ./mvnw -q test`

#### Manual Verification:

- Against a real Postgres, V7 and V8 apply cleanly and `ddl-auto=validate` passes on
  backend startup (no schema mismatch between entities and migrations).

**Implementation Note**: After completing this phase and all automated verification
passes, pause here for manual confirmation before proceeding.

---

## Phase 2: Backend REST API & security

### Overview

Expose the forum over HTTP behind `.authenticated()`, with a transactional service for
the multi-write operations, and prove the authorization matrix (anon → 401, fan → 200,
admin → 200) with tests.

### Changes Required:

#### 1. ForumService

**File**: `backend/src/main/java/com/plomienkostrze/forum/ForumService.java`

**Intent**: Own the two multi-write transactions so thread+opener and reply+bump are
atomic. Compute the safe author display label here (name → email local-part → "Kibic")
so the raw email never persists.

**Contract**: `@Service` with constructor-injected repositories. Methods:
`ForumThread openThread(String title, String body, String authorUid, String rawName, String rawEmail)`
— `@Transactional`, saves the thread then the opening post. `ForumPost reply(Long threadId, String body, String authorUid, String rawName, String rawEmail)`
— `@Transactional`, loads the thread (404 → thrown domain exception mapped by the
controller), saves the post, calls `thread.registerReply(now)`. A private
`displayLabel(rawName, rawEmail)` helper implements the fallback.

#### 2. Forum controller + DTOs

**File**: `backend/src/main/java/com/plomienkostrze/web/ForumController.java`

**Intent**: The REST surface. Mirror `NewsPostController`'s nested-record DTO style and
`ResponseStatusException` error mapping. Read the author identity from
`@AuthenticationPrincipal Jwt jwt`.

**Contract**: `@RestController @RequestMapping("/api/forum")`. Endpoints:
- `GET /threads` → `ThreadListResponse` (paginated summaries: id, title,
  authorDisplayName, createdAt, lastActivityAt, postCount), ordered by last activity.
  Validate `page`/`size` with a `MAX_PAGE_SIZE` guard as `NewsPostController` does.
- `POST /threads` (`@ResponseStatus(CREATED)`) → body `CreateThreadRequest {@NotBlank @Size(max=200) title; @NotBlank @Size(max=10000) body}`,
  returns the created thread's detail.
- `GET /threads/{id}` → `ThreadDetailResponse` (thread header + paginated posts list);
  404 if the thread doesn't exist.
- `POST /threads/{id}/posts` (`@ResponseStatus(CREATED)`) → body
  `CreatePostRequest {@NotBlank @Size(max=10000) body}`, returns the created post;
  404 if the thread doesn't exist.

DTOs are nested `record`s with `from(entity)` mappers. Author fields in responses carry
only `authorDisplayName` (never uid, never email).

#### 3. Security matchers

**File**: `backend/src/main/java/com/plomienkostrze/security/SecurityConfig.java`

**Intent**: Gate the entire forum behind authentication — reads and writes. This adds
the first authenticated-GET endpoint class.

**Contract**: Add matchers before `anyRequest().denyAll()`:
`.requestMatchers("/api/forum/**").authenticated()` (covers GET + POST for the whole
forum subtree). Confirm the `DispatcherType.ERROR` permitAll carve-out still applies so
`ResponseStatusException`s aren't rewritten.

#### 4. Authorization + behavior tests

**File**: `backend/src/test/java/com/plomienkostrze/web/AuthorizationMatrixTest.java`
(extend), a new `backend/src/test/java/com/plomienkostrze/web/ForumApiTest.java`, and the
`@MockitoBean` additions in existing `@WebMvcTest` classes.

**Intent**: Prove the access matrix and the core behaviors (thread+opener created,
reply bumps activity/count, 404 on missing thread).

**Contract**: Add each forum endpoint as a row in `AuthorizationMatrixTest.endpoints()`
with `Access.AUTHENTICATED` (401 anon, 200/201 any signed-in). Add `ForumService` (and
any new controller collaborators) as `@MockitoBean` to `AuthorizationMatrixTest`,
`PublishGateTest`, `CorsOrthogonalityTest` so their contexts still load. `ForumApiTest`
(`@WebMvcTest @Import(SecurityConfig, CorsConfig)`) uses `MockPrincipals.userJwt()` to
assert: creating a thread returns 201 with the opener; replying returns 201 and the
service bumps activity/count (verify via mock); missing thread → 404; blank
title/body → 400.

### Success Criteria:

#### Automated Verification:

- Backend compiles: `cd backend && ./mvnw -q compile`
- Authorization matrix passes (forum rows included): `cd backend && ./mvnw -q test -Dtest=AuthorizationMatrixTest`
- Forum behavior tests pass: `cd backend && ./mvnw -q test -Dtest=ForumApiTest`
- Full backend test suite green: `cd backend && ./mvnw -q test`

#### Manual Verification:

- With a throwaway Postgres + backend sourcing `.env.local`, curls confirm: anonymous
  gets 401 on `GET /api/forum/threads` and both POSTs; a signed-in fan (valid token, UID
  not in `ADMIN_UIDS`) gets 200/201; `POST /threads` then `POST /threads/{id}/posts`
  bubbles the thread to the top of the list and `post_count` increments; missing thread
  → 404; blank title/body → 400. Responses never contain the author's email or UID.

**Implementation Note**: After completing this phase and all automated verification
passes, pause here for manual confirmation before proceeding.

---

## Phase 3: Frontend forum feature

### Overview

The `forum/` feature folder mirroring `news/`: an API service, a thread list, a thread
detail with an inline reply form, a new-thread form, plus the route, nav tab, and login-
gate redirect.

### Changes Required:

#### 1. Forum API service

**File**: `frontend/src/app/forum/forum-api.ts`

**Intent**: The single HttpClient service for `/api/forum`, DTOs declared at top —
mirror `news-api.ts`.

**Contract**: Root `@Injectable`. Interfaces: `ThreadSummary`, `ThreadList`,
`ThreadDetail`, `ForumPost`. Base `url = \`${environment.apiBaseUrl}/api/forum\``.
Methods: `listThreads(page, size)`, `getThread(id, page, size)`,
`createThread({title, body})`, `reply(threadId, {body})`. No auth handling needed — the
interceptor attaches the token.

#### 2. Thread list component

**File**: `frontend/src/app/forum/thread-list.ts`, `frontend/src/app/forum/thread-list.html`

**Intent**: List threads newest-activity-first with the news `loadMore` / "Pokaż
starsze" pattern; empty state; a "Nowy wątek" CTA. Gate on login.

**Contract**: Standalone signal component mirroring `news-list.ts` (items/total/busy/
error signals, `PAGE_SIZE ~10`, `loadMore()`). Empty state renders "brak wątków — załóż
pierwszy". Each row links to `/forum/:id` and shows title, author label, last-activity
time, post count. Login-gate: constructor `effect()` — if `authService.user() === null`,
redirect to trigger login (mirror `admin-panel.ts:46` but gate on `user`, not `isAdmin`).

#### 3. Thread detail + reply form

**File**: `frontend/src/app/forum/thread-detail.ts`, `frontend/src/app/forum/thread-detail.html`

**Intent**: Show the thread's opening post and all replies chronologically, with an
inline reply form (reactive form, mirror `admin-panel.ts`). Gate on login; handle 404.

**Contract**: Reads `:id` from `ActivatedRoute.snapshot.paramMap` (as `news-detail.ts`).
Loads thread + posts (paginated). Reply form: `NonNullableFormBuilder` group
`{body: ['', [required, maxLength(10000)]]}`, submit calls `forumApi.reply(...)`, on
success appends the post and clears the field. `notFound` signal on 404. Author label
via the shared helper. Login-gate `effect()` as above.

#### 4. New-thread form

**File**: `frontend/src/app/forum/new-thread.ts`, `frontend/src/app/forum/new-thread.html`

**Intent**: The create-thread form (title + opening body), mirroring `admin-panel.ts`
but gated on being logged in rather than admin.

**Contract**: Reactive form `{title: ['', [required, maxLength(200)]], body: ['', [required, maxLength(10000)]]}`,
`sending` signal, submit calls `forumApi.createThread(...)`, navigates to the new
thread on success, inline `error` on failure. Login-gate `effect()`.

#### 5. Author-label helper

**File**: `frontend/src/app/forum/author-label.ts` (or a small pure function colocated)

**Intent**: Centralise the "name → email local-part → 'Kibic'" fallback so list, detail,
and reply render authors consistently.

**Contract**: Pure function `authorLabel(displayName?: string): string`. Since the API
already emits only a safe `authorDisplayName`, this mostly guards the empty/missing case
→ "Kibic". (If the server already guarantees a non-empty safe label, this collapses to a
trivial fallback; keep it for defensive rendering.)

#### 6. Routes + nav tab

**File**: `frontend/src/app/app.routes.ts`, `frontend/src/app/app.html`

**Intent**: Wire the three lazy routes and add the Forum tab to the nav.

**Contract**: Routes: `forum` → thread-list, `forum/nowy` → new-thread, `forum/:id` →
thread-detail (all `loadComponent`). Add `<a routerLink="/forum" routerLinkActive="active">Forum</a>`
to `.app-nav` in `app.html`. The tab is always visible; clicking it while logged out
lands on the list component, whose `effect()` triggers login.

### Success Criteria:

#### Automated Verification:

- Frontend builds: `cd frontend && npm run build`
- Lint passes: `cd frontend && npm run lint` (if configured)
- Unit tests pass: `cd frontend && npm test -- --watch=false --browsers=ChromeHeadless`
  (only if forum specs are added; otherwise the existing suite stays green)

#### Manual Verification:

- With the full local stack up: a guest clicking "Forum" is redirected to login and sees
  no threads. After Google login, the fan sees the (initially empty) thread list, creates
  a thread, opens it, replies; the thread bubbles to the top of the list with an
  incremented post count. Author labels show the Google display name (never an email or
  UID). Production build boots.

**Implementation Note**: After completing this phase and all automated verification
passes, pause for manual confirmation. The only clicks left to the human are the ones
requiring their Firebase login session.

---

## Testing Strategy

### Unit / slice tests (backend):

- Authorization matrix: each forum endpoint returns 401 anon, 200/201 for any signed-in
  fan, 200/201 for admin (`AuthorizationMatrixTest`).
- Behavior (`ForumApiTest`, `@WebMvcTest` + `MockPrincipals.userJwt()`): thread creation
  returns the opener; reply bumps activity/count (verified on the mocked service);
  missing thread → 404; blank title/body → 400; responses omit uid/email.

### Integration:

- Local Postgres + backend: end-to-end thread → reply flow, ordering by last activity,
  `post_count` increment, `ddl-auto=validate` parity.

### Manual Testing Steps:

1. As a guest, navigate to `/forum` → redirected to login, no content leaked.
2. Log in as a fan (UID not in `ADMIN_UIDS`), create a thread, verify it appears.
3. Reply to the thread → thread moves to top of list, post count increments.
4. Open a second thread, reply, confirm independent ordering.
5. Confirm author labels never show an email or UID.

## Performance Considerations

`post_count` and `last_activity_at` are denormalized on the thread to avoid an N+1
count/scan when rendering the list; both are maintained in the reply transaction. The
list index on `last_activity_at DESC` and the posts index on `(thread_id, created_at)`
back the only two hot queries. Volume is expected low (small club audience) — pagination
mirrors the news feature and is sufficient.

## Migration Notes

V7/V8 create new tables only — backward-compatible with the previous app revision
(safe under the "Cloud Run revision rolls back instantly, DB migration does not" caveat).
No data backfill; the forum launches empty.

## References

- Roadmap slice: `context/foundation/roadmap.md` (S-07)
- PRD: `context/foundation/prd.md` (FR-002, FR-014–FR-016, §Access Control)
- Auth foundation (reused): `context/archive/2026-07-06-manual-news-publishing/plan.md`
- Domain template: `backend/.../news/NewsPost.java`, `backend/.../web/NewsPostController.java`
- Frontend template: `frontend/src/app/news/`, `frontend/src/app/admin/admin-panel.ts`
- Test harness: `backend/.../web/AuthorizationMatrixTest.java`, `backend/.../web/MockPrincipals.java`
- Manual-verification lessons: `context/foundation/lessons.md`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Backend domain & persistence

#### Automated

- [x] 1.1 Backend compiles (`./mvnw -q compile`) — 3b69fe8
- [x] 1.2 Context boots / schema validates against entities (`PlomienKostrzeApiApplicationTests`) — 3b69fe8
- [x] 1.3 Full backend test suite green (`./mvnw -q test`) — 3b69fe8

#### Manual

- [x] 1.4 V7/V8 apply cleanly against real Postgres and `ddl-auto=validate` passes on startup — 3b69fe8

### Phase 2: Backend REST API & security

#### Automated

- [x] 2.1 Backend compiles (`./mvnw -q compile`) — f0dd833
- [x] 2.2 Authorization matrix passes with forum rows (`AuthorizationMatrixTest`) — f0dd833
- [x] 2.3 Forum behavior tests pass (`ForumApiTest`) — f0dd833
- [x] 2.4 Full backend test suite green (`./mvnw -q test`) — f0dd833

#### Manual

- [x] 2.5 Curls confirm anon→401, fan→200/201, reply bumps activity+count, 404/400 cases, no uid/email in responses — f0dd833

### Phase 3: Frontend forum feature

#### Automated

- [x] 3.1 Frontend builds (`npm run build`)
- [x] 3.2 Lint passes (if configured)
- [x] 3.3 Unit tests pass (if forum specs added; otherwise existing suite green)

#### Manual

- [x] 3.4 Guest redirected to login at /forum, no content leaked
- [x] 3.5 Fan creates thread, replies; thread bubbles to top with incremented count; author labels show name (never email/UID); prod build boots
