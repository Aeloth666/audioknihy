# Přehrávač audioknih z Plexu – návrh

Datum: 2026-11-23
Stav: návrh ke schválení

## Záměr a proč

Uživatel poslouchá audioknihy ze svého Plex serveru přes Plexamp. Často střídá dvě i více knih najednou. Plexamp si u knihy, od které odejde, nepamatuje pozici, takže po návratu musí složitě dohledávat, kde přestal.

Chce vlastní Android aplikaci, která tohle řeší: **každá kniha si samostatně pamatuje, kde skončil**, a z jedné obrazovky se dá kdykoli pokračovat v kterékoli rozposlouchané knize. Nepotřebuje přitom všechny funkce Plexampu, jen dobrý přehrávač audioknih.

## Cíle

1. **Pozice u každé knihy zvlášť.** Přepnutí na jinou knihu nikdy nesmaže ani nepřepíše pozici té předchozí. Návrat ke knize pokračuje přesně tam, kde skončil (s krátkým převinutím zpět, viz níže).
2. **Obrazovka „Rozposlouchané“** jako výchozí. Ukazuje všechny načaté a nedoposlouchané knihy seřazené podle posledního poslechu. U každé je vidět obal, název, autor, aktuální kapitola/soubor, ukazatel průběhu a zbývající čas. Klepnutí rovnou spustí přehrávání od uložené pozice.
3. **Časovač vypnutí:** volby 15/30/45/60 min a „do konce kapitoly“. Posledních ~10 s hlasitost plynule ztlumí. Zatřesení telefonem během ztlumování nebo krátce po vypnutí prodlouží časovač o stejnou dobu. Po vypnutí časovačem se pozice uloží.
4. **Rychlost přehrávání** 0,5×–3× s krokem 0,1. Pamatuje se u každé knihy zvlášť.
5. **Přeskakování** zpět o 15 s a vpřed o 30 s. Délku jde změnit v nastavení. Dále přechod na předchozí/další kapitolu.
6. **Automatické převinutí zpět při pokračování:** po pauze delší než pár minut se přehrávání vrátí o několik sekund (výchozí 10 s). Hodnotu jde změnit v nastavení.
7. **Knihovna:** procházení všech knih z Plexu podle autora/názvu, s vyhledáváním. Z detailu knihy jde spustit přehrávání od uložené pozice, od začátku nebo od zvolené kapitoly.
8. **Ovládání odjinud:** ovládání na zamčené obrazovce a v notifikaci, tlačítka na sluchátkách a Bluetooth.
9. **Android Auto** už v první verzi: procházení „Rozposlouchané“ a knihovny, přehrávání, pauza, skok ±, kapitoly.
10. **Synchronizace pozice s Plexem**, aby se pozice neztratila a přenesla se mezi zařízeními.
11. **Označení knihy jako dokončené / nedokončené**, ručně i automaticky na konci posledního souboru. Dokončené knihy zmizí z „Rozposlouchané“.

## Necíle (v této verzi)

- **Stahování na offline poslech.** Uživatel poslouchá online. Doplní se později, pokud bude potřeba. Architektura s tím ale musí počítat (viz Omezení).
- Hudební funkce Plexampu: rádia, mixy, plynulé přechody, analýza zvuku, vizualizace, ekvalizér.
- Distribuce přes Google Play, verze pro iOS, tablet nebo TV rozhraní.
- Přepínání mezi uživateli Plex Home a víc serverů najednou. Jeden účet a jeden server (výběr serveru při prvním spuštění).
- Záložky a poznámky k místům v knize.

## Anti-cíle (co by bylo selhání, i kdyby to „fungovalo“)

- Pozice knihy se ztratí nebo přepíše jinou knihou (pád aplikace, zabití systémem, výpadek sítě, přepnutí knihy, časovač).
- Návrat ke knize skončí na začátku souboru nebo na začátku knihy místo uložené pozice.
- Synchronizace s Plexem přepíše novější místní pozici starší pozicí ze serveru.
- Aplikace se chová jako hudební přehrávač: náhodné přehrávání, automatické pokračování další knihou po dokončení.

