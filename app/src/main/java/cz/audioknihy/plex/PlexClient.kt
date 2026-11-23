package cz.audioknihy.plex

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder

data class MusicSection(val key: String, val title: String)

/** Co aplikace potřebuje od Plex serveru. Rozhraní kvůli testům. */
interface PlexSource {
    suspend fun allTracks(sectionKey: String): List<PlexTrack>
    suspend fun tracksMetadata(ratingKeys: List<String>): List<PlexTrack>
    suspend fun timeline(ratingKey: String, state: String, timeMs: Long, durationMs: Long)
    suspend fun scrobble(ratingKey: String)
    suspend fun unscrobble(ratingKey: String)
    suspend fun headRange(partKey: String, start: Long, length: Int): ByteArray
    fun streamUrl(partKey: String): String
    fun imageUrl(thumb: String, sizePx: Int): String
}

class PlexClient(
    private val baseUrl: String,
    private val token: String,
    private val http: OkHttpClient,
    private val headers: PlexHeaders,
) : PlexSource {

    private fun req(pathAndQuery: String) = headers.apply(Request.Builder().url(baseUrl + pathAndQuery), token)

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    suspend fun musicSections(): List<MusicSection> {
        val c = PlexJson.container.fromJson(http.fetch(req("/library/sections").build()).decodeToString())
            ?: throw IOException("Prázdná odpověď")
        return c.container.directories.filter { it.type == "artist" }.map { MusicSection(it.key, it.title) }
    }

    override suspend fun allTracks(sectionKey: String): List<PlexTrack> {
        val out = mutableListOf<PlexTrack>()
        var start = 0
        while (true) {
            val r = req("/library/sections/$sectionKey/all?type=10")
                .header("X-Plex-Container-Start", start.toString())
                .header("X-Plex-Container-Size", PAGE.toString())
                .build()
            val c = PlexJson.container.fromJson(http.fetch(r).decodeToString())?.container
                ?: throw IOException("Prázdná odpověď")
            out += c.metadata.mapNotNull { it.toTrack() }
            start += PAGE
            val total = c.totalSize ?: break
            if (start >= total || c.metadata.isEmpty()) break
        }
        return out
    }

    override suspend fun tracksMetadata(ratingKeys: List<String>): List<PlexTrack> {
        if (ratingKeys.isEmpty()) return emptyList()
        val c = PlexJson.container.fromJson(http.fetch(req("/library/metadata/" + ratingKeys.joinToString(",")).build()).decodeToString())
            ?: throw IOException("Prázdná odpověď")
        return c.container.metadata.mapNotNull { it.toTrack() }
    }

    override suspend fun timeline(ratingKey: String, state: String, timeMs: Long, durationMs: Long) {
        http.fetch(
            req(
                "/:/timeline?ratingKey=$ratingKey&key=${enc("/library/metadata/$ratingKey")}" +
                    "&state=$state&time=$timeMs&duration=$durationMs"
            ).build()
        )
    }

    override suspend fun scrobble(ratingKey: String) {
        http.fetch(req("/:/scrobble?identifier=com.plexapp.plugins.library&key=$ratingKey").build())
    }

    override suspend fun unscrobble(ratingKey: String) {
        http.fetch(req("/:/unscrobble?identifier=com.plexapp.plugins.library&key=$ratingKey").build())
    }

    override suspend fun headRange(partKey: String, start: Long, length: Int): ByteArray =
        http.fetch(req(partKey).header("Range", "bytes=$start-${start + length - 1}").build())

    override fun streamUrl(partKey: String): String =
        (baseUrl + partKey).toHttpUrl().newBuilder().addQueryParameter("X-Plex-Token", token).build().toString()

    override fun imageUrl(thumb: String, sizePx: Int): String =
        "$baseUrl/photo/:/transcode?width=$sizePx&height=$sizePx&url=${enc(thumb)}&X-Plex-Token=$token"

    companion object { const val PAGE = 500 }
}
