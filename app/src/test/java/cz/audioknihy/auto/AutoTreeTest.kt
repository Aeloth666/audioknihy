package cz.audioknihy.auto

import android.app.Application
import cz.audioknihy.library.Book
import cz.audioknihy.library.track
import cz.audioknihy.progress.BookProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AutoTreeTest {
    private fun book(id: String, author: String, title: String) =
        Book(id, title, author, null, listOf(track("$id/1.mp3", key = "$id-1")))

    private val books = listOf(
        book("/k/Sapkowski/Zaklínač", "Andrzej Sapkowski", "Zaklínač"),
        book("/k/Herbert/Duna", "Frank Herbert", "DUNA"),
        book("/k/Herbert/Spasitel", "Frank Herbert", "Spasitel Duny"),
    )
    private val progress = listOf(
        BookProgress("/k/Herbert/Spasitel", 0, "x", 0, 300, 300),
        BookProgress("/k/Sapkowski/Zaklínač", 0, "x", 0, 200, 200),
    )
    private val labels = AutoTree.Labels(inProgress = "Rozposlouchané", library = "Knihovna")

    @Test
    fun root_has_two_folders() {
        val items = AutoTree.children(AutoTree.ROOT, books, progress, labels)
        assertEquals(listOf(AutoTree.IN_PROGRESS, AutoTree.LIBRARY), items.map { it.mediaId })
        assertEquals("Rozposlouchané", items[0].mediaMetadata.title)
        assertTrue(items.all { it.mediaMetadata.isBrowsable == true && it.mediaMetadata.isPlayable == false })
    }

    @Test
    fun in_progress_lists_books_in_order_playable() {
        val items = AutoTree.children(AutoTree.IN_PROGRESS, books, progress, labels)
        assertEquals(listOf("Spasitel Duny", "Zaklínač"), items.map { it.mediaMetadata.title.toString() })
        assertTrue(items.all { it.mediaMetadata.isPlayable == true })
    }

    @Test
    fun library_groups_by_author() {
        val authors = AutoTree.children(AutoTree.LIBRARY, books, progress, labels)
        assertEquals(listOf("Andrzej Sapkowski", "Frank Herbert"), authors.map { it.mediaMetadata.title.toString() })
        assertFalse(authors[0].mediaMetadata.isPlayable!!)
        val herbert = AutoTree.children(authors[1].mediaId, books, progress, labels)
        assertEquals(listOf("DUNA", "Spasitel Duny"), herbert.map { it.mediaMetadata.title.toString() })
    }

    @Test
    fun book_item_id_round_trips() {
        val id = "/data/AudioKnihy/Autor/Žluťoučký kůň/CD 1"
        val mediaId = AutoTree.bookMediaId(id)
        assertEquals(id, AutoTree.bookIdOf(mediaId))
        assertEquals(null, AutoTree.bookIdOf("author:x"))
    }

    @Test
    fun unknown_parent_is_empty() {
        assertTrue(AutoTree.children("nesmysl", books, progress, labels).isEmpty())
    }
}
