# Handoff: Serwis kibica „Płomień Kostrze"

## Overview
Single Page Application — serwis dla kibiców klubu piłkarskiego **Płomień Kostrze** (liga okręgowa, Kraków). Główny widok to **aktualności** dodawane przez administratora po zalogowaniu. Wersja 2 dodaje nawigację zakładkową: **Aktualności, Tabela, Terminarz, Forum**. Logowanie odbywa się przez konto Google; administrator (konkretny adres e-mail) dostaje uprawnienia do publikowania postów, w tym generowania treści przez AI.

## About the Design Files
Pliki w tej paczce to **referencje projektowe stworzone w HTML** — prototypy pokazujące zamierzony wygląd i zachowanie, **nie** kod produkcyjny do skopiowania 1:1. Zadaniem jest **odtworzenie tych widoków w docelowej aplikacji** przy użyciu jej istniejącego środowiska i wzorców (React, Vue, Angular, itp.). Jeśli aplikacja nie ma jeszcze frontendu, wybierz najodpowiedniejszy framework i zaimplementuj w nim te widoki.

Prototyp jest zbudowany jako pojedynczy komponent z lekką warstwą reaktywną (`support.js`) — traktuj go jako opis UI + logiki, nie jako zależność. Dane (aktualności, forum, sesja) trzymane są w `localStorage`; w produkcji zastąp je realnym backendem/API.

## Fidelity
**High-fidelity (hifi)** — finalne kolory, typografia, odstępy i interakcje. Odtwórz UI wiernie, używając bibliotek i wzorców z docelowego repo. Dane tabeli/terminarza oraz wątki forum są przykładowe — podmień na realne źródła.

---

## Design Tokens

