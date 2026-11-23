package cz.audioknihy.progress

import android.app.Application
import cz.audioknihy.testutil.inMemoryDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ProgressDaoTest {
    private val db = inMemoryDb()
    private val dao = db.progressDao()

    @After fun close() = db.close()

    private fun p(id: String, lastPlayed: Long, finished: Boolean = false, pos: Long = 0) =
        BookProgress(id, 0, "k", pos, lastPlayed, lastPlayed, finished = finished)

    @Test
    fun in_progress_ordered_and_excludes_finished() = runTest {
        dao.upsert(p("a", 100))
        dao.upsert(p("b", 300))
        dao.upsert(p("c", 200, finished = true))
        assertEquals(listOf("b", "a"), dao.inProgress().first().map { it.bookId })
    }

    @Test
    fun upsert_one_book_does_not_touch_other() = runTest {
        dao.upsert(p("b", 1, pos = 777))
        dao.upsert(p("a", 1, pos = 1))
        dao.upsert(p("a", 2, pos = 2))
        assertEquals(777, dao.get("b")!!.positionMs)
        assertEquals(2, dao.get("a")!!.positionMs)
    }

    @Test
    fun unplayed_rows_are_not_in_progress() = runTest {
        dao.upsert(p("a", 100))
        dao.upsert(p("b", 0))
        assertEquals(listOf("a"), dao.inProgress().first().map { it.bookId })
    }
}
