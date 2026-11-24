package cz.audioknihy.viz

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import cz.audioknihy.R
import cz.audioknihy.ui.theme.LocalPalette
import cz.audioknihy.ui.theme.Silk
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Režimy vizualizace v přehrávači. */
enum class VizMode(val label: Int) {
    COVER(R.string.viz_cover),
    WAVE(R.string.viz_wave),
    STEREO(R.string.viz_stereo),
    SPECTRUM(R.string.viz_spectrum),
    AURA(R.string.viz_aura),
    SILK(R.string.viz_silk);

    companion object {
        fun from(name: String?) = entries.firstOrNull { it.name == name } ?: COVER
    }
}

/**
 * Vizualizace zvuku, který právě hraje. Kreslí se každý snímek obrazovky ze vzorků v [tap].
 * [color] je barva stužky knihy, [coverUrl] obal pro režim Aura.
 */
@Composable
fun Visualizer(
    mode: VizMode, tap: AudioTap, color: Color, coverUrl: String?, playing: Boolean,
    modifier: Modifier = Modifier,
    heard: () -> Pair<Int, Long> = { 0 to -1L },
) {
    // Synchronizace: zvuk ve výstupu (a v Bluetooth) je napřed; měříme o kolik a kreslíme slyšený úsek.
    val sync = remember { object { var flush = -1; var file = -1; var lagMs = -1.0 } }
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(mode, playing) {
        tap.enabled = true
        while (true) withFrameNanos { frame = it }
    }
    val gold = LocalPalette.current.gold
    val dim = LocalPalette.current.thread
    val bandSmooth = remember(mode) { Smoother(attack = 0.55f, release = 0.10f) }
    val gain = remember(mode) { AutoGain(decay = 0.997f, floor = 0.18f) }
    val peakSmooth = remember(mode) { Smoother(attack = 1f, release = 0.025f) }
    val waveHistory = remember(mode) { ArrayDeque<FloatArray>() }
    val stereoHistory = remember(mode) { ArrayDeque<Pair<FloatArray, FloatArray>>() }

    Box(modifier, contentAlignment = Alignment.Center) {
        if (mode == VizMode.AURA) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().padding(64.dp).clip(CircleShape),
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            val t = frame / 1_000_000_000.0 // sekundy, pro pomalé pohyby
            val (fileNow, posNow) = heard()
            if (tap.flushCount != sync.flush) {
                sync.flush = tap.flushCount; sync.file = fileNow // zpoždění výstupu se nemění, ponecháme odhad
            }
            if (posNow >= 0 && tap.baseMs >= 0 && fileNow == sync.file) {
                val ahead = Latency.aheadMs(tap.baseMs, tap.framesSinceBase, tap.sampleRate, posNow).toDouble()
                sync.lagMs = if (sync.lagMs < 0) ahead else sync.lagMs * 0.92 + ahead * 0.08
            }
            val delayFrames = (sync.lagMs.coerceAtLeast(0.0) * tap.sampleRate / 1000).toInt()
            val snap = tap.snapshot(1024, delayFrames)
            val quiet = !playing || snap.left.isEmpty()
            when (mode) {
                VizMode.WAVE -> drawWave(snap, quiet, color, gold, waveHistory)
                VizMode.STEREO -> drawStereo(snap, quiet, color, dim, stereoHistory)
                VizMode.SPECTRUM -> {
                    val b = gain.apply(levels(snap, 32, quiet))
                    drawSpectrum(bandSmooth.next(b), peakSmooth.next(b), color, gold)
                }
                VizMode.AURA -> drawAura(bandSmooth.next(levels(snap, 48, quiet)), color, gold, t)
                VizMode.SILK -> drawSilk(bandSmooth.next(levels(snap, 10, quiet)), t)
                VizMode.COVER -> Unit
            }
        }
    }
}

private fun levels(snap: AudioSnapshot, count: Int, quiet: Boolean): FloatArray {
    if (quiet || snap.left.size < 256) return FloatArray(count)
    val mono = FloatArray(snap.left.size) { (snap.left[it] + snap.right[it]) / 2f }
    val bands = Spectrum.bands(Spectrum.magnitudes(mono), snap.sampleRate, count)
    // vyrovnání: řeč má většinu energie v hloubkách, výšky se zesílí úměrně pásmu (~ +19 dB nahoře)
    return FloatArray(count) { Spectrum.level(bands[it] * 6f * (1f + it * 8f / count), floorDb = -62f) }
}

