package cz.audioknihy.library

import cz.audioknihy.plex.PlexTrack
import java.text.Collator
import java.util.Locale

/** Seskupí stopy z Plexu do knih podle složek (podsložky CD/Disk se spojí). */
object BookGrouper {

    private val CD_REGEX = Regex("^(cd|disk|disc)[ _-]*(\\d+)$", RegexOption.IGNORE_CASE)
    private val collator: Collator = Collator.getInstance(Locale("cs", "CZ"))

    private class Placed(val track: PlexTrack, val disc: Int, val fileName: String)

    fun group(tracks: List<PlexTrack>): List<Book> {
        val byBook = LinkedHashMap<String, MutableList<Placed>>()
        for (t in tracks) {
            val sep = if (t.filePath.contains('/')) '/' else '\\'
            val dir = t.filePath.substringBeforeLast(sep, "")
            val fileName = t.filePath.substringAfterLast(sep)
            val dirName = dir.substringAfterLast(sep)
            val cd = CD_REGEX.matchEntire(dirName.trim())
            val (bookDir, disc) = if (cd != null) {
                dir.substringBeforeLast(sep, "") to cd.groupValues[2].toInt()
            } else {
                dir to 0
            }
            byBook.getOrPut(bookDir) { mutableListOf() }.add(Placed(t, disc, fileName))
        }
        return sortBooks(byBook.map { (dir, placed) -> toBook(dir, placed) })
    }

    fun sortBooks(books: List<Book>): List<Book> = books.sortedWith { a, b ->
        val c = collator.compare(a.author, b.author)
        if (c != 0) c else collator.compare(a.title, b.title)
    }

    private fun toBook(dir: String, placed: List<Placed>): Book {
        val sorted = placed.sortedWith(
            compareBy<Placed> { it.disc }
                .thenBy(nullsLast()) { it.track.index }
                .thenComparator { a, b -> naturalCompare(a.fileName, b.fileName) }
        )
        val first = sorted.first().track
        val sep = if (dir.contains('/')) '/' else '\\'
        val folderName = dir.substringAfterLast(sep)
        val albums = placed.map { it.track.albumTitle }.distinct()
        val mergedCds = placed.any { it.disc != 0 }
        val title = if (!mergedCds && albums.size == 1 && albums[0].isNotBlank()) albums[0] else folderName
        return Book(
            id = dir,
            title = title,
            author = first.artist,
            coverThumb = sorted.firstNotNullOfOrNull { it.track.thumb },
            files = sorted.map { it.track },
            authorThumb = sorted.firstNotNullOfOrNull { it.track.authorThumb },
        )
    }

    /** Přirozené řazení: "2" < "10". */
    fun naturalCompare(a: String, b: String): Int {
        val ta = tokenize(a)
        val tb = tokenize(b)
        for (i in 0 until minOf(ta.size, tb.size)) {
            val x = ta[i]
            val y = tb[i]
            val xn = x.firstOrNull()?.isDigit() == true
            val yn = y.firstOrNull()?.isDigit() == true
            val c = if (xn && yn) {
                x.toBigInteger().compareTo(y.toBigInteger()).takeIf { it != 0 } ?: x.length.compareTo(y.length)
            } else {
                collator.compare(x, y)
            }
            if (c != 0) return c
        }
        return ta.size.compareTo(tb.size)
    }

    private fun tokenize(s: String): List<String> = Regex("\\d+|\\D+").findAll(s).map { it.value }.toList()
}
