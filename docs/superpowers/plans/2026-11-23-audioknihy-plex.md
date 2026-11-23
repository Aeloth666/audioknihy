# Audioknihy z Plexu – plán stavby

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Android aplikace „Audioknihy“, která přehrává audioknihy z uživatelova Plex serveru. Každá kniha si samostatně pamatuje pozici. Součástí je časovač vypnutí a Android Auto.

**Architecture:** Jeden modul `app`, kód rozdělený po funkcích (`plex`, `library`, `chapters`, `progress`, `playback`, `sleep`, `auto`, `ui`, `settings`). Logika, na které závisí hlavní funkce (seskupení do knih, kapitoly, rozhodování o pozici, časovač), jsou čisté Kotlin třídy s unit testy. Přehrávač je Media3 `MediaLibraryService` s ExoPlayerem, ovládaný přes úzké rozhraní `PlayerFacade`, aby šla logika ukládání pozic testovat bez zařízení. Místní Room databáze je zdroj pravdy pro pozice. Plex dostává hlášení přes frontu, která přežije výpadek sítě.

**Tech Stack:** Kotlin 2.1, AGP 8.7, Gradle 8.11 (wrapper), JDK 21 (je na stroji), compileSdk/targetSdk 35, minSdk 26, Jetpack Compose (BOM 2024.12.01) + Material 3, Media3 1.5.1 (exoplayer, session), Room 2.6.1 (KSP), Hilt 2.53.1, Retrofit 2.11 + Moshi 1.15 + OkHttp 4.12, WorkManager 2.10, DataStore 1.1, Coil 2.7, Navigation Compose 2.8. Testy: JUnit4, kotlinx-coroutines-test, MockWebServer, Robolectric 4.14 (Room). Pokud se některá verze nepodaří stáhnout, použij nejbližší novější stabilní verzi a zapiš ji do `libs.versions.toml`.

**Spec:** `docs/superpowers/specs/2026-11-23-audioknihy-plex-design.md`

## Global Constraints

- Balíček `cz.audioknihy`, název aplikace „Audioknihy“. Všechny texty v rozhraní česky (`res/values/strings.xml`), žádné natvrdo zapsané řetězce v Compose.
- Distribuce jen jako APK (sideload). Žádné Play Billing, reklamy ani analytika.
- Zdroj dat je výhradně Plex Media Server uživatele: jeden účet, jeden server, jedna knihovna typu hudba.
- Kniha = nejnižší složka obsahující audio. Pokud se jmenuje `CD`/`Disk`/`Disc` + číslo (bez ohledu na velikost písmen, s mezerou/`_`/`-` nebo bez), kniha = rodičovská složka. Identita knihy = cesta k té složce, tak jak ji vrací Plex v `Media/Part@file`.
- Místní databáze je zdroj pravdy. Pozice ze serveru se převezme jen tehdy, když je novější než místní o víc než `SERVER_MARGIN_MS = 60_000`.
- Ukládání pozice: každých 5 s při přehrávání a okamžitě při pauze, přepnutí knihy, přechodu na jinou stopu, vypnutí časovačem, odpojení sluchátek (`ACTION_AUDIO_BECOMING_NOISY` → pauza), `onTaskRemoved` a `onDestroy` služby. Při přepnutí knihy se **nejdřív** zapíše pozice staré knihy.
- Výchozí nastavení: skok zpět 15 s, vpřed 30 s, převinutí při pokračování 10 s po pauze delší než 3 min, rychlost 0,5×–3,0× po 0,1. Rychlost se ukládá u každé knihy zvlášť.
- Časovač vypnutí: 15/30/45/60 min a „do konce kapitoly“. Ztlumení posledních 10 s. Zatřesení během ztlumení nebo do 60 s po vypnutí prodlouží časovač o původní délku a přehrávání pokračuje.
- Kapitoly: soubor s atomem `chpl` = jeho kapitoly, jinak soubor = jedna kapitola. „Předchozí kapitola“ víc než 3 s od začátku kapitoly skočí na její začátek, jinak na předchozí kapitolu.
- Dokončení knihy (konec posledního souboru nebo ruční označení) = `finished = true` + scrobble všech stop v Plexu. Zrušení dokončení = unscrobble. Po dokončení se nepřehrává žádná další kniha.
- Přehrávání bere URI stopy přes `TrackUriResolver` (dnes Plex stream URL), aby šlo později přidat offline soubory.

## Review Focus

1. **Systém zabije aplikaci během přehrávání.** Po znovuotevření musí být pozice nejvýš ~5 s stará, ne na začátku. → test `BookSessionTest.periodic_save_every_5s` (Task 8) + ruční kontrola `adb shell am kill` (Task 12).
2. **Vlastní hlášení na server se vrátí jako „novější“ pozice** (`lastViewedAt` serveru je o pár sekund novější než místní `updatedAt`, nebo se liší hodiny). Nesmí to posunout pozici. → test `ProgressResolverTest.server_within_margin_keeps_local` (Task 3).
3. **Kniha se v knihovně změní** (soubor přejmenován, přidán nebo smazán), takže uložené `trackRatingKey` už neexistuje. Aplikace nesmí spadnout. Použije `fileIndex` (oříznutý na rozsah), a pokud neexistuje ani ten, začátek knihy. → test `BookSessionTest.missing_track_falls_back_to_file_index` (Task 8).
4. **Skok zpět na začátku souboru** (5 s do souboru 3, skok −15 s) má skončit 10 s před koncem souboru 2, ne na 0. → test `BookSessionTest.skip_back_crosses_file_boundary` (Task 8).
5. **Přepnutí knihy během ztlumování časovačem.** Nová kniha musí hrát s hlasitostí 1,0 a časovač se zruší. → test `BookSessionTest.open_resets_volume_and_cancels_timer` (Task 8).

