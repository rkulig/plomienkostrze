-- Seed content (roadmap S-01): admin tooling arrives in S-02, so the first
-- posts ship as a versioned migration — identical on every environment.
-- Fixed published_at timestamps (not now()) keep list order stable and
-- repeatable; multi-paragraph bodies (blank-line separated) exercise the
-- paragraph rendering in the SPA. Placeholder-realistic club content — to be
-- replaced by real posts once admin tools land (S-02).
INSERT INTO news_posts (title, content, status, published_at) VALUES
(
    'Zwycięstwo na własnym boisku — Płomień pokonuje rywali 3:1',
    'W niedzielne popołudnie nasi zawodnicy rozegrali jedno z lepszych spotkań w tym sezonie. Po bramce straconej w pierwszym kwadransie drużyna wzięła się do pracy i jeszcze przed przerwą wyrównała po składnej akcji lewą stroną boiska.

Druga połowa należała już wyłącznie do nas. Dwie kolejne bramki padły po stałych fragmentach gry, nad którymi pracowaliśmy przez cały tydzień na treningach. Szczególne słowa uznania należą się najmłodszym zawodnikom w składzie, którzy nie wyglądali na stremowanych.

Dziękujemy kibicom za doping od pierwszej do ostatniej minuty. Kolejne spotkanie u siebie już za dwa tygodnie — szczegóły podamy w osobnym komunikacie.',
    'PUBLISHED',
    '2026-06-28 18:30:00+02'
),
(
    'Wznawiamy treningi grup młodzieżowych — zapisy otwarte',
    'Po krótkiej przerwie wracamy na boisko. Od najbliższego poniedziałku wznawiamy treningi wszystkich grup młodzieżowych. Zajęcia odbywają się jak dotychczas: młodsze roczniki w poniedziałki i środy o 17:00, starsze we wtorki i czwartki o 18:00.

Prowadzimy również zapisy nowych zawodniczek i zawodników. Zapraszamy dzieci i młodzież z Kostrza i okolic — pierwszy miesiąc treningów jest bezpłatny, wystarczy przyjść na zajęcia w stroju sportowym. Rodziców prosimy o zabranie ze sobą dokumentu w celu wypełnienia formularza zgłoszeniowego.

W razie pytań zapraszamy do kontaktu z trenerami przed zajęciami lub przez klubowe media społecznościowe.',
    'PUBLISHED',
    '2026-06-20 12:00:00+02'
),
(
    'Rodzinny piknik klubowy — zapraszamy w ostatnią sobotę czerwca',
    'Tradycyjnie na zakończenie sezonu zapraszamy wszystkich członków klubu, rodziców i sympatyków na rodzinny piknik na naszym obiekcie. Początek o godzinie 14:00, zakończenie planujemy około 19:00.

W programie mecz pokazowy rodzice kontra zawodnicy, konkursy sprawnościowe z nagrodami dla najmłodszych oraz wspólne grillowanie. Klub zapewnia kiełbaski i napoje, a każdy dodatkowy wypiek domowej roboty będzie mile widziany na wspólnym stole.

Wstęp wolny. W przypadku załamania pogody piknik przeniesiemy na pierwszą sobotę lipca — informacja pojawi się na stronie najpóźniej dzień wcześniej.',
    'PUBLISHED',
    '2026-06-12 09:00:00+02'
),
(
    'Komunikat: przerwa wakacyjna i godziny otwarcia obiektu',
    'Informujemy, że w okresie wakacyjnym boisko i zaplecze klubowe będą dostępne w zmienionych godzinach. Obiekt pozostaje otwarty od poniedziałku do piątku w godzinach 16:00–21:00 oraz w soboty od 10:00 do 18:00.

Treningi drużyny seniorów odbywają się bez zmian. Grupy młodzieżowe wracają do zajęć zgodnie z harmonogramem ogłoszonym w osobnym wpisie. Prosimy o śledzenie aktualności — wszelkie zmiany będziemy ogłaszać na bieżąco.',
    'PUBLISHED',
    '2026-06-05 10:15:00+02'
);
