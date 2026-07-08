# Research: pomysł „generuj wpis ze scrapowanych danych meczowych" (90minut.pl)

> Data: 2026-07-08. Zewnętrzny research (exa.ai) + weryfikacja na żywo źródeł.
> Kontekst: pomysł zmiany kierunku S-03 — zamiast „surowy tekst + ton → wpis",
> generować wpis **jednym kliknięciem z ostatniego meczu**, zaciągając dane ze
> strony. Bramka akceptacji przed publikacją zostaje.

## TL;DR — kluczowy wniosek

Premisa „na 90minut.pl jest wynik + składy + strzelcy + zmiany" **dla poziomu
Płomienia Kostrze (Klasa A / Klasa okręgowa) nie jest prawdziwa**. 90minut.pl na
tym szczeblu publikuje wyłącznie: **datę, rozgrywki+kolejkę, gospodarzy, gości i
wynik końcowy**. Składy, strzelców i zmiany 90minut **linkuje na zewnątrz** do
`laczynaspilka.pl`, a ten portal jest bramkowany (Keycloak Bearer + reCAPTCHA),
więc nie da się go zescrapować prostym HTTP.

Wniosek praktyczny: „jedno kliknięcie → pełny wpis z składami i strzelcami"
**nie jest osiągalne z darmowych, legalnie dostępnych źródeł**. Realny jest
lżejszy wariant: scrape 90minut po **wynik + rywala + rozgrywki + datę** i tym
zasilić generację (resztę koloru dopisuje LLM albo admin).

## Co dokładnie sprawdzono (dowody)

### 90minut.pl — źródło łatwe, ale ubogie

- **Technologia:** klasyczny serwer Apache/PHP, **HTML renderowany po stronie
  serwera**, kodowanie `ISO-8859-2`. Zero JS do danych → idealny cel dla
  parsera HTML (jsoup). Brak `robots.txt` (404).
- **Strona drużyny:** `http://www.90minut.pl/mecze_druzyna.php?id=3154&id_sezon=<S>`
  (Płomień = `id=3154`; sezon 2025/26 = `id_sezon=107`, 2026/27 = `109`).
  Zwraca tabelę meczów: `data | rozgrywki | gospodarze | wynik | goście`.
  Przykładowy wiersz: `2025-08-23 17:45 | VII liga, Kolejka 1 | Płomień Kostrze | 6-0 | Dąbski KS Kraków`.
- **Strona ligi:** `http://www.90minut.pl/liga/1/liga14612.html` (Klasa A
  2025/26 gr. Kraków III; 2026/27 = `liga14875.html`). Zawiera tabelę i terminarz
  kolejek z wynikami — ale każdy wynik to link **wychodzący** do
  `laczynaspilka.pl/rozgrywki/mecz/<uuid>`.
- **Weryfikacja braku detalu:** przeszukanie HTML ligi po `składy`, `zmiany`,
  `żółt`, `czerwon`, `sędzia` → **0 trafień**. Słowo „strzelcy" pojawia się tylko
  w tytułach linków nawigacyjnych, nie jako dane. Brak stron `mecz.php?id_mecz=`
  dla tego klubu — 90minut nie ma tu podstron pojedynczego meczu.

### laczynaspilka.pl — źródło bogate, ale zamknięte

- **Technologia:** Angular SPA (`main.<hash>.js`, webpack). Surowy HTML to pusta
  skorupa — `curl`/jsoup dostają `<title>Rozgrywki</title>` i nic więcej.
- **API odkryte w bundlu:**
  `https://competition-api-pro.laczynaspilka.pl/api/bus/competition/v1/`
  z endpointami `matches/{matchId}` i `matches/{matchId}/events` (dokładnie
  składy/zdarzenia/strzelcy).
- **Bramka:** każde wywołanie API bez tokenu → **HTTP 401 `WWW-Authenticate:
  Bearer`**. Token wydaje Keycloak (`login.laczynaspilka.pl`, realm `PZPN`,
  client `PJS`), a wejście dodatkowo chroni **reCAPTCHA**: endpoint
  `Authorize/recaptcha` w headless Chrome zwrócił **403 „Token validation
  unsuccessful"**. Czyli: nawet z prawdziwą przeglądarką bez ważnego tokenu
  reCAPTCHA danych nie ma. Automatyzacja wymagałaby łamania reCAPTCHA — krucho,
  wbrew regulaminowi ŁNP, do wyrzucenia.

