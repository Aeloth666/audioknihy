package cz.audioknihy.progress

import org.junit.Assert.assertEquals
import org.junit.Test

class ResumePositionTest {
    private val now = 10_000_000L

    @Test
    fun long_pause_rewinds() {
        assertEquals(40_000, resumePositionMs(50_000, lastPlayedAt = now - 240_000, now = now, rewindMs = 10_000))
    }

    @Test
    fun short_pause_does_not_rewind() {
        assertEquals(50_000, resumePositionMs(50_000, lastPlayedAt = now - 60_000, now = now, rewindMs = 10_000))
    }

    @Test
    fun never_negative() {
        assertEquals(0, resumePositionMs(4_000, lastPlayedAt = now - 240_000, now = now, rewindMs = 10_000))
    }
}
