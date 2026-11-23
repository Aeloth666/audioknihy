package cz.audioknihy.ui.setup

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cz.audioknihy.R
import cz.audioknihy.library.LibraryRepository
import cz.audioknihy.plex.MusicSection
import cz.audioknihy.plex.PlexServer
import cz.audioknihy.plex.ServerConnector
import cz.audioknihy.plex.ServerSession
import cz.audioknihy.settings.Settings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SetupState {
    data class Busy(val message: Int) : SetupState
    data class Servers(val list: List<PlexServer>) : SetupState
    data class Sections(val list: List<MusicSection>) : SetupState
    data class Error(val message: Int) : SetupState
    data object Done : SetupState
}

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val settings: Settings,
    private val connector: ServerConnector,
    private val server: ServerSession,
    private val library: LibraryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<SetupState>(SetupState.Busy(R.string.setup_connecting))
    val state = _state.asStateFlow()

    init { loadServers() }

    fun loadServers() {
        _state.value = SetupState.Busy(R.string.setup_connecting)
        viewModelScope.launch {
            val token = settings.plexUserToken.first() ?: return@launch
            val servers = runCatching { connector.servers(token) }.getOrNull()
            _state.value = when {
                servers == null -> SetupState.Error(R.string.setup_no_connection)
                servers.isEmpty() -> SetupState.Error(R.string.setup_no_server)
                servers.size == 1 -> { pickServer(servers[0]); return@launch }
                else -> SetupState.Servers(servers)
            }
        }
    }

    fun pickServer(s: PlexServer) {
        _state.value = SetupState.Busy(R.string.setup_connecting)
        viewModelScope.launch {
            val uri = connector.bestConnection(s)
            if (uri == null) {
                _state.value = SetupState.Error(R.string.setup_no_connection)
                return@launch
            }
            settings.setServer(s.clientId, s.name, uri, s.accessToken)
            val sections = runCatching { server.client()!!.musicSections() }.getOrNull()
            _state.value = when {
                sections == null -> SetupState.Error(R.string.setup_no_connection)
                sections.isEmpty() -> SetupState.Error(R.string.setup_no_sections)
                else -> SetupState.Sections(sections)
            }
        }
    }

    fun pickSection(section: MusicSection) {
        _state.value = SetupState.Busy(R.string.loading_library)
        viewModelScope.launch {
            settings.setSectionKey(section.key)
            _state.value = if (library.refresh().isSuccess) SetupState.Done else SetupState.Error(R.string.setup_no_connection)
        }
    }
}

@Composable
fun SetupScreen(onDone: () -> Unit, vm: SetupViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state) { if (state == SetupState.Done) onDone() }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        when (val s = state) {
            is SetupState.Busy -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Text(stringResource(s.message))
            }
            is SetupState.Error -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(s.message), color = MaterialTheme.colorScheme.error)
                Button(onClick = vm::loadServers) { Text(stringResource(R.string.try_again)) }
            }
            is SetupState.Servers -> Picker(stringResource(R.string.setup_server), s.list, { it.name }, vm::pickServer)
            is SetupState.Sections -> Picker(stringResource(R.string.setup_section), s.list, { it.title }, vm::pickSection)
            SetupState.Done -> Unit
        }
    }
}

@Composable
private fun <T> Picker(title: String, items: List<T>, label: (T) -> String, onPick: (T) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 16.dp))
    LazyColumn {
        items(items) { item ->
            ListItem(headlineContent = { Text(label(item)) }, modifier = Modifier.clickable { onPick(item) })
            HorizontalDivider()
        }
    }
}
