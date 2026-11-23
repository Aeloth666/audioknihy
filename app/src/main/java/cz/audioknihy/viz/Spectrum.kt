package cz.audioknihy.viz

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Rychlá Fourierova transformace a pásma pro vizualizace (čistý Kotlin, testovatelné). */
object Spectrum {

    /** Amplitudy n/2 frekvenčních košů (Hannovo okno, n = mocnina dvou). */
    fun magnitudes(samples: FloatArray): FloatArray {
        val n = Integer.highestOneBit(samples.size.coerceAtLeast(2))
        val re = DoubleArray(n) { i -> samples[i] * (0.5 - 0.5 * cos(2 * PI * i / (n - 1))) }
        val im = DoubleArray(n)
        fft(re, im)
        return FloatArray(n / 2) { k -> (sqrt(re[k] * re[k] + im[k] * im[k]) * 2.0 / n).toFloat() }
    }

    /** Logaritmicky rozložená pásma od [minHz] do [maxHz] (řeč leží zhruba 80 Hz – 8 kHz). */
    fun bands(mags: FloatArray, sampleRate: Int, count: Int, minHz: Double = 60.0, maxHz: Double = 9_000.0): FloatArray {
        if (mags.isEmpty()) return FloatArray(count)
        val binHz = sampleRate / 2.0 / mags.size
        val top = minOf(maxHz, sampleRate / 2.0)
        return FloatArray(count) { b ->
            val lo = minHz * (top / minHz).pow(b.toDouble() / count)
            val hi = minHz * (top / minHz).pow((b + 1).toDouble() / count)
            val from = (lo / binHz).toInt().coerceIn(0, mags.lastIndex)
            val to = max(from + 1, (hi / binHz).toInt()).coerceAtMost(mags.size)
            var sum = 0f
            for (i in from until to) sum += mags[i]
            sum / (to - from)
        }
    }

    /** Převod amplitudy na 0..1 v decibelech (rozsah [floorDb]..0 dB). */
    fun level(amplitude: Float, floorDb: Float = -60f): Float {
        if (amplitude <= 0f) return 0f
        val db = (20 * ln(amplitude.toDouble()) / ln(10.0)).toFloat()
        return ((db - floorDb) / -floorDb).coerceIn(0f, 1f)
    }

    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                re[i] = re[j].also { re[j] = re[i] }
                im[i] = im[j].also { im[j] = im[i] }
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang)
            val wi = sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0
                var ci = 0.0
                for (k in 0 until len / 2) {
                    val ur = re[i + k]
                    val ui = im[i + k]
                    val vr = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val vi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k] = ur + vr
                    im[i + k] = ui + vi
                    re[i + k + len / 2] = ur - vr
                    im[i + k + len / 2] = ui - vi
                    val ncr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }
}

/** Vyhlazení hodnot: rychlý nástup, pomalý dozvuk (jako ručička měřáku). */
class Smoother(private val attack: Float = 0.6f, private val release: Float = 0.12f) {
    private var state = FloatArray(0)

    fun next(target: FloatArray): FloatArray {
        if (state.size != target.size) state = FloatArray(target.size)
        for (i in target.indices) {
            val k = if (target[i] > state[i]) attack else release
            state[i] += (target[i] - state[i]) * k
        }
        return state.copyOf()
    }
}

/** Poslední vzorky obou kanálů pro vykreslení. */
class AudioSnapshot(val left: FloatArray, val right: FloatArray, val sampleRate: Int)

/**
 * Odbočka zvuku z přehrávače (PCM 16 bit). Drží posledních [capacity] vzorků každého kanálu.
 * Zapisuje zvukové vlákno, čte vykreslování; synchronizováno zámkem.
 */
class AudioTap(private val capacity: Int = 2048) {
    private val left = FloatArray(capacity)
    private val right = FloatArray(capacity)
    private var write = 0
    private var filled = 0
    @Volatile private var channels = 2
    @Volatile var sampleRate = 44_100
        private set

    /** Vizualizace je vidět: jen pak má smysl zvuk zpracovávat. */
    @Volatile var enabled = true

    fun onFormat(sampleRate: Int, channels: Int) {
        this.sampleRate = sampleRate
        this.channels = channels.coerceAtLeast(1)
    }

    fun onPcm16(buffer: ByteBuffer) {
        if (!enabled) return
        val b = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val ch = channels
        synchronized(this) {
            while (b.remaining() >= 2 * ch) {
                val l = b.short / 32768f
                val r = if (ch >= 2) b.short / 32768f else l
                repeat(ch - 2) { b.short }
                left[write] = l
                right[write] = r
                write = (write + 1) % capacity
                if (filled < capacity) filled++
            }
        }
    }

    fun snapshot(count: Int = capacity): AudioSnapshot = synchronized(this) {
        val n = minOf(count, filled)
        val l = FloatArray(n)
        val r = FloatArray(n)
        for (i in 0 until n) {
            val idx = (write - n + i + capacity) % capacity
            l[i] = left[idx]
            r[i] = right[idx]
        }
        AudioSnapshot(l, r, sampleRate)
    }

    fun clear() = synchronized(this) {
        filled = 0
        write = 0
    }
}

/** Automatické zesílení: dělí vrcholem posledních hodnot (pomalu klesá), ticho nezesiluje. */
class AutoGain(private val decay: Float = 0.995f, private val floor: Float = 0.15f) {
    private var peak = floor

    fun apply(values: FloatArray): FloatArray {
        val m = values.maxOrNull() ?: 0f
        peak = maxOf(floor, m, peak * decay)
        return FloatArray(values.size) { (values[it] / peak).coerceIn(0f, 1f) }
    }
}

object Stereo {
    /** Rozdíl kanálů je zanedbatelný proti jejich součtu = mono nahrávka. */
    fun isMono(left: FloatArray, right: FloatArray): Boolean {
        var side = 0.0
        var mid = 0.0
        for (i in left.indices) {
            val m = (left[i] + right[i]) / 2.0
            val s = (left[i] - right[i]) / 2.0
            mid += m * m
            side += s * s
        }
        return side <= mid * 0.01
    }
}
