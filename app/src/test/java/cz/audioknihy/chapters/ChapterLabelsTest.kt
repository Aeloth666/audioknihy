package cz.audioknihy.chapters

import cz.audioknihy.library.Book
import cz.audioknihy.library.track
import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterLabelsTest {

    @Test
    fun fixes_windows_1250_mojibake() {
        assertEquals("Princův příběh", ChapterLabels.fixMojibake("Princùv pøíbìh"))
        assertEquals("Godrikův Důl", ChapterLabels.fixMojibake("Godrikùv Dùl"))
        assertEquals("O devatenáct let později", ChapterLabels.fixMojibake("O devatenáct let pozdìji"))
        assertEquals("Žluťoučký kůň", ChapterLabels.fixMojibake("luouèký kùò"))
    }

    @Test
    fun leaves_correct_text_alone() {
        assertEquals("Pán zla nabírá síly", ChapterLabels.fixMojibake("Pán zla nabírá síly"))
        assertEquals("Café crème", ChapterLabels.fixMojibake("Café crème"))
        assertEquals("Normal title", ChapterLabels.fixMojibake("Normal title"))
    }

    @Test
    fun strips_numbering_from_tag_titles() {
        assertEquals("Kap.33.- Princův příběh", ChapterLabels.cleanTitle("7-Kap.33.- Princùv pøíbìh"))
        assertEquals("Titul", ChapterLabels.cleanTitle("1-Titul"))
        assertEquals("Titul", ChapterLabels.cleanTitle("Titul"))
        assertEquals("", ChapterLabels.cleanTitle("6"))
        assertEquals("", ChapterLabels.cleanTitle("06"))
        assertEquals("Lysistrata", ChapterLabels.cleanTitle("01 Lysistrata"))
        assertEquals("Neočekávaný dýchánek, část 1", ChapterLabels.cleanTitle("Neočekávaný dýchánek, část 1"))
    }

    @Test
    fun groups_by_cd_and_numbers_each_disc_from_one() {
        val dir = "/k/HP/7 - Relikvie"
        val book = Book(
            dir, "HP7", "JKR", null,
            listOf(
                track("$dir/CD1/01.mp3", title = "1-Titul"),
                track("$dir/CD1/02.mp3", title = "2-Kap.1.- Pán zla"),
                track("$dir/CD2/01.mp3", title = "Titul"),
                track("$dir/CD2/02.mp3", title = "6"),
            ),
        )
        val chapters = buildChapters(book, emptyMap())
        val rows = ChapterLabels.rows(book, chapters)
        assertEquals(
            listOf(
                ChapterRow.Header("CD 1"),
                ChapterRow.Item(0, "1. Titul"),
                ChapterRow.Item(1, "2. Kap.1.- Pán zla"),
                ChapterRow.Header("CD 2"),
                ChapterRow.Item(2, "1. Titul"),
                ChapterRow.Item(3, "2."),
            ),
            rows,
        )
    }

    @Test
    fun book_without_cds_has_no_headers_and_keeps_embedded_titles() {
        val book = Book("/b", "B", "A", null, listOf(track("/b/1.m4a", title = "Kniha", durationMs = 1_000_000)))
        val chapters = buildChapters(book, mapOf(0 to listOf(EmbeddedChapter(0, "Chapter 001"), EmbeddedChapter(500_000, "Chapter 002"))))
        assertEquals(
            listOf(ChapterRow.Item(0, "1. Chapter 001"), ChapterRow.Item(1, "2. Chapter 002")),
            ChapterLabels.rows(book, chapters),
        )
    }
}
