# Gated News Generation (S-03) — Implementation Plan

> Change ID: `gated-news-generation` · Roadmap: S-03 (gwiazda przewodnia) · PRD: US-01, FR-003–FR-005
> Upstream: `research.md` (wewnętrzny, weryfikacja wersji + follow-up), `research-libraries.md` (wybór bibliotek),
> `research-scraping-90minut.md` (źródło danych meczowych + granice), `docs-spring-ai-openai.md` (dokument użycia Spring AI 2.0, skorygowany 2026-07-08)

## Overview

Administrator w panelu `/admin` klika **„Generuj z ostatniego meczu"**. Backend scrapuje
z 90minut.pl wynik ostatniego rozegranego meczu Płomienia Kostrze (data, rozgrywki+kolejka,
rywal, u siebie/wyjazd, wynik), a Spring AI 2.0 → OpenRouter (`anthropic/claude-sonnet-4.6`)
generuje na tej podstawie propozycję wpisu (tytuł + treść) po polsku. Administrator edytuje ją
w istniejącym formularzu publikacji i publikuje ścieżką z S-02 — albo odrzuca. Nic nie trafia
do publicznych aktualności bez jawnej akceptacji (guardrail PRD): system w ogóle nie ma
ścieżki automatycznej publikacji.

**Zmiana kierunku wejścia (decyzja 2026-07-08, `roadmap.md` S-03):** wejściem jest **sam wynik
meczu scrapowany z 90minut.pl**, nie „surowe dane meczowe + ton/styl". Rezygnujemy z pola tonu —
generacja jednym kliknięciem. Pełne dane meczowe (składy, strzelcy, zmiany) są na
`laczynaspilka.pl`, ale ich API jest za reCAPTCHA v3 i niedostępne z backendu — poza zakresem
tego slice'a (`research-scraping-90minut.md`).

Kluczowa decyzja upraszczająca (bez zmian): **propozycja żyje wyłącznie w kliencie**. Zapisywane
są tylko zaakceptowane propozycje — akceptacja to zwykłe `POST /api/news-posts` (istniejący
endpoint S-02), odrzucenie niczego nie zapisuje. W efekcie: **bez migracji V6, bez nowych
wartości `NewsPostStatus`, bez zmian w schemacie bazy**. Pomiar progu 75% akceptacji prowadzi
użytkownik ręcznie, poza kodem.

## Current State Analysis

- **Backend** (Spring Boot 4.1.0, Java 21, MVC + JPA, bez WebFlux): publiczne odczyty
  + adminowy `POST /api/news-posts` (`backend/src/main/java/com/plomienkostrze/web/NewsPostController.java:90-95`),
  default-deny w security (`SecurityConfig.java:70`), `ROLE_ADMIN` z allowlisty UID-ów.
  Zero integracji LLM, zero scrapowania; brak warstwy serwisów — klasy generacji i scrapera
  będą pierwszymi `*Service`/klientami zewnętrznymi.
- **Frontend** (Angular, standalone + signals): panel `/admin` z reaktywnym formularzem
  tytuł/treść i publikacją (`frontend/src/app/admin/admin-panel.ts`), `NewsApi` jako
  jedyna warstwa HTTP (`frontend/src/app/news/news-api.ts`), interceptor dokleja token
  Firebase do każdego żądania pod `apiBaseUrl` (`frontend/src/app/auth/auth-interceptor.ts:14-22`)
  — nowy endpoint dostaje auth za darmo. UI po polsku, wskaźniki ładowania tekstowe.
- **Konfiguracja**: `application.properties` z placeholderami `${ENV:default}`;
  test-properties **całkowicie zasłaniają** główny plik — każda wymagana właściwość
  musi być zdublowana (`backend/src/test/resources/application.properties:4-5`).
- **Testy**: jedyny test to pełnokontekstowy `contextLoads()` na H2 offline; CI =
  `./mvnw -B verify` bez sieci i sekretów. Brak testów frontendowych w CI.
- **Deploy**: Cloud Run (`europe-central2`), sekrety przez Secret Manager +
  `--set-secrets` poza CI (wzorzec `db-password`,
  `context/changes/deployment/deployment-plan.md:369-380`); workflow deployu nie
  przekazuje sekretów (`.github/workflows/backend.yml:90-98`).
- **CORS**: `/api/**` wszystkie metody (`CorsConfig.java:25-28`) — nowy endpoint nie
  wymaga zmian.
