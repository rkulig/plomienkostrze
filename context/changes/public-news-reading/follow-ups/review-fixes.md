# Follow-ups z przeglądu implementacji (impl-review 2026-07-05)

## Do planu S-02 (narzędzia administratora)

- **F3 — inwariant `published_at` dla statusu `PUBLISHED`**: schemat `news_posts`
  dopuszcza `published_at IS NULL` (celowo, pod przyszłe szkice), ale wiersz
  `PUBLISHED` z NULL-em sortowałby się **pierwszy** (Postgres `DESC` = NULLS FIRST)
  i renderował pustą datę w SPA. Wymaganie dla S-02: **akcja publikacji musi ustawiać
  `published_at`** (i/lub S-02 dokłada `CHECK (status <> 'PUBLISHED' OR published_at
  IS NOT NULL)` w swojej migracji). Źródło: `reviews/impl-review.md` F3, Fix A.
