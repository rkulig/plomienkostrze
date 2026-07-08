<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: Gated News Generation (S-03)

- **Plan**: context/changes/gated-news-generation/plan.md
- **Scope**: Phase 1–2 of 3
- **Date**: 2026-07-08
- **Verdict**: APPROVED
- **Findings**: 0 critical, 1 warning, 8 observations

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | WARNING |
| Scope Discipline | WARNING |
| Safety & Quality | PASS (5 obserwacji) |
| Architecture | PASS |
| Pattern Consistency | PASS (2 obserwacje) |
| Success Criteria | PASS |

Kryteria automatyczne zweryfikowane w tym przeglądzie: `./mvnw test` offline bez
`OPENROUTER_API_KEY` — BUILD SUCCESS (1 test, 0 błędów); `cd frontend && npm run build` —
zielony (bundle 358.74 kB). Autoryzacja endpointu potwierdzona: matcher
`SecurityConfig.java:76` pokrywa dokładnie ścieżkę kontrolera, `anyRequest().denyAll()`
jako backstop. Timeouty obecne: jsoup 10 s, LLM 60 s przez `OpenAiHttpClientBuilderCustomizer`
(klasa potwierdzona w jarze spring-ai-openai-2.0.0). Bez zmian w schemacie bazy, bez XSS
(czysty tekst, brak innerHTML), bez sekretów w kodzie.

## Findings

