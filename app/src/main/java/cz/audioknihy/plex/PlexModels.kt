package cz.audioknihy.plex

import com.squareup.moshi.JsonClass

/** Jedna zvuková stopa (soubor) z Plexu. */
@JsonClass(generateAdapter = true)
data class PlexTrack(
    val ratingKey: String,
    val title: String,
    val albumTitle: String,
    val artist: String,
    val index: Int?,
    val durationMs: Long,
    val filePath: String,
    val partKey: String,
    val thumb: String?,
    val viewOffsetMs: Long?,
    val viewCount: Int,
    val lastViewedAtSec: Long?,
    /** Obrázek autora v Plexu (grandparentThumb). */
    val authorThumb: String? = null,
)

/** Připojený server a vybraná knihovna. */
data class Connected(val source: PlexSource, val sectionKey: String)

/** Poskytuje aktuálně připojený server (null = nepřihlášeno / nevybráno). */
fun interface ActiveServer {
    suspend fun get(): Connected?
}
