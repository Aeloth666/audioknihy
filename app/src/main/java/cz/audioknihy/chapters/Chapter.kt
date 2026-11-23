package cz.audioknihy.chapters

import com.squareup.moshi.JsonClass
import cz.audioknihy.library.Book

/** Kapitola v knize: úsek [startMs, endMs) v souboru [fileIndex]. */
@JsonClass(generateAdapter = true)
data class Chapter(val title: String, val fileIndex: Int, val startMs: Long, val endMs: Long)

/** Kapitola vložená v souboru (atom chpl). */
data class EmbeddedChapter(val startMs: Long, val title: String)

/** Soubor s vloženými kapitolami = jeho kapitoly, jinak soubor = jedna kapitola. */
fun buildChapters(book: Book, embedded: Map<Int, List<EmbeddedChapter>>): List<Chapter> =
    book.files.flatMapIndexed { i, track ->
        val inner = embedded[i].orEmpty().filter { it.startMs < track.durationMs }.sortedBy { it.startMs }
        if (inner.isEmpty()) {
            listOf(Chapter(track.title, i, 0, track.durationMs))
        } else {
            inner.mapIndexed { j, c ->
                val end = inner.getOrNull(j + 1)?.startMs ?: track.durationMs
                Chapter(c.title, i, c.startMs, end)
            }
        }
    }