### F1 — Faza 3 częściowo wykonana przedwcześnie; runbook niecommitowany, Progress niezsynchronizowany

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — realny tradeoff; warto się zatrzymać i przemyśleć
- **Dimension**: Plan Adherence
- **Location**: context/changes/deployment/deployment-plan.md (working tree, niecommitowane)
- **Detail**: Niecommitowana edycja runbooka to dokładnie punkt 3 fazy 3 (zmiana nazwy
  `LLM_API_KEY` → `OPENROUTER_API_KEY=openrouter-api-key:latest`, odhaczone checkboxy sekretu
  i flipa `min-instances`), ale runbook twierdzi, że **operacje gcloud z punktów 1–2 fazy 3
  już wykonano** (grant `secretAccessor`, konkretna rewizja `plomien-api-00010-x7d` z sekretem
  i `min-instances=1`, „done 2026-07-08") — przed merge'em i przed formalnym startem fazy 3
  (Progress 3.2/3.5 wciąż `[ ]`). Dodatkowo edycja zawiera nieplanowane rozstrzygnięcie
  redakcyjne („IdP client secret" uznany za bezprzedmiotowy), podczas gdy kontrakt punktu 3
  mówił tylko „poprawić nazwę i odhaczyć, bez zmian struktury". Treść edycji jest wierna
  fazie 3, a wcześniejsze wykonanie operacji jest rollback-safe z definicji planu — problem
  to rozjazd stanu: plan/Progress kłamią względem rzeczywistości, a zmiana wisi
  niecommitowana na branchu p1/p2.
- **Fix A ⭐ Recommended**: Zalegitymizować — potwierdzić stan `gcloud run services describe`,
  odhaczyć 3.2/3.5 w Progress, zanotować w change.md wcześniejsze wykonanie operacji,
  a edycję runbooka wcommitować jako commit fazy 3.
  - Strength: Dokumentacja wraca do zgodności z faktycznym stanem GCP; nic nie ginie.
  - Tradeoff: Faza 3 miesza się z przeglądem faz 1–2; sekwencja planu naruszona świadomie.
  - Confidence: HIGH — operacje są idempotentne i rollback-safe (pusty default klucza).
  - Blind spot: Nie zweryfikowano z tej sesji, że rewizja `plomien-api-00010-x7d` faktycznie
    istnieje — wymaga `gcloud` od użytkownika.
- **Fix B**: Wycofać zmiany robocze runbooka (`git checkout -- context/changes/deployment/deployment-plan.md`)
  i wrócić do nich formalnie przy fazie 3.
  - Strength: Czysta dyscyplina sekwencji faz.
  - Tradeoff: Jeśli operacje w GCP faktycznie wykonano, runbook przestaje odzwierciedlać
    rzeczywistość do czasu fazy 3.
  - Confidence: MEDIUM — zależy, czy operacje gcloud rzeczywiście już poszły.
  - Blind spot: Jak wyżej — stan GCP niezweryfikowany.
- **Decision**: FIXED via Fix A — stan GCP potwierdzony `gcloud describe` (rewizja
  `plomien-api-00010-x7d`, minScale=1, sekret `openrouter-api-key:latest`; usługa nazywa się
  `plomien-api`, nie `plomien-kostrze-api` z kontraktu planu); Progress 3.2/3.5 odhaczone,
  notka w change.md, commit `abacaeb`.

### F2 — Scrape po czystym HTTP (90minut.pl nie serwuje HTTPS)

- **Severity**: 👁 OBSERVATION
- **Impact**: 🏃 LOW — szybka decyzja; poprawka oczywista i wąska
- **Dimension**: Safety & Quality
- **Location**: backend/src/main/resources/application.properties:25
- **Detail**: Domyślny `base-url` to `http://www.90minut.pl` — scrapowane stringi trafiają do
  promptu LLM i draftu admina, więc podmiot on-path mógłby je podmienić. Zweryfikowano w tym
  przeglądzie: `https://www.90minut.pl` nie odpowiada (brak TLS po stronie źródła), HTTP
  zwraca 200 bez przekierowania — przejście na HTTPS jest niemożliwe. Ryzyko ograniczone
  bramką ludzką (admin recenzuje każdy draft) i env-nadpisywalnym URL-em.
- **Fix**: Zaakceptować ryzyko; dopisać do komentarza nad blokiem `app.ninetyminut.*`, że
  źródło nie oferuje TLS (świadoma akceptacja, mitygacja = przegląd ludzki).
- **Decision**: FIXED — komentarz o braku TLS i akceptacji ryzyka dopisany w
  application.properties (nad blokiem app.ninetyminut.*).

### F3 — NumberFormatException omija klasę błędu 424 i wychodzi jako 502

- **Severity**: 👁 OBSERVATION
- **Impact**: 🏃 LOW — szybka decyzja; poprawka oczywista i wąska
- **Dimension**: Safety & Quality
- **Location**: backend/src/main/java/com/plomienkostrze/news/NinetyMinutClient.java:103-104
- **Detail**: Regex wyniku dopuszcza nieograniczone ciągi cyfr; patologiczna komórka wyniku
  rzuci `NumberFormatException`, która ominie `MatchDataUnavailableException` i wyjdzie jako
  502 „news generation failed" zamiast 424 „match data unavailable". Skrajny przypadek, ale
  psuje czysty kontrakt dwóch statusów.
- **Fix**: Ograniczyć regex do `\d{1,3}` (lub złapać `NumberFormatException` w `parseRow`
  i pominąć wiersz).
- **Decision**: SKIPPED

### F4 — Szeroki catch(RuntimeException) maskuje bugi serwera jako 502

- **Severity**: 👁 OBSERVATION
- **Impact**: 🏃 LOW — szybka decyzja; poprawka oczywista i wąska
- **Dimension**: Safety & Quality
- **Location**: backend/src/main/java/com/plomienkostrze/web/NewsGenerationController.java:46-49
- **Detail**: NPE lub błąd konfiguracji w serwisie zwraca 502 nieodróżnialne od awarii modelu.
  Nic nie wycieka do klienta (dobrze), ale w monitoringu prawdziwe bugi nie odróżnią się od
  problemów upstreamu.
- **Fix**: Zawęzić catch do wyjątków Spring AI / klienta HTTP (np. `RestClientException`,
  wyjątki `org.springframework.ai`); pozostałe RuntimeException zostawić domyślnej obsłudze (500).
- **Decision**: FIXED — catch zawężony do `com.openai.errors.OpenAIException` (baza wszystkich
  błędów SDK: IO/timeout/401/rate-limit/5xx); NPE i bugi konfiguracji → domyślne 500.
  Świadomy koszt: malformed JSON z modelu (goły RuntimeException z BeanOutputConverter) → 500.

### F5 — Model i temperatura bez konwencji ${ENV:default}

- **Severity**: 👁 OBSERVATION
- **Impact**: 🏃 LOW — szybka decyzja; poprawka oczywista i wąska
- **Dimension**: Pattern Consistency
- **Location**: backend/src/main/resources/application.properties:28-29
- **Detail**: `spring.ai.openai.chat.model` i `chat.temperature` są zapisane na sztywno,
  podczas gdy sąsiednie właściwości tego samego bloku (api-key, base-url, team-id, season-id)
  używają `${ENV:default}`. Podmiana modelu (np. po deprecjacji sluga na OpenRouterze)
  wymaga redeployu zamiast zmiany env.
- **Fix**: Owinąć w konwencję repo: `${OPENROUTER_MODEL:anthropic/claude-sonnet-4.6}` i
  `${OPENROUTER_TEMPERATURE:0.7}`.
- **Decision**: SKIPPED

### F6 — Przeterminowany season-id generuje wiarygodne drafty o starym meczu

- **Severity**: 👁 OBSERVATION
- **Impact**: 🔎 MEDIUM — realny tradeoff; warto się zatrzymać i przemyśleć
- **Dimension**: Safety & Quality
- **Location**: backend/src/main/resources/application.properties:32 + NinetyMinutClient.java
- **Detail**: `season-id=107` trzeba ręcznie podbić raz na sezon (udokumentowane w komentarzu —
  zgodnie z planem). Po przełomie sezonu generacja po cichu zwróci ostatni mecz **poprzedniego**
  sezonu — draft będzie wiarygodny, tylko nieaktualny. Data jest w promptcie i draftcie, więc
  recenzujący admin może to wychwycić, ale nic nie wymusza świeżości.
- **Fix A ⭐ Recommended**: Tani guard świeżości — mecz starszy niż ~21 dni →
  `MatchDataUnavailableException` (424 z komunikatem o możliwym starym sezonie).
  - Strength: Kilka linii; zamienia cichy błąd merytoryczny w jawny błąd operacyjny —
    bezpośrednio służy progowi 75% akceptacji.
  - Tradeoff: Przerwa zimowa/letnia zablokuje generację ze starego meczu, nawet gdyby admin
    świadomie jej chciał.
  - Confidence: HIGH — logika daty już jest w kliencie (skip przyszłych meczów).
  - Blind spot: Dobór progu dni (przerwy między kolejkami bywają długie w niższych ligach).
- **Fix B**: Zostawić — polegać na adminie (data widoczna) i udokumentowanym bumpie sezonu.
  - Strength: Zero kodu; zgodne z minimalizmem planu (bump w configu był świadomą decyzją).
  - Tradeoff: Ryzyko opublikowania notki o meczu sprzed miesięcy, jeśli admin przeoczy datę.
  - Confidence: MEDIUM — zależy od uważności jedynego admina.
  - Blind spot: Brak danych, jak często admin czyta datę w draftcie.
- **Decision**: SKIPPED

### F7 — Pusty klucz API na produkcji zawodzi dopiero przy pierwszym kliknięciu

- **Severity**: 👁 OBSERVATION
- **Impact**: 🏃 LOW — szybka decyzja; poprawka oczywista i wąska
- **Dimension**: Safety & Quality
- **Location**: backend/src/main/resources/application.properties:24
- **Detail**: `${OPENROUTER_API_KEY:}` z pustym defaultem jest celowe (offline CI + wymóg
  rollback-safety z fazy 3: poprzednia rewizja musi działać bez sekretu — twarda asercja
  startowa złamałaby ten wymóg). Koszt: deploy bez sekretu ujawnia się dopiero 401→502 przy
  pierwszej generacji.
- **Fix**: Log WARN przy starcie, gdy klucz pusty („generacja newsów nieaktywna — brak
  OPENROUTER_API_KEY") — sygnał w logach bez łamania rollback-safety.
- **Decision**: SKIPPED

### F8 — Generacja nadpisuje ręczne edycje w formularzu bez potwierdzenia

- **Severity**: 👁 OBSERVATION
- **Impact**: 🏃 LOW — szybka decyzja; poprawka oczywista i wąska
- **Dimension**: Pattern Consistency
- **Location**: frontend/src/app/admin/admin-panel.ts:64
- **Detail**: `form.patchValue({title, content})` zastępuje wszystko, co admin już wpisał
  (ręczny szkic albo poprawioną poprzednią propozycję), bez ostrzeżenia. Dane żyją tylko
  w przeglądarce, więc strata jest nieodwracalna.
- **Fix**: Przed nadpisaniem niepustego, ręcznie zmodyfikowanego formularza (`form.dirty` +
  niepuste pola) — proste `confirm()` po polsku.
- **Decision**: SKIPPED

### F9 — Drobne, udokumentowane odstępstwa od kontraktu planu (jsoup, kickoff, CLAUDE.md)

- **Severity**: 👁 OBSERVATION
- **Impact**: 🏃 LOW — szybka decyzja; poprawka oczywista i wąska
- **Dimension**: Plan Adherence / Scope Discipline
- **Location**: backend/pom.xml:113-117; NinetyMinutClient.java:56-57; CLAUDE.md
- **Detail**: Trzy odstępstwa, wszystkie łagodne: (1) jsoup przypięty do `1.22.2` wbrew
  kontraktowi „bez `<version>`" — przesłanka planu była błędna (jsoup NIE jest w Spring Boot
  BOM), pin był konieczny i jest udokumentowany komentarzem w pom; (2) rekord `MatchResult`
  ma `LocalDateTime kickoff` zamiast kontraktowego `LocalDate date` — bogatsze dane,
  konsumowane w promptcie; (3) do commita p1 wjechała regeneracja bloku lekcji 10x-cli
  w CLAUDE.md — narzędziowy boilerplate spoza zakresu planu, bez zmian konwencji projektu.
  (roadmap.md w p1 to zapis decyzji, na której plan sam bazuje — provenance, nie scope creep.)
- **Fix**: Krótki addendum w plan.md (sekcja Key Discoveries lub notka przy kontraktach)
  dokumentujący (1) i (2); CLAUDE.md — nic do zrobienia.
- **Decision**: SKIPPED
