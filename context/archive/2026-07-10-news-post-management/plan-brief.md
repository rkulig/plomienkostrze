# News Post Management (edit + delete) — Plan Brief

> Full plan: `context/changes/news-post-management/plan.md`

## What & Why

Slice **S-04** domyka cykl życia wpisu aktualności: administrator może **edytować** treść opublikowanego wpisu (FR-007) i **usunąć** wpis błędny (FR-008). Przed publicznym startem daje to możliwość poprawy literówek i wycofania błędnego wpisu z publicznej strony klubu.

## Starting Point

CRUD wpisów żyje bezpośrednio w `NewsPostController` (bez warstwy serwisowej): istnieje create i read, **brak** update/delete. Encja `NewsPost` jest immutable-style (same gettery, fabryka `published(...)`, brak setterów, brak `updatedAt`/`@Version`). Frontend ma jedynie publiczne widoki listy/szczegółu i formularz tworzenia (`AdminPanel`) — brak listy admina i brak wzorca dialogu/modala.

## Desired End State

Na publicznym widoku szczegółu administrator (i tylko on) widzi akcje **Edytuj** i **Usuń**. Edytuj otwiera wypełniony formularz `admin/edit/:id` i po zapisie wraca na wpis z nową treścią, natychmiast widoczną dla gości. Usuń pyta o potwierdzenie inline, a po akceptacji fizycznie kasuje wpis i wraca na listę. Brak warstwy cache — zmiany są widoczne od razu.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Semantyka usuwania | Hard delete (`deleteById`) | Zgodne z „warstwa danych celowo prosta"; bez nowych kolumn/statusów | Plan |
| Śledzenie edycji | `updated_at` (V6) zapisywane, nieпokazywane | Tanie future-proofing; `publishedAt` bez zmian → brak reorderu | Plan |
| Współbieżność | Last-write-wins (bez `@Version`) | Produkt jednoadministratorowy — brak realnego konfliktu | Plan |
| Umiejscowienie akcji | Publiczny widok szczegółu, gated `isAdmin` | Detail już ładuje pełną treść — naturalne miejsce edycji; najmniejsza powierzchnia | Plan |
| Formularz edycji | Nowy komponent + trasa `admin/edit/:id` | Rozdziela create (z generacją AI) od edycji — prościej | Plan |
| Potwierdzenie usunięcia | Dwustopniowe inline | Brak infra dialogów w repo; minimalne, stylowalne | Plan |
| Testy | Ręczna weryfikacja per faza | Konwencja S-01→S-03; strategia testów to Moduł 3 | Plan |

## Scope

**In scope:** `PUT`/`DELETE /api/news-posts/{id}`; migracja V6 (`updated_at`); metoda `edit()` na encji; matchery bezpieczeństwa; `NewsApi.update()`/`delete()`; komponent `admin/edit/:id`; akcje Edytuj/Usuń na `NewsDetail` z potwierdzeniem.

**Out of scope:** soft-delete/kosz/undo; nowe wartości `NewsPostStatus`; optimistic locking; publiczne „edytowano"; dashboard admina; reużywalny modal/toast; testy automatyczne.

## Architecture / Approach

Cienki pion, faza po fazie: **backend** dokłada dwa endpointy na istniejący zasób `/api/news-posts/{id}` (pierwsza metoda mutująca encji + migracja V6 + DTO `UpdateRequest` + reguły security), **frontend** dokłada metody do `NewsApi` i UI (nowy komponent edycji + gated akcje na szczegółzie), **produkcja** weryfikuje po merge/auto-deploy. Interceptor autoryzuje nowe wywołania „za darmo"; brak cache = natychmiastowa widoczność.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Backend | `PUT`/`DELETE` + V6 + `edit()` + security | Zmiana encji vs `ddl-auto=validate` — migracja V6 musi pasować |
| 2. Frontend | `NewsApi.update/delete`, `admin/edit/:id`, akcje na szczegółzie | Gating admina i dwustopniowe potwierdzenie od zera (brak wzorca) |
| 3. Produkcja | Deploy + e2e na publicznym URL | Auto-deploy na Cloud Run `plomien-api`; PR/push robi użytkownik |

**Prerequisites:** S-02 (`manual-news-publishing`, done) — auth admina i model wpisu już wpięte; jest co edytować.
**Estimated effort:** ~2–3 sesje na 3 fazy.

## Open Risks & Assumptions

- Last-write-wins: przy hipotetycznym drugim adminie równoległa edycja cicho nadpisuje — akceptowalne przy jednym adminie.
- Migracje/CHECK nie są ćwiczone w CI (H2, Flyway wyłączony) — walidacja V6 potwierdzana dopiero przy realnym starcie (lokalnie/prod).
- Hard delete jest nieodwracalny — brak „cofnij" dla omyłkowego usunięcia (świadomy koszt MVP).

## Success Criteria (Summary)

- Administrator edytuje opublikowany wpis, a zmiana jest natychmiast widoczna publicznie (bez zmiany pozycji na liście).
- Administrator usuwa wpis po potwierdzeniu, a wpis znika z publicznej listy i szczegółu.
- Gość/nie-admin nie widzi akcji i nie może wywołać endpointów (401/403).
