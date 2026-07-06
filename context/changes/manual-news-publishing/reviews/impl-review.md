<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Logowanie administratora + ręczne tworzenie i publikacja wpisów

- **Plan**: context/changes/manual-news-publishing/plan.md
- **Scope**: Phase 1–2 of 3 (Faza 3 w toku: 3.4 i 3.6 otwarte)
- **Date**: 2026-07-06
- **Verdict**: NEEDS ATTENTION
- **Findings**: 0 critical, 3 warnings, 5 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | PASS |
| Success Criteria | PASS |

## Context

- Dryf planu: 15/15 zaplanowanych pozycji MATCH, 0 DRIFT, 0 MISSING. Nazwa artefaktu
  `spring-boot-starter-security-oauth2-resource-server` to poprawna weryfikacja dla
  Boot 4.1, jawnie przewidziana w planie. `signInWithPopup` to zaakceptowany fallback
  udokumentowany w change.md — czysto zaimplementowany, bez pozostałości po redirect.
- Zweryfikowane jako bezpieczne (bez findingów): walidacja JWT (issuer, audience z
  null-guardem, exp/nbf, RS256), łańcuch autoryzacji z `denyAll()` jako default,
  pusta `ADMIN_UIDS` = nikt nie jest adminem, brak XSS (wyłącznie interpolacja
  Angulara), CORS bez `*` i bez credentials, limity formularza zgodne z `@Size`
  i kolumnami DB, migracja V5 nie wywali się na istniejących danych (seed ma
  `published_at`).
- Kryteria automatyczne: `./mvnw -DskipTests package` ✅, `./mvnw test` ✅
  (contextLoads na H2 bez sieci), `npm run build` ✅ (lazy chunk admin-panel).
- Extra poza listą plików planu: `plan-brief.md` (dokument kontekstu, benign),
  mikro-dodatki w plikach planowych (navigate-home po wylogowaniu, fail-closed
  `isAdmin=false` przy błędzie API, atrybuty `maxlength` w HTML) — zgodne z intencją.

## Findings

### F1 — Wyścig w AdminStatus: nieanulowane żądanie /api/me może nadpisać status admina

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: frontend/src/app/auth/admin-status.ts:20-30
- **Detail**: `effect()` odpala `meApi.get().subscribe()` przy każdej zmianie stanu auth, ale nie anuluje poprzedniego żądania w locie. Sekwencja: admin A loguje się → żądanie R1 w locie → A wylogowuje się, loguje się nie-admin B → R2 w locie → jeśli R1 rozwiąże się PO R2, `isAdmin` kończy jako `true` dla B. Skutek tylko w UI (backend i tak zwróci 403 na POST), ale to realny stale-write dokładnie w przepływie, dla którego sygnał powstał.
- **Fix**: Zamienić subscribe-w-effekcie na anulujący pipeline: `toObservable(this.auth.user).pipe(switchMap(user => user ? this.meApi.get() : of(null)))` — `switchMap` anuluje poprzednie żądanie przy zmianie użytkownika.
- **Decision**: SKIPPED

### F2 — Interceptor bez obsługi odrzucenia getIdToken() — błąd Firebase udaje HttpErrorResponse

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: frontend/src/app/auth/auth-interceptor.ts:18-22
- **Detail**: `user.getIdToken()` robi sieciowe odświeżenie tokenu, gdy cache wygasł (~co godzinę) i odrzuca przy awarii sieci. Odrzucenie propaguje przez `from(...)` jako `FirebaseError`, a konsumenci typują błąd jako `HttpErrorResponse` i interpolują `err.status` — panel admina (admin-panel.ts:57-58) pokazałby „Nie udało się opublikować wpisu (HTTP undefined)".
- **Fix**: `catchError` wokół pobrania tokenu z fallbackiem: wyślij żądanie bez tokenu — backend odpowie czystym 401, czyli kształtem błędu, który konsumenci już obsługują.
- **Decision**: SKIPPED

