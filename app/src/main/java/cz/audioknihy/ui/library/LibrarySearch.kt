package cz.audioknihy.ui.library

import cz.audioknihy.library.Book
import java.text.Normalizer

object LibrarySearch {
    private val marks = Regex("\\p{Mn}+")

    fun normalize(s: String): String =
        marks.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "").lowercase()

    /** Všechna slova dotazu se musí vyskytnout v autorovi nebo názvu (bez ohledu na diakritiku). */
    fun filter(books: List<Book>, query: String): List<Book> {
        val words = normalize(query).split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return books
        return books.filter { b ->
            val hay = normalize(b.author + " " + b.title)
            words.all { it in hay }
        }
    }
}
