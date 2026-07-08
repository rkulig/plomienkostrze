---
date: 2026-07-08T17:52:24+02:00
researcher: Claude (Fable 5)
git_commit: 3a73613a11e039c3bf690038e91c93f41a5816dd
branch: master
repository: plomienkostrze
topic: "Czy docs-spring-ai-openai.md jest kompatybilny z naszym codebase (S-03: gated-news-generation)?"
tags: [research, codebase, spring-ai, openrouter, spring-boot-4, backend, gated-news-generation]
status: complete
last_updated: 2026-07-08
last_updated_by: Claude (Fable 5)
last_updated_note: "Follow-up 2026-07-08T20:01: niezależne potwierdzenie konfiguracji Spring AI 2.0 przez Context7 (javadoc 2.0.0) + wykryty rozjazd input S-03 (docs/plan: surowe dane+ton vs roadmap: wynik z 90minut, bez tonu)."
---

# Research: Kompatybilność `docs-spring-ai-openai.md` z codebase (S-03)

**Date**: 2026-07-08T17:52:24+02:00
**Researcher**: Claude (Fable 5)
**Git Commit**: 3a73613a11e039c3bf690038e91c93f41a5816dd
**Branch**: master
**Repository**: plomienkostrze

## Research Question

Przejrzeć codebase i rozstrzygnąć, czy `context/changes/gated-news-generation/docs-spring-ai-openai.md`
jest z nim kompatybilny — pod implementację S-03 (`gated-news-generation`) z
`context/foundation/roadmap.md`.

## Summary

**Werdykt: dokument jest kompatybilny co do kształtu (API, artefakty, podejście), ale
niekompatybilny co do wersji — i jedna z jego konfiguracji spowodowałaby 404 w runtime.**

1. **Zła linia wersji.** Dokument pisany był z referencji **Spring AI 1.1**
   (`docs.spring.io/spring-ai/reference/1.1`), a Spring AI 1.1.x wspiera wyłącznie
   Spring Boot **3.5.x**. Backend jest na **Spring Boot 4.1.0**
   (`backend/pom.xml:8`) — wymaga **Spring AI 2.0** (GA 2026-06-12, budowany
   wprost pod Boot 4.0/4.1 i Framework 7; 2.0.0 podciąga Boot 4.1.0, czyli
   dokładnie naszą wersję).
2. **Rdzeń dokumentu przeżywa migrację na 2.0 bez zmian**: BOM `spring-ai-bom`
   + starter `spring-ai-starter-model-openai` to poprawne nazwy także w 2.0;
   API `ChatClient` (`defaultSystem`, `.param()`, `.entity()`,
   `templateRenderer`, advisor `ENABLE_NATIVE_STRUCTURED_OUTPUT`) jest w 2.0
   niezmienione.
3. **Otwarta kwestia base-url z dokumentu jest rozstrzygnięta — przeciwko jego
   snippetowi.** W 2.0 (od M5) moduł OpenAI przepisano na oficjalne SDK
   `openai-java`: właściwość `completions-path` **usunięto**, SDK dokleja tylko
   `chat/completions`, więc base-url **musi** zawierać `/v1`:
   `https://openrouter.ai/api/v1`. Snippet z dokumentu
   (`https://openrouter.ai/api`) dawałby 404.
4. **Nazwy właściwości w 2.0 się spłaszczyły**: `spring.ai.openai.chat.options.model`
   → `spring.ai.openai.chat.model` (warianty `.options.*` są w 2.0.0
   deprecated, jeszcze działają).
5. **Codebase przyjmie starter czysto** — konwencje (placeholder `${ENV:default}`
   w `application.properties`, sekrety przez Cloud Run `--set-secrets`,
   default-deny w SecurityConfig, brak WebFlux → blokujące `.call()`) mają
   gotowe miejsca na tę integrację; szczegóły i pułapki niżej.

## Detailed Findings

### 1. Wersja Spring AI vs Spring Boot 4.1 (krytyczne)

- Backend: `spring-boot-starter-parent` **4.1.0**, Java 21 (`backend/pom.xml:8`, `backend/pom.xml:30`).
- Oficjalna matryca zgodności (README spring-projects/spring-ai + getting-started 2.0):
  - Spring AI **1.1.x** → Spring Boot **3.5.x** (tylko),
  - Spring AI **2.0.x** → Spring Boot **4.0.x / 4.1.x**.
