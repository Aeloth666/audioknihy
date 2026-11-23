package cz.audioknihy.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cz.audioknihy.playback.BookSession
import cz.audioknihy.playback.SessionState
import cz.audioknihy.plex.ServerSession
import cz.audioknihy.sleep.SleepMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import cz.audioknihy.settings.Settings
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val session: BookSession,
    private val server: ServerSession,
    settings: Settings,
) : ViewModel() {
    val skipBackSec: StateFlow<Int> = settings.skipBackMs.map { (it / 1000).toInt() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, (Settings.DEFAULT_SKIP_BACK / 1000).toInt())
    val skipForwardSec: StateFlow<Int> = settings.skipForwardMs.map { (it / 1000).toInt() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, (Settings.DEFAULT_SKIP_FORWARD / 1000).toInt())

    val state: StateFlow<SessionState> = session.state
    val sleep: StateFlow<SleepMode?> = session.sleepTimer.state

    fun sleepRemainingMs(): Long? = session.sleepTimer.remainingMs()
    fun artwork(thumb: String?): String? = server.imageUrl(thumb, 800)

    fun togglePlay() = viewModelScope.launch { if (state.value.isPlaying) session.pause() else session.play() }
    fun skipBack() = viewModelScope.launch { session.skipBack() }
    fun skipForward() = viewModelScope.launch { session.skipForward() }
    fun prevChapter() = viewModelScope.launch { session.prevChapter() }
    fun nextChapter() = viewModelScope.launch { session.nextChapter() }
    fun setSpeed(v: Float) = viewModelScope.launch { session.setSpeed(v) }
    fun seekInChapter(fileIndex: Int, positionMs: Long) = viewModelScope.launch { session.seekTo(fileIndex, positionMs) }
    fun startSleep(mode: SleepMode) = session.sleepTimer.start(mode)
    fun cancelSleep() = viewModelScope.launch { session.cancelSleep() }
}
