package cz.audioknihy.update

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    private val server = MockWebServer().apply { start() }
    private val base = server.url("/").toString().trimEnd('/')

    @After fun tearDown() = server.shutdown()

    @Test
    fun version_comparison() {
        assertTrue(Versions.isNewer("v1.6", "1.5"))
        assertTrue(Versions.isNewer("1.10", "1.9"))
        assertTrue(Versions.isNewer("v2.0", "1.15"))
        assertTrue(Versions.isNewer("1.5.1", "1.5"))
        assertFalse(Versions.isNewer("v1.5", "1.5"))
        assertFalse(Versions.isNewer("1.4", "1.5"))
        assertFalse(Versions.isNewer("nesmysl", "1.5"))
    }

    @Test
    fun finds_newer_release_with_apk() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"tag_name":"v1.6","name":"Audioknihy 1.6","body":"Novinky","draft":false,"prerelease":false,
                   "assets":[{"name":"notes.txt","browser_download_url":"https://x/notes.txt"},
                             {"name":"Audioknihy-1.6.apk","browser_download_url":"https://x/Audioknihy-1.6.apk","size":123}]}"""
            )
        )
        val checker = UpdateChecker(OkHttpClient(), repo = "owner/repo", apiBase = base)
        val r = checker.newerThan("1.5")!!
        assertEquals("1.6", r.version)
        assertEquals("https://x/Audioknihy-1.6.apk", r.apkUrl)
        assertEquals("Novinky", r.notes)
        assertEquals("/repos/owner/repo/releases/latest", server.takeRequest().path)
    }

    @Test
    fun same_version_is_null() = runTest {
        server.enqueue(MockResponse().setBody("""{"tag_name":"v1.5","assets":[{"name":"a.apk","browser_download_url":"u"}]}"""))
        assertNull(UpdateChecker(OkHttpClient(), "o/r", base).newerThan("1.5"))
    }

    @Test
    fun release_without_apk_is_null() = runTest {
        server.enqueue(MockResponse().setBody("""{"tag_name":"v9.0","assets":[]}"""))
        assertNull(UpdateChecker(OkHttpClient(), "o/r", base).newerThan("1.5"))
    }

    @Test
    fun http_error_is_null_not_crash() = runTest {
        server.enqueue(MockResponse().setResponseCode(403))
        assertNull(UpdateChecker(OkHttpClient(), "o/r", base).newerThan("1.5"))
    }
}
