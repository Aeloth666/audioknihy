package cz.audioknihy.plex

import cz.audioknihy.settings.Settings
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient

/**
 * Aktuálně vybraný server podle uloženého nastavení. Umí přepnout spojení
 * (doma místní adresa, venku vzdálený přístup) přes [ensureReachable].
 */
private const val TAG = "Audioknihy"

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

    @Volatile private var lastOk = 0L

    /** Zapomene poslední úspěšné ověření (změna sítě, chyba přehrávání). */
    fun invalidate() { lastOk = 0L }

    /** Ověří, že uložené spojení funguje; jinak najde jiné (místní/vzdálené/relay). */
    suspend fun ensureReachable(): Boolean {
        val uri = settings.serverUri.first() ?: return false
        val token = settings.serverToken.first() ?: return false
        if (System.currentTimeMillis() - lastOk < 30_000) return true
        if (connector.isReachable(uri, token)) {
            client()
            lastOk = System.currentTimeMillis()
            return true
        }
        android.util.Log.i(TAG, "uložené spojení nedostupné: $uri – hledám jiné")
        return reconnect()
    }

    suspend fun reconnect(): Boolean {
        val userToken = settings.plexUserToken.first() ?: return false
        val serverId = settings.serverId.first() ?: return false
        val servers = runCatching { connector.servers(userToken) }
            .onFailure { android.util.Log.w(TAG, "plex.tv resources selhalo", it) }.getOrNull()
        val server = servers?.firstOrNull { it.clientId == serverId } ?: run {
            android.util.Log.w(TAG, "server nenalezen mezi ${servers?.size} servery")
            return false
        }
        android.util.Log.i(TAG, "spojení: " + server.connections.joinToString { "${it.uri} local=${it.local} relay=${it.relay}" })
        val uri = connector.bestConnection(server) ?: run {
            android.util.Log.w(TAG, "žádné spojení neodpovědělo")
            return false
        }
        android.util.Log.i(TAG, "nové spojení: $uri")
        lastOk = System.currentTimeMillis()
        settings.setServer(server.clientId, server.name, uri, server.accessToken)
        client()
        return true
    }
}
