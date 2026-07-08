# Publiczna lista i widok opublikowanych aktualności — Implementation Plan

## Overview

Pierwszy slice domenowy (roadmap S-01, issue [#9](https://github.com/rkulig/plomienkostrze/issues/9)):
gość bez logowania widzi listę opublikowanych wpisów aktualności i otwiera dowolny
do przeczytania. Slice przecina wszystkie warstwy — migracja Flyway → encja JPA →
publiczne API odczytu → widoki SPA — i przy okazji sprząta tymczasowy probe
test-flow, który ten tor udowadniał.

## Current State Analysis

- **Backend** (`backend/src/main/java/com/plomienkostrze/`): tylko probe —
  `web/TestMessageController.java`, `testmessage/TestMessage.java` + repozytorium,
  `web/PingController.java`, `web/CorsConfig.java`. Zero endpointów domenowych.
- **Schemat**: Flyway właścicielem (`spring.jpa.hibernate.ddl-auto=validate`),
  jedyna migracja `V1__create_test_messages.sql` (tabela probe'a).
- **Frontend** (`frontend/src/app/`): app shell (nagłówek z tytułem-sygnałem
  `plomien-kostrze`), router z jedyną trasą `test-flow` (lazy `loadComponent`),
  `provideHttpClient()` już w `app.config.ts`, `environment.apiBaseUrl` dla dev/prod.
- **Testy**: backend `PlomienKostrzeApiApplicationTests` (contextLoads) na H2 —
  `src/test/resources/application.properties` **cieniuje** produkcyjny plik i wyłącza
  Flyway (`ddl-auto=create-drop`); frontend bez speców.
- **Deploy**: auto-deploy po merge do `master` (path-filtrowane workflows GitHub
  Actions); `min-instances=1` to decyzja MVP z `infrastructure.md` (NFR < 2 s), ale
  bring-up deployował celowo z `--min-instances=0` (`deployment-plan.md`), a CI nie
  ustawia tej flagi — faktyczną wartość trzeba sprawdzić przed weryfikacją NFR (Faza 3).
- **Kluczowe ograniczenie slice'a**: narzędzia administratora wchodzą dopiero w S-02,
  więc treść startowa musi zostać zasiana migracją.

## Desired End State

Gość otwiera `https://<firebase-hosting>/` i widzi listę opublikowanych wpisów
(tytuł, data, zajawka) od najnowszego; klika wpis → `/news/:id` pokazuje pełną treść
z akapitami. Czytanie nie wymaga logowania; lista pokazuje wyłącznie wpisy
`PUBLISHED`. Probe test-flow (widok, API, tabela) nie istnieje. Weryfikacja: ścieżka
przeklikana na produkcji po auto-deployu, pierwsza treść widoczna < 2 s.

### Key Discoveries:

- Probe to gotowy wzorzec do naśladowania: kontroler z rekordami DTO i
  `ResponseStatusException` (`web/TestMessageController.java`), encja z `@PrePersist`
  (`testmessage/TestMessage.java`), komponent standalone z sygnałami i stanami
  busy/error (`frontend/src/app/test-flow/test-flow.ts`).
- `frontend/CLAUDE.md`: konwencja „standalone + signals, wzorzec scaffoldu" — wiążąca;
  cięższa architektura warstwowa ze skilla `frontend` odrzucona decyzją (hybryda:
  scaffold + wydzielony serwis API).
- Testowe `application.properties` cieniuje produkcyjne — każda nowa właściwość
  wymagana przez aplikację musiałaby być tam powtórzona (ten slice żadnej nie dodaje).
- `tech-stack-backend.md`: migracje forward-only i wstecznie kompatybilne z poprzednią
  rewizją appki (rollback Cloud Run nie cofa migracji) — istotne przy `DROP TABLE`
  w Fazie 3.

## What We're NOT Doing

- Żadnych testów automatycznych nowego kodu (decyzja: „potem dopiszemy testy") —
  bramką są buildy, istniejące testy i weryfikacja manualna.
- Żadnego auth / narzędzi administratora (S-02), generowania (S-03), edycji/usuwania (S-04).
- Żadnego formatu bogatego treści (Markdown/HTML) — czysty tekst z akapitami.
- Żadnych slugów w URL, SEO/SSR, obrazków we wpisach, osobnego landingu.
- Żadnej kolumny `updated_at` ani pól pod generowanie — dochodzą w slice'ach, które
  ich potrzebują (S-03/S-04 mają własne plany).

## Implementation Approach

Trzy fazy, każda kończy się działającą całością i bramką manualną: (1) backend —
schemat + seed + publiczne API odczytu, weryfikowane curlem; (2) frontend — lista
pod `/` i szczegół pod `/news/:id` przez wydzielony serwis API, weryfikowane e2e
lokalnie; (3) usunięcie probe'a i weryfikacja produkcyjna po auto-deployu.
Model danych dostaje kolumnę `status` już teraz (S-02/S-03 tylko dopisują wartości),
a publiczny kontrakt listy jest od razu paginowany (`{ items, total }`), żeby przyszli
konsumenci mobilni nie dostali breaking change.

## Critical Implementation Details

- **Typ kolumny `content` musi zgadzać się w trzech miejscach naraz**: Postgres
  (migracja), walidator Hibernate (`ddl-auto=validate` na deployu) i H2 w testach
  (`create-drop`). Deterministycznie godzi je `VARCHAR(10000)` po obu stronach
  (migracja + `@Column(length = 10000)`); `TEXT`/`@Lob` ryzykuje rozjazd walidacji
  i składni H2. Limit jest hojny dla wpisów newsowych.
- **Seed z deterministycznymi znacznikami czasu**: `published_at` w migracji seedowej
  jako stałe timestampy (nie `now()`), żeby porządek listy był stabilny i
  powtarzalny między środowiskami.
- **`DROP TABLE test_messages` (Faza 3) świadomie łamie wsteczną kompatybilność**
  z rewizjami sprzed tej fazy (walidacja schematu starej rewizji by padła). Ryzyko
  zaakceptowane: probe jest jawnie tymczasowy, a usunięcie kodu i tabeli jedzie w tym
  samym wdrożeniu; rollback celowałby i tak w rewizję sprzed S-01.

## Phase 1: Backend — model danych + publiczne API odczytu

### Overview

Tabela `news_posts` z seedem oraz dwa publiczne endpointy odczytu (lista paginowana
+ szczegół), filtrujące status `PUBLISHED`.

### Changes Required:

#### 1. Migracja schematu

**File**: `backend/src/main/resources/db/migration/V2__create_news_posts.sql`

**Intent**: Tabela wpisów aktualności z kolumną statusu (przyszłe S-02/S-03 dopisują
wartości, nie kolumny) i indeksem pod jedyne zapytanie listy.

**Contract**:

```sql
CREATE TABLE news_posts (
    id           BIGSERIAL PRIMARY KEY,
    title        VARCHAR(200)  NOT NULL,
    content      VARCHAR(10000) NOT NULL,
    status       VARCHAR(20)   NOT NULL,
    published_at TIMESTAMPTZ,            -- NULL dla przyszłych szkiców/propozycji
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_news_posts_status_published_at
    ON news_posts (status, published_at DESC);
```

#### 2. Migracja seedująca

**File**: `backend/src/main/resources/db/migration/V3__seed_news_posts.sql`

**Intent**: 4 realistyczne wpisy klubowe po polsku (relacja meczowa, ogłoszenie
treningu, wydarzenie klubowe, komunikat), status `PUBLISHED`, treść wieloakapitowa
(akapity rozdzielone pustą linią — testuje render), stałe `published_at` w różnych
dniach. Treść pisze implementujący; recenzja treści w PR.

**Contract**: same `INSERT INTO news_posts (title, content, status, published_at)`;
bez jawnych `id` (BIGSERIAL nadaje).

#### 3. Encja i repozytorium

**File**: `backend/src/main/java/com/plomienkostrze/news/NewsPost.java`,
`.../news/NewsPostStatus.java`, `.../news/NewsPostRepository.java`

**Intent**: Encja JPA lustrzana do migracji (wzorzec `TestMessage`: `@PrePersist`
dla `created_at`), enum statusu, repozytorium z jedynym zapytaniem listy.

**Contract**: `NewsPostStatus { PUBLISHED }` mapowany `@Enumerated(EnumType.STRING)`;
`content` z `@Column(length = 10000)` (zob. Critical Implementation Details);
`NewsPostRepository extends JpaRepository<NewsPost, Long>` z
`Page<NewsPost> findByStatus(NewsPostStatus status, Pageable pageable)`.

#### 4. Kontroler publicznego odczytu

**File**: `backend/src/main/java/com/plomienkostrze/web/NewsPostController.java`

**Intent**: Dwa endpointy odczytu dla gościa (wzorzec `TestMessageController`:
rekordy DTO w kontrolerze, `ResponseStatusException`). Zajawka liczona serwerowo,
żeby lista nie woziła pełnych treści (payload i przyszli konsumenci mobilni).

**Contract**:

- `GET /api/news-posts?page=0&size=10` → `200` z
  `{ "items": [ { "id", "title", "publishedAt", "excerpt" } ], "total": <long> }`;
  tylko `PUBLISHED`, sortowanie `published_at DESC`; `page` ≥ 0 (default 0),
  `size` 1–50 (default 10), wartości spoza zakresu → `400`.
- `GET /api/news-posts/{id}` → `200` z `{ "id", "title", "publishedAt", "content" }`;
  brak wpisu **lub** status ≠ `PUBLISHED` → `404`.
- `excerpt` = pierwszy akapit treści przycięty do 200 znaków (z „…" gdy przycięto).

### Success Criteria:

#### Automated Verification:

- Backend builduje się i istniejące testy przechodzą: `cd backend && ./mvnw test`
  (wymaga JDK 21 — `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`)

#### Manual Verification:

- Po `./mvnw spring-boot:run` (lokalny Postgres): migracje V2+V3 aplikują się czysto,
  walidacja schematu przechodzi (aplikacja wstaje)
- `curl localhost:8080/api/news-posts` zwraca 4 zaseedowane wpisy od najnowszego,
  z `total: 4` i sensownymi zajawkami
- `curl localhost:8080/api/news-posts?page=0&size=2` zwraca 2 wpisy, `total: 4`;
  `size=100` zwraca `400`
- `curl localhost:8080/api/news-posts/1` zwraca pełną treść;
  `curl localhost:8080/api/news-posts/999` zwraca `404`

**Implementation Note**: Po tej fazie i zielonych kryteriach automatycznych zatrzymaj
się na manualne potwierdzenie człowieka przed Fazą 2.

---

## Phase 2: Frontend — widoki listy i szczegółu

### Overview

Lista aktualności pod `/` i widok pojedynczego wpisu pod `/news/:id`, z wydzielonym
serwisem API (hybryda: scaffold + signals, ale zero `HttpClient` w komponentach).

### Changes Required:

#### 1. Typy i serwis API

**File**: `frontend/src/app/news/news-api.ts` (+ interfejsy w tym samym pliku lub
`news-types.ts`)

**Intent**: Jedyne miejsce rozmowy z API newsów — komponenty nie dotykają
`HttpClient`. Interfejsy lustrzane do kontraktu z Fazy 1.

**Contract**: `@Injectable({ providedIn: 'root' })` klasa `NewsApi` z metodami
`list(page: number, size: number): Observable<NewsPostList>` i
`get(id: number): Observable<NewsPost>`; bazowy URL z `environment.apiBaseUrl`
(wzorzec `test-flow`); interfejsy `NewsPostSummary { id, title, publishedAt, excerpt }`,
`NewsPost { id, title, publishedAt, content }`, `NewsPostList { items, total }`.

#### 2. Komponent listy

**File**: `frontend/src/app/news/news-list.ts` + `.html` + `.scss`

**Intent**: Domyślny widok aplikacji: wpisy od najnowszego (tytuł-link, data,
zajawka), doładowywanie starszych stron przyciskiem, stany ładowania/pustki/błędu.

**Contract**: Komponent standalone z sygnałami (wzorzec `test-flow`: `signal()` na
`items/busy/error`); pierwsza strona `size=10` ładowana w konstruktorze; przycisk
„Pokaż starsze" dokleja kolejną stronę i znika, gdy `items.length >= total`; pusty
stan „Brak aktualności"; błąd HTTP → komunikat po polsku (wzorzec `test-flow`);
data przez `DatePipe` (`dd.MM.yyyy`); tytuł linkuje `routerLink` do `/news/:id`.

#### 3. Komponent szczegółu

**File**: `frontend/src/app/news/news-detail.ts` + `.html` + `.scss`

**Intent**: Pełna treść wpisu renderowana jako akapity z czystego tekstu; 404 z API
pokazuje przyjazny komunikat z linkiem powrotnym.

**Contract**: `:id` z `ActivatedRoute`; treść dzielona na akapity po pustych liniach
(`split(/\n\s*\n/)`) i renderowana pętlą `@for` w `<p>` — **bez** `innerHTML`
(gwarancja braku XSS); nagłówek: tytuł + data; błąd/404 → komunikat „Nie znaleziono
wpisu" + link do `/`.

#### 4. Routing i app shell

**File**: `frontend/src/app/app.routes.ts`, `frontend/src/app/app.ts`, `app.html`,
`app.scss`

**Intent**: Lista jako strona główna, szczegół lazy; nagłówek aplikacji przestaje
być techniczny.

**Contract**: trasy `'' → NewsList` i `'news/:id' → NewsDetail` (obie lazy
`loadComponent`, wzorzec istniejącej trasy); tytuł w nagłówku zmienia się na
„Płomień Kostrze" i linkuje do `/`. Trasa `test-flow` zostaje do Fazy 3.

### Success Criteria:

#### Automated Verification:

- Frontend builduje się produkcyjnie: `cd frontend && nvm use && npm run build`

#### Manual Verification:

- Lokalnie (`npm start` + działający backend z Fazy 1): `/` pokazuje 4 wpisy od
  najnowszego z datą i zajawką
- Klik w tytuł otwiera `/news/:id` z pełną treścią w akapitach; powrót działa
- Ręczne wejście na `/news/999` pokazuje komunikat „Nie znaleziono wpisu" z linkiem do `/`
- Przy zatrzymanym backendzie `/` pokazuje komunikat błędu (nie pustkę/wieczny spinner)
- Pusty stan: lokalnie `UPDATE news_posts SET status = 'HIDDEN';` (kolumna to VARCHAR,
  wartość tymczasowa), odświeżenie `/` pokazuje „Brak aktualności"; potem przywrócenie
  `UPDATE news_posts SET status = 'PUBLISHED';`
- Odświeżenie strony na `/news/:id` działa (deep-link przez routing SPA)

**Implementation Note**: Po tej fazie zatrzymaj się na manualne potwierdzenie
człowieka przed Fazą 3.

---

## Phase 3: Sprzątanie probe'a + weryfikacja produkcyjna

### Overview

Usunięcie tymczasowego probe'a (widok SPA, API, tabela) — jego rolę przejęła
prawdziwa ścieżka domenowa — i potwierdzenie całości na produkcji po auto-deployu.

### Changes Required:

#### 1. Usunięcie probe'a z SPA

**File**: `frontend/src/app/test-flow/` (katalog), `frontend/src/app/app.routes.ts`

**Intent**: Kasacja widoku diagnostycznego i jego trasy (komentarz w routes wprost
to zapowiada).

**Contract**: katalog `test-flow/` znika; w `app.routes.ts` zostają tylko trasy newsowe.

#### 2. Usunięcie probe'a z API

**File**: `backend/src/main/java/com/plomienkostrze/web/TestMessageController.java`,
`backend/src/main/java/com/plomienkostrze/testmessage/` (katalog)

**Intent**: Kasacja niezabezpieczonego endpointu zapisu (`POST /api/test-messages`
bez auth na produkcji) wraz z encją i repozytorium.

**Contract**: pakiet `testmessage` i kontroler znikają; `PingController` i
`CorsConfig` zostają bez zmian.

#### 3. Migracja porządkowa

**File**: `backend/src/main/resources/db/migration/V4__drop_test_messages.sql`

**Intent**: Usunięcie tabeli probe'a — jedzie w tym samym wdrożeniu co usunięcie
encji (zob. Critical Implementation Details: świadome złamanie wstecznej
kompatybilności z rewizjami probe'a).

**Contract**: `DROP TABLE test_messages;`

### Success Criteria:

#### Automated Verification:

- Backend builduje się i testy przechodzą: `cd backend && ./mvnw test`
- Frontend builduje się: `cd frontend && nvm use && npm run build`
- Brak śladów probe'a w żywym kodzie: `grep -ri "test-message\|TestMessage\|test-flow" frontend/src backend/src/main/java` zwraca zero trafień (katalog migracji celowo poza zakresem — V1 zostaje jako niemutowalna historia Flyway, a V4 z natury zawiera `test_messages`)

#### Manual Verification:

- Lokalny smoke: aplikacja wstaje (V4 aplikuje się czysto), `/` i `/news/:id` działają
- Przed pomiarem NFR: `gcloud run services describe plomien-api --region europe-central2`
  potwierdza `min-instances=1`; jeśli nadal 0 (stan z bring-upu) — `gcloud run services
  update plomien-api --region europe-central2 --min-instances=1` (decyzja i koszt
  zaakceptowane w `infrastructure.md`)
- Po merge do `master` i auto-deployu: produkcyjne `/` (Firebase Hosting) pokazuje
  listę, klik otwiera wpis; pierwsza treść widoczna < 2 s (NFR)
- Produkcyjne `GET /api/test-messages` zwraca `404`; `/test-flow` w SPA nie istnieje
- `GET /api/ping` nadal odpowiada `{"status":"ok"}` (brak regresji)

**Implementation Note**: Merge do `master` (auto-deploy) wykonuje użytkownik przez
PR — zgodnie z konwencją repo commit z brancha, push i PR po stronie użytkownika.

---

## Testing Strategy

Decyzją użytkownika ten slice **nie dodaje testów automatycznych** — dopisujemy je
później (kandydat na osobny change lub doklejkę do S-02: MockMvc dla filtra statusu,
paginacji i 404). Siatka bezpieczeństwa slice'a:

### Istniejące automaty:

- `./mvnw test` (contextLoads na H2 — wykryje błędy konfiguracji/encji)
- `npm run build` (AOT złapie błędy szablonów i typów)
- CI na PR: path-filtrowane build+test obu aplikacji

### Manual Testing Steps:

1. Faza 1: sekwencja curli (lista / paginacja / walidacja `size` / szczegół / 404)
2. Faza 2: przeklik `/` → wpis → powrót; `/news/999`; backend zatrzymany; deep-link
3. Faza 3: smoke lokalny, po deployu przeklik produkcyjny + `test-messages` → 404

## Performance Considerations

NFR „pierwsza treść < 2 s" niesie infrastruktura (`min-instances=1` — do potwierdzenia
/ ustawienia w Fazie 3, zob. Manual Verification; CDN Firebase).
Slice wspiera go: zajawki zamiast pełnych treści na liście, indeks pod zapytanie
listy, `size` capowane na 50.

## Migration Notes

V2 (schemat) i V3 (seed) są czysto addytywne. V4 (`DROP TABLE test_messages`) łamie
kompatybilność z rewizjami probe'a — świadoma decyzja, zob. Critical Implementation
Details. Wszystkie migracje forward-only (konwencja `tech-stack-backend.md`).

## References

- Zmiana: `context/changes/public-news-reading/change.md`
- Roadmapa S-01: `context/foundation/roadmap.md`
- PRD (US-02, FR-009, NFR-y): `context/foundation/prd.md`
- Wzorce probe'a: `backend/src/main/java/com/plomienkostrze/web/TestMessageController.java`,
  `frontend/src/app/test-flow/test-flow.ts`
- Konwencje per-app: `frontend/CLAUDE.md`, `backend/CLAUDE.md`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Backend — model danych + publiczne API odczytu

#### Automated

- [x] 1.1 Backend builduje się i istniejące testy przechodzą (`./mvnw test`) — 5c815e1

#### Manual

- [x] 1.2 Migracje V2+V3 aplikują się czysto, aplikacja wstaje (walidacja schematu OK) — 5c815e1
- [x] 1.3 Lista zwraca 4 zaseedowane wpisy od najnowszego z `total: 4` i zajawkami — 5c815e1
- [x] 1.4 Paginacja działa (`size=2` → 2 wpisy), `size=100` → `400` — 5c815e1
- [x] 1.5 Szczegół zwraca pełną treść; nieistniejący id → `404` — 5c815e1

### Phase 2: Frontend — widoki listy i szczegółu

#### Automated

- [x] 2.1 Frontend builduje się produkcyjnie (`npm run build`) — ecaacf3

#### Manual

- [x] 2.2 `/` pokazuje 4 wpisy od najnowszego z datą i zajawką — ecaacf3
- [x] 2.3 Klik otwiera `/news/:id` z treścią w akapitach; powrót działa — ecaacf3
- [x] 2.4 `/news/999` pokazuje komunikat „Nie znaleziono wpisu" z linkiem do `/` — ecaacf3
- [x] 2.5 Zatrzymany backend → komunikat błędu na `/` — ecaacf3
- [x] 2.6 Pusty stan „Brak aktualności" (tymczasowy UPDATE statusu w lokalnej bazie) — ecaacf3
- [x] 2.7 Deep-link (odświeżenie na `/news/:id`) działa — ecaacf3

### Phase 3: Sprzątanie probe'a + weryfikacja produkcyjna

#### Automated

- [x] 3.1 Backend builduje się i testy przechodzą (`./mvnw test`) — 6612687
- [x] 3.2 Frontend builduje się (`npm run build`) — 6612687
- [x] 3.3 Grep nie znajduje śladów probe'a w `frontend/src` i `backend/src/main/java` (migracje poza zakresem) — 6612687

#### Manual

- [x] 3.4 Smoke lokalny: V4 aplikuje się, `/` i `/news/:id` działają — 6612687
- [x] 3.5 `min-instances=1` potwierdzone (lub ustawione) przed pomiarem NFR
- [x] 3.6 Produkcja po auto-deployu: lista i szczegół działają, pierwsza treść < 2 s
- [x] 3.7 Produkcja: `GET /api/test-messages` → `404`, `/api/ping` → OK
