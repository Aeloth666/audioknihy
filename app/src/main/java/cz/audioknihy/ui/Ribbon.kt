package cz.audioknihy.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cz.audioknihy.ui.theme.LocalPalette

/**
 * Obal knihy se záložkou-stužkou: stužka vychází zpod horní hrany knihy (jako ze stránek),
 * přeloží se přes hranu a visí dolů po obalu. Délka = poslechnutá část knihy.
 */
@Composable
fun CoverWithRibbon(
    url: String?,
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    description: String? = null,
    /** Šířka obalu; výška se dopočítá z poměru stran obrázku (nejvýš [maxHeight]). */
    width: Dp = 88.dp,
    maxHeight: Dp = Dp.Infinity,
    onClick: (() -> Unit)? = null,
) {
    val f by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "coverRibbon",
    )
    val painter = coil.compose.rememberAsyncImagePainter(url)
    val state = painter.state
    val aspect = (state as? coil.compose.AsyncImagePainter.State.Success)?.painter?.intrinsicSize
        ?.takeIf { it.width > 0f && it.height > 0f }?.let { it.width / it.height }?.coerceIn(0.5f, 1.6f) ?: 1f
    androidx.compose.foundation.layout.Box(modifier) {
        var w = width
        var h = w / aspect
        if (h > maxHeight) {
            h = maxHeight
            w = h * aspect
        }
    androidx.compose.foundation.layout.Box(
        Modifier.size(w, h)
            .semantics { if (description != null) contentDescription = description }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        // Obrázek se vykresluje vždy (jinak by se nezačal načítat); zástupce leží nad ním do načtení.
        androidx.compose.foundation.Image(
            painter = painter,
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            modifier = Modifier.fillMaxSize().clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)),
        )
        if (url == null || state !is coil.compose.AsyncImagePainter.State.Success) {
            Cover(null, Modifier.fillMaxSize())
        }
        if (f > 0f) {
            Canvas(Modifier.matchParentSizeCompat()) {
                val w = (size.width * 0.12f).coerceIn(6.dp.toPx(), 20.dp.toPx())
                val x = size.width * 0.78f - w / 2f
                val tuck = (w * 0.55f).coerceAtLeast(3.dp.toPx())
                val notch = w * 0.7f
                val len = (size.height * f).coerceAtLeast(notch + 4.dp.toPx())
                fun ribbon(dx: Float, dy: Float) = Path().apply {
                    moveTo(x + dx, dy)
                    lineTo(x + w + dx, dy)
                    lineTo(x + w + dx, len + dy)
                    lineTo(x + w / 2f + dx, len - notch + dy)
                    lineTo(x + dx, len + dy)
                    close()
                }
                // část stužky ještě mezi stránkami: vyčnívá nad horní hranu, ve stínu
                drawRect(
                    color = Color.Black.copy(alpha = 0.35f).compositeOver(color),
                    topLeft = Offset(x, -tuck),
                    size = androidx.compose.ui.geometry.Size(w, tuck),
                )
                // stín stužky na obalu
                drawPath(ribbon(1.5.dp.toPx(), 1.dp.toPx()), Color.Black.copy(alpha = 0.45f))
                drawPath(ribbon(0f, 0f), color)
                // ohyb přes horní hranu
                drawRect(Color.Black.copy(alpha = 0.22f), Offset(x, 0f), androidx.compose.ui.geometry.Size(w, 1.5.dp.toPx()))
                // lesk hedvábí
                drawLine(
                    Color.White.copy(alpha = 0.22f),
                    Offset(x + 1.dp.toPx(), 2.dp.toPx()),
                    Offset(x + 1.dp.toPx(), len - notch * 0.6f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        }
    }
    }
}

/** Obal přes celou obrazovku; klepnutím se zavře. */
@Composable
fun CoverZoomDialog(url: String?, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f)).clickable(onClick = onDismiss),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            coil.compose.AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(16.dp),
            )
        }
    }
}

@Composable
private fun Modifier.matchParentSizeCompat(): Modifier = this.then(Modifier.fillMaxSize())

/**
 * Záložka-stužka: svislý pruh ve barvě knihy, jehož délka je poslechnutá část.
 * Konec má vlaštovčí zástřih. Nit za ní ukazuje celou délku knihy.
 */
@Composable
fun Ribbon(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    width: Dp = 10.dp,
    description: String? = null,
) {
    val f by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "ribbon",
    )
    val thread = LocalPalette.current.thread
    Canvas(
        modifier
            .width(width)
            .semantics { if (description != null) contentDescription = description },
    ) {
        val w = size.width
        val cx = w / 2f
        // nit: celá délka knihy
        drawLine(thread, Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 1.dp.toPx())
        if (f <= 0f) return@Canvas
        val notch = w * 0.7f
        val len = (size.height * f).coerceAtLeast(notch + 2.dp.toPx())
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(w, 0f)
            lineTo(w, len)
            lineTo(cx, len - notch)
            lineTo(0f, len)
            close()
        }
        drawPath(path, color)
        // jemný lesk hedvábí: světlejší levý okraj
        drawLine(Color.White.copy(alpha = 0.18f), Offset(1.dp.toPx(), 0f), Offset(1.dp.toPx(), len - notch * 0.6f), strokeWidth = 1.dp.toPx())
    }
}
