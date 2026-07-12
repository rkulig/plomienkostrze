# League Table (S-05) — Plan Brief

> Full plan: `context/changes/league-table/plan.md`
> Research: `context/changes/league-table/research.md`

## What & Why

Kibice Płomienia nie mają w aplikacji aktualnej tabeli ligi — dziś muszą szukać jej na
90minut.pl. Ten slice dodaje publiczną zakładkę **"Tabela"**, która scrapuje aktualne
standings z 90minut.pl (tak jak S-03 scrapuje wynik meczu) i pokazuje je gościom bez
logowania. To pierwszy publiczny „content tab" w aplikacji.

## Starting Point

S-03 zbudował już wzorzec scrapingu 90minut (jsoup, `ISO-8859-2`, config
`app.ninetyminut.*`, wyjątek przy błędzie) dla strony meczów drużyny. S-01 dał wzorzec
publicznego read-API (kontroler→repo, `record` DTO, `permitAll()` w fail-closed
`SecurityConfig`) i publicznych widoków Angular 22 (standalone, signals, lazy routes).
Brak dotąd: paska zakładek dla gościa i jakiegokolwiek stylowania `<table>`.

## Desired End State

Gość widzi w nagłówku zakładkę **"Tabela"**; po kliknięciu widzi aktualną tabelę ligi —
pozycja, drużyna, mecze, punkty — scrapowaną na żywo z 90minut.pl i cache'owaną ~15 min
po stronie serwera. Gdy źródło jest niedostępne lub nie da się sparsować, zakładka
pokazuje czytelny komunikat błędu zamiast zepsutej tabeli.

## Key Decisions Made

| Decision | Choice | Why | Source |
| --- | --- | --- | --- |
| Przechowywanie danych | Transient scrape + cache ~15 min in-memory | Najprościej; bez DB/migracji/stale-policy dla jednej małej strony | Plan |
| Konfiguracja strony ligi | Env-overridable pełny URL (`app.ninetyminut.league-url`) | Zmiana sezonu/klasy = zmiana jednej zmiennej; URL koduje dywizję (Płomień awansował) | Plan |
| Kolumny | Compact: Poz · Drużyna · M · Pkt | Czytelne na mobile, minimalne parsowanie | Plan |
| Stan pusty/błąd | Pokaż co zescrapowano (w tym zera przedsezonowe); błąd przy awarii scrapa | Uczciwe i proste; zera to realny stan w lipcu | Plan |
| Testy parsera | Brak nowych testów | Świadomy wybór zakresu | Plan |
| Podświetlenie Płomienia | Nie | Neutralna tabela | Plan |
| Kształt pod S-06 | Wydzielony, reużywalny fetch strony; teraz tylko standings | Honoruje wspólny fetch z roadmapy przy niskim koszcie | Plan |

## Scope

**In scope:**
- Backend: `NinetyMinutLeagueClient` (fetch-seam + parser standings), cache'ujący
  `LeagueService`, publiczny `GET /api/league-table`, klucze config, wpis w security.
- Frontend: feature `league/` (serwis API + model + komponent), route `/tabela`,
  pierwsza zakładka nawigacji gościa, pierwsze stylowanie `<table>`.

**Out of scope:**
- Baza/migracja/encja (dane transient), scheduled refresh, `@EnableCaching`.
- Terminarz/fixtures (to S-06 — zostaje tylko reużywalny seam fetch).
- W-R-P, bramki, różnica, splity dom/wyjazd; podświetlenie Płomienia.
- Nowe testy automatyczne; dane z `laczynaspilka.pl` (za reCAPTCHA).

## Architecture / Approach

`GET /api/league-table` → `LeagueService` (TTL cache) → na miss `NinetyMinutLeagueClient`
(jsoup: fetch strony ligi → parse standings) → `List<StandingRow>`. Kontroler mapuje
`StandingRow` na `record` DTO; awarię scrapa mapuje na `502`, żeby SPA wyrenderowało stan
błędu jak w widokach news. Frontendowy `LeagueApi` woła endpoint, komponent `LeagueTable`
trzyma stan w signalach i renderuje `<table>`. Cache trzyma ruch gości z dala od 90minut.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Backend | Cache'owany publiczny `GET /api/league-table` scrapujący standings | Selektory tabeli standings (parser) — najbardziej podatne na drift |
| 2. Frontend | Zakładka "Tabela" renderująca standings | Pierwsze `<table>` + pierwsza nawigacja gościa; responsywność na mobile |

**Prerequisites:** S-01 i S-03 (done). jsoup już w `backend/pom.xml`. Origin backendu już
skonfigurowany w `environment.ts` — bez zmian env po stronie frontu.
**Estimated effort:** ~1–2 sesje, 2 fazy.

## Open Risks & Assumptions

- **Kruchość parsera:** strona ma ~60 tabel; standings identyfikujemy po nagłówku
  (`Nazwa`/`Pkt.`) i linku drużyny `a.main[href*=skarb.php]`. Zmiana struktury 90minut
  cicho zepsuje parsowanie — brak testu (świadoma decyzja), łapane w PR review + manualnej
  weryfikacji.
- **Domyślny URL** wskazuje na sezon 2026/27 (`liga14875`), który w lipcu jest
  przedsezonową tabelą samych zer — to poprawny bieżący stan, ale gość zobaczy zera do
  startu rozgrywek.
- **Bez testu authz dla nowego endpointu:** endpoint dodaje `permitAll()`, ale nie jest
  asertowany w `AuthorizationMatrixTest` (brak nowych testów). Tani przyszły safeguard.
- Kontrakt DTO backend↔frontend (`{ position, team, played, points }`) musi się zgadzać.

## Success Criteria (Summary)

- Gość otwiera "Tabela" i widzi aktualną tabelę ligi (Poz · Drużyna · M · Pkt) zgodną z
  90minut.pl, bez logowania.
- Gdy 90minut jest niedostępne, zakładka pokazuje komunikat błędu, nie zepsutą tabelę.
- Brak regresji w istniejących widokach aktualności; `mvn test` i `npm run build` zielone.
