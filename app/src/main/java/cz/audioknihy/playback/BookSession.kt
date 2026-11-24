package cz.audioknihy.playback

import cz.audioknihy.chapters.Chapter
import cz.audioknihy.chapters.ChapterIndex
import cz.audioknihy.chapters.ChapterSource
import cz.audioknihy.library.Book
import cz.audioknihy.progress.BookProgress
import cz.audioknihy.progress.ProgressDao
import cz.audioknihy.progress.ProgressResolver
import cz.audioknihy.progress.Resolution
import cz.audioknihy.progress.ServerPosition
import cz.audioknihy.progress.Reporter
import cz.audioknihy.progress.resumePositionMs
import cz.audioknihy.sleep.SleepTimer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt

sealed interface StartAt {
    data object Saved : StartAt
    data object Beginning : StartAt
    data class AtChapter(val chapter: Chapter) : StartAt
}

/** Stav přehrávání pro obrazovky. */
data class SessionState(
    val book: Book? = null,
    val fileIndex: Int = 0,
    val positionMs: Long = 0,
    val isPlaying: Boolean = false,
    val speed: Float = 1f,
    val chapters: ChapterIndex = ChapterIndex(emptyList()),
) {
    val chapterIndex: Int get() = chapters.indexAt(fileIndex, positionMs)
    val chapter: Chapter? get() = chapters.chapters.getOrNull(chapterIndex)
    val bookPositionMs: Long get() = (book?.files?.take(fileIndex)?.sumOf { it.durationMs } ?: 0) + positionMs
}

/**
 * Jádro přehrávače audioknih: otevírá knihy, ukládá pozici u každé knihy zvlášť,
 * skáče přes hranice souborů a řídí časovač vypnutí.
 *
 * Pod zámkem se dělá jen práce s přehrávačem a místní databází. Síť (hlášení do Plexu,
 * načítání kapitol) běží mimo zámek, aby nezdržovala pauzu ani průběžné ukládání.
 */