---

## Struktura souborů

```
settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml, gradlew(.bat), gradle/wrapper/*
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/res/values/strings.xml, res/xml/automotive_app_desc.xml
app/src/main/java/cz/audioknihy/
  AudioknihyApp.kt                      Hilt aplikace
  di/AppModule.kt                       Retrofit, Room, DataStore, hodiny
  plex/PlexModels.kt                    DTO + PlexTrack
  plex/PlexTvApi.kt, plex/PlexServerApi.kt   Retrofit rozhraní
  plex/PlexHeaders.kt                   X-Plex-* hlavičky, clientId
  plex/PlexAuth.kt                      PIN přihlášení
  plex/ServerConnector.kt               výběr serveru a nejlepšího spojení
  plex/PlexClient.kt                    sekce, stopy, timeline, scrobble
  library/Book.kt, library/BookGrouper.kt
  library/LibraryRepository.kt
  chapters/Chapter.kt, chapters/ChapterIndex.kt, chapters/Mp4ChapterReader.kt, chapters/ChapterRepository.kt
  progress/BookProgress.kt, progress/ProgressResolver.kt, progress/ResumePosition.kt
  progress/AppDatabase.kt, progress/ProgressDao.kt, progress/CachedLibraryDao.kt
  progress/PendingReport.kt, progress/ReportQueue.kt, progress/ReportWorker.kt
  playback/PlayerFacade.kt, playback/ExoPlayerFacade.kt
  playback/BookSession.kt               otevření knihy, ukládání, skoky, kapitoly
  playback/TrackUriResolver.kt
  playback/PlaybackService.kt           MediaLibraryService + notifikace + Auto callback
  sleep/SleepTimer.kt, sleep/ShakeDetector.kt
  auto/AutoTree.kt
  settings/Settings.kt
  ui/MainActivity.kt, ui/Nav.kt, ui/theme/*
  ui/login/LoginScreen.kt(+VM), ui/setup/ServerPickerScreen.kt(+VM)
  ui/home/InProgressScreen.kt(+VM), ui/library/LibraryScreen.kt(+VM)
  ui/book/BookDetailScreen.kt(+VM), ui/player/PlayerScreen.kt(+VM), ui/settings/SettingsScreen.kt
app/src/test/java/cz/audioknihy/...     unit testy (zrcadlí balíčky)
README.md                               český návod: instalace APK, Android Auto
```

---

### Task 1: Kostra projektu

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `AudioknihyApp.kt`, `ui/MainActivity.kt`, `res/values/strings.xml`, `.gitignore`, `local.properties` (není v gitu)
- Test: `app/src/test/java/cz/audioknihy/SmokeTest.kt`

**Interfaces:**
- Produces: buildovatelný projekt s Hilt, Compose, Room/KSP, Media3 a testovacími závislostmi. Příkazy `.\gradlew.bat testDebugUnitTest` a `.\gradlew.bat assembleDebug`.

- [ ] **Step 1:** `git init` v kořeni projektu, `.gitignore` pro Android (`.gradle/`, `build/`, `local.properties`, `*.iml`, `.idea/`). Zapsat `local.properties` s cestou k Android SDK (`sdk.dir=...`).
- [ ] **Step 2:** Vygenerovat Gradle wrapper 8.11.1: stáhnout `https://services.gradle.org/distributions/gradle-8.11.1-bin.zip` do scratchpadu, rozbalit a spustit `gradle wrapper --gradle-version 8.11.1` v kořeni projektu.
- [ ] **Step 3:** Zapsat build soubory s verzemi z Tech Stacku. Manifest: oprávnění `INTERNET`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS`, `WAKE_LOCK`. `MainActivity` zobrazí Compose text `@string/app_name`.
- [ ] **Step 4: Smoke test** `SmokeTest.app_name_is_czech`: přečte `R.string.app_name` přes Robolectric a ověří `"Audioknihy"`.
- [ ] **Step 5:** Spustit `.\gradlew.bat testDebugUnitTest assembleDebug`. Očekávané: BUILD SUCCESSFUL a existuje `app/build/outputs/apk/debug/app-debug.apk`.
- [ ] **Step 6: Commit** `chore: kostra projektu Audioknihy`

---

### Task 2: Seskupení stop do knih

**Files:**
- Create: `plex/PlexModels.kt` (jen `PlexTrack`), `library/Book.kt`, `library/BookGrouper.kt`
- Test: `library/BookGrouperTest.kt`

**Interfaces:**
- Produces:
```kotlin
data class PlexTrack(
    val ratingKey: String, val title: String, val albumTitle: String, val artist: String,
    val index: Int?, val durationMs: Long, val filePath: String, val partKey: String,
    val thumb: String?, val viewOffsetMs: Long?, val viewCount: Int, val lastViewedAtSec: Long?,
)
data class Book(val id: String, val title: String, val author: String, val coverThumb: String?,
                val files: List<PlexTrack>) { val durationMs: Long }
