package cz.audioknihy.plex

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlexAuthTest {
    private val server = MockWebServer().apply { start() }
    private val headers = PlexHeaders(clientId = "client-123", deviceName = "Test")
    private val auth = PlexAuth(OkHttpClient(), headers, baseUrl = server.url("/").toString().trimEnd('/'), pollIntervalMs = 1)

    @After fun tearDown() = server.shutdown()

    @Test
    fun create_pin_posts_with_plex_headers() = runTest {
        server.enqueue(MockResponse().setBody("""{"id":42,"code":"abcd"}"""))
        val pin = auth.createPin()
        assertEquals(Pin(42, "abcd"), pin)
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/api/v2/pins?strong=true", req.path)
        assertEquals("client-123", req.getHeader("X-Plex-Client-Identifier"))
        assertEquals("Audioknihy", req.getHeader("X-Plex-Product"))
        assertEquals("application/json", req.getHeader("Accept"))
    }

    @Test
    fun auth_url_contains_client_and_code() {
        assertEquals(
            "https://app.plex.tv/auth#?clientID=client-123&code=abcd&context%5Bdevice%5D%5Bproduct%5D=Audioknihy",
            auth.authUrl(Pin(42, "abcd")),
        )
    }

    @Test
    fun poll_returns_token_once_present() = runTest {
        server.enqueue(MockResponse().setBody("""{"id":42,"code":"abcd","authToken":null}"""))
        server.enqueue(MockResponse().setBody("""{"id":42,"code":"abcd","authToken":"tok"}"""))
        assertEquals("tok", auth.pollToken(Pin(42, "abcd")))
        assertEquals("/api/v2/pins/42", server.takeRequest().path)
    }

    @Test
    fun poll_times_out_to_null() = runTest {
        repeat(50) { server.enqueue(MockResponse().setBody("""{"id":42,"code":"abcd"}""")) }
        assertNull(auth.pollToken(Pin(42, "abcd"), timeoutMs = 0))
    }
}