## Biblioteki (zgodne z tech-stack-backend.md: Spring Boot, Java 21, Maven)

Dla scrapowania 90minut **nie trzeba nic ciężkiego**:

- **jsoup** — standard do statycznego HTML w Javie (potwierdzone w wielu
  źródłach 2026). Jedna zależność, fetch + parsowanie + selektory CSS.
  Uwaga na kodowanie: parsować jako `ISO-8859-2` (jsoup wykryje z nagłówka, ale
  warto wymusić). To wszystko, czego potrzeba dla danych 90minut.
- **Odrzucone:** Playwright/Selenium/HtmlUnit (headless browser) — potrzebne
  tylko dla SPA/JS. Dokładałyby „podatek footprintu JVM + przeglądarka" obok
  Cloud Run; sensowne wyłącznie gdyby ktoś chciał iść w laczynaspilka, czego
  **odradzam** (reCAPTCHA). Wzorzec sidecar (osobny kontener-scraper) to
  przerost formy przy jednym prostym źródle.

To spina się z istniejącym `research-libraries.md`: **Spring AI (OpenAI starter)
→ OpenRouter** do generacji zostaje bez zmian; scraping to dodatkowo tylko jsoup.

## Wpływ na S-03 — uczciwa ocena pomysłu

1. **„Rezygnujemy z tonu, jedno kliknięcie z ostatniego meczu"** — wykonalne, ale
   input LLM będzie chudy: `Płomień 6-0 Dąbski KS, VII liga Kolejka 1, 2025-08-23`.
   Bez składów/strzelców model będzie *konfabulował* szczegóły (kto strzelił) —
   to wprost zagraża Kryterium sukcesu (75% akceptacji), bo admin będzie odrzucał
   wpisy ze zmyślonymi nazwiskami. Trzeba by promptować model, żeby trzymał się
   wyłącznie wyniku i rywala.
2. **Bramka akceptacji zostaje** — zgodne z rdzeniem S-03, żadnej zmiany tu nie ma.
3. **Konflikt z PRD/roadmapą:** roadmap ma w `Parked` pozycję *„Automatyczne
   pobieranie danych meczowych z zewnętrznych źródeł — PRD §Non-Goals:
   administrator wprowadza dane ręcznie"*. Ten pomysł **odwraca udokumentowaną
   decyzję produktową**. To nie znaczy „nie" — znaczy, że to zmiana zakresu,
   którą trzeba świadomie przeprowadzić (kandydat na `/10x-frame`), a nie
   doklejka w planie S-03.

## Rekomendacja

- **Nie budować pełnej automatyzacji „jedno kliknięcie → gotowy wpis ze składami"**
  — dane źródłowe tego nie udźwigną (90minut ubogie, laczynaspilka zamknięte).
- **Wariant realny (hybryda):** przycisk „Zaciągnij ostatni mecz" scrapuje z
  90minut (jsoup) wynik+rywala+rozgrywki+datę i **prefilluje** formularz S-03;
  admin uzupełnia strzelców/kontekst luźnym tekstem (jak w oryginalnym S-03),
  potem generacja → bramka akceptacji. Zachowuje wygodę „z ostatniego meczu" bez
  ryzyka konfabulacji i bez łamania reCAPTCHA.
- **Zanim to wejdzie do planu:** przepuścić przez `/10x-frame` (to zmiana Non-Goal
  z PRD) i zaktualizować roadmapę/PRD, żeby decyzja była jawna.

## Aktualizacja 2026-07-08 — test logowania (opcja D rozstrzygnięta: NIE)

Zweryfikowano na żywo (sterowana przeglądarka + konto ŁNP użytkownika), czy
zalogowanie omija bramkę. **Nie omija.**

Ustalenia:

1. **Logowanie działa** — Keycloak `login.laczynaspilka.pl` (realm `PZPN`),
   standardowy formularz, poprawne dane → sesja SSO aktywna.
2. **Token dla klienta `PJS` da się wydać bez reCAPTCHA** — przez SSO
   `prompt=none` + PKCE dostaliśmy `access_token` (JWT), `refresh_token`,
   `expires_in=3600`. Czyli sesja SSO działa poprawnie.
