package cz.audioknihy.playback

import cz.audioknihy.chapters.ChapterIndex
import cz.audioknihy.chapters.ChapterSource
import cz.audioknihy.chapters.buildChapters
import cz.audioknihy.library.Book
import cz.audioknihy.library.track
import cz.audioknihy.progress.BookProgress
import cz.audioknihy.progress.ProgressDao
import cz.audioknihy.progress.Reporter
import cz.audioknihy.progress.ServerPosition
import cz.audioknihy.sleep.SleepMode
import cz.audioknihy.sleep.SleepTimer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakePlayer(private val log: MutableList<String>) : PlayerFacade {
    override var fileIndex = 0
    override var positionMs = 0L
    override var isPlaying = false
    var currentSpeed = 1f
    var currentVolume = 1f
    var loadedUris: List<String> = emptyList()

    override fun load(items: List<PlayItem>, fileIndex: Int, positionMs: Long, speed: Float) {
        log += "load:${items.first().mediaId}:$fileIndex:$positionMs:$speed"
        loadedUris = items.map { it.uri }
        this.fileIndex = fileIndex; this.positionMs = positionMs; this.currentSpeed = speed
    }
    override fun play() { isPlaying = true; log += "play" }
    override fun pause() { isPlaying = false; log += "pause" }
    override fun seekTo(fileIndex: Int, positionMs: Long) {
        log += "seek:$fileIndex:$positionMs"; this.fileIndex = fileIndex; this.positionMs = positionMs
    }
    override fun setSpeed(speed: Float) { currentSpeed = speed }
    override fun setVolume(v: Float) { currentVolume = v }
}

class FakeProgressDao(private val log: MutableList<String>) : ProgressDao {
    val map = MutableStateFlow<Map<String, BookProgress>>(emptyMap())
    var writes = 0
    override suspend fun get(bookId: String) = map.value[bookId]
    override suspend fun upsert(p: BookProgress) {
        writes++
        log += "upsert:${p.bookId}:${p.fileIndex}:${p.positionMs}"
        map.value = map.value + (p.bookId to p)
    }
    override fun inProgress(): Flow<List<BookProgress>> = map.map { m -> m.values.filter { !it.finished && it.lastPlayedAt > 0 }.sortedByDescending { it.lastPlayedAt } }
    override fun all(): Flow<List<BookProgress>> = map.map { it.values.toList() }
}

class FakeReporter : Reporter {
    val calls = mutableListOf<String>()
    override suspend fun timeline(ratingKey: String, state: String, timeMs: Long, durationMs: Long) { calls += "timeline:$ratingKey:$state" }
    override suspend fun scrobbleBook(book: Book) { calls += "scrobble:${book.id}" }
    override suspend fun unscrobbleBook(book: Book) { calls += "unscrobble:${book.id}" }
}

class BookSessionTest {
    private var now = 100_000_000L
    private val log = mutableListOf<String>()
    private val player = FakePlayer(log)
    private val dao = FakeProgressDao(log)
    private val reporter = FakeReporter()
    private val timer = SleepTimer { now }
    private val chapters = ChapterSource { b -> ChapterIndex(buildChapters(b, emptyMap())) }
    private val prefs = object : PlaybackPrefs {
        override suspend fun skipBackMs() = 15_000L
        override suspend fun skipForwardMs() = 30_000L
        override suspend fun resumeRewindMs() = 10_000L
        override suspend fun syncToServer() = true
    }
    private val session = BookSession(player, dao, chapters, reporter, prefs, { "uri:" + it.ratingKey }, { now }, timer)

    private fun book(id: String, vararg durations: Long) = Book(
        id = id, title = id, author = "A", coverThumb = null,
        files = durations.mapIndexed { i, d -> track("$id/$i.mp3", key = "$id-$i", durationMs = d, title = "Část ${i + 1}") },
    )

    private val a = book("A", 600_000, 600_000, 600_000)
    private val b = book("B", 300_000, 300_000)

    @Test
    fun open_resumes_saved_position_with_rewind() = runTest {
        dao.upsert(BookProgress("A", 2, "A-2", 50_000, now - 240_000, now - 240_000, speed = 1.2f))
        session.open(a)
        assertTrue(log.contains("load:A-0:2:40000:1.2"))
        assertTrue(player.isPlaying)
    }

