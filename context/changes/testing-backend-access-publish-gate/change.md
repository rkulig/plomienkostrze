---
change_id: testing-backend-access-publish-gate
title: Backend test harness — bramka dostępu, gate publikacji i CORS/security
status: impl_reviewed
created: 2026-07-11
updated: 2026-07-11
archived_at: null
---

## Notes

Faza 1 rolloutu testów z `context/foundation/test-plan.md`: postawić runner
backendu (integration MockMvc / @WebMvcTest) i dowieść najtańszą warstwą trzy
ryzyka; wpiąć CI gate „backend tests".

Ryzyka i intencja ochrony (response intent, nie anchor — anchory z /10x-research):

- **#1 (High × High) — autoryzacja.** Gość i zalogowany kibic dostają 403 na
  każdym write / generate / publish / delete; tylko admin przechodzi. Macierz
  ról × endpoint. Wyzwanie: „happy-path logowania admina dowodzi, że kibic jest
  odcięty". Anti-pattern: testować wyłącznie ścieżkę admina, over-mock filtra
  bezpieczeństwa.
- **#2 — gate publikacji.** Publiczny odczyt zwraca wyłącznie `PUBLISHED`;
  generacja tworzy draft i niczego nie publikuje. Wyzwanie: „201/200 z generacji
  znaczy, że wpis jest widoczny publicznie". Anti-pattern: assert skopiowany z
  logiki statusu (oracle problem).
- **#4 — CORS/security.** Preflight/CORS przepuszcza legalny origin, a chroniony
  endpoint dalej wymaga ważnego tokenu. Wyzwanie: „ustawiony nagłówek CORS = auth
  działa". Anti-pattern: assert na samym nagłówku bez sprawdzenia ścieżki auth.

Test types: integration (MockMvc), @WebMvcTest. Stack już obecny w
`backend/pom.xml` (JUnit 5 + Mockito + MockMvc + AssertJ).
