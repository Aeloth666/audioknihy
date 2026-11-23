package cz.audioknihy.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import cz.audioknihy.ui.theme.AppTheme
import cz.audioknihy.ui.theme.AppDesign
import cz.audioknihy.ui.theme.Silk
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.audioknihy.R
import cz.audioknihy.settings.Settings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(val settings: Settings) : ViewModel() {
    fun setSkipBack(ms: Long) = viewModelScope.launch { settings.setSkipBackMs(ms) }
    fun setSkipForward(ms: Long) = viewModelScope.launch { settings.setSkipForwardMs(ms) }
    fun setRewind(ms: Long) = viewModelScope.launch { settings.setResumeRewindMs(ms) }
    fun setSync(v: Boolean) = viewModelScope.launch { settings.setSyncPositionToServer(v) }
    fun setTheme(t: AppTheme) = viewModelScope.launch { settings.setTheme(t.name) }
    fun setDesign(d: AppDesign) = viewModelScope.launch { settings.setDesign(d.name) }
    fun signOut(done: () -> Unit) = viewModelScope.launch { settings.signOut(); done() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onSignedOut: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val back by vm.settings.skipBackMs.collectAsState(Settings.DEFAULT_SKIP_BACK)
    val fwd by vm.settings.skipForwardMs.collectAsState(Settings.DEFAULT_SKIP_FORWARD)
    val rewind by vm.settings.resumeRewindMs.collectAsState(Settings.DEFAULT_RESUME_REWIND)
    val sync by vm.settings.syncPositionToServer.collectAsState(true)
    val server by vm.settings.serverName.collectAsState(null)
    val themeName by vm.settings.theme.collectAsState(AppTheme.CLOTH.name)
    val designName by vm.settings.design.collectAsState(AppDesign.RIBBON.name)
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.settings)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
        )
    }) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DesignPicker(AppDesign.from(designName), vm::setDesign)
            ThemePicker(AppTheme.from(themeName), vm::setTheme)
            Choice(stringResource(R.string.settings_skip_back), listOf(10, 15, 30, 60), (back / 1000).toInt()) { vm.setSkipBack(it * 1000L) }
            Choice(stringResource(R.string.settings_skip_forward), listOf(15, 30, 60, 90), (fwd / 1000).toInt()) { vm.setSkipForward(it * 1000L) }
            Choice(stringResource(R.string.settings_resume_rewind), listOf(0, 5, 10, 30), (rewind / 1000).toInt()) { vm.setRewind(it * 1000L) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_sync), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Switch(checked = sync, onCheckedChange = vm::setSync)
            }
            server?.let { Text(stringResource(R.string.settings_server, it), style = MaterialTheme.typography.bodyMedium) }
            OutlinedButton(onClick = { vm.signOut(onSignedOut) }) { Text(stringResource(R.string.sign_out)) }
        }
    }
}

@Composable
private fun Choice(title: String, options: List<Int>, selected: Int, onPick: (Int) -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { s ->
                FilterChip(selected = s == selected, onClick = { onPick(s) }, label = { Text(stringResource(R.string.seconds, s)) })
            }
        }
    }
}

@Composable
private fun ThemePicker(current: AppTheme, onPick: (AppTheme) -> Unit) {
    Column {
        Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleSmall)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppTheme.entries.forEach { t ->
                val p = t.palette
                val selected = t == current
                Column(
                    Modifier.selectable(selected = selected, role = Role.RadioButton) { onPick(t) }.padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(width = 64.dp, height = 84.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(p.ground)
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) p.gold else MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(10.dp),
                            ),
                    ) {
                        Box(Modifier.padding(start = 10.dp, top = 12.dp).size(30.dp, 40.dp).clip(RoundedCornerShape(4.dp)).background(p.highest))
                        Box(Modifier.padding(start = 30.dp, top = 8.dp).size(6.dp, 30.dp).background(Silk.all[0]))
                        Box(Modifier.align(Alignment.BottomEnd).padding(8.dp).size(18.dp).clip(CircleShape).background(p.gold))
                    }
                    Text(
                        stringResource(t.label),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DesignPicker(current: AppDesign, onPick: (AppDesign) -> Unit) {
    Column {
        Text(stringResource(R.string.settings_design), style = MaterialTheme.typography.titleSmall)
        Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppDesign.entries.forEach { d ->
                val selected = d == current
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .selectable(selected = selected, role = Role.RadioButton) { onPick(d) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(d.label), style = MaterialTheme.typography.titleMedium,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        Text(stringResource(d.description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
