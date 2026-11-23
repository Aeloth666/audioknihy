package cz.audioknihy.progress

import cz.audioknihy.library.Book

sealed interface Resolution {
    data object KeepLocal : Resolution
    data class TakeServer(val pos: ServerPosition) : Resolution
    data object Nothing : Resolution
}

object ProgressResolver {
    /** Server musí být novější o víc než tohle, jinak věříme místní pozici (hodiny, vlastní hlášení). */
    const val SERVER_MARGIN_MS = 60_000L

    /** Pozice v poslední minutě posledního souboru = kniha je dočtená. */
    const val FINISHED_TAIL_MS = 60_000L

    fun serverPosition(book: Book): ServerPosition? {
        val files = book.files
        val viewed = files.withIndex().filter { it.value.lastViewedAtSec != null }
        if (viewed.isEmpty()) return null
        val (idx, cand) = viewed.maxWith(compareBy({ it.value.lastViewedAtSec!! }, { it.index }))
        val updatedAt = cand.lastViewedAtSec!! * 1000
        val anyOffset = files.any { (it.viewOffsetMs ?: 0) > 0 }
        if (!anyOffset && files.all { it.viewCount > 0 }) {
            return finished(book, updatedAt)
        }
        val offset = cand.viewOffsetMs ?: 0
        val nearEndOfBook = idx == files.lastIndex && offset > 0 && offset <= cand.durationMs &&
            cand.durationMs - offset <= FINISHED_TAIL_MS
        return when {
            nearEndOfBook -> finished(book, updatedAt)
            offset > 0 -> ServerPosition(idx, cand.ratingKey, offset, updatedAt, finished = false)
            cand.viewCount > 0 -> {
                val next = files.getOrNull(idx + 1) ?: return finished(book, updatedAt)
                ServerPosition(idx + 1, next.ratingKey, 0, updatedAt, finished = false)
            }
            else -> ServerPosition(idx, cand.ratingKey, 0, updatedAt, finished = false)
        }
    }

    private fun finished(book: Book, updatedAt: Long): ServerPosition {
        val last = book.files.lastIndex
        return ServerPosition(last, book.files[last].ratingKey, 0, updatedAt, finished = true)
    }

    fun resolve(local: BookProgress?, server: ServerPosition?): Resolution = when {
        local == null && server == null -> Resolution.Nothing
        local == null -> Resolution.TakeServer(server!!)
        server == null -> Resolution.KeepLocal
        server.updatedAt > local.updatedAt + SERVER_MARGIN_MS -> Resolution.TakeServer(server)
        // Místní pozice pochází ze stejného stavu serveru, jen dřív vyhodnoceného jako nedokončená.
        server.finished && !local.finished && server.updatedAt == local.updatedAt -> Resolution.TakeServer(server)
        else -> Resolution.KeepLocal
    }
}