3. **Ale `competition-api-pro` odrzuca ten token — HTTP 401.** API danych meczowych
   ma **własnego wystawcę tokenów, niezależnego od Keycloak**.
4. **Jedyna droga do tokenu `competition-api` to reCAPTCHA.** W bundlu
   (`getTokenRequest`): `recaptchaV3Service.execute("token")` →
   `GET Authorize/recaptcha` z nagłówkiem `x-recaptcha-key: <token reCAPTCHA v3>`
   → zwraca bearer używany do API. **Nie ma wariantu dla zalogowanych** — konto
   PZPN jest kompletnie nieistotne dla dostępu do danych rozgrywek.

**Wniosek twardy:** bramką jest wyłącznie **reCAPTCHA v3** (score'owa, niewidzialna).
- W **Twojej realnej przeglądarce** score jest wysoki → token wydany → dane płyną
  (dlatego strona działa Ci normalnie).
- W **headless / z datacenter** (czyli backend na Cloud Run) score jest niski →
  `403 „Token validation unsuccessful"`. Potwierdzone wielokrotnie.
- Token `Authorize/recaptcha` jest krótkotrwały, a jego odświeżenie **znów wywołuje
  reCAPTCHA** → jednorazowe przechwycenie tokenu i używanie go po stronie serwera
  też nie przetrwa.

### Co z tego wynika dla realizacji (personal use)

Dane MOŻNA pobierać, ale **tylko z zaufanego, prawdziwego kontekstu przeglądarki**
(Twojej), nie z serwerowego scrapera. Warianty od najlepszego:

- **A. Rozszerzenie / userscript (Tampermonkey) w Twojej przeglądarce.** Gdy
  wchodzisz na mecz w ŁNP, skrypt przechwytuje **gotowy JSON** z
  `competition-api-pro…/matches/{uuid}` i `.../events` (składy, strzelcy, zdarzenia)
  i POST-uje go do Twojego backendu. reCAPTCHA przechodzi sama (jesteś realnym
  userem). Zero łamania zabezpieczeń, zero kruchości. **Rekomendacja.**
- **B. Lokalny Playwright/Puppeteer w trybie NIE-headless z Twoim realnym profilem
  Chrome, na Twoim łączu domowym.** reCAPTCHA v3 oceni to jako człowieka;
  skrypt przechwytuje bearer i woła API albo czyta wyrenderowany DOM. Uruchamiane
  na Twoim PC, **nie na Cloud Run**. Działa, ale bardziej krucho niż A.
- **C. Serwis rozwiązujący reCAPTCHA (2captcha/CapSolver) + site-key
  `6Le308YkAAAA…`.** Technicznie działa z backendu, ale płatne, kruche i **wprost
  łamie regulamin ŁNP**. Odradzam.
- **NIE: serwerowy headless na Cloud Run** — zablokowany reCAPTCHA na starcie.

### Wpływ na architekturę S-03

Dane meczowe wchodzą do backendu **wypchnięte z Twojej przeglądarki/lokalnego
narzędzia jako JSON** (model „push"), a nie zaciągane przez backend („pull").
Backend: przyjmuje JSON meczu → mapuje na strukturę → generuje wpis (Spring AI →
OpenRouter) → bramka akceptacji. Warstwa generacji i bramka bez zmian. jsoup nadal
przydatny do 90minut jako lekkie, publiczne źródło samego wyniku/rywala (fallback
albo wzbogacenie).

### Bezpieczeństwo

Hasło do konta ŁNP zostało podane jawnie w rozmowie — **należy je zmienić.**

## Źródła

- 90minut.pl — weryfikacja na żywo: strona drużyny `id=3154`, ligi `liga14612`/
  `liga14875` (2026-07-08).
- laczynaspilka.pl — analiza bundla Angular + test API (401 Bearer,
  403 reCAPTCHA) w headless Chrome (2026-07-08).
- [Web Scraping in Java (2026) — jsoup jako standard dla statycznego HTML](https://fastcrw.com/blog/web-scraping-in-java)
- [Best Web Scraping Libraries for Spring Boot (jsoup/HtmlUnit/Selenium)](https://dev.to/antozanini/best-web-scraping-libraries-for-spring-boot-2l47)
- [Java Web Scraping Guide 2026 — kiedy jsoup, kiedy Playwright](https://nodemaven.com/blog/java-web-scraping-guide/)