- Spring AI **2.0.0 GA: 2026-06-12** (blog spring.io); release 2.0.0 zawiera
  „Upgrade to Spring Boot 4.1.0" — idealne dopasowanie do naszego POM-a.
  (Znany issue spring-ai#6465: startery 2.0.0 podciągają Boot 4.1 — dla
  projektów na Boot 4.0 to był konflikt; nas nie dotyczy, jesteśmy na 4.1.0.)
- `docs-spring-ai-openai.md:4` cytuje referencję 1.1 — stąd cała korekta.
- Wniosek do planu: `spring-ai.version` = **2.0.0** (lub nowsza łatka 2.0.x)
  w `dependencyManagement`; sam blok BOM + starter z dokumentu (`docs-spring-ai-openai.md:13-29`)
  jest poprawny bez zmian nazw artefaktów.

### 2. Co w dokumencie pozostaje aktualne (zweryfikowane na docs 2.0)

- **Artefakty** (`docs-spring-ai-openai.md:9-11, 26-29`): `spring-ai-starter-model-openai`
  przez `spring-ai-bom` — getting-started 2.0 podaje dokładnie te nazwy.
- **`ChatClient`** (`docs-spring-ai-openai.md:52-87`): autokonfigurowany
  `ChatClient.Builder`, `.defaultSystem(...)`, `.prompt().user(...).call().content()` —
  bez zmian w 2.0 (deprecated jest jedynie `ChatClientCustomizer` →
  `ChatClientBuilderCustomizer`, czego dokument nie używa).
- **Szablony promptów** (`docs-spring-ai-openai.md:89-117`): `.param()`,
  StringTemplate z delimiterami `{}`, `templateRenderer(...)` — API obecne w 2.0
  (`ChatClient.Builder.defaultTemplateRenderer` istnieje). Uwaga o nawiasach
  klamrowych w surowych danych meczowych pozostaje trafna.
- **Structured output** (`docs-spring-ai-openai.md:119-136`): `.entity(...)` na
  `.call()` oraz advisor `ENABLE_NATIVE_STRUCTUED_OUTPUT` — oba potwierdzone
  w API 2.0 (`AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT`).
- Bug `finish_reason: "end_turn"` (spring-ai#1522, cytowany w
  `research-libraries.md:50-55`) jest w 2.0 bezprzedmiotowy — moduł OpenAI
  przepisano na oficjalne SDK.

### 3. Co w dokumencie wymagało korekty pod 2.0

> **Status: naniesione.** Poniższe korekty zostały wprowadzone do
> `docs-spring-ai-openai.md` 2026-07-08 (numery linii w tabeli = wersja sprzed
> korekty); tabela zostaje jako zapis „co i dlaczego się zmieniło".

