package cz.audioknihy.plex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** Hlavičky X-Plex-*, které Plex vyžaduje od každého klienta. */
class PlexHeaders(val clientId: String, val deviceName: String) {
    fun apply(b: Request.Builder, token: String? = null): Request.Builder = b
        .header("X-Plex-Client-Identifier", clientId)
        .header("X-Plex-Product", PRODUCT)
        .header("X-Plex-Version", "1.0")
        .header("X-Plex-Platform", "Android")
        .header("X-Plex-Device-Name", deviceName)
        .header("Accept", "application/json")
        .apply { if (token != null) header("X-Plex-Token", token) }

    companion object { const val PRODUCT = "Audioknihy" }
}

/** Provede požadavek mimo hlavní vlákno; při chybě HTTP vyhodí IOException. */
internal suspend fun OkHttpClient.fetch(request: Request): ByteArray = withContext(Dispatchers.IO) {
    newCall(request).execute().use { r ->
        if (!r.isSuccessful) throw IOException("HTTP ${r.code} ${request.url.encodedPath}")
        r.body?.bytes() ?: ByteArray(0)
    }
}
