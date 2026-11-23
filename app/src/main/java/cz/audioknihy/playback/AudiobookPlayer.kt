package cz.audioknihy.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import cz.audioknihy.auto.AutoTree
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Přehrávač, který vidí notifikace, sluchátka a Android Auto. Skoky a „další/předchozí“
 * posílá do [BookSession] (skok přes soubory, kapitoly), výběr knihy (mediaId "book:…")
 * otevře knihu s uloženou pozicí.
 */
class AudiobookPlayer(
    player: Player,
    private val scope: CoroutineScope,
    private val session: BookSession,
    private val openBook: suspend (bookId: String) -> Unit,
    private val resumeLast: suspend () -> Unit,
) : ForwardingPlayer(player) {

    override fun getAvailableCommands(): Player.Commands = super.getAvailableCommands().buildUpon()
        .addAll(
            Player.COMMAND_SEEK_BACK, Player.COMMAND_SEEK_FORWARD,
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_PLAY_PAUSE,
        )
        .build()

    override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)

    override fun seekBack() { scope.launch { session.skipBack() } }
    override fun seekForward() { scope.launch { session.skipForward() } }
    override fun seekToNext() { scope.launch { session.nextChapter() } }
    override fun seekToNextMediaItem() { scope.launch { session.nextChapter() } }
    override fun seekToPrevious() { scope.launch { session.prevChapter() } }
    override fun seekToPreviousMediaItem() { scope.launch { session.prevChapter() } }

    /** Právě se otevírá kniha vybraná v autě/notifikaci: play() na ni počká, nespustí jinou. */
    @Volatile private var pendingOpen = false

    override fun play() {
        when {
            pendingOpen -> Unit
            session.current.value == null -> scope.launch { resumeLast() }
            else -> scope.launch { session.play() }
        }
    }

    override fun pause() {
        super.pause() // zastavit zvuk hned, uložení počká na zámek
        scope.launch { session.pause() }
    }

    override fun seekTo(positionMs: Long) {
        val index = currentMediaItemIndex
        scope.launch { session.seekTo(index, positionMs) }
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        scope.launch { session.seekTo(mediaItemIndex, positionMs) }
    }

    // Media3 po dohrání před Přehrát skočí na začátek posledního souboru; dokončenou knihu to nesmí oživit.
    override fun seekToDefaultPosition() {
        if (!session.isCurrentFinished) super.seekToDefaultPosition()
    }

    override fun seekToDefaultPosition(mediaItemIndex: Int) {
        if (!session.isCurrentFinished) super.seekToDefaultPosition(mediaItemIndex)
    }

    override fun getSeekBackIncrement(): Long = skipBackMs
    override fun getSeekForwardIncrement(): Long = skipForwardMs

    /** Délky skoků z nastavení (pro zobrazení v notifikaci a v autě). */
    @Volatile var skipBackMs: Long = 15_000
    @Volatile var skipForwardMs: Long = 30_000

    override fun setPlayWhenReady(playWhenReady: Boolean) = if (playWhenReady) play() else pause()

    /**
     * Výběr knihy (mediaId "book:…") otevře knihu přes [BookSession]. Cokoli jiného se ignoruje:
     * cizí položka v přehrávači by jinak přepsala pozici aktuální knihy.
     */
    private fun interceptBook(items: List<MediaItem>): Boolean {
        val id = items.firstOrNull()?.mediaId?.let(AutoTree::bookIdOf) ?: return true
        pendingOpen = true
        scope.launch {
            try {
                openBook(id)
            } finally {
                pendingOpen = false
            }
        }
        return true
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>) {
        if (!interceptBook(mediaItems)) super.setMediaItems(mediaItems)
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {
        if (!interceptBook(mediaItems)) super.setMediaItems(mediaItems, resetPosition)
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long) {
        if (!interceptBook(mediaItems)) super.setMediaItems(mediaItems, startIndex, startPositionMs)
    }

    override fun setMediaItem(mediaItem: MediaItem) {
        if (!interceptBook(listOf(mediaItem))) super.setMediaItem(mediaItem)
    }

    override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) {
        if (!interceptBook(listOf(mediaItem))) super.setMediaItem(mediaItem, resetPosition)
    }

    override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) {
        if (!interceptBook(listOf(mediaItem))) super.setMediaItem(mediaItem, startPositionMs)
    }

    override fun addMediaItems(mediaItems: MutableList<MediaItem>) {
        if (!interceptBook(mediaItems)) super.addMediaItems(mediaItems)
    }

    override fun prepare() {
        if (session.current.value != null) super.prepare()
    }
}
