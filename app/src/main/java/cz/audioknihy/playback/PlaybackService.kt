package cz.audioknihy.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import cz.audioknihy.R
import cz.audioknihy.auto.AutoTree
import cz.audioknihy.library.LibraryRepository
import cz.audioknihy.plex.ServerSession
import cz.audioknihy.progress.ProgressDao
import cz.audioknihy.sleep.ShakeDetector
import cz.audioknihy.ui.MainActivity
import cz.audioknihy.ui.library.LibrarySearch
import cz.audioknihy.di.AppScope
import cz.audioknihy.settings.Settings
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Služba přehrávání: notifikace, zamčená obrazovka, sluchátka a Android Auto. */
@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {

    @Inject lateinit var facade: ExoPlayerFacade
    @Inject lateinit var session: BookSession
    @Inject lateinit var library: LibraryRepository
    @Inject lateinit var progress: ProgressDao
    @Inject lateinit var server: ServerSession
    @Inject @AppScope lateinit var appScope: CoroutineScope
    @Inject lateinit var settings: Settings

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var mediaSession: MediaLibrarySession
    private lateinit var shake: ShakeDetector
    private var shakeStop: Job? = null

    override fun onCreate() {
        super.onCreate()
        facade.onEnded = { scope.launch { session.onBookEnded() } }
        facade.onStoppedPlaying = { scope.launch { session.save("stopped") } }
        facade.onError = { error -> scope.launch { recoverFromError(error) } }
        registerNetworkWatch()

        val player = AudiobookPlayer(
            facade.exo, scope, session,
            openBook = { id -> library.book(id)?.let { session.open(it) } },
            resumeLast = { resumeLastBook() },
        )
        val activity = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        mediaSession = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(activity)
            .setCustomLayout(skipButtons(15_000, 30_000))
            .build()

        // Tlačítka v notifikaci a v autě odpovídají nastaveným délkám skoků.
        scope.launch {
            combine(settings.skipBackMs, settings.skipForwardMs) { b, f -> b to f }.distinctUntilChanged().collect { (b, f) ->
                player.skipBackMs = b
                player.skipForwardMs = f
                mediaSession.setCustomLayout(skipButtons(b, f))
            }
        }

        shake = ShakeDetector(this) { scope.launch { session.onShake() } }
        scope.launch {
            session.sleepTimer.state.collect { mode ->
                if (mode != null) {
                    shakeStop?.cancel()
                    shake.start()
                } else {
                    shakeStop = scope.launch {
                        delay(60_000)
                        shake.stop()
                    }
                }
            }
        }
        scope.launch {
            while (isActive) {
                delay(1_000)
                session.onTick()
            }
        }
    }

    private fun skipButtons(backMs: Long, forwardMs: Long): List<CommandButton> {
        val back = (backMs / 1000).toInt()
        val fwd = (forwardMs / 1000).toInt()
        val backIcon = when (back) {
            5 -> CommandButton.ICON_SKIP_BACK_5
            10 -> CommandButton.ICON_SKIP_BACK_10
            15 -> CommandButton.ICON_SKIP_BACK_15
            30 -> CommandButton.ICON_SKIP_BACK_30
            else -> CommandButton.ICON_SKIP_BACK
        }
        val fwdIcon = when (fwd) {
            5 -> CommandButton.ICON_SKIP_FORWARD_5
            10 -> CommandButton.ICON_SKIP_FORWARD_10
            15 -> CommandButton.ICON_SKIP_FORWARD_15
            30 -> CommandButton.ICON_SKIP_FORWARD_30
            else -> CommandButton.ICON_SKIP_FORWARD
        }
        return listOf(
            CommandButton.Builder(backIcon).setPlayerCommand(Player.COMMAND_SEEK_BACK)
                .setDisplayName(getString(R.string.skip_back_n, back)).build(),
            CommandButton.Builder(fwdIcon).setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
                .setDisplayName(getString(R.string.skip_forward_n, fwd)).build(),
        )
    }

    private var lastRecovery = 0L

    /** Chyba přehrávání: najít funkční spojení (doma/venku) a načíst soubory znovu na stejném místě. */
    private suspend fun recoverFromError(error: androidx.media3.common.PlaybackException) {
        android.util.Log.w("Audioknihy", "chyba přehrávání: ${error.errorCodeName}", error)
        val now = System.currentTimeMillis()
        if (now - lastRecovery < 15_000) return
        lastRecovery = now
        server.invalidate()
        if (server.ensureReachable()) session.reloadSources()
    }

    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null

    /** Při změně sítě (Wi-Fi ↔ mobilní data) hned ověří spojení se serverem. */
    private fun registerNetworkWatch() {
        val cm = getSystemService(android.net.ConnectivityManager::class.java) ?: return
        val cb = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                scope.launch {
                    server.invalidate()
                    val before = server.client()?.let { it.streamUrl("") }
                    if (server.ensureReachable() && server.client()?.streamUrl("") != before && session.current.value != null) {
                        session.reloadSources()
                    }
                }
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(cb) }.onSuccess { networkCallback = cb }
    }

    private suspend fun resumeLastBook() {
        val last = progress.inProgress().first().firstOrNull() ?: return
        library.book(last.bookId)?.let { session.open(it) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession = mediaSession

    /** Nouzové uložení bez čekání na zámek a bez sítě (hlavní vlákno se nesmí zablokovat). */
    private fun saveNow() {
        val snap = session.snapshot() ?: return
        appScope.launch(Dispatchers.IO) { progress.upsert(snap) }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        saveNow()
        if (!facade.exo.playWhenReady) stopSelf()
    }

    override fun onDestroy() {
        saveNow()
        shake.stop()
        networkCallback?.let { cb -> runCatching { getSystemService(android.net.ConnectivityManager::class.java)?.unregisterNetworkCallback(cb) } }
        mediaSession.release()
        scope.cancel()
        super.onDestroy()
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        private val labels get() = AutoTree.Labels(getString(R.string.in_progress), getString(R.string.library))

        override fun onGetLibraryRoot(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(AutoTree.root(labels), params))

        override fun onGetChildren(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String,
            page: Int, pageSize: Int, params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = scope.future {
            val items = AutoTree.children(
                parentId, library.books.first(), progress.inProgress().first(), labels,
            ) { server.imageUrl(it.coverThumb, 300) }
            LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
        }

        override fun onGetItem(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = scope.future {
            val book = AutoTree.bookIdOf(mediaId)?.let { library.book(it) }
            if (book != null) LibraryResult.ofItem(AutoTree.bookItem(book, server.imageUrl(book.coverThumb, 300)), null)
            else LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = scope.future {
            // Kniha z procházení projde, hlasové hledání se převede na nejlépe odpovídající knihu.
            val books = library.books.first()
            mediaItems.mapNotNull { item ->
                when {
                    AutoTree.bookIdOf(item.mediaId) != null -> item
                    !item.requestMetadata.searchQuery.isNullOrBlank() ->
                        LibrarySearch.filter(books, item.requestMetadata.searchQuery!!).firstOrNull()
                            ?.let { AutoTree.bookItem(it, null) }
                    else -> null
                }
            }.toMutableList()
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession, controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
            val last = progress.inProgress().first().firstOrNull()
            val book = last?.let { library.book(it.bookId) }
            val items = listOfNotNull(book?.let { AutoTree.bookItem(it, null) })
            MediaSession.MediaItemsWithStartPosition(items, 0, 0)
        }
    }
}
