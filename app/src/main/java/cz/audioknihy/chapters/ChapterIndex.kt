package cz.audioknihy.chapters

class ChapterIndex(val chapters: List<Chapter>) {

    fun indexAt(fileIndex: Int, posMs: Long): Int {
        if (chapters.isEmpty()) return 0
        val inFile = chapters.withIndex().filter { it.value.fileIndex == fileIndex }
        if (inFile.isEmpty()) return chapters.indexOfLast { it.fileIndex < fileIndex }.coerceAtLeast(0)
        return (inFile.lastOrNull { it.value.startMs <= posMs } ?: inFile.first()).index
    }

    fun current(fileIndex: Int, posMs: Long): Chapter? = chapters.getOrNull(indexAt(fileIndex, posMs))

    /** Víc než 3 s od začátku kapitoly → její začátek, jinak předchozí kapitola. */
    fun previousTarget(fileIndex: Int, posMs: Long): Chapter {
        val i = indexAt(fileIndex, posMs)
        val ch = chapters[i]
        return if (ch.fileIndex == fileIndex && posMs - ch.startMs > RESTART_THRESHOLD_MS) ch
        else chapters[(i - 1).coerceAtLeast(0)]
    }

    fun next(fileIndex: Int, posMs: Long): Chapter? = chapters.getOrNull(indexAt(fileIndex, posMs) + 1)

    fun remainingMs(fileIndex: Int, posMs: Long): Long {
        val ch = current(fileIndex, posMs) ?: return 0
        return (ch.endMs - posMs).coerceAtLeast(0)
    }

    companion object {
        const val RESTART_THRESHOLD_MS = 3_000L
    }
}
