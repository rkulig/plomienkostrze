---
project: "Płomień Kostrze"
version: 1
status: draft
created: 2026-06-29
context_type: greenfield
product_type: web-app
target_scale:
  users: medium
  qps: low
  data_volume: small
timeline_budget:
  mvp_weeks: 3
  hard_deadline: null
  after_hours_only: true
---

# Płomień Kostrze — PRD

## Vision & Problem Statement

Kibice drużyny piłkarskiej Płomień Kostrze nie mają jednego, klubowego miejsca, w
którym mogliby śledzić aktualności (wyniki, wydarzenia meczowe, życie klubu) i
rozmawiać między sobą. Dziś te informacje krążą po rozproszonych mediach
społecznościowych, gdzie giną w strumieniu treści, nie tworzą trwałego archiwum, a
dyskusja kibiców jest rozproszona. Klub nie ma własnego kanału, nad którym miałby
kontrolę.

Wartością nie jest sam strumień newsów, lecz skupienie aktualności i dyskusji
kibiców w jednym, klubowym miejscu — zamiast rozpraszania ich po mediach
społecznościowych. Dodatkowym wyróżnikiem jest automatyczne generowanie wpisów
aktualności z surowych danych meczowych, publikowanych dopiero po akceptacji
administratora, co odciąża osobę prowadzącą komunikację klubu.

## User & Persona

Persona główna — **Kibic Płomienia Kostrze**: lokalny sympatyk klubu, który chce
być na bieżąco z wynikami i życiem drużyny oraz wymieniać się komentarzami z innymi
kibicami. Sięga po produkt po meczu i przy okazji wydarzeń klubowych; dziś szuka
tych treści w rozproszonych mediach społecznościowych.

### Secondary persona

**Administrator klubu** — osoba prowadząca komunikację: tworzy i publikuje wpisy
aktualności (ręcznie lub akceptując wersję wygenerowaną z danych meczowych) oraz
dba o porządek na forum. Ceni szybkość tworzenia wpisów.

## Success Criteria

MVP = pełny pipeline aktualności z automatycznym generowaniem (forum jest
fast-followem, poza MVP). Przepływ główny: administrator loguje się → wprowadza
dane meczowe → system generuje propozycję wpisu → administrator przegląda /
poprawia / akceptuje → wpis publikowany w publicznych aktualnościach → kibic/gość
go czyta.

### Primary
- Administrator publikuje wpis aktualności wygenerowany z surowych danych meczowych
  przez ścieżkę „dane → wygenerowana propozycja → akceptacja → publikacja", a
  kibic/gość czyta go w publicznej zakładce. Pipeline działa end-to-end.
- Co najmniej 75% wygenerowanych propozycji wpisów jest akceptowanych przez
  administratora (z co najwyżej drobną edycją, bez przepisywania od zera).

### Secondary
- Administrator tworzy wpisy aktualności także ręcznie (bez generowania).
- (Fast-follow) Kibice zakładają tematy na forum i zaczynają ze sobą rozmawiać.

### Guardrails
- Żaden wygenerowany wpis nie trafia do publicznych aktualności bez wyraźnej
  akceptacji administratora — system nigdy nie publikuje samodzielnie.
- Kibic loguje się przy użyciu istniejącego konta zewnętrznego dostawcy tożsamości;
  aplikacja nie przechowuje haseł.

## User Stories

### US-01: Administrator publikuje wpis aktualności z danych meczowych

- **Given** zalogowany administrator po rozegranym meczu
- **When** wprowadza surowe dane meczowe i prosi o wygenerowanie wpisu
- **Then** otrzymuje propozycję wpisu, którą może poprawić, a po akceptacji zostaje
  ona opublikowana w publicznej zakładce aktualności

#### Acceptance Criteria
- Żadna propozycja nie jest publikowana automatycznie — wymagana jest jawna akceptacja administratora
- Administrator może edytować treść propozycji przed publikacją
- Administrator może odrzucić propozycję bez publikowania
- Opublikowany wpis jest natychmiast widoczny dla gościa bez logowania

### US-02: Kibic czyta aktualności klubu

- **Given** dowolna osoba (zalogowana lub nie) wchodzi na stronę
- **When** otwiera zakładkę aktualności
- **Then** widzi listę opublikowanych wpisów i może otworzyć dowolny do przeczytania

