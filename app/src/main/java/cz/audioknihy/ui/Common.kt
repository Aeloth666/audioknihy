package cz.audioknihy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Replay
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import cz.audioknihy.R

@Composable
fun Cover(url: String?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    val placeholder: @Composable () -> Unit = {
        Box(
            modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (url == null) {
        placeholder()
    } else {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(shape),
            loading = { placeholder() },
            error = { placeholder() },
        )
    }
}

@Composable
fun remainingText(ms: Long): String {
    val (h, m) = TimeFormat.hoursMinutes(ms)
    return if (h > 0) stringResource(R.string.remaining_hm, h, m) else stringResource(R.string.remaining_m, m)
}

@Composable
fun durationText(ms: Long): String {
    val (h, m) = TimeFormat.hoursMinutes(ms)
    return stringResource(R.string.duration_hm, h, m)
}

/** Ikona skoku s počtem sekund uvnitř šipky (stejná pro zpět i vpřed). */
@Composable
fun SkipIcon(seconds: Int, forward: Boolean, description: String, size: androidx.compose.ui.unit.Dp) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        Icon(
            Icons.Filled.Replay,
            contentDescription = description,
            modifier = Modifier.fillMaxSize().graphicsLayer { if (forward) scaleX = -1f },
        )
        Text(
            seconds.toString(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = (size.value * 0.24f).sp, fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(top = (size.value * 0.12f).dp),
        )
    }
}

/** Zdroj obalů pro obrazovky (dodává MainActivity). */
val LocalCovers = androidx.compose.runtime.staticCompositionLocalOf<cz.audioknihy.covers.Covers?> { null }

/** Adresa obalu knihy: hned z Plexu/paměti, případně dohledaná na internetu nebo obrázek autora. */
@Composable
fun rememberCoverUrl(book: cz.audioknihy.library.Book, sizePx: Int = 600): String? {
    val covers = LocalCovers.current ?: return null
    val version = covers.version.collectAsState().value
    val state = androidx.compose.runtime.produceState(initialValue = covers.immediate(book, sizePx), book.id, book.coverThumb, version) {
        value = covers.resolve(book, sizePx)
    }
    return state.value
}
