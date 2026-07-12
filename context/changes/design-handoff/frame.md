# Frame Brief: design-handoff (S-08) — restyle scope boundary

> Framing step before /10x-plan. This document captures what is *actually*
> at issue, separated from what was initially assumed.

## Reported Observation

Widoki aplikacji są bardzo podstawowe / brzydkie. Istnieje gotowy, wysokiej
wierności design handoff wszystkich ekranów (`design_handoff_plomien_kostrze/`).
Roadmap zapisuje to jako **S-08 / `design-handoff`**, framing: „przestylowuje
istniejące widoki, nie dokłada nowych funkcji".

## Initial Framing (preserved)

- **User's stated cause or approach**: To jest czysty *restyle* — odtworzenie
  wyglądu istniejących ekranów, bez nowych funkcji.
- **User's proposed direction**: Odtworzyć widoki z `design_handoff_plomien_kostrze/`
  w aplikacji Angulara (wg wzorców repo, nie kopiując prototypu 1:1).
- **Pre-dispatch narrowing** (Step 1.5, odpowiedzi usera):
  - *Wierność danych*: **„Tylko to, co renderujemy dziś"** — elementy handoffu
    niosące dane spoza API (autor wpisu, kolumny W/R/P + bramki, data/godzina)
    są poza zakresem.
  - *Model nawigacji*: **„Tylko wygląd ekranów"** — nawigacja zostaje route'owa;
    bez budowy warstwy modali/overlay.
  - *Rdzeń zakresu*: **„Spójny fundament wszędzie"** — shell (nagłówek + stopka +
    tricolor), tokeny klubowe, typografia Anton/Barlow na wszystkich ekranach.

## Dimension Map

Obserwacja mogłaby wymagać pracy w czterech wymiarach:

1. **Fundament wizualny (czysty CSS/HTML)** — tokeny, fonty, shell, tricolor,
   dark theme, karty, layout. Framing usera ląduje tutaj. ← **initial framing**
2. **Net-new prymitywy UI (overlay/modale)** — login/dodaj-post/nowy-temat jako
   modale; dziś nie istnieją.
3. **Warstwa danych/backend** — autor newsa, pełna tabela (W/R/P/bramki/różnica),
   data + godzina meczu; handoff pokazuje pola spoza obecnego API.
4. **Ulepszenia frontowe z obecnych danych** — podświetlenie wiersza Płomienia,
   awatary forum z hasha nicku, featured news (bez linii autora), empty states,
   karty/grid.

## Hypothesis Investigation

Reużyte dowody z `context/changes/design-handoff/research.md` (ten sam commit
`7181368`, dziś; 3 sub-agenty czytające kod z file:line). Nie dublowano dispatchu.

| Hypothesis | Evidence | Verdict |
| --- | --- | --- |
| Dim 1 — fundament wizualny to główna praca | `styles.scss:1` pusty; `angular.json` bez `stylePreprocessorOptions`; zero tokenów/fontów/shared-UI; `app.html:4-19` jednorzędowy nagłówek bez logo/stopki/tricolor; `public/` tylko `favicon.ico` | **STRONG** |
| Dim 2 — handoff wymaga modali (net-new) | grep `dialog\|modal\|overlay\|backdrop` = 0 trafień; brak `@angular/cdk`; login = Firebase popup (`auth-service.ts:32-36`), dodaj-post = route `/admin`, nowy-temat = route `/forum/nowy` | **STRONG** (ale **odcięte** decyzją Q2) |
| Dim 3 — wierność wymaga danych spoza API | news bez pola autora (`news-api.ts:7-24`); `StandingRow{position,team,played,points}` bez W/R/P/bramek (`league-api.ts:7-12`); `FixtureRow` bez daty/godziny (`fixtures-api.ts:7-14`) | **STRONG** (ale **odcięte** decyzją Q1) |
| Dim 4 — część efektu osiągalna frontend-only | `StandingRow.team` → highlight Płomienia; `ThreadSummary.authorDisplayName` → awatary z hasha; pierwszy element listy → featured; wszystkie modele UI-agnostyczne (restyle rusza tylko `.html`+`.scss`) | **STRONG** |

