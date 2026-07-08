---
change_id: gated-news-generation
title: Gated news generation
status: impl_reviewed
created: 2026-07-08
updated: 2026-07-08
---

## Notes

<!-- Free-form notes for this change: links, ad-hoc context, decisions that don't belong in research/frame/plan. -->

- Realizuje S-03 z `context/foundation/roadmap.md` (gwiazda przewodnia; PRD: US-01, FR-003–FR-005).
- 2026-07-08: w folderze istniały już dwa artefakty researchu zewnętrznego
  (`research-libraries.md`, `docs-spring-ai-openai.md`) — folder utworzony przed
  `change.md`; ten plik dopisany przy `/10x-research`.
- 2026-07-08: `research.md` (research wewnętrzny + weryfikacja wersji) wykrył, że
  `docs-spring-ai-openai.md` był pisany pod Spring AI 1.1, a backend (Spring Boot
  4.1.0) wymaga Spring AI 2.0 — szczegóły i korekty w `research.md`.
- 2026-07-08: `/10x-plan` — plan + brief napisane. Decyzje upraszczające (użytkownik):
  propozycje NIE są persystowane (zapis tylko przy akceptacji, istniejącą ścieżką
  publikacji ⇒ bez migracji V6 i nowych statusów), licznik 75% poza aplikacją.
  Model: `anthropic/claude-sonnet-4.6`; sekret: `OPENROUTER_API_KEY`.
- 2026-07-08: `/10x-research` (follow-up) wykrył rozjazd input S-03: plan/docs
  zakładały „surowe dane + ton", a `roadmap.md` (ten sam dzień) ustalił „wynik z
  90minut, bez tonu". Decyzja (użytkownik): idziemy wg roadmapy. **`plan.md`
  przepisany pod scrape wyniku z 90minut** — jeden przycisk „Generuj z ostatniego
  meczu", scraper jsoup (team+season w configu; `id=3154`, sezon bumpowany raz na
  rok), guardrail przeciw konfabulacji strzelców. `docs-spring-ai-openai.md` bez
  zmian — mechanika Spring AI 2.0 niezależna od kształtu wejścia.
- 2026-07-08: `/10x-impl-review` (fazy 1–2) — raport `reviews/impl-review.md` (APPROVED,
  1 warning). F1/Fix A: operacje fazy 3 (sekret + min-instances) wykonano przed formalnym
  startem fazy — potwierdzone `gcloud describe` (rewizja `plomien-api-00010-x7d`);
  odhaczono 3.2/3.5, edycja runbooka wcommitowana. Uwaga: usługa Cloud Run nazywa się
  `plomien-api`, nie `plomien-kostrze-api` jak w kontrakcie fazy 3 planu.