class BookSession(
    private val player: PlayerFacade,
    private val progress: ProgressDao,
    private val chapterSource: ChapterSource,
    private val reporter: Reporter,
    private val prefs: PlaybackPrefs,
    private val uris: TrackUriResolver,
    private val clock: () -> Long,
    val sleepTimer: SleepTimer,
    private val artwork: (Book) -> String? = { null },
    /** Spustí práci na pozadí (hlášení do Plexu). V aplikaci launch v aplikačním scope. */
    private val fire: (suspend () -> Unit) -> Unit = { block -> runBlocking { block() } },
    /** Aktuální pozice knihy na serveru (při otevření), null = nevíme. */
    private val serverCheck: suspend (Book) -> ServerPosition? = { null },
    /** Před otevřením knihy: ověří spojení se serverem (doma × venku). */
    private val beforeOpen: suspend () -> Unit = {},
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _current = MutableStateFlow<Book?>(null)
    val current: StateFlow<Book?> = _current.asStateFlow()

    private var chapters = ChapterIndex(emptyList())
    private var speed = 1f
    private var lastSaveAt = 0L
    private var lastFileIndex = -1
    /** Kdy naposledy opravdu hrálo (pro převinutí po pauze). */
    private var lastPlayingAt = 0L
    /** Aktuální kniha je dokončená: žádné další ukládání nesmí zrušit „dokončeno“. */
    private var currentFinished = false

    /** Aktuální kniha je dokončená (Přehrát pak nic nedělá). */
    val isCurrentFinished: Boolean get() = currentFinished

    suspend fun open(book: Book, startAt: StartAt = StartAt.Saved) {
        runCatching { beforeOpen() }
        // Pozice ze serveru (jiné zařízení) se zjišťuje mimo zámek a s krátkým limitem.
        val server = if (startAt == StartAt.Saved) {
            runCatching { withTimeoutOrNull(SERVER_CHECK_TIMEOUT_MS) { serverCheck(book) } }.getOrNull()
        } else null
        mutex.withLock {
            if (_current.value != null) saveLocked()
            sleepTimer.cancel()
            player.setVolume(1f)

            var saved = progress.get(book.id)
            val resolution = ProgressResolver.resolve(saved, server)
            if (server != null && resolution is Resolution.TakeServer) {
                saved = (saved ?: BookProgress(book.id, 0, "", 0, 0, 0)).copy(
                    fileIndex = server.fileIndex, trackRatingKey = server.trackRatingKey, positionMs = server.positionMs,
                    updatedAt = server.updatedAt, lastPlayedAt = server.updatedAt, finished = server.finished,
                )
                progress.upsert(saved)
            }
            val wasFinished = saved?.finished == true
            var (fileIndex, positionMs) = when (startAt) {
                StartAt.Beginning -> 0 to 0L
                is StartAt.AtChapter -> startAt.chapter.fileIndex to startAt.chapter.startMs
                StartAt.Saved -> savedStart(book, saved)
            }
            fileIndex = fileIndex.coerceIn(0, book.files.lastIndex)
            val duration = book.files[fileIndex].durationMs
            positionMs = if (duration > 0) positionMs.coerceIn(0, duration) else positionMs.coerceAtLeast(0)
            speed = saved?.speed ?: 1f

            val items = book.files.map { t ->
                PlayItem(t.ratingKey, uris.uri(t), t.title, book.title, book.author, artwork(book))
            }
            player.load(items, fileIndex, positionMs, speed)
            _current.value = book
            currentFinished = false
            lastFileIndex = fileIndex
            chapters = ChapterIndex(emptyList())
            player.play()
            lastPlayingAt = clock()
            if (wasFinished) fire { reporter.unscrobbleBook(book) }
            saveLocked()
            publish()
        }
        // Kapitoly se načítají mimo zámek (může to trvat).
        val loaded = runCatching { chapterSource.chapters(book) }.getOrElse { ChapterIndex(emptyList()) }
        mutex.withLock {
            if (_current.value?.id == book.id) {
                chapters = loaded
                publish()
            }
        }
    }

    /**
     * Znovu načte soubory aktuální knihy (nová adresa serveru, např. doma → venku)
     * na stejném místě a ve stejném stavu přehrávání.
     */
    suspend fun reloadSources() = mutex.withLock {
        val book = _current.value ?: return@withLock
        if (currentFinished) return@withLock
        val fi = player.fileIndex.coerceIn(0, book.files.lastIndex)
        val pos = player.positionMs.coerceAtLeast(0)
        val wasPlaying = player.isPlaying
        val items = book.files.map { t -> PlayItem(t.ratingKey, uris.uri(t), t.title, book.title, book.author, artwork(book)) }
        player.load(items, fi, pos, speed)
        if (wasPlaying) player.play()
        publish()
    }

    private suspend fun savedStart(book: Book, saved: BookProgress?): Pair<Int, Long> {
        if (saved == null || saved.finished) return 0 to 0L
        val byKey = book.files.indexOfFirst { it.ratingKey == saved.trackRatingKey }
        val (idx, pos) = when {
            byKey >= 0 -> byKey to saved.positionMs
            saved.fileIndex in book.files.indices -> saved.fileIndex to saved.positionMs
            else -> book.files.lastIndex to 0L
        }
        return idx to resumePositionMs(pos, saved.lastPlayedAt, clock(), prefs.resumeRewindMs())
    }

    suspend fun save(reason: String = "") = mutex.withLock { saveLocked() }

    /** Aktuální pozice bez čekání (pro nouzové uložení při ukončení služby). */
    fun snapshot(): BookProgress? {
        val book = _current.value ?: return null
        if (currentFinished) return null
        val fi = player.fileIndex.coerceIn(0, book.files.lastIndex)
        val now = clock()
        val lastPlayed = if (player.isPlaying) now else lastPlayingAt
        return BookProgress(book.id, fi, book.files[fi].ratingKey, player.positionMs.coerceAtLeast(0), now, lastPlayed, speed)
    }

    private suspend fun saveLocked() {
        val book = _current.value ?: return
        if (currentFinished) return
        val fi = player.fileIndex.coerceIn(0, book.files.lastIndex)
        val pos = player.positionMs.coerceAtLeast(0)
        val track = book.files[fi]
        val now = clock()
        val playing = player.isPlaying
        if (playing) lastPlayingAt = now
        progress.upsert(BookProgress(book.id, fi, track.ratingKey, pos, now, lastPlayingAt, speed, finished = false))
        lastSaveAt = now
        lastFileIndex = fi
        if (prefs.syncToServer()) {
            val state = if (playing) "playing" else "paused"
            fire { reporter.timeline(track.ratingKey, state, pos, track.durationMs) }
        }
    }

    /** Volat každou 1 s: časovač, průběžné ukládání, změna souboru. */
    suspend fun onTick() = mutex.withLock {
        if (_current.value == null) return@withLock
        if (sleepTimer.state.value != null) {
            val remaining = (chapters.remainingMs(player.fileIndex, player.positionMs) / speed).toLong()
            val tick = sleepTimer.tick(if (chapters.chapters.isEmpty()) Long.MAX_VALUE else remaining)
            if (tick.shouldPause) {
                player.pause()
                player.setVolume(1f)
                saveLocked()
                publish()
                return@withLock
            }
            player.setVolume(tick.volume)
        }
        if (player.isPlaying) lastPlayingAt = clock()
        if (player.fileIndex != lastFileIndex) {
            saveLocked()
        } else if (player.isPlaying && clock() - lastSaveAt >= SAVE_INTERVAL_MS) {
            saveLocked()
        }
        publish()
    }

    /** Pokračování; po pauze delší než 3 min se vrátí o nastavený kus zpět. */
    suspend fun play() = mutex.withLock {
        if (_current.value == null || player.isPlaying || currentFinished) return@withLock
        val target = resumePositionMs(player.positionMs, lastPlayingAt, clock(), prefs.resumeRewindMs())
        if (target != player.positionMs) player.seekTo(player.fileIndex, target)
        player.play()
        lastPlayingAt = clock()
        publish()
    }

    suspend fun pause() = mutex.withLock {
        if (player.isPlaying) lastPlayingAt = clock()
        player.pause()
        saveLocked()
        publish()
    }

    /** Zatřesení telefonem: když prodloužilo časovač, pokračuje v přehrávání. */
    suspend fun onShake() = mutex.withLock {
        if (sleepTimer.onShake()) {
            player.setVolume(1f)
            if (!player.isPlaying && _current.value != null) {
                player.play()
                lastPlayingAt = clock()
            }
            publish()
        }
    }

    suspend fun cancelSleep() = mutex.withLock {
        sleepTimer.cancel()
        player.setVolume(1f)
        publish()
    }

    suspend fun skipBack() = mutex.withLock {
        val book = _current.value ?: return@withLock
        var remaining = prefs.skipBackMs() - player.positionMs
        var i = player.fileIndex
        if (remaining <= 0) {
            seek(i, -remaining)
            return@withLock
        }
        while (--i >= 0) {
            val d = book.files[i].durationMs
            if (d >= remaining) {
                seek(i, d - remaining)
                return@withLock
            }
            remaining -= d
        }
        seek(0, 0)
    }

    suspend fun skipForward() = mutex.withLock {
        val book = _current.value ?: return@withLock
        var target = player.positionMs + prefs.skipForwardMs()
        var i = player.fileIndex
        while (i <= book.files.lastIndex) {
            val d = book.files[i].durationMs
            if (target < d) {
                seek(i, target)
                return@withLock
            }
            target -= d
            i++
        }
        finishLocked(book)
    }

    suspend fun seekTo(fileIndex: Int, positionMs: Long) = mutex.withLock {
        val book = _current.value ?: return@withLock
        seek(fileIndex.coerceIn(0, book.files.lastIndex), positionMs.coerceAtLeast(0))
        saveLocked()
    }

    suspend fun prevChapter() = mutex.withLock {
        if (_current.value == null || chapters.chapters.isEmpty()) return@withLock
        val ch = chapters.previousTarget(player.fileIndex, player.positionMs)
        seek(ch.fileIndex, ch.startMs)
    }

    suspend fun nextChapter() = mutex.withLock {
        if (_current.value == null || chapters.chapters.isEmpty()) return@withLock
        val ch = chapters.next(player.fileIndex, player.positionMs) ?: return@withLock
        seek(ch.fileIndex, ch.startMs)
    }

    suspend fun setSpeed(value: Float) = mutex.withLock {
        speed = ((value * 10).roundToInt() / 10f).coerceIn(MIN_SPEED, MAX_SPEED)
        player.setSpeed(speed)
        saveLocked()
        publish()
    }

    /** Konec posledního souboru: kniha je dokončená a nic dalšího se nepřehrává. */
    suspend fun onBookEnded() = mutex.withLock {
        val book = _current.value ?: return@withLock
        finishLocked(book)
    }

    suspend fun setFinished(book: Book, finished: Boolean) = mutex.withLock {
        if (finished) {
            if (_current.value?.id == book.id) finishLocked(book) else markFinished(book)
        } else {
            if (_current.value?.id == book.id) currentFinished = false
            val existing = progress.get(book.id)
            val now = clock()
            progress.upsert(
                BookProgress(book.id, 0, book.files.first().ratingKey, 0, now, now, existing?.speed ?: 1f, finished = false)
            )
            fire { reporter.unscrobbleBook(book) }
        }
    }

    /**
     * „Nepřehrané“: kniha zmizí z Rozposlouchaných a začne od začátku. Na Plexu se odznačí.
     * Je-li právě načtená, přehrávání se zastaví a kniha se uvolní, aby ji další uložení nevrátilo.
     */
    suspend fun setUnplayed(book: Book) = mutex.withLock {
        if (_current.value?.id == book.id) {
            player.pause()
            sleepTimer.cancel()
            player.setVolume(1f)
            _current.value = null
            currentFinished = false
            chapters = ChapterIndex(emptyList())
        }
        val existing = progress.get(book.id)
        progress.upsert(
            BookProgress(book.id, 0, book.files.first().ratingKey, 0, clock(), lastPlayedAt = 0, existing?.speed ?: 1f, finished = false)
        )
        fire { reporter.unscrobbleBook(book) }
        publish()
    }

    private suspend fun finishLocked(book: Book) {
        currentFinished = true
        player.pause()
        markFinished(book)
        publish()
    }

    private suspend fun markFinished(book: Book) {
        val now = clock()
        val existing = progress.get(book.id)
        progress.upsert(
            BookProgress(book.id, book.files.lastIndex, book.files.last().ratingKey, 0, now, now, existing?.speed ?: speed, finished = true)
        )
        fire { reporter.scrobbleBook(book) }
    }

    private suspend fun seek(fileIndex: Int, positionMs: Long) {
        player.seekTo(fileIndex, positionMs)
        if (fileIndex != lastFileIndex) saveLocked()
        publish()
    }

    private fun publish() {
        _state.value = SessionState(
            book = _current.value,
            fileIndex = player.fileIndex,
            positionMs = player.positionMs,
            isPlaying = player.isPlaying,
            speed = speed,
            chapters = chapters,
        )
    }

    companion object {
        const val SAVE_INTERVAL_MS = 5_000L
        const val SERVER_CHECK_TIMEOUT_MS = 3_000L
        const val MIN_SPEED = 0.5f
        const val MAX_SPEED = 3.0f
    }
}
