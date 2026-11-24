@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package cz.audioknihy.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import cz.audioknihy.viz.AudioTap
import java.nio.ByteBuffer

/** [PlayerFacade] nad ExoPlayerem. Volat jen z hlavního vlákna. */
class ExoPlayerFacade(context: Context) : PlayerFacade {

    /** Odbočka zvuku pro vizualizace (bez oprávnění k mikrofonu). */
    val tap = AudioTap(144_000).apply { enabled = false }

    private val renderers = object : DefaultRenderersFactory(context.applicationContext) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioTrackPlaybackParams: Boolean,
        ): AudioSink = DefaultAudioSink.Builder(context)
            .setAudioProcessorChain(
                DefaultAudioSink.DefaultAudioProcessorChain(
                    TeeAudioProcessor(object : TeeAudioProcessor.AudioBufferSink {
                        @Volatile private var pcm16 = true
                        override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
                            pcm16 = encoding == C.ENCODING_PCM_16BIT
                            tap.onFormat(sampleRateHz, channelCount)
                        }
                        override fun handleBuffer(buffer: ByteBuffer) {
                            if (pcm16) tap.onPcm16(buffer)
                        }
                    })
                )
            )
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .build()
    }

    val exo: ExoPlayer = ExoPlayer.Builder(context.applicationContext, renderers)
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

    /** Chyba přehrávání (typicky nedostupný server po změně sítě). */
    var onError: (androidx.media3.common.PlaybackException) -> Unit = {}

    init {
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) onEnded()
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                onError(error)
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
        tap.expectDiscontinuity(positionMs)
        exo.setMediaItems(media, fileIndex, positionMs)
        exo.playbackParameters = PlaybackParameters(speed)
        exo.prepare()
    }

    override fun play() {
        if (exo.playbackState == Player.STATE_IDLE) exo.prepare()
        exo.play()
    }

    override fun pause() = exo.pause()
    override fun seekTo(fileIndex: Int, positionMs: Long) {
        tap.expectDiscontinuity(positionMs)
        exo.seekTo(fileIndex, positionMs)
    }
    override fun setSpeed(speed: Float) { exo.playbackParameters = PlaybackParameters(speed) }
    override fun setVolume(v: Float) { exo.volume = v }
}
