package cz.audioknihy.ui

import cz.audioknihy.library.Book
import cz.audioknihy.library.track
import cz.audioknihy.progress.BookProgress
import cz.audioknihy.ui.home.InProgressRows
import cz.audioknihy.ui.home.InProgressSort
import cz.audioknihy.ui.library.LibrarySearch
import org.junit.Assert.assertEquals
import org.junit.Test

class UiLogicTest {
    private val hour = 3_600_000L

    @Test
    fun maps_progress_to_rows() {
        val book = Book("/b", "Duna", "Herbert", null, (0 until 10).map { track("/b/$it.m4a", key = "k$it", durationMs = hour) })
        val rows = InProgressRows.build(
            listOf(BookProgress("/b", 4, "k4", 0, 1, 1)),
            listOf(book),
        )
        assertEquals(1, rows.size)
        assertEquals(6 * hour, rows[0].remainingMs)
        assertEquals(0.4f, rows[0].fraction, 0.0001f)
        assertEquals(5, rows[0].partNumber)
        assertEquals(10, rows[0].partCount)
    }

    @Test
    fun rows_skip_books_missing_from_library() {
        assertEquals(0, InProgressRows.build(listOf(BookProgress("/x", 0, "k", 0, 1, 1)), emptyList()).size)
    }

    @Test
    fun hours_minutes_split() {
        assertEquals(6 to 0, TimeFormat.hoursMinutes(6 * hour))
        assertEquals(1 to 5, TimeFormat.hoursMinutes(hour + 5 * 60_000 + 59_000))
        assertEquals("1:02:03", TimeFormat.clock(hour + 2 * 60_000 + 3_000))
        assertEquals("2:03", TimeFormat.clock(2 * 60_000 + 3_000))
    }

    @Test
    fun search_ignores_diacritics() {
        val books = listOf(
            Book("/a", "Zaklínač", "Andrzej Sapkowski", null, emptyList()),
            Book("/b", "Duna", "Frank Herbert", null, emptyList()),
        )
        assertEquals(listOf("Zaklínač"), LibrarySearch.filter(books, "sapkowski zaklinac").map { it.title })
        assertEquals(2, LibrarySearch.filter(books, "  ").size)
        assertEquals(listOf("Duna"), LibrarySearch.filter(books, "DÚNA").map { it.title })
    }

    @Test
    fun in_progress_sorting() {
        fun b(id: String, title: String, author: String, hours: Int) =
            Book(id, title, author, null, (0 until hours).map { track("$id/$it.mp3", key = "$id$it", durationMs = hour) })
        val books = listOf(b("/1", "Čarodějky", "Pratchett", 10), b("/2", "Duna", "Herbert", 10), b("/3", "Bílá", "Adams", 4))
        val progress = listOf(
            BookProgress("/2", 1, "x", 0, 300, 300), // 10 %, naposledy
            BookProgress("/1", 8, "x", 0, 200, 200), // 80 %
            BookProgress("/3", 1, "x", 0, 100, 100), // 25 %, zbývá 3 h
        )
        val rows = InProgressRows.build(progress, books)
        fun ids(o: InProgressSort) = InProgressRows.sort(rows, o).map { it.book.id }
        assertEquals(listOf("/2", "/1", "/3"), ids(InProgressSort.RECENT))
        assertEquals(listOf("/3", "/1", "/2"), ids(InProgressSort.TITLE))
        assertEquals(listOf("/3", "/2", "/1"), ids(InProgressSort.AUTHOR))
        assertEquals(listOf("/1", "/3", "/2"), ids(InProgressSort.LEAST_REMAINING))
        assertEquals(listOf("/1", "/3", "/2"), ids(InProgressSort.MOST_PROGRESS))
    }
}
