<!-- PLAN-REVIEW-REPORT -->
# Plan Review: Publiczna lista i widok opublikowanych aktualności

- **Plan**: context/changes/public-news-reading/plan.md
- **Mode**: Deep
- **Date**: 2026-07-05
- **Verdict**: REVISE → SOUND (po naprawach z triage)
- **Findings**: 1 critical, 1 warning, 1 observation

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| End-State Alignment | PASS |
| Lean Execution | PASS |
| Architectural Fitness | PASS |
| Blind Spots | WARNING |
| Plan Completeness | FAIL (naprawione w triage) |

## Grounding

8/8 paths ✓, 5/5 symbols ✓, brief↔plan ✓. Zweryfikowane bezpośrednio w kodzie:
CORS obejmuje `/api/**` z konfigurowalnymi originami (twierdzenie „CorsConfig bez
zmian" trafne), wzorce probe'a (rekordy DTO + `ResponseStatusException`,
`@PrePersist` + `Instant` ↔ `TIMESTAMPTZ`) zgodne z opisem, rewrite SPA w
`firebase.json` obecny (produkcyjny deep-link zadziała), frontend faktycznie bez
speców, sekcja `## Progress` mechanicznie spójna z fazami.

## Findings

### F1 — Kryterium 3.3 (grep „zero trafień") jest niespełnialne

- **Severity**: ❌ CRITICAL
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Plan Completeness
- **Location**: Phase 3 — Success Criteria (Automated) + Progress 3.3
- **Detail**: Grep obejmował cały `backend/src`, gdzie na stałe zostają
  `V1__create_test_messages.sql` (niemutowalna historia Flyway — usunięcie
  złamałoby walidację checksumy na istniejącej bazie) i nowa
  `V4__drop_test_messages.sql`, która sama zawiera frazę `test_messages`.
  Kryterium nigdy nie byłoby zielone i zablokowałoby `/10x-implement` w Fazie 3.
- **Fix**: Grep zawężony do `frontend/src` + `backend/src/main/java` (katalog
  migracji celowo poza zakresem); kryterium 3.3 poprawione spójnie w Success
  Criteria i w `## Progress`.
- **Decision**: FIXED

### F2 — „Cloud Run ma min-instances=1" — repo mówi co innego

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Current State Analysis + Performance Considerations (nośnik
  kryterium „pierwsza treść < 2 s")
- **Detail**: Plan podawał `min-instances=1` jako fakt, ale `deployment-plan.md`
  deployował celowo z `--min-instances=0` („stays 0 for Phase C"; cost-guard:
  „still shows min-instances=0"), a workflow CI (`backend.yml`) nie przekazuje
  flagi — konfiguracja serwisu przenosi się między rewizjami. `infrastructure.md`
  ustala `min-instances=1` jako decyzję MVP, ale nic w repo nie potwierdza
  wykonania. Przy 0 cold start JVM (5–15 s) obala kryterium NFR na produkcji.
- **Fix**: Current State i Performance Considerations skorygowane (decyzja ≠
  stan); do Fazy 3 dopisany krok manualny „sprawdź `gcloud run services
  describe`, jeśli 0 → `--min-instances=1`" (nowy Progress 3.5, dalsze
  przenumerowane na 3.6/3.7); brief zsynchronizowany.
- **Decision**: FIXED

### F3 — Pusty stan „Brak aktualności" bez ścieżki weryfikacji

- **Severity**: ℹ️ OBSERVATION
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Blind Spots
- **Location**: Phase 2 — kontrakt komponentu listy vs Success Criteria
- **Detail**: Kontrakt `NewsList` wymagał pustego stanu „Brak aktualności", ale
  żadne kryterium go nie sprawdzało — seed gwarantuje 4 wpisy, więc pustka nigdy
  naturalnie nie wystąpi (zatrzymany backend testuje błąd, nie pustkę).
- **Fix**: Dodany krok manualny w Fazie 2: tymczasowy `UPDATE news_posts SET
  status = 'HIDDEN'` w lokalnej bazie → `/` pokazuje pusty stan → przywrócenie
  `PUBLISHED` (nowy Progress 2.6, deep-link przenumerowany na 2.7).
- **Decision**: FIXED
