# Logowanie administratora + ręczne tworzenie i publikacja wpisów — Plan Brief

> Full plan: `context/changes/manual-news-publishing/plan.md`

## What & Why

Roadmap S-02: administrator loguje się kontem Google (Firebase Authentication),
tworzy wpis aktualności ręcznie i publikuje go jednym krokiem — wpis natychmiast
widoczny w publicznych aktualnościach z S-01 (FR-001, FR-006). To pierwszy slice
wprowadzający security: backend zaczyna weryfikować tokeny, SPA dostaje pierwszy
chroniony widok, a system — pierwszy uwierzytelniony endpoint zapisu.

## Starting Point

S-01 działa na produkcji: publiczne read-only API (`GET /api/news-posts` +
szczegół) i widoki listy/szczegółu w SPA. Zero auth w obu aplikacjach (brak
spring-security i SDK Firebase), encja `NewsPost` immutable-style bez ścieżki
tworzenia, enum statusów z samym `PUBLISHED`, `published_at` nullable z twardym
wymogiem z przeglądu S-01: publikacja musi je ustawiać.

## Desired End State

Admin klika „Zaloguj" w nagłówku, loguje się Google (redirect), po powrocie widzi
w nagłówku „Dodaj post" i „Wyloguj". „Dodaj post" prowadzi do formularza (tytuł +
treść); „Opublikuj" → `/news/:id` wpisu widocznego od razu dla każdego gościa.
Osoba spoza allowlisty po zalogowaniu widzi tylko „Wyloguj" (bez „Dodaj post");
deep-link `/admin` bez uprawnień → redirect na `/`; `POST` bez ważnego tokenu
admina → 401/403. Publiczne czytanie bez żadnych zmian.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) |
| --- | --- | --- |
| Rozpoznawanie admina | Allowlist UID w env (`ADMIN_UIDS`) | Najprostszy wariant dla jednego admina; wzór env-var (ALLOWED_ORIGINS) już istnieje; tech-stack jawnie odłożył tę decyzję tu. |
| Weryfikacja tokenu | Resource server + JWKS Google, bez Admin SDK | Bezstanowo, zero dodatkowych sekretów; `jwk-set-uri` (lazy) zamiast `issuer-uri` (eager), żeby testy działały bez sieci. |
| Workflow publikacji | Jeden krok: POST tworzy od razu `PUBLISHED` + `published_at` | Minimalny zakres FR-006 spełniający twardy wymóg z przeglądu S-01; bez `DRAFT` — S-03 zdefiniuje własny przepływ propozycji. |
| Inwariant `published_at` | Fabryka na encji + CHECK w migracji V5 | Defense in depth; migracja addytywna i backward-compatible. |
| Wejście admina w UI | Przyciski w nagłówku: „Zaloguj" → po zalogowaniu „Wyloguj" (+ „Dodaj post" dla admina) | Jawny, standardowy wzorzec — decyzja z przeglądu planu (zastąpiła wcześniejszy dyskretny link „Admin" w stopce). |
| Metoda logowania | `signInWithRedirect` | Wybór użytkownika; prod `authDomain = plomien-kostrze.web.app` (same-origin) neutralizuje główne ryzyko; fallback popup = 1 linia. |
| Nie-admin po zalogowaniu | Tylko „Wyloguj" — brak „Dodaj post", bez komunikatu; deep-link `/admin` → redirect na `/` | Najczystszy UX (brak przycisku mówi wszystko); allowlist zna tylko backend, więc SPA pyta `GET /api/me`. |
| Zakres panelu | Sam formularz nowego wpisu | Weryfikacja wizualna na publicznych widokach; lista/edycja/usuwanie to S-04. |
| SDK frontendu | Czysty `firebase` (bez `@angular/fire`) | Potrzebny tylko moduł auth; zero sprzężenia wersji z Angular 22; opakowany w jeden serwis (konwencja jak `NewsApi`). |
| Dev auth | Prawdziwy projekt Firebase także lokalnie | Zero dodatkowego setupu i identyczne zachowanie dev/prod dla solo dewelopera. |
| Testy | Nadal bez testów automatycznych | Decyzja użytkownika (konsekwentnie z S-01); bramki = buildy + weryfikacja manualna; dług spłacany w Module 3. |

