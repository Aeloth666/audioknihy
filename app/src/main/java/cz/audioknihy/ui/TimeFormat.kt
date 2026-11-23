package cz.audioknihy.ui

object TimeFormat {
    fun hoursMinutes(ms: Long): Pair<Int, Int> {
        val totalMin = (ms.coerceAtLeast(0) / 60_000).toInt()
        return totalMin / 60 to totalMin % 60
    }

    /** „1:02:03“ nebo „2:03“. */
    fun clock(ms: Long): String {
        val s = ms.coerceAtLeast(0) / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }
}
