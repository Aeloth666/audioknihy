package cz.audioknihy.plex

import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

data class Pin(val id: Long, val code: String)

/** Přihlášení přes PIN na plex.tv (stejně jako Plexamp). */
class PlexAuth(
    private val http: OkHttpClient,
    private val headers: PlexHeaders,
    private val baseUrl: String = "https://plex.tv",
    private val pollIntervalMs: Long = 2_000,
) {
    suspend fun createPin(): Pin {
        val req = headers.apply(Request.Builder().url("$baseUrl/api/v2/pins?strong=true"))
            .post(ByteArray(0).toRequestBody()).build()
        val json = PlexJson.pin.fromJson(http.fetch(req).decodeToString()) ?: throw IOException("Prázdná odpověď")
        return Pin(json.id, json.code)
    }

    fun authUrl(pin: Pin): String =
        "https://app.plex.tv/auth#?clientID=${headers.clientId}&code=${pin.code}" +
            "&context%5Bdevice%5D%5Bproduct%5D=${PlexHeaders.PRODUCT}"

    /** Čeká, dokud uživatel v prohlížeči nepotvrdí přihlášení. Vrátí token, nebo null po vypršení. */
    suspend fun pollToken(pin: Pin, timeoutMs: Long = 300_000): String? {
        var waited = 0L
        while (true) {
            val req = headers.apply(Request.Builder().url("$baseUrl/api/v2/pins/${pin.id}")).build()
            val token = PlexJson.pin.fromJson(http.fetch(req).decodeToString())?.authToken
            if (!token.isNullOrEmpty()) return token
            if (waited >= timeoutMs) return null
            delay(pollIntervalMs)
            waited += pollIntervalMs
        }
    }
}
