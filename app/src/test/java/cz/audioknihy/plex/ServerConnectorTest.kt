package cz.audioknihy.plex

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerConnectorTest {
    private val plexTv = MockWebServer().apply { start() }
    private val pms = MockWebServer().apply { start() }
    private val connector = ServerConnector(
        OkHttpClient(), PlexHeaders("c", "Test"), plexTvUrl = plexTv.url("/").toString().trimEnd('/'),
    )
    private val dead = "http://127.0.0.1:1"

    @After fun tearDown() { plexTv.shutdown(); pms.shutdown() }

    @Test
    fun servers_lists_only_server_resources() = runTest {
        plexTv.enqueue(
            MockResponse().setBody(
                """[{"name":"myserver","clientIdentifier":"srv1","provides":"server","accessToken":"st",
                     "connections":[{"uri":"http://192.168.0.10:32400","local":true,"relay":false}]},
                    {"name":"Telefon","clientIdentifier":"ph","provides":"client,player","connections":[]}]"""
            )
        )
        val servers = connector.servers("usertok")
        assertEquals(
            listOf(PlexServer("myserver", "srv1", "st", listOf(PlexConnection("http://192.168.0.10:32400", local = true, relay = false)))),
            servers,
        )
        val req = plexTv.takeRequest()
        assertEquals("/api/v2/resources?includeHttps=1&includeRelay=1", req.path)
        assertEquals("usertok", req.getHeader("X-Plex-Token"))
    }

    @Test
    fun best_connection_prefers_working_local_then_remote_then_relay() = runTest {
        pms.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) =
                if (request.path == "/identity" && request.getHeader("X-Plex-Token") == "st") MockResponse().setBody("{}")
                else MockResponse().setResponseCode(404)
        }
        val good = pms.url("/").toString().trimEnd('/')
        val relayFirst = PlexServer(
            "s", "id", "st",
            listOf(
                PlexConnection("http://relay.invalid:1", local = false, relay = true),
                PlexConnection(good, local = false, relay = false),
                PlexConnection(dead, local = true, relay = false),
            ),
        )
        assertEquals(good, connector.bestConnection(relayFirst))
    }

    @Test
    fun best_connection_null_when_none_work() = runTest {
        assertNull(connector.bestConnection(PlexServer("s", "id", "st", listOf(PlexConnection(dead, local = true, relay = false)))))
    }
}