    @Test
    fun open_without_progress_starts_at_beginning() = runTest {
        session.open(a)
        assertTrue(log.contains("load:A-0:0:0:1.0"))
    }

    @Test
    fun switching_book_saves_previous_first() = runTest {
        session.open(a)
        player.fileIndex = 1; player.positionMs = 70_000
        session.open(b)
        val saveIdx = log.indexOf("upsert:A:1:70000")
        val loadIdx = log.indexOfFirst { it.startsWith("load:B-0") }
        assertTrue(saveIdx in 0 until loadIdx)
        assertEquals(70_000, dao.get("A")!!.positionMs)
    }

    @Test
    fun periodic_save_every_5s() = runTest {
        session.open(a)
        val before = dao.writes
        repeat(12) { now += 1_000; session.onTick() }
        assertEquals(2, dao.writes - before)
        session.pause()
        val afterPause = dao.writes
        repeat(12) { now += 1_000; session.onTick() }
        assertEquals(afterPause, dao.writes)
    }

    @Test
    fun pause_saves_immediately() = runTest {
        session.open(a)
        player.positionMs = 33_000
        session.pause()
        assertEquals(33_000, dao.get("A")!!.positionMs)
        assertTrue(reporter.calls.last().startsWith("timeline:A-0:paused"))
    }

    @Test
    fun file_change_saves_immediately() = runTest {
        session.open(a)
        val before = dao.writes
        player.fileIndex = 1; player.positionMs = 200
        now += 1_000
        session.onTick()
        assertEquals(before + 1, dao.writes)
        assertEquals("A-1", dao.get("A")!!.trackRatingKey)
    }

    @Test
    fun missing_track_falls_back_to_file_index() = runTest {
        dao.upsert(BookProgress("A", 1, "zmizela", 20_000, now, now))
        session.open(a)
        assertTrue(log.contains("load:A-0:1:20000:1.0"))
        dao.upsert(BookProgress("A", 9, "zmizela", 20_000, now, now))
        val fresh = BookSession(player, dao, chapters, reporter, prefs, { "uri:" + it.ratingKey }, { now }, timer)
        fresh.open(a)
        assertTrue(log.contains("load:A-0:2:0:1.0"))
    }

    @Test
    fun skip_back_crosses_file_boundary() = runTest {
        val c = book("C", 100_000, 100_000, 600_000, 100_000)
        session.open(c)
        player.fileIndex = 3; player.positionMs = 5_000
        session.skipBack()
        assertEquals("seek:2:590000", log.last { it.startsWith("seek") })
    }

    @Test
    fun skip_back_at_book_start_goes_to_zero() = runTest {
        session.open(a)
        player.positionMs = 3_000
        session.skipBack()
        assertEquals("seek:0:0", log.last { it.startsWith("seek") })
    }

    @Test
    fun skip_forward_crosses_file_boundary() = runTest {
        session.open(a)
        player.positionMs = 590_000
        session.skipForward()
        assertEquals("seek:1:20000", log.last { it.startsWith("seek") })
    }

    @Test
    fun skip_forward_past_end_of_last_file_finishes_book() = runTest {
        session.open(a)
        player.fileIndex = 2; player.positionMs = 590_000
        session.skipForward()
        assertTrue(dao.get("A")!!.finished)
        assertFalse(player.isPlaying)
    }

    @Test
    fun speed_is_per_book() = runTest {
        session.open(a)
        session.setSpeed(1.5f)
        session.open(b)
        assertTrue(log.contains("load:B-0:0:0:1.0"))
        session.open(a)
        assertEquals(1.5f, player.currentSpeed)
    }

    @Test
    fun speed_is_clamped_and_rounded() = runTest {
        session.open(a)
        session.setSpeed(3.7f); assertEquals(3.0f, player.currentSpeed)
        session.setSpeed(0.2f); assertEquals(0.5f, player.currentSpeed)
        session.setSpeed(1.26f); assertEquals(1.3f, player.currentSpeed, 0.0001f)
    }

    @Test
    fun book_end_marks_finished_and_does_not_continue() = runTest {
        session.open(a)
        val loads = log.count { it.startsWith("load") }
        session.onBookEnded()
        assertTrue(dao.get("A")!!.finished)
        assertTrue(reporter.calls.contains("scrobble:A"))
        assertEquals(loads, log.count { it.startsWith("load") })
        assertFalse(player.isPlaying)
    }

