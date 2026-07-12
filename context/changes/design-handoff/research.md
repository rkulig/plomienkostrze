---
date: 2026-07-12T16:34:18+02:00
researcher: Radek Kulig
git_commit: 7181368532ed9d922b978eb5fad2d5511eaf4fff
branch: master
repository: rkulig/plomienkostrze
topic: "Grounding the design-handoff restyle: current frontend inventory + per-view gap analysis vs. the design handoff"
tags: [research, codebase, frontend, angular, styling, design-handoff, s-08]
status: complete
last_updated: 2026-07-12
last_updated_by: Radek Kulig
---

# Research: design-handoff — current frontend inventory + gap analysis

**Date**: 2026-07-12T16:34:18+02:00
**Researcher**: Radek Kulig
**Git Commit**: 7181368532ed9d922b978eb5fad2d5511eaf4fff
**Branch**: master
**Repository**: rkulig/plomienkostrze

## Research Question

Ground the S-08 `design-handoff` restyle (odtworzenie widoków wg
`design_handoff_plomien_kostrze/`) with an accurate inventory of the current
Angular frontend and a per-view gap analysis: what exists today vs. what the
handoff requires, so `/10x-plan` can scope the work and spot where "just a
restyle" actually needs data/backend changes or net-new UI primitives.

## Summary

The current frontend is a **deliberately minimal, unstyled** Angular 22 SPA
(standalone components + signals, SCSS, lazy routes). Every view works and its
**data layer is presentation-agnostic** — a pure restyle touches only `.html` +
`.scss` for most views. But the handoff is far more than a repaint, and the
research surfaced **four places where the handoff wants data the app doesn't have
today** and **two net-new UI systems that don't exist at all**:

**Net-new foundations (nothing exists today):**
- **No design-token layer** — `styles.scss` is empty, no SCSS vars, no CSS custom
  properties, no `stylePreprocessorOptions.includePaths`. Club palette, dark
  theme, tricolor bar: all unimplemented.
- **No web fonts** — no Anton / Barlow / Barlow Condensed; app uses browser
  default sans-serif.
- **No shared UI / styling layer** — neutral greys (`#666`/`#eee`/`#b00020`) are
  duplicated per component; no `shared/`, no mixins, no partials.
- **No logo asset** — `frontend/public/` has only `favicon.ico`; there is no
  `assets/` dir. The handoff's `uploads/logo.jpg` must be added.
- **No modal/overlay pattern anywhere** — no `@angular/cdk`, no `<dialog>`, no
  backdrop. The handoff's 3 modals (login / add-post / new-thread) are net-new;
  today those flows are routes or a browser popup.
- **No app footer, no two-row header with tab bar, no "Aktualności" nav item.**

**Data gaps — handoff shows fields the API/model doesn't provide (see Open Questions):**
1. **News author** — handoff shows "Autor: {nick}"; the news model has **no author field**.
2. **League table** — handoff wants 9 columns (`# / Klub / M / W / R / P / Bramki / +/- / Pkt`); model has only `played` + `points`.
3. **Fixtures** — handoff wants match **date + kickoff time** and explicit host/guest; model has `round`, `opponent`, `home` (bool), `played`, goals — no date/time.
4. **Forum avatars** — handoff wants hash-colored initial avatars; today author is plain text (but this one is derivable client-side, no backend needed).

The upside: the restyle can proceed **view-by-view** on a shared token/shell
foundation without touching TypeScript logic or API interfaces for most screens.
The scope decisions are (a) how far to chase the data gaps vs. render the subset
we have, and (b) modal-vs-route for the three create/login flows.

## Detailed Findings

### App shell & navigation (`frontend/src/app/`)

- Root `App` component (`app.ts`) imports `RouterOutlet, RouterLink,
  RouterLinkActive`; holds `title = signal('Płomień Kostrze')` (`app.ts:18`) plus
  `user`/`isAdmin` signals for auth-gated nav.