### F3 — Cicha porażka logowania: popup-blocker robi z „Zaloguj" martwy przycisk

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: frontend/src/app/auth/auth-service.ts:32-36
- **Detail**: `signInWithPopup(...).catch(err => console.error(...))` połyka najczęstszą realną porażkę: `auth/popup-blocked` / `auth/popup-closed-by-user`. Użytkownik klika „Zaloguj" i nic widocznego się nie dzieje. Każda inna granica błędu w tym feature pokazuje komunikat użytkownikowi — ta tylko loguje. Kontekst łagodzący: jedyny realny użytkownik logowania to jeden administrator.
- **Fix A ⭐ Recommended**: Wystawić sygnał błędu logowania w `AuthService` i wyrenderować komunikat w nagłówku (lustrzany wzór do sygnału `error` w admin-panel.ts).
  - Strength: Spójność z resztą granic błędów w feature; diagnozowalność bez konsoli.
  - Tradeoff: Dotyka trzech plików (serwis + app.html + app.ts) dla przepływu używanego przez jedną osobę.
  - Confidence: HIGH — wzór sygnału błędu już istnieje w admin-panel.ts.
  - Blind spot: Nie testowaliśmy realnego popup-blockera w przeglądarkach docelowych.
- **Fix B**: Zaakceptować jako ryzyko MVP (jeden admin, diagnoza przez konsolę) i odnotować.
  - Strength: Zero kodu; admin to persona techniczna z dostępem do konsoli.
  - Tradeoff: Martwy przycisk przy zablokowanym popupie pozostaje niezdiagnozowalny z UI.
  - Confidence: MEDIUM — zależy od przeglądarki/ustawień admina.
  - Blind spot: Nie wiemy, czy przeglądarka admina blokuje popupy.
- **Decision**: SKIPPED

### F4 — Przejściowy błąd /api/me po cichu wyrzuca prawdziwego admina z /admin

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: frontend/src/app/auth/admin-status.ts:28 + frontend/src/app/admin/admin-panel.ts:42-44
- **Detail**: Każdy błąd `/api/me` (chwilowy brak sieci, cold-start Cloud Run) rozwiązuje `isAdmin` do `false` i panel przekierowuje na `/` bez wyjaśnienia — nieodróżnialne od braku uprawnień. Fail-closed to dobry kierunek; rozróżnienie null-vs-false jest zaimplementowane poprawnie.
- **Fix**: Traktować błędy inne niż 401/403 jako „nierozstrzygnięte" (zostaw `null`) lub pokazać komunikat. Akceptowalne dla MVP — skip jest zasadny.
- **Decision**: SKIPPED

### F5 — Interceptor dopasowuje URL surowym prefiksem stringa

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Safety & Quality
- **Location**: frontend/src/app/auth/auth-interceptor.ts:14
- **Detail**: `req.url.startsWith(environment.apiBaseUrl)` bez trailing slasha dopasowałoby też `https://<api-host>.evil.example/...`. Dziś nieeksploatowalne — wszystkie żądania budują URL z tej samej stałej — czysty hardening.
- **Fix**: Porównywać `new URL(req.url).origin` z originem `apiBaseUrl` (albo prefiks `apiBaseUrl + '/'`).
- **Decision**: SKIPPED

### F6 — Constraint CHECK z V5 nigdy nie jest wykonywany w CI (H2 z wyłączonym Flyway)

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Success Criteria
- **Location**: backend/src/test/resources/application.properties:10
- **Detail**: Testy jadą na H2 z `spring.flyway.enabled=false`, więc inwariant `published_at NOT NULL dla PUBLISHED` w czasie testów chroni wyłącznie fabryka `NewsPost.published()`. Świadomy dług testowy (decyzja planu); jeśli w prod kiedykolwiek wstawiano wiersze ręcznie poza migracjami, rozważyć `NOT VALID` + `VALIDATE CONSTRAINT`.
- **Fix**: Brak działania teraz — spłata w Module 3 (strategia testów). Skip zasadny.
- **Decision**: SKIPPED

