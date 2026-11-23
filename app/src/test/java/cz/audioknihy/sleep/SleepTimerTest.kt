package cz.audioknihy.sleep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerTest {
    private var now = 1_000_000L
    private val timer = SleepTimer { now }
    private val noChapter = Long.MAX_VALUE

    private fun at(min: Int, sec: Int, start: Long) { now = start + min * 60_000L + sec * 1_000L }

    @Test
    fun minutes_mode_fades_last_10_seconds_then_pauses() {
        val t0 = now
        timer.start(SleepMode.Minutes(15))
        at(14, 49, t0); assertEquals(1f, timer.tick(noChapter).volume, 0.001f)
        at(14, 55, t0); assertEquals(0.5f, timer.tick(noChapter).volume, 0.001f)
        at(15, 0, t0)
        val end = timer.tick(noChapter)
        assertTrue(end.shouldPause)
        assertNull(timer.state.value)
    }

    @Test
    fun after_firing_ticks_are_neutral() {
        val t0 = now
        timer.start(SleepMode.Minutes(15))
        at(15, 0, t0); timer.tick(noChapter)
        at(15, 1, t0)
        val tick = timer.tick(noChapter)
        assertFalse(tick.shouldPause)
        assertEquals(1f, tick.volume, 0.001f)
        assertNull(tick.remainingMs)
    }

    @Test
    fun end_of_chapter_mode_uses_chapter_remaining() {
        timer.start(SleepMode.EndOfChapter)
        assertEquals(1f, timer.tick(60_000).volume, 0.001f)
        assertEquals(0.5f, timer.tick(5_000).volume, 0.001f)
        assertEquals(5_000L, timer.tick(5_000).remainingMs)
        assertTrue(timer.tick(0).shouldPause)
    }

    @Test
    fun end_of_chapter_detects_crossing_into_next_chapter_between_ticks() {
        timer.start(SleepMode.EndOfChapter)
        timer.tick(5_000)
        timer.tick(800)
        assertTrue(timer.tick(600_000).shouldPause)
    }

    @Test
    fun shake_during_end_of_chapter_fade_extends_by_one_chapter() {
        timer.start(SleepMode.EndOfChapter)
        timer.tick(5_000)
        assertTrue(timer.onShake())
        assertEquals(1f, timer.tick(800).volume, 0.001f)
        assertFalse(timer.tick(600_000).shouldPause)
        assertEquals(0.5f, timer.tick(5_000).volume, 0.001f)
        assertTrue(timer.tick(0).shouldPause)
    }

    @Test
    fun shake_during_fade_extends_by_original_length() {
        val t0 = now
        timer.start(SleepMode.Minutes(15))
        at(14, 55, t0)
        assertTrue(timer.onShake())
        val tick = timer.tick(noChapter)
        assertEquals(15 * 60_000L, tick.remainingMs)
        assertEquals(1f, tick.volume, 0.001f)
    }

    @Test
    fun shake_outside_fade_while_running_does_nothing() {
        val t0 = now
        timer.start(SleepMode.Minutes(15))
        at(5, 0, t0)
        assertFalse(timer.onShake())
        assertEquals(10 * 60_000L, timer.tick(noChapter).remainingMs)
    }

    @Test
    fun shake_within_grace_after_firing_restarts() {
        val t0 = now
        timer.start(SleepMode.Minutes(15))
        at(15, 0, t0); timer.tick(noChapter)
        at(15, 30, t0)
        assertTrue(timer.onShake())
        assertEquals(SleepMode.Minutes(15), timer.state.value)
    }

    @Test
    fun shake_after_grace_is_ignored() {
        val t0 = now
        timer.start(SleepMode.Minutes(15))
        at(15, 0, t0); timer.tick(noChapter)
        at(16, 30, t0)
        assertFalse(timer.onShake())
        assertNull(timer.state.value)
    }

    @Test
    fun shake_after_end_of_chapter_rearms_end_of_chapter() {
        timer.start(SleepMode.EndOfChapter)
        timer.tick(0)
        assertTrue(timer.onShake())
        assertEquals(SleepMode.EndOfChapter, timer.state.value)
    }

    @Test
    fun remaining_for_minutes_mode() {
        val t0 = now
        timer.start(SleepMode.Minutes(30))
        at(10, 0, t0)
        assertEquals(20 * 60_000L, timer.remainingMs())
        timer.start(SleepMode.EndOfChapter)
        assertNull(timer.remainingMs())
    }

    @Test
    fun cancel_resets() {
        timer.start(SleepMode.Minutes(15))
        timer.cancel()
        assertNull(timer.state.value)
        assertEquals(1f, timer.tick(0).volume, 0.001f)
        assertFalse(timer.tick(0).shouldPause)
        assertFalse(timer.onShake())
    }
}
