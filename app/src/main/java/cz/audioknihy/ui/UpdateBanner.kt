package cz.audioknihy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cz.audioknihy.R
import cz.audioknihy.update.UpdateState

/** Lišta „je k dispozici nová verze“ nad obsahem hlavní obrazovky. */
@Composable
fun UpdateBanner(state: UpdateState, onUpdate: () -> Unit, onLater: () -> Unit) {
    if (state is UpdateState.Idle) return
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.SystemUpdate, null)
                val text = when (state) {
                    is UpdateState.Available -> stringResource(R.string.update_available, state.release.version)
                    is UpdateState.Downloading -> stringResource(R.string.update_downloading, state.release.version)
                    is UpdateState.Ready -> stringResource(R.string.update_ready, state.release.version)
                    is UpdateState.Failed -> stringResource(R.string.update_failed)
                    UpdateState.Idle -> ""
                }
                Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            }
            if (state is UpdateState.Downloading) {
                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (state is UpdateState.Available) TextButton(onClick = onLater) { Text(stringResource(R.string.update_later)) }
                    Button(onClick = onUpdate) {
                        Text(stringResource(if (state is UpdateState.Ready) R.string.update_install else R.string.update_now))
                    }
                }
            }
        }
    }
}
