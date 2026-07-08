# Gated News Generation (S-03) — Plan Brief

> Full plan: `context/changes/gated-news-generation/plan.md`
> Research: `context/changes/gated-news-generation/research.md` (+ `research-libraries.md`, `docs-spring-ai-openai.md`)

## What & Why

Administrator wprowadza surowe dane meczowe i opcjonalny ton/styl, otrzymuje
wygenerowaną przez LLM propozycję wpisu, edytuje ją i publikuje po jawnej akceptacji —
albo odrzuca. To gwiazda przewodnia roadmapy (S-03): najmniejszy przepływ dowodzący
rdzenia wartości produktu — czy generowane propozycje są na tyle dobre, że admin
akceptuje ≥ 75% z nich. Nic nigdy nie trafia do publicznych aktualności samoczynnie
(guardrail PRD).

## Starting Point

S-02 dostarczył pełną ścieżkę adminową: logowanie Firebase, `ROLE_ADMIN` z allowlisty,
panel `/admin` z formularzem tytuł/treść i publikacją przez `POST /api/news-posts`.
W kodzie nie ma żadnej integracji LLM. Research zweryfikował wersje: Boot 4.1.0 wymaga
**Spring AI 2.0** (nie 1.1), a `base-url` OpenRoutera musi zawierać `/v1`.

## Desired End State

Admin na produkcji wkleja dane meczowe, klika „Generuj propozycję", widzi wskaźnik
postępu, dostaje polską propozycję w edytowalnych polach formularza, poprawia i
publikuje — wpis natychmiast czytelny dla gościa. Odrzucenie niczego nie zapisuje;
można generować ponownie. CI i testy pozostają zielone bez sieci i sekretów.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Biblioteka LLM | Spring AI 2.0 (starter OpenAI) | Jedyna linia zgodna z Boot 4.1.0; OpenRouter jest OpenAI-kompatybilny | Research |
| Model | `anthropic/claude-sonnet-4.6` | Najlepszy start pod próg 75% (polska proza); zmiana = 1 string w properties | Plan |
| Sekret/env | `OPENROUTER_API_KEY` / `openrouter-api-key` | Zgodne z tech-stack; runbook (mówiący `LLM_API_KEY`) dostaje korektę | Plan |
| Trwałość propozycji | Tylko zaakceptowane; propozycja żyje w kliencie | Akceptacja = istniejący `POST /api/news-posts`; **bez migracji V6 i nowych statusów** | Plan |
| Licznik 75% | Poza aplikacją — nie implementujemy | Użytkownik prowadzi statystykę samodzielnie, poza kodem | Plan |
| Ton/styl | Jedno wolne pole tekstowe (opcjonalne) | Język PRD („żartobliwie po wygranej…"); presety usztywniałyby rdzeń wartości | Plan |
| Miejsce w UI | Rozszerzenie istniejącego panelu `/admin` | Wspólny formularz publikacji i ochrona już na miejscu; najmniejsza powierzchnia zmian | Plan |
| Synchroniczność | Blokujące `.call()` + tekstowy wskaźnik postępu | Grain servletowy repo; streaming to świadome rozszerzenie, nie warunek wejścia | Research |
| Testy | Bez nowych testów automatycznych | Posture repo: build + `contextLoads()` (H2, offline, pusty klucz) + weryfikacja manualna | Research |

## Scope

**In scope:** zależność Spring AI 2.0 + konfiguracja (main + shadow test-properties);
bean `ChatClient` (persona redaktora, PL); `NewsGenerationService` (structured output
`{title, content}`, prompt składany poza rendererem — dane mogą zawierać `{}`);
`POST /api/news-posts/generate` za `hasRole("ADMIN")`; rozszerzenie `AdminPanel`
(dane meczowe + ton → generuj → edytowalna propozycja → publikuj/odrzuć); sekret w
Secret Manager + flip `min-instances` 0→1; korekta runbooka.

**Out of scope:** migracje/nowe statusy, licznik 75% w aplikacji, streaming/SSE,
presety tonu, lista szkiców propozycji, edycja/usuwanie wpisów (S-04), import plików,
nowe testy automatyczne.

## Architecture / Approach

SPA (`AdminPanel` + `NewsApi`, token z interceptora) → `POST /api/news-posts/generate`
(ADMIN) → `NewsGenerationService` → `ChatClient.call().entity()` → OpenRouter
(`/api/v1`, Claude Sonnet 4.6) → `{title, content}` do klienta. Propozycja żyje tylko
w formularzu; „Opublikuj" idzie istniejącą ścieżką S-02, „Odrzuć" czyści pola. Baza
danych nietknięta.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Backend: Spring AI + endpoint | Działająca generacja przez curl; `./mvnw test` zielone offline | Rozjazdy wersji/właściwości 2.0 (zmitigowane researchem); timeout SDK do zweryfikowania |
| 2. Frontend: przepływ w /admin | Pełny lokalny flow generuj → edytuj → publikuj/odrzuć | Rosnący komponent panelu; UX błędów generacji |
| 3. Produkcja: sekret + min-instances + e2e | Feature żywy na produkcji; runbook domknięty | Operacje poza CI (ręczne gcloud); stały koszt min-instances=1 |

**Prerequisites:** S-02 na produkcji (jest); klucz OpenRoutera do ręki (fazy 1 i 3);
dostęp gcloud (faza 3).
**Estimated effort:** ~3 sesje (jedna na fazę); faza 3 to głównie operacje + weryfikacja.

## Open Risks & Assumptions

- **Jakość polskiej prozy Sonneta 4.6 to hipoteza eksperymentu 75%** — jeśli nie
  dowiezie, zmiana modelu to jeden string w properties (po to jest OpenRouter).
- Slug modelu do literalnego potwierdzenia na openrouter.ai/models przy implementacji.
- Brak trwałości propozycji: odświeżenie strony traci wygenerowany tekst — świadomy
  koszt; jeśli zaboli, przyszła iteracja doda persystencję (statusy enuma czekają).
- Domyślny timeout transportu SDK nieznany — do zweryfikowania i ewentualnego
  ograniczenia w fazie 1 (NFR „bez nieokreślonego oczekiwania").

## Success Criteria (Summary)

- Admin przechodzi na produkcji pełną ścieżkę „dane → propozycja → edycja → akceptacja
  → publikacja", a gość czyta wpis bez logowania (Primary z PRD, e2e).
- Żadna propozycja nie publikuje się bez jawnej akceptacji; odrzucenie nie zostawia śladu.
- CI/testy zielone offline bez sekretu; rollback rewizji bezpieczny (zero zmian schematu).