object BookGrouper { fun group(tracks: List<PlexTrack>): List<Book> }  // seřazeno podle author, title
```

- [ ] **Step 1: Testy** (cesty jako z Plexu: `/data/AudioKnihy/AudioKnihy/...`):
  - `cd_folders_merge_into_one_book`: stopy `…/7 - Harry Potter a Relikvie smrti (čte Jan Hyhlik)/CD1/01.mp3`, `CD1/02.mp3`, `CD2/01.mp3`, `CD3/01.mp3` s albem „… CD1“/„… CD2“ → jedna kniha, `id` končí `/7 - Harry Potter a Relikvie smrti (čte Jan Hyhlik)`, `title == "7 - Harry Potter a Relikvie smrti (čte Jan Hyhlik)"`, pořadí souborů CD1/01, CD1/02, CD2/01, CD3/01.
  - `cd_folder_variants`: `CD 1`, `cd2`, `Disk_3`, `Disc-4` se všechny poznají jako CD složky. `CD-ROM extras` ne.
  - `plain_folder_is_book_titled_by_album`: `Frank Herbert/01 Duna/*.m4a` s albem „DUNA“ → `title == "DUNA"`, `author == "Frank Herbert"`.
  - `mixed_albums_in_folder_use_folder_name`: dvě různá alba v jedné složce → `title` = jméno složky.
  - `ordering_by_index_then_natural_filename`: indexy 1, 2, 10 → 1, 2, 10. Bez indexů soubory `2.mp3`, `10.mp3`, `1.mp3` → 1, 2, 10.
  - `single_file_book`: `Sitarane/František Kotleta - Sitarane.m4a` → kniha s jedním souborem.
- [ ] **Step 2:** `.\gradlew.bat testDebugUnitTest --tests "*BookGrouperTest"` → FAIL (nepřeloží se).
- [ ] **Step 3:** Implementovat `BookGrouper.group`. CD regex: `^(cd|disk|disc)[ _-]*(\d+)$` (ignore case). Klíč řazení: (číslo CD, nebo 0), `index` (null až za čísla), přirozené řazení názvu souboru. `coverThumb` = `thumb` první stopy.
- [ ] **Step 4:** Test → PASS.
- [ ] **Step 5: Commit** `feat: seskupení stop do knih podle složek`

---

### Task 3: Rozhodování o pozici

**Files:**
- Create: `progress/BookProgress.kt`, `progress/ProgressResolver.kt`, `progress/ResumePosition.kt`
- Test: `progress/ProgressResolverTest.kt`, `progress/ResumePositionTest.kt`

**Interfaces:**
- Consumes: `Book`, `PlexTrack` (Task 2)
- Produces:
```kotlin
@Entity(tableName = "progress") data class BookProgress(
    @PrimaryKey val bookId: String, val fileIndex: Int, val trackRatingKey: String,
    val positionMs: Long, val updatedAt: Long, val lastPlayedAt: Long,
    val speed: Float = 1f, val finished: Boolean = false,
)
data class ServerPosition(val fileIndex: Int, val trackRatingKey: String, val positionMs: Long,
                          val updatedAt: Long, val finished: Boolean)
sealed interface Resolution { data object KeepLocal : Resolution; data class TakeServer(val pos: ServerPosition) : Resolution; data object Nothing : Resolution }
object ProgressResolver {
    const val SERVER_MARGIN_MS = 60_000L
    fun serverPosition(book: Book): ServerPosition?
    fun resolve(local: BookProgress?, server: ServerPosition?): Resolution
}
fun resumePositionMs(savedMs: Long, lastPlayedAt: Long, now: Long, rewindMs: Long, pauseThresholdMs: Long = 180_000L): Long
```

- [ ] **Step 1: Testy `ProgressResolverTest`:**
  - `server_position_from_latest_viewed_track_with_offset`: stopa 3 má `viewOffset=120_000` a nejnovější `lastViewedAt` → `ServerPosition(fileIndex=2, positionMs=120_000, updatedAt=lastViewedAt*1000, finished=false)`.
  - `server_position_after_fully_played_track`: nejnovější je stopa 2, `viewCount=1`, bez offsetu → `fileIndex=2, positionMs=0`.
  - `all_tracks_played_is_finished`: všechny `viewCount>0`, žádný offset → `finished=true`.
  - `untouched_book_has_no_server_position`: žádné `lastViewedAt` → `null`.
  - `no_local_takes_server`, `no_server_keeps_local`, `neither_is_nothing`.
  - `server_within_margin_keeps_local`: server `updatedAt = local.updatedAt + 59_000` → `KeepLocal`.
  - `server_older_keeps_local`: server o hodinu starší → `KeepLocal`.
  - `server_newer_beyond_margin_takes_server`: `+61_000` → `TakeServer`.
- [ ] **Step 2: Testy `ResumePositionTest`:** pauza 4 min, uloženo 50_000, převinutí 10_000 → 40_000. Pauza 1 min → 50_000. Uloženo 4_000 → 0 (nikdy záporné).
- [ ] **Step 3:** Spustit testy → FAIL.
- [ ] **Step 4:** Implementovat podle Global Constraints. `serverPosition`: kandidát = stopa s nejvyšším `lastViewedAtSec`. Pokud má `viewOffsetMs > 0` → ta stopa a offset. Pokud `viewCount > 0` → další stopa na 0 (pokud další není → `finished`). Pokud mají všechny stopy `viewCount > 0` a žádná nemá offset → `finished`.
- [ ] **Step 5:** Testy → PASS.
- [ ] **Step 6: Commit** `feat: pravidla pro pozici v knize`

---

### Task 4: Kapitoly

**Files:**
- Create: `chapters/Chapter.kt`, `chapters/ChapterIndex.kt`, `chapters/Mp4ChapterReader.kt`
- Test: `chapters/ChapterIndexTest.kt`, `chapters/Mp4ChapterReaderTest.kt`

**Interfaces:**
- Consumes: `Book` (Task 2)
- Produces:
```kotlin
data class Chapter(val title: String, val fileIndex: Int, val startMs: Long, val endMs: Long)
data class EmbeddedChapter(val startMs: Long, val title: String)
object Mp4ChapterReader {
    /** Vrátí velikost atomu moov, pokud bajty začínají ftyp+moov (moov je na začátku souboru). */
    fun moovRange(head: ByteArray): LongRange?
    fun parseChpl(moov: ByteArray): List<EmbeddedChapter>   // prázdné, když chpl není
}
fun buildChapters(book: Book, embedded: Map<Int, List<EmbeddedChapter>>): List<Chapter>
class ChapterIndex(val chapters: List<Chapter>) {
    fun indexAt(fileIndex: Int, posMs: Long): Int
    fun previousTarget(fileIndex: Int, posMs: Long): Chapter   // pravidlo 3 s
    fun next(fileIndex: Int, posMs: Long): Chapter?
    fun remainingMs(fileIndex: Int, posMs: Long): Long
}
```

- [ ] **Step 1: Testy `Mp4ChapterReaderTest`:** v testu se poskládá moov → udta → chpl (verze 1: 1 B verze, 3 B příznaky, 4 B rezervováno, 1 B počet; každá kapitola: 8 B čas v jednotkách 100 ns, 1 B délka názvu, UTF-8 název). Se třemi kapitolami (0 / 129.541 s / 389.590 s, názvy „Chapter 001“…) → `[EmbeddedChapter(0,"Chapter 001"), EmbeddedChapter(129_541,"Chapter 002"), EmbeddedChapter(389_590,"Chapter 003")]`. Bez chpl → prázdné. Poškozená délka → prázdné, bez výjimky. `moovRange` pro `ftyp(24 B)` + `moov` na offsetu 24 vrátí jeho rozsah. Pro `mdat` před `moov` vrátí `null`.
- [ ] **Step 2: Testy `ChapterIndexTest`:** kniha se 2 soubory, soubor 0 má 3 vložené kapitoly, soubor 1 žádnou → 4 kapitoly. Poslední kapitola souboru 0 končí na `files[0].durationMs`, kapitola souboru 1 se jmenuje podle `title` stopy. `previousTarget` 2 s do kapitoly 2 → kapitola 1, 10 s do kapitoly 2 → kapitola 2. `next` v poslední → `null`. `remainingMs` uprostřed kapitoly je správně.
- [ ] **Step 3:** Testy → FAIL. **Step 4:** Implementovat. **Step 5:** Testy → PASS.
- [ ] **Step 6: Commit** `feat: kapitoly z chpl a souborů`

---

### Task 5: Plex klient

**Files:**
- Create: `plex/PlexModels.kt` (DTO), `plex/PlexHeaders.kt`, `plex/PlexTvApi.kt`, `plex/PlexServerApi.kt`, `plex/PlexAuth.kt`, `plex/ServerConnector.kt`, `plex/PlexClient.kt`, `di/AppModule.kt` (síť)
- Test: `plex/PlexAuthTest.kt`, `plex/ServerConnectorTest.kt`, `plex/PlexClientTest.kt` (MockWebServer, JSON fixtury v `src/test/resources/plex/`)

**Interfaces:**
- Consumes: `PlexTrack` (Task 2)
- Produces:
```kotlin
class PlexHeaders(clientId: String)        // X-Plex-Client-Identifier (UUID uložené v DataStore), X-Plex-Product=Audioknihy, X-Plex-Version, X-Plex-Platform=Android, X-Plex-Device-Name, Accept: application/json
class PlexAuth { suspend fun createPin(): Pin; fun authUrl(pin: Pin): String; suspend fun pollToken(pin: Pin, timeoutMs: Long = 300_000): String? }
data class PlexServer(val name: String, val clientId: String, val accessToken: String, val connections: List<PlexConnection>)
data class PlexConnection(val uri: String, val local: Boolean, val relay: Boolean)
class ServerConnector { suspend fun servers(userToken: String): List<PlexServer>; suspend fun bestConnection(server: PlexServer): String? }
data class MusicSection(val key: String, val title: String)
class PlexClient(baseUrl: String, token: String) {
    suspend fun musicSections(): List<MusicSection>
    suspend fun allTracks(sectionKey: String): List<PlexTrack>          // stránkuje po 500
    suspend fun timeline(ratingKey: String, state: String, timeMs: Long, durationMs: Long)  // "playing"|"paused"|"stopped"
    suspend fun scrobble(ratingKey: String); suspend fun unscrobble(ratingKey: String)
    fun streamUrl(partKey: String): String
    fun imageUrl(thumb: String, sizePx: Int): String                   // /photo/:/transcode?width=&height=&url=
    suspend fun headRange(partKey: String, bytes: Int): ByteArray      // Range: bytes=0..n-1
}
```

- [ ] **Step 1: Testy:**
  - `PlexAuthTest`: `createPin` pošle `POST /api/v2/pins?strong=true` s hlavičkami `X-Plex-Client-Identifier` a `X-Plex-Product: Audioknihy`. `authUrl` = `https://app.plex.tv/auth#?clientID=<id>&code=<code>&context%5Bdevice%5D%5Bproduct%5D=Audioknihy`. `pollToken` vrátí `authToken`, jakmile ho `GET /api/v2/pins/{id}` obsahuje.
  - `ServerConnectorTest`: `GET /api/v2/resources?includeHttps=1&includeRelay=1` → jen položky, jejichž `provides` obsahuje `server`. `bestConnection` zkouší `GET {uri}/identity` (timeout 3 s) v pořadí lokální → vzdálené ne-relay → relay a vrátí první funkční.
  - `PlexClientTest`: `allTracks` volá `/library/sections/{key}/all?type=10` se `X-Plex-Container-Start`/`Size` a pro 1 200 stop udělá 3 stránky. Mapuje `parentTitle`, `grandparentTitle`, `index`, `duration`, `viewOffset`, `viewCount`, `lastViewedAt`, `Media[0].Part[0].key/file`, `thumb ?: parentThumb`. `timeline` volá `/:/timeline?ratingKey=…&key=/library/metadata/…&state=…&time=…&duration=…`. `scrobble` volá `/:/scrobble?identifier=com.plexapp.plugins.library&key=…`. `streamUrl` obsahuje `X-Plex-Token`.
