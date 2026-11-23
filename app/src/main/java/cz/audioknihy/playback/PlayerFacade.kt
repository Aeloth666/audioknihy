package cz.audioknihy.playback

import cz.audioknihy.plex.PlexTrack

/** Jedna položka fronty přehrávače (= jeden soubor knihy). */
data class PlayItem(
    val mediaId: String,
    val uri: String,
    val title: String,
    val bookTitle: String,
    val author: String,
    val artworkUri: String?,
)

/** Úzké rozhraní přehrávače, aby šla logika [BookSession] testovat bez zařízení. */
interface PlayerFacade {
    val fileIndex: Int
    val positionMs: Long
    val isPlaying: Boolean
    fun load(items: List<PlayItem>, fileIndex: Int, positionMs: Long, speed: Float)
    fun play()
    fun pause()
    fun seekTo(fileIndex: Int, positionMs: Long)
    fun setSpeed(speed: Float)
    fun setVolume(v: Float)
}

/** Vrátí URI, ze kterého se stopa přehraje (dnes stream z Plexu, později i místní soubor). */
fun interface TrackUriResolver {
    suspend fun uri(track: PlexTrack): String
}

/** Nastavení, která přehrávač potřebuje. */
interface PlaybackPrefs {
    suspend fun skipBackMs(): Long
    suspend fun skipForwardMs(): Long
    suspend fun resumeRewindMs(): Long
    suspend fun syncToServer(): Boolean
}
