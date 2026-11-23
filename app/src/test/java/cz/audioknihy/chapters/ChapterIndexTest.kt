package cz.audioknihy.chapters

import cz.audioknihy.library.Book
import cz.audioknihy.library.track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterIndexTest {

    private val book = Book(
        id = "/b", title = "B", author = "A", coverThumb = null,
        files = listOf(
            track("/b/1.m4a", durationMs = 600_000, title = "Díl 1"),
            track("/b/2.m4a", durationMs = 300_000, title = "Díl 2"),
        ),
    )
    private val chapters = buildChapters(
        book,
        mapOf(0 to listOf(EmbeddedChapter(0, "K1"), EmbeddedChapter(100_000, "K2"), EmbeddedChapter(400_000, "K3"))),
    )
    private val index = ChapterIndex(chapters)

    @Test
    fun builds_embedded_and_file_chapters() {
        assertEquals(
            listOf(
                Chapter("K1", 0, 0, 100_000),
                Chapter("K2", 0, 100_000, 400_000),
                Chapter("K3", 0, 400_000, 600_000),
                Chapter("Díl 2", 1, 0, 300_000),
            ),
            chapters,
        )
    }

    @Test
    fun index_at_position() {
        assertEquals(1, index.indexAt(0, 150_000))
        assertEquals(3, index.indexAt(1, 10))
    }

    @Test
    fun previous_target_goes_to_previous_when_near_start() {
        assertEquals(chapters[0], index.previousTarget(0, 102_000))
    }

    @Test
    fun previous_target_restarts_current_chapter_when_far() {
        assertEquals(chapters[1], index.previousTarget(0, 110_000))
    }

    @Test
    fun previous_target_of_first_chapter_is_first() {
        assertEquals(chapters[0], index.previousTarget(0, 1_000))
    }

    @Test
    fun next_chapter_and_none_after_last() {
        assertEquals(chapters[3], index.next(0, 450_000))
        assertNull(index.next(1, 10_000))
    }

    @Test
    fun remaining_in_chapter() {
        assertEquals(250_000, index.remainingMs(0, 150_000))
        assertEquals(100_000, index.remainingMs(1, 200_000))
    }
}