## Omezení

- Android, Kotlin, Jetpack Compose, Media3 (ExoPlayer + `MediaLibraryService` kvůli Android Auto a ovládání z notifikace).
- Instalace přes APK (sideload). Bez Play Billing a bez reklam. Aplikace je jen pro uživatele.
- Rozhraní v češtině.
- Zdroj dat je výhradně Plex Media Server uživatele, přístupný doma i vzdáleně (Plex Remote Access / relay).
- **Pro budoucí offline režim:** přehrávání musí jít přes vrstvu, která pro danou stopu vrátí URI (dnes vždy streamovací URL z Plexu, později případně místní soubor). Místní databáze je zdrojem pravdy pro pozice, ne server.

## Přístupy (zvažované)

| | Přístup | Výhody | Nevýhody |
|---|---|---|---|
| A | Upravit open-source Chronicle | Většina funkcí už existuje (časovač, rychlost, stahování) | Starý kód (targetSdk 30, ExoPlayer 2, Dagger/kapt, Play Billing). Převod na nový Android je velká a riskantní práce. Otevřená chyba „nepamatuje si pozici“ (#112) zasahuje přesně do hlavní funkce. |
| **B** | **Nová štíhlá aplikace, Chronicle jako vzor** | **Malý, moderní kód pod kontrolou. Hlavní funkce (pozice u každé knihy) navržená od začátku. Snadné rozšíření o offline.** | Víc práce na začátku |
| C | Jen nainstalovat Chronicle z GitHubu | Hotové za pár minut | Na novém Androidu nejspíš narazí, chyba s pozicí zůstává |

**Vybráno: B** (rozhodl uživatel). Chronicle (github.com/mattttvaughn/chronicle) slouží jako reference pro práci s Plex API a pro chování přehrávače audioknih.

## Rozhodnutí o tom, jak to postavit

Rozhodl uživatel:
- Nová aplikace (B), napřed online verze, offline později, Android Auto v první verzi, časovač vypnutí povinně.
- Pokud aplikace bude fungovat dobře, uživatel na ni úplně přejde z Plexampu. Souběžné používání Plexampu tedy není cíl. **Místní databáze je zdroj pravdy.** Pozice ze serveru se převezme jen tehdy, když je novější než místní. Slouží hlavně pro první spuštění (navázání na Plexamp) a pro druhé zařízení.
- Uživatel souhlasí, že v Android Auto zapne vývojářský režim a „Neznámé zdroje“. Součástí dodávky je krátký český návod, jak na to.

Moje rozhodnutí, která uživatel slyšel a nenamítal:
- **Kniha = složka na disku, ne album v Plexu.** Průzkum knihovny (domácí server s Plexem, 2026-11-23) ukázal:
  - ~300 složek s audiem, 4 464 MP3 a 1 698 M4A, mediánem 12 souborů na knihu (max. 121). Kořen je složka autora, kniha je obvykle o 1–2 úrovně níž (třeba `Autor/Série/Kniha`).
  - **10 knih je rozdělených do podsložek `CD 1`, `CD2`… a každé CD má v tagu jiné album** (např. „7 - 1 - Harry Potter a Relikvie smrti CD1“). Plex je proto ukáže jako samostatná alba. Seskupení podle alba by takovou knihu rozbilo.
  - Některé knihy jsou jeden M4A soubor s vloženými kapitolami (např. *Sitarane*, 2 h, kapitoly „Chapter 001“…). Jiné jsou víc dlouhých M4A dílů bez kapitol (*Duna*: 13 souborů po ~1 h 45 min).
  
  Pravidlo: kniha = nejnižší složka obsahující audio. Pokud se taková složka jmenuje `CD`/`Disk`/`Disc` + číslo, kniha = její rodičovská složka a CD se spojí za sebou podle čísla. Soubory se řadí podle CD, pak podle čísla stopy, pak podle názvu souboru. Cesty k souborům vrací Plex API (`Media/Part@file`), takže stačí stopy z Plexu seskupit v aplikaci. Identita knihy = cesta ke složce. Název a obal se berou z alba první stopy, název knihy se případně odvodí ze jména složky.
- Kapitoly: jeden soubor bez vložených kapitol = jedna kapitola. Soubor s vloženými kapitolami (M4A/M4B) = jeho kapitoly. Takže dvouúrovňový model: kniha → soubory → (volitelně) kapitoly v souboru.
- **Pozice = (stopa, čas ve stopě)**, ukládá se do místní databáze a zároveň do Plexu.
- Přihlášení účtem Plex stejně jako Plexamp (PIN/OAuth přes plex.tv), výběr serveru a knihovny při prvním spuštění.
- Instalace APK mimo Google Play.

### Model pozice (jádro aplikace)

Pro každou knihu (identita = cesta ke složce knihy) se lokálně ukládá:
- `trackRatingKey`, `positionMs` ve stopě, `updatedAt` (čas zařízení, UTC), `playbackSpeed`, `finished` (bool), `lastPlayedAt`.

Ukládání pozice:
- Průběžně během přehrávání každých ~5 s.
- Okamžitě při pauze, přepnutí knihy, přechodu na jinou stopu, vypnutí časovačem, odpojení sluchátek, ukončení služby a `onTaskRemoved`.

Přepnutí knihy: nejdřív se zapíše pozice aktuální knihy, teprve pak se načte nová.

Synchronizace s Plexem:
- Při přehrávání se posílá `/:/timeline` (stav playing/paused/stopped, čas, délka) pro aktuální stopu. Plex si tak ukládá `viewOffset` stopy.
- Při startu aplikace a při otevření knihy se pozice ze serveru porovná s místní a vyhrává novější podle času. Pozici knihy na serveru odvodíme ze stop knihy: stopa s nejnovějším `lastViewedAt` a jejím `viewOffset`. Při nejasnosti vyhrává místní pozice.
- Zápisy na server, které selžou kvůli síti, se zařadí do fronty a pošlou se později.
- *(Moje rozhodnutí)* Stav „dokončeno“ se synchronizuje do Plexu: dokončení = označit stopy alba jako přehrané (scrobble), návrat = unscrobble. Rychlost přehrávání zůstává jen v zařízení.
- *(Moje rozhodnutí)* Při prvním spuštění aplikace projde knihovnu a do „Rozposlouchané“ zařadí knihy, které už mají na serveru rozposlouchanou stopu (viewOffset > 0 nebo část stop přehraná, ale ne všechny). Navážete tak na knihy rozposlouchané v Plexampu.
- **Ověřit hned na začátku stavby:** jestli uživatelův server u hudebních stop ukládá `viewOffset` z `/:/timeline`. Pokud ne, synchronizace s Plexem se omezí na stav dokončeno a místní databáze zůstane jediným zdrojem pozice.

## Co zbývá na stavitele

- Struktura projektu, DI (Hilt nebo ruční), Room vs. DataStore pro jednotlivá data, minSdk (doporučeno 26+).
- Přesné volání Plex API: přihlášení přes PIN, výpis serverů a volba nejlepšího spojení (lokální vs. vzdálené), výpis knihoven typu hudba, alb a stop, streamovací URL s tokenem. Při nejasnostech podle Chronicle.
- Jak získat vložené kapitoly z M4A/M4B: z Plexu, pokud je vrací (`includeChapters=1`), jinak vlastním čtením atomu `chpl` / kapitolové stopy přes HTTP range request. Když kapitoly nejsou k dispozici, soubor = jedna kapitola.
- Vzhled obrazovek nad rámec popsaného (Material 3, tmavý motiv podle systému).
- Chování při nedostupném serveru: srozumitelná hláška, žádná ztráta pozice.
- Testy: model pozice a synchronizace musí mít automatické testy (je to hlavní funkce). UI stačí ověřit ručně.
