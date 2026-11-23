package cz.audioknihy.plex

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun fixture(name: String) =
    PlexClientTest::class.java.getResource("/plex/$name")!!.readText()

class PlexClientTest {
    private val server = MockWebServer().apply { start() }
    private val base = server.url("/").toString().trimEnd('/')
    private val client = PlexClient(base, "srvtok", OkHttpClient(), PlexHeaders("c", "Test"))

    @After fun tearDown() = server.shutdown()

    @Test
    fun music_sections_only_artist_type() = runTest {
        server.enqueue(MockResponse().setBody(fixture("sections.json")))
        assertEquals(listOf(MusicSection("5", "AudioKnihy"), MusicSection("6", "Hudba")), client.musicSections())
        val req = server.takeRequest()
        assertEquals("/library/sections", req.path)
        assertEquals("srvtok", req.getHeader("X-Plex-Token"))
    }

    @Test
    fun all_tracks_maps_fields() = runTest {
        server.enqueue(MockResponse().setBody(fixture("tracks_page.json").replace("\"totalSize\":1200", "\"totalSize\":2")))
        val tracks = client.allTracks("5")
        assertEquals(
            PlexTrack(
                ratingKey = "101", title = "01", albumTitle = "DUNA", artist = "Frank Herbert", index = 1,
                durationMs = 6_268_000, filePath = "/data/AudioKnihy/AudioKnihy/Frank Herbert/01 Duna/01.m4a",
                partKey = "/library/parts/9001/1700000000/file.m4a", thumb = "/library/metadata/101/thumb/1",
                viewOffsetMs = 120_000, viewCount = 0, lastViewedAtSec = 1_700_000_000,
            ),
            tracks[0],
        )
        assertEquals(null, tracks[1].index)
        assertEquals("/library/metadata/100/thumb/1", tracks[1].thumb)
        assertEquals(0, tracks[1].viewCount)
        val req = server.takeRequest()
        assertEquals("/library/sections/5/all?type=10", req.path)
        assertEquals("0", req.getHeader("X-Plex-Container-Start"))
        assertEquals("500", req.getHeader("X-Plex-Container-Size"))
    }

    @Test
    fun all_tracks_pages_by_500() = runTest {
        val starts = mutableListOf<String>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                starts += request.getHeader("X-Plex-Container-Start")!!
                return MockResponse().setBody(fixture("tracks_page.json"))
            }
        }
        client.allTracks("5")
        assertEquals(listOf("0", "500", "1000"), starts)
    }

    @Test
    fun timeline_and_scrobble_paths() = runTest {
        repeat(3) { server.enqueue(MockResponse()) }
        client.timeline("101", "paused", 5_000, 60_000)
        client.scrobble("101")
        client.unscrobble("101")
        assertEquals(
            "/:/timeline?ratingKey=101&key=%2Flibrary%2Fmetadata%2F101&state=paused&time=5000&duration=60000",
            server.takeRequest().path,
        )
        assertEquals("/:/scrobble?identifier=com.plexapp.plugins.library&key=101", server.takeRequest().path)
        assertEquals("/:/unscrobble?identifier=com.plexapp.plugins.library&key=101", server.takeRequest().path)
    }

    @Test
    fun stream_and_image_urls_carry_token() {
        assertEquals("$base/library/parts/9001/1/file.m4a?X-Plex-Token=srvtok", client.streamUrl("/library/parts/9001/1/file.m4a"))
        val img = client.imageUrl("/library/metadata/100/thumb/1", 300)
        assertTrue(img.startsWith("$base/photo/:/transcode?width=300&height=300&url=%2Flibrary%2Fmetadata%2F100%2Fthumb%2F1"))
        assertTrue(img.endsWith("X-Plex-Token=srvtok"))
    }

    @Test
    fun head_range_requests_first_bytes() = runTest {
        server.enqueue(MockResponse().setResponseCode(206).setBody("abcd"))
        assertArrayEquals("abcd".toByteArray(), client.headRange("/library/parts/1/file.m4a", 0, 4))
        assertEquals("bytes=0-3", server.takeRequest().getHeader("Range"))
    }

    @Test(expected = java.io.IOException::class)
    fun http_error_throws_io_exception() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        client.musicSections()
    }

    @Test
    fun tracks_metadata_fetches_keys_in_one_request() = runTest {
        server.enqueue(MockResponse().setBody(fixture("tracks_page.json")))
        val tracks = client.tracksMetadata(listOf("101", "102"))
        assertEquals(listOf("101", "102"), tracks.map { it.ratingKey })
        assertEquals("/library/metadata/101,102", server.takeRequest().path)
    }
}
