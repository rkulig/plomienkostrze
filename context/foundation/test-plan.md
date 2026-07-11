# Test Plan

> Phased test rollout for this project. Strategy is frozen at the top
> (§1–§5); cookbook patterns at the bottom (§6) fill in as phases ship.
> Read before writing any new test.
>
> Refresh: re-run `/10x-test-plan --refresh` when stale (see §8).
>
> Last updated: 2026-07-11

## 1. Strategy

Testy w tym projekcie kierują się trzema nienegocjowalnymi zasadami:

1. **Cost × signal.** Wygrywa najtańszy test, który daje realny sygnał dla
   ryzyka. Nie promuj do e2e, bo e2e „wydaje się bezpieczniejsze". Nie
   nakładaj modelu wizyjnego na deterministyczny diff, który regresję i tak
   łapie.
2. **Obawy użytkownika to dowód pierwszej klasy.** Ryzyka zakotwiczone w
   „zespół boi się X, a porażka ujawni się gdzieś w obszarze <area>" mają tę
   samą wagę co linie PRD czy dane o churnie.
3. **Ryzyka to scenariusze, nie lokalizacje w kodzie.** Ten plan dokumentuje
   *co może się zepsuć* i *dlaczego uważamy to za prawdopodobne* — z
   dokumentów, wywiadu i *sygnału* z kodu (churn, struktura, baza testów).
   NIE twierdzi, że wie, która linia jest właścicielem porażki. Tę wiedzę
   produkuje `/10x-research` w każdej fazie rolloutu. Jeśli plan i research
   różnią się co do miejsca porażki, ground truth jest research.

Hot-spot scope użyty do ważenia prawdopodobieństwa: `frontend/src`,
`backend/src/main` (z pominięciem scaffoldu `test-flow`, dokumentów, migracji
jako narzędzia).

## 2. Risk Map

Najważniejsze scenariusze porażki, które projekt musi chronić, uszeregowane
wg risk = impact × likelihood. Ryzyka to scenariusze porażki w kategoriach
użytkownika/biznesu, nie nazwy testów. Kolumna Source cytuje *dowód, który
wyniósł ryzyko na wierzch* — nigdy konkretny plik jako „miejsce porażki" (to
zadanie research, zob. §1 zasada #3).

| # | Ryzyko (scenariusz porażki) | Impact | Likelihood | Source (dowód — nie anchor) |
|---|------------------------------|--------|------------|------------------------------|
| 1 | Gość / zalogowany kibic sięga po operacje admina (create / edit / delete / generate / publish) — bramka sprawdza „czy zalogowany", a nie „czy Twoja rola" *(abuse: authorization)* | High | High | PRD §Access Control + §Guardrails; interview Q2, Q3; hot-spot `backend/src/main/.../security` (3 commity), `frontend/src/app` (`app.routes.ts` 7×) |
| 2 | Propozycja / draft trafia do publicznych aktualności bez jawnej akceptacji admina — złamany guardrail „system nigdy nie publikuje sam" | High | Medium | PRD §Guardrails + US-01 AC; hot-spot `backend/src/main/.../news` (status wpisu); migracja `V5 published_check` |
| 3 | Migracja Flyway (changeset) niekompatybilna wstecz / psuje dane na prodzie — Cloud Run cofa rewizję, baza nie | High | Medium | interview Q2 (przeżyte); `tech-stack-backend.md` (forward-only + rollback caveat); `backend/src/main/resources/db/migration` (6 migracji) |
| 4 | Regresja CORS/Security blokuje legalny ruch (front dostaje 401/403 wszędzie) albo cicho rozluźnia bramkę | Medium | Medium | interview Q2, Q3; hot-spot `backend/src/main/.../security`, `backend/src/main/.../web` (CORS) |
| 5 | Ścieżka generowania publikuje zmyślony wynik zamiast czystego błędu, gdy brak danych meczowych *(kontrakt deterministyczny, nie jakość prozy LLM)* | High | Medium | PRD S-03 risk „chude wejście"; roadmap decyzja 2026-07-08; hot-spot `backend/src/main/.../news` |
| 6 | Scraper 90minut.pl łamie się na zmianie HTML/kodowania (`ISO-8859-2`) — generacja dostaje cicho błędne dane wejściowe | Medium | Medium | roadmap decyzja 2026-07-08; `context/archive/2026-07-08-gated-news-generation/research-scraping-90minut.md`; hot-spot `backend/src/main/.../news` |
| 7 | Wyciek sekretu: `OPENROUTER_API_KEY` lub token Firebase w logach albo ciele odpowiedzi błędu *(abuse: secret leakage)* | Medium | Low | abuse lens; `tech-stack-backend.md` (Secret Manager, klucz montowany do Cloud Run przez `--set-secrets`) |

