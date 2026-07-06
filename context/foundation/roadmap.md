---
project: "Płomień Kostrze"
version: 1
status: draft
created: 2026-07-04
updated: 2026-07-06
prd_version: 1
main_goal: market-feedback
top_blocker: none  # was: decisions — dostawca tożsamości i dostawca LLM rozstrzygnięci 2026-07-04
---

# Roadmap: Płomień Kostrze

> Derived from `context/foundation/prd.md` (v1) + auto-researched codebase baseline.
> Edit-in-place; archive when superseded.
> Slices below are listed in dependency order. The "At a glance" table is the index.

## Vision recap

Kibice Płomienia Kostrze nie mają jednego, klubowego miejsca z aktualnościami —
informacje giną w rozproszonych mediach społecznościowych i nie tworzą archiwum.
Produkt skupia aktualności klubu w jednym miejscu, a jego wyróżnikiem jest
automatyczne generowanie wpisów z surowych danych meczowych, publikowanych
wyłącznie po akceptacji administratora. Kolejność prac maksymalizuje sygnał od
użytkowników (`main_goal: market-feedback`), z głęboką inwestycją w backend
(pipeline generowania i logika publikacji) oraz we frontend (widoki publiczne i
panel administratora); warstwa danych i infrastruktura pozostają celowo proste.

## North star

**S-03: Administrator publikuje wpis wygenerowany z danych meczowych, a gość go czyta** —
to najmniejszy przepływ, który weryfikuje rdzeń wartości produktu: czy generowane
propozycje są na tyle dobre, że administrator akceptuje co najmniej 75% z nich
(pierwsze Kryterium sukcesu PRD).

> „Gwiazda przewodnia" (north star) oznacza tu: najmniejszy przelotowy,
> widoczny dla użytkownika slice, którego dostarczenie dowodzi głównej hipotezy
> produktu — umieszczony tak wcześnie, jak pozwalają jego zależności, bo reszta
> planu ma sens tylko wtedy, gdy on zadziała.

## At a glance

| ID   | Change ID              | Outcome (user can …)                                                  | Prerequisites | PRD refs                     | Status   |
| ---- | ---------------------- | --------------------------------------------------------------------- | ------------- | ---------------------------- | -------- |
| S-01 | public-news-reading    | Gość czyta listę i treść opublikowanych aktualności bez logowania      | —             | US-02, FR-009                | ready    |
| S-02 | manual-news-publishing | Administrator loguje się i publikuje wpis utworzony ręcznie            | S-01          | FR-001, FR-006               | done |
| S-03 | gated-news-generation  | Administrator generuje propozycję wpisu z danych meczowych i publikuje ją po akceptacji | S-02          | US-01, FR-003, FR-004, FR-005 | proposed |
| S-04 | news-post-management   | Administrator edytuje i usuwa opublikowane wpisy                       | S-02          | FR-007, FR-008               | proposed |

## Baseline

What's already in place in the codebase as of `2026-07-04` (auto-researched + user-confirmed).
Foundations below assume these are present and do NOT re-scaffold them.

- **Frontend:** partial — Angular SPA (`frontend/src/app/`): router i minimalny app shell + komponent `test-flow`; zero funkcji domenowych.
- **Backend / API:** partial — Spring Boot (`backend/src/main/java/com/plomienkostrze/`): kontrolery `ping`/`test-message`, `CorsConfig`; zero endpointów domenowych.
- **Data:** partial — Spring Data JPA + PostgreSQL + Flyway wpięte (`backend/pom.xml`); jedyna migracja `V1__create_test_messages.sql` to tabela testowa.
- **Auth:** absent — brak spring-security/OAuth w zależnościach; dostawca tożsamości niewybrany (PRD świadomie odłożył).
- **Deploy / infra:** present — `backend/Dockerfile`, `frontend/firebase.json`, path-filtrowane GitHub Actions z auto-deployem po merge do `master`; deploy potwierdzony end-to-end.
- **Observability:** partial — `spring-boot-starter-actuator` w zależnościach backendu; brak error-trackingu i metryk poza tym.

