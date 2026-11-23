# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

Majitel domácího Plex serveru a jeho rodina. Poslouchají české audioknihy (beletrie, fantasy, detektivky, pohádky) a často střídají dvě i více knih najednou. Poslouchají hlavně v posteli před spaním (tma, telefon v ruce, časovač vypnutí), v autě (Android Auto nebo telefon v držáku) a doma při práci (telefon leží opodál).

## Product Purpose

Přehrávač audioknih z vlastního Plex serveru, který si u každé knihy samostatně pamatuje, kde posluchač skončil. Úspěch = posluchač kdykoli přepne knihu a po návratu pokračuje přesně tam, kde přestal, bez dohledávání.

## Positioning

Na rozdíl od Plexampu (hudební přehrávač) je postavený jen na audioknihy: seznam „Rozposlouchané“ s pozicí u každé knihy, knihy složené ze složek (včetně podsložek CD), kapitoly, časovač vypnutí se ztlumením.

## Operating Context

- Knihovna ~300 knih na Unraid serveru, ve složkách Autor/(Série)/Kniha, MP3 i M4A, některé knihy rozdělené do CD.
- Obaly knih pocházejí z Plexu (u některých chybí).
- Poslech často ve tmě před spaním; v autě přes Android Auto.
- Instalace jako APK mimo Google Play.

## Capabilities and Constraints

- Kotlin, Jetpack Compose, Material 3, Media3. Česky.
- Online poslech; stahování na offline poslech je možné přidat později.
- Obrazovky: Rozposlouchané, Knihovna (autoři rozklikávací), Detail knihy s kapitolami, Přehrávač, Nastavení, mini-přehrávač.

## Product Principles

1. Pozice knihy je posvátná: nic ji nesmí ztratit ani přepsat.
2. Pokračování na jedno klepnutí z první obrazovky.
3. Ovladatelné potmě a jednou rukou, bez rušivého jasu.
4. Česky a srozumitelně pro celou rodinu.

## Accessibility & Inclusion

Velké ovládací prvky použitelné v posteli i v autě; čitelnost potmě bez oslnění.