**Rubryka Impact × Likelihood.** Oba wymiary w skali High / Medium / Low, by
dwóch czytelników zgodziło się co do wiersza. Bez drobniejszych gradacji —
celem jest kolejność, nie fałszywa precyzja.

| Ocena | Impact | Likelihood |
|-------|--------|------------|
| High   | użytkownik traci dostęp/dane/pieniądze; porażka publicznie widoczna | obszar zmieniany co tydzień lub już się tu sparzyliśmy |
| Medium | funkcja degraduje, jest obejście, część użytkowników dotknięta | ruszany okazjonalnie, bywał źródłem bugów |
| Low    | kosmetyka, łatwo cofnąć, brak wpływu na dane | kod stabilny, rzadko ruszany |

Kolejność wg impact × likelihood. Chronić High × High najpierw. Ryzyka
High-impact × Low-likelihood (np. awaria dostawcy chmury) należą zwykle do
obserwowalności/alertingu, nie do testu — zob. §7.

### Risk Response Guidance

| Risk | Co dowodzi ochrony | Co zakwestionować | Kontekst do ugruntowania przez `/10x-research` | Najtańsza warstwa | Anti-pattern do uniknięcia |
|------|--------------------|-------------------|-----------------------------------------------|-------------------|----------------------------|
| #1 | Gość i zalogowany kibic dostają 403 na każdym write / generate / publish / delete; tylko admin przechodzi | „happy-path logowania admina dowodzi, że kibic jest odcięty" | punkt wejścia każdego chronionego endpointu, kształt roli/claimu admina, mapowanie tokenu na autoryzację | integration (MockMvc, macierz ról × endpoint) | testować wyłącznie ścieżkę admina; over-mock filtra bezpieczeństwa |
| #2 | Publiczny odczyt zwraca wyłącznie `PUBLISHED`; generacja tworzy draft, niczego nie publikując | „201/200 z generacji znaczy, że wpis jest widoczny publicznie" | przejście statusu draft→published, zapytanie listy publicznej, gdzie egzekwowany jest gate | integration + test zapytania repo | assert skopiowany z logiki statusu (oracle problem) |
| #3 | Pełny łańcuch migracji stawia się na czystym Postgresie i zachowuje istniejące dane | „migracja przeszła na H2/staging, więc przejdzie na prod PG" | kolejność migracji, ograniczenia (`published_check`, `updated_at`), zgodność wstecz z poprzednią rewizją app | integration (Testcontainers Postgres) | testować przeciw H2/in-memory zamiast realnego PG |
| #4 | Preflight/CORS przepuszcza legalny origin, a chroniony endpoint dalej wymaga ważnego tokenu | „ustawiony nagłówek CORS = auth działa" | konfiguracja CORS, kolejność filtrów, które ścieżki są `permitAll` vs chronione | integration (MockMvc) | assert na samym nagłówku bez sprawdzenia ścieżki auth |
| #5 | Brak danych meczowych → czysty błąd (`MatchDataUnavailableException` / 4xx-5xx), zero auto-publikacji; nic zmyślonego nie ląduje jako draft/published | „200 znaczy, że treść jest prawdziwa" | granica klienta LLM i klienta scrapera, co dzieje się przy pustych/niekompletnych danych, gdzie powstaje draft | integration z zamockowaną granicą sieci (LLM + scraper) | asertować wygenerowany tekst słowo-w-słowo (oracle problem) |
| #6 | Parser na utrwalonym fixture HTML (`ISO-8859-2`) daje poprawne pola; na zepsutym/zmienionym HTML — jawny błąd, nie cicha zła wartość | „strona się nie zmieni; polskie znaki zdekodują się same" | struktura HTML 90minut.pl, kodowanie `ISO-8859-2`, mapowanie pól (data, rozgrywki, gospodarz, gość, wynik) | unit/contract z zapisanym fixture | uderzać w żywą stronę w teście (flaky, zależność sieciowa) |
| #7 | Ciało odpowiedzi błędu i logi na ścieżce awaryjnej nie zawierają klucza OpenRoutera ani tokenu Firebase | „framework nie loguje sekretów domyślnie" | ścieżka obsługi wyjątków, co trafia do ciała odpowiedzi i logów przy błędzie LLM/auth | integration ścieżki błędu | testować tylko happy-path; zakładać, że handler nie echuje wejścia |

