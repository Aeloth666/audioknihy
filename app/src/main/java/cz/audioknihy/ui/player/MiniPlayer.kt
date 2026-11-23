package cz.audioknihy.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledIconButton
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import cz.audioknihy.ui.CoverWithRibbon
import cz.audioknihy.ui.rememberCoverUrl
import cz.audioknihy.ui.Ribbon
import cz.audioknihy.ui.theme.Silk
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cz.audioknihy.R
import cz.audioknihy.ui.Cover
import cz.audioknihy.ui.SkipIcon

@Composable
fun MiniPlayer(onOpen: () -> Unit, vm: PlayerViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val book = s.book ?: return
    val fraction = if (book.durationMs > 0) s.bookPositionMs.toFloat() / book.durationMs else 0f
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().clickable(onClick = onOpen)
                .padding(horizontal = 12.dp, vertical = 8.dp).height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverWithRibbon(rememberCoverUrl(book, 200), fraction, Silk.forBook(book.id), Modifier.size(52.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(book.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                Text(s.chapter?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            val back by vm.skipBackSec.collectAsStateWithLifecycle()
            IconButton(onClick = { vm.skipBack() }, Modifier.size(52.dp)) {
                SkipIcon(back, forward = false, description = stringResource(R.string.skip_back_n, back), size = 30.dp)
            }
            FilledIconButton(onClick = { vm.togglePlay() }, Modifier.size(52.dp)) {
                if (s.isPlaying) Icon(Icons.Filled.Pause, stringResource(R.string.pause), Modifier.size(30.dp))
                else Icon(Icons.Filled.PlayArrow, stringResource(R.string.play), Modifier.size(30.dp))
            }
        }
    }
}