    @Test
    fun open_resets_volume_and_cancels_timer() = runTest {
        session.open(a)
        timer.start(SleepMode.Minutes(15))
        player.setVolume(0.3f)
        session.open(b)
        assertNull(timer.state.value)
        assertEquals(1f, player.currentVolume)
    }

    @Test
    fun sleep_timer_pauses_and_saves_and_restores_volume() = runTest {
        session.open(a)
        timer.start(SleepMode.Minutes(15))
        player.positionMs = 44_000
        now += 15 * 60_000L
        session.onTick()
        assertFalse(player.isPlaying)
        assertEquals(1f, player.currentVolume)
        assertEquals(44_000, dao.get("A")!!.positionMs)
    }

    @Test
    fun finished_book_reopened_from_saved_starts_over_and_unfinishes() = runTest {
        dao.upsert(BookProgress("A", 2, "A-2", 0, now, now, finished = true))
        session.open(a)
        assertTrue(log.contains("load:A-0:0:0:1.0"))
        assertFalse(dao.get("A")!!.finished)
        assertTrue(reporter.calls.contains("unscrobble:A"))
    }

    @Test
    fun set_finished_false_puts_book_back_in_progress_from_start() = runTest {
        session.setFinished(a, true)
        assertTrue(dao.get("A")!!.finished)
        session.setFinished(a, false)
        val p = dao.get("A")!!
        assertFalse(p.finished)
        assertEquals(0, p.fileIndex)
        assertEquals(0, p.positionMs)
    }

    @Test
    fun chapter_navigation() = runTest {
        session.open(a)
        player.fileIndex = 1; player.positionMs = 10_000
        session.prevChapter()
        assertEquals("seek:1:0", log.last { it.startsWith("seek") })
        player.positionMs = 1_000
        session.prevChapter()
        assertEquals("seek:0:0", log.last { it.startsWith("seek") })
        session.nextChapter()
        assertEquals("seek:1:0", log.last { it.startsWith("seek") })
    }

    @Test
    fun finished_flag_survives_later_save() = runTest {
        session.open(a)
        session.onBookEnded()
        session.save("stopped")
        assertTrue(dao.get("A")!!.finished)
    }

    @Test
    fun cancel_sleep_during_fade_restores_volume() = runTest {
        session.open(a)
        timer.start(SleepMode.Minutes(15))
        now += 15 * 60_000L - 5_000
        session.onTick()
        assertEquals(0.5f, player.currentVolume, 0.01f)
        session.cancelSleep()
        assertEquals(1f, player.currentVolume)
        assertNull(timer.state.value)
    }

    @Test
    fun play_after_long_pause_rewinds() = runTest {
        session.open(a)
        player.positionMs = 100_000
        session.pause()
        now += 4 * 60_000L
        session.play()
        assertEquals("seek:0:90000", log.last { it.startsWith("seek") })
        assertTrue(player.isPlaying)
    }

    @Test
    fun play_after_short_pause_does_not_rewind() = runTest {
        session.open(a)
        player.positionMs = 100_000
        session.pause()
        now += 60_000L
        session.play()
        assertTrue(log.none { it.startsWith("seek") })
    }

    @Test
    fun saves_while_paused_keep_last_played_time() = runTest {
        session.open(a)
        player.positionMs = 50_000
        session.pause()
        val pausedAt = now
        now += 10 * 60_000L
        session.setSpeed(1.2f)
        assertEquals(pausedAt, dao.get("A")!!.lastPlayedAt)
    }

    @Test
    fun snapshot_reads_current_position_without_suspending() = runTest {
        session.open(b)
        player.fileIndex = 1; player.positionMs = 12_345
        val snap = session.snapshot()!!
        assertEquals("B", snap.bookId)
        assertEquals(1, snap.fileIndex)
        assertEquals("B-1", snap.trackRatingKey)
        assertEquals(12_345, snap.positionMs)
    }

