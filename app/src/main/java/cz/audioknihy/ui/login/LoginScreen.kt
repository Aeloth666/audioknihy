package cz.audioknihy.ui.login

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cz.audioknihy.R
import cz.audioknihy.plex.PlexAuth
import cz.audioknihy.settings.Settings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LoginState { Idle, Waiting, Failed, Done }

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: PlexAuth,
    private val settings: Settings,
) : ViewModel() {
    private val _state = MutableStateFlow(LoginState.Idle)
    val state = _state.asStateFlow()

    fun login(context: Context) {
        _state.value = LoginState.Waiting
        viewModelScope.launch {
            val token = runCatching {
                val pin = auth.createPin()
                CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(auth.authUrl(pin)))
                auth.pollToken(pin)
            }.getOrNull()
            if (token == null) {
                _state.value = LoginState.Failed
            } else {
                settings.setUserToken(token)
                _state.value = LoginState.Done
            }
        }
    }
}

@Composable
fun LoginScreen(onDone: () -> Unit, vm: LoginViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    LaunchedEffect(state) { if (state == LoginState.Done) onDone() }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.login_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.login_explain))
        when (state) {
            LoginState.Waiting -> {
                CircularProgressIndicator()
                Text(stringResource(R.string.login_waiting))
            }
            else -> {
                if (state == LoginState.Failed) Text(stringResource(R.string.login_failed), color = MaterialTheme.colorScheme.error)
                Button(onClick = { vm.login(ctx) }) { Text(stringResource(R.string.login_button)) }
            }
        }
    }
}
