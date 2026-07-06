---
change_id: manual-news-publishing
title: Manual news publishing
status: implementing
created: 2026-07-06
updated: 2026-07-06
archived_at: null
---

## Notes

<!-- Free-form notes for this change: links, ad-hoc context, decisions that don't belong in research/frame/plan. -->

- 2026-07-06 (Faza 2): zmaterializowało się ryzyko z planu — `signInWithRedirect`
  na localhost gubił wynik logowania (cross-origin authDomain, blokada 3rd-party
  storage); zastosowano przewidziany fallback `signInWithPopup` (jedna linia,
  dotyczy też produkcji — popup działa niezależnie od domeny).
