package cz.audioknihy.library

import android.app.Application
import cz.audioknihy.progress.BookProgress
import cz.audioknihy.testutil.FakeActiveServer
import cz.audioknihy.testutil.FakeSource
import cz.audioknihy.testutil.inMemoryDb
import kotlinx.coroutines.flow.first
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
class LibraryRepositoryTest {
    private val db = inMemoryDb()
    private val source = FakeSource(
        listOf(
            track("/k/Autor/Kniha/1.mp3", key = "t1", viewCount = 1, lastViewedAtSec = 1_000),
            track("/k/Autor/Kniha/2.mp3", key = "t2", durationMs = 600_000, viewOffsetMs = 30_000, lastViewedAtSec = 2_000),
            track("/k/Autor/Jina/1.mp3", key = "j1"),
        )
    )
    private val repo = LibraryRepository(db.libraryDao(), db.progressDao(), FakeActiveServer(source))

    @After fun close() = db.close()

    @Test
    fun import_adds_books_started_in_plexamp() = runTest {
        assertTrue(repo.refresh().isSuccess)
        val inProgress = db.progressDao().inProgress().first()
        assertEquals(listOf("/k/Autor/Kniha"), inProgress.map { it.bookId })
        assertEquals(1, inProgress[0].fileIndex)
        assertEquals(30_000, inProgress[0].positionMs)
        assertEquals(2, repo.books.first().size)
    }

    @Test
    fun import_never_overwrites_newer_local() = runTest {
        val local = BookProgress("/k/Autor/Kniha", 0, "t1", 5_000, updatedAt = 9_000_000, lastPlayedAt = 9_000_000)
        db.progressDao().upsert(local)
        repo.refresh()
        assertEquals(local, db.progressDao().get("/k/Autor/Kniha"))
    }

    @Test
    fun refresh_failure_keeps_cache_and_progress() = runTest {
        repo.refresh()
        source.failing = true
        val r = repo.refresh()
        assertTrue(r.isFailure)
        assertEquals(2, repo.books.first().size)
        assertEquals(1, db.progressDao().inProgress().first().size)
    }

    @Test
    fun book_lookup_by_id() = runTest {
        repo.refresh()
        assertEquals(2, repo.book("/k/Autor/Kniha")!!.files.size)
    }
}
