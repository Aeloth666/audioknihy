package cz.audioknihy.ui.home

import cz.audioknihy.library.Book
import cz.audioknihy.progress.BookProgress

data class InProgressRow(
    val book: Book,
    val progress: BookProgress,
    val fraction: Float,
    val remainingMs: Long,
    val partNumber: Int,
    val partCount: Int,
)

enum class InProgressSort { RECENT, TITLE, AUTHOR, LEAST_REMAINING, MOST_PROGRESS }

object InProgressRows {
    private val collator = java.text.Collator.getInstance(java.util.Locale("cs", "CZ"))

    fun sort(rows: List<InProgressRow>, order: InProgressSort): List<InProgressRow> = when (order) {
        InProgressSort.RECENT -> rows.sortedByDescending { it.progress.lastPlayedAt }
        InProgressSort.TITLE -> rows.sortedWith { a, b -> collator.compare(a.book.title, b.book.title) }
        InProgressSort.AUTHOR -> rows.sortedWith { a, b ->
            collator.compare(a.book.author, b.book.author).takeIf { it != 0 } ?: collator.compare(a.book.title, b.book.title)
        }
        InProgressSort.LEAST_REMAINING -> rows.sortedBy { it.remainingMs }
        InProgressSort.MOST_PROGRESS -> rows.sortedByDescending { it.fraction }
    }

    /** [progress] je už seřazené (nejnověji poslouchané první). */
    fun build(progress: List<BookProgress>, books: List<Book>): List<InProgressRow> {
        val byId = books.associateBy { it.id }
        return progress.mapNotNull { p ->
            val book = byId[p.bookId] ?: return@mapNotNull null
            val fi = p.fileIndex.coerceIn(0, (book.files.size - 1).coerceAtLeast(0))
            val done = book.files.take(fi).sumOf { it.durationMs } + p.positionMs
            val total = book.durationMs
            InProgressRow(
                book = book,
                progress = p,
                fraction = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f,
                remainingMs = (total - done).coerceAtLeast(0),
                partNumber = fi + 1,
                partCount = book.files.size,
            )
        }
    }
}
