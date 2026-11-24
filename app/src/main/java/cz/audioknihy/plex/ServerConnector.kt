package cz.audioknihy.plex

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

data class PlexServer(val name: String, val clientId: String, val accessToken: String, val connections: List<PlexConnection>)
data class PlexConnection(val uri: String, val local: Boolean, val relay: Boolean)

/** Najde servery účtu a vybere spojení, které opravdu funguje. */
class ServerConnector(
    private val http: OkHttpClient,
    private val headers: PlexHeaders,
    private val plexTvUrl: String = "https://plex.tv",
) {
    private val probe = http.newBuilder()
        .connectTimeout(3, TimeUnit.SECONDS).readTimeout(3, TimeUnit.SECONDS).callTimeout(4, TimeUnit.SECONDS)
        .build()

    /** Vzdálené spojení a relay přes mobilní data potřebují na TLS víc času. */
    private val probeRemote = http.newBuilder()
        .connectTimeout(6, TimeUnit.SECONDS).readTimeout(6, TimeUnit.SECONDS).callTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun servers(userToken: String): List<PlexServer> {
        val req = headers.apply(Request.Builder().url("$plexTvUrl/api/v2/resources?includeHttps=1&includeRelay=1"), userToken).build()
        val list = PlexJson.resources.fromJson(http.fetch(req).decodeToString()).orEmpty()
        return list.filter { "server" in it.provides.split(',') }.map { r ->
            PlexServer(
                name = r.name,
                clientId = r.clientIdentifier,
                accessToken = r.accessToken ?: userToken,
                connections = r.connections.map { PlexConnection(it.uri, it.local, it.relay) },
            )
        }
    }

    suspend fun isReachable(uri: String, token: String): Boolean = try {
        probe.fetch(headers.apply(Request.Builder().url("${uri.trimEnd('/')}/identity"), token).build())
        true
    } catch (e: IOException) {
        false
    } catch (e: IllegalArgumentException) {
        false
    }

    /** Pořadí: místní → vzdálené přímé → relay. Vrátí první URI, které odpoví na /identity. */
    suspend fun bestConnection(server: PlexServer): String? {
        val ordered = server.connections.sortedBy {
            when {
                it.relay -> 2
                it.local -> 0
                else -> 1
            }
        }
        for (c in ordered) {
            val uri = c.uri.trimEnd('/')
            val ok = try {
                (if (c.local) probe else probeRemote).fetch(headers.apply(Request.Builder().url("$uri/identity"), server.accessToken).build())
                true
            } catch (e: IOException) {
                false
            } catch (e: IllegalArgumentException) {
                false
            }
            android.util.Log.i("Audioknihy", "zkouším $uri → ${if (ok) "OK" else "nedostupné"}")
            if (ok) return uri
        }
        return null
    }
}
