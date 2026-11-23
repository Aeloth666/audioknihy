package cz.audioknihy.progress

import cz.audioknihy.library.Book
import cz.audioknihy.library.track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressResolverTest {

    private fun book(vararg tracks: cz.audioknihy.plex.PlexTrack) =
        Book(id = "/b", title = "B", author = "A", coverThumb = null, files = tracks.toList())

    private val local = BookProgress(
        bookId = "/b", fileIndex = 1, trackRatingKey = "t2", positionMs = 10_000,
        updatedAt = 1_000_000_000L, lastPlayedAt = 1_000_000_000L,
    )

    private fun server(updatedAt: Long) =
        ServerPosition(fileIndex = 2, trackRatingKey = "t3", positionMs = 5_000, updatedAt = updatedAt, finished = false)

    @Test
    fun server_position_from_latest_viewed_track_with_offset() {
        val b = book(
            track("/b/1.mp3", key = "t1", viewCount = 1, lastViewedAtSec = 100),
            track("/b/2.mp3", key = "t2", viewCount = 1, lastViewedAtSec = 200),
            track("/b/3.mp3", key = "t3", viewOffsetMs = 120_000, lastViewedAtSec = 300),
        )
        assertEquals(
            ServerPosition(fileIndex = 2, trackRatingKey = "t3", positionMs = 120_000, updatedAt = 300_000, finished = false),
            ProgressResolver.serverPosition(b),
        )
    }

    @Test
    fun server_position_after_fully_played_track() {
        val b = book(
            track("/b/1.mp3", key = "t1", viewCount = 1, lastViewedAtSec = 100),
            track("/b/2.mp3", key = "t2", viewCount = 1, lastViewedAtSec = 200),
            track("/b/3.mp3", key = "t3"),
        )
        assertEquals(
            ServerPosition(fileIndex = 2, trackRatingKey = "t3", positionMs = 0, updatedAt = 200_000, finished = false),
            ProgressResolver.serverPosition(b),
        )
    }

    @Test
    fun all_tracks_played_is_finished() {
        val b = book(
            track("/b/1.mp3", key = "t1", viewCount = 1, lastViewedAtSec = 100),
            track("/b/2.mp3", key = "t2", viewCount = 2, lastViewedAtSec = 200),
        )
        assertTrue(ProgressResolver.serverPosition(b)!!.finished)
    }

    @Test
    fun offset_in_last_minute_of_last_file_is_finished() {
        val b = book(
            track("/b/1.mp3", key = "t1", durationMs = 600_000),
            track("/b/2.mp3", key = "t2", durationMs = 600_000, viewOffsetMs = 570_000, lastViewedAtSec = 300),
        )
        assertTrue(ProgressResolver.serverPosition(b)!!.finished)
    }

    @Test
    fun offset_near_end_of_middle_file_is_not_finished() {
        val b = book(
            track("/b/1.mp3", key = "t1", durationMs = 600_000, viewOffsetMs = 590_000, lastViewedAtSec = 300),
            track("/b/2.mp3", key = "t2", durationMs = 600_000),
        )
        assertEquals(false, ProgressResolver.serverPosition(b)!!.finished)
    }

    @Test
    fun untouched_book_has_no_server_position() {
        assertNull(ProgressResolver.serverPosition(book(track("/b/1.mp3"), track("/b/2.mp3"))))
    }

    @Test
    fun no_local_takes_server() {
        val s = server(5)
        assertEquals(Resolution.TakeServer(s), ProgressResolver.resolve(null, s))
    }

    @Test
    fun no_server_keeps_local() {
        assertEquals(Resolution.KeepLocal, ProgressResolver.resolve(local, null))
    }

    @Test
    fun neither_is_nothing() {
        assertEquals(Resolution.Nothing, ProgressResolver.resolve(null, null))
    }

    @Test
    fun server_within_margin_keeps_local() {
        assertEquals(Resolution.KeepLocal, ProgressResolver.resolve(local, server(local.updatedAt + 59_000)))
    }

    @Test
    fun server_older_keeps_local() {
        assertEquals(Resolution.KeepLocal, ProgressResolver.resolve(local, server(local.updatedAt - 3_600_000)))
    }

    @Test
    fun server_newer_beyond_margin_takes_server() {
        val s = server(local.updatedAt + 61_000)
        assertEquals(Resolution.TakeServer(s), ProgressResolver.resolve(local, s))
    }

    @Test
    fun tie_on_last_viewed_prefers_later_file() {
        val b = book(
            track("/b/1.mp3", key = "t1", durationMs = 600_000, viewOffsetMs = 590_000, lastViewedAtSec = 500),
            track("/b/2.mp3", key = "t2", durationMs = 600_000, viewOffsetMs = 4_000, lastViewedAtSec = 500),
            track("/b/3.mp3", key = "t3", durationMs = 600_000),
        )
        assertEquals(1, ProgressResolver.serverPosition(b)!!.fileIndex)
    }

    @Test
    fun same_snapshot_marked_finished_on_server_takes_server() {
        val imported = local.copy(updatedAt = 500_000, lastPlayedAt = 500_000)
        val s = ServerPosition(1, "t2", 0, updatedAt = 500_000, finished = true)
        assertEquals(Resolution.TakeServer(s), ProgressResolver.resolve(imported, s))
    }
}