## 3. Phased Rollout

Każdy wiersz to odrębna faza rolloutu, która otworzy własny change folder
przez `/10x-new`. Status przesuwa się od lewej do prawej; orchestrator
aktualizuje Status, gdy artefakty pojawiają się na dysku.

| # | Phase name | Goal (one line) | Risks covered | Test types | Status | Change folder |
|---|------------|-----------------|---------------|------------|--------|---------------|
| 1 | Backend harness + bramka dostępu i publikacji | Postawić runner backendu i dowieść macierz ról oraz gate publikacji na najtańszej warstwie; wpiąć CI gate „backend tests" | #1, #2, #4 | integration (MockMvc), @WebMvcTest | researched | context/changes/testing-backend-access-publish-gate/ |
| 2 | Warstwa danych i granice zewnętrzne | Migracje na realnym Postgresie, kontrakt scrapera, deterministyczny kontrakt błędu generacji (brak auto-publikacji) | #3, #6, #5 | integration (Testcontainers), contract/fixture | not started | — |
| 3 | Wyciek sekretu + utwardzenie security gate | Sekrety nie wyciekają w błędach/logach; zablokować testy bezpieczeństwa jako wymaganą bramkę CI | #7 | integration ścieżki błędu | not started | — |
| 4 | Frontend — testy podstawowe | Bootstrap Vitest (Angular 22 `@angular/build:unit-test`) + smoke: publiczna lista pokazuje tylko `PUBLISHED`, guard panelu admina | #1, #2 (poziom widoku) | unit/component (Vitest + TestBed) | not started | — |

**Status vocabulary** (fixed — parser literals): `not started` → `change
opened` → `researched` → `planned` → `implementing` → `complete`.

| Value | Meaning |
|-------|---------|
| `not started` | Brak change foldera dla tej fazy. |
| `change opened` | Istnieje `context/changes/<id>/` z `change.md`; brak research. |
| `researched` | `research.md` istnieje w change folderze. |
| `planned` | `plan.md` istnieje z sekcją `## Progress`. |
| `implementing` | Sekcja Progress ma min. jeden `[x]` i min. jeden `[ ]`. |
| `complete` | Sekcja Progress w całości `[x]`. |

## 4. Stack

Klasyczna baza testów tego projektu. Narzędzia AI-native (jeśli są) noszą datę
`checked:`. Rekomendacje ugruntowane w lokalnych manifestach/configach oraz w
MCP/narzędziach faktycznie wystawionych w bieżącej sesji.

| Layer | Tool | Version | Notes |
|-------|------|---------|-------|
| unit + integration (backend) | JUnit 5 + Mockito + MockMvc | via `spring-boot-starter-webmvc-test` | **Już obecny** w `backend/pom.xml`; jedyny istniejący test to context-load. |
| integration/DB (backend) | Testcontainers (PostgreSQL) | none yet — see Phase 2 | Realny PG dla migracji i integracji; nie H2 (różnice PG istotne dla `published_check`). |
| assertions (backend) | AssertJ | via starter | Fluent assertions, płynne z MockMvc. |
| unit/component (frontend) | Vitest (`@angular/build:unit-test`) + `TestBed` | none yet — see Phase 4 | Domyślny runner Angulara od v21 (potwierdzone Context7 2026-07-11). Front celowo minimalny. |
| e2e | — | — | Poza zakresem MVP (cienki front). `ng add playwright-ng-schematics` dostępne, gdyby zaszła potrzeba. |

Karma świadomie **odrzucony** — deprecated w Angular 22 (wciąż wspierany dla
migracji, ale nowy kod idzie na Vitest).

**Stack grounding tools (current session):**
- Docs: Context7 — zweryfikowano domyślny runner testów Angular 22 (Vitest przez `@angular/build:unit-test`, Karma deprecated) oraz opcje e2e (`ng add` schematics); checked: 2026-07-11
- Search: Exa.ai — dostępne w sesji, nieużyte (stack w pełni ugruntowany z manifestów + Context7); checked: 2026-07-11
- Runtime/browser: brak w sesji — not used; checked: 2026-07-11
- Provider/platform: brak w sesji (GitHub/GCP CLI poza tą sesją) — not used; checked: 2026-07-11

