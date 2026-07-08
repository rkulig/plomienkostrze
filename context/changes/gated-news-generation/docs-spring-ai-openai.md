# Dokumentacja: Spring AI — starter OpenAI (pod OpenRouter) dla S-03

> Data: 2026-07-08 (zaktualizowane tego samego dnia po `research.md`). Źródło:
> Context7 MCP — oficjalna referencja Spring AI **2.0**
> (docs.spring.io/spring-ai/reference/2.0 + upgrade-notes 2.0).
> Uzupełnienie `research-libraries.md` (tam: wybór biblioteki; tu: jak jej użyć).

> **Wersja (krytyczne):** backend jest na Spring Boot **4.1.0**
> (`backend/pom.xml:8`), a Spring AI 1.1.x wspiera tylko Boot 3.5.x — wymagany
> jest **Spring AI 2.0** (GA 2026-06-12, budowany pod Boot 4.0/4.1, sam
> podciąga Boot 4.1.0). Pierwsza wersja tego dokumentu była pisana z referencji
> 1.1; poniższe snippety są skorygowane pod 2.0. Uzasadnienie: `research.md`.

## 1. Zależności (Maven, `backend/pom.xml`)

BOM + starter. Uwaga: stara nazwa `spring-ai-openai-spring-boot-starter` jest
przestarzała — aktualny artefakt to `spring-ai-starter-model-openai`
(nazwa potwierdzona w getting-started 2.0). `${spring-ai.version}` = **2.0.0**
(lub nowsza łatka 2.0.x).

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>${spring-ai.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>
```

## 2. Konfiguracja — endpoint zgodny z OpenAI (OpenRouter)

Endpoint OpenAI-kompatybilny wskazuje się przez `spring.ai.openai.base-url`.
W 2.0 nazwy właściwości chat spłaszczono — **bez segmentu `.options`**
(stare `spring.ai.openai.chat.options.*` są deprecated, jeszcze działają):

```properties
spring.ai.openai.base-url=https://openrouter.ai/api/v1
spring.ai.openai.api-key=${OPENROUTER_API_KEY:}
spring.ai.openai.chat.model=<model — wybór w /10x-plan>
spring.ai.openai.chat.temperature=0.7
```

> **Rozstrzygnięte (2.0):** `base-url` **musi** zawierać `/v1`. Od 2.0.0-M5
> moduł OpenAI stoi na oficjalnym SDK `openai-java`, które dokleja tylko
> `chat/completions`; właściwość `spring.ai.openai.chat.completions-path`
> została **usunięta**. Gołe `https://openrouter.ai/api` dawałoby 404
> (spring-ai#6036, PR #6093).

Klucz z env/Secret Managera, zgodnie z `tech-stack-backend.md`. Dwie uwagi
operacyjne pod nasz codebase:

- **Testy:** `backend/src/test/resources/application.properties` zasłania
  główny plik — trzeba tam zdublować `spring.ai.openai.api-key` (i `base-url`),
  inaczej `contextLoads()` w CI może paść. Pusty klucz
  (`spring.ai.openai.api-key=`) przełącza SDK w tryb bez nagłówka
  `Authorization` — kontekst wstaje bez sieci i sekretu.
- **Włączanie autokonfiguracji** modeli w 2.0 idzie przez top-level
  `spring.ai.model.chat` (stare `spring.ai.openai.chat.enabled` usunięte);
  transport HTTP (OkHttp z SDK) customizuje się beanami
  `OpenAiHttpClientBuilderCustomizer`.

## 3. ChatClient — bean i wywołanie

Spring Boot autokonfiguruje `ChatClient.Builder`; domyślny system prompt
ustawia się raz, w konfiguracji:

```java
@Configuration
class AiConfig {

    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder
            .defaultSystem("…persona redaktora klubowego…")
            .build();
    }
}
```

Najprostsze wywołanie — odpowiedź jako `String`:

```java
String content = chatClient.prompt()
    .user(userInput)
    .call()
    .content();
```

Programowe tworzenie (gdyby było kilka modeli):

```java
ChatClient chatClient = ChatClient.create(myChatModel);
// lub
ChatClient custom = ChatClient.builder(myChatModel)
    .defaultSystem("You are a helpful assistant.")
    .build();
```

## 4. Szablony promptów z parametrami — „dane meczowe + ton/styl"

Fluent API wstrzykuje zmienne do szablonu przez `.param()`; zmienne w `{}`:

```java
String proposal = chatClient.prompt()
    .user(u -> u.text("""
            Napisz wpis aktualności na podstawie danych meczowych.
            Ton/styl: {tone}
            Dane meczowe: {matchData}
            """)
        .param("tone", tone)
        .param("matchData", rawMatchData))
    .call()
    .content();
```

Domyślny renderer to StringTemplate (`StTemplateRenderer`) z delimiterami
`{}`. Istotne dla S-03, bo **surowe dane meczowe mogą zawierać nawiasy
klamrowe** — wtedy albo zmienić delimitery:

```java
.templateRenderer(StTemplateRenderer.builder()
    .startDelimiterToken('<')
    .endDelimiterToken('>')
    .build())
```

albo użyć `NoOpTemplateRenderer` i skleić prompt samodzielnie.

## 5. Structured output — propozycja jako typowany obiekt

Zamiast `.content()` można użyć `.entity(...)` — odpowiedź parsowana do
rekordu Javy (np. tytuł + treść propozycji):

```java
record ProposalDraft(String title, String content) {}

ProposalDraft draft = chatClient.prompt()
    .user(...)
    .call()
    .entity(ProposalDraft.class);
```

(Docs pokazują też advisor `ENABLE_NATIVE_STRUCTURED_OUTPUT` dla modeli z
natywnym structured outputem; `.entity()` bez advisora działa przez
Structured Output Converter.) Ograniczenie znane z research:
`.entity()` działa na `.call()`, nie na `.stream()`.

## Pokrycie S-03

Powyższe domyka ścieżkę backendową generacji: zależność → konfiguracja pod
OpenRouter → bean `ChatClient` → prompt z parametrami (dane meczowe, ton) →
odpowiedź (String lub typowany draft). Bramka akceptacji to logika domenowa
poza Spring AI — propozycja wraca jako draft, publikacja idzie ścieżką z S-02.