- [ ] **Step 2:** Testy → FAIL. **Step 3:** Implementovat (Retrofit + Moshi, `OkHttp` interceptor pro hlavičky a token). **Step 4:** Testy → PASS.
- [ ] **Step 5: Commit** `feat: Plex klient (přihlášení, server, stopy, hlášení)`

---

### Task 6: Databáze, knihovna a fronta hlášení

**Files:**
- Create: `progress/AppDatabase.kt`, `progress/ProgressDao.kt`, `progress/CachedLibraryDao.kt`, `progress/PendingReport.kt`, `progress/ReportQueue.kt`, `progress/ReportWorker.kt`, `library/LibraryRepository.kt`, `chapters/ChapterRepository.kt`, `settings/Settings.kt`
- Test: `progress/ProgressDaoTest.kt` (Robolectric), `library/LibraryRepositoryTest.kt`, `progress/ReportQueueTest.kt`

**Interfaces:**
- Consumes: Task 2–5
- Produces:
```kotlin
interface ProgressDao {
    suspend fun get(bookId: String): BookProgress?
    suspend fun upsert(p: BookProgress)
    fun inProgress(): Flow<List<BookProgress>>      // finished = false, ORDER BY lastPlayedAt DESC
}
class LibraryRepository {
    val books: Flow<List<Book>>                      // z cache (Room, tracks jako JSON sloupec)
    suspend fun refresh()                            // allTracks → BookGrouper → cache → importServerProgress()
    suspend fun book(id: String): Book?
    internal suspend fun importServerProgress()     // pro každou knihu: resolve(local, serverPosition) → upsert při TakeServer
}
class ChapterRepository { suspend fun chapters(book: Book): ChapterIndex }  // cache v Room. Pro .m4a/.m4b: headRange(64 KiB) → moovRange → headRange(moov) → parseChpl
@Entity data class PendingReport(@PrimaryKey(autoGenerate = true) val id: Long = 0, val kind: String, val ratingKey: String, val state: String?, val timeMs: Long, val durationMs: Long, val createdAt: Long)
class ReportQueue { suspend fun timeline(...); suspend fun scrobbleBook(book: Book); suspend fun unscrobbleBook(book: Book) }  // zkusí hned, při chybě uloží a naplánuje ReportWorker (síť CONNECTED, exponenciální backoff)
class Settings {   // DataStore
    val skipBackMs: Flow<Long>  // 15_000
    val skipForwardMs: Flow<Long>  // 30_000
    val resumeRewindMs: Flow<Long>  // 10_000
    val syncPositionToServer: Flow<Boolean>  // true; false, když Task 9 zjistí, že server viewOffset neukládá
    // + plexUserToken, serverUri, serverToken, sectionKey, clientId
}
```

