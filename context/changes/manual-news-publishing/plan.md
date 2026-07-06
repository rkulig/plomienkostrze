# Logowanie administratora + ręczne tworzenie i publikacja wpisów — Implementation Plan

## Overview

Roadmap S-02 (`manual-news-publishing`, issue #10): administrator loguje się kontem
Google przez Firebase Authentication, tworzy wpis aktualności ręcznie i publikuje go
jednym krokiem — wpis natychmiast widoczny w publicznych aktualnościach z S-01
(PRD: FR-001, FR-006, §Access Control, NFR „logowanie wyłącznie przez zewnętrznego
dostawcę; brak haseł w aplikacji").

To pierwszy slice wprowadzający warstwę cross-cutting security: backend staje się
bezstanowym resource serverem weryfikującym Firebase ID tokeny (JWT) przez publiczne
klucze JWKS Google, a SPA dostaje pierwszy chroniony widok i pierwszy endpoint zapisu.

## Current State Analysis

- **Zero auth w kodzie.** Backend nie ma spring-security ani żadnej biblioteki
  Firebase (`backend/pom.xml`); frontend nie ma `firebase` ani `@angular/fire`
  (`frontend/package.json`, grep w lockfile: 0 trafień).
- **Read-only API newsów działa** (S-01): `GET /api/news-posts` (paginacja
  `{ items, total }`, tylko `PUBLISHED`, sort `published_at DESC`) i
  `GET /api/news-posts/{id}` (404 dla nieistniejących i nieopublikowanych) —
  `backend/src/main/java/com/plomienkostrze/web/NewsPostController.java`.
- **Encja przygotowana pod rozbudowę**: `NewsPost` jest immutable-style (gettery,
  `protected` konstruktor JPA, brak publicznego konstruktora — trzeba dodać fabrykę);
  `NewsPostStatus` ma tylko `PUBLISHED`, projektowany pod dopisywanie wartości;
  `published_at` nullable z założenia (przyszłe szkice).
- **Twardy wymóg z przeglądu S-01**
  (`context/changes/public-news-reading/follow-ups/review-fixes.md`, F3): akcja
  publikacji **musi ustawiać `published_at`** — wiersz `PUBLISHED` z NULL-em
  sortowałby się pierwszy (Postgres `DESC` = NULLS FIRST) i renderował pustą datę.
  Rekomendowany dodatkowo CHECK w migracji S-02.
- **Frontend ma czyste szwy**: `NewsApi` (`frontend/src/app/news/news-api.ts`) jako
  jedyne miejsce HTTP (konwencja z docstringa), lazy routes bez guardów
  (`app.routes.ts` — wildcard `**` na końcu), nagłówek+main w `app.html` (stopki brak),
  Firebase config nieobecny w `environments/`.
- **Deploy/konfiguracja**: auto-deploy po merge do `master` (path-filtered); env vars
  na Cloud Run ustawiane ręcznie `gcloud run services update` i **persystują między
  rewizjami** (wzór: `ALLOWED_ORIGINS`); migracje forward-only, muszą być
  backward-compatible (V1–V4 zajęte, zaczynamy od V5).
- **Testy**: brak (decyzja S-01 podtrzymana w tym planie — patrz „What We're NOT
  Doing"); jedyny test to `contextLoads()` na H2;
  `src/test/resources/application.properties` **shadowuje w całości** główny plik —
  nowe wymagane properties potrzebują tam bezpiecznych wartości.

## Desired End State

Administrator wchodzi na `https://plomien-kostrze.web.app`, klika przycisk „Zaloguj"
w nagłówku, loguje się kontem Google (redirect), po powrocie widzi w nagłówku
„Wyloguj" oraz — jako admin — „Dodaj post". Klika „Dodaj post", wypełnia formularz
nowego wpisu (tytuł + treść), klika „Opublikuj" — i zostaje przekierowany na
`/news/:id` opublikowanego wpisu, który od razu widzi każdy gość bez logowania.
Zalogowana osoba spoza allowlisty widzi w nagłówku tylko „Wyloguj" (bez „Dodaj
post", bez komunikatu); wejście deep-linkiem na `/admin` bez uprawnień kończy się
przekierowaniem na `/`; żądanie `POST /api/news-posts` bez ważnego tokenu admina
kończy się 401/403. Publiczne czytanie aktualności działa bez żadnych zmian.

### Key Discoveries:

- Tech-stack przesądza mechanikę weryfikacji: backend = stateless resource server,
  token z nagłówka `Authorization` weryfikowany przez JWKS Google — bez sesji i bez
  Firebase Admin SDK (`context/foundation/tech-stack-backend.md:93-101`).
- Mechanizm roli admina był jawnie odłożony do tego planu („custom claim lub DB
  allowlist") — rozstrzygnięcie: **allowlist UID w konfiguracji** (`ADMIN_UIDS`),
  najprostszy wariant dla jednego administratora; wzór env-var już istnieje
  (`app.cors.allowed-origins=${ALLOWED_ORIGINS:...}` w `application.properties`).
- Firebase ID tokeny to standardowe JWT RS256: issuer
  `https://securetoken.google.com/plomien-kostrze`, audience `plomien-kostrze`,
  JWKS pod `https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com`.
- Admin auth jest **in-app**, nie przez Cloud Run IAM — serwis pozostaje
  `--allow-unauthenticated` (`context/foundation/infrastructure.md:230`).
- Uniform 404 dla nieopublikowanych wpisów w publicznym detalu (S-01 impl-review) —
  ten slice niczego tu nie zmienia i nie może zmienić.

## What We're NOT Doing

- **Żadnych testów automatycznych nowego kodu** (decyzja użytkownika, konsekwentnie
  z S-01) — bramką są buildy, istniejący `contextLoads()` i weryfikacja manualna.
  Dług testowy (w tym odłożone z S-01 testy MockMvc odczytu) pozostaje otwarty.
- Żadnego statusu `DRAFT` ani zapisu szkiców — publikacja jest jednokrokowa; S-03
  zdefiniuje własny przepływ propozycji.
- Żadnej edycji/usuwania wpisów (S-04) i generowania (S-03).
- Żadnego logowania kibiców, custom claims, tabeli adminów w DB — jeden administrator
  przez `ADMIN_UIDS`.
- Żadnej listy wpisów w panelu admina — panel to wyłącznie formularz nowego wpisu;
  weryfikacja wizualna na publicznych widokach.
- Żadnej podmiany treści seedowanej z S-01 — to działanie operacyjne admina po
  wdrożeniu (usuwanie starych wpisów dopiero w S-04).
- Żadnego Firebase Auth Emulatora — dev używa prawdziwego projektu `plomien-kostrze`.
- Żadnego rate-limitingu / audit logu / CSRF (API bezstanowe na bearer tokenach).

## Implementation Approach

Backend najpierw (Faza 1): Spring Security jako resource server z ręcznie złożonym
`JwtDecoder` (JWKS + jawne walidatory issuer/audience), rola `ROLE_ADMIN` nadawana
gdy `sub` tokenu jest na liście `ADMIN_UIDS`, endpoint `POST /api/news-posts`
tworzący wpis od razu jako `PUBLISHED` z `published_at = now()`, pomocniczy
`GET /api/me` dla stanów UI, migracja V5 z CHECK-iem. Publiczne GET-y pozostają
`permitAll` — zachowanie z S-01 bez zmian.

Potem frontend (Faza 2): czysty SDK `firebase` (bez `@angular/fire` — zero sprzężenia
wersji z Angular 22; potrzebujemy tylko modułu auth) opakowany w jeden serwis z
sygnałami — lustrzana konwencja do `NewsApi` („jedyne miejsce dotykające Firebase").
Token doklejany interceptorem HTTP. Wejście admina żyje w nagłówku: gość widzi
„Zaloguj", zalogowany „Wyloguj", a admin (status z `GET /api/me`) dodatkowo „Dodaj
post" prowadzący do `/admin`. `/admin` to jeden lazy komponent z samym formularzem —
bez osobnego guarda; wejście bez uprawnień przekierowuje na `/`, a obrona jest
w backendzie.

Na końcu produkcja (Faza 3): jednorazowe kroki ludzkie w konsoli Firebase i na Cloud
Run, deploy przez merge, weryfikacja e2e.

## Critical Implementation Details

- **Nazwy artefaktów Spring Boot 4**: pom już ma pułapkę modularizacji (komentarz
  przy `spring-boot-flyway`). Przy dodawaniu security zweryfikuj faktyczne nazwy
  starterów dla Boot 4.1 (`spring-boot-starter-security`,
  `spring-boot-starter-oauth2-resource-server`, `spring-boot-starter-validation` —
  jeśli któryś nie istnieje pod tą nazwą, sprawdź release notes Boot 4 zamiast
  zgadywać).
- **`jwk-set-uri`, nie `issuer-uri`**: dekoder z `issuer-uri` robi OIDC discovery
  eagerly przy starcie kontekstu — `contextLoads()` na H2 bez sieci by padł. Dekoder
  budowany z JWKS URI pobiera klucze lazily przy pierwszym tokenie; issuer i audience
  walidujemy jawnie w bean-ie.
- **Shadowing test-properties**: `src/test/resources/application.properties`
  zastępuje główny plik w całości — każda nowa property czytana bez defaultu w kodzie
  musi mieć tam wpis. Najbezpieczniej: wszystkie nowe properties z defaultami w
  `${VAR:default}` i lustrzane wpisy w pliku testowym.
- **CORS × Security**: preflight OPTIONS dla `POST` z nagłówkiem `Authorization` musi
  przechodzić bez auth — w łańcuchu security włącz integrację CORS
  (`http.cors(withDefaults())`), żeby istniejący `CorsConfig` (poziom MVC) dalej
  działał; zweryfikuj preflight manualnie w Fazie 2.
- **`signInWithRedirect` a domeny**: prod `authDomain` ustawiamy na
  `plomien-kostrze.web.app` — redirect jest wtedy same-origin z hostowaną aplikacją
  (obejście blokad 3rd-party storage). Konsekwencja: kanoniczny adres produkcyjny to
  `web.app`; logowanie na `firebaseapp.com` może nie działać w restrykcyjnych
  przeglądarkach. Na localhost (dev, `authDomain=plomien-kostrze.firebaseapp.com`)
  redirect jest cross-origin — w razie problemów w danej przeglądarce fallback to
  jednolinijkowa zmiana na `signInWithPopup` (odnotować, nie przebudowywać).
- **Kolejność tras**: `admin` musi wejść do `app.routes.ts` przed wildcardem `**`
  (który przekierowuje na `''`).
- **Sekwencja zdobycia UID admina**: allowlist wymaga UID, a UID powstaje przy
  pierwszym logowaniu. Kolejność w Fazie 2: najpierw logowanie (brak przycisku
  „Dodaj post" jest wtedy oczekiwany — nie-admin nie dostaje żadnego komunikatu,
  więc literówkę w UID diagnozuje się przez konsolę Firebase, nie przez UI), potem
  odczyt UID z konsoli Firebase (Authentication → Users), ustawienie `ADMIN_UIDS`
  lokalnie i restart backendu.
- **Status admina jako wspólny sygnał**: nagłówek („Dodaj post") i redirect w
  `/admin` potrzebują tego samego stanu — jeden serwis woła `GET /api/me` raz po
  każdej zmianie zalogowanego użytkownika i wystawia sygnał
  `isAdmin: boolean | null` (`null` = nierozstrzygnięte). Redirect w `/admin`
  reaguje wyłącznie na rozstrzygnięte `false` — przekierowanie przy `null`
  wyrzucałoby admina w trakcie ładowania statusu.

## Phase 1: Backend — security + endpoint publikacji

### Overview

Backend zaczyna weryfikować Firebase ID tokeny i wystawia pierwszy chroniony endpoint
zapisu. Publiczne zachowanie z S-01 bez zmian.

### Changes Required:

#### 1. Zależności security + validation

**File**: `backend/pom.xml`

**Intent**: Dodać wsparcie resource servera (weryfikacja JWT) i Bean Validation dla
DTO zapisu. Bez Firebase Admin SDK.

**Contract**: `spring-boot-starter-security`, `spring-boot-starter-oauth2-resource-server`,
`spring-boot-starter-validation` (nazwy zweryfikować dla Boot 4.1 — patrz Critical
Implementation Details).

#### 2. Konfiguracja security

**File**: `backend/src/main/java/com/plomienkostrze/security/SecurityConfig.java` (nowy)

**Intent**: Bezstanowy łańcuch security: publiczne GET-y newsów, ping i health bez
auth; `/api/me` dla zalogowanych; `POST /api/news-posts` tylko dla `ROLE_ADMIN`;
weryfikacja Firebase ID tokenów; rola z allowlisty UID.

**Contract**: `SecurityFilterChain` — `csrf.disable()`, sesje `STATELESS`,
`cors(withDefaults())`; reguły: `permitAll` dla `GET /api/news-posts/**`,
`GET /api/ping`, `/actuator/health`; `authenticated` dla `GET /api/me`;
`hasRole("ADMIN")` dla `POST /api/news-posts`; `anyRequest().denyAll()`.
`JwtDecoder` budowany z JWKS URI z jawnymi walidatorami (bo `jwk-set-uri` sam ich
nie ustawia):

```java
NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(
    new JwtIssuerValidator("https://securetoken.google.com/" + projectId),
    new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, aud -> aud != null && aud.contains(projectId))));
```

`JwtAuthenticationConverter` nadaje `ROLE_ADMIN`, gdy `sub` tokenu jest na liście z
property `app.admin.uids` (rozdzielanej przecinkami, jak `app.cors.allowed-origins`).

#### 3. Properties

**File**: `backend/src/main/resources/application.properties`

**Intent**: Nowe właściwości z bezpiecznymi defaultami — pusta allowlist znaczy
„nikt nie jest adminem", nie „każdy".

**Contract**: `app.admin.uids=${ADMIN_UIDS:}`, `app.firebase.project-id=plomien-kostrze`,
`app.firebase.jwk-set-uri=https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com`.

#### 4. Test properties (shadowing)

**File**: `backend/src/test/resources/application.properties`

**Intent**: Lustrzane wpisy nowych properties, żeby `contextLoads()` dalej przechodził
(plik testowy shadowuje główny w całości).

**Contract**: te same trzy klucze z wartościami bez env-var (`app.admin.uids=` puste).

#### 5. Fabryka publikacji na encji

**File**: `backend/src/main/java/com/plomienkostrze/news/NewsPost.java`

**Intent**: Pierwsza ścieżka tworzenia wpisu w kodzie. Fabryka gwarantuje inwariant
z przeglądu S-01: wpis `PUBLISHED` zawsze ma `published_at`.

**Contract**: statyczna fabryka `NewsPost.published(String title, String content)` —
ustawia `status = PUBLISHED` i `publishedAt = Instant.now()`; bez setterów,
`createdAt` zostaje w `@PrePersist`.

#### 6. Endpoint publikacji

**File**: `backend/src/main/java/com/plomienkostrze/web/NewsPostController.java`

**Intent**: `POST /api/news-posts` tworzy i publikuje wpis jednym krokiem (FR-006).

**Contract**: request `{ "title": string, "content": string }` z walidacją
(`@NotBlank`, `@Size(max=200)` / `@Size(max=10000)` — spójnie z kolumnami i encją);
response `201` z istniejącym `DetailResponse`; błędy walidacji → `400` (domyślny
mechanizm Bean Validation, zgodnie z konwencją `ResponseStatusException`/ProblemDetail).

#### 7. Endpoint statusu zalogowanego

**File**: `backend/src/main/java/com/plomienkostrze/web/MeController.java` (nowy)

**Intent**: SPA musi wiedzieć, czy zalogowany użytkownik jest adminem (allowlist
żyje tylko po stronie backendu) — do przełączania stanów widoku `/admin`.

**Contract**: `GET /api/me` (wymaga tokenu) → `200` `{ "admin": boolean }`;
bez tokenu → `401` (z łańcucha security).

#### 8. Migracja CHECK

**File**: `backend/src/main/resources/db/migration/V5__news_posts_published_check.sql` (nowy)

**Intent**: Domknięcie inwariantu F3 z przeglądu S-01 na poziomie bazy (defense in
depth obok fabryki). Addytywna, backward-compatible — poprzednia rewizja nie ma
ścieżki zapisu, a wszystkie wiersze seedowe mają `published_at`.

**Contract**:

```sql
ALTER TABLE news_posts
    ADD CONSTRAINT chk_news_posts_published_at
    CHECK (status <> 'PUBLISHED' OR published_at IS NOT NULL);
```

### Success Criteria:

#### Automated Verification:

- Build przechodzi: `cd backend && ./mvnw -DskipTests package`
- Istniejące testy przechodzą (contextLoads z security na H2, bez sieci): `./mvnw test`

#### Manual Verification:

- Lokalny start (`./mvnw spring-boot:run`): log Flyway pokazuje zastosowane V5
- `GET /api/news-posts` i `GET /api/news-posts/{id}` bez tokenu → `200` (bez regresji S-01)
- `POST /api/news-posts` bez tokenu → `401`; ze śmieciowym tokenem → `401`
- `GET /api/me` bez tokenu → `401`
- `GET /api/ping` i `GET /actuator/health` bez tokenu → `200`

**Implementation Note**: Ścieżki `200`/`403` z prawdziwym tokenem weryfikujemy w
Fazie 2 (token wymaga SPA). Po zakończeniu fazy i przejściu weryfikacji automatycznej
zatrzymaj się na bramce manualnej przed Fazą 2.

---

## Phase 2: Frontend — logowanie + panel admina

### Overview

SPA dostaje logowanie Google (redirect), interceptor tokenu, przyciski auth w
nagłówku („Zaloguj" / „Wyloguj" / „Dodaj post" dla admina) i widok `/admin` z
formularzem publikacji. Faza kończy się pełnym lokalnym e2e.

### Changes Required:

#### 0. Kroki ludzkie (prerequisite fazy)

**Intent**: Dev używa prawdziwego projektu Firebase — konfiguracja musi istnieć
przed kodem.

**Contract**: w konsoli Firebase projektu `plomien-kostrze`: (a) zarejestrować
aplikację webową (jeśli nie istnieje) i skopiować config (`apiKey`, `authDomain`,
`projectId` — `apiKey` Firebase jest publiczny, może być w repo), (b) włączyć
dostawcę logowania Google (Authentication → Sign-in method), (c) sprawdzić, że
`localhost` jest na Authorized domains (jest domyślnie).

#### 1. Zależność firebase

**File**: `frontend/package.json`

**Intent**: Czysty SDK `firebase` (tylko moduł `firebase/auth` będzie importowany) —
bez `@angular/fire`, żeby nie sprzęgać wersji z Angular 22.

**Contract**: `npm install firebase` (pamiętaj o `nvm use`).

#### 2. Konfiguracja środowisk

**File**: `frontend/src/environments/environment.ts`, `environment.production.ts`

**Intent**: Firebase config obok istniejącego `apiBaseUrl`, wg wzoru `fileReplacements`.

**Contract**: pole `firebase: { apiKey, authDomain, projectId }`;
dev: `authDomain: 'plomien-kostrze.firebaseapp.com'`;
prod: `authDomain: 'plomien-kostrze.web.app'` (same-origin redirect — patrz Critical
Implementation Details).

#### 3. Serwis Auth

**File**: `frontend/src/app/auth/auth-service.ts` (nowy)

**Intent**: Jedyne miejsce w SPA dotykające SDK Firebase (lustrzana konwencja do
`NewsApi` dla HTTP). Stan logowania jako sygnał.

**Contract**: klasa `AuthService` (`providedIn: 'root'`): inicjalizuje
`initializeApp(environment.firebase)` + `onAuthStateChanged`; wystawia sygnał
`user` (null = wylogowany, undefined/loading do rozstrzygnięcia stanem „ładowanie"),
metody `signInWithGoogle()` (`signInWithRedirect` + `GoogleAuthProvider`),
`signOut()`, `getIdToken(): Promise<string | null>`.

#### 4. Interceptor tokenu

**File**: `frontend/src/app/auth/auth-interceptor.ts` (nowy) + `frontend/src/app/app.config.ts`

**Intent**: Każde żądanie do API backendu wychodzi z `Authorization: Bearer <token>`,
gdy użytkownik jest zalogowany; publiczne żądania bez zmian, gdy nie jest.

**Contract**: funkcyjny `HttpInterceptorFn` — dokleja token tylko dla URL-i
zaczynających się od `environment.apiBaseUrl`; rejestracja przez
`provideHttpClient(withInterceptors([...]))` w `app.config.ts`.

#### 5. Serwis MeApi + status admina + rozszerzenie NewsApi

**File**: `frontend/src/app/auth/me-api.ts` (nowy),
`frontend/src/app/auth/admin-status.ts` (nowy), `frontend/src/app/news/news-api.ts`

**Intent**: Status admina z backendu (allowlist niewidoczna dla SPA) jako wspólny
sygnał dla nagłówka i widoku `/admin`, plus pierwsza metoda zapisu w istniejącym
serwisie HTTP.

**Contract**: `MeApi.get(): Observable<{ admin: boolean }>` → `GET /api/me`;
`AdminStatus` (`providedIn: 'root'`) reaguje na sygnał `user` z `AuthService`:
po zalogowaniu woła `GET /api/me` raz i wystawia sygnał
`isAdmin: boolean | null` (`null` = nierozstrzygnięte / wylogowany — reset);
`NewsApi.create(title, content): Observable<NewsPost>` → `POST /api/news-posts`.

#### 6. Widok /admin (formularz)

**File**: `frontend/src/app/admin/admin-panel.ts` + `.html` + `.scss` (nowe),
`frontend/src/app/app.routes.ts`

**Intent**: Jeden lazy komponent z samym formularzem publikacji — logowanie żyje
w nagłówku, obrona w backendzie. Deep-link bez uprawnień przekierowuje na `/`.
Formularz = minimalny zakres FR-006.

**Contract**: trasa `admin` (przed wildcardem `**`); komponent `AdminPanel`
(standalone, sygnały): obserwuje `AdminStatus.isAdmin` — przy rozstrzygniętym
`false` `router.navigate([''])` (nie przy `null` — patrz Critical Implementation
Details); przy `true` reactive form: tytuł (`required`, `maxlength 200`), treść
(`required`, `maxlength 10000`, textarea; czysty tekst, akapity pustymi liniami —
format z S-01), przycisk „Opublikuj" (disabled w trakcie wysyłki), obsługa błędu
API (komunikat, treść formularza nie ginie); po sukcesie
`router.navigate(['/news', id])`.

#### 7. Przyciski auth w nagłówku

**File**: `frontend/src/app/app.html` (+ `app.ts`, `app.scss`)

**Intent**: Jawne wejście przez nagłówek (decyzja z przeglądu planu — zamiast
wcześniejszego dyskretnego linku „Admin" w stopce): gość widzi „Zaloguj",
zalogowany „Wyloguj", admin dodatkowo „Dodaj post".

**Contract**: prawa strona istniejącego `header.app-header`; stan z sygnałów
`AuthService.user` i `AdminStatus.isAdmin`:
- wylogowany → przycisk „Zaloguj" (`signInWithGoogle()`);
- zalogowany → przycisk „Wyloguj" (`signOut()`);
- zalogowany admin (`isAdmin === true`) → dodatkowo link „Dodaj post"
  (`routerLink="/admin"`).

### Success Criteria:

#### Automated Verification:

- Build produkcyjny przechodzi: `cd frontend && nvm use && npm run build`

#### Manual Verification:

- Lokalne e2e (backend `spring-boot:run` + `npm start`): „Zaloguj" w nagłówku →
  redirect logowania Google → powrót z „Wyloguj" w nagłówku
- Sekwencja UID: po pierwszym logowaniu brak przycisku „Dodaj post" (oczekiwany —
  UID spoza pustej allowlisty); skopiowanie UID z konsoli Firebase,
  `ADMIN_UIDS=<uid>` lokalnie, restart backendu → „Dodaj post" w nagłówku →
  formularz na `/admin`
- Publikacja: wypełnienie formularza → „Opublikuj" → redirect na `/news/:id`,
  wpis na liście `/` jako pierwszy (poprawna data)
- Walidacja: puste pola blokują wysyłkę; komunikat błędu przy odpowiedzi ≠ 2xx,
  treść formularza nie ginie
- Nie-admin: po usunięciu UID z lokalnej `ADMIN_UIDS` i restarcie — brak „Dodaj
  post" w nagłówku, deep-link `/admin` przekierowuje na `/`, „Wyloguj" działa
  (powrót do „Zaloguj")
- Preflight CORS: `POST` z `Authorization` przechodzi z `localhost:4200` (brak
  błędów CORS w konsoli)
- Bez regresji publicznych widoków: lista i szczegół działają bez logowania;
  gość nie widzi żadnych zmian poza przyciskiem „Zaloguj" w nagłówku

**Implementation Note**: Po zakończeniu fazy i przejściu weryfikacji zatrzymaj się
na bramce manualnej przed Fazą 3.

---

## Phase 3: Produkcja — konfiguracja + weryfikacja e2e

### Overview

Jednorazowa konfiguracja produkcyjna (konsola Firebase zrobiona w Fazie 2; zostaje
Cloud Run), deploy przez merge i weryfikacja pełnego przepływu na produkcji.

### Changes Required:

#### 1. ADMIN_UIDS na Cloud Run (krok ludzki)

**Intent**: Produkcyjna allowlist admina — env var persystuje między rewizjami
(CI nie nadpisuje flag serwisu).

**Contract**: `gcloud run services update plomien-api --region europe-central2
--update-env-vars ADMIN_UIDS=<uid-admina>` (ten sam wzór co `ALLOWED_ORIGINS`;
UID nie jest sekretem — env var wystarczy, bez Secret Managera).

#### 2. Authorized domains (krok ludzki, weryfikacja)

**Intent**: Redirect logowania musi być dozwolony z domeny produkcyjnej.

**Contract**: w konsoli Firebase Authentication → Authorized domains muszą być
`plomien-kostrze.web.app` i `plomien-kostrze.firebaseapp.com` (są domyślnie —
tylko sprawdzić).

#### 3. Deploy i weryfikacja

**Intent**: Merge do `master` uruchamia auto-deploy obu aplikacji (zmiany w
`frontend/**` i `backend/**`); weryfikacja e2e na produkcji.

**Contract**: PR → review → merge; obserwacja obu workflow GitHub Actions.

### Success Criteria:

#### Automated Verification:

- CI zielone na PR (build+test obu aplikacji); po merge oba workflow deploy przechodzą

#### Manual Verification:

- `https://plomien-kostrze.web.app` → „Zaloguj" → logowanie Google → „Dodaj post"
  → formularz → publikacja wpisu → redirect na `/news/:id`
- Nowy wpis widoczny na publicznej liście bez logowania (inna przeglądarka/incognito)
- `curl -X POST https://plomien-api-….run.app/api/news-posts -d '{}' -H 'Content-Type: application/json'` → `401`
- Publiczne czytanie bez regresji: lista, szczegół, paginacja na produkcji
- (Opcjonalnie) logowanie innym kontem Google → brak „Dodaj post" w nagłówku

---

## Testing Strategy

Zgodnie z decyzją użytkownika: **bez nowych testów automatycznych** w tym slice
(konsekwentnie z S-01). Bramki jakości: buildy obu aplikacji, istniejący
`contextLoads()` (który po dodaniu security weryfikuje, że kontekst z resource
serverem wstaje na H2 bez sieci) oraz manualne kryteria per faza powyżej.

Dług testowy narasta świadomie: publiczny odczyt (odłożone z S-01) + cała warstwa
security i zapisu (ten slice). Naturalny moduł na spłatę: Moduł 3 kursu (strategia
testów) lub osobny change.

### Manual Testing Steps:

Pełne scenariusze wpisane w Success Criteria faz — kluczowa ścieżka:

1. Gość: lista `/` → szczegół `/news/:id` — bez logowania, bez regresji.
2. Admin: „Zaloguj" w nagłówku → login Google → „Dodaj post" → formularz →
   „Opublikuj" → `/news/:id`.
3. Nie-admin: login spoza allowlisty → brak „Dodaj post" w nagłówku, deep-link
   `/admin` → redirect na `/` → „Wyloguj".
4. API wprost: `POST` bez tokenu → `401`; walidacja → `400`.

## Performance Considerations

Bez wpływu na NFR „pierwsza treść < 2 s" — publiczne GET-y nie przechodzą przez
weryfikację JWT (permitAll; filtr bearer aktywuje się tylko przy obecnym nagłówku).
Pierwsze żądanie z tokenem po starcie instancji pobiera JWKS (dziesiątki ms,
cache'owane) — pomijalne dla operacji admina. `min-instances` bez zmian w tym slice
(flip na 1 zaplanowany przy S-03, zgodnie z `infrastructure.md`).

## Migration Notes

V5 jest addytywna (CHECK) i backward-compatible: poprzednia rewizja aplikacji jest
read-only, a wszystkie istniejące wiersze spełniają warunek (seed ma `published_at`).
Rollback rewizji Cloud Run nie wymaga cofania migracji.

## References

- Identyfikacja slice'a: `context/foundation/roadmap.md` (S-02), issue
  [#10](https://github.com/rkulig/plomienkostrze/issues/10)
- Decyzja IdP i mechanika weryfikacji: `context/foundation/tech-stack-backend.md:93-101`
- Twardy wymóg `published_at`: `context/changes/public-news-reading/follow-ups/review-fixes.md`
- Kontrakt API odczytu (bez zmian): `context/changes/public-news-reading/plan.md` (Faza 1)
- Wzorce konfiguracji Cloud Run: `context/changes/deployment/deployment-plan.md`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Backend — security + endpoint publikacji

#### Automated

- [x] 1.1 Build przechodzi: `./mvnw -DskipTests package` — 89eb59b
- [x] 1.2 Istniejące testy przechodzą na H2 bez sieci: `./mvnw test` — 89eb59b

#### Manual

- [x] 1.3 Flyway stosuje V5 przy lokalnym starcie — 89eb59b
- [x] 1.4 Publiczne GET-y newsów bez tokenu → 200 (bez regresji S-01) — 89eb59b
- [x] 1.5 `POST /api/news-posts` bez tokenu i ze śmieciowym tokenem → 401 — 89eb59b
- [x] 1.6 `GET /api/me` bez tokenu → 401 — 89eb59b
- [x] 1.7 `GET /api/ping` i `/actuator/health` bez tokenu → 200 — 89eb59b

### Phase 2: Frontend — logowanie + panel admina

#### Automated

- [x] 2.1 Build produkcyjny przechodzi: `npm run build` — e85b759

#### Manual

- [x] 2.2 Lokalne e2e: „Zaloguj" w nagłówku → redirect Google → powrót z „Wyloguj" — e85b759
- [x] 2.3 Sekwencja UID: brak „Dodaj post" → UID z konsoli → `ADMIN_UIDS` → „Dodaj post" → formularz — e85b759
- [x] 2.4 Publikacja: formularz → `/news/:id`, wpis pierwszy na liście z poprawną datą — e85b759
- [x] 2.5 Walidacja formularza + obsługa błędu API bez utraty treści — e85b759
- [x] 2.6 Nie-admin: brak „Dodaj post", deep-link `/admin` → redirect na `/`, „Wyloguj" działa — e85b759
- [x] 2.7 Preflight CORS dla POST z Authorization przechodzi z localhost:4200 — e85b759
- [x] 2.8 Bez regresji publicznych widoków (gość nie widzi zmian poza „Zaloguj" w nagłówku) — e85b759

### Phase 3: Produkcja — konfiguracja + weryfikacja e2e

#### Automated

- [x] 3.1 CI zielone na PR; po merge oba workflow deploy przechodzą

#### Manual

- [x] 3.2 `ADMIN_UIDS` ustawione na Cloud Run (gcloud, krok ludzki)
- [x] 3.3 Authorized domains zweryfikowane w konsoli Firebase
- [x] 3.4 Prod e2e: login na `web.app` → „Dodaj post" → publikacja → wpis publicznie widoczny
- [x] 3.5 `POST /api/news-posts` bez tokenu na prod → 401
- [x] 3.6 Publiczne czytanie bez regresji na produkcji