## Scope

**In scope:** Spring Security (resource server, JWKS, `ROLE_ADMIN` z `ADMIN_UIDS`),
`POST /api/news-posts`, `GET /api/me`, fabryka publikacji na encji, migracja V5
(CHECK), SDK firebase + serwis Auth + interceptor tokenu, przyciski auth w nagłówku
(„Zaloguj"/„Wyloguj"/„Dodaj post"), widok `/admin` (formularz + redirect bez
uprawnień), konfiguracja Firebase/Cloud Run, weryfikacja e2e na produkcji.

**Out of scope:** testy automatyczne, status `DRAFT`/szkice, edycja/usuwanie (S-04),
generowanie (S-03), logowanie kibiców, custom claims / tabela adminów, lista wpisów
w panelu, podmiana treści seedowanej, Auth Emulator, rate-limiting/audit log.

## Architecture / Approach

SPA (Firebase Auth SDK w serwisie `AuthService`, token przez interceptor HTTP) →
Spring Security (JWKS Google + walidatory issuer/audience; `sub` ∈ `ADMIN_UIDS` →
`ROLE_ADMIN`) → `POST /api/news-posts` tworzy wpis fabryką `NewsPost.published()`
(zawsze z `published_at`) → publiczne GET-y z S-01 bez zmian (permitAll). Nagłówek
steruje logowaniem; „Dodaj post" widzi tylko admin (sygnał `isAdmin` z `GET /api/me`).
Widok `/admin` bez guarda — sam formularz, bez uprawnień redirect na `/`; obrona
w backendzie.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Backend: security + publikacja | Chroniony `POST`, `GET /api/me`, V5, publiczne GET-y nietknięte | Nazwy starterów Boot 4 + shadowing test-properties (rozpoznane, w planie) |
| 2. Frontend: logowanie + panel | Pełne lokalne e2e: login → formularz → wpis publiczny | `signInWithRedirect` na localhost (cross-origin) — fallback popup odnotowany |
| 3. Produkcja | Slice zweryfikowany e2e na `web.app` | Kroki ludzkie (konsola Firebase, `ADMIN_UIDS` na Cloud Run) muszą poprzedzić test |

**Prerequisites:** dostęp do konsoli Firebase projektu `plomien-kostrze` (rejestracja
web app + włączenie Google sign-in — krok ludzki na starcie Fazy 2), gcloud z
uprawnieniami do `plomien-api`, lokalny Postgres, JDK 21, `nvm use`.
**Estimated effort:** ~2–3 sesje, 3 fazy (każda z bramką manualną).

## Open Risks & Assumptions

- `signInWithRedirect` z localhost jest cross-origin względem `authDomain` — w
  restrykcyjnych przeglądarkach może wymagać przejścia na `signInWithPopup`
  (jednolinijkowy fallback, decyzja przy pierwszym e2e).
- Kanoniczny adres produkcyjny to `plomien-kostrze.web.app` (prod `authDomain`) —
  logowanie z `firebaseapp.com` może nie działać w części przeglądarek.
- Pusta `ADMIN_UIDS` = nikt nie jest adminem (bezpieczny default); literówka w UID
  objawia się cicho jako brak przycisku „Dodaj post" — diagnoza przez konsolę
  Firebase, nie przez UI.
- Brak testów automatycznych = regresje security wykrywane manualnie; dług
  odnotowany, spłata planowana w Module 3.

## Success Criteria (Summary)

- Admin publikuje wpis end-to-end: „Zaloguj" → „Dodaj post" → formularz → wpis
  natychmiast czytelny dla gościa bez logowania.
- `POST /api/news-posts` bez ważnego tokenu admina → 401/403; konto spoza allowlisty
  nie widzi „Dodaj post" i nie wejdzie na formularz (redirect).
- Publiczne czytanie aktualności (S-01) działa bez jakiejkolwiek regresji.
