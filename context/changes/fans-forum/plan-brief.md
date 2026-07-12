# Fans Forum (S-07) — Plan Brief

> Full plan: `context/changes/fans-forum/plan.md`

## What & Why

Give logged-in fans a club-owned place to talk: a login-gated forum where any
authenticated Firebase user reads and creates threads and posts flat replies. It
delivers the parked FR-002 + FR-014/FR-015 and activates the **fan tier** of the access
model for the first time — the biggest access-model change since the project began, but
built entirely on the auth infra already in place.

## Starting Point

Auth is already fan-capable: `signInWithGoogle()` logs in any Google user, and the
backend recognises three tiers (anon / fan / admin) with "admin" being a UID allowlist.
There is no user table — identity is the Firebase UID. News is the domain template
(immutable entity + records DTOs + URL-matcher security); migrations start at V7; the
`AuthorizationMatrixTest` harness already mocks a signed-in fan.

## Desired End State

A fan opens a "Forum" tab, browses threads ordered by most-recent activity, opens one to
read the opening post + chronological replies, starts a new thread, and replies. A guest
hitting `/forum` is redirected to login and sees no forum content — neither API nor SPA
exposes threads to anonymous callers. The forum launches empty with a friendly CTA.

## Key Decisions Made

| Decision              | Choice                                              | Why (1 sentence)                                                        | Source |
| --------------------- | --------------------------------------------------- | ---------------------------------------------------------------------- | ------ |
| Thread model          | Uniform posts (opener = first post)                 | One content table, mirrors the news entity pattern cleanly.            | Plan   |
| Author identity       | Denormalized name snapshot from JWT                 | Matches the no-user-table reality; zero new infra, one read to render. | Plan   |
| Own-content edit/del  | Append-only (create only)                           | Honors the roadmap's narrow "wątki + posty" scope; smallest authz.     | Plan   |
| Reply shape           | Flat, chronological                                 | Exactly the roadmap's scope; simplest model + UI.                      | Plan   |
| Read access           | Full gate — login to even read                      | PRD: "całe forum jest za logowaniem".                                  | PRD    |
| Thread ordering       | By last activity (bumped on reply)                  | Standard forum UX; active threads stay on top.                         | Plan   |
| Moderation            | Out — kept parked                                   | Roadmap explicitly parks FR-016 as fast-follow.                        | Roadmap|
| Pagination            | Mirror news "Pokaż starsze" loadMore                | Consistent with existing UI + Pageable guardrails.                     | Plan   |
| Seed data             | Empty state, no seed                                | Only real fan content; no fake posts (and no delete to remove them).   | Plan   |
| Author label          | name → email local-part → "Kibic"                   | Robust, privacy-preserving; email never exposed to other fans.         | Plan   |

## Scope

**In scope:** two tables (`forum_threads`, `forum_posts`); authenticated CRUD-subset
(create thread, create reply, list, detail); login-gated reads + writes; Forum tab, list,
detail+reply form, new-thread form; author-label helper.

**Out of scope:** edit/delete (any actor), moderation, reactions, news comments, nested
replies, fan-identity table, seed data, Facebook login, route guards.

## Architecture / Approach

Vertical slice, database → API → client, mirroring every prior slice. New backend package
`com.plomienkostrze.forum` (entities, repositories, a `@Transactional ForumService` for
the multi-write thread+opener and reply+bump transactions) plus a `ForumController` in
`web`. `SecurityConfig` gets one matcher — `/api/forum/**` → `.authenticated()` — the
first authenticated-GET endpoint class. Frontend adds a `forum/` folder mirroring `news/`:
`forum-api.ts`, thread-list, thread-detail (inline reply), new-thread, wired with lazy
routes + a nav tab and the in-component `effect()` login-gate.

## Phases at a Glance

| Phase                          | What it delivers                                   | Key risk                                             |
| ------------------------------ | -------------------------------------------------- | --------------------------------------------------- |
| 1. Backend domain & persistence| Entities, repositories, V7/V8 migrations           | Entity/migration column drift breaks `validate`.    |
| 2. Backend REST API & security | Transactional service, controller, matchers, tests | New authenticated-GET class; `@WebMvcTest` mocks.   |
| 3. Frontend forum feature      | API service + views + forms + route/nav + gate     | Login-gate UX (effect redirect) for reads, not writes. |

**Prerequisites:** S-02 (auth foundation) — done. Local stack for manual verification.
**Estimated effort:** ~3 sessions, one per phase.

## Open Risks & Assumptions

- No moderation at launch: abusive/spam content can't be removed until the fast-follow —
  accepted for a small, login-gated, trusted club audience.
- Display-name snapshots go stale if a user renames — accepted for a club forum.
- Login-gating reads (not just writes) departs from every existing public GET; the
  interceptor already sends the token, so the cost is a new endpoint class, not new infra.

## Success Criteria (Summary)

- A logged-in fan creates a thread and a reply; the thread bubbles to the top of the list
  with an incremented post count.
- A guest is redirected to login at `/forum` and no forum content leaks to anonymous callers.
- Author labels always show a human name and never expose an email or UID.