- [ ] **Step 1: Testy:**
  - `ProgressDaoTest.in_progress_ordered_and_excludes_finished`: 3 knihy, jedna `finished` → vrátí 2 seřazené podle `lastPlayedAt` sestupně.
  - `ProgressDaoTest.upsert_one_book_does_not_touch_other`: upsert knihy A dvakrát nezmění záznam knihy B.
  - `LibraryRepositoryTest.import_adds_books_started_in_plexamp`: falešný `PlexClient` vrátí knihu se stopou s `viewOffset` → po `refresh()` je v `inProgress()`.
  - `LibraryRepositoryTest.import_never_overwrites_newer_local`: místní pozice je novější → po `refresh()` beze změny.
  - `LibraryRepositoryTest.refresh_failure_keeps_cache_and_progress`: klient vyhodí `IOException` → `books` i pozice zůstanou, výjimka se propaguje jako `Result.failure`.
  - `ReportQueueTest.failed_report_is_persisted_and_retried`: první volání selže → 1 `PendingReport`, po úspěšném `flush()` 0.
- [ ] **Step 2:** Testy → FAIL. **Step 3:** Implementovat. **Step 4:** Testy → PASS.
- [ ] **Step 5: Commit** `feat: místní databáze, knihovna a fronta hlášení`

