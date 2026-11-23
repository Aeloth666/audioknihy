package cz.audioknihy.chapters

import java.nio.BufferUnderflowException
import java.nio.ByteBuffer

/** Čte Nero kapitoly (atom moov/udta/chpl) z MP4/M4A/M4B. */
object Mp4ChapterReader {

    private val CONTAINERS = setOf("moov", "udta")

    /** Rozsah bajtů atomu moov, pokud leží před mdat v [head] (začátek souboru). */
    fun moovRange(head: ByteArray): LongRange? {
        var offset = 0L
        while (offset + 8 <= head.size) {
            val bb = ByteBuffer.wrap(head, offset.toInt(), head.size - offset.toInt())
            var size = bb.int.toLong() and 0xFFFFFFFFL
            val type = String(head, offset.toInt() + 4, 4, Charsets.US_ASCII)
            if (size == 1L) {
                if (offset + 16 > head.size) return null
                size = bb.getLong(bb.position() + 4)
            }
            if (size < 8) return null
            when (type) {
                "moov" -> return offset until offset + size
                "mdat" -> return null
            }
            offset += size
        }
        return null
    }

    fun parseChpl(moov: ByteArray): List<EmbeddedChapter> = try {
        findChpl(moov, 0, moov.size)?.let { (start, end) -> parseChplBody(moov, start, end) }.orEmpty()
    } catch (e: RuntimeException) {
        emptyList()
    }

    /** Vrátí (začátek, konec) těla atomu chpl. */
    private fun findChpl(data: ByteArray, from: Int, to: Int): Pair<Int, Int>? {
        var offset = from
        while (offset + 8 <= to) {
            val size = ByteBuffer.wrap(data, offset, 4).int
            val type = String(data, offset + 4, 4, Charsets.US_ASCII)
            if (size < 8 || offset + size > to) return null
            if (type == "chpl") return (offset + 8) to (offset + size)
            if (type in CONTAINERS) findChpl(data, offset + 8, offset + size)?.let { return it }
            offset += size
        }
        return null
    }

    private fun parseChplBody(data: ByteArray, start: Int, end: Int): List<EmbeddedChapter> {
        val version = data[start].toInt()
        val afterHeader = start + 4 + if (version == 1) 4 else 0
        return tryParse(data, afterHeader, end, countBytes = 1)
            ?: tryParse(data, afterHeader, end, countBytes = 4)
            ?: emptyList()
    }

    private fun tryParse(data: ByteArray, from: Int, end: Int, countBytes: Int): List<EmbeddedChapter>? = try {
        val bb = ByteBuffer.wrap(data, from, end - from)
        val count = if (countBytes == 1) bb.get().toInt() and 0xFF else bb.int
        if (count < 0 || count > 10_000) null else {
            val list = List(count) {
                val ms = bb.long / 10_000
                val len = bb.get().toInt() and 0xFF
                val bytes = ByteArray(len).also { b -> bb.get(b) }
                EmbeddedChapter(ms, String(bytes, Charsets.UTF_8).trim())
            }
            if (bb.position() == end) list else null
        }
    } catch (e: BufferUnderflowException) {
        null
    }
}
