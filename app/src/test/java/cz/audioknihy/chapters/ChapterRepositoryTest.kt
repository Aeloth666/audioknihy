package cz.audioknihy.chapters

import android.app.Application
import cz.audioknihy.library.Book
import cz.audioknihy.library.track
import cz.audioknihy.testutil.FakeActiveServer
import cz.audioknihy.testutil.FakeSource
import cz.audioknihy.testutil.inMemoryDb
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ChapterRepositoryTest {
    private val db = inMemoryDb()
    private val source = FakeSource()
    private val repo = ChapterRepository(db.libraryDao(), FakeActiveServer(source))

    private val m4a = track("/b/kniha.m4a", key = "m", durationMs = 1_000_000, title = "Kniha")
    private val mp3 = track("/b/dalsi.mp3", key = "x", durationMs = 50_000, title = "Další")
    private val book = Book("/b", "B", "A", null, listOf(m4a, mp3))

    init {
        val moov = Mp4.moov(Mp4.atom("mvhd", ByteArray(100)), Mp4.udta(Mp4.chpl(listOf(0L to "Úvod", 300_000L to "Dva"))))
        // moov za hranicí prvních 64 KiB → musí se dočíst druhým požadavkem
        val ftyp = Mp4.atom("ftyp", ByteArray(16))
        val free = Mp4.atom("free", ByteArray(70_000))
        source.heads = mapOf(m4a.partKey to (ftyp + free + moov))
    }

    @After fun close() = db.close()

    @Test
    fun reads_embedded_chapters_of_m4a_and_file_chapter_of_mp3() = runTest {
        val idx = repo.chapters(book)
        assertEquals(listOf("Úvod", "Dva", "Další"), idx.chapters.map { it.title })
    }

    @Test
    fun network_failure_gives_file_chapters_and_is_not_cached() = runTest {
        source.failing = true
        assertEquals(listOf("Kniha", "Další"), repo.chapters(book).chapters.map { it.title })
        source.failing = false
        assertEquals(listOf("Úvod", "Dva", "Další"), repo.chapters(book).chapters.map { it.title })
    }

    @Test
    fun cached_result_used_without_network() = runTest {
        repo.chapters(book)
        source.failing = true
        assertEquals(3, repo.chapters(book).chapters.size)
    }
}