---

### Task 7: Časovač vypnutí

**Files:**
- Create: `sleep/SleepTimer.kt`, `sleep/ShakeDetector.kt`
- Test: `sleep/SleepTimerTest.kt`

**Interfaces:**
- Produces:
```kotlin
sealed interface SleepMode { data class Minutes(val minutes: Int) : SleepMode; data object EndOfChapter : SleepMode }
data class TimerTick(val volume: Float, val shouldPause: Boolean, val remainingMs: Long?)
class SleepTimer(clock: () -> Long) {
    companion object { const val FADE_MS = 10_000L; const val SHAKE_GRACE_MS = 60_000L }
    val state: StateFlow<SleepMode?>
    fun start(mode: SleepMode)
    fun tick(chapterRemainingRealMs: Long): TimerTick   // remaining v reálném čase = mediální / rychlost
    fun onShake(): Boolean                              // true = prodlouženo (volající pak play())
    fun cancel()
}
class ShakeDetector(context: Context, onShake: () -> Unit) { fun start(); fun stop() }   // akcelerometr, práh 2.7 g, aktivní jen při běžícím časovači nebo v grace době
```

- [ ] **Step 1: Testy:** `Minutes(15)`: v 14:49 hlasitost 1.0, v 14:55 0.5, v 15:00 `shouldPause = true`. `EndOfChapter`: zbývá 5 s → hlasitost 0.5, 0 s → pause. `onShake` během ztlumení → `remainingMs` = 15 min, hlasitost 1.0. `onShake` 30 s po vypnutí → `true`. 90 s po vypnutí → `false`. `cancel` → hlasitost 1.0, `state = null`.
- [ ] **Step 2:** FAIL. **Step 3:** Implementovat. **Step 4:** PASS.
- [ ] **Step 5: Commit** `feat: časovač vypnutí se ztlumením a zatřesením`

---

### Task 8: Přehrávací jádro (BookSession)

**Files:**
- Create: `playback/PlayerFacade.kt`, `playback/BookSession.kt`, `playback/TrackUriResolver.kt`
- Test: `playback/BookSessionTest.kt` (s `FakePlayerFacade` a falešnými hodinami)

**Interfaces:**
- Consumes: Task 3, 4, 6, 7 (`SleepTimer`)
- Produces:
```kotlin
interface PlayerFacade {
    val fileIndex: Int; val positionMs: Long; val isPlaying: Boolean
    fun load(uris: List<String>, fileIndex: Int, positionMs: Long, speed: Float)
    fun play(); fun pause(); fun seekTo(fileIndex: Int, positionMs: Long)
    fun setSpeed(speed: Float); fun setVolume(v: Float)
}
fun interface TrackUriResolver { fun uri(track: PlexTrack): String }   // dnes PlexClient.streamUrl
sealed interface StartAt { data object Saved : StartAt; data object Beginning : StartAt; data class AtChapter(val chapter: Chapter) : StartAt }
class BookSession(player: PlayerFacade, progress: ProgressDao, chapters: ChapterRepository, reports: ReportQueue, settings: Settings, uris: TrackUriResolver, clock: () -> Long) {
    val current: StateFlow<Book?>
    suspend fun open(book: Book, startAt: StartAt = StartAt.Saved)   // 1) save(current) 2) cancel timer, volume 1.0 3) načte a spustí
    suspend fun save(reason: String)                                  // upsert BookProgress, timeline do ReportQueue (pokud syncPositionToServer)
    suspend fun onTick()                                              // voláno každou 1 s; ukládá každých 5 s při přehrávání
    suspend fun skipBack(); suspend fun skipForward()                // přes hranice souborů
    suspend fun prevChapter(); suspend fun nextChapter()
    suspend fun setSpeed(speed: Float)                                // oříznout na 0.5..3.0, uložit do knihy
    suspend fun onBookEnded()                                         // finished = true, scrobbleBook, pause, nic dalšího nehrát
    suspend fun setFinished(book: Book, finished: Boolean)
    val sleepTimer: SleepTimer                                        // Task 7; open() volá cancel(); onTick(): setVolume(tick.volume), při shouldPause → pause() + save("sleep"), při zatřesení → play()
}
```

