---
change_id: news-post-management
title: Administrator edytuje i usuwa opublikowane wpisy
status: implementing
created: 2026-07-10
updated: 2026-07-10
archived_at: null
---

## Notes

Slice **S-04** z `context/foundation/roadmap.md`.

- **Outcome:** Administrator poprawia treść opublikowanego wpisu oraz usuwa wpis błędny — zmiany natychmiast widoczne w publicznych aktualnościach.
- **PRD refs:** FR-007 (edycja), FR-008 (usuwanie).
- **Prerequisites:** S-02 (`manual-news-publishing`, done) — musi istnieć co edytować; auth administratora już wpięty.
- **Parallel with:** S-03 (`gated-news-generation`, done).
- **Risk:** Domknięcie cyklu życia wpisu; nie blokuje gwiazdy przewodniej, a przed publicznym startem daje administratorowi możliwość wycofania błędnego wpisu.
