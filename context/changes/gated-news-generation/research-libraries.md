# Research: biblioteki/rozwiązania dla S-03 (gated-news-generation)

> Data: 2026-07-08. Wejście do `/10x-plan gated-news-generation`.
> Zakres: integracja OpenRouter z backendem (Spring Boot, Java 21, Maven) i UX
> generowania po stronie frontendu (Angular), zgodnie z
> `context/foundation/tech-stack.md` i `tech-stack-backend.md`.

## Rekomendacja — TL;DR

Nie potrzeba żadnej egzotycznej biblioteki: **Spring AI ze starterem OpenAI**
obsłuży OpenRouter po stronie backendu (OpenRouter jest API-kompatybilny z
OpenAI), a po stronie Angulara wystarczy natywny `EventSource`/`fetch`
opakowany w RxJS — ewentualnie mała biblioteka `@microsoft/fetch-event-source`,
jeśli zapadnie decyzja o streamingu przez chroniony endpoint.

## Backend (Spring Boot, Java 21, Maven)

### 1. Spring AI — `spring-ai-starter-model-openai` (rekomendowane)

Integracja z OpenRouter sprowadza się do konfiguracji, zero kodu specyficznego
dla dostawcy:

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENROUTER_API_KEY}     # z Secret Managera, jak zapisano w tech-stack-backend.md
      base-url: https://openrouter.ai/api/v1
      chat:
        options:
          model: anthropic/claude-sonnet-4   # zmiana modelu = zmiana stringa, pod eksperyment 75%
```

To wprost realizuje decyzję z `tech-stack-backend.md` („switching the
generation model is a config-string change"). Spring AI daje dodatkowo trzy
rzeczy, które S-03 bezpośrednio wykorzysta:

- **`ChatClient` + prompt templates** — czyste miejsce na prompt „dane meczowe
  + ton/styl → wpis po polsku".
- **Structured output**: `.call().entity(ProposalDraft.class)` — model zwraca
  JSON zgodny ze schematem wygenerowanym z rekordu Java (np.
  `record ProposalDraft(String title, String content)`), więc tytuł i treść
  propozycji przychodzą jako typowany obiekt, nie tekst do parsowania.
  Uwaga: `.entity()` działa tylko na `.call()`, nie na `.stream()` — przy
  streamingu trzeba by składać JSON po stronie serwera albo streamować czysty
  tekst.
- **Streaming**: `.stream().content()` zwraca `Flux<String>`, który wystawia
  się jako SSE (`SseEmitter` w Spring MVC albo `Flux` z `text/event-stream`).

**Zastrzeżenie wersji:** w Spring AI 1.0.x był znany bug deserializacji
`finish_reason: "end_turn"` przy modelach Claude przez OpenRouter
([spring-ai#1522](https://github.com/spring-projects/spring-ai/issues/1522)).
W **Spring AI 1.1+/2.0** działa out-of-the-box z
`base-url: https://openrouter.ai/api/v1` — brać aktualną wersję przez
`spring-ai-bom`.

### 2. Alternatywy (świadomie odrzucone)

- **LangChain4j** — dojrzała (1.x), ale jej przewaga to niezależność od
  frameworka (Quarkus, Micronaut, plain Java). Skoro stack to Spring Boot,
  Spring AI daje to samo z natywną autokonfiguracją, observability i mniejszym
  tarciem.
- **Gołe wywołanie HTTP (`RestClient` / oficjalny SDK OpenAI Java)** — najmniej
  zależności, ale traci się structured output, streaming helpers i abstrakcję
  nad promptem; przy planowanym eksperymentowaniu z modelami to fałszywa
  oszczędność.

## Frontend (Angular)

NFR wymaga „widocznego postępu każdej operacji > ~2 s" — do spełnienia na dwa
sposoby:

- **Wariant minimalny (bez nowych bibliotek):** zwykły `HttpClient.post()` +
  sygnał `loading` i spinner/skeleton z Angular Material. Generacja to jedno
  żądanie, admin widzi progres. Do MVP w zupełności wystarcza.
- **Wariant streaming (lepszy odbiór, trochę więcej pracy):** backend streamuje
  tokeny przez SSE, front pokazuje rosnący tekst propozycji. Haczyk: natywny
  `EventSource` **nie pozwala ustawić nagłówka `Authorization`**, a endpoint
  generowania jest adminowy (Firebase ID token). Rozwiązanie:
  **`@microsoft/fetch-event-source`** (mała, popularna paczka) — obsługuje POST
  z body i własne nagłówki, łatwo opakować w RxJS Observable. Alternatywnie
  query-param z tokenem (brzydkie) albo natywny `fetch` + ręczne parsowanie
  strumienia.

## Pozostałe elementy S-03 — bez nowych bibliotek

- **Bramka akceptacji** (propozycja → edycja → akceptuj/odrzuć): zwykły stan
  encji w PostgreSQL (`PROPOSED / ACCEPTED / REJECTED`) przez istniejące
  Spring Data JPA + migracja Flyway.
- **Licznik do progu 75%** (Unknown z roadmapy): kolumny/wiersze zdarzeń
  akceptacji-odrzucenia w tej samej bazie; do odczytu wystarczy jedno zapytanie
  agregujące — telemetria zewnętrzna nie jest potrzebna na tę skalę.

Konkretny model LLM zostaje do wyboru w `/10x-plan gated-news-generation`,
zgodnie z roadmapą — powyższy setup czyni tę decyzję odwracalną (jeden string
w `application.yml`).

## Źródła

- [Spring AI + OpenRouter — tutorial (BootcampToProd)](https://bootcamptoprod.com/integrate-openrouter-with-spring-ai/)
- [spring-ai#1522 — bug finish_reason, fix od 1.1/2.0](https://github.com/spring-projects/spring-ai/issues/1522)
- [Structured Output Converter — docs Spring AI](https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html)
- [Streaming SSE z ChatClient (devglan)](https://www.devglan.com/spring-ai/streaming-ai-responses-sse-spring-ai-chatclient)
- [Spring AI 1.1 vs LangChain4j vs direct API — porównanie 2026 (JavaCodeGeeks)](https://www.javacodegeeks.com/2026/03/choosing-a-java-llm-integration-strategy-in-2026-spring-ai-1-1-vs-langchain4j-vs-direct-api-calls.html)
- [Reactive chat Angular + Spring WebFlux/SSE (Bytz Echo)](https://bytzecho.com/tutorials/angular/reactive-chat-angular-primeng-spring-webflux)
