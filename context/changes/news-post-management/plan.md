# News Post Management (edit + delete) Implementation Plan

## Overview

Slice **S-04** domyka cykl życia wpisu aktualności: administrator może **edytować** treść opublikowanego wpisu (FR-007) oraz **usunąć** wpis błędny (FR-008). Obie operacje są chronione (tylko admin) i natychmiast widoczne na publicznych widokach listy/szczegółu — bez warstwy cache do inwalidacji. Kolejność prac powiela sprawdzony rytm S-02/S-03: najpierw backend (endpointy + migracja), potem frontend (UI admina), na końcu weryfikacja produkcyjna.

## Current State Analysis

Stan zastany (z badania kodu):

- **Backend CRUD żyje bezpośrednio w `NewsPostController`** — brak warstwy serwisowej. Create: `repository.save(NewsPost.published(...))`; read: `repository.findById`. `NewsPostRepository extends JpaRepository<NewsPost, Long>` daje już `save`/`deleteById`.
- **Encja `NewsPost` jest immutable-style** — same gettery, `protected` konstruktor JPA, fabryka `published(...)`; **brak setterów**. Edit to pierwsza ścieżka mutacji i musi rozszerzyć ten styl (metoda intencyjna, nie settery). Ma `createdAt` + `publishedAt`, **brak `updatedAt`**, **brak `@Version`**.
- **Status:** enum `NewsPostStatus` ma jedynie `PUBLISHED` — wszystkie wiersze są publikowalne, nie trzeba filtrować po statusie przy edycji/usuwaniu.
- **Bezpieczeństwo** jest URL-owe (`SecurityConfig`), zakończone `.anyRequest().denyAll()`. `GET /api/news-posts/**` jest `permitAll` (tylko GET). `POST /api/news-posts` i `.../generate` → `hasRole("ADMIN")`. Każdy nowy `PUT`/`DELETE` da 403, dopóki nie dostanie własnego matchera.
- **Ograniczenie DB:** CHECK `chk_news_posts_published_at` (migracja V5) wymaga `published_at IS NOT NULL` dla wierszy `PUBLISHED`. Edycja **nie rusza** `publishedAt` ani statusu, więc CHECK pozostaje spełniony. Następna wolna migracja: **V6**.
- **Błędy:** brak `@ControllerAdvice`. 404 przez inline `ResponseStatusException(HttpStatus.NOT_FOUND, "news post not found")` (`NewsPostController.java:102`) — wzorzec do powtórzenia. Walidacja `@Valid` → 400 (domyślny Spring).
- **Frontend:** wszystkie wywołania idą przez `NewsApi` (`news-api.ts`); `authInterceptor` dokłada token Firebase do żądań pod `apiBaseUrl` — nowe wywołania są autoryzowane „za darmo". `AdminPanel` (`admin` route) to jedyny widok admina (formularz create + generacja AI). **Brak listy admina, brak wzorca dialogu/toast/modala.** Postęp = przycisk `disabled` + etykieta „…". Błędy inline `<p class="error">`. Gating admina: sygnał `AdminStatus.isAdmin` + efekt przekierowujący (jak w `AdminPanel`).
- **Testy:** brak testów automatycznych dla kodu domenowego (świadoma konwencja S-01→S-03; strategia testów to Moduł 3). Jedyny test to `contextLoads()` na H2 (create-drop, offline). Weryfikacja per faza: curl + e2e ręczne.
- **Deploy:** merge do `master` → auto-deploy. Usługa Cloud Run to **`plomien-api`** (nie `plomien-kostrze-api`).

## Desired End State

Administrator otwiera opublikowany wpis na publicznym widoku szczegółu i widzi (tylko jako admin) akcje **Edytuj** i **Usuń**:

- **Edytuj** → przenosi do formularza `admin/edit/:id` z wypełnionymi tytułem i treścią; po zapisie (`PUT`) wraca na `/news/:id` z zaktualizowaną treścią, którą od razu widzi każdy gość.
- **Usuń** → dwustopniowe potwierdzenie inline („Na pewno? Tak / Anuluj"); po potwierdzeniu (`DELETE`) wiersz znika z bazy, użytkownik wraca na listę, a wpis nie jest już widoczny publicznie.

Weryfikacja: patrz Success Criteria per faza (curl dla backendu, e2e lokalny i produkcyjny dla całości).

### Key Discoveries:

- `NewsPostController.java` — cała logika CRUD, wzorzec DTO (zagnieżdżone `record`), `DetailResponse.from(...)`, inline 404 (`:102`).
- `NewsPost.java` — immutable-style, fabryka `published(...)` (`:52-59`), `@PrePersist onCreate()` (`:61-66`); brak setterów, brak `updatedAt`/`@Version`.
- `SecurityConfig.java:66-77` — matchery URL zakończone `denyAll()`; wzorzec `.requestMatchers(HttpMethod.POST, "...").hasRole("ADMIN")`.
- `db/migration/V5__news_posts_published_check.sql` — CHECK na `published_at`; następna migracja to V6.
- `news-api.ts` — jedyny punkt HTTP; wzorce `create()` (`:48`) i `get()` (`:44`) do naśladowania dla `update()`/`delete()`.
- `admin-panel.ts` — wzorzec reactive form + walidacja + gating (`isAdmin` effect) + progres „…"; `news-detail.ts:39-43` — walidacja dodatniej liczby w `:id`.
- `frontend/CLAUDE.md` i `backend/CLAUDE.md` są autorytatywne nad skillem `frontend` (który jest niezgodny z realną konwencją repo — sygnały, standalone, `providedIn: 'root'`).

## What We're NOT Doing

- **Brak soft-delete** — usuwanie jest fizyczne (`deleteById`). Bez kolumny/statusu `DELETED`, bez kosza, bez „cofnij".
- **Brak nowej wartości `NewsPostStatus`** i brak zmiany statusu/`publishedAt` przy edycji (post nie jest przenoszony na górę listy).
- **Brak optimistic locking (`@Version`)** — last-write-wins; produkt jest jednoadministratorowy.
- **Brak wyświetlania „edytowano" publicznie** — kolumna `updated_at` jest na razie tylko zapisywana (write-only), nie renderowana.
- **Brak dedykowanego dashboardu admina** — akcje żyją na publicznym widoku szczegółu, gated po `isAdmin`.
- **Brak reużywalnego komponentu modala/toast** — potwierdzenie usunięcia jest inline.
- **Brak testów automatycznych** — trzymamy konwencję repo (weryfikacja ręczna per faza); dług testowy spłacany w Module 3.
- **Brak zmian w publicznych zapytaniach GET** poza tym, co wynika z mutacji danych (edycja/usuwanie widoczne natychmiast, bez cache).

## Implementation Approach

Cienki pion przez trzy warstwy, faza po fazie, każda z bramką ręcznej weryfikacji:

1. **Backend** dokłada dwa endpointy na istniejący zasób `/api/news-posts/{id}` (`PUT`, `DELETE`), pierwszą metodę mutującą na encji, migrację V6 (`updated_at`), DTO `UpdateRequest` i dwa matchery bezpieczeństwa. Zero warstwy serwisowej — spójnie z obecnym stylem kontrolera.
2. **Frontend** dokłada dwie metody do `NewsApi`, nowy komponent edycji na trasie `admin/edit/:id` (reużywa konwencje formularza z `AdminPanel`) oraz gated akcje Edytuj/Usuń na `NewsDetail` z dwustopniowym potwierdzeniem usunięcia.
3. **Produkcja** — merge wyzwala auto-deploy; weryfikacja rewizji `plomien-api` i e2e na publicznym URL.

## Critical Implementation Details

- **Kolejność zmian encji vs migracja (ddl-auto=validate):** produkcyjny profil uruchamia Hibernate z `validate`, więc nowa kolumna `updated_at` w encji **musi** mieć odpowiadającą migrację V6, inaczej start aplikacji padnie na walidacji schematu. Testy używają H2 create-drop (schemat generowany z encji), więc migracja V6 nie jest wykonywana w CI — dodanie kolumny nie wymaga zmian w `src/test/resources/application.properties` (nie dochodzi żadna *wymagana property*).
- **Matchery metod w SecurityConfig:** `GET /api/news-posts/**` jest `permitAll`, ale to reguła tylko-GET. `PUT` i `DELETE` na `/api/news-posts/*` muszą dostać osobne matchery z `.hasRole("ADMIN")` **przed** `denyAll()`, inaczej zwrócą 403. Użyj wzorca ścieżki jednosegmentowej (`/api/news-posts/*`), spójnie z `{id}`.
- **Dirty-state przy ładowaniu formularza edycji:** w odróżnieniu od `AdminPanel` (gdzie `patchValue` z generacji AI nadpisywał ręczne zmiany — znany warning F8 z S-03), formularz edycji wypełnia pola **raz** po wczytaniu posta, zanim użytkownik zacznie pisać; nie ma tu asynchronicznego nadpisania po interakcji.

## Phase 1: Backend — endpointy edit/delete

### Overview

Dodaj `PUT` i `DELETE` na `/api/news-posts/{id}`, pierwszą metodę mutującą encji, migrację V6 (`updated_at`), DTO `UpdateRequest` i matchery bezpieczeństwa. Bez warstwy serwisowej.

### Changes Required:

#### 1. Migracja V6 — kolumna `updated_at`

**File**: `backend/src/main/resources/db/migration/V6__news_posts_updated_at.sql`

**Intent**: Dodaje nullową kolumnę śledzącą czas ostatniej edycji, wymaganą przez `ddl-auto=validate` po zmianie encji.

**Contract**: `ALTER TABLE news_posts ADD COLUMN updated_at TIMESTAMPTZ;` — nullable (istniejące wiersze mają NULL = nigdy nie edytowane). Bez indeksu, bez zmian w CHECK V5.

#### 2. Encja — metoda mutująca `edit(...)` + pole `updatedAt`

**File**: `backend/src/main/java/com/plomienkostrze/news/NewsPost.java`

**Intent**: Wprowadza pierwszą i jedyną ścieżkę mutacji, zachowując styl immutable (jedna metoda intencyjna zamiast setterów). Edycja zmienia tylko tytuł i treść oraz ustawia znacznik edycji; **nie** dotyka `status` ani `publishedAt`.

**Contract**: nowe pole `private Instant updatedAt` z `@Column(name = "updated_at")` (nullable) + getter; metoda `public void edit(String title, String content)` ustawiająca `this.title`, `this.content` i `this.updatedAt = Instant.now()`. Brak publicznych setterów.

#### 3. Endpointy `PUT` i `DELETE` + DTO `UpdateRequest`

**File**: `backend/src/main/java/com/plomienkostrze/web/NewsPostController.java`

**Intent**: Wystawia edycję (pełne zastąpienie tytułu+treści) i usunięcie po id, reużywając wzorzec inline-404 i konwencję DTO-jako-`record`.

**Contract**:
- `record UpdateRequest(@NotBlank @Size(max=200) String title, @NotBlank @Size(max=10000) String content)` — lustro `CreateRequest`.
- `PUT /api/news-posts/{id}` → `@Valid UpdateRequest` → `findById(id)` else `ResponseStatusException(NOT_FOUND, "news post not found")`; `post.edit(title, content)`; `repository.save(post)`; zwraca `DetailResponse.from(saved)` (200).
- `DELETE /api/news-posts/{id}` → jeśli `!repository.existsById(id)` → `ResponseStatusException(NOT_FOUND, "news post not found")`; inaczej `repository.deleteById(id)`; zwraca `204 No Content` (`ResponseEntity<Void>` lub `@ResponseStatus(NO_CONTENT)`).

#### 4. Reguły bezpieczeństwa dla `PUT`/`DELETE`

**File**: `backend/src/main/java/com/plomienkostrze/security/SecurityConfig.java`

**Intent**: Chroni oba nowe endpointy rolą admina; bez tego `denyAll()` zwróci 403.

**Contract**: przed `.anyRequest().denyAll()` dodać `.requestMatchers(HttpMethod.PUT, "/api/news-posts/*").hasRole("ADMIN")` i `.requestMatchers(HttpMethod.DELETE, "/api/news-posts/*").hasRole("ADMIN")`.

### Success Criteria:

#### Automated Verification:

- Backend buduje się i przechodzi testy: `cd backend && ./mvnw -B verify`
- Migracja V6 waliduje się względem encji (start aplikacji na profilu produkcyjnym nie zgłasza błędu walidacji schematu) — potwierdzone lokalnym `./mvnw spring-boot:run` przy podłączonej bazie (lub w fazie 3 na prod).

#### Manual Verification:

- `PUT /api/news-posts/{id}` bez tokenu → 401; z tokenem nie-admina → 403.
- `PUT /api/news-posts/{id}` z tokenem admina i poprawnym body → 200 + zaktualizowany `DetailResponse`; pusty/za długi tytuł lub treść → 400.
- `PUT` na nieistniejący id → 404.
- `DELETE /api/news-posts/{id}` bez tokenu → 401; nie-admin → 403; admin na istniejący id → 204; admin na nieistniejący id → 404.
- Po `PUT` publiczne `GET /api/news-posts/{id}` zwraca nową treść; `publishedAt` bez zmian (post nie zmienił pozycji na liście). Po `DELETE` publiczne `GET /api/news-posts/{id}` → 404, a lista go nie zawiera.

**Implementation Note**: Po ukończeniu fazy i przejściu weryfikacji automatycznej zatrzymaj się i poczekaj na ręczne potwierdzenie testów (curl) zanim przejdziesz do fazy 2. Bloki faz używają zwykłych punktorów — checkboxy `- [ ]` są w sekcji `## Progress`.

---

## Phase 2: Frontend — UI edycji i usuwania (admin)

### Overview

Dodaj metody `update()`/`delete()` do `NewsApi`, nowy komponent edycji na trasie `admin/edit/:id` oraz gated akcje Edytuj/Usuń na publicznym widoku szczegółu, z dwustopniowym potwierdzeniem usunięcia.

### Changes Required:

#### 1. Metody API `update()` i `delete()`

**File**: `frontend/src/app/news/news-api.ts`

**Intent**: Jedyny punkt HTTP zyskuje edycję i usuwanie; interceptor autoryzuje je automatycznie.

**Contract**: `update(id: number, title: string, content: string): Observable<NewsPost>` → `PUT ${base}/${id}` z body `{ title, content }`. `delete(id: number): Observable<void>` → `DELETE ${base}/${id}`. Nazewnictwo i styl jak `create()`/`get()`.

#### 2. Komponent edycji + trasa

**File**: `frontend/src/app/admin/post-edit.ts` (+ `.html`, `.scss`), trasa w `frontend/src/app/app.routes.ts`

**Intent**: Dedykowany widok edycji reużywający konwencje formularza z `AdminPanel` (reactive form, walidacja, progres „…", błąd inline) i gating admina.

**Contract**:
- Trasa lazy `loadComponent`: `admin/edit/:id` → `PostEdit`. Walidacja `:id` jako dodatniej liczby (wzorzec `news-detail.ts:39-43`); niepoprawny/nieistniejący → komunikat + powrót.
- Gating jak w `AdminPanel`: efekt przekierowujący gdy `user === null` lub `isAdmin === false`; render dopiero gdy `ready()`.
- Po wczytaniu `NewsApi.get(id)` — jednorazowe wypełnienie formularza (`title`, `content`; te same walidatory: `required`, `maxLength 200/10000`).
- Submit → `NewsApi.update(id, title, content)`; sukces → `router.navigate(['/news', id])`; błąd → sygnał `error` z HTTP status (wzorzec `admin-panel.ts:96`). Przycisk `disabled` + etykieta „Zapisywanie… / Zapisz".

#### 3. Akcje Edytuj/Usuń na widoku szczegółu

**File**: `frontend/src/app/news/news-detail.ts` (+ `.html`, `.scss`)

**Intent**: Udostępnia adminowi wejście do edycji i usuwanie z dwustopniowym potwierdzeniem inline; niewidoczne dla gościa.

**Contract**:
- Wstrzyknąć `AdminStatus`; akcje renderowane tylko gdy `isAdmin() === true`.
- **Edytuj**: `routerLink` do `['/admin/edit', id]`.
- **Usuń**: dwustopniowo — sygnał `confirmingDelete`; klik „Usuń" → pokaż „Na pewno? Tak / Anuluj"; „Tak" → `NewsApi.delete(id)`, na sukces `router.navigate(['/'])`; „Anuluj" → reset sygnału. Stan `deleting` blokuje przycisk („Usuwanie…"); błąd inline `<p class="error">` z HTTP status.

### Success Criteria:

#### Automated Verification:

- Frontend buduje się produkcyjnie: `cd frontend && npm run build`
- Lint/format przechodzi (jeśli skonfigurowany w repo): `cd frontend && npm run lint` (pominąć, jeśli skrypt nie istnieje).

#### Manual Verification:

- Uruchomiony lokalnie backend (`spring-boot:run`) + `npm start`; zalogowany jako admin (UID na allowliście): na `/news/:id` widać Edytuj i Usuń; jako gość (incognito) — nie widać.
- Edytuj → formularz wypełniony bieżącą treścią → zmiana → Zapisz → powrót na `/news/:id` z nową treścią; gość w incognito widzi zmianę bez logowania.
- Usuń → „Na pewno?" → Anuluj nie usuwa; Tak → powrót na listę, wpis znika z listy i z `/news/:id` (404-friendly) także dla gościa.
- Walidacja formularza edycji: pusty tytuł/treść blokuje Zapisz; przekroczenie limitów obsłużone.
- Postęp widoczny (>~2 s): etykiety „Zapisywanie…"/„Usuwanie…" i zablokowane przyciski w trakcie operacji.

**Implementation Note**: Po ukończeniu fazy i weryfikacji automatycznej zatrzymaj się na ręczne potwierdzenie e2e lokalnego przed fazą 3.

---

## Phase 3: Produkcja — deploy i weryfikacja e2e

### Overview

Merge do `master` wyzwala auto-deploy (backend Cloud Run `plomien-api`, frontend Firebase Hosting). Potwierdź rewizję i przeprowadź e2e na publicznym URL.

### Changes Required:

#### 1. Merge i auto-deploy

**File**: — (proces; PR robi użytkownik, zgodnie z konwencją repo)

**Intent**: Wypuszcza obie zmiany na produkcję ścieżką path-filtrowanych workflowów.

**Contract**: PR z brancha `M2L4-gatedNewsGeneration` (lub bieżącego) do `master`; po merge workflowy backend/frontend budują, testują i deployują. **Push i PR wykonuje wyłącznie użytkownik.**

### Success Criteria:

#### Automated Verification:

- CI (GitHub Actions) zielone dla obu jobów (backend build+test, frontend build) na PR i po merge.
- Rewizja Cloud Run zaktualizowana: `gcloud run services describe plomien-api --region europe-central2` pokazuje nową, aktywną rewizję.

#### Manual Verification:

- Na produkcyjnym URL, zalogowany jako admin: edycja opublikowanego wpisu zapisuje się i jest natychmiast widoczna publicznie (incognito).
- Usunięcie wpisu na produkcji: wpis znika z publicznej listy i szczegółu (gość widzi 404-friendly).
- Nie-admin/gość nie widzi akcji Edytuj/Usuń i nie może wywołać endpointów (403/401 przy próbie bezpośredniej).
- Brak regresji: publiczne czytanie listy/szczegółu i tworzenie/generacja wpisów działają jak wcześniej.

**Implementation Note**: To ostatnia faza — po weryfikacji produkcyjnej slice jest gotowy do `/10x-archive`.

---

## Testing Strategy

Zgodnie z konwencją repo (S-01→S-03): **brak nowych testów automatycznych**; jakość pilnowana przez buildy, istniejący `contextLoads()` i ręczną weryfikację per faza. Dług testowy jest świadomy i spłacany w Module 3.

### Unit Tests:

- Brak nowych (konwencja). `contextLoads()` nadal musi przechodzić po zmianie encji (H2 create-drop generuje schemat z encji, więc `updated_at` pojawia się automatycznie; brak nowych wymaganych property do zdublowania w `src/test/resources/application.properties`).

### Integration Tests:

- Brak nowych (konwencja).

### Manual Testing Steps:

1. Backend (faza 1): curl dla 401/403/404/200/204 na `PUT`/`DELETE` (patrz Success Criteria fazy 1).
2. Frontend (faza 2): lokalny e2e admin+gość — edycja, usunięcie z potwierdzeniem, walidacja, widoczność akcji tylko dla admina.
3. Produkcja (faza 3): e2e na publicznym URL + `gcloud run services describe plomien-api`.

## Performance Considerations

- Publiczne GET-y nie przechodzą przez weryfikację JWT i nie mają cache — edycja/usunięcie są widoczne natychmiast, bez inwalidacji.
- Operacje admina (>~2 s) sygnalizowane wzorcem „przycisk disabled + etykieta …", spójnie z NFR PRD o widocznym postępie.
- Brak nowych zapytań ciężkich: `PUT` to `findById` + `save`, `DELETE` to `existsById` + `deleteById`.

## Migration Notes

- **V6** dodaje nullową `updated_at`; istniejące wiersze dostają NULL (semantyka: nigdy nie edytowane). Migracja nieodwracalna w ramach Flyway forward-only, ale addytywna i bezpieczna.
- CHECK `chk_news_posts_published_at` (V5) pozostaje spełniony — edycja nie zmienia `status`/`published_at`.
- Usuwanie jest fizyczne; brak migracji potrzebnej dla delete.

## References

- Roadmap slice: `context/foundation/roadmap.md` (S-04), PRD FR-007/FR-008 (`context/foundation/prd.md:117-120`).
- Wzorce backendu: `backend/src/main/java/com/plomienkostrze/web/NewsPostController.java`, `.../news/NewsPost.java`, `.../security/SecurityConfig.java:66-77`, `db/migration/V5__news_posts_published_check.sql`.
- Wzorce frontendu: `frontend/src/app/news/news-api.ts`, `frontend/src/app/admin/admin-panel.ts`, `frontend/src/app/news/news-detail.ts`, `frontend/src/app/auth/admin-status.ts`, `frontend/src/app/app.routes.ts`.
- Konwencje: `frontend/CLAUDE.md`, `backend/CLAUDE.md` (autorytatywne nad skillem `frontend`).
- Poprzednie slice: `context/archive/2026-07-06-manual-news-publishing/`, `context/archive/2026-07-08-gated-news-generation/` (auth e2e, konwencja testów, nazwa usługi `plomien-api`).

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Backend — endpointy edit/delete

#### Automated

- [x] 1.1 Backend buduje się i przechodzi testy: `cd backend && ./mvnw -B verify`
- [x] 1.2 Migracja V6 waliduje się względem encji (brak błędu walidacji schematu przy starcie)

#### Manual

- [x] 1.3 PUT bez tokenu → 401; nie-admin → 403
- [x] 1.4 PUT admin poprawne body → 200 + zaktualizowany DetailResponse; niepoprawne → 400
- [x] 1.5 PUT na nieistniejący id → 404
- [x] 1.6 DELETE bez tokenu → 401; nie-admin → 403; admin istniejący → 204; admin nieistniejący → 404
- [x] 1.7 Po PUT publiczny GET zwraca nową treść, publishedAt bez zmian; po DELETE GET → 404 i brak na liście

### Phase 2: Frontend — UI edycji i usuwania (admin)

#### Automated

- [ ] 2.1 Frontend buduje się produkcyjnie: `cd frontend && npm run build`
- [ ] 2.2 Lint/format przechodzi (jeśli skonfigurowany): `cd frontend && npm run lint`

#### Manual

- [ ] 2.3 Akcje Edytuj/Usuń widoczne tylko dla admina (gość w incognito ich nie widzi)
- [ ] 2.4 Edycja: formularz wypełniony → zmiana → Zapisz → powrót na /news/:id z nową treścią widoczną dla gościa
- [ ] 2.5 Usuwanie: „Na pewno?" → Anuluj nie usuwa; Tak → powrót na listę, wpis znika także dla gościa
- [ ] 2.6 Walidacja formularza edycji (pusty/za długi tytuł/treść) blokuje Zapisz
- [ ] 2.7 Postęp widoczny: etykiety „Zapisywanie…"/„Usuwanie…" i zablokowane przyciski

### Phase 3: Produkcja — deploy i weryfikacja e2e

#### Automated

- [ ] 3.1 CI zielone dla obu jobów na PR i po merge
- [ ] 3.2 `gcloud run services describe plomien-api --region europe-central2` pokazuje nową aktywną rewizję

#### Manual

- [ ] 3.3 Edycja opublikowanego wpisu na produkcji natychmiast widoczna publicznie
- [ ] 3.4 Usunięcie wpisu na produkcji: znika z publicznej listy i szczegółu
- [ ] 3.5 Nie-admin/gość nie widzi akcji i nie może wywołać endpointów (403/401)
- [ ] 3.6 Brak regresji w czytaniu listy/szczegółu i tworzeniu/generacji wpisów