/** Vlny: plynulý průběh se dvěma doznívajícími ozvěnami a zrcadlovou výplní. */
private fun DrawScope.drawWave(snap: AudioSnapshot, quiet: Boolean, color: Color, gold: Color, history: ArrayDeque<FloatArray>) {
    val points = 160
    val src = snap.left
    val cur = FloatArray(points) { i ->
        if (quiet || src.isEmpty()) 0f else {
            val from = i * src.size / points
            val to = ((i + 1) * src.size / points).coerceAtLeast(from + 1)
            var s = 0f
            for (k in from until to) s += (src[k] + snap.right[k]) / 2f
            s / (to - from)
        }
    }
    history.addFirst(cur)
    while (history.size > 4) history.removeLast()
    val mid = size.height / 2
    val amp = size.height * 0.42f
    history.forEachIndexed { age, wave ->
        val gain = 3.2f
        val path = Path()
        wave.forEachIndexed { i, v ->
            val x = size.width * i / (points - 1)
            val y = mid - (v * gain).coerceIn(-1f, 1f) * amp
            if (i == 0) path.moveTo(x, y) else {
                val px = size.width * (i - 1) / (points - 1)
                val py = mid - (wave[i - 1] * gain).coerceIn(-1f, 1f) * amp
                path.quadraticTo(px, py, (px + x) / 2, (py + y) / 2)
            }
        }
        val alpha = 1f - age * 0.26f
        if (age == 0) {
            val fill = Path().apply {
                addPath(path)
                lineTo(size.width, mid); lineTo(0f, mid); close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.35f), Color.Transparent), startY = 0f, endY = mid * 2))
        }
        drawPath(
            path,
            Brush.horizontalGradient(listOf(color.copy(alpha = alpha), gold.copy(alpha = alpha), color.copy(alpha = alpha))),
            style = Stroke(width = (if (age == 0) 3.5f else 2f).dp.toPx(), cap = StrokeCap.Round),
        )
    }
    drawLine(color.copy(alpha = 0.15f), Offset(0f, mid), Offset(size.width, mid), strokeWidth = 1.dp.toPx())
}

