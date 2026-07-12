# Design Handoff (S-08) — Frontend Restyle Implementation Plan

## Overview

Restyle the entire Angular SPA to the high-fidelity design handoff
(`design_handoff_plomien_kostrze/`) — **frontend only, on the current API
contract**. We introduce a shared visual foundation (club-color design tokens,
self-hosted fonts, dark theme, a sticky two-row header + footer + tricolor bar)
and then restyle every view against it. No backend/data changes; no modal/overlay
layer. Data-bearing handoff elements the current API can't feed (news author,
full standings columns, fixture date/time) are rendered in a graceful variant on
today's fields and deferred as fast-follow.

## Current State Analysis

The frontend is a deliberately minimal, unstyled Angular 22 SPA (standalone
components + signals, SCSS, lazy routes). Foundation is greenfield and every view
is presentation-agnostic:

- **No design-token layer** — `frontend/src/styles.scss:1` is an empty comment;
  no SCSS vars, no CSS custom properties, no `stylePreprocessorOptions` in
  `frontend/angular.json`. Club palette, dark theme, tricolor: unimplemented.
- **No web fonts** — `frontend/src/index.html` has no font link; no `font-family`
  declared anywhere. App uses browser-default sans-serif.
- **No shared UI / styling layer** — neutral greys (`#666`/`#eee`/`#b00020`)
  duplicated per component `.scss`. No `shared/`, no mixins, no partials.
- **App shell** (`frontend/src/app/app.html:1-21`, `app.ts`, `app.scss`) — a
  single-row header: text title link, 3 `routerLink` nav items (Tabela/Terminarz/
  Forum — **"Aktualności" missing**), auth controls. No logo, no footer, no
  tricolor, no tab-active neutral highlight.
- **Assets** — no `frontend/src/assets/`; `frontend/public/` holds only
  `favicon.ico`. Handoff's `uploads/logo.jpg` is absent.
- **Views** (each `.ts`/`.html`/`.scss`, all data models UI-agnostic):
  - News: `news-list` is a `<ul>` (title+date+excerpt), no featured/grid;
    `news-detail` is an `<article>`. Model `NewsPost` has **no author**.
  - League: `league-table` renders 4 columns (`Poz/Drużyna/M/Pkt`), no Płomień
    highlight, no legend. `StandingRow{position,team,played,points}`.
  - Fixtures: `fixtures` is one flat table, no upcoming/played split.
    `FixtureRow{round,opponent,home,played,goalsFor,goalsAgainst}` — has `played`
    bool (enables grouping) but **no date/time**.
  - Forum: `thread-list` is a `<ul>`, `thread-detail` article + inline reply
    form, `new-thread` a full page (`/forum/nowy`), `forum-login-gate` inline;
    `author-label.ts` returns text, **no avatars**.
  - Admin: `admin-panel` (`/admin`) is a "new post" form with the AI
    "Generuj z ostatniego meczu" action; `post-edit` (`admin/edit/:id`) a page.

Full inventory with file:line lives in `context/changes/design-handoff/research.md`.
Scope boundary settled in `context/changes/design-handoff/frame.md`.

## Desired End State

Every screen renders in the handoff's dark club theme: a sticky two-row header
(logo lockup + club name + subtitle, then a tab row including **Aktualności**,
with the neutral active highlight), a footer (logo + stadium address), and 4px
tricolor accents. News shows a hero, a featured "NAJNOWSZE" card, and a card grid;
the league table highlights Płomień and carries a legend; fixtures split into
Nadchodzące/Rozegrane; the forum shows thread cards with reply-count pills and
hash-colored initial avatars. Typography is Anton / Barlow / Barlow Condensed,
self-hosted. **No `.ts`/API changes** beyond adding tiny presentation helpers
(avatar hash, club-name match); the app builds and all existing behavior is
preserved.

Verify: `npm run build` succeeds; `ng serve` renders the dark theme with fonts
loaded; each view matches the handoff screenshots within the "current fields"
variant; no console errors; existing flows (login, publish, forum post) still work.

### Key Discoveries:

- Tokens as **CSS custom properties** in `styles.scss` are global to every
  component's scoped SCSS with no import and no `includePaths` — cleanest fit
  given `angular.json` has no `stylePreprocessorOptions` (research §Architecture).
- **Fixtures `played: boolean`** (`fixtures-api.ts:7-14`) lets us group
  Nadchodzące/Rozegrane frontend-only — no date field needed for the split.
- **League `StandingRow.team: string`** enables the Płomień row highlight by
  matching the club name (a configurable constant).
- **Forum `authorDisplayName`** (`forum-api.ts`) + `author-label.ts` give us the
  string to derive hash-colored initial avatars client-side.
- Data models are UI-agnostic — a restyle touches only `.html` + `.scss` (+ two
  tiny presentation helpers), never the API layer.
- Handoff `.dc.html` is a **reference prototype, not production code** (its README
  says so, uses non-Angular template syntax + `localStorage`) — adopt tokens/
  layout/interactions, do not port the runtime.

## What We're NOT Doing

- **No backend/data changes.** News author, full standings columns
  (W/R/P/Bramki/+-), and fixture date/kickoff-time are out — deferred fast-follow.
  Handoff elements needing them render in a current-fields variant (featured card
  with no "Autor" line; table keeps today's columns; fixture rows show no time).
- **No modal/overlay layer.** Login stays the Firebase popup + restyled header
  button; "Dodaj post" stays route `/admin`; "Nowy temat" stays route
  `/forum/nowy`. No login account-picker modal.
- **No light theme.** Dark-only per handoff.
- **No routing changes** beyond adding the "Aktualności" tab link to the existing
  `/` route. No new routes, no route→modal conversions.
- **No new dependencies** (no `@angular/cdk`, no font CDN). Fonts self-hosted.

## Implementation Approach

Foundation-first, because the shared layer is load-bearing — every view consumes
it. Phase 1 lays tokens + fonts + base + logo + global UI primitives (invisible
plumbing). Phase 2 makes it visible via the shell (header/footer/tricolor), which
is also the biggest single visual win and validates the foundation end-to-end.
Phases 3–5 restyle views group-by-group, each independently shippable, all reading
the same tokens/primitives so the app stays consistent as it lands incrementally.

Styling split: **global** `styles.scss` owns tokens (`:root` custom properties),
`@font-face`, base/reset, and reusable structural classes (`.tricolor`, `.band`,
`.card`, `.pill`, button variants) — these are global because Angular component
styles are scoped by default. Each component `.scss` handles only its own layout,
referencing `var(--…)` tokens. Two tiny TS helpers are added: an avatar
color/initial deriver and a club-name match constant.

## Phase 1: Fundament wizualny (tokeny, fonty, base, logo, prymitywy)

### Overview

Establish the shared visual foundation everything else depends on. No view looks
different yet except the global background/body font.

### Changes Required:

#### 1. Self-hosted fonts

**File**: `frontend/public/fonts/` (new) + `frontend/src/styles.scss`

**Intent**: Add Anton, Barlow (400/500/600/700), Barlow Condensed (600/700) as
self-hosted woff2 and declare them via `@font-face`, so the app never depends on
Google's CDN. Fonts fetched once and committed under `public/fonts/`.

**Contract**: `@font-face` blocks with `font-display: swap` mapping the three
families; `public/` is already the configured static-assets input
(`angular.json:49-53`) so files serve at `/fonts/…`. Families referenced only via
tokens (below), never hardcoded in component SCSS.

#### 2. Design tokens (CSS custom properties)

**File**: `frontend/src/styles.scss`

**Intent**: Define the handoff's token set as CSS custom properties on `:root` so
every scoped component SCSS can use them with no import. Dark-only.

**Contract**: Token groups from `design_handoff_plomien_kostrze/README.md`
§Design Tokens — colors (`--c-yellow #F5C518` +hover, `--c-green #1E8A3C` +variants,
`--c-red #D91E2A`, backgrounds `--bg-page #0e0e0e`/`--bg-header #111`/
`--bg-band #121212`/`--bg-card #161616`, borders `#242424`/`#333`, text
`--text #f4f4f4`/muted `#bdbdbd`/`#9a9a9a`/`#7a7a7a`, `--err #ff5a5a`), fonts
(`--font-display: Anton`, `--font-body: Barlow`, `--font-label: 'Barlow Condensed'`),
radii (card 12–14px, button 6–8px, pill 20px), shadows (featured/card/modal),
container widths (`--w-main 1120px`, `--w-forum 920px`). Avatar palette
(`#1E8A3C,#D91E2A,#E0A400,#2f74d0,#9b59b6,#0f8f86`) as a documented list for the
TS helper.

#### 3. Global base / reset

**File**: `frontend/src/styles.scss`

**Intent**: Set the dark page background, base body typography (Barlow), default
link/heading treatment, and a minimal reset so unstyled elements inherit the theme.

**Contract**: `body` gets `background: var(--bg-page)`, `color: var(--text)`,
`font-family: var(--font-body)`, base size/line-height per README (body 14–17px,
line-height 1.5–1.65); headings default to `--font-display` uppercase where the
handoff uses Anton. Box-sizing border-box reset.

#### 4. Reusable structural classes

**File**: `frontend/src/styles.scss`

**Intent**: Provide global, non-scoped utility/structural classes for patterns
repeated across views, so components don't duplicate them.

**Contract**: `.tricolor` (the `linear-gradient(90deg,#1E8A3C 0 33.33%,#F5C518
33.33% 66.66%,#D91E2A 66.66%)` bar, 4px, plus a `.tricolor--rev` for the footer),
`.band` (section title band with eyebrow + H1), `.card` (dark card w/ radius +
shadow), `.pill` (rounded label), button variants (`.btn`, `.btn--primary` yellow
fill, `.btn--outline` yellow outline, `.btn--green`, `.btn--ghost`), `.container`
(max-width + horizontal padding). Class names are new and additive; existing
component classes stay until each view's phase restyles them.

#### 5. Club logo asset

**File**: `frontend/public/logo.jpg` (new)

**Intent**: Copy the handoff crest so the shell/hero/footer can render it.

**Contract**: Copy `design_handoff_plomien_kostrze/uploads/logo.jpg` →
`frontend/public/logo.jpg`; served at `/logo.jpg`. Always rendered circular
(`border-radius:50%`) by consumers.

### Success Criteria:

#### Automated Verification:

- [ ] Production build succeeds: `cd frontend && npm run build`
- [ ] Lint passes: `cd frontend && npm run lint` (if configured; else skip)
- [ ] `frontend/public/logo.jpg` and `frontend/public/fonts/*.woff2` exist
- [ ] `styles.scss` defines `:root` custom properties and `@font-face` blocks (grep)

#### Manual Verification:

- [ ] `ng serve`: page background is dark `#0e0e0e`, body text light and rendered
  in Barlow (fonts load — check Network tab, no 404s on `/fonts/…`)
- [ ] No console errors; existing views still navigable (unstyled but functional)

**Implementation Note**: After automated verification passes, pause for manual
confirmation before Phase 2.

---

## Phase 2: App shell (header, footer, tricolor)

### Overview

Make the foundation visible: a sticky two-row header, footer, and tricolor bars —
the single biggest visual win, validating tokens/fonts/logo end-to-end.

### Changes Required:

#### 1. Two-row sticky header

**File**: `frontend/src/app/app.html`, `frontend/src/app/app.scss`, `frontend/src/app/app.ts`

**Intent**: Replace the single-row header with the handoff's two-row sticky header:
row 1 = circular logo + "PŁOMIEŃ KOSTRZE" (Anton, yellow) + subtitle
"LIGA OKRĘGOWA • KRAKÓW" (Barlow Condensed, green) on the left, auth controls on
the right; row 2 = tab bar. Then a 4px tricolor bar.

**Contract**: Row-2 tabs are `routerLink` pills for **Aktualności `/`**, Tabela
`/tabela`, Terminarz `/terminarz`, Forum `/forum`, using `routerLinkActive`
(Aktualności needs `[routerLinkActiveOptions]="{exact:true}"` so `/` isn't always
active). Active pill = neutral highlight (`rgba(255,255,255,0.12)`, white text)
per README §Shared header — **not** club-color. Auth block restyle (logged-out:
yellow-outline "Zaloguj"; logged-in: "ZALOGOWANY JAKO {nick}" muted +
outline "Wyloguj"; admin: yellow-fill "+ Dodaj post"). `app.ts` keeps existing
`user`/`isAdmin` signals and `signIn`/`signOut` — markup/labels only. Header
`position: sticky; top:0`.

#### 2. Footer

**File**: `frontend/src/app/app.html`, `frontend/src/app/app.scss`

**Intent**: Add the footer that doesn't exist today — reversed tricolor on top,
logo + "PŁOMIEŃ KOSTRZE", and the stadium address, right-aligned.

**Contract**: New `<footer>` after `<main>`: `.tricolor--rev` bar, left logo
(38px) + club name (Anton 16px), right "Stadion" label + "ul. Krzewowa 9c,
30-380 Kraków" (Barlow Condensed, muted) — text from README §Shared footer.

### Success Criteria:

#### Automated Verification:

- [ ] Build succeeds: `cd frontend && npm run build`
- [ ] Header template references `routerLinkActive` and includes an Aktualności
  link to `/` with exact matching (grep `app.html`)

#### Manual Verification:

- [ ] Header is sticky, two rows, logo + club name + subtitle render correctly
- [ ] Tab row shows all four tabs; active tab gets the neutral highlight; clicking
  each navigates and the active state follows (Aktualności active only on `/`)
- [ ] Auth controls: logged-out shows yellow-outline Zaloguj; after Google login
  shows "ZALOGOWANY JAKO {nick}" + Wyloguj; admin additionally sees "+ Dodaj post"
- [ ] Footer renders with reversed tricolor + stadium address; tricolor bars visible
- [ ] No regressions: login/logout and admin gating still work

**Implementation Note**: Pause for manual confirmation before Phase 3.

---

## Phase 3: Aktualności (hero, featured, grid, detail)

### Overview

Restyle the news list (hero band + featured card + card grid) and the news detail
view, on current fields (no author line).

### Changes Required:

#### 1. News list — hero, featured, grid

**File**: `frontend/src/app/news/news-list.html`, `news-list.scss`

**Intent**: Replace the `<ul>` with the handoff layout: a hero band (eyebrow
"SERWIS KIBICA", H1 "AKTUALNOŚCI KLUBU" with "KLUBU" in red, motto, hero logo),
an "OSTATNIE WPISY" header with a count, a full-width featured card for the newest
item, and an auto-fill card grid for the rest. Preserve existing signals,
pagination ("Pokaż starsze"), loading/empty/error states — restyle only.

**Contract**: Featured = first item of `items()` when present, rendered as the
white featured card (red top-border, "NAJNOWSZE" badge, green date, Anton title,
`white-space:pre-wrap` excerpt) **without the "Autor" line** (no author field).
Remaining items → `.card` grid (`repeat(auto-fill,minmax(300px,1fr))`, yellow
top-border, green date, Anton 22px title). Count uses Polish pluralization
("N wpisów") — a small template/TS helper. Empty state = dashed "BRAK AKTUALNOŚCI".
Hero logo hidden on narrow widths. No `news-api.ts` change.

#### 2. News detail

**File**: `frontend/src/app/news/news-detail.html`, `news-detail.scss`

**Intent**: Restyle the article to the handoff's featured-style reading layout
(Anton title, green date, readable body paragraphs) in the dark theme; keep the
admin edit/delete actions but restyle to the new button variants.

**Contract**: Header (Anton title + date), paragraphs keep the existing
blank-line split (`computed paragraphs`, plain text — no innerHTML). Admin actions
reuse `.btn` variants. Back link restyled. No author line. No `.ts` logic change.

### Success Criteria:

#### Automated Verification:

- [ ] Build succeeds: `cd frontend && npm run build`
- [ ] News list template renders a grid container + featured branch (grep)

#### Manual Verification:

- [ ] `/` shows hero band, featured newest card (no "Autor" line), and a card grid
  of remaining posts; count reads correctly with Polish plural
- [ ] "Pokaż starsze" still paginates; empty/loading/error states render in-theme
- [ ] News detail renders themed article; admin (if logged in) sees restyled
  edit/delete; back link works

**Implementation Note**: Pause for manual confirmation before Phase 4.

---

## Phase 4: Tabela + Terminarz

### Overview

Restyle the league table (band, dark table, Płomień highlight, legend) and
fixtures (band, Nadchodzące/Rozegrane split, match rows with Płomień emphasis) —
grouped because they share the band + table/row patterns.

### Changes Required:

#### 1. League table

**File**: `frontend/src/app/league/league-table.html`, `league-table.scss`,
`league-table.ts`

**Intent**: Restyle to the handoff table: a title band ("LIGA OKRĘGOWA • KRAKÓW"
/ "TABELA"), a dark bordered table in a horizontal-scroll container, the **Płomień
row highlighted** (green bg, white text), points in yellow, and a legend beneath.
Keep today's columns (no W/R/P/goals — out of scope).

**Contract**: Add a small `isPlomien(row)` check in `league-table.ts` matching
`row.team` against a club-name constant (case-insensitive `includes`), used to set
a highlight class per `<tr>`. Keep existing `.table-scroll`, `<table>`, and the
current 4 columns — restyle headers (Barlow Condensed) and cells only. Legend
text ("Dane przykładowe" + column-abbrev note) below the table. No `league-api.ts`
change.

#### 2. Fixtures — split + match rows

**File**: `frontend/src/app/fixtures/fixtures.html`, `fixtures.scss`, `fixtures.ts`

**Intent**: Replace the flat table with the handoff's two sections
("Nadchodzące mecze" / "Rozegrane mecze") of match rows, grouping by the existing
`played` bool, with Płomień emphasized and a score badge for played matches.

**Contract**: Add two `computed` signals in `fixtures.ts` — `upcoming` = rows
where `!played`, `played` = rows where `played` (derived from the existing `rows`
signal; no API change). Each row: round + venue on the left, "Płomień — opponent"
or "opponent — Płomień" per `row.home` (Płomień bold yellow), and a score badge
(`goalsFor–goalsAgainst`) + "Koniec" for played rows; upcoming rows show no time
(field absent). Section headers use `--font-display`. Reuse `.card`/row patterns.

### Success Criteria:

#### Automated Verification:

- [ ] Build succeeds: `cd frontend && npm run build`
- [ ] `fixtures.ts` exposes `upcoming`/`played` computed signals; `league-table.ts`
  has the club-match helper (grep)

#### Manual Verification:

- [ ] `/tabela`: themed table, Płomień row highlighted green, points yellow,
  legend present; horizontal scroll works on narrow width
- [ ] `/terminarz`: two sections (Nadchodzące/Rozegrane) populated by `played`;
  Płomień emphasized; played rows show score badge; no time shown (expected)
- [ ] Loading/empty/error states render in-theme for both

**Implementation Note**: Pause for manual confirmation before Phase 5.

---

## Phase 5: Forum + Admin

### Overview

Restyle the forum (thread cards + reply pills + hash-color avatars, thread detail,
new-thread page, login-gate) and the admin forms (new-post panel + edit page),
which stay route-based (no modals).

### Changes Required:

#### 1. Avatar helper

**File**: `frontend/src/app/forum/author-label.ts` (or a sibling helper)

**Intent**: Add a pure function deriving an avatar `{initial, color}` from a
display name — initial = first letter, color = deterministic pick from the
handoff avatar palette via a name hash.

**Contract**: `avatarFor(displayName?: string): {initial: string; color: string}`
using the 6-color palette from tokens; stable per name. No backend. Keeps existing
`authorLabel()` fallback ("Kibic").

#### 2. Forum thread list

**File**: `frontend/src/app/forum/thread-list.html`, `thread-list.scss`

**Intent**: Replace the `<ul>` with the handoff card list: title band
("SPOŁECZNOŚĆ DZIKÓW" / "FORUM KIBICÓW", narrower container), a "TEMATY" header
with a green "+ Nowy temat" link (logged-in), thread cards (Anton title,
reply-count pill, 2-line snippet clamp, author+date meta with avatar), dashed
empty state. Keep signals, auth-gated loading, pagination.

**Contract**: Each thread → `.card` button with hover border; reply count as
`.pill` with Polish plural (existing logic); avatar via `avatarFor(authorDisplayName)`.
Container uses `--w-forum`. No `forum-api.ts` change.

#### 3. Forum thread detail + reply form

**File**: `frontend/src/app/forum/thread-detail.html`, `thread-detail.scss`

**Intent**: Restyle to the handoff thread view: "‹ WRÓĆ DO TEMATÓW" link, thread
card (yellow top-border, Anton title, meta), "ODPOWIEDZI (N)" header, reply cards
with avatars, and the restyled reply textarea + yellow "Odpowiedz" button.

**Contract**: Reuse existing reactive reply form + optimistic append
(`submitReply`); restyle only. Posts render with `avatarFor()` + `pre-wrap` body.
Empty replies → "Brak odpowiedzi — bądź pierwszy!".

#### 4. New-thread page + login-gate

**File**: `frontend/src/app/forum/new-thread.html`, `new-thread.scss`,
`forum-login-gate.html`, `forum-login-gate.scss`

**Intent**: Restyle the new-thread **page** (stays route `/forum/nowy`) as a
themed form (title band, inputs, green "Opublikuj" / ghost "Anuluj"), and the
login-gate as a themed inline section with the yellow "Zaloguj się przez Google".

**Contract**: Markup/SCSS only; keep reactive form + navigate-to-new-thread logic
and the gate's `signIn()` trigger. Not a modal.

#### 5. Admin panel + post-edit

**File**: `frontend/src/app/admin/admin-panel.html`, `admin-panel.scss`,
`post-edit.html`, `post-edit.scss`

**Intent**: Restyle the admin new-post form (route `/admin`) and edit page to the
handoff "Dodaj aktualność" look — full-width yellow "Generuj z ostatniego meczu"
button with its loading/error states, "LUB NAPISZ SAMODZIELNIE" separator, themed
title/content inputs, green "Opublikuj" / ghost "Anuluj". Stays a page, not a modal.

**Contract**: Reuse all existing reactive-form + AI-generate/reject logic
(`generate()`, `NewsApi.generateFromLastMatch`, 424 handling) and admin gating;
restyle markup/SCSS only. `post-edit` mirrors the form styling (no AI action).

### Success Criteria:

#### Automated Verification:

- [ ] Build succeeds: `cd frontend && npm run build`
- [ ] `avatarFor` helper exists and is unit-testable; thread-list template renders
  cards with pill + avatar (grep)
- [ ] Existing unit tests pass: `cd frontend && npm test -- --watch=false` (if any)

#### Manual Verification:

- [ ] `/forum` (logged in): themed thread cards with avatars + reply pills; empty
  state themed; "+ Nowy temat" visible
- [ ] Thread detail: themed thread card + reply cards with avatars; reply form
  posts and appends; back link works
- [ ] `/forum/nowy` and login-gate (logged out) render themed; creating a thread
  still navigates to it
- [ ] `/admin`: themed form; "Generuj z ostatniego meczu" runs (loading/error/
  reject), publish works; `admin/edit/:id` themed and saves
- [ ] Guest visiting forum sees the themed login gate (no data leak)

**Implementation Note**: Final phase — after manual confirmation, the restyle is
complete.

---

## Testing Strategy

### Unit Tests:

- `avatarFor()` — deterministic color/initial per name; empty/undefined → fallback.
- (Existing component specs, if present, should still pass after restyle since
  logic is unchanged.)

### Integration Tests:

- None new — no API/logic changes. Rely on existing suite passing post-restyle.

### Manual Testing Steps:

1. Run the full local stack (per `lessons.md`: Postgres in Docker, backend
   sourcing `.env.local`, `npm start` after `nvm use`).
2. Walk every route in the dark theme: `/`, `/news/:id`, `/tabela`, `/terminarz`,
   `/forum`, `/forum/:id`, `/forum/nowy`, `/admin`, `/admin/edit/:id`.
3. Verify fonts load (Network: no `/fonts/…` 404s), tricolor + logo render,
   header sticky, tabs + active state correct including Aktualności exact-match.
4. Verify the four enhancements: Płomień highlight (table + fixtures), forum
   avatars, featured news card, news grid + fixtures split.
5. Regression: login/logout, publish + AI generate, forum post/reply, admin gating.
6. Narrow-viewport check: header wrap, tab horizontal scroll, table scroll, hero
   logo hidden.

## Migration Notes

No data migration. Purely additive frontend assets (fonts, logo) + markup/SCSS
edits. Rollback = revert the frontend commits; no backend or DB impact.

## References

- Frame brief: `context/changes/design-handoff/frame.md`
- Research: `context/changes/design-handoff/research.md`
- Design source: `design_handoff_plomien_kostrze/README.md` (tokens + per-view
  specs) and `screenshots/`
- Frontend conventions: `frontend/CLAUDE.md` (SCSS, standalone + signals)
- Manual-verification lessons: `context/foundation/lessons.md`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Fundament wizualny

#### Automated

- [x] 1.1 Production build succeeds: `cd frontend && npm run build` — 1683704
- [x] 1.2 Lint passes (if configured) — 1683704
- [x] 1.3 `logo.jpg` and `public/fonts/*.woff2` exist — 1683704
- [x] 1.4 `styles.scss` defines `:root` custom properties and `@font-face` blocks — 1683704

#### Manual

- [x] 1.5 Dark background + light body text in Barlow; fonts load with no `/fonts/…` 404s — 1683704
- [x] 1.6 No console errors; views still navigable — 1683704

### Phase 2: App shell

#### Automated

- [x] 2.1 Build succeeds: `cd frontend && npm run build` — caccf63
- [x] 2.2 Header references `routerLinkActive` + Aktualności link to `/` (exact) — caccf63

#### Manual

- [x] 2.3 Sticky two-row header; logo + club name + subtitle render — caccf63
- [x] 2.4 Four tabs; neutral active highlight; Aktualności active only on `/` — caccf63
- [x] 2.5 Auth controls correct for guest / logged-in / admin — caccf63
- [x] 2.6 Footer with reversed tricolor + stadium address; tricolor bars visible — caccf63
- [x] 2.7 Login/logout and admin gating still work — caccf63

### Phase 3: Aktualności

#### Automated

- [x] 3.1 Build succeeds: `cd frontend && npm run build`
- [x] 3.2 News list template renders grid container + featured branch

#### Manual

- [x] 3.3 Hero + featured newest card (no Autor line) + card grid; count plural correct
- [x] 3.4 Pagination + empty/loading/error states in-theme
- [x] 3.5 News detail themed; admin edit/delete restyled; back link works

### Phase 4: Tabela + Terminarz

#### Automated

- [ ] 4.1 Build succeeds: `cd frontend && npm run build`
- [ ] 4.2 `fixtures.ts` has `upcoming`/`played` computed; `league-table.ts` has club-match helper

#### Manual

- [ ] 4.3 Tabela themed; Płomień row highlighted; points yellow; legend; scroll works
- [ ] 4.4 Terminarz split into Nadchodzące/Rozegrane by `played`; Płomień emphasized; score badges
- [ ] 4.5 Loading/empty/error states in-theme for both

### Phase 5: Forum + Admin

#### Automated

- [ ] 5.1 Build succeeds: `cd frontend && npm run build`
- [ ] 5.2 `avatarFor` helper exists; thread-list renders cards with pill + avatar
- [ ] 5.3 Existing unit tests pass (if any)

#### Manual

- [ ] 5.4 Forum thread cards with avatars + reply pills; empty state themed; "+ Nowy temat" visible
- [ ] 5.5 Thread detail themed; reply posts + appends; back link works
- [ ] 5.6 New-thread page + login-gate themed; create navigates to thread
- [ ] 5.7 Admin form themed; AI generate (loading/error/reject) + publish work; edit page themed + saves
- [ ] 5.8 Guest sees themed login gate on forum