    @Test
    fun pause_is_not_blocked_by_slow_chapter_loading() = runTest {
        val gate = CompletableDeferred<ChapterIndex>()
        val slow = BookSession(player, dao, { gate.await() }, reporter, prefs, { "uri:" + it.ratingKey }, { now }, timer)
        val job = launch { slow.open(a) }
        advanceUntilIdle()
        player.positionMs = 7_000
        slow.pause()
        assertEquals(7_000, dao.get("A")!!.positionMs)
        gate.complete(ChapterIndex(buildChapters(a, emptyMap())))
        job.join()
        assertEquals(3, slow.state.value.chapters.chapters.size)
    }

    @Test
    fun zero_duration_track_keeps_saved_position() = runTest {
        val z = Book("Z", "Z", "A", null, listOf(track("Z/0.mp3", key = "Z-0", durationMs = 0)))
        dao.upsert(BookProgress("Z", 0, "Z-0", 33_000, now, now))
        session.open(z)
        assertTrue(log.contains("load:Z-0:0:33000:1.0"))
    }

    @Test
    fun play_after_book_ended_does_nothing() = runTest {
        session.open(a)
        session.onBookEnded()
        session.play()
        assertFalse(player.isPlaying)
        assertTrue(dao.get("A")!!.finished)
        assertTrue(session.isCurrentFinished)
    }

    private fun sessionWithServer(pos: ServerPosition?) =
        BookSession(player, dao, chapters, reporter, prefs, { "uri:" + it.ratingKey }, { now }, timer, serverCheck = { pos })

    @Test
    fun open_takes_newer_server_position() = runTest {
        dao.upsert(BookProgress("A", 0, "A-0", 10_000, now - 3_600_000, now - 3_600_000))
        val s = sessionWithServer(ServerPosition(2, "A-2", 77_000, updatedAt = now - 60_000, finished = false))
        s.open(a)
        assertTrue(log.contains("load:A-0:2:77000:1.0"))
    }

    @Test
    fun open_ignores_older_server_position() = runTest {
        dao.upsert(BookProgress("A", 1, "A-1", 10_000, now - 60_000, now - 60_000))
        val s = sessionWithServer(ServerPosition(2, "A-2", 77_000, updatedAt = now - 3_600_000, finished = false))
        s.open(a)
        assertTrue(log.contains("load:A-0:1:10000:1.0"))
    }

    @Test
    fun mark_unplayed_resets_and_hides_from_in_progress() = runTest {
        session.open(a)
        player.fileIndex = 1; player.positionMs = 50_000
        session.setUnplayed(a)
        val p = dao.get("A")!!
        assertEquals(0, p.fileIndex)
        assertEquals(0, p.positionMs)
        assertFalse(p.finished)
        assertEquals(0L, p.lastPlayedAt)
        assertTrue(reporter.calls.contains("unscrobble:A"))
        assertNull(session.current.value)
        assertFalse(player.isPlaying)
        // další uložení (tik, pauza) už knihu nevrátí
        now += 10_000; session.onTick(); session.save("x")
        assertEquals(0, dao.get("A")!!.positionMs)
    }

    @Test
    fun unplayed_book_of_other_book_does_not_touch_current() = runTest {
        session.open(a)
        player.positionMs = 9_000
        session.setUnplayed(b)
        assertEquals("A", session.current.value!!.id)
        assertTrue(player.isPlaying)
        assertEquals(0L, dao.get("B")!!.lastPlayedAt)
    }

    @Test
    fun reload_sources_keeps_position_and_uses_new_address() = runTest {
        var base = "home"
        val s = BookSession(player, dao, chapters, reporter, prefs, { "$base:" + it.ratingKey }, { now }, timer)
        s.open(a)
        player.fileIndex = 1; player.positionMs = 123_000
        base = "remote"
        s.reloadSources()
        assertEquals("load:A-0:1:123000:1.0", log.last { it.startsWith("load") })
        assertTrue(player.loadedUris.all { it.startsWith("remote:") })
        assertTrue(player.isPlaying)
    }

    @Test
    fun reload_sources_keeps_paused_state() = runTest {
        session.open(a)
        session.pause()
        session.reloadSources()
        assertFalse(player.isPlaying)
    }

    @Test
    fun open_checks_connection_first() = runTest {
        val events = mutableListOf<String>()
        val s = BookSession(
            player, dao, chapters, reporter, prefs, { events += "uri"; "u" }, { now }, timer,
            beforeOpen = { events += "check" },
        )
        s.open(a)
        assertEquals("check", events.first())
    }
}