- **90minut.pl** (źródło danych): statyczny HTML, kodowanie **ISO-8859-2**, bez auth i bez
  reCAPTCHA. Strona meczów drużyny: `mecze_druzyna.php?id=<teamId>&id_sezon=<seasonId>`
  (Płomień = `id=3154`) — tabela `data | rozgrywki | gospodarze | wynik | goście`; drużyna
  własna oznaczona `<b><u>…</u></b>`, rywal linkiem. **Bez `id_sezon` strona nie zwraca
  tabeli meczów** (zweryfikowane). Schemat id sezonów jest nieoczywisty (mecze: `107`=2025/26,
  `109`=2026/27) — stąd sezon w configu (niżej).

## Desired End State

Administrator na produkcji: otwiera `/admin`, klika „Generuj z ostatniego meczu", widzi
tekstowy wskaźnik postępu, po kilku–kilkunastu sekundach formularz tytuł/treść wypełnia się
propozycją po polsku opartą na realnym wyniku ostatniego meczu (np. „Pewne zwycięstwo Płomienia
— 6:0 z Dąbskim KS"), poprawia ją i klika „Opublikuj" — wpis natychmiast widoczny dla gościa.
Albo klika „Odrzuć" — propozycja znika, można generować ponownie. `./mvnw test` i CI pozostają
zielone bez sieci i sekretów.

### Key Discoveries:

- Spring AI **2.0.0** jest wymagane pod Boot 4.1.0 (1.1.x wspiera tylko Boot 3.5.x);
  BOM `spring-ai-bom` + `spring-ai-starter-model-openai` — nazwy artefaktów bez zmian
  (`research.md` §1–2, potwierdzone javadoc 2.0.0 w follow-upie).
- `base-url` **musi** zawierać `/v1` (`https://openrouter.ai/api/v1`) — od 2.0.0-M5
  SDK `openai-java` dokleja tylko `chat/completions`; właściwość `completions-path`
  usunięta (`research.md` §3 + follow-up A).
- Właściwości chat spłaszczone: `spring.ai.openai.chat.model` (bez `.options`).
- Pusty `spring.ai.openai.api-key=` przełącza SDK w tryb bez nagłówka `Authorization`
  — kontekst wstaje bez sieci i sekretu, co ratuje `contextLoads()` w CI (`research.md` §3).
- **90minut wymaga `id_sezon`**; strona bez sezonu nie ma tabeli meczów. Id sezonu nie
  wyprowadza się trywialnie z roku — trzymamy je w configu (jak team id), z opcją
  dynamicznego rozwiązania z linku na stronie klubu jako rozszerzenie (`research-scraping-90minut.md`).
- **Format wyniku bywa złożony**: zwykły `6-0`, karne `1-1 k. 4-3` (z `<br>`), mecz
  nierozegrany `-`. „Ostatni mecz" = najnowszy wiersz z realnym wynikiem (`\d+-\d+`),
  pomijając `-` i mecze przyszłe.
- **Ryzyko konfabulacji (kluczowe dla progu 75%)**: model dostaje sam wynik (bez składów
  i strzelców) — system prompt musi twardo zabronić zmyślania nazwisk, minut i przebiegu
  bramek (`roadmap.md` S-03, ryzyko „chudego wejścia").
- Ekscerpt listy publicznej to pierwszy akapit do pustej linii
  (`NewsPostController.java:106-112`) — prompt powinien wymuszać akapity rozdzielone
  pustymi liniami.
- jsoup jest zarządzany wersją przez Spring Boot BOM — dodajemy zależność bez `<version>`
  (spójnie z pozostałymi w `backend/pom.xml`).

## What We're NOT Doing

- **Bez scrapowania składów / strzelców / zmian z `laczynaspilka.pl`** — API za reCAPTCHA v3,
  niedostępne z backendu; przyszłe rozszerzenie przez przechwyt z przeglądarki admina
  (`research-scraping-90minut.md`). Ten slice używa wyłącznie publicznego wyniku z 90minut.
- **Bez pola tonu/stylu** — generacja jednym kliknięciem, bez parametrów wejścia (zmiana
  kierunku 2026-07-08).
- **Bez migracji V6 i bez nowych wartości `NewsPostStatus`** — propozycje nie są
  persystowane; zapisujemy wyłącznie zaakceptowane (= opublikowane istniejącą ścieżką).
- **Bez licznika 75% w aplikacji** — statystykę akceptacji użytkownik prowadzi samodzielnie,
  poza kodem (decyzja z planowania; Unknown z roadmapy rozstrzygnięty).
- **Bez streamingu/SSE** — blokujące `.call()` + tekstowy wskaźnik postępu spełnia NFR
  „widoczny postęp > ~2 s"; streaming to świadome rozszerzenie poza tym slice.
- **Bez wyboru meczu / listy meczów** — zawsze ostatni rozegrany (jedno kliknięcie).
- **Bez listy oczekujących propozycji / szkiców** — propozycja żyje w jednej sesji
  przeglądarki; odświeżenie strony ją traci (świadomy koszt braku persystencji).
- **Bez edycji/usuwania opublikowanych wpisów** — to S-04 (równoległy slice).
- **Bez testów automatycznych dla nowego kodu** — świadoma, powtarzalna decyzja repo;
  bramki = build + `contextLoads()` + weryfikacja manualna (`research.md` §5).

## Implementation Approach

Trzy fazy w naturalnej kolejności zależności: backend (scraper 90minut + integracja Spring AI +
endpoint generacji, weryfikowalny lokalnie curlem), frontend (jeden przycisk w istniejącym
panelu `/admin`), produkcja (sekret + `min-instances` + e2e). Każda faza zostawia repo w stanie
zielonym (build + `contextLoads()` offline) i deployowalnym — brak zmian w schemacie bazy czyni
całość trywialnie backward-compatible.

Konwencja commitów/branchy: nowy branch `M2L4-gatedNewsGeneration` (wzorzec
`M<moduł>L<lekcja>-nazwa`); push i PR wykonuje wyłącznie użytkownik.

## Critical Implementation Details

- **Scraper czyta ISO-8859-2**: 90minut serwuje `charset=ISO-8859-2` w nagłówku — jsoup
  respektuje nagłówek automatycznie, ale zweryfikować, że polskie znaki (ł, ś, ż) wychodzą
  poprawnie do promptu. Parsowanie po strukturze tabeli (`data | rozgrywki | gospodarze |
  wynik | goście`), drużyna własna po `<b><u>`; wynik z komórki `class="mecze2"`.
- **Wybór ostatniego meczu**: najnowszy wiersz z realnym wynikiem (`\d+-\d+`); pomijać `-`
  (nierozegrane) i wiersze z datą przyszłą. Format karnych `1-1 k. 4-3` → wynik regulaminowy
  `1-1` + informacja o karnych (opcjonalnie w prompt jako fakt). Ustalić u siebie/wyjazd i
  wynik z perspektywy Płomienia (wygrana/remis/porażka) po stronie serwera, nie zostawiać
  interpretacji modelowi.
- **Guardrail przeciw konfabulacji**: system prompt dostaje TYLKO fakty wyniku (rywal,
  u siebie/wyjazd, rozgrywki+kolejka, data, wynik, rozstrzygnięcie) i ma **jawny zakaz**
  wymyślania strzelców, minut, kartek i przebiegu gry. To warunek sensownej akceptacji
  (próg 75%). Prompt użytkownika składać z zaufanych, strukturalnych pól — bez przepuszczania
  scrapowanych stringów przez renderer StringTemplate (nazwy rywali są zaufane, ale konkatenacja
  / text block Javy jest bezpieczniejsza i prostsza niż `.param()`).
- **Odporność scrapera**: brak sieci / zmiana struktury 90minut / brak rozegranego meczu w
  sezonie → czytelny błąd do klienta (502 z komunikatem, np. „nie udało się pobrać danych
  meczu"), nigdy wiszące żądanie ani NPE. Ograniczony connect/read-timeout na kliencie jsoup.
- **Ograniczony czas oczekiwania (NFR)** dla LLM: sprawdzić domyślny read-timeout transportu
  OkHttp (SDK openai-java); jeśli nieograniczony lub > ~60 s, ustawić beanem
  `OpenAiHttpClientBuilderCustomizer`. Błąd/timeout wraca jako 502, nie jako wiszące żądanie.
- **Slug modelu**: `anthropic/claude-sonnet-4.6` zweryfikować literalnie na
  <https://openrouter.ai/models> przy implementacji (stan wiedzy: lipiec 2026); literówka = 404.
- **Limity długości**: generowana treść trafi do `POST /api/news-posts` z walidacją
  `@Size(max=10000)` (tytuł 200). System prompt ma wymuszać tytuł ≤ 200 znaków i treść
  wyraźnie poniżej 10000, akapity rozdzielone pustymi liniami (ekscerpt), czysty tekst bez
  Markdownu (frontend renderuje tekst, nie HTML).

---

## Phase 1: Backend — scraper 90minut + Spring AI 2.0 + endpoint generacji

### Overview

Wpięcie Spring AI 2.0 pod OpenRouter, klienta scrapującego 90minut (jsoup) i wystawienie
adminowego endpointu `POST /api/news-posts/generate` (bez body), który pobiera wynik ostatniego
meczu i zwraca propozycję `{title, content}`. Faza kończy się zielonym `./mvnw test` offline
i ręczną generacją lokalnie z prawdziwym kluczem.

### Changes Required:

#### 1. Zależności Maven

**File**: `backend/pom.xml`

**Intent**: Dodać Spring AI 2.0 (BOM + starter) oraz jsoup do scrapowania.

**Contract**: `<spring-ai.version>2.0.0</spring-ai.version>` (lub nowsza łatka 2.0.x);
import `org.springframework.ai:spring-ai-bom` (`type=pom`, `scope=import`); zależność
`spring-ai-starter-model-openai` bez wersji (snippet: `docs-spring-ai-openai.md` §1).
Dodać `org.jsoup:jsoup` **bez `<version>`** — wersję zarządza Spring Boot BOM (spójnie z resztą
pom). Dyscyplina Boot 4: nazwy artefaktów zweryfikowane pod 2.0 w `research.md` §2.

#### 2. Konfiguracja główna

**File**: `backend/src/main/resources/application.properties`

**Intent**: Konfiguracja OpenRoutera i 90minut w konwencji repo (`${ENV:default}`, komentarz
nad blokiem). Pusty default klucza utrzymuje lokalny start bez sekretu.

**Contract**: właściwości Spring AI (spłaszczone nazwy 2.0, `/v1` obowiązkowe) + parametry
scrapera 90minut (team id + season id + base URL; season id bumpuje się raz na sezon):

```properties
spring.ai.openai.base-url=https://openrouter.ai/api/v1
spring.ai.openai.api-key=${OPENROUTER_API_KEY:}
spring.ai.openai.chat.model=anthropic/claude-sonnet-4.6
spring.ai.openai.chat.temperature=0.7
# Źródło danych meczowych — publiczny, statyczny HTML 90minut.pl (ISO-8859-2, bez auth).
app.ninetyminut.base-url=${NINETYMINUT_BASE_URL:http://www.90minut.pl}
app.ninetyminut.team-id=${NINETYMINUT_TEAM_ID:3154}
# Id sezonu 90minut (schemat nietrywialny: 107=2025/26, 109=2026/27). Zaktualizować raz na sezon.
app.ninetyminut.season-id=${NINETYMINUT_SEASON_ID:107}
```

> Rozszerzenie (opcjonalne, nie w tym slice): dynamiczne rozwiązanie `season-id` z linku
> `mecze_druzyna.php?id=<teamId>&id_sezon=<N>` na stronie klubu (`skarb.php?id_klub=<teamId>`),
> z fallbackiem do poprzedniego sezonu, gdy bieżący nie ma rozegranych meczów.

#### 3. Konfiguracja testowa (shadow)

**File**: `backend/src/test/resources/application.properties`

**Intent**: Zdublować bloki `spring.ai.openai.*` (z celowo pustym kluczem) oraz `app.ninetyminut.*`,
żeby `contextLoads()` wstawał offline w CI — plik zasłania główny w całości; scraper nie
wykonuje sieci przy starcie kontekstu.

**Contract**: te same właściwości co wyżej, `spring.ai.openai.api-key` pusty (tryb no-auth SDK);
`app.ninetyminut.*` z wartościami domyślnymi; komentarz w stylu istniejących komentarzy pliku.

#### 4. Bean ChatClient

**File**: `backend/src/main/java/com/plomienkostrze/news/AiConfig.java` (nowy)

**Intent**: Autokonfigurowany `ChatClient.Builder` → jeden bean `ChatClient` z domyślnym system
promptem: persona redaktora klubowego Płomienia Kostrze piszącego po polsku, z **twardym zakazem
konfabulacji** (tylko fakty z wyniku; nie wymyślać strzelców, minut, kartek, przebiegu gry) i
wymogami formatu (tytuł ≤ 200, treść < 10000, akapity przez puste linie, czysty tekst).

**Contract**: `@Configuration` + `@Bean ChatClient` z `builder.defaultSystem(...)`
(`docs-spring-ai-openai.md` §3). Pakiet `com.plomienkostrze.news`.

#### 5. Klient 90minut (scraper)

**File**: `backend/src/main/java/com/plomienkostrze/news/NinetyMinutClient.java` (nowy)

**Intent**: Pobiera i parsuje stronę meczów drużyny, zwraca ostatni rozegrany mecz jako typowany
rekord. Jedyny punkt styku z 90minut; izolowany, by zmiana struktury HTML dotykała jednej klasy.

**Contract**: `record MatchResult(String opponent, boolean home, int goalsFor, int goalsAgainst,
String competition, LocalDate date, Outcome outcome, String note)` (`note` np. „po karnych 4-3");
metoda `MatchResult fetchLastMatch()`. Buduje URL `…/mecze_druzyna.php?id={teamId}&id_sezon={seasonId}`
z configu; `Jsoup.connect(url).timeout(…).get()`; parsuje tabelę, wybiera najnowszy wiersz z realnym
wynikiem (patrz Critical Implementation Details); wyznacza `home`/`outcome`/`goalsFor` z perspektywy
Płomienia. Brak rozegranego meczu / błąd sieci / niezgodna struktura → dedykowany wyjątek
(np. `MatchDataUnavailableException`), nie `null`.

#### 6. Serwis generacji

**File**: `backend/src/main/java/com/plomienkostrze/news/NewsGenerationService.java` (nowy)

**Intent**: Spina scraper i model: pobiera `MatchResult`, składa prompt użytkownika z zaufanych
pól i woła model blokująco, zwracając typowaną propozycję.

**Contract**: `record ProposalDraft(String title, String content)` + metoda
`ProposalDraft generateFromLastMatch()`; w środku `ninetyMinutClient.fetchLastMatch()` →
prompt użytkownika składany z pól `MatchResult` (bez renderera szablonów) →
`.prompt().user(...).call().entity(ProposalDraft.class)`. Wyjątki scrapera i SDK/Spring AI
propagują do kontrolera.

#### 7. Kontroler generacji

**File**: `backend/src/main/java/com/plomienkostrze/web/NewsGenerationController.java` (nowy)

**Intent**: Adminowy endpoint generacji „z ostatniego meczu", bez body, w konwencji istniejących
kontrolerów (rekordy DTO, `ResponseStatusException`).

**Contract**: `POST /api/news-posts/generate` (bez request body); response
`ProposalResponse(String title, String content)` (200 OK). Brak danych meczu (np. początek sezonu,
`MatchDataUnavailableException`) → 502/424 z czytelnym komunikatem („nie udało się pobrać danych
meczu"); błąd LLM (wyjątek/timeout) → 502 „news generation failed" (bez przenoszenia szczegółów
SDK do klienta). Endpoint niczego nie zapisuje — publikacja to osobne, istniejące
`POST /api/news-posts`.

#### 8. Reguła security

**File**: `backend/src/main/java/com/plomienkostrze/security/SecurityConfig.java`

**Intent**: Jawny matcher dla nowej ścieżki przed `anyRequest().denyAll()` — bez niego endpoint
zwraca 403 (bezpieczny kierunek błędu, ale martwy feature).

**Contract**: `.requestMatchers(HttpMethod.POST, "/api/news-posts/generate").hasRole("ADMIN")`
obok istniejącego matchera POST `/api/news-posts` (`SecurityConfig.java:69`). Istniejące matchery
bez zmian (GET `/api/news-posts/**` pozostaje permitAll — dotyczy tylko GET).

### Success Criteria:

#### Automated Verification:

- Build + test offline zielone: `cd backend && ./mvnw test` (bez `OPENROUTER_API_KEY`
  w środowisku — to zarazem rozstrzygnięcie „pusty klucz" z Open Question #4 researchu;
  scraper nie uderza w sieć przy `contextLoads()`)

#### Manual Verification:

- Slug `anthropic/claude-sonnet-4.6` potwierdzony na openrouter.ai/models
- Scraper lokalnie (unit/manualnie) zwraca poprawny ostatni mecz Płomienia: rywal,
  u siebie/wyjazd, wynik, rozgrywki — zgodne z tym, co widać na 90minut; polskie znaki OK
- Lokalnie z realnym kluczem (`OPENROUTER_API_KEY=… ./mvnw spring-boot:run`) żądanie
  `POST /api/news-posts/generate` (z tokenem admina) zwraca sensowną polską propozycję
  `{title, content}` zgodną z realnym wynikiem; brak zmyślonych strzelców/zdarzeń
- Żądanie bez tokena/bez roli admina → 401/403
- Brak danych meczu (np. wskazany pusty sezon) → czytelny 502/424, nie wiszące żądanie
- Błędny klucz LLM lub brak sieci → 502 w rozsądnym czasie

**Implementation Note**: Po zakończeniu fazy i zielonych kryteriach automatycznych —
pauza na manualne potwierdzenie użytkownika przed przejściem do fazy 2.

---

## Phase 2: Frontend — przycisk „Generuj z ostatniego meczu" w panelu /admin

### Overview

Rozszerzenie istniejącego `AdminPanel` o jeden przycisk generacji: „Generuj z ostatniego meczu"
(z tekstowym wskaźnikiem postępu) → propozycja wypełnia istniejące edytowalne pola tytuł/treść →
„Opublikuj" (istniejąca ścieżka) albo „Odrzuć" (czyści propozycję).

### Changes Required:

#### 1. Warstwa API

**File**: `frontend/src/app/news/news-api.ts`

**Intent**: Nowa metoda `generateFromLastMatch()` w istniejącym `NewsApi` — komponenty nie
dotykają `HttpClient` bezpośrednio (konwencja udokumentowana w pliku). Interceptor dokleja token.

**Contract**: `interface NewsPostProposal { title: string; content: string; }` +
`generateFromLastMatch(): Observable<NewsPostProposal>` → `POST ${apiBaseUrl}/api/news-posts/generate`
(bez body).

#### 2. Panel administratora

**File**: `frontend/src/app/admin/admin-panel.ts` + `admin-panel.html` (+ `admin-panel.scss`)

**Intent**: Druga ścieżka tworzenia wpisu w tym samym komponencie (decyzja z planowania:
rozszerzamy panel, nie nową trasę). Nad istniejącym formularzem — jeden przycisk „Generuj z
ostatniego meczu"; **bez pól danych meczowych i tonu** (backend sam pobiera wynik). Wynik
generacji wypełnia istniejące kontrolki `title`/`content` — edycja przed publikacją i sama
publikacja to już istniejący przepływ S-02, bez zmian.

**Contract**: wzorce z S-02 bez odstępstw — sygnały `generating` i `generationError`, przycisk
z etykietami „Generuj z ostatniego meczu"/„Generowanie…" (disabled w trakcie; NFR widocznego
postępu spełnia stan tekstowy jak `Publikowanie…`), błąd po polsku w konwencji istniejącej
(`Nie udało się wygenerować propozycji (HTTP …)`; osobny komunikat, gdy backend zwróci brak
danych meczu). Po sukcesie: `form.patchValue({title, content})`. Przycisk „Odrzuć" (widoczny,
gdy w polach jest niezapublikowana propozycja) czyści `title`/`content`; ponowna generacja to
znów ten sam przycisk. Publikacja: istniejący `publish()` bez zmian. Spełnione AC US-01: edycja
przed publikacją, odrzucenie bez publikacji, zero automatycznej publikacji.

### Success Criteria:

#### Automated Verification:

- Build frontendu zielony: `cd frontend && npm run build`
- Backend nadal zielony (sanity): `cd backend && ./mvnw test`

#### Manual Verification:

- Lokalne e2e (backend z realnym kluczem + `ng serve`): zalogowany admin klika „Generuj z
  ostatniego meczu", widzi „Generowanie…" przez cały czas oczekiwania, dostaje propozycję
  zgodną z realnym ostatnim meczem, edytuje wynik, publikuje — wpis widoczny na publicznej
  liście i w szczególe bez logowania
- „Odrzuć" czyści propozycję; ponowna generacja działa
- Błąd backendu (ubity backend / zły klucz / brak danych meczu) pokazuje polski komunikat,
  przycisk wraca do używalności
- Niezalogowany/nie-admin nadal jest przekierowywany z `/admin` (istniejący `effect()`)

**Implementation Note**: Po zakończeniu fazy — pauza na manualne potwierdzenie użytkownika
przed przejściem do fazy 3.

---

## Phase 3: Produkcja — sekret, min-instances, weryfikacja e2e

### Overview

Konfiguracja produkcyjna poza CI (wzorzec `db-password` z runbooka deploymentu), sprzężony z
S-03 flip `min-instances` 0→1 i weryfikacja pełnego przepływu na produkcji po merge'u. 90minut
nie wymaga sekretu (publiczne) — jedyny sekret to klucz OpenRoutera.

### Changes Required:

#### 1. Sekret OpenRoutera w GCP (operacja, nie kod)

**Intent**: Klucz OpenRoutera w Secret Manager i podpięty do Cloud Run — jednorazowo, poza CI.

**Contract**: sekret `openrouter-api-key` (decyzja: nazwa `OPENROUTER_API_KEY`, nie `LLM_API_KEY`
z runbooka) + grant `secretAccessor` dla service accountu Cloud Run + `gcloud run services update
plomien-kostrze-api --region europe-central2 --update-secrets=OPENROUTER_API_KEY=openrouter-api-key:latest`
— wg wzorca `db-password` (`context/changes/deployment/deployment-plan.md:369-380`).

#### 2. Flip min-instances 0→1 (operacja, nie kod)

**Intent**: Runbook wiąże flip z wejściem „realnego adminowego flow generacji"
(`deployment-plan.md:224-226, 404-406`) — S-03 to ten moment; chroni NFR-y latencji przed
cold-startem JVM. Świadomy koszt: stały bieg jednej instancji.

**Contract**: `gcloud run services update plomien-kostrze-api --region europe-central2
--min-instances=1` (można połączyć z komendą sekretu).

#### 3. Aktualizacja runbooka deploymentu

**File**: `context/changes/deployment/deployment-plan.md`

**Intent**: Domknąć rozjazd nazw wykryty w researchu: otwarty checkbox klucza LLM i wpis
`LLM_API_KEY=llm-api-key:latest` (`:402-403, 425-427`) poprawić na
`OPENROUTER_API_KEY=openrouter-api-key:latest` i odhaczyć; odhaczyć też flip `min-instances`.

**Contract**: edycja istniejących pozycji checklisty; bez zmian struktury dokumentu.

### Success Criteria:

#### Automated Verification:

- CI na PR zielone (build + test path-filtrowane); merge do `master` uruchamia auto-deploy
  backendu i frontendu (push i PR wykonuje użytkownik)

#### Manual Verification:

- Nowa rewizja Cloud Run ma `OPENROUTER_API_KEY` z Secret Managera i `min-instances=1`
  (widoczne w `gcloud run services describe`)
- Pełne e2e na produkcji: admin klika „Generuj z ostatniego meczu", dostaje propozycję zgodną
  z realnym wynikiem, edytuje, publikuje; wpis widoczny publicznie bez logowania
- Odrzucenie propozycji na produkcji nie zostawia śladu (publiczna lista bez zmian)
- Rollback-safety: poprzednia rewizja działa bez `OPENROUTER_API_KEY` (pusty default w
  properties) — brak zmian schematu ⇒ rollback rewizji jest bezpieczny

---

## Testing Strategy

Zgodnie z posture repo (świadoma decyzja, `research.md` §5): **bez nowych testów automatycznych**;
bramki jakości to:

### Automated (istniejące):

- `./mvnw test` — pełnokontekstowy `contextLoads()` na H2, offline (pusty klucz Spring AI =
  tryb no-auth SDK; scraper nie uderza w sieć przy starcie kontekstu); łapie błędy konfiguracji,
  brakujące beany i niezdublowane właściwości testowe
- `npm run build` — kompilacja/AOT Angulara

### Manual Testing Steps:

1. **Faza 1 (backend, curl/httpie):** scraper zwraca poprawny ostatni mecz (rywal, dom/wyjazd,
   wynik); generacja z realnym kluczem (poprawna polska notka bez zmyślonych strzelców); brak
   tokena → 401/403; brak danych meczu → 502/424; zły klucz → 502 bez wiszenia
2. **Faza 2 (lokalne e2e):** pełny przepływ „Generuj z ostatniego meczu" → edytuj → publikuj →
   widoczne publicznie; odrzuć → czyści; błąd → polski komunikat i odzyskanie przycisku
3. **Faza 3 (produkcja):** przepływ jak wyżej na produkcji + weryfikacja konfiguracji rewizji
   (`gcloud run services describe`)

## Performance Considerations

- Scrape 90minut to jedno lekkie żądanie HTTP + parsowanie (ms); generacja to
  sekundy–kilkanaście sekund. Blokujące `.call()` na MVC jest akceptowalne przy jednym
  administratorze i kilku generacjach tygodniowo. NFR postępu spełnia stan `generating`
  + etykieta „Generowanie…"; NFR „bez nieokreślonego oczekiwania" — ograniczone timeouty
  scrapera (jsoup) i transportu LLM (OkHttp).
- `min-instances=1` eliminuje cold-start JVM (5–15 s) na ścieżce adminowej i publicznej.

## Migration Notes

Brak migracji bazy w tym slice (decyzja: persystujemy tylko zaakceptowane propozycje, istniejącą
ścieżką publikacji). Następna wolna migracja pozostaje **V6** — dla S-04 lub przyszłych rozszerzeń
S-03 (np. persystowane szkice, albo przechwyt składów/strzelców z ŁNP przez przeglądarkę admina).

## References

- Research wewnętrzny: `context/changes/gated-news-generation/research.md`
- Wybór bibliotek: `context/changes/gated-news-generation/research-libraries.md`
- Źródło danych meczowych i granice: `context/changes/gated-news-generation/research-scraping-90minut.md`
- Dokument użycia Spring AI 2.0: `context/changes/gated-news-generation/docs-spring-ai-openai.md`
- Wzorce S-02 (auth, kontroler, panel admina): `context/archive/2026-07-06-manual-news-publishing/plan.md`
- Mechanika sekretów i min-instances: `context/changes/deployment/deployment-plan.md:358-431`
- Decyzje OpenRouter/Firebase: `context/foundation/tech-stack-backend.md:77-101`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles.

### Phase 1: Backend — scraper 90minut + Spring AI 2.0 + endpoint generacji

#### Automated

- [x] 1.1 Build + test offline zielone: `cd backend && ./mvnw test` (bez klucza w środowisku) — b9ac18d

#### Manual

- [x] 1.2 Slug `anthropic/claude-sonnet-4.6` potwierdzony na openrouter.ai/models — b9ac18d
- [x] 1.3 Scraper zwraca poprawny ostatni mecz (rywal, dom/wyjazd, wynik, rozgrywki); polskie znaki OK — b9ac18d
- [x] 1.4 Lokalna generacja z realnym kluczem: polska notka zgodna z wynikiem, bez zmyślonych strzelców/zdarzeń — b9ac18d
- [x] 1.5 Bez tokena/roli admina → 401/403 — b9ac18d
- [x] 1.6 Brak danych meczu → czytelny 502/424 (nie wiszące żądanie) — b9ac18d
- [x] 1.7 Zły klucz/brak sieci LLM → 502 w rozsądnym czasie — b9ac18d

### Phase 2: Frontend — przycisk „Generuj z ostatniego meczu" w panelu /admin

#### Automated

- [x] 2.1 Build frontendu zielony: `cd frontend && npm run build`
- [x] 2.2 Backend sanity: `cd backend && ./mvnw test`

#### Manual

- [x] 2.3 Lokalne e2e: „Generuj z ostatniego meczu" → „Generowanie…" → edytuj → publikuj → widoczne publicznie
- [x] 2.4 „Odrzuć" czyści propozycję; ponowna generacja działa
- [x] 2.5 Błąd backendu (zły klucz / brak danych meczu) → polski komunikat, przycisk odzyskuje używalność
- [x] 2.6 Nie-admin nadal przekierowywany z `/admin`

### Phase 3: Produkcja — sekret, min-instances, weryfikacja e2e

#### Automated

- [ ] 3.1 CI na PR zielone; merge uruchamia auto-deploy (push/PR wykonuje użytkownik)

#### Manual

- [ ] 3.2 Rewizja Cloud Run ma `OPENROUTER_API_KEY` i `min-instances=1`
- [ ] 3.3 Pełne e2e na produkcji: generuj z ostatniego meczu → edytuj → publikuj → widoczne publicznie
- [ ] 3.4 Odrzucenie na produkcji bez śladu w publicznej liście
- [ ] 3.5 Runbook deploymentu zaktualizowany (nazwa sekretu + odhaczone checkboxy)
