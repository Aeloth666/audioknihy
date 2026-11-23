package cz.audioknihy.library

import com.squareup.moshi.Moshi
import cz.audioknihy.plex.ActiveServer
import cz.audioknihy.progress.BookProgress
import cz.audioknihy.progress.CachedBook
import cz.audioknihy.progress.LibraryDao
import cz.audioknihy.progress.ProgressDao
import cz.audioknihy.progress.ProgressResolver
import cz.audioknihy.progress.Resolution
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Knihovna knih: cache v Room, obnova z Plexu, import pozic ze serveru. */
class LibraryRepository(
    private val dao: LibraryDao,
    private val progress: ProgressDao,
    private val active: ActiveServer,
) {
    private val adapter = Moshi.Builder().build().adapter(Book::class.java)

    val books: Flow<List<Book>> = dao.all().map { list ->
        list.mapNotNull { runCatching { adapter.fromJson(it.json) }.getOrNull() }
            .let { BookGrouper.sortBooks(it) }
    }

    suspend fun book(id: String): Book? = dao.get(id)?.let { runCatching { adapter.fromJson(it.json) }.getOrNull() }

    /** Načte všechny stopy z Plexu. Při chybě zůstává cache i pozice beze změny. */
    suspend fun refresh(): Result<Unit> {
        val conn = active.get() ?: return Result.failure(IOException("Není vybrán server"))
        val tracks = try {
            conn.source.allTracks(conn.sectionKey)
        } catch (e: IOException) {
            return Result.failure(e)
        }
        val books = BookGrouper.group(tracks)
        dao.replaceAll(books.map { CachedBook(it.id, adapter.toJson(it)) })
        importServerProgress(books)
        return Result.success(Unit)
    }

    internal suspend fun importServerProgress(books: List<Book>) {
        for (book in books) {
            val local = progress.get(book.id)
            val resolution = ProgressResolver.resolve(local, ProgressResolver.serverPosition(book))
            if (resolution is Resolution.TakeServer) {
                val pos = resolution.pos
                progress.upsert(
                    BookProgress(
                        bookId = book.id,
                        fileIndex = pos.fileIndex,
                        trackRatingKey = pos.trackRatingKey,
                        positionMs = pos.positionMs,
                        updatedAt = pos.updatedAt,
                        lastPlayedAt = pos.updatedAt,
                        speed = local?.speed ?: 1f,
                        finished = pos.finished,
                    )
                )
            }
        }
    }
}
