package cz.audioknihy.viz

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

class SpectrumTest {

    @Test
    fun fft_finds_sine_peak_bin() {
        val n = 1024
        val sampleRate = 44_100
        val freq = sampleRate * 64.0 / n // přesně bin 64
        val x = FloatArray(n) { sin(2 * PI * freq * it / sampleRate).toFloat() }
        val mags = Spectrum.magnitudes(x)
        assertEquals(n / 2, mags.size)
        val peak = mags.indices.maxBy { mags[it] }
        assertEquals(64, peak)
    }

    @Test
    fun silence_has_zero_spectrum() {
        val mags = Spectrum.magnitudes(FloatArray(512))
        assertTrue(mags.all { it == 0f })
    }

    @Test
    fun bands_are_log_spaced_and_cover_range() {
        val mags = FloatArray(512) { 1f }
        val bands = Spectrum.bands(mags, sampleRate = 44_100, count = 24)
        assertEquals(24, bands.size)
        assertTrue(bands.all { it > 0f })
    }

    @Test
    fun pcm16_stereo_is_split_into_channels() {
        val buf = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(Short.MAX_VALUE).putShort(0).putShort((-16384).toShort()).putShort(16384)
        buf.flip()
        val tap = AudioTap(capacity = 4)
        tap.onFormat(sampleRate = 44_100, channels = 2)
        tap.onPcm16(buf)
        val snap = tap.snapshot(2)
        assertArrayEquals(floatArrayOf(1f, -0.5f), snap.left, 0.001f)
        assertArrayEquals(floatArrayOf(0f, 0.5f), snap.right, 0.001f)
    }

    @Test
    fun mono_is_copied_to_both_channels() {
        val buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        buf.putShort(16384).putShort((-16384).toShort())
        buf.flip()
        val tap = AudioTap(capacity = 4)
        tap.onFormat(sampleRate = 22_050, channels = 1)
        tap.onPcm16(buf)
        val snap = tap.snapshot(2)
        assertArrayEquals(floatArrayOf(0.5f, -0.5f), snap.left, 0.001f)
        assertArrayEquals(snap.left, snap.right, 0.0001f)
        assertEquals(22_050, snap.sampleRate)
    }

    @Test
    fun ring_buffer_keeps_latest_samples() {
        val tap = AudioTap(capacity = 3)
        tap.onFormat(44_100, 1)
        val buf = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
        listOf(1, 2, 3, 4, 5).forEach { buf.putShort((it * 1000).toShort()) }
        buf.flip()
        tap.onPcm16(buf)
        val snap = tap.snapshot(3)
        assertArrayEquals(floatArrayOf(3000f, 4000f, 5000f).map { it / 32768f }.toFloatArray(), snap.left, 0.0001f)
    }

    @Test
    fun smoothing_rises_fast_and_falls_slowly() {
        val s = Smoother(attack = 1f, release = 0.1f)
        assertEquals(1f, s.next(floatArrayOf(1f))[0], 0.001f)
        assertEquals(0.9f, s.next(floatArrayOf(0f))[0], 0.001f)
    }

    @Test
    fun auto_gain_lifts_quiet_signal_to_full_scale() {
        val g = AutoGain(decay = 0.99f, floor = 0.05f)
        val out = g.apply(floatArrayOf(0.1f, 0.2f))
        assertEquals(1f, out[1], 0.001f)
        assertEquals(0.5f, out[0], 0.001f)
    }

    @Test
    fun auto_gain_does_not_amplify_silence_noise() {
        val g = AutoGain(decay = 0.99f, floor = 0.2f)
        val out = g.apply(floatArrayOf(0.02f))
        assertEquals(0.1f, out[0], 0.001f)
    }

    @Test
    fun mono_detection() {
        val l = FloatArray(100) { sin(it / 5.0).toFloat() }
        assertTrue(Stereo.isMono(l, l.copyOf()))
        val r = FloatArray(100) { -l[it] }
        assertEquals(false, Stereo.isMono(l, r))
    }

    private fun mono(tap: AudioTap, values: List<Int>) {
        val buf = ByteBuffer.allocate(values.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        values.forEach { buf.putShort(it.toShort()) }
        buf.flip()
        tap.onPcm16(buf)
    }

    @Test
    fun snapshot_with_delay_returns_older_samples() {
        val tap = AudioTap(capacity = 10)
        tap.onFormat(1000, 1)
        mono(tap, (1..8).map { it * 1000 })
        val snap = tap.snapshot(count = 3, delayFrames = 2)
        assertArrayEquals(floatArrayOf(4000f, 5000f, 6000f).map { it / 32768f }.toFloatArray(), snap.left, 0.0001f)
    }

    @Test
    fun frames_since_flush_are_counted_and_reset() {
        val tap = AudioTap(capacity = 10)
        tap.expectDiscontinuity(positionMs = 30_000)
        tap.onFormat(1000, 1)
        mono(tap, listOf(1, 2, 3, 4))
        assertEquals(4L, tap.framesSinceBase)
        assertEquals(30_000L, tap.baseMs)
        tap.onFormat(1000, 1)
        assertEquals(0L, tap.framesSinceBase)
    }

    @Test
    fun latency_is_written_time_minus_heard_position() {
        // od pozice 30 s zapsáno 1,5 s zvuku, slyšet je 31,2 s → zvuk je 300 ms napřed
        assertEquals(300L, Latency.aheadMs(baseMs = 30_000, framesSinceBase = 1_500, sampleRate = 1000, heardMs = 31_200))
        assertEquals(0L, Latency.aheadMs(baseMs = 30_000, framesSinceBase = 100, sampleRate = 1000, heardMs = 31_200))
    }
}
