# Design Handoff (S-08) — Plan Brief

> Full plan: `context/changes/design-handoff/plan.md`
> Frame brief: `context/changes/design-handoff/frame.md`
> Research: `context/changes/design-handoff/research.md`

## What & Why

Podnieść warstwę wizualną całej aplikacji do poziomu design handoffu **w granicach
frontendu i obecnego kontraktu API** — wspólny ciemny fundament (tokeny klubowe,
fonty, dwurzędowy header, stopka, tricolor) na wszystkich ekranach plus restyle
każdego widoku. Obecne widoki są celowo podstawowe/brzydkie; handoff daje gotowe
tokeny i specyfikacje.

## Starting Point

Angular 22 SPA (standalone + signals, SCSS) z **zerowym fundamentem wizualnym**:
`styles.scss` pusty, brak tokenów/fontów/shared-UI, nagłówek jednorzędowy bez
logo/stopki/tricolor (brakuje też zakładki „Aktualności"), `public/` tylko favicon.
Warstwa danych jest UI-agnostyczna — restyle rusza tylko `.html`+`.scss`.

## Desired End State

Każdy ekran w ciemnym motywie klubowym: sticky dwurzędowy header (logo + nazwa +
podtytuł + zakładki z aktywnym podświetleniem), stopka z adresem stadionu, paski
tricolor, typografia Anton/Barlow. Aktualności z hero + featured + siatką kart;
tabela z podświetleniem Płomienia; terminarz podzielony na Nadchodzące/Rozegrane;
forum z kartami wątków, pigułkami odpowiedzi i awatarami. Zero zmian API/logiki
(poza dwoma drobnymi helperami prezentacji).

## Key Decisions Made

| Decision | Choice | Why | Source |
| --- | --- | --- | --- |
| Zakres | Frontend-only, obecny kontrakt API | Trzyma slice pionowym i szybko wdrażalnym | Frame |
| Dane spoza API (autor, W/R/P, data/godzina) | Poza zakresem → fast-follow | Handoff je pokazuje, ale API ich nie ma | Frame |
| Modale/overlay | Poza zakresem; login/dodaj-post/nowy-temat zostają route/popup | „Tylko wygląd ekranów" | Frame |
| Dostawa fontów | Self-host w `public/` | Bez zależności od Google, RODO, offline dev | Plan |
| Poziom wierności | Wiernie wg tokenów README | README daje gotowe wartości, efekt zgodny z projektem | Plan |
| Ulepszenia frontowe | Wszystkie 4 (highlight, awatary, featured, siatka+split) | Tanie, osiągalne z obecnych danych | Plan |
| Motyw | Tylko ciemny | Handoff definiuje wyłącznie dark; jeden motyw = prostsze tokeny | Plan |
| Dostawa tokenów | CSS custom properties w `styles.scss` | Globalne w każdym scoped SCSS bez importu; omija brak `includePaths` | Research |

## Scope

**In scope:** tokeny (CSS custom properties) + self-hosted fonty + globalne
prymitywy (tricolor/band/card/pill/btn); shell (header/footer/tricolor + zakładka
Aktualności); restyle wszystkich widoków (aktualności, detal, tabela, terminarz,
forum ×4, admin ×2); 4 ulepszenia frontowe (highlight Płomienia, awatary,
featured news, siatka + split terminarza); logo do `public/`.

**Out of scope:** zmiany backendu/API; pola danych (autor, pełna tabela,
data/godzina meczu); warstwa modali/overlay; tryb jasny; nowe trasy (poza linkiem
Aktualności do istniejącej `/`); nowe zależności.

## Architecture / Approach

Foundation-first: **globalny** `styles.scss` trzyma tokeny (`:root` custom
properties), `@font-face`, base/reset i wielokrotnego użytku klasy strukturalne
(component styles są scoped, więc te muszą być globalne). Każdy komponent `.scss`
robi tylko własny layout na `var(--…)`. Dwa drobne helpery TS: deriver awatara
(kolor z hasha + inicjał) i stała nazwy klubu do highlightu. Fazy 1–2 (fundament +
shell) dają największy widoczny efekt i walidują fundament; fazy 3–5 restylują
widoki grupami, każda niezależnie wdrażalna na tych samych tokenach.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Fundament | Tokeny, fonty, base, logo, prymitywy | Nośny dla całości — błąd w tokenach propaguje wszędzie |
| 2. App shell | Dwurzędowy header + stopka + tricolor + zakładka Aktualności | `routerLinkActive` exact dla `/`; regresja auth-controls |
| 3. Aktualności | Hero + featured + siatka kart + detal | Featured/grid to największa zmiana strukturalna publicznego widoku |
| 4. Tabela+Terminarz | Highlight Płomienia; split Nadchodzące/Rozegrane | Match nazwy klubu (stała); grupowanie po `played` |
| 5. Forum+Admin | Karty+awatary+pigułki; restyle formularzy admina | Reużycie istniejącej logiki formularzy/AI bez zmian |

**Prerequisites:** S-01–S-07 (wszystkie widoki istnieją); handoff w
`design_handoff_plomien_kostrze/`; lokalny stack do weryfikacji manualnej.
**Estimated effort:** ~4–5 sesji przez 5 faz (szeroko, ale płytko — mechaniczny CSS/HTML).

## Open Risks & Assumptions

- **Nazwa klubu w danych** — highlight Płomienia zakłada dopasowanie `row.team` /
  fixtures do stałej nazwy klubu; dokładny string zweryfikować na żywych danych
  scrapa w Fazie 4 (case-insensitive `includes` jako bufor).
- **Wierność vs obecne dane** — elementy zależne od brakujących pól renderowane w
  wariancie (featured bez autora, tabela w 4 kolumnach, terminarz bez godziny);
  ryzyko rozjazdu ze screenshotami handoffu — świadome i zaakceptowane.
- **Regresje wizualne** — szeroki zasięg; łagodzone fazowaniem i weryfikacją
  manualną per faza.
- **Fonty** — pobranie woff2 Anton/Barlow/Barlow Condensed do `public/fonts/`
  (licencje SIL OFL — wolno redystrybuować).

## Success Criteria (Summary)

- Każdy ekran renderuje ciemny motyw handoffu z załadowanymi fontami; build
  produkcyjny przechodzi; brak błędów w konsoli.
- Cztery ulepszenia widoczne (highlight Płomienia, awatary, featured, siatka+split).
- Zero regresji: login/logout, publikacja + AI generate, forum post/reply,
  bramkowanie admina działają jak wcześniej.
