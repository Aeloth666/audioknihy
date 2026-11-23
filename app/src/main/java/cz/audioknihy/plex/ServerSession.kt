package cz.audioknihy.plex

import cz.audioknihy.settings.Settings
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient

/**
 * Aktuálně vybraný server podle uloženého nastavení. Umí přepnout spojení
 * (doma místní adresa, venku vzdálený přístup) přes [ensureReachable].
 */
class ServerSession(
    private val settings: Settings,
    private val http: OkHttpClient,
    private val headers: PlexHeaders,
    private val connector: ServerConnector,
) : ActiveServer {

    @Volatile private var cached: Triple<String, String, PlexClient>? = null

    private val _ready = kotlinx.coroutines.flow.MutableStateFlow(0)
    /** Mění se, když je k dispozici klient (obrazovky se pak překreslí s obaly). */
    val ready: kotlinx.coroutines.flow.StateFlow<Int> = _ready

    suspend fun client(): PlexClient? {
        val uri = settings.serverUri.first() ?: return null
        val token = settings.serverToken.first() ?: return null
        cached?.let { (u, t, c) -> if (u == uri && t == token) return c }
        return PlexClient(uri, token, http, headers).also {
            cached = Triple(uri, token, it)
            _ready.value = _ready.value + 1
        }
    }

    override suspend fun get(): Connected? {
        val section = settings.sectionKey.first() ?: return null
        return client()?.let { Connected(it, section) }
    }

    /** URL obrázku přes naposledy použitého klienta (pro Compose, bez čekání). */
    fun imageUrl(thumb: String?, sizePx: Int): String? = thumb?.let { cached?.third?.imageUrl(it, sizePx) }

    /** Ověří, že uložené spojení funguje; jinak najde jiné (místní/vzdálené/relay). */
    suspend fun ensureReachable(): Boolean {
        val uri = settings.serverUri.first() ?: return false
        val token = settings.serverToken.first() ?: return false
        if (connector.isReachable(uri, token)) {
            client()
            return true
        }
        return reconnect()
    }

    suspend fun reconnect(): Boolean {
        val userToken = settings.plexUserToken.first() ?: return false
        val serverId = settings.serverId.first() ?: return false
        val server = runCatching { connector.servers(userToken) }.getOrNull()
            ?.firstOrNull { it.clientId == serverId } ?: return false
        val uri = connector.bestConnection(server) ?: return false
        settings.setServer(server.clientId, server.name, uri, server.accessToken)
        client()
        return true
    }
}
