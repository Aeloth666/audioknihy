package cz.audioknihy.library

import cz.audioknihy.plex.PlexTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ROOT = "/data/AudioKnihy/AudioKnihy"

fun track(
    path: String,
    album: String = "Album",
    artist: String = "Autor",
    index: Int? = null,
    durationMs: Long = 60_000,
    key: String = path,
    title: String = path.substringAfterLast('/').substringBeforeLast('.'),
    viewOffsetMs: Long? = null,
    viewCount: Int = 0,
    lastViewedAtSec: Long? = null,
) = PlexTrack(
    ratingKey = key, title = title, albumTitle = album, artist = artist, index = index,
    durationMs = durationMs, filePath = path, partKey = "/library/parts/$key/file",
    thumb = null, viewOffsetMs = viewOffsetMs, viewCount = viewCount, lastViewedAtSec = lastViewedAtSec,
)

class BookGrouperTest {

    @Test
    fun cd_folders_merge_into_one_book() {
        val dir = "$ROOT/J. K. Rowling/7 - Harry Potter a Relikvie smrti (čte Jan Hyhlik)"
        val tracks = listOf(
            track("$dir/CD2/01.mp3", album = "7 - 2 - Harry Potter a Relikvie smrti CD2", index = 1),
            track("$dir/CD1/02.mp3", album = "7 - 1 - Harry Potter a Relikvie smrti CD1", index = 2),
            track("$dir/CD3/01.mp3", album = "7 - 3 - Harry Potter a Relikvie smrti CD3", index = 1),
            track("$dir/CD1/01.mp3", album = "7 - 1 - Harry Potter a Relikvie smrti CD1", index = 1),
        )
        val books = BookGrouper.group(tracks)
        assertEquals(1, books.size)
        val book = books[0]
        assertTrue(book.id.endsWith("/7 - Harry Potter a Relikvie smrti (čte Jan Hyhlik)"))
        assertEquals("7 - Harry Potter a Relikvie smrti (čte Jan Hyhlik)", book.title)
        assertEquals(
            listOf("$dir/CD1/01.mp3", "$dir/CD1/02.mp3", "$dir/CD2/01.mp3", "$dir/CD3/01.mp3"),
            book.files.map { it.filePath },
        )
    }

    @Test
    fun cd_folder_variants() {
        val dir = "$ROOT/Autor/Kniha"
        val tracks = listOf(
            track("$dir/Disc-4/a.mp3"),
            track("$dir/CD 1/a.mp3"),
            track("$dir/Disk_3/a.mp3"),
            track("$dir/cd2/a.mp3"),
        )
        val books = BookGrouper.group(tracks)
        assertEquals(1, books.size)
        assertEquals(dir, books[0].id)
        assertEquals(
            listOf("CD 1", "cd2", "Disk_3", "Disc-4"),
            books[0].files.map { it.filePath.split('/').dropLast(1).last() },
        )
        val notCd = BookGrouper.group(listOf(track("$dir/CD-ROM extras/a.mp3")))
        assertEquals("$dir/CD-ROM extras", notCd[0].id)
    }

    @Test
    fun plain_folder_is_book_titled_by_album() {
        val dir = "$ROOT/Frank Herbert/01 Duna"
        val books = BookGrouper.group(
            listOf(
                track("$dir/02.m4a", album = "DUNA", artist = "Frank Herbert", index = 2),
                track("$dir/01.m4a", album = "DUNA", artist = "Frank Herbert", index = 1),
            )
        )
        assertEquals(1, books.size)
        assertEquals("DUNA", books[0].title)
        assertEquals("Frank Herbert", books[0].author)
        assertEquals(120_000, books[0].durationMs)
    }

    @Test
    fun mixed_albums_in_folder_use_folder_name() {
        val dir = "$ROOT/Jan Werich/Pohádky"
        val books = BookGrouper.group(
            listOf(track("$dir/a.mp3", album = "A"), track("$dir/b.mp3", album = "B"))
        )
        assertEquals("Pohádky", books[0].title)
    }

    @Test
    fun ordering_by_index_then_natural_filename() {
        val dir = "$ROOT/X/Y"
        val byIndex = BookGrouper.group(
            listOf(track("$dir/c.mp3", index = 10), track("$dir/a.mp3", index = 2), track("$dir/b.mp3", index = 1))
        )
        assertEquals(listOf(1, 2, 10), byIndex[0].files.map { it.index })
        val byName = BookGrouper.group(
            listOf(track("$dir/2.mp3"), track("$dir/10.mp3"), track("$dir/1.mp3"))
        )
        assertEquals(listOf("1.mp3", "2.mp3", "10.mp3"), byName[0].files.map { it.filePath.substringAfterLast('/') })
    }

    @Test
    fun single_file_book() {
        val books = BookGrouper.group(
            listOf(track("$ROOT/Frantisek Kotleta/Sitarane/František Kotleta - Sitarane.m4a", album = "Sitarane"))
        )
        assertEquals(1, books.size)
        assertEquals(1, books[0].files.size)
        assertEquals("Sitarane", books[0].title)
    }

    @Test
    fun books_sorted_by_author_then_title() {
        val books = BookGrouper.group(
            listOf(
                track("$ROOT/B/Kniha2/a.mp3", artist = "Bob", album = "Zeta"),
                track("$ROOT/A/Kniha1/a.mp3", artist = "Alice", album = "Beta"),
                track("$ROOT/B/Kniha3/a.mp3", artist = "Bob", album = "Alfa"),
            )
        )
        assertEquals(listOf("Beta", "Alfa", "Zeta"), books.map { it.title })
    }

    @Test
    fun cover_falls_back_to_first_file_with_thumb() {
        val a = track("$ROOT/A/K/1.mp3", index = 1).copy(thumb = null)
        val b = track("$ROOT/A/K/2.mp3", index = 2).copy(thumb = "/thumb/2")
        assertEquals("/thumb/2", BookGrouper.group(listOf(a, b))[0].coverThumb)
    }

    @Test
    fun windows_style_paths_are_grouped() {
        val books = BookGrouper.group(
            listOf(track("D:\\Knihy\\Autor\\Kniha\\CD1\\01.mp3"), track("D:\\Knihy\\Autor\\Kniha\\CD2\\01.mp3"))
        )
        assertEquals(1, books.size)
        assertEquals("D:\\Knihy\\Autor\\Kniha", books[0].id)
        assertEquals(2, books[0].files.size)
    }
}
