package cz.audioknihy.library

import com.squareup.moshi.JsonClass
import cz.audioknihy.plex.PlexTrack

/**
 * Kniha = složka na disku. [id] je cesta ke složce knihy tak, jak ji vrací Plex.
 * [files] jsou seřazené v pořadí přehrávání.
 */
@JsonClass(generateAdapter = true)
data class Book(
    val id: String,
    val title: String,
    val author: String,
    val coverThumb: String?,
    val files: List<PlexTrack>,
    /** Obrázek autora v Plexu, když kniha nemá obal. */
    val authorThumb: String? = null,
) {
    val durationMs: Long get() = files.sumOf { it.durationMs }
}
