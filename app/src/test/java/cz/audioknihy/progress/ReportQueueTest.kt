package cz.audioknihy.progress

import android.app.Application
import cz.audioknihy.library.Book
import cz.audioknihy.library.track
import cz.audioknihy.testutil.FakeActiveServer
import cz.audioknihy.testutil.FakeSource
import cz.audioknihy.testutil.inMemoryDb
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReportQueueTest {
    private val db = inMemoryDb()
    private val source = FakeSource()
    private var scheduled = 0
    private var now = 1L
    private val queue = ReportQueue(db.reportDao(), FakeActiveServer(source), schedule = { scheduled++ }, clock = { now })

    @After fun close() = db.close()

    @Test
    fun failed_report_is_persisted_and_retried() = runTest {
        source.failing = true
        queue.timeline("t1", "paused", 5_000, 60_000)
        assertEquals(1, db.reportDao().all().size)
        assertEquals(1, scheduled)
        source.failing = false
        assertTrue(queue.flush())
        assertEquals(0, db.reportDao().all().size)
        assertEquals(listOf("timeline:t1:paused:5000"), source.calls)
    }

    @Test
    fun only_latest_timeline_per_track_is_kept() = runTest {
        source.failing = true
        queue.timeline("t1", "playing", 1_000, 60_000)
        queue.timeline("t1", "paused", 2_000, 60_000)
        assertEquals(1, db.reportDao().all().size)
        assertEquals(2_000, db.reportDao().all()[0].timeMs)
    }

    @Test
    fun scrobble_book_reports_every_track_and_queues_rest_on_failure() = runTest {
        val book = Book("/b", "B", "A", null, listOf(track("/b/1.mp3", key = "t1"), track("/b/2.mp3", key = "t2")))
        queue.scrobbleBook(book)
        assertEquals(listOf("scrobble:t1", "scrobble:t2"), source.calls)
        source.failing = true
        queue.unscrobbleBook(book)
        assertEquals(2, db.reportDao().all().size)
    }

    @Test
    fun flush_stops_on_failure_and_keeps_latest() = runTest {
        source.failing = true
        now = 1; queue.timeline("t1", "paused", 1, 2)
        now = 2; queue.timeline("t2", "paused", 1, 2)
        assertEquals(false, queue.flush())
        assertEquals(listOf("t2"), db.reportDao().all().map { it.ratingKey })
    }

    @Test
    fun live_success_clears_queued_timelines() = runTest {
        source.failing = true
        queue.timeline("t1", "paused", 1_000, 60_000)
        source.failing = false
        queue.timeline("t2", "playing", 5_000, 60_000)
        assertEquals(0, db.reportDao().all().size)
    }

    @Test
    fun flush_sends_only_latest_timeline() = runTest {
        source.failing = true
        now = 1; queue.timeline("t1", "paused", 590_000, 600_000)
        now = 2; queue.timeline("t2", "paused", 4_000, 600_000)
        source.failing = false
        assertTrue(queue.flush())
        assertEquals(listOf("timeline:t2:paused:4000"), source.calls)
        assertEquals(0, db.reportDao().all().size)
    }
}