### F7 — backend/CLAUDE.md nieaktualny co do zależności

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: backend/CLAUDE.md
- **Detail**: Plik wciąż twierdzi „Dependencies are `web` (Spring MVC) + `devtools` only" — nieprawda od czasu JPA/Flyway, a teraz dodatkowo po dwóch starterach security. Dryf sprzed tej zmiany, pogłębiony przez nią.
- **Fix**: Zaktualizować zdanie albo usunąć enumerację zależności (przestaje się starzeć).
- **Decision**: FIXED — zdanie o zależnościach w backend/CLAUDE.md zaktualizowane (bez ruszania tripwire'a „no controllers yet", decyzja użytkownika)

### F8 — Skill `frontend` przeczy faktycznym konwencjom repo

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Pattern Consistency
- **Location**: .claude/skills/frontend/SKILL.md
- **Detail**: Skill nakazuje konwencje z innego codebase'u (interfejsy z prefiksem I, `Array<T>`, bez sygnałów dla stanu danych, bez `providedIn: 'root'`, warstwa `rest-api/`), którym kod S-01/S-02 i frontend/CLAUDE.md przeczą w każdym punkcie. Nowy kod słusznie poszedł za realnymi sąsiadami — ale któryś agent w końcu pójdzie za skillem i rozwidli styl.
- **Fix A ⭐ Recommended**: Przeredagować/oznaczyć skill jako „referencja zewnętrzna, nie reguły tego repo" albo usunąć go.
  - Strength: Usuwa sprzeczność u źródła, zanim wyprodukuje niespójny kod.
  - Tradeoff: Skill jest zarządzany przez 10x-cli (gitignored) — zmiana może zostać nadpisana przy aktualizacji.
  - Confidence: MEDIUM — nie wiemy, jak 10x-cli traktuje lokalne edycje skilli.
  - Blind spot: Pochodzenie i cel skilla w toolkicie kursu.
- **Fix B**: Zapisać jako regułę w lessons.md (`/10x-lesson`): „konwencje frontend/CLAUDE.md i istniejącego kodu wygrywają ze skillem frontend".
  - Strength: Przeżywa aktualizacje 10x-cli; lessons.md jest czytany przez łańcuch skilli 10x.
  - Tradeoff: Sprzeczność zostaje — reguła tylko ją neutralizuje.
  - Confidence: HIGH — dokładnie do tego służy lessons.md.
  - Blind spot: None significant.
- **Decision**: FIXED via Fix A — nagłówek skilla oznaczony jako referencja zewnętrzna z jawnym pierwszeństwem frontend/CLAUDE.md i istniejącego kodu. Uwaga: plik jest gitignored i zarządzany przez 10x-cli — aktualizacja skilli może nadpisać edycję.

## Success criteria verification

### Automated (uruchomione podczas przeglądu)

| Kryterium | Komenda | Wynik |
|---|---|---|
| 1.1 Build backendu | `./mvnw -DskipTests package` | ✅ PASS (exit 0) |
| 1.2 Testy backendu | `./mvnw test` | ✅ PASS (contextLoads na H2, bez sieci) |
| 2.1 Build produkcyjny frontendu | `npm run build` | ✅ PASS (lazy chunk admin-panel 43.66 kB) |

### Manual

Wszystkie pozycje manualne Faz 1–2 (1.3–1.7, 2.2–2.8) oznaczone `[x]` z SHA commitów.
Wiarygodne — nota w change.md o zmaterializowanym fallbacku popup dowodzi, że przepływ
logowania był realnie ćwiczony lokalnie (problem cross-origin wykryto empirycznie).
Faza 3: 3.4 (prod e2e) i 3.6 (regresja publiczna na prod) otwarte — poza zakresem
tego przeglądu, do domknięcia przed zamknięciem change'a.