/** Stereoskop (goniometr): levý/pravý kanál otočené o 45°, stopa doznívá jako na luminoforu. */
private fun DrawScope.drawStereo(
    snap: AudioSnapshot, quiet: Boolean, color: Color, grid: Color,
    history: ArrayDeque<Pair<FloatArray, FloatArray>>,
) {
    val c = Offset(size.width / 2, size.height / 2)
    val r = min(size.width, size.height) * 0.46f
    // mřížka: kruh, kříž a úhlopříčky L/R
    drawCircle(grid, r, c, style = Stroke(1.dp.toPx()))
    drawCircle(grid.copy(alpha = 0.5f), r * 0.5f, c, style = Stroke(1.dp.toPx()))
    drawLine(grid, Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1.dp.toPx())
    drawLine(grid, Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1.dp.toPx())
    if (!quiet) {
        // Mono (typické u audioknih): fázový obrazec signálu proti sobě se zpožděním,
        // jinak klasický goniometr L/R otočený o 45°.
        val mono = Stereo.isMono(snap.left, snap.right)
        val lag = 18
        val n = if (mono) (snap.left.size - lag).coerceAtLeast(0) else snap.left.size
        val xs = FloatArray(n)
        val ys = FloatArray(n)
        var peak = 0.05f
        for (i in 0 until n) {
            if (mono) {
                val a = snap.left[i]
                val b = snap.left[i + lag]
                xs[i] = (a - b) / sqrt(2f); ys[i] = (a + b) / sqrt(2f)
            } else {
                xs[i] = (snap.left[i] - snap.right[i]) / sqrt(2f)
                ys[i] = (snap.left[i] + snap.right[i]) / sqrt(2f)
            }
            peak = maxOf(peak, abs(xs[i]), abs(ys[i]))
        }
        val g = 0.9f / peak // automatické zesílení: tichá řeč je taky vidět
        for (i in 0 until n) { xs[i] *= g; ys[i] *= g }
        history.addFirst(xs to ys)
    } else history.addFirst(FloatArray(0) to FloatArray(0))
    while (history.size > 7) history.removeLast()
    history.forEachIndexed { age, (xs, ys) ->
        if (xs.isEmpty()) return@forEachIndexed
        val path = Path()
        for (i in xs.indices) {
            val p = Offset(c.x + xs[i] * r, c.y - ys[i] * r)
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        val a = (1f - age / 7f).let { it * it }
        drawPath(path, color.copy(alpha = 0.9f * a), style = Stroke(width = (2.2f - age * 0.2f).dp.toPx(), cap = StrokeCap.Round))
    }
}

/** Spektrum: zaoblené sloupce, padající vrcholy a odraz. */
private fun DrawScope.drawSpectrum(bands: FloatArray, peaks: FloatArray, color: Color, gold: Color) {
    val n = bands.size
    val gap = 4.dp.toPx()
    val w = (size.width - gap * (n - 1)) / n
    val base = size.height * 0.72f
    val maxH = base * 0.95f
    val brush = Brush.verticalGradient(listOf(gold, color), startY = base - maxH, endY = base)
    for (i in 0 until n) {
        val x = i * (w + gap)
        val h = (bands[i] * maxH).coerceAtLeast(2.dp.toPx())
        drawRoundRect(brush, Offset(x, base - h), Size(w, h), CornerRadius(w / 2, w / 2))
        // odraz
        drawRoundRect(color.copy(alpha = 0.18f), Offset(x, base + gap), Size(w, h * 0.3f), CornerRadius(w / 2, w / 2))
        // padající vrchol
        val py = base - peaks[i] * maxH - 6.dp.toPx()
        drawRoundRect(gold, Offset(x, py), Size(w, 3.dp.toPx()), CornerRadius(2f, 2f))
    }
}

/** Aura: prstenec spektra kolem kulatého obalu, pomalu se otáčí; zrcadlený, aby byl souměrný. */
private fun DrawScope.drawAura(bands: FloatArray, color: Color, gold: Color, t: Double) {
    val c = Offset(size.width / 2, size.height / 2)
    val inner = min(size.width, size.height) / 2 - 60.dp.toPx()
    val maxLen = 52.dp.toPx()
    val n = bands.size
    val energy = bands.average().toFloat()
    // záře za obalem podle hlasitosti
    drawCircle(
        Brush.radialGradient(listOf(color.copy(alpha = 0.25f + energy * 0.5f), Color.Transparent), center = c, radius = inner + maxLen * 1.2f),
        radius = inner + maxLen * 1.2f, center = c,
    )
    rotate(degrees = (t * 6 % 360).toFloat(), pivot = c) {
        val total = n * 2
        for (i in 0 until total) {
            val v = bands[if (i < n) i else total - 1 - i]
            val a = 2 * PI * i / total
            val len = 3.dp.toPx() + v * maxLen
            val start = Offset(c.x + cos(a).toFloat() * (inner + 4.dp.toPx()), c.y + sin(a).toFloat() * (inner + 4.dp.toPx()))
            val end = Offset(c.x + cos(a).toFloat() * (inner + 4.dp.toPx() + len), c.y + sin(a).toFloat() * (inner + 4.dp.toPx() + len))
            drawLine(
                Brush.linearGradient(listOf(color, gold), start, end),
                start, end, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round,
            )
        }
    }
}

/** Hedvábí: barevné stužky vlní se podle pásem hlasu; v klidu jen jemně dýchají. */
private fun DrawScope.drawSilk(bands: FloatArray, t: Double) {
    val ribbons = 5
    val mid = size.height / 2
    for (r in 0 until ribbons) {
        val energy = (bands[r * 2] + bands[r * 2 + 1]) / 2f
        val amp = size.height * (0.04f + energy * 0.32f)
        val color = Silk.all[(r * 2) % Silk.all.size]
        val phase = t * (0.6 + r * 0.17) + r
        val freq = 1.2 + r * 0.35
        val thickness = (10f + energy * 22f).dp.toPx()
        val top = Path()
        val steps = 64
        for (s in 0..steps) {
            val x = size.width * s / steps
            val k = s.toDouble() / steps
            val y = mid + (r - 2) * 10.dp.toPx() + (amp * sin(2 * PI * freq * k + phase) * sin(PI * k)).toFloat()
            if (s == 0) top.moveTo(x, y) else top.lineTo(x, y)
        }
        drawPath(
            top,
            Brush.horizontalGradient(listOf(color.copy(alpha = 0f), color.copy(alpha = 0.85f), color.copy(alpha = 0f))),
            style = Stroke(width = thickness, cap = StrokeCap.Round),
        )
        // lesk hedvábí
        drawPath(top, Color.White.copy(alpha = 0.10f + energy * 0.2f), style = Stroke(width = thickness * 0.18f, cap = StrokeCap.Round))
    }
}
