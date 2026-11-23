package cz.audioknihy.plex

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

/** JSON odpovědi Plexu (jen pole, která aplikace potřebuje). */
@JsonClass(generateAdapter = true)
internal data class PinJson(val id: Long, val code: String, val authToken: String? = null)

@JsonClass(generateAdapter = true)
internal data class ResourceJson(
    val name: String,
    val clientIdentifier: String,
    val provides: String = "",
    val accessToken: String? = null,
    val connections: List<ConnectionJson> = emptyList(),
)

@JsonClass(generateAdapter = true)
internal data class ConnectionJson(val uri: String, val local: Boolean = false, val relay: Boolean = false)

@JsonClass(generateAdapter = true)
internal data class ContainerJson(@com.squareup.moshi.Json(name = "MediaContainer") val container: MediaContainerJson)

@JsonClass(generateAdapter = true)
internal data class MediaContainerJson(
    val size: Int = 0,
    val totalSize: Int? = null,
    @com.squareup.moshi.Json(name = "Directory") val directories: List<DirectoryJson> = emptyList(),
    @com.squareup.moshi.Json(name = "Metadata") val metadata: List<MetadataJson> = emptyList(),
)

@JsonClass(generateAdapter = true)
internal data class DirectoryJson(val key: String, val title: String, val type: String)

@JsonClass(generateAdapter = true)
internal data class MetadataJson(
    val ratingKey: String,
    val title: String = "",
    val parentTitle: String? = null,
    val grandparentTitle: String? = null,
    val index: Int? = null,
    val duration: Long? = null,
    val viewOffset: Long? = null,
    val viewCount: Int? = null,
    val lastViewedAt: Long? = null,
    val thumb: String? = null,
    val parentThumb: String? = null,
    val grandparentThumb: String? = null,
    @com.squareup.moshi.Json(name = "Media") val media: List<MediaJson> = emptyList(),
)

@JsonClass(generateAdapter = true)
internal data class MediaJson(@com.squareup.moshi.Json(name = "Part") val parts: List<PartJson> = emptyList())

@JsonClass(generateAdapter = true)
internal data class PartJson(val key: String, val file: String? = null)

internal object PlexJson {
    val moshi: Moshi = Moshi.Builder().build()
    val pin = moshi.adapter(PinJson::class.java)
    val resources = moshi.adapter<List<ResourceJson>>(Types.newParameterizedType(List::class.java, ResourceJson::class.java))
    val container = moshi.adapter(ContainerJson::class.java)
}

internal fun MetadataJson.toTrack(): PlexTrack? {
    val part = media.firstOrNull()?.parts?.firstOrNull() ?: return null
    return PlexTrack(
        ratingKey = ratingKey,
        title = title,
        albumTitle = parentTitle.orEmpty(),
        artist = grandparentTitle.orEmpty(),
        index = index,
        durationMs = duration ?: 0,
        filePath = part.file ?: part.key,
        partKey = part.key,
        thumb = thumb ?: parentThumb,
        viewOffsetMs = viewOffset,
        viewCount = viewCount ?: 0,
        lastViewedAtSec = lastViewedAt,
        authorThumb = grandparentThumb,
    )
}