#### Acceptance Criteria
- Czytanie aktualności nie wymaga logowania
- Lista pokazuje tylko opublikowane wpisy (nie propozycje/szkice)

## Functional Requirements

### Konta i dostęp
- FR-001: Administrator może zalogować się, by uzyskać dostęp do narzędzi publikacji. Priority: must-have
  > Socrates: Brak kontrargumentu — narzędzia publikacji muszą być chronione. Zostaje.
- FR-002: Kibic może zalogować się przy użyciu istniejącego konta zewnętrznego dostawcy tożsamości. Priority: nice-to-have
  > Socrates: Potrzebne tylko do interakcji (reakcje/komentarze) i forum — wszystkie nice-to-have. Świadomie poza MVP.

### Aktualności — generowanie
- FR-003: Administrator może wygenerować propozycję wpisu aktualności na podstawie wprowadzonych danych meczowych lub luźnego tekstu. Priority: must-have
  > Socrates: Rozważono „szablon zamiast generowania". Rozstrzygnięcie: automatyczne generowanie to rdzeń wartości — administrator może wskazać ton i styl wpisu, czego szablon nie osiągnie. Zostaje.
- FR-004: Administrator może przejrzeć i edytować wygenerowaną propozycję przed publikacją. Priority: must-have
  > Socrates: Brak kontrargumentu — możliwość poprawy propozycji jest warunkiem sensownej akceptacji. Zostaje.
- FR-005: Administrator może zaakceptować i opublikować propozycję albo ją odrzucić. Priority: must-have
  > Socrates: Rozważono auto-publikację (skoro 75% i tak akceptowane). Rozstrzygnięcie: akceptacja obowiązkowa — błędny/ośmieszający wpis na publicznej stronie klubu to realny koszt wizerunkowy. Człowiek w pętli to świadomy guardrail.

### Aktualności — zarządzanie
- FR-006: Administrator może utworzyć wpis aktualności ręcznie, bez generowania. Priority: must-have
  > Socrates: Rozważono usunięcie z MVP (skoro pipeline generowania działa). Rozstrzygnięcie: zostaje — komunikaty nie-meczowe (treningi, wydarzenia, ogłoszenia) nie mają danych meczowych i wymagają ścieżki ręcznej.
- FR-007: Administrator może edytować opublikowany wpis aktualności. Priority: must-have
  > Socrates: Brak kontrargumentu — poprawa literówek/błędów po publikacji jest oczywista. Zostaje.
- FR-008: Administrator może usunąć wpis aktualności. Priority: must-have
  > Socrates: Brak kontrargumentu — wycofanie błędnego wpisu jest konieczne. Zostaje.
- FR-009: Gość/kibic może przeglądać i czytać opublikowane aktualności. Priority: must-have
  > Socrates: Brak kontrargumentu — to cel istnienia produktu po stronie odbiorcy. Zostaje.

### Interakcje kibiców (nice-to-have, poza MVP)
- FR-011: Kibic (zalogowany) może zareagować na wpis aktualności „łapką w górę" lub „łapką w dół". Priority: nice-to-have
  > Socrates: Wciąga autoryzację kibiców do zakresu. Rozstrzygnięcie: zdecydowanie nice-to-have, poza MVP.
- FR-012: Kibic (zalogowany) może dodać komentarz pod wpisem aktualności. Priority: nice-to-have
  > Socrates: Publiczne komentarze rodzą potrzebę moderacji. Rozstrzygnięcie: zdecydowanie nice-to-have, poza MVP.
- FR-013: Administrator może moderować komentarze pod aktualnościami (usuwać). Priority: nice-to-have
  > Socrates: Zależne od FR-012. Rozstrzygnięcie: nice-to-have, dochodzi razem z komentarzami.

### Forum (fast-follow, poza MVP)
- FR-014: Kibic może założyć temat na forum. Priority: nice-to-have
  > Socrates: Forum świadomie odłożone jako fast-follow. Poza MVP.
- FR-015: Kibic może odpowiadać w temacie na forum. Priority: nice-to-have
  > Socrates: Jw. — część fast-followu forum. Poza MVP.