| Miejsce w dokumencie | Problem | Korekta |
| --- | --- | --- |
| `docs-spring-ai-openai.md:38` (`base-url=https://openrouter.ai/api`) | W 2.0 SDK dokleja tylko `chat/completions` → `https://openrouter.ai/api/chat/completions` = 404 | `spring.ai.openai.base-url=https://openrouter.ai/api/v1` |
| `docs-spring-ai-openai.md:44-48` (otwarta kwestia `completions-path`) | Właściwość `spring.ai.openai.chat.completions-path` **usunięta** w 2.0.0-M5 (migracja na openai-java SDK; spring-ai#6036, PR #6093) | Skreślić pytanie — rozstrzygnięte: `/v1` obowiązkowe w base-url |
| `docs-spring-ai-openai.md:40-41` (`spring.ai.openai.chat.options.model`, `...options.temperature`) | Prefiks `.options` w 2.0 deprecated (upgrade-notes 2.0) | `spring.ai.openai.chat.model`, `spring.ai.openai.chat.temperature` |
| `docs-spring-ai-openai.md:4` (źródło: referencja 1.1) | Zła linia wersji dla Boot 4.1 | Podstawą planu ma być referencja **2.0** (`docs.spring.io/spring-ai/reference/2.0`) + upgrade-notes |

Dodatkowe zmiany 2.0 warte odnotowania w planie (dokumentu nie dotyczą wprost):

- Włączanie/wyłączanie autokonfiguracji modeli przeszło na właściwości
  `spring.ai.model.chat` (stare `spring.ai.openai.chat.enabled` usunięte).
- Transport HTTP to OkHttp z SDK; customizacja przez beany
  `OpenAiHttpClientBuilderCustomizer`.
- Pusty `spring.ai.openai.api-key=` przełącza klienta w tryb bez nagłówka
  `Authorization` (nie wywala kontekstu) — istotne dla testu `contextLoads()`.

### 4. Fit z konwencjami backendu (research wewnętrzny)

- **Konfiguracja**: format `.properties`, jeden plik, sekrety jako
  `${ENV_VAR:default}` (`backend/src/main/resources/application.properties:3-19`,
  np. `DB_PASSWORD`, `app.admin.uids=${ADMIN_UIDS:}`). Wpis
  `spring.ai.openai.api-key=${OPENROUTER_API_KEY:}` + `spring.ai.openai.base-url=...`
  idealnie pasuje do stylu. `backend/CLAUDE.md` wprost: config w
  `application.properties` — snippet `.properties` z dokumentu (`docs-spring-ai-openai.md:37-42`)
  trafia w konwencję lepiej niż YAML z `research-libraries.md:23-32`.
- **Miejsce w kodzie**: pakiet feature `com.plomienkostrze.news` + kontrolery w
  `com.plomienkostrze.web`; DTOs jako rekordy zagnieżdżone w kontrolerze
  (`backend/src/main/java/com/plomienkostrze/web/NewsPostController.java:47-65`);
  brak warstwy serwisów — klasa wywołująca model będzie pierwszym `*Service`.
  `NewsPostStatus` zaprojektowany na rozrost wartości, nie kolumn — komentarz
  wprost antycypuje propozycje S-03
  (`backend/src/main/java/com/plomienkostrze/news/NewsPostStatus.java:5-8`).
  Następna migracja Flyway: **V6** (V5 = CHECK z S-02).
- **Security**: default-deny — `anyRequest().denyAll()`
  (`backend/src/main/java/com/plomienkostrze/security/SecurityConfig.java:70`);
  nowy endpoint generacji wymaga jawnego matchera
  `hasRole("ADMIN")` przed linią deny-all (wzorzec: `SecurityConfig.java:69`).
  Zapomnienie = 403, nie dziura — bezpieczny kierunek błędu.
- **Testy (główne ryzyko integracji)**: jedyny test to pełnokontekstowy
  `@SpringBootTest contextLoads()`
  (`backend/src/test/java/com/plomienkostrze/PlomienKostrzeApiApplicationTests.java:6-11`),
  a `backend/src/test/resources/application.properties` **całkowicie zasłania**
  główny plik (udokumentowane w liniach 4-5) — każda nowa wymagana właściwość
  musi być tam zdublowana. Konwencja repo to shadow-properties, nie wykluczanie
  autokonfiguracji. Do planu: dopisać w test-properties `spring.ai.openai.api-key`
  (dummy albo celowo pusty — patrz tryb no-auth wyżej) i `base-url`, żeby CI
  (`./mvnw -B verify` bez sieci i sekretów) pozostało zielone.
- **Stack servletowy, bez WebFlux**: zero `Flux`/`SseEmitter`/reactive w kodzie;
  `spring-boot-starter-webmvc` (`backend/pom.xml:35`). Naturalny kształt S-03 to
  **blokujące `.call()`** (spójne z synchronicznymi kontrolerami wołającymi
  repozytoria wprost); streaming przez SSE wymagałby `SseEmitter`/mostkowania i
  jest opcją, nie domyślną ścieżką — co i tak zgadza się z zastrzeżeniem
  dokumentu, że `.entity()` działa tylko na `.call()` (`docs-spring-ai-openai.md:135-136`).
- **CI/deploy**: deploy nie przekazuje sekretów w workflow —
  `.github/workflows/backend.yml:90-92` (konfiguracja serwisu przenosi się
  między rewizjami). Klucz OpenRoutera wchodzi więc **poza CI**, jednorazowo:
  Secret Manager + `gcloud run services update --update-secrets`, wzorzec
  `db-password` (`context/changes/deployment/deployment-plan.md:369-380`).

### 5. Kontekst historyczny potwierdzający kierunek

- **Pułapka modularnych starterów Boot 4** już raz kosztowała: sam `flyway-core`
  był po cichu ignorowany, trzeba było `spring-boot-flyway`
  (`context/changes/deployment/deployment-plan.md:407-418`, komentarz w
  `backend/pom.xml:68-74`). Stąd standard „weryfikuj nazwy artefaktów pod Boot 4,
  nie zakładaj nazw z Boot 3" (`context/archive/2026-07-06-manual-news-publishing/plan.md:114-120`)
  — dokładnie ta dyscyplina wykryła tu rozjazd 1.1 vs 2.0.
- **Sekret LLM jest już zarezerwowany w runbooku**: otwarty checkbox „Store the
  LLM API key … same pattern as db-password" + `LLM_API_KEY=llm-api-key:latest`
  (`context/changes/deployment/deployment-plan.md:402-403, 425-427`). **Do
  rozstrzygnięcia w planie rozjazd nazwy**: runbook mówi `LLM_API_KEY`,
  research S-03 i `tech-stack-backend.md:89` mówią `OPENROUTER_API_KEY`.
- **`min-instances` 0→1 jest sprzężone z S-03**: flip dopiero gdy „realny
  adminowy flow generacji" wejdzie (`deployment-plan.md:224-226, 404-406`) —
  pozycja na checklistę planu.
- **Bramka akceptacji poza Spring AI** — potwierdzone: S-02 świadomie nie zrobił
  draftów („S-03 zdefiniuje własny przepływ propozycji",
  `context/archive/2026-07-06-manual-news-publishing/plan.md:80-82`); stan
  propozycji jako wartości statusu encji + migracja V6 + agregat SQL do progu
  75% (`research-libraries.md:85-92`).
- **Posture testowe repo**: brak testów automatycznych dla nowego kodu to
  świadoma, powtarzalna decyzja użytkownika; bramki = build + `contextLoads()`
  na H2 + weryfikacja manualna (`.../plan.md:76-79, 482-491`).

## Code References

- `backend/pom.xml:8` — `spring-boot-starter-parent` 4.1.0 (sedno rozjazdu wersji)
- `backend/pom.xml:68-74` — komentarz o pułapce modularizacji Boot 4 (flyway)
- `backend/src/main/resources/application.properties:10-19` — konwencja `${ENV:default}`, allowlist adminów
- `backend/src/test/resources/application.properties:4-11` — shadow-properties dla testów (H2, flyway off)
- `backend/src/test/java/com/plomienkostrze/PlomienKostrzeApiApplicationTests.java:6-11` — pełnokontekstowy `contextLoads()`
- `backend/src/main/java/com/plomienkostrze/security/SecurityConfig.java:58-93` — default-deny, `ROLE_ADMIN` z allowlisty, lazy JWKS
- `backend/src/main/java/com/plomienkostrze/news/NewsPostStatus.java:5-8` — enum zaprojektowany pod statusy propozycji S-03
- `backend/src/main/java/com/plomienkostrze/web/NewsPostController.java:47-95` — wzorzec kontroler+rekordy DTO+walidacja
- `backend/src/main/resources/db/migration/` — V1–V5; S-03 zaczyna od V6
- `.github/workflows/backend.yml:90-98` — deploy bez `--set-secrets`; konfiguracja serwisu poza CI

## Architecture Insights

- Dokument użycia biblioteki trzeba wersjonować względem **naszego** Boot-a, nie
  „najnowszych docs": jedyna istotna niekompatybilność wynikła z czytania
  referencji 1.1 przy backendzie na Boot 4.1.
- Grain backendu jest synchroniczny (MVC + JPA + blokujące kontrolery) — S-03
  powinien startować od `.call()` + spinner po stronie SPA (NFR „widoczny postęp
  > ~2 s" spełnia wariant minimalny z `research-libraries.md:73-75`); streaming
  to świadome rozszerzenie, nie warunek wejścia.
- Kierunki błędów są dobrze ustawione: default-deny w security, fail-closed
  allowlist adminów, testy na H2 offline — integracja LLM musi te własności
  zachować (dummy/pusty klucz w test-properties zamiast sięgania do sieci).

## Historical Context (from prior changes)

- `context/changes/gated-news-generation/research-libraries.md` — wybór Spring AI (LangChain4j i gołe HTTP odrzucone); frontendowe warianty progresu/streamingu
- `context/changes/gated-news-generation/docs-spring-ai-openai.md` — recenzowany dokument użycia (do korekty per sekcja 3)
- `context/archive/2026-07-06-manual-news-publishing/plan.md` — wzorce auth/endpointów/encji do reużycia; jawnie odroczony przepływ propozycji do S-03
- `context/changes/deployment/deployment-plan.md:358-431` — mechanika sekretów, checkbox klucza LLM, pułapka flyway, dyscyplina forward-only migracji
- `context/foundation/tech-stack-backend.md:77-101` — decyzje OpenRouter + Firebase Auth
- `context/foundation/infrastructure.md:79-96, 131-135` — cold-start NFR (`min-instances`), mechanizm Secret Manager

## Related Research

- `context/changes/gated-news-generation/research-libraries.md` (2026-07-08) — research zewnętrzny: wybór bibliotek
- `context/changes/gated-news-generation/docs-spring-ai-openai.md` (2026-07-08) — dokumentacja użycia (1.1 → do aktualizacji na 2.0)
- Brak `context/foundation/lessons.md` w repo (potwierdzone) — kandydat na pierwszą lekcję: „dokumentację biblioteki dobieraj do wersji Boot-a w POM, nie do domyślnej wersji docs".

## Open Questions

1. **Model LLM** — wybór w `/10x-plan` (zgodnie z roadmapą); string w
   `spring.ai.openai.chat.model` (uwaga: bez `.options` w 2.0).
2. **Nazwa sekretu/env**: `LLM_API_KEY`/`llm-api-key` (runbook deploymentu) vs
   `OPENROUTER_API_KEY` (tech-stack, research S-03) — ujednolicić w planie.
3. **Licznik 75% akceptacji** (Unknown z roadmapy): zdarzenia akceptacji/odrzucenia
   w PostgreSQL + zapytanie agregujące — do skonkretyzowania w planie (schemat V6).
4. **Test-properties**: dummy klucz czy celowo pusty `spring.ai.openai.api-key=`
   (tryb no-auth SDK)? Do rozstrzygnięcia jednym uruchomieniem `./mvnw test`
   po dodaniu startera.
5. ~~Czy przy okazji planu aktualizować sam `docs-spring-ai-openai.md`, czy tylko
   przenieść korekty (sekcja 3) do `plan.md`?~~ — rozstrzygnięte 2026-07-08:
   dokument zaktualizowany na miejscu (decyzja użytkownika).

## Follow-up Research 2026-07-08T20:01:27+02:00

Powtórka researchu na wprost pytania („czy `docs-spring-ai-openai.md` jest
kompatybilny z codebase pod S-03") — świeży odczyt kodu + **niezależna
weryfikacja Context7** (javadoc Spring AI **2.0.0**, `/websites/spring_io_spring-ai_2_0_0`).
Werdykt bez zmian: **kompatybilny**. Dwie rzeczy warte dopisania.

### A. Konfiguracja 2.0 z dokumentu — potwierdzona wprost w javadoc 2.0.0

Wcześniejsze §2–3 opierały się na upgrade-notes/README; teraz potwierdzone na
źródle API 2.0.0:

- **Spłaszczenie właściwości** — `OpenAiChatProperties` ma prefiks
  `spring.ai.openai.chat` z polami `model`, `temperature`, `max-tokens` jako
  top-level; klasa zagnieżdżona `OpenAiChatProperties.Options` jest w całości
  **`@Deprecated`, for removal**. Dokładnie jak w skorygowanym dokumencie
  (`docs-spring-ai-openai.md:40-51`).
- **`base-url` na `AbstractOpenAiProperties`** (`getBaseUrl/setBaseUrl`), a moduł
  stoi na **oficjalnym SDK `com.openai`** (widoczne
  `setCredential(com.openai.credential.Credential)`,
  `com.openai.azure.AzureOpenAIServiceVersion`). To potwierdza wymóg `/v1` w
  base-url (SDK dokleja tylko `chat/completions`) — snippet planu
  `https://openrouter.ai/api/v1` jest poprawny (`plan.md:164`).
- **`completions-path` NIE istnieje** w `OpenAiChatProperties`/
  `AbstractOpenAiProperties` (występuje za to w `DeepSeekChatProperties` —
  `DEFAULT_COMPLETIONS_PATH`), co potwierdza usunięcie tej właściwości z modułu
  OpenAI w 2.0. Otwarta kwestia z pierwotnego dokumentu pozostaje słusznie
  skreślona.
- `spring-ai-starter-model-openai`: nazwa spójna z konwencją modułów 2.0
  (`org.springframework.ai.model.openai.autoconfigure`); jedyna rzecz do
  potwierdzenia empirycznie przy `./mvnw` (rozwiązanie zależności BOM), nie
  blokująca — zgodnie z dyscypliną Boot 4 z §5.

**Wniosek:** cała warstwa „jak użyć Spring AI" w dokumencie i w `plan.md`
(fazy 1) jest zgodna z API 2.0.0. Nic do zmiany po stronie mechaniki LLM.

### B. Rozjazd zakresu S-03: wejście „surowe dane + ton" vs „wynik z 90minut"

To **nie jest** problem kompatybilności dokumentu z codebase (mechanika Spring AI
działa dla dowolnego kształtu promptu), ale rozjazd spójności, który uderzy w
implementację, jeśli go nie domkniemy:

- `docs-spring-ai-openai.md` §4 i `plan.md` (Overview, Faza 1 pkt 5–6, Faza 2)
  zakładają input **„surowe dane meczowe + opcjonalny ton/styl"** — DTO
  `GenerateRequest(matchData, tone)`, textarea + pole tonu w panelu.
- `roadmap.md` S-03 (zaktualizowany **2026-07-08**, ten sam dzień) i
  `research-scraping-90minut.md` ustaliły inny kształt: **wejściem jest wynik
  ostatniego meczu scrapowany z 90minut.pl, jednym kliknięciem, bez podawania
  tonu**. Pełne dane (składy/strzelcy) są za reCAPTCHA na ŁNP — poza zakresem.

Czyli `plan.md`/`docs` reprezentują **wcześniejszy** model wejścia (paste + ton),
sprzed decyzji o scrapowaniu wyniku. Do rozstrzygnięcia przez użytkownika **przed
Fazą 1**: który model S-03 realizujemy teraz.

Co to zmienia (gdyby wybrać model „wynik z 90minut", zgodny z roadmapą):
- **Backend:** dochodzi krok scrapowania 90minut (jsoup — statyczny HTML
  `ISO-8859-2`, bez auth; zob. `research-libraries.md` i `research-scraping-90minut.md`);
  DTO generacji zmienia się z `matchData`+`tone` na wynik/strukturę meczu (albo
  identyfikator meczu do pobrania), pole `tone` znika.
- **Prompt:** zamiast „dane + ton" — **twardy guardrail przeciw konfabulacji**
  (model dostaje sam wynik/rywala/rozgrywki/datę; ma NIE zmyślać strzelców ani
  przebiegu bramek — kluczowe dla progu 75% akceptacji; zob. ryzyko „chudego
  wejścia" w `roadmap.md` S-03).
- **Frontend:** zamiast textarea+ton — przycisk „Generuj z ostatniego meczu"
  (jedno kliknięcie); reszta przepływu (edycja → publikacja/odrzucenie
  istniejącą ścieżką) bez zmian.
- **Bez zmian:** wersja Spring AI (2.0), konfiguracja OpenRouter, bean
  `ChatClient`, `.call().entity(ProposalDraft)`, reguła security (nadal jeden
  adminowy `POST …/generate` przed `denyAll`), decyzja o braku persystencji
  propozycji, sekret, `min-instances`.

**Rekomendacja:** dokument Spring AI zostaje jako poprawna podstawa mechaniki
generacji — nie wymaga korekt pod codebase. Zaktualizować należy **`plan.md`**
(kształt wejścia S-03: scrape 90minut zamiast paste+ton) tak, by był spójny z
`roadmap.md`; alternatywnie, jeśli użytkownik świadomie wraca do modelu „paste +
ton", zaktualizować `roadmap.md` w drugą stronę. To decyzja produktowa, nie
techniczna.

### Zweryfikowane odniesienia (follow-up)

- `backend/pom.xml:8,30` — Spring Boot 4.1.0 + Java 21 (premisa dokumentu, potwierdzona)
- Boot-4 modularne startery w użyciu: `spring-boot-starter-webmvc`, `spring-boot-flyway`,
  `spring-boot-starter-security-oauth2-resource-server` (`backend/pom.xml:35,52,73`) —
  starter Spring AI wnosi własny transport (SDK/OkHttp), brak kolizji z warstwą web
- `SecurityConfig.java:69-70` — `POST /api/news-posts` = `hasRole("ADMIN")`, potem
  `anyRequest().denyAll()`; nowy `…/generate` musi mieć jawny matcher (plan pkt 7 to ma)
- `NewsPost.java:52` — jedyny konstruktor to fabryka `published(...)`; spójne z decyzją
  „persystujemy tylko zaakceptowane" (brak encji propozycji)
- `src/test/resources/application.properties:4-16` — shadow-properties (komentarz wprost
  o zasłanianiu); plan słusznie dubluje tam `spring.ai.openai.*`
- Context7 `/websites/spring_io_spring-ai_2_0_0` — `OpenAiChatProperties`,
  `AbstractOpenAiProperties`, `OpenAiChatProperties.Options` (deprecated) — źródło pkt A