### Kolory (barwy klubu z logo)
| Rola | Hex |
|---|---|
| Żółty (akcent główny, CTA) | `#F5C518` (hover `#ffd633`) |
| Podświetlenie aktywnej zakładki (neutralne) | tło `rgba(255,255,255,0.12)`, tekst `#ffffff`; hover `rgba(255,255,255,0.06)` |
| Zielony (klub, „done"/pozytyw) | `#1E8A3C` (jasny wariant tekstu `#3fb15f`, hover przycisku `#23a447`) |
| Czerwony (akcent „najnowsze", alerty) | `#D91E2A` |
| Tło strony | `#0e0e0e` |
| Tło nagłówka/stopki | `#111` |
| Tło sekcji „band" | `#121212` |
| Tło kart (forum/tabela/wiersze) | `#161616` / `#141414` |
| Obramowania | `#242424`, `#333`, `#262626`, `#2c2c2c` |
| Tekst podstawowy | `#f4f4f4` |
| Tekst wtórny / muted | `#bdbdbd`, `#9a9a9a`, `#8a8a8a`, `#7a7a7a` |
| Tekst na kartach (jasnych aktualności) | `#141414` / `#3a3a3a` / `#4a4a4a` |
| Błąd | `#ff5a5a` |
| Google „G" badge | `#1a73e8` |
| Awatary forum (paleta hash po nicku) | `#1E8A3C`, `#D91E2A`, `#E0A400`, `#2f74d0`, `#9b59b6`, `#0f8f86` |

Pasek tricolor (dekor): `linear-gradient(90deg,#1E8A3C 0 33.33%,#F5C518 33.33% 66.66%,#D91E2A 66.66% 100%)`, wysokość 4–5px. W stopce kolejność odwrócona (czerwony→żółty→zielony).

### Typografia (Google Fonts)
- **Anton** — nagłówki, tytuły, przyciski CTA, liczby. `font-weight:400`, `text-transform:uppercase`, `letter-spacing:0.5px`. Rozmiary: H1 hero `clamp(44px,8vw,88px)`; tytuły sekcji `clamp(26px,4vw,38px)`; tytuł karty aktualności `22px`; tytuł featured `clamp(26px,4vw,40px)`.
- **Barlow Condensed** — etykiety, metadane, nawigacja, przyciski wtórne. `font-weight:600/700`, `text-transform:uppercase`, `letter-spacing:1–3px`. Rozmiary 12–16px.
- **Barlow** — tekst treści (body). `font-weight:400/500/600/700`. Body 14–17px, `line-height:1.5–1.65`.

Import: `https://fonts.googleapis.com/css2?family=Anton&family=Barlow:wght@400;500;600;700&family=Barlow+Condensed:wght@600;700&display=swap`

### Odstępy / promienie / cienie
- Kontener główny: `max-width:1120px`, padding poziomy `20px`. Forum węższe: `max-width:920px`.
- Border-radius: karty `12–14px`, przyciski `6–8px`, pigułki `20px`, inputy `8px`, awatary `50%`.
- Cienie: featured `0 14px 40px rgba(0,0,0,0.4)`; karta `0 8px 24px rgba(0,0,0,0.3)`; modal `0 24px 70px rgba(0,0,0,0.7)`.
- Siatka aktualności: `grid-template-columns:repeat(auto-fill,minmax(300px,1fr));gap:22px`.

---

## Screens / Views

### Wspólne: Nagłówek (sticky)
- Tło `#111`, `position:sticky;top:0;z-index:40`.
- **Rząd 1** (`max-width:1120px`, padding `12px 20px`, flex space-between, wrap):
  - Lewa: logo w kółku `52px` (`border-radius:50%`, `box-shadow:0 0 0 2px #F5C518`) + nazwa „PŁOMIEŃ KOSTRZE" (Anton 24px, `#F5C518`) i podtytuł „LIGA OKRĘGOWA • KRAKÓW" (Barlow Condensed 13px, `#3fb15f`).
  - Prawa (nav, flex gap 10px):
    - Wylogowany: przycisk **Zaloguj** (outline żółty: `border:2px solid #F5C518`, Anton 14px; hover wypełnia żółtym).
    - Zalogowany: dyskretny tekst „ZALOGOWANY JAKO {nick}" (Barlow Condensed 12px, `#7a7a7a`) + przycisk **Wyloguj** (outline szary, hover czerwony).
    - Admin dodatkowo: przycisk **+ Dodaj post** (żółte wypełnienie, Anton 14px).
- **Rząd 2 — zakładki** (border-top `1px solid #1e1e1e`, flex gap 6px, padding `8px 14px`, `overflow-x:auto`): Aktualności / Tabela / Terminarz / Forum. Każda zakładka to „pill" (`border-radius:8px`, padding `9px 16px`). Aktywna: **neutralne podświetlenie** tłem `rgba(255,255,255,0.12)`, tekst biały `#ffffff`; nieaktywna: tło przezroczyste, tekst `#9a9a9a` (hover `rgba(255,255,255,0.06)`). Barlow Condensed 16px uppercase. **Bez** paska w barwach klubowych.
- **Pasek tricolor** 4px na dole nagłówka.

### Wspólne: Stopka
- Tło `#111`, tricolor 4px (odwrócony) u góry. Rząd (padding `26px 20px`, space-between):
  - Lewa: logo `38px` + „PŁOMIEŃ KOSTRZE" (Anton 16px).
  - Prawa (text-align right): etykieta „Stadion" i adres „ul. Krzewowa 9c, 30-380 Kraków" — obie w tym samym neutralnym kolorze `#9a9a9a` (Barlow Condensed 13px).

### 1. Aktualności (zakładka domyślna)
- **Hero**: tło `radial-gradient(120% 140% at 100% 0%, #1c1c1c, #0e0e0e 60%)`, padding `56px 20px 64px`. Lewa: eyebrow „SERWIS KIBICA" (`#3fb15f`), H1 „AKTUALNOŚCI **KLUBU**" (słowo „KLUBU" w `#D91E2A`), akapit motto (`#bdbdbd`, `clamp(16px,2.4vw,20px)`). Prawa: logo w kółku `clamp(160px,22vw,260px)` z poświatą (`box-shadow:0 0 0 6px rgba(245,197,24,0.15),0 24px 60px rgba(0,0,0,0.6)`), ukryte na wąskich ekranach.
- **Lista wpisów** (`padding:40px 20px 72px`): nagłówek „OSTATNIE WPISY" (Anton, `#F5C518`) + licznik po prawej („N wpisów", polska odmiana).
- **Featured** (najnowszy, jeśli włączone `highlightLatest`): pełnej szerokości biała karta (`#fff`, `border-top:6px solid #D91E2A`), badge „NAJNOWSZE" (czerwony), data (zielona), tytuł (Anton `clamp(26px,4vw,40px)`, `#141414`), treść (`white-space:pre-wrap`), „Autor: {nick}".
- **Siatka kart**: białe karty (`border-top:5px solid #F5C518`), data zielona, tytuł Anton 22px, treść, „Autor:" u dołu.
- **Empty state**: gdy brak wpisów — ramka `dashed`, „BRAK AKTUALNOŚCI".

### 2. Tabela
- **Band tytułowy**: eyebrow „LIGA OKRĘGOWA • KRAKÓW", H1 „TABELA".
- **Tabela** w kontenerze `#161616` (`border:1px solid #242424`, `overflow-x:auto`, `min-width:660px`): kolumny **# / Klub / M / W / R / P / Bramki / +/- / Pkt**. Nagłówki Barlow Condensed 12px `#8a8a8a`. Wiersze oddzielone `border-top:1px solid #222`. **Wiersz Płomienia podświetlony** tłem `#1E8A3C`, tekst biały; pozostałe punkty w kolumnie Pkt na żółto `#F5C518`.
- Legenda pod tabelą (`#7a7a7a`, 13px): objaśnienie skrótów + „Dane przykładowe".

### 3. Terminarz
- **Band tytułowy**: eyebrow „SEZON 2025/2026", H1 „TERMINARZ".
- Dwie sekcje: **„Nadchodzące mecze"** i **„Rozegrane mecze"** (nagłówki Anton 24px `#F5C518`).
- Wiersz meczu: flex, `border:1px solid #242424`, `border-radius:10px`, padding `14px 18px`; mecze Płomienia mają tło `#181818`. Lewa: kolejka (zielona dla nadchodzących / szara dla rozegranych) + data. Środek: gospodarz — `VS`/wynik — gość (nazwa Płomienia pogrubiona `#F5C518`). Prawa: godzina (Anton `#F5C518`) dla nadchodzących lub badge wyniku (`#F5C518` tło, ciemny tekst) + „Koniec" dla rozegranych.

### 4. Forum
- **Band tytułowy**: eyebrow „SPOŁECZNOŚĆ DZIKÓW", H1 „FORUM KIBICÓW". Kontener `max-width:920px`.
- **Widok listy** (`notInThread`):
  - Rząd: „TEMATY" (Anton `#F5C518`) + przycisk **+ Nowy temat** (zielony, tylko zalogowani).
  - Niezalogowani: notka „Zaloguj się, aby zakładać tematy i odpowiadać na forum." (`#161616`).
  - Karty wątków (`button`, `#161616`, `border-radius:12px`, hover `border-color:#F5C518`): rząd tytuł (Anton 19px) + pigułka „N odpowiedzi" (żółte tło, ciemny tekst, `border-radius:20px`); snippet treści (`-webkit-line-clamp:2`, `#a8a8a8`); meta „autor • data" (`#3fb15f`).
  - Empty state: ramka dashed „Brak tematów. Załóż pierwszy!".
- **Widok wątku** (`inThread`):
  - Link „‹ WRÓĆ DO TEMATÓW" (`#3fb15f`).
  - Karta wątku (`#161616`, `border-top:5px solid #F5C518`): tytuł (Anton `clamp(22px,4vw,30px)`), meta „autor • data" (zielona), treść (`white-space:pre-wrap`).
  - Nagłówek „ODPOWIEDZI (N)".
  - Lista odpowiedzi: karty `#141414` (`border-radius:10px`), meta „autor • data" (`#9a9a9a`), treść. Gdy brak: „Brak odpowiedzi — bądź pierwszy!".
  - Formularz odpowiedzi (tylko zalogowani): `textarea` (`#0e0e0e`, `border:2px solid #333`) + przycisk **Odpowiedz** (żółty, wyrównany do prawej). Niezalogowani: notka o logowaniu.

### Modale (overlay `rgba(0,0,0,0.72)`, karta `#1a1a1a`, `border-radius:14px`, tricolor 5px u góry, klik w tło zamyka, klik w kartę = `stopPropagation`)

**A. Logowanie (Google)** — `max-width:420px`:
- Tytuł „ZALOGUJ SIĘ", opis. Lista kont Google (przyciski `#0e0e0e`, hover żółte obramowanie): kolorowy awatar z inicjałem + nazwa + tag (Administrator/Kibic) + e-mail. Konta demo: `klub.plomien@gmail.com` (Administrator), `kibic.dzik@gmail.com` (Kibic).
- Separator „LUB INNE KONTO GOOGLE" + input e-mail.
- Notka: „Uprawnienia administratora ma konto {adminEmail}. Pozostałe konta logują się jako kibic."
- Przyciski: **Anuluj** (outline) / **Zaloguj z Google** (białe tło, ciemny tekst, niebieskie kółko „G").

**B. Dodaj aktualność** (admin) — `max-width:560px`:
- Tytuł „DODAJ AKTUALNOŚĆ".
- Przycisk **Generuj post z ostatniego meczu** (żółty, pełnej szerokości) — wywołuje agenta AI, który wymyśla wynik/przeciwnika/strzelców i wypełnia tytuł + treść. Stan ładowania: etykieta „Generuję…", przycisk zablokowany. Błąd: „Nie udało się wygenerować posta…".
- Separator „LUB NAPISZ SAMODZIELNIE".
- Input **Tytuł** + `textarea` **Treść**. Przyciski: **Anuluj** / **Opublikuj** (zielony).

**C. Nowy temat** (zalogowany) — `max-width:560px`: tytuł „NOWY TEMAT", input Tytuł + textarea Treść, przyciski **Anuluj** / **Opublikuj** (zielony). Po dodaniu otwiera nowy wątek.

---

## Interactions & Behavior
- **Zakładki**: `setTab(key)` przełącza widok; przy zmianie zakładki forum wraca do listy (`forumView='list'`).
- **Logowanie**: wybór konta lub wpisany e-mail → walidacja formatu e-mail → rola `admin` gdy adres = `adminEmail` (case-insensitive), inaczej `kibic`. Nick = część przed `@`. Enter w polu e-mail = zaloguj.
- **Uprawnienia**: przycisk „+ Dodaj post" i możliwość generowania AI tylko dla roli `admin`. Zakładanie tematów/odpowiedzi — każdy zalogowany.
- **Dodawanie posta**: walidacja niepustego tytułu i treści; nowy wpis trafia na początek listy z datą `now` i autorem = nick.
- **AI generowanie**: pojedyncze wywołanie LLM z promptem redaktora klubowego; oczekiwany zwrot to JSON `{title, body}` (parser wyłuskuje `{…}` z odpowiedzi). W produkcji podłącz własny endpoint LLM.
- **Forum**: `openThread(id)` / `backToForum()`; `addThread()` (otwiera nowy wątek); `addReply()` dokłada odpowiedź do bieżącego wątku.
- **Hover**: przyciski i karty zmieniają tło/obramowanie (patrz tokeny). Modale: klik w tło zamyka.
- **Responsywność**: nagłówek i wiersze `flex-wrap`; zakładki przewijalne poziomo; logo hero i tabela chowają się/scrollują na wąskich ekranach; rozmiary `clamp()`.
- **Format daty**: polski, „D miesiąca RRRR" (np. „9 lipca 2026").
- **Odmiana**: „1 wpis / 2–4 wpisy / N wpisów"; „1 odpowiedź / N odpowiedzi".

## State Management
Stan komponentu (w prototypie; w produkcji rozbij na store + API):
- `session` `{name, email, role}` — utrwalane w `localStorage['plomien_session_v1']`.
- `news[]` `{id, title, body, author, date}` — `localStorage['plomien_news_v1']`, seed przy pierwszym wejściu.
- `forum[]` `{id, title, body, author, date, replies:[{id, author, body, date}]}` — `localStorage['plomien_forum_v1']`, seed.
- `activeTab` (`news|table|schedule|forum`), `forumView` (`'list'` | id wątku).
- Flagi modali: `showLogin`, `showAddPost`, `showNewThread`.
- Pola formularzy: `loginEmail`, `postTitle`/`postBody`, `threadTitle`/`threadBody`, `replyText` + odpowiadające `*Error`.
- `aiLoading`, `aiError`.
- **Do produkcji**: `localStorage` → realny backend (auth przez Google OAuth, CRUD aktualności z rolą admina, forum jako zasób z wątkami/odpowiedziami). Tabela i terminarz — z API ligi lub CMS.

## Konfiguracja (tweaki prototypu → props/config w produkcji)
- `showHeroLogo` (bool) — logo w hero.
- `highlightLatest` (bool) — wyróżnianie najnowszego wpisu.
- `motto` (text) — tekst w hero.
- `adminEmail` (text) — adres z uprawnieniami administratora (domyślnie `klub.plomien@gmail.com`).

## Assets
- `uploads/logo.jpg` (612×612, JPEG, białe tło) — herb klubu: czarny dzik na tle płomieni, obwódka żółto-zielono-czerwona. Używany w nagłówku (52px), hero (do 260px) i stopce (38px), zawsze przycięty do koła (`border-radius:50%`). Dołączony w tej paczce.
- Brak innych grafik/ikon — całość na typografii, kolorach i pasku tricolor. Awatary forum generowane (kolor z hasha nicku + inicjał).

## Screenshots
Poglądowe zrzuty aktualnego wyglądu w folderze `screenshots/`:
- `01-aktualnosci.png` — widok Aktualności (hero + wpisy)
- `02-tabela.png` — Tabela ligowa (Płomień podświetlony)
- `03-terminarz.png` — Terminarz (nadchodzące / rozegrane)
- `04-forum-lista.png` — Forum, lista tematów
- `05-forum-watek.png` — Forum, widok wątku z odpowiedziami
- `06-modal-nowy-temat.png` — modal „Nowy temat"
- `07-modal-dodaj-post.png` — modal „Dodaj aktualność" (z generowaniem AI)
- `08-modal-logowanie.png` — modal logowania Google

## Files
- `Płomień Kostrze.dc.html` — projekt do odtworzenia (zakładki: Aktualności / Tabela / Terminarz / Forum).
- `support.js` — lekki runtime prototypu (nie przenoś do produkcji; służy tylko do otwarcia pliku w przeglądarce).
- `uploads/logo.jpg` — herb klubu.

> Uwaga: pliki `.dc.html` używają składni szablonów (`{{ }}`, `<sc-if>`, `<sc-for>`) oraz klasy logiki. Traktuj je jako opis struktury + zachowania; przełóż na komponenty docelowego frameworka.