- [ ] **Step 1: Testy `BookSessionTest`:**
  - `open_resumes_saved_position_with_rewind`: uloženo soubor 2 / 50_000, `lastPlayedAt` před 4 min → `load(..., 2, 40_000, speed)`.
  - `switching_book_saves_previous_first`: hraje A na 1 / 70_000, `open(B)` → v DAO je A = (1, 70_000) dřív, než se zavolá `load` pro B.
  - `periodic_save_every_5s`: 12× `onTick()` po 1 s při přehrávání → 2 zápisy, při pauze 0.
  - `pause_saves_immediately`.
  - `missing_track_falls_back_to_file_index`: uložené `trackRatingKey` v knize není, `fileIndex=1` → start na souboru 1. `fileIndex=9` u knihy se 3 soubory → soubor 2 na 0, žádná výjimka.
  - `skip_back_crosses_file_boundary`: 5_000 do souboru 3, předchozí soubor má 600_000 → `seekTo(2, 590_000)`.
  - `skip_forward_past_end_of_last_file_finishes_book`.
  - `speed_is_per_book`: A na 1.5, B otevřená → B 1.0. Návrat na A → 1.5.
  - `book_end_marks_finished_and_does_not_continue`: `onBookEnded()` → `finished = true`, `scrobbleBook` zavoláno, `load` už nezavoláno.
  - `open_resets_volume_and_cancels_timer`.
- [ ] **Step 2:** Testy → FAIL. **Step 3:** Implementovat. **Step 4:** Testy → PASS.
- [ ] **Step 5: Commit** `feat: přehrávací jádro s pamětí pozice u každé knihy`

---

### Task 9: Přehrávací služba, přihlášení a ověření serveru

**Files:**
- Create: `playback/ExoPlayerFacade.kt`, `playback/PlaybackService.kt`, `ui/login/*`, `ui/setup/*`, `ui/Nav.kt`
- Modify: `AndroidManifest.xml` (služba `MediaLibraryService` s `foregroundServiceType="mediaPlayback"`, intent-filter `androidx.media3.session.MediaLibraryService` a `android.media.browse.MediaBrowserService`)

**Interfaces:**
- Consumes: Task 5–8
- Produces: služba, která drží `BookSession` (Hilt singleton), každou 1 s volá `onTick()`, při `onTaskRemoved`/`onDestroy` volá `save()`, při `ACTION_AUDIO_BECOMING_NOISY` pauzuje a ukládá. Notifikace a zamčená obrazovka přes `MediaLibrarySession` (tlačítka: −15 s, play/pause, +30 s). Tok přihlášení: Login (otevře `authUrl` v Custom Tab, čeká na token) → výběr serveru → výběr hudební knihovny → `LibraryRepository.refresh()`.

- [ ] **Step 1:** Implementovat `ExoPlayerFacade` (ExoPlayer s `AudioAttributes` USAGE_MEDIA/CONTENT_TYPE_SPEECH, `handleAudioFocus = true`, `setHandleAudioBecomingNoisy(true)`). Mediální položky mají `mediaId = ratingKey`. Na `STATE_ENDED` posledního souboru volá `BookSession.onBookEnded()`.
- [ ] **Step 2:** Implementovat `PlaybackService` a obrazovky přihlášení a nastavení serveru.
- [ ] **Step 3:** `.\gradlew.bat assembleDebug` → BUILD SUCCESSFUL. Nainstalovat `adb install -r app\build\outputs\apk\debug\app-debug.apk`.
- [ ] **Step 4: Ověření serveru (ručně, se zařízením):** přihlásit se, vybrat knihovnu audioknih, spustit libovolnou knihu na 2 min, pauza. Potom přes `adb logcat` / dočasné debug tlačítko načíst `GET /library/metadata/{ratingKey}` té stopy. Očekávané: `viewOffset` ≈ pozice pauzy a `lastViewedAt` aktuální. **Pokud `viewOffset` chybí:** nastavit výchozí `syncPositionToServer = false`, zapsat zjištění do specu (sekce Model pozice) a import z Plexampu omezit na stav dokončeno. Výsledek zapsat do commit zprávy.
- [ ] **Step 5: Commit** `feat: přehrávací služba a přihlášení k Plexu`

---

### Task 10: Obrazovky

**Files:**
- Create: `ui/home/*`, `ui/library/*`, `ui/book/*`, `ui/player/*`, `ui/settings/*`, `ui/theme/*`
- Test: `ui/home/InProgressViewModelTest.kt`, `ui/library/LibraryViewModelTest.kt`

**Interfaces:**
- Consumes: Task 6–9
- Produces obrazovky (Material 3, tmavý motiv podle systému):
  - **Rozposlouchané** (výchozí): obal, název, autor, „kap. X/Y“, ukazatel průběhu, „zbývá N h M min“. Klepnutí = `open(book, Saved)` + přechod na Přehrávač. Dlouhý stisk = „Označit jako dokončené“. Dole mini-přehrávač aktuální knihy. Při chybě obnovy knihovny snackbar „Server Plex není dostupný“, seznam zůstává.
  - **Knihovna:** seznam autorů → knihy, vyhledávání v názvu i autorovi bez ohledu na diakritiku.
  - **Detail knihy:** Pokračovat / Od začátku / seznam kapitol (klepnutí = `AtChapter`), Označit jako (ne)dokončené.
  - **Přehrávač:** obal, název kapitoly, posuvník v kapitole, celkový průběh knihy, −15/+30, předchozí/další kapitola, rychlost (dialog 0,5–3,0 po 0,1), časovač (15/30/45/60 min, Do konce kapitoly, Zrušit, zbývající čas).
  - **Nastavení:** délka skoků, převinutí při pokračování, odhlásit.