W kodzie nie ma żadnej integracji LLM (zależności obu aplikacji: zero trafień).

## Foundations

Brak fundamentów w tej wersji roadmapy. Warstwa deploy/CI jest już obecna
(baseline: present), a pozostałe elementy przekrojowe wchodzą progresywnie w
pierwszym slice, który ich potrzebuje: schemat danych wpisów w S-01, logowanie
administratora w S-02, integracja LLM w S-03. Wydzielanie ich z góry byłoby
pracą horyzontalną bez odbiorcy.

## Slices

### S-01: Gość czyta opublikowane aktualności

- **Outcome:** Gość (bez logowania) widzi listę opublikowanych wpisów aktualności i otwiera dowolny do przeczytania.
- **Change ID:** public-news-reading
- **PRD refs:** US-02, FR-009, NFR „pierwsza treść wpisu widoczna < 2 s"
- **Prerequisites:** —
- **Parallel with:** —
- **Blockers:** —
- **Unknowns:** —
- **Risk:** Pierwszy slice przecina wszystkie warstwy (model wpisu → API → widoki SPA → deploy) na najprostszym możliwym przypadku; treść startowa jest zasiana danymi, bo narzędzia administratora przychodzą dopiero w S-02 — to świadomy koszt cienkiego pionowego startu.
- **Status:** ready

### S-02: Administrator loguje się i publikuje wpis ręcznie

- **Outcome:** Administrator loguje się kontem zewnętrznego dostawcy tożsamości, tworzy wpis ręcznie i publikuje go — wpis natychmiast widać w publicznych aktualnościach z S-01.
- **Change ID:** manual-news-publishing
- **PRD refs:** FR-001, FR-006, §Access Control, NFR „logowanie wyłącznie przez zewnętrznego dostawcę; brak haseł w aplikacji"
- **Prerequisites:** S-01
- **Parallel with:** —
- **Blockers:** —
- **Unknowns:** — (rozstrzygnięte 2026-07-04: Firebase Authentication — zob. `tech-stack.md` i sekcję Identity w `tech-stack-backend.md`)
- **Risk:** Ścieżka ręczna celowo wyprzedza generowanie — daje działający kanał publikacji i chronione narzędzia administratora, na których S-03 tylko dokłada generację; minimalny zakres auth (jeden administrator, bez logowania kibiców) trzyma slice w ryzach.
- **Status:** done

### S-03: Administrator generuje wpis z danych meczowych i publikuje po akceptacji

- **Outcome:** Administrator wprowadza surowe dane meczowe ze wskazaniem tonu/stylu, otrzymuje wygenerowaną propozycję wpisu, edytuje ją w razie potrzeby i publikuje po jawnej akceptacji (albo odrzuca) — nic nie trafia do publicznych aktualności samoczynnie.
- **Change ID:** gated-news-generation
- **PRD refs:** US-01, FR-003, FR-004, FR-005, NFR „widoczny postęp każdej operacji > ~2 s", §Business Logic
- **Prerequisites:** S-02
- **Parallel with:** S-04
- **Blockers:** —
- **Unknowns:**
  - ~~Który dostawca/model LLM generuje propozycje?~~ — rozstrzygnięte 2026-07-04: OpenRouter (zob. sekcję AI/LLM layer w `tech-stack-backend.md`); konkretny model wybierany w `/10x-plan` tego slice'a.
  - Jak zliczać akceptacje/odrzucenia propozycji, by zmierzyć próg 75% z Kryteriów sukcesu? — Owner: team. Block: no.
- **Risk:** To gwiazda przewodnia i najryzykowniejsze założenie produktu — jeśli jakość generacji nie dowiezie progu 75% akceptacji, rdzeń wartości wymaga rewizji; dlatego slice wchodzi natychmiast po tym, jak S-02 da ścieżkę publikacji, na której można oprzeć bramkę akceptacji.
- **Status:** proposed

### S-04: Administrator edytuje i usuwa opublikowane wpisy