- `app.html` renders a **single-row** `<header class="app-header">`: text
  `.app-title` link to `/`, `.app-nav` with **three** `routerLink` anchors
  (Tabela `/tabela`, Terminarz `/terminarz`, Forum `/forum`; `app.html:4-8`), and
  `.app-auth` (Zaloguj/Wyloguj + admin-only "Dodaj post" → `/admin`,
  `app.html:10-19`). Then `<main>` with `<router-outlet>`.
- **Gaps vs. handoff shell:** no logo/club-name lockup, no `LIGA OKRĘGOWA •
  KRAKÓW` subtitle, no **second tab row**, **"Aktualności" is missing** from nav
  (it's the `/` route but unlinked), no **footer** at all, no **4px tricolor
  bar**. Handoff wants a sticky two-row header + footer with stadium address.

### Routing (`frontend/src/app/app.routes.ts`)

All routes lazy (`loadComponent`):
- `''` → `NewsList` (default) · `tabela` → `LeagueTable` · `terminarz` →
  `Fixtures` · `news/:id` → `NewsDetail` · `forum` → `ThreadList` · `forum/nowy`
  → `NewThread` · `forum/:id` → `ThreadDetail` · `admin` → `AdminPanel` ·
  `admin/edit/:id` → `PostEdit` · `**` → redirect to `''`.
- Note: `forum/nowy`, `admin`, `admin/edit/:id` are the three "form" flows the
  handoff wants as **modals** — converting them fully to modals trades away these
  deep links (see Open Questions).

### Global styling & tokens

- `frontend/src/styles.scss` is **effectively empty** (one comment). No reset, no
  base bg/text color, no font-family.
- `angular.json`: `"style": "scss"`, `"styles": ["src/styles.scss"]`, **no
  `stylePreprocessorOptions`** — no include path for shared partials.
- **Zero design tokens**: no SCSS `$vars`, no CSS custom props, no `@use`/`@import`
  anywhere. Club colors entirely absent.
- **No web fonts**: `index.html` has no Google Fonts link; no `font-family`
  declared anywhere.
- **No shared UI**: no `shared/`/`ui/`/`common/` folder; neutral palette
  (`#666`, `#eee`, `#ccc`, `#b00020`) duplicated across every component `.scss`.
- **Assets**: no `frontend/src/assets/`; `frontend/public/` holds only
  `favicon.ico` — **no logo**.

### News (`frontend/src/app/news/`)

- Data (`news-api.ts`): `NewsPostSummary {id, title, publishedAt, excerpt}`
  (`:7-12`), `NewsPost {id, title, publishedAt, content}` (`:14-19`),
  `NewsPostList {items, total}`. **No author field anywhere.**
- `news-list`: signals `items/total/busy/error`, paged "Pokaż starsze"
  (`PAGE_SIZE=10`). Template is a plain `<ul>/<li>` (title link + inline date +
  excerpt) — **not a card grid**, no featured/latest highlight, empty state
  "Brak aktualności". `.scss` is a `max-width:40rem` centered column, neutral.
- `news-detail`: `<article>` with header (title + date) + paragraphs split on
  blank lines (plain text, no innerHTML); admin edit/delete actions inline.
- **Gaps vs. handoff:** hero band, `highlightLatest` featured white card
  (red top-border, "NAJNOWSZE" badge), auto-fill card **grid**
  (`minmax(300px,1fr)`), yellow card top-borders, and **"Autor: {nick}"** — the
  last needs a **backend/model change** (no author today).

### League table (`frontend/src/app/league/`)

- Data (`league-api.ts`): `StandingRow {position, team, played, points}`
  (`:7-12`), `LeagueTable {rows}`.
- `league-table.html`: real `<table>` in `.table-scroll`, **4 columns** — `Poz /
  Drużyna / M / Pkt`. **No Płomień-row highlight**, no legend/caption.
- **Gaps vs. handoff:** handoff table has **9 columns** (`# / Klub / M / W / R / P
  / Bramki / +/- / Pkt`), **highlighted Płomień row** (green bg), yellow points,
  legend + "Dane przykładowe". `W/R/P`, goals, and goal-diff are **not in the
  model** → data gap (see Open Questions). Highlight + legend + styling are pure
  frontend.

### Fixtures (`frontend/src/app/fixtures/`)

- Data (`fixtures-api.ts`): `FixtureRow {round, opponent, home, played, goalsFor,
  goalsAgainst}` (`:7-14`), `Fixtures {rows}`.
- `fixtures.html`: single **flat** `<table>` (`Data=round / Przeciwnik / Miejsce
  (dom|wyjazd) / Wynik`), no upcoming-vs-played grouping — played/unplayed
  distinguished only per-row in the score cell.
- **Gaps vs. handoff:** handoff splits **"Nadchodzące" vs "Rozegrane"** sections,
  renders **host — VS/score — guest** rows with **kickoff time (godzina)** for
  upcoming and a score badge + "Koniec" for played; Płomień name bolded yellow.
  **Date and kickoff time are not in the model**; `home` is a bool (no explicit
  host/guest names beyond "opponent") → data gap.

### Auth / session (`frontend/src/app/auth/`)

- `auth-service.ts`: Firebase `signInWithPopup(GoogleAuthProvider)` (`:32-36`),
  `signOut` (`:38-40`), `getIdToken` (`:42-45`). Session is a tri-state signal
  `user = signal<User|null|undefined>` driven by `onAuthStateChanged`
  (`:23-26`) — `undefined`=initializing, `null`=logged out, `User`=logged in.
- Admin role is **backend-decided**: `me-api.ts` `GET /api/me → {admin}`;
  `admin-status.ts` exposes `isAdmin = signal<boolean|null>` via an `effect` that
  refetches on every `user()` change.
- `auth-interceptor.ts`: attaches `Authorization: Bearer <idToken>` only to
  `apiBaseUrl` requests.
- Login/logout live in the **shell header** (`app.html:10-19`, `app.ts:22-28`).
- **Gaps vs. handoff:** handoff wants a **Login modal** listing demo Google
  accounts + an email field + admin-email note. Real app uses the **Firebase
  browser popup** — so the handoff's account-picker modal is a *prototype
  affordance*; with real Firebase the modal would be, at most, a styled launcher
  for the popup (a decision for the plan). Header restyle (logo, "ZALOGOWANY JAKO
  {nick}", yellow outline button) is pure styling.

### Forum (`frontend/src/app/forum/`)

- Data (`forum-api.ts`): `ThreadSummary {…postCount, lastActivityAt,
  authorDisplayName}` (`:7-14`), `ForumPost` (`:16-21`), `ThreadDetail {…posts[]}`
  (`:23-32`), `ThreadList {items, total}`.
- `thread-list`: plain `<ul>` (title link + `.meta` = author + date + postCount
  with PL pluralization), "Nowy wątek" → route `/forum/nowy`, load-more.
- `thread-detail`: `<article>` (title + meta) → `<ul.posts>` (opening post +
  replies, `pre-wrap`) → inline reactive reply form → back link.
- `new-thread`: **separate route/page** `/forum/nowy` (not a modal), reactive
  form (title + body), navigates to new thread on success.
- `forum-login-gate`: rendered **inline** (replaces page content) when
  `user()===null` across all three forum components; no forum HTTP call until
  logged in. **Not an overlay.**
- `author-label.ts`: `authorLabel()` returns trimmed `authorDisplayName` or
  `'Kibic'`. **No avatars anywhere.**
- **Gaps vs. handoff:** handoff wants forum **cards** (reply-count pill, snippet
  clamp, hover), thread-detail card with yellow top-border, **hash-colored
  initial avatars** (client-derivable — no backend), and **"+ Nowy temat" as a
  modal** instead of the `/forum/nowy` route.

### Admin (`frontend/src/app/admin/`)

- `admin-panel` (route `/admin`): despite the name, a **"Nowy wpis" creation
  form**, not a dashboard. Reactive form (title + content), admin-gated via
  redirect effect. **Has the AI action**: "Generuj z ostatniego meczu" →
  `NewsApi.generateFromLastMatch()` (`POST /api/news/generate`,
  `admin-panel.ts:56-78`), patches the form with the proposal; "Odrzuć" clears
  it; HTTP 424 → specific message. Proposal is ephemeral (lost on refresh).
- `post-edit` (route `admin/edit/:id`): **separate page**, loads + `NewsApi.update`.
- Styling: `max-width:40rem` flex form, `#ccc` inputs, `#b00020` errors,
  utilitarian buttons.
- **Gaps vs. handoff:** handoff wants "Dodaj aktualność" as a **modal**
  (`max-width:560px`) with the yellow full-width "Generuj post z ostatniego
  meczu" button, "LUB NAPISZ SAMODZIELNIE" separator, and green "Opublikuj". The
  existing reactive-form logic + AI wiring is reusable as modal **body** content.

### Modals/dialogs — CRITICAL

**No modal/overlay pattern exists.** Repo-wide grep for
`dialog|modal|overlay|backdrop|cdk-overlay` = zero hits; `package.json` has no
`@angular/cdk`/`@angular/material`. The three handoff modals map to today's:
1. **Login** → header button → Firebase **browser popup** (+ inline login-gate).
2. **Add-news-post** → route `/admin`.
3. **New-forum-thread** → route `/forum/nowy`.

Converting to centered overlay cards is **net-new**: backdrop, overlay shell,
open/close state, focus trap, and (if kept) deep-link behavior all need building —
either adopt `@angular/cdk` Dialog/Overlay or hand-roll a `<dialog>`/backdrop
component. Existing reactive forms reuse as modal bodies.

## Code References

- `frontend/src/app/app.ts:18` / `app.html:4-19` — single-row shell, 3 nav links, auth controls (no logo/footer/tabs/Aktualności).
- `frontend/src/app/app.routes.ts:5-43` — all lazy routes incl. the 3 form routes (`/admin`, `/forum/nowy`, `admin/edit/:id`).
- `frontend/src/styles.scss:1` — empty global styles.
- `frontend/angular.json` — `style:scss`, `styles:[src/styles.scss]`, no `stylePreprocessorOptions`.
- `frontend/public/` — only `favicon.ico`; no logo, no `assets/`.
- `frontend/src/app/news/news-api.ts:7-24` — news models, **no author field**.
- `frontend/src/app/news/news-list.html` / `.scss` — `<ul>` list, no card grid/featured.
- `frontend/src/app/league/league-api.ts:7-12` — `StandingRow` (only played+points).
- `frontend/src/app/league/league-table.html` — 4-col table, no highlight/legend.
- `frontend/src/app/fixtures/fixtures-api.ts:7-14` — `FixtureRow`, no date/time.
- `frontend/src/app/fixtures/fixtures.html` — flat table, no upcoming/played split.
- `frontend/src/app/auth/auth-service.ts:23-45` — Firebase popup sign-in, `user` signal.
- `frontend/src/app/auth/admin-status.ts:17-30` — `isAdmin` via `GET /api/me`.
- `frontend/src/app/forum/thread-list.html:20-32` — `<ul>` threads, no cards.
- `frontend/src/app/forum/new-thread.ts` — new thread is a **route**, not modal.
- `frontend/src/app/forum/author-label.ts:6-8` — text author, no avatars.
- `frontend/src/app/admin/admin-panel.ts:56-78` — AI "generate from last match" wiring (reusable).
- `design_handoff_plomien_kostrze/README.md` — tokens, per-view specs, 3 modals, screenshots.

## Architecture Insights

- **Clean presentation/data separation.** Every view's `*-api.ts` interfaces and
  component signals are UI-agnostic; a restyle that touches only `.html` + `.scss`
  will not break the data flow — *except* where the handoff introduces fields the
  model lacks (news author, league W/R/P + goals, fixtures date/time).
- **Consistent minimal idiom to preserve.** All views use `max-width` centered
  columns and a shared informal class vocabulary (`.note` loading/empty,
  `.error`, `.table-scroll`). Keeping these class names reduces churn.
- **Tokens-first is the natural first step.** With no token layer today, the
  cheapest high-leverage move is a global tokens layer (CSS custom properties in
  `styles.scss` — avoids needing `stylePreprocessorOptions.includePaths`) + font
  import + the shell (header/footer/tricolor), then restyle views one at a time
  against those tokens. This makes the slice **incrementally shippable**.
- **The handoff is a *reference prototype*, not production code** (its README says
  so explicitly, and `.dc.html` uses non-Angular template syntax + `localStorage`
  seeds). Adopt tokens/layout/interactions; do not port its runtime.
- **Modal-vs-route is the one real architecture decision.** The app is entirely
  route/inline today; the handoff is modal-centric. A hybrid (modals that are also
  routable, or keep routes and skip modals) is viable and avoids losing deep links.

## Historical Context (from prior changes)

- The slice is recorded as **S-08 / `design-handoff`** in
  `context/foundation/roadmap.md` (At-a-glance + Slices), framed as "przestylowuje
  istniejące widoki, nie dokłada nowych funkcji", prerequisites S-01–S-07,
  status `planned`. This research qualifies that framing: several handoff
  affordances *do* imply new data/functionality, not just styling.
- Views were built across the archived slices: `public-news-reading` (S-01),
  `manual-news-publishing` (S-02), `gated-news-generation` (S-03,
  `POST /api/news/generate`), `news-post-management` (S-04), `league-table`
  (S-05), `fixtures-schedule` (S-06), and `fans-forum` (S-07, still `planned` in
  roadmap but present in code). Their `context/archive/**/plan.md` document the
  data shapes the restyle must respect.
- `context/foundation/lessons.md` — local manual-verification lessons: for this
  slice's verify phase, **stand up the full local stack yourself** (Postgres in
  Docker, backend sourcing `.env.local`, `npm start` after `nvm use`) and leave
  the user only the Firebase admin login clicks.

## Related Research

None prior for this change (`context/changes/design-handoff/` was created today
via `/10x-new`). No other `research.md` under `context/changes/**` covers frontend
styling.

## Open Questions

These are **scope decisions for `/10x-frame` or `/10x-plan`** — each is a place
where "just a restyle" meets missing data or net-new UI:

1. **News author** — handoff shows "Autor: {nick}". Model has no author. Options:
   (a) add author to backend (news entity + API + generate flow), (b) drop the
   author line from the restyle. **Owner: team.**
2. **League table columns** — handoff wants `W / R / P / Bramki / +/- / Pkt`;
   model has only `played + points`. Options: (a) extend the 90minut scrape +
   `StandingRow` to full standings, (b) render only the columns we have and style
   those. **Owner: team.**
3. **Fixtures date/time + host/guest** — handoff shows match date, kickoff time,
   and explicit host—guest; model has `round/opponent/home/played/goals`. Options:
   (a) extend the scrape/model, (b) restyle within current fields (no time). 
   **Owner: team.**
4. **Modal vs route** — build a real overlay layer (`@angular/cdk` Dialog vs
   hand-rolled `<dialog>`) for login/add-post/new-thread, or keep the current
   route/inline flows and apply handoff styling to them in place? Full modal
   conversion loses `/admin`, `/forum/nowy`, `admin/edit/:id` deep links unless
   modals are made routable. **Owner: team.**
5. **Login modal** — the handoff's Google-account-picker modal is a prototype
   affordance; real auth uses the Firebase popup. Style a launcher only, or skip
   the login modal entirely and just restyle the header button + login-gate?
   **Owner: team.**
6. **Fonts delivery** — Google Fonts `<link>` in `index.html` vs self-hosted in
   `public/`. (Firebase Hosting serves either; external link is simplest.)
7. **Scope discipline** — S-08 is capped at "restyle existing views." Recommend
   deferring the data-gap items (1–3) to fast-follow backend slices and shipping
   the restyle against **current fields + client-only enhancements** (Płomień
   highlight, forum avatars, featured card, tricolor, tokens, shell), so the slice
   stays vertical and shippable. Confirm with team.