## Narrowing Signals

- User: **„tylko to, co renderujemy dziś"** → Dim 3 (backend/dane) **poza S-08**;
  restyle jedzie na obecnym kontrakcie API.
- User: **„tylko wygląd ekranów"** → Dim 2 (modale/overlay) **poza S-08**;
  route-based flows zostają, stylujemy je w miejscu.
- User: **„spójny fundament wszędzie"** → Dim 1 to rdzeń i priorytet szerokości;
  Dim 4 to dopełnienie osiągalne bez backendu.

## Cross-System Convention

Restyle-only slice na istniejącym API to standardowo praca w warstwie
prezentacji: globalna warstwa tokenów + import fontów + wspólny shell, potem
ekran-po-ekranie na `.html`+`.scss`. Kluczowa zgodność z konwencją repo
(`frontend/CLAUDE.md`): **SCSS, standalone + signals, brak CSS-in-JS**. Tokeny
najtaniej jako CSS custom properties w `styles.scss` (omija potrzebę
`stylePreprocessorOptions.includePaths`). Data layer i logika TS nietknięte —
zgodne z „nie dokłada nowych funkcji".

## Reframed (or Confirmed) Problem Statement

> **The actual problem to plan around is**: podnieść warstwę wizualną całej
> aplikacji do poziomu handoffu **w granicach frontendu i obecnego kontraktu
> API** — wspólny fundament (tokeny klubowe, fonty, dwurzędowy sticky header,
> stopka, pasek tricolor, dark theme) na wszystkich ekranach, plus restyle
> każdego widoku i ulepszenia wyliczalne z obecnych danych — **bez** zmian
> backendu i **bez** nowej warstwy modali.

Framing usera („restyle, bez nowych funkcji") **obronił się** — ale tylko po
jawnym narysowaniu granicy: naiwne „odtwórzmy handoff 1:1" wciągnęłoby pracę
backendową (Dim 3) i net-new overlay (Dim 2), łamiąc założenie „bez nowych
funkcji". Odpowiedzi usera odcinają oba wymiary do fast-follow, dzięki czemu
S-08 zostaje pionowy, frontendowy i szybko wdrażalny. To potwierdzenie framingu
z doprecyzowaną granicą zakresu, nie przeramowanie.

## Confidence

**HIGH** — dowody STRONG z file:line (świeży research, ten sam commit), zgodne z
konwencją repo, a trzy decyzje zakresowe usera są jednoznaczne i spójne.

## What Changes for /10x-plan

Plan ma dotyczyć **wyłącznie frontendu**: (1) fundament — tokeny (CSS custom
properties), fonty, shell (header/footer/tricolor), logo do `public/`; (2)
restyle każdego widoku (aktualności, tabela, terminarz, forum, panel admina) na
obecnych modelach, rusza `.html`+`.scss`, nie TypeScript ani API; (3) ulepszenia
frontowe z Dim 4. **Poza planem** (fast-follow, do backlogu): modale/overlay
(Dim 2) oraz pola danych — autor, pełna tabela, data/godzina (Dim 3). Elementy
handoffu zależne od tych danych renderować w wariancie na obecnych polach (np.
featured bez linii autora, tabela w obecnych kolumnach).

## References

- Source files: `frontend/src/styles.scss:1`, `frontend/angular.json`,
  `frontend/src/app/app.html:4-19`, `frontend/src/app/news/news-api.ts:7-24`,
  `frontend/src/app/league/league-api.ts:7-12`,
  `frontend/src/app/fixtures/fixtures-api.ts:7-14`,
  `frontend/src/app/auth/auth-service.ts:32-36`,
  `frontend/src/app/forum/author-label.ts:6-8`
- Design source: `design_handoff_plomien_kostrze/README.md`
- Related research: `context/changes/design-handoff/research.md`
- Investigation tasks (reused, not re-dispatched): #1 app-shell, #2 public-views,
  #3 auth/forum/admin/modals
