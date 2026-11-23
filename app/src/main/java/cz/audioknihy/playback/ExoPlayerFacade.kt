package cz.audioknihy.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/** [PlayerFacade] nad ExoPlayerem. Volat jen z hlavního vlákna. */
class ExoPlayerFacade(context: Context) : PlayerFacade {

    val exo: ExoPlayer = ExoPlayer.Builder(context.applicationContext)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .setSeekBackIncrementMs(15_000)
        .setSeekForwardIncrementMs(30_000)
        .build()

    /** Konec posledního souboru knihy. */
    var onEnded: () -> Unit = {}

    /** Přehrávání se zastavilo odjinud (sluchátka, notifikace, výpadek zvuku). */
    var onStoppedPlaying: () -> Unit = {}

    init {
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) onEnded()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying && !exo.playWhenReady) onStoppedPlaying()
            }
        })
    }

    override val fileIndex: Int get() = exo.currentMediaItemIndex
    override val positionMs: Long get() = exo.currentPosition
    override val isPlaying: Boolean get() = exo.playWhenReady && exo.playbackState != Player.STATE_ENDED

    override fun load(items: List<PlayItem>, fileIndex: Int, positionMs: Long, speed: Float) {
        val media = items.map { item ->
            MediaItem.Builder()
                .setMediaId(item.mediaId)
                .setUri(item.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(item.title)
                        .setAlbumTitle(item.bookTitle)
                        .setArtist(item.author)
                        .setArtworkUri(item.artworkUri?.let(Uri::parse))
                        .setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK_CHAPTER)
                        .build()
                )
                .build()
        }
        exo.setMediaItems(media, fileIndex, positionMs)
        exo.playbackParameters = PlaybackParameters(speed)
        exo.prepare()
    }

    override fun play() {
        if (exo.playbackState == Player.STATE_IDLE) exo.prepare()
        exo.play()
    }

    override fun pause() = exo.pause()
    override fun seekTo(fileIndex: Int, positionMs: Long) = exo.seekTo(fileIndex, positionMs)
    override fun setSpeed(speed: Float) { exo.playbackParameters = PlaybackParameters(speed) }
    override fun setVolume(v: Float) { exo.volume = v }
}
