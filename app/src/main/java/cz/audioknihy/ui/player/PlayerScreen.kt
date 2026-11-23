package cz.audioknihy.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.SliderDefaults
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import cz.audioknihy.ui.CoverWithRibbon
import cz.audioknihy.ui.rememberCoverUrl
import cz.audioknihy.ui.Ribbon
import cz.audioknihy.ui.theme.Silk
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.audioknihy.R
import cz.audioknihy.sleep.SleepMode
import cz.audioknihy.ui.Cover
import cz.audioknihy.ui.SkipIcon
import cz.audioknihy.ui.TimeFormat
import cz.audioknihy.ui.remainingText
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(onBack: () -> Unit, vm: PlayerViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val sleep by vm.sleep.collectAsStateWithLifecycle()
    val back by vm.skipBackSec.collectAsStateWithLifecycle()
    val fwd by vm.skipForwardSec.collectAsStateWithLifecycle()
    val book = s.book
    var speedDialog by remember { mutableStateOf(false) }
    var sleepDialog by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(book?.author.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            },
        )
    }) { pad ->
        if (book == null) return@Scaffold
        val chapter = s.chapter
        val chStart = chapter?.startMs ?: 0
        val chEnd = chapter?.endMs ?: book.files.getOrNull(s.fileIndex)?.durationMs ?: 0
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val silk = Silk.forBook(book.id)
            val bookFraction = if (book.durationMs > 0) s.bookPositionMs.toFloat() / book.durationMs else 0f
            CoverWithRibbon(
                url = rememberCoverUrl(book, 800),
                fraction = bookFraction,
                color = silk,
                modifier = Modifier.padding(top = 12.dp).fillMaxWidth(0.82f).aspectRatio(1f),
                description = stringResource(R.string.progress_percent, (bookFraction * 100).toInt()),
            )
            Text(book.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            if (chapter != null) {
                Text(
                    chapter.title + "  ·  " + stringResource(R.string.chapter_of, s.chapterIndex + 1, s.chapters.chapters.size),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }

            // Posuvník v rámci kapitoly
            var dragging by remember { mutableStateOf(false) }
            var dragValue by remember { mutableFloatStateOf(0f) }
            val span = (chEnd - chStart).coerceAtLeast(1)
            val current = ((s.positionMs - chStart).toFloat() / span).coerceIn(0f, 1f)
            Slider(
                colors = SliderDefaults.colors(
                    thumbColor = Silk.forBook(book.id),
                    activeTrackColor = Silk.forBook(book.id),
                    inactiveTrackColor = MaterialTheme.colorScheme.outline,
                ),
                value = if (dragging) dragValue else current,
                onValueChange = { dragging = true; dragValue = it },
                onValueChangeFinished = {
                    dragging = false
                    vm.seekInChapter(chapter?.fileIndex ?: s.fileIndex, chStart + (dragValue * span).toLong())
                },
            )
            Row(Modifier.fillMaxWidth()) {
                Text(TimeFormat.clock(s.positionMs - chStart), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.weight(1f))
                Text("-" + TimeFormat.clock(chEnd - s.positionMs), style = MaterialTheme.typography.labelMedium)
            }
            Text(
                remainingText(book.durationMs - s.bookPositionMs),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = { vm.prevChapter() }, Modifier.size(52.dp)) {
                    Icon(Icons.Filled.SkipPrevious, stringResource(R.string.prev_chapter), Modifier.size(30.dp))
                }
                IconButton(onClick = { vm.skipBack() }, Modifier.size(64.dp)) {
                    SkipIcon(back, forward = false, description = stringResource(R.string.skip_back_n, back), size = 42.dp)
                }
                FilledIconButton(onClick = { vm.togglePlay() }, Modifier.size(88.dp)) {
                    if (s.isPlaying) Icon(Icons.Filled.Pause, stringResource(R.string.pause), Modifier.size(48.dp))
                    else Icon(Icons.Filled.PlayArrow, stringResource(R.string.play), Modifier.size(48.dp))
                }
                IconButton(onClick = { vm.skipForward() }, Modifier.size(64.dp)) {
                    SkipIcon(fwd, forward = true, description = stringResource(R.string.skip_forward_n, fwd), size = 42.dp)
                }
                IconButton(onClick = { vm.nextChapter() }, Modifier.size(52.dp)) {
                    Icon(Icons.Filled.SkipNext, stringResource(R.string.next_chapter), Modifier.size(30.dp))
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(onClick = { speedDialog = true }, modifier = Modifier.heightIn(min = 52.dp)) {
                    Icon(Icons.Filled.Speed, null)
                    Text(" " + stringResource(R.string.speed_value, formatSpeed(s.speed)))
                }
                FilledTonalButton(
                    onClick = { sleepDialog = true },
                    modifier = Modifier.heightIn(min = 52.dp),
                    colors = if (sleep != null) ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) else ButtonDefaults.filledTonalButtonColors(),
                ) {
                    Icon(Icons.Filled.Bedtime, null)
                    Text(" " + sleepLabel(sleep, vm))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (speedDialog) SpeedDialog(s.speed, onPick = { vm.setSpeed(it) }, onDismiss = { speedDialog = false })
    if (sleepDialog) SleepDialog(
        active = sleep != null,
        onPick = { vm.startSleep(it); sleepDialog = false },
        onCancel = { vm.cancelSleep(); sleepDialog = false },
        onDismiss = { sleepDialog = false },
    )
}

private fun formatSpeed(v: Float) = String.format(Locale("cs", "CZ"), "%.1f", v)

@Composable
private fun sleepLabel(mode: SleepMode?, vm: PlayerViewModel): String {
    var remaining by remember { mutableLongStateOf(0L) }
    LaunchedEffect(mode) {
        while (mode is SleepMode.Minutes) {
            remaining = vm.sleepRemainingMs() ?: 0
            delay(1_000)
        }
    }
    return when (mode) {
        null -> stringResource(R.string.sleep_timer)
        SleepMode.EndOfChapter -> stringResource(R.string.sleep_eoc_active)
        is SleepMode.Minutes -> stringResource(R.string.sleep_remaining, TimeFormat.clock(remaining))
    }
}

@Composable
private fun SpeedDialog(current: Float, onPick: (Float) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableFloatStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.speed)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.speed_value, formatSpeed(value)), style = MaterialTheme.typography.headlineMedium)
                Slider(
                    value = value,
                    onValueChange = { value = (Math.round(it * 10) / 10f) },
                    onValueChangeFinished = { onPick(value) },
                    valueRange = 0.5f..3.0f,
                    steps = 24,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(0.8f, 1.0f, 1.2f, 1.5f, 2.0f).forEach { v ->
                        TextButton(onClick = { value = v; onPick(v) }) { Text(formatSpeed(v)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun SleepDialog(active: Boolean, onPick: (SleepMode) -> Unit, onCancel: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sleep_timer)) },
        text = {
            Column {
                listOf(15, 30, 45, 60).forEach { m ->
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.sleep_minutes, m)) },
                        modifier = Modifier.clickable { onPick(SleepMode.Minutes(m)) },
                    )
                }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.sleep_end_of_chapter)) },
                    modifier = Modifier.clickable { onPick(SleepMode.EndOfChapter) },
                )
                if (active) ListItem(
                    headlineContent = { Text(stringResource(R.string.sleep_cancel), color = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable { onCancel() },
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
