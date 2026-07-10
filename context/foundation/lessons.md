# Lessons Learned

> Append-only register of recurring rules and patterns. Re-read at start by /10x-frame, /10x-research, /10x-plan, /10x-plan-review, /10x-implement, /10x-impl-review.

## Manualną weryfikację backendu wykonaj sam, nie rozpisuj jej userowi

- **Context**: Faza weryfikacji manualnej w /10x-implement dla backendu (endpointy Spring Boot chronione Firebase JWT). Repo plomienkostrze: backend na Postgresie (localhost:5432, baza/user/hasło `plomien`), sekrety w `.env.local` (ADMIN_UIDS, PLOMIEN_ADMIN_TOKEN).
- **Problem**: Domyślnie rozpisywałem userowi krok-po-kroku setup + curle i czekałem, choć całość mogę wykonać sam narzędziami. To niepotrzebnie przerzuca pracę na usera i wydłuża pętlę; user musi ręcznie robić to, co agent zrobi szybciej.
- **Rule**: Gdy plan wymaga manualnej weryfikacji backendu, wykonaj ją sam zamiast oddawać userowi: wystaw jednorazowego Postgresa w Dockerze (`docker run` z bazą/user/hasłem `plomien` na porcie 5432), uruchom backend w tle sourcując `.env.local` (żeby ADMIN_UIDS wpięło rolę admina), odpal curle pokrywające wszystkie kody (401/403/400/404/200/204) i widoczność po mutacji, a na końcu posprzątaj kontener i zatrzymaj backend. Od usera potrzebujesz tylko świeżego tokena Firebase admina (wygasa ~1 h) — poproś o niego wprost; przypadek 403 (zalogowany nie-admin) zrób restartem backendu z `ADMIN_UIDS=""`.
- **Applies to**: implement, impl-review

## Manualną weryfikację frontendu: sam postaw stack, userowi zostaw tylko kliknięcia wymagające jego loginu

- **Context**: Faza manualnej weryfikacji frontendu w /10x-implement (repo plomienkostrze: Angular `ng serve` na :4200, Spring Boot na :8080, Postgres na :5432). Scenariusze e2e admina (edycja/usuwanie wpisów) wymagają zalogowanego admina przez Firebase — login żyje w przeglądarce usera.
- **Problem**: Domyślnie prosiłem usera, żeby sam uruchomił backend i frontend, choć cały stack mogę postawić narzędziami. User musiał mi wskazać „uruchom za mnie" — niepotrzebnie przerzucona praca.
- **Rule**: Gdy plan wymaga manualnej weryfikacji frontendu, sam postaw pełny lokalny stack zamiast oddawać to userowi: Postgres w Dockerze (baza/user/hasło `plomien` na :5432), backend w tle sourcując `.env.local` (ddl-auto=validate → Flyway przejedzie migracje, w tym seedy), `npm start` (po `nvm use`). Odpal build produkcyjny i sprawdź, że wstaje, zanim oddasz sterowanie. Userowi zostaw wyłącznie kliknięcia e2e wymagające jego sesji (login admina przez Firebase). Po testach posprzątaj: zatrzymaj oba serwery i usuń kontener Postgresa.
- **Applies to**: implement, impl-review
