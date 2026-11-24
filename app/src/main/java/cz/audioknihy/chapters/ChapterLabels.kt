package cz.audioknihy.chapters

import cz.audioknihy.library.Book
import cz.audioknihy.library.BookGrouper
import java.nio.charset.Charset

/** Řádek seznamu kapitol: nadpis disku nebo kapitola. */
sealed interface ChapterRow {
    data class Header(val title: String) : ChapterRow
    data class Item(val chapterIndex: Int, val label: String) : ChapterRow
}

object ChapterLabels {
    private val cp1250: Charset = Charset.forName("windows-1250")
    private val LEADING_NO = Regex("""^\d+\s*[-.)]?\s*""")

    /** Znaky, které v češtině nedávají smysl a vznikají čtením Windows-1250 jako Latin-1. */
    private fun suspicious(c: Char) = c in "øìùò¹¾»" || c.code in 0x80..0x9F

    /** Opraví tagy zapsané ve Windows-1250, které se přečetly jako Latin-1 („pøíbìh“ → „příběh“). */
    fun fixMojibake(s: String): String {
        if (s.none(::suspicious) || s.any { it.code > 0xFF }) return s
        return String(s.toByteArray(Charsets.ISO_8859_1), cp1250)
    }

    /** Název z tagu bez čísla na začátku („7-Kap.33…“ → „Kap.33…“); samotné číslo → prázdné. */
    fun cleanTitle(raw: String): String {
        val t = LEADING_NO.replace(fixMojibake(raw).trim(), "").trim()
        return if (t.all { it.isDigit() }) "" else t
    }

    /** Kapitoly rozdělené podle CD; uvnitř každého disku číslované od 1. */
    fun rows(book: Book, chapters: List<Chapter>): List<ChapterRow> {
        val discs = book.files.map { BookGrouper.discOf(it.filePath) }
        val hasDiscs = discs.any { it != null }
        val out = mutableListOf<ChapterRow>()
        var currentDisc: Int? = -1
        var n = 0
        chapters.forEachIndexed { i, ch ->
            val disc = discs.getOrNull(ch.fileIndex)
            if (hasDiscs && disc != currentDisc) {
                currentDisc = disc
                n = 0
                out += ChapterRow.Header(if (disc != null) "CD $disc" else "")
            }
            n++
            val title = cleanTitle(ch.title)
            out += ChapterRow.Item(i, if (title.isEmpty()) "$n." else "$n. $title")
        }
        return out
    }
}
