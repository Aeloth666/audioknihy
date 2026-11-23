package cz.audioknihy.chapters

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import cz.audioknihy.library.Book
import cz.audioknihy.plex.ActiveServer
import cz.audioknihy.plex.PlexSource
import cz.audioknihy.plex.PlexTrack
import cz.audioknihy.progress.CachedChapters
import cz.audioknihy.progress.LibraryDao
import java.io.IOException
import java.nio.ByteBuffer

/** Zdroj kapitol pro přehrávač (rozhraní kvůli testům). */
fun interface ChapterSource {
    suspend fun chapters(book: Book): ChapterIndex
}

/** Kapitoly knihy. Vložené kapitoly M4A/M4B čte přes HTTP range a ukládá do cache. */
class ChapterRepository(
    private val dao: LibraryDao,
    private val active: ActiveServer,
) : ChapterSource {
    private val adapter = Moshi.Builder().build()
        .adapter<List<Chapter>>(Types.newParameterizedType(List::class.java, Chapter::class.java))

    override suspend fun chapters(book: Book): ChapterIndex {
        val key = cacheKey(book)
        dao.chapters(key)?.let { cached ->
            runCatching { adapter.fromJson(cached.json) }.getOrNull()?.let { return ChapterIndex(it) }
        }
        val source = active.get()?.source
        var complete = source != null
        val embedded = mutableMapOf<Int, List<EmbeddedChapter>>()
        if (source != null) {
            book.files.forEachIndexed { i, track ->
                if (track.isMp4()) {
                    try {
                        embedded[i] = readEmbedded(source, track)
                    } catch (e: IOException) {
                        complete = false
                    }
                }
            }
        }
        val chapters = buildChapters(book, embedded)
        if (complete) dao.putChapters(CachedChapters(key, adapter.toJson(chapters)))
        return ChapterIndex(chapters)
    }

    /** Klíč cache zahrnuje stopy, aby se při změně knihy kapitoly přečetly znovu. */
    private fun cacheKey(book: Book) = book.id + "#" + book.files.joinToString(",") { it.ratingKey }.hashCode()

    private fun PlexTrack.isMp4(): Boolean =
        filePath.substringAfterLast('.').lowercase() in setOf("m4a", "m4b", "mp4")

    private suspend fun readEmbedded(source: PlexSource, track: PlexTrack): List<EmbeddedChapter> {
        val head = source.headRange(track.partKey, 0, HEAD_BYTES)
        val range = Mp4ChapterReader.moovRange(head) ?: locateMoov(source, track.partKey) ?: return emptyList()
        val size = (range.last - range.first + 1)
        if (size > MAX_MOOV) return emptyList()
        val moov = if (range.last < head.size) {
            head.copyOfRange(range.first.toInt(), range.last.toInt() + 1)
        } else {
            source.headRange(track.partKey, range.first, size.toInt())
        }
        return Mp4ChapterReader.parseChpl(moov)
    }

    /** Projde hlavičky atomů nejvýš do mdat a najde moov. */
    private suspend fun locateMoov(source: PlexSource, partKey: String): LongRange? {
        var offset = 0L
        repeat(MAX_ATOMS) {
            val h = source.headRange(partKey, offset, 16)
            if (h.size < 8) return null
            val bb = ByteBuffer.wrap(h)
            var size = bb.int.toLong() and 0xFFFFFFFFL
            val type = String(h, 4, 4, Charsets.US_ASCII)
            if (size == 1L && h.size >= 16) size = bb.getLong(8)
            if (size < 8) return null
            when (type) {
                "moov" -> return offset until offset + size
                "mdat" -> return null
            }
            offset += size
        }
        return null
    }

    companion object {
        const val HEAD_BYTES = 64 * 1024
        const val MAX_MOOV = 16L * 1024 * 1024
        const val MAX_ATOMS = 16
    }
}