- [ ] **Step 1: Testy:** `InProgressViewModelTest.maps_progress_to_rows`: kniha 10 h, pozice 4 h → řádek „zbývá 6 h 0 min“ a průběh 0.4. `LibraryViewModelTest.search_ignores_diacritics`: dotaz „sapkowski zaklinac“ najde „Zaklínač“.
- [ ] **Step 2:** FAIL. **Step 3:** Implementovat ViewModely a obrazovky. **Step 4:** PASS a `assembleDebug` OK.
- [ ] **Step 5:** Ruční kontrola na telefonu: projít všechny obrazovky a ověřit české texty a bez pádu.
- [ ] **Step 6: Commit** `feat: obrazovky aplikace`

---

### Task 11: Android Auto

**Files:**
- Create: `auto/AutoTree.kt`, `res/xml/automotive_app_desc.xml`
- Modify: `PlaybackService.kt` (`MediaLibrarySession.Callback.onGetLibraryRoot/onGetChildren/onSetMediaItems`), `AndroidManifest.xml` (`<meta-data android:name="com.google.android.gms.car.application" android:resource="@xml/automotive_app_desc"/>`)
- Test: `auto/AutoTreeTest.kt`

**Interfaces:**
- Consumes: `LibraryRepository`, `ProgressDao`, `BookSession`
- Produces:
```kotlin
object AutoTree {
    const val ROOT = "root"; const val IN_PROGRESS = "inprogress"; const val LIBRARY = "library"
    fun authorId(author: String) = "author:$author"; fun bookId(id: String) = "book:$id"
    fun children(parentId: String, books: List<Book>, progress: List<BookProgress>): List<MediaItem>
}
```
  Kořen: „Rozposlouchané“, „Knihovna“. Knihovna → autoři (procházitelné) → knihy (hratelné). Výběr knihy = `BookSession.open(book, Saved)`. Tlačítka v autě: −15 s, play/pause, +30 s. Další/předchozí = další/předchozí kapitola (custom `SessionCommand`).

- [ ] **Step 1: Testy:** `root_has_two_folders`, `in_progress_lists_books_in_order_playable`, `library_groups_by_author`, `book_item_id_round_trips` (cesta s diakritikou a `/`).
- [ ] **Step 2:** FAIL. **Step 3:** Implementovat. **Step 4:** PASS.
- [ ] **Step 5:** Ruční ověření v Desktop Head Unit (`DHU` ze SDK Extras), případně v autě: procházení, spuštění knihy, pokračování na uložené pozici.
- [ ] **Step 6: Commit** `feat: Android Auto`

---

### Task 12: Vydání a přejímka

**Files:**
- Create: `README.md` (česky), `app/release.keystore` (není v gitu; heslo v `keystore.properties`, není v gitu)
- Modify: `app/build.gradle.kts` (signingConfig `release` z `keystore.properties`, `isMinifyEnabled = true` + pravidla pro Moshi/Retrofit)

- [ ] **Step 1:** Vytvořit podpisový klíč `keytool -genkeypair -v -keystore app/release.keystore -alias audioknihy -keyalg RSA -keysize 2048 -validity 10000` (heslo vygenerovat a uložit do `keystore.properties`). `.\gradlew.bat assembleRelease` → `app-release.apk`.
- [ ] **Step 2:** `README.md`: instalace APK (povolit instalaci z neznámých zdrojů), první přihlášení, a Android Auto krok za krokem: Nastavení → Android Auto → 10× klepnout na „Verze“ → ⋮ → Nastavení pro vývojáře → „Neznámé zdroje“ zapnout.
- [ ] **Step 3: Přejímka na telefonu (každý bod musí projít):**
  1. Kniha A 3 min, přepnout na B 2 min, zpět na A → pokračuje na A ~10 s před místem odchodu (pauza > 3 min) nebo přesně (pauza < 3 min).
  2. Při přehrávání `adb shell am kill cz.audioknihy` (s aplikací na pozadí) a znovu otevřít → pozice nejvýš 5 s stará.
  3. Vypnout Wi-Fi i data během přehrávání a pozastavit, pak zapnout → v Plexu se pozice doplní (fronta hlášení), v aplikaci se pozice nezměnila.
  4. Časovač 15 min a „do konce kapitoly“ na *Sitarane* (vložené kapitoly) i na *Duna* (soubor = kapitola) → ztlumí, zastaví, pozice uložena. Zatřesení prodlouží.
  5. Harry Potter 7 se zobrazí jako jedna kniha se všemi CD ve správném pořadí.
  6. Knihy rozposlouchané v Plexampu se objevily v „Rozposlouchané“ po prvním přihlášení (nebo je zapsáno, proč ne, viz Task 9 Step 4).
  7. Android Auto: kniha se spustí z „Rozposlouchané“ na uložené pozici.
- [ ] **Step 4: Commit** `chore: release build a návod`