- **Outcome:** Administrator poprawia treść opublikowanego wpisu oraz usuwa wpis błędny — zmiany natychmiast widoczne w publicznych aktualnościach.
- **Change ID:** news-post-management
- **PRD refs:** FR-007, FR-008
- **Prerequisites:** S-02
- **Parallel with:** S-03
- **Blockers:** —
- **Unknowns:** —
- **Risk:** Domknięcie cyklu życia wpisu; celowo za S-02 (musi istnieć co edytować) i równolegle do S-03 — nie blokuje gwiazdy przewodniej, a przed publicznym startem daje administratorowi możliwość wycofania błędnego wpisu.
- **Status:** proposed

## Backlog Handoff

Backlog prowadzony w GitHub Projects (prywatny, dostęp przez zaproszenia):
<https://github.com/users/rkulig/projects/2> — pole „Roadmap status" odzwierciedla
statusy z tego pliku (synchronizowane 2026-07-04).

| Roadmap ID | Change ID              | Suggested issue title                                                     | Issue | Ready for `/10x-plan` | Notes |
| ---------- | ---------------------- | ------------------------------------------------------------------------- | ----- | --------------------- | ----- |
| S-01       | public-news-reading    | Publiczna lista i widok opublikowanych aktualności                        | [#9](https://github.com/rkulig/plomienkostrze/issues/9)   | yes                   | Run `/10x-plan public-news-reading` |
| S-02       | manual-news-publishing | Logowanie administratora + ręczne tworzenie i publikacja wpisów           | [#10](https://github.com/rkulig/plomienkostrze/issues/10) | no                    | Czeka na S-01 |
| S-03       | gated-news-generation  | Generowanie propozycji wpisu z danych meczowych z bramką akceptacji       | [#11](https://github.com/rkulig/plomienkostrze/issues/11) | no                    | Czeka na S-02 |
| S-04       | news-post-management   | Edycja i usuwanie opublikowanych wpisów                                   | [#12](https://github.com/rkulig/plomienkostrze/issues/12) | no                    | Czeka na S-02 |

## Open Roadmap Questions

1. ~~**Który zewnętrzny dostawca tożsamości obsłuży logowanie?**~~ — **ROZSTRZYGNIĘTE 2026-07-04: Firebase Authentication** (Google teraz, Facebook w fast-follow; backend weryfikuje tokeny ID przez JWKS). Zapis: `tech-stack.md` + sekcja Identity w `tech-stack-backend.md`. Odblokowało S-02.
2. ~~**Który dostawca/model LLM będzie generował propozycje wpisów?**~~ — **ROZSTRZYGNIĘTE 2026-07-04: OpenRouter** (elastyczna zmiana modeli pod eksperyment 75% akceptacji; klucz w Secret Manager). Zapis: sekcja AI/LLM layer w `tech-stack-backend.md`; konkretny model wybierany w `/10x-plan gated-news-generation`. Odblokowało S-03.

## Parked

- **Forum i interakcje kibiców (FR-002, FR-011–FR-016: logowanie kibiców, reakcje, komentarze, moderacja, forum)** — Why parked: PRD §Non-Goals — jawnie poza MVP jako fast-follow; MVP obsługuje wyłącznie publikację i czytanie aktualności.
- **Import plików (PDF/DOCX itp.) przy tworzeniu wpisów** — Why parked: PRD §Non-Goals — dane meczowe wprowadzane luźnym tekstem.
- **Natywna aplikacja mobilna (iOS/Android)** — Why parked: PRD §Non-Goals — na początek wyłącznie web; odsprzęglone API trzyma tę furtkę otwartą bez dodatkowej pracy teraz.
- **Automatyczne pobieranie danych meczowych z zewnętrznych źródeł** — Why parked: PRD §Non-Goals — administrator wprowadza dane ręcznie.

## Done

(Empty on first generation. `/10x-archive` appends an entry here — and flips that item's `Status` to `done` — when a change whose `Change ID` matches the item is archived. Do NOT pre-populate.)

- **S-02: Administrator loguje się kontem zewnętrznego dostawcy tożsamości, tworzy wpis ręcznie i publikuje go — wpis natychmiast widać w publicznych aktualnościach z S-01.** — Archived 2026-07-06 → `context/archive/2026-07-06-manual-news-publishing/`. Lesson: —.