## 5. Quality Gates

Pełen zestaw bramek, które muszą przejść, zanim zmiana trafi na produkcję.
„Required after §3 Phase <N>" oznacza egzekwowanie po wdrożeniu tej fazy;
wcześniej bramka jest `planned`.

| Gate | Where | Required? | Catches |
|------|-------|-----------|---------|
| build + typecheck (Maven `verify`, `ng build`) | local + CI | required | dryf składni/typów, błędy kompilacji |
| unit + integration (backend) | local + CI | required after §3 Phase 1 | regresje logiki, bramka dostępu, gate publikacji |
| integration DB (Testcontainers) | CI on PR | required after §3 Phase 2 | migracje psujące schemat/dane, kontrakt scrapera |
| security tests (authz + wyciek sekretu) | CI on PR | required after §3 Phase 3 | rozluźnienie bramki, wyciek klucza/tokenu |
| unit/component (frontend) | local + CI | required after §3 Phase 4 | regresje widoku published-only, guard admina |
| post-edit hook | local (agent loop) | optional | regresje w czasie edycji |
| pre-prod smoke | between merge + prod | optional | awarie specyficzne dla środowiska (Cloud Run/Cloud SQL) |

## 6. Cookbook Patterns

Jak dodawać nowe testy w tym projekcie. Każda podsekcja wypełnia się, gdy
odpowiednia faza rolloutu wejdzie; wcześniej brzmi „TBD — see §3 Phase <N>".

### 6.1 Adding a backend unit test

- TBD — see §3 Phase 1.

### 6.2 Adding a backend integration test (MockMvc / role matrix)

- TBD — see §3 Phase 1.

### 6.3 Adding a DB/migration integration test (Testcontainers)

- TBD — see §3 Phase 2.

### 6.4 Adding a contract/fixture test for an external boundary (scraper / LLM)

- TBD — see §3 Phase 2.

### 6.5 Adding a frontend component/smoke test (Vitest + TestBed)

- TBD — see §3 Phase 4.

### 6.6 Per-rollout-phase notes

(Opcjonalne. Po wejściu każdej fazy `/10x-implement` dopisuje tu 2–3 linie z
tym, czego faza nauczyła — np. lokalizacja fixture'ów, wspólny helper.)

## 7. What We Deliberately Don't Test

Wyłączenia uzgodnione podczas rolloutu (interview Q5). Przyszli kontrybutorzy
mają je respektować, dopóki założenie się nie zmieni.

- **Jakość prozy LLM / pomiar progu 75% akceptacji** — nie test
  deterministyczny (oracle problem: wartość oczekiwana wzięta z implementacji
  jest tautologią). To przyszły eval AI-native, nie unit test. Re-evaluate,
  gdy powstanie warstwa oceny generacji. (Source: challenger pass, PRD S-03.)
- **Ciężkie testy frontendu** (rozbudowane component / e2e / visual) —
  świadomie poza zakresem; front dostaje tylko podstawowy smoke. Re-evaluate,
  gdy front urośnie o interakcje kibiców (forum, reakcje). (Source: interview Q5.)
- **Scaffold `test-flow` / endpointy `ping`, `test-message`** — resztki
  bootstrapu do usunięcia; nie testować. (Source: hot-spot scan + interview Q5.)
- **Rate-limiting endpointu generacji** — obserwowalność/alerting, nie test
  (admin-only za bramką #1, kilka generacji/tydz., niski blast radius).
  Re-evaluate, gdy generacja zostanie wystawiona szerzej. (Source: challenger pass.)
- **Pełny scraping składów/strzelców z laczynaspilka.pl** — parked feature (API
  za reCAPTCHA v3, niezbudowane). Nic do testowania. (Source: roadmap §Parked.)

## 8. Freshness Ledger

- Strategy (§1–§5) last reviewed: 2026-07-11
- Stack versions last verified: 2026-07-11
- AI-native tool references last verified: 2026-07-11

Refresh (`/10x-test-plan --refresh`) when:

- a new top-3 risk surfaces from the roadmap or archive,
- a recommended tool's `checked:` date is older than three months,
- the project's tech stack changes (new framework, new test runner),
- §7 negative-space no longer matches what the team believes.
