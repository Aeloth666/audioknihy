---
version: 1
slug: "app-src-main-java-cz-audioknihy-ui"
primary_target: "app/src/main/java/cz/audioknihy/ui"
related_targets: []
---

# Surface brief: Audioknihy app (all screens)

Scope: Android app UI — Rozposlouchané, Knihovna, Detail knihy, Přehrávač, Nastavení, mini-přehrávač. Mode: Operate.
Audience/job: rodina poslouchající české audioknihy z Plexu, hlavně potmě v posteli, v autě a doma při práci; najít, kde skončili, a pokračovat. Constraints: tmavé (žádné oslnění), velká tlačítka (≥56 dp hlavní, 48 dp minimum), Material 3 komponenty, česky.

## Direction contract

THESIS: Pozice v knize je záložka-stužka. Každá kniha nese hedvábnou stužku ve vlastní barvě; její délka je to, co máte za sebou. Odmítá šedý seznam s tenkým progress barem.

OWN-WORLD: Tmavé lahvově zelené knihařské plátno (#0F1714 → #213029) jako podklad, teplý papírový text (#ECE6D8), zlatá nit (#D9B45A) pro hlavní akce, stužky v hedvábných barvách (karmín, zlato, tyrkys, švestka, kobalt, rez) přidělené knize. Stužka končí vlaštovčím ocasem. Žádné textury, žádný krém.

STORY: Otevřu aplikaci potmě, vidím obaly s visícími stužkami, poznám knihu i kolik mám za sebou, jedním velkým ▶ pokračuji; klepnutím na knihu jen otevřu detail.

FIRST VIEWPORT: Horní lišta „Audioknihy“ + obnovit/nastavení; záložky Rozposlouchané/Knihovna. Řádky: obal 3:4 (72×96 dp), vedle svislá stužka (10 dp, délka = podíl poslechnutého, vlaštovčí konec), název (titleMedium), autor, „část X/Y · zbývá“, vpravo tónové tlačítko ▶ 56 dp ve zlaté. Dole mini-přehrávač s obalem, stužkou a ▶.

FORM: Stužka (záložka) — position 1 on own list (Impeccable's pick); seed:09887ab540b638b1

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
