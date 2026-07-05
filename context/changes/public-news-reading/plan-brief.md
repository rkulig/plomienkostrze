# Publiczna lista i widok opublikowanych aktualności — Plan Brief

> Full plan: `context/changes/public-news-reading/plan.md`

## What & Why

Pierwszy slice domenowy (roadmap S-01): gość bez logowania widzi listę
opublikowanych wpisów aktualności i otwiera dowolny do przeczytania (PRD: US-02,
FR-009). To najcieńszy pionowy przekrój produktu — model wpisu → API → widoki SPA —
na którym S-02/S-03/S-04 tylko dobudowują.

## Starting Point

Oba appki to działający scaffold z probe'em e2e (widok `test-flow` → `POST/GET
/api/test-messages` → tabela w Cloud SQL), jawnie tymczasowym. Zero funkcji
domenowych; Flyway, CORS i deploy (auto po merge do `master`) już działają;
`min-instances=1` to decyzja MVP z `infrastructure.md` do potwierdzenia/ustawienia
w Fazie 3 (bring-up celowo deployował z 0).

## Desired End State

Kibic wchodzi na produkcyjne `/` i widzi wpisy klubu (tytuł, data, zajawka) od
najnowszego; klik otwiera `/news/:id` z pełną treścią w akapitach — bez logowania,
pierwsza treść < 2 s. Probe test-flow (widok, endpoint, tabela) już nie istnieje.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) |
| --- | --- | --- |
| Model danych | Kolumna `status` od razu (`PUBLISHED`) | S-02/S-03 dopisują wartości enum, nie przebudowują schematu i API. |
| Format treści | Czysty tekst, akapity po pustych liniach | Zero XSS/sanityzacji, najprostszy edytor (S-02) i prompt (S-03). |
| Treść startowa | Migracja Flyway z seedem (4 wpisy, pisze implementujący) | Narzędzia admina dopiero w S-02; seed wersjonowany, identyczny na każdym środowisku. |
| Adresowanie wpisu | Po `id` (`/news/:id`, `GET /api/news-posts/{id}`) | Zero logiki slugów; slug można dodać później bez łamania czegokolwiek. |
| Kształt listy API | Paginacja offsetowa od razu (`{ items, total }`) | Dokładanie paginacji później = breaking change dla przyszłych klientów mobilnych. |
| Architektura SPA | Hybryda: scaffold + signals, HTTP tylko w serwisie `NewsApi` | Zgodne z `frontend/CLAUDE.md`, a czysty szew pod rozbudowę prawie za darmo. |
| Strona główna | `/` = lista aktualności | Aktualności to jedyna treść MVP — kibic dostaje je natychmiast. |
| Probe test-flow | Usunięty w tym slice (kod + `DROP TABLE`) | Był „do usunięcia gdy wejdą prawdziwe feature'y" — to teraz; znika endpoint zapisu bez auth. |
| Testy automatyczne | Brak nowych testów (dopisane później) | Decyzja użytkownika; bramką są buildy, istniejące testy i weryfikacja manualna. |

## Scope

**In scope:** migracje `news_posts` (schemat + seed), encja/repozytorium/kontroler
odczytu (lista paginowana + szczegół, tylko `PUBLISHED`), serwis `NewsApi`, widoki
listy (`/`) i szczegółu (`/news/:id`) ze stanami błędu/pustki, aktualizacja app
shella, pełne usunięcie probe'a test-flow.

**Out of scope:** auth i narzędzia admina (S-02), generowanie (S-03),
edycja/usuwanie (S-04), Markdown/HTML w treści, slugi/SEO/SSR, nowe testy
automatyczne, landing page.

## Architecture / Approach

Postgres (`news_posts` ze statusem) → Spring: `NewsPostRepository.findByStatus` →
`NewsPostController` (`GET /api/news-posts` z `{ items, total }`, zajawka liczona
serwerowo; `GET /api/news-posts/{id}`, 404 dla nieopublikowanych) → Angular: serwis
`NewsApi` (jedyne miejsce HTTP) → standalone komponenty `NewsList` (sygnały,
doładowywanie starszych) i `NewsDetail` (akapity przez `@for`, bez `innerHTML`).

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Backend: model + API odczytu | Zaseedowane `news_posts` + dwa publiczne endpointy | Zgodność typu `content` między Postgres/walidatorem/H2 (rozstrzygnięte: `VARCHAR(10000)`) |
| 2. Frontend: lista i szczegół | Kibic czyta aktualności lokalnie e2e | Stany błędu/pustki łatwo pominąć — wprost w kryteriach |
| 3. Sprzątanie probe'a + produkcja | Czysty kod, slice zweryfikowany na produkcji | `DROP TABLE` łamie rollback do rewizji probe'a (świadomie zaakceptowane) |

**Prerequisites:** lokalny Postgres (defaulty `plomien`/`plomien`), JDK 21, `nvm use` przed komendami frontendu.
**Estimated effort:** ~2–3 sesje, 3 fazy (każda z bramką manualną).

## Open Risks & Assumptions

- Treść seedowana jest fikcyjno-realistyczna — przed publicznym startem admin
  podmieni ją na prawdziwą (możliwe od S-02); recenzja treści w PR.
- Zakładamy, że walidator Hibernate przyjmie schemat V2 bez niespodzianek —
  pierwszy lokalny start w Fazie 1 to weryfikuje przed jakimkolwiek deployem.
- Brak nowych testów automatycznych = regresje kontraktu wykrywane manualnie do
  czasu doklejenia testów (kandydat: razem z S-02).

## Success Criteria (Summary)

- Gość na produkcji czyta listę i pełną treść wpisów bez logowania; pierwsza treść < 2 s.
- Lista pokazuje wyłącznie wpisy `PUBLISHED`, od najnowszego, z poprawną paginacją.
- Probe zniknął z produkcji: `GET /api/test-messages` → 404, `/test-flow` nie istnieje.
