<img src="art/icon-512.png" width="112" align="right" alt="">

# Audioknihy – audiobook player for Plex

🇨🇿 [Česká verze](README.cs.md)

An Android audiobook player for your own Plex Media Server. **Every book remembers exactly where you stopped**, so you can switch between several books and always continue in the right place.

Built for listening in bed, in the car and at home: dark by default, big controls, a sleep timer, Android Auto.

## Features

- **In progress** – all started books with how much you have left. Tap a book to see its chapters, tap ▶ to continue. Sort by recently played, title, author, least remaining or most progress.
- **Per-book position** – saved every 5 seconds and on every pause, book switch, sleep-timer stop, unplugged headphones and app close. After a pause longer than 3 minutes playback rewinds a few seconds. Speed is remembered per book.
- **Bookmark ribbon** – each book carries a silk ribbon in its own colour hanging from the top of its cover; its length is how far you are.
- **Library** – authors collapse and expand, search ignores diacritics. Books split into `CD 1`, `CD 2`… folders are joined into one book.
- **Chapters** – embedded chapters of M4A/M4B files (Nero `chpl`), otherwise one file = one chapter.
- **Player** – skip back/forward (configurable), previous/next chapter, speed 0.5×–3.0×.
- **Sleep timer** – 15/30/45/60 min or end of chapter, fades out at the end, shake the phone to extend.
- **Visualizers** – Waves, Stereoscope (a phase-space figure for mono audiobooks), Spectrum, Aura (spectrum ring around the cover) and Silk. Audio is tapped straight from the player – no microphone permission.
- **Covers** – Plex cover; if missing, the app looks it up on Apple Books (CZ store), Google Books and Open Library, then falls back to the author's picture. You can also search for a cover manually (⋮ → Find cover).
- **Mark as finished / unplayed** – synced to Plex as played / unplayed.
- **Sync with Plex** – positions are also reported to Plex (when the network is down they are sent later). Books you started in Plexamp appear in *In progress* after the first sign-in.
- **Everywhere** – notification, lock screen, headphone buttons, Android Auto (In progress / Library, chapters on next/previous).
- **Automatic updates** – the app checks GitHub Releases for a new version, downloads it and opens the installer (you confirm the install). Can be turned off in Settings.
- **Language** – Czech or English, switchable in Settings (or follow the phone).
- **Appearance** – three layouts (Ribbon, Shelf, Compact), each with five colour themes (Cloth, Midnight, Oxblood, Night blue, Day). English and Czech.

## Requirements

- Android 8.0 or newer
- A Plex Media Server with your audiobooks in a **music** library (book = folder, files = parts/chapters)

## Install

1. Build the APK (below) or download it from Releases.
2. Open the APK on your phone and allow installing from this source.
3. Start **Audioknihy**, tap **Sign in with Plex**, sign in in the browser and come back.
4. Choose your server and the library with audiobooks.

### Android Auto

Android Auto hides apps that are not installed from Google Play. Enable them once:

1. Phone **Settings → Android Auto**.
2. Scroll down and tap **Version** 10× until developer settings are offered, confirm.
3. **⋮ → Developer settings** → enable **Unknown sources**.
4. Reconnect the phone to the car.

## Build

Requirements: JDK 17+ and the Android SDK (API 35).

```bash
./gradlew testDebugUnitTest   # unit tests
./gradlew assembleDebug       # debug APK
```

Release signing is optional. Create `keystore.properties` in the project root (it is git-ignored, never commit it):

```properties
storeFile=release.keystore
storePassword=...
keyAlias=audioknihy
keyPassword=...
```

Put the keystore in `app/` and run `./gradlew assembleRelease`.

> The build directory is redirected to `%LOCALAPPDATA%/AudioknihyBuild` (the original project lived in a synced OneDrive folder). Change it in `build.gradle.kts` if you like.

## Tech

Kotlin, Jetpack Compose (Material 3), Media3 (ExoPlayer + MediaLibraryService), Room, Hilt, OkHttp + Moshi, WorkManager, DataStore, Coil.

Core logic (book grouping, position rules, chapters, sleep timer, playback session, report queue) is covered by unit tests: `./gradlew testDebugUnitTest`.

Design notes: [`docs/superpowers/specs`](docs/superpowers/specs), implementation plan: [`docs/superpowers/plans`](docs/superpowers/plans) (Czech).

## Privacy

The app talks only to your Plex server and plex.tv (sign-in). For books without a cover it sends the book title and author to Apple (iTunes Search), Google Books and Open Library. No analytics, no ads.

## Credits

Inspired by [Chronicle](https://github.com/mattttvaughn/chronicle) (no code reused). Not affiliated with Plex Inc.