- FR-016: Administrator może moderować forum (usuwać/edytować tematy i wpisy). Priority: nice-to-have
  > Socrates: Jw. — część fast-followu forum. Poza MVP.

## Non-Functional Requirements

- Podczas generowania propozycji administrator otrzymuje potwierdzenie rozpoczęcia
  bez zauważalnej zwłoki oraz ciągłą widoczną informację o postępie przy każdej
  operacji trwającej dłużej niż ~2 s; propozycja jest dostarczana bez nieokreślonego
  oczekiwania.
- Gość/kibic widzi listę i treść opublikowanych aktualności szybko — pierwsza treść
  wpisu jest widoczna w czasie rzędu < 2 s przy typowym łączu.
- Logowanie odbywa się wyłącznie przy użyciu istniejącego konta zewnętrznego
  dostawcy tożsamości; aplikacja nie przechowuje haseł i ogranicza dane o kibicu do
  minimum niezbędnego do jego identyfikacji.

## Business Logic

Aplikacja przekształca surowe dane meczowe i wskazany przez administratora ton/styl
w gotową propozycję wpisu aktualności w naturalnym języku — która trafia do
publikacji dopiero po akceptacji człowieka.

Wejścia (jako dane podawane przez użytkownika): luźny tekstowy opis meczu — kto
grał, strzelcy, wynik, przebieg zdarzeń — oraz wskazanie pożądanego tonu i stylu
wpisu (np. żartobliwie po wygranej, rzeczowo po porażce).

Wyjście: propozycja wpisu aktualności w naturalnym języku, utrzymana w zadanym
tonie, gotowa do przejrzenia.

Jak użytkownik ją spotyka: administrator przegląda propozycję, w razie potrzeby ją
edytuje, a następnie akceptuje lub odrzuca. Dopiero zaakceptowany wpis pojawia się
w publicznej zakładce aktualności, widocznej dla każdego bez logowania. Reguła ta
dotyczy wpisów generowanych; wpisy tworzone ręcznie pomijają etap generowania, ale
podlegają tej samej publikacji.

## Access Control

Model wielodostępowy z logowaniem delegowanym do zewnętrznego dostawcy tożsamości
— kibic loguje się istniejącym kontem zewnętrznym, a aplikacja nie zarządza
hasłami.

Role i uprawnienia:
- **Gość (niezalogowany)** — może czytać aktualności. Nie ma dostępu do forum (ani
  czytania, ani pisania).
- **Kibic (zalogowany)** — wszystko, co gość, plus pełny dostęp do forum: czyta
  wątki, zakłada tematy, odpowiada.
- **Administrator** — wszystko, co kibic, plus zarządzanie aktualnościami
  (tworzenie ręczne, akceptacja/edycja wpisów wygenerowanych z danych meczowych,
  edycja, usuwanie) oraz moderacja forum.

Zasada granicy: aktualności są publiczne (czyta każdy bez konta); całe forum jest
za logowaniem. Niezalogowany użytkownik próbujący wejść na forum jest kierowany do
logowania.

## Non-Goals

- **Import plików (PDF/DOCX/itp.) przy tworzeniu wpisów** — dane meczowe wprowadzane
  luźnym tekstem; parsowanie formatów plików jest poza zakresem (i poza forum).
- **Natywna aplikacja mobilna** — na początek wyłącznie aplikacja webowa; brak
  aplikacji iOS/Android.
- **Forum i interakcje kibiców w MVP** — logowanie kibiców, reakcje („łapki"),
  komentarze pod aktualnościami oraz forum są poza pierwszą wersją (fast-follow /
  nice-to-have). MVP obsługuje wyłącznie publikację i czytanie aktualności.
- **Automatyczne pobieranie danych meczowych** — brak integracji z zewnętrznymi
  źródłami wyników; administrator wprowadza dane meczowe ręcznie.

## Open Questions

Brak otwartych kwestii blokujących. Wszystkie gray-areas zostały rozstrzygnięte
podczas shapingu (zob. `gray_areas_resolved` w `shape-notes.md` oraz blockquote'y
`> Socrates:` przy FR-ach); kontrola jakości shapingu: `accepted`. Wybór konkretnego
dostawcy tożsamości i pozostałe decyzje stackowe są świadomie odłożone do kroku
selekcji technologii.
