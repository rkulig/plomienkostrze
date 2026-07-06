---
change_id: manual-news-publishing
title: Manual news publishing
status: impl_reviewed
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
- 2026-07-06 (Faza 3): prod `authDomain` wrócił na `plomien-kostrze.firebaseapp.com`
  (odejście od decyzji z planu „authDomain = web.app"). Powód: uporczywy
  `redirect_uri_mismatch` — redirect URI `web.app/__/auth/handler` dodany do
  klienta OAuth nie propagował (sonda endpointu autoryzacji potwierdzała błąd
  po stronie Google), a po przejściu na popup same-origin przestał być potrzebny.
  Wpisy `web.app` w kliencie OAuth można w przyszłości usunąć albo wykorzystać,
  gdyby wrócił wariant redirect.
