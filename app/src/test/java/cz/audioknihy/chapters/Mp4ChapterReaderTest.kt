package cz.audioknihy.chapters

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

object Mp4 {
    fun atom(type: String, body: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream()
        DataOutputStream(bos).apply {
            writeInt(8 + body.size)
            write(type.toByteArray(Charsets.US_ASCII))
            write(body)
        }
        return bos.toByteArray()
    }

    fun chpl(chapters: List<Pair<Long, String>>, countBytes: Int = 1): ByteArray {
        val bos = ByteArrayOutputStream()
        DataOutputStream(bos).apply {
            writeByte(1); write(byteArrayOf(0, 0, 0)) // verze + příznaky
            writeInt(0) // rezervováno
            if (countBytes == 1) writeByte(chapters.size) else writeInt(chapters.size)
            for ((ms, title) in chapters) {
                writeLong(ms * 10_000) // 100 ns jednotky
                val t = title.toByteArray(Charsets.UTF_8)
                writeByte(t.size); write(t)
            }
        }
        return atom("chpl", bos.toByteArray())
    }

    fun moov(vararg children: ByteArray) = atom("moov", children.fold(ByteArray(0)) { a, b -> a + b })
    fun udta(vararg children: ByteArray) = atom("udta", children.fold(ByteArray(0)) { a, b -> a + b })
}

class Mp4ChapterReaderTest {

    private val three = listOf(0L to "Chapter 001", 129_541L to "Chapter 002", 389_590L to "Chapter 003")

    @Test
    fun parses_chpl_inside_moov_udta() {
        val moov = Mp4.moov(Mp4.atom("mvhd", ByteArray(100)), Mp4.udta(Mp4.chpl(three)))
        assertEquals(
            listOf(
                EmbeddedChapter(0, "Chapter 001"),
                EmbeddedChapter(129_541, "Chapter 002"),
                EmbeddedChapter(389_590, "Chapter 003"),
            ),
            Mp4ChapterReader.parseChpl(moov),
        )
    }

    @Test
    fun no_chpl_is_empty() {
        val moov = Mp4.moov(Mp4.atom("mvhd", ByteArray(100)), Mp4.udta(Mp4.atom("meta", ByteArray(20))))
        assertTrue(Mp4ChapterReader.parseChpl(moov).isEmpty())
    }

    @Test
    fun corrupted_length_is_empty_without_exception() {
        val good = Mp4.moov(Mp4.udta(Mp4.chpl(three)))
        val broken = good.copyOf(good.size - 7)
        assertTrue(Mp4ChapterReader.parseChpl(broken).isEmpty())
        assertTrue(Mp4ChapterReader.parseChpl(ByteArray(3)).isEmpty())
    }

    @Test
    fun moov_range_when_moov_follows_ftyp() {
        val ftyp = Mp4.atom("ftyp", ByteArray(16)) // 24 B
        val moov = Mp4.moov(Mp4.atom("mvhd", ByteArray(100)))
        val head = (ftyp + moov + Mp4.atom("mdat", ByteArray(10))).copyOf(64)
        assertEquals(24L until 24L + moov.size, Mp4ChapterReader.moovRange(head))
    }

    @Test
    fun moov_range_null_when_mdat_first() {
        val head = Mp4.atom("ftyp", ByteArray(16)) + Mp4.atom("mdat", ByteArray(40))
        assertNull(Mp4ChapterReader.moovRange(head))
    }
}
