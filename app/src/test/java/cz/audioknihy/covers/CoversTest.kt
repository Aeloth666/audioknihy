package cz.audioknihy.covers

import cz.audioknihy.library.Book
import cz.audioknihy.library.track
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

class CoverSearchTest {
    private val server = MockWebServer().apply { start() }
    private val base = server.url("/").toString().trimEnd('/')
    private val search = CoverSearch(OkHttpClient(), googleBase = "$base/g", openLibraryBase = "$base/ol", coversBase = "https://covers.test")

    @After fun tearDown() = server.shutdown()

    @Test
    fun clean_title_strips_numbering_and_narrator() {
        assertEquals("Harry Potter a Relikvie smrti", CoverSearch.cleanTitle("7 - Harry Potter a Relikvie smrti (čte Jan Hyhlik)"))
        assertEquals("Duna", CoverSearch.cleanTitle("01 Duna"))
        assertEquals("věž vlaštovky", CoverSearch.cleanTitle("kniha 6.věž vlaštovky"))
        assertEquals("Husitská triologie Narrenturm", CoverSearch.cleanTitle("Husitská triologie - 01 - Narrenturm"))
        assertEquals("Cry Wolf", CoverSearch.cleanTitle("01. Cry Wolf"))
        assertEquals("Vyděděnci", CoverSearch.cleanTitle("John Flanagan - 01 Vyděděnci", author = "John Flanagan"))
    }

    @Test
    fun search_variants_full_title_first_then_parts() {
        assertEquals(
            listOf("Harry Potter a Relikvie smrti"),
            CoverSearch.searchVariants("7 - Harry Potter a Relikvie smrti (čte Jan Hyhlik)", "J.K. Rowlingová"),
        )
        assertEquals(
            listOf("Hra o trůny: Píseň ledu a ohně 1", "Hra o trůny", "Píseň ledu a ohně 1"),
            CoverSearch.searchVariants("Hra o trůny: Píseň ledu a ohně 1", "G.R.R. Martin"),
        )
        assertEquals(
            listOf("Zaklínač - 2. Meč osudu", "Meč osudu", "Zaklínač"),
            CoverSearch.searchVariants("Zaklínač - 2. Meč osudu", "Andrzej Sapkowski"),
        )
    }

    @Test
    fun google_books_thumbnail_is_used_and_made_https() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = when {
                request.path!!.startsWith("/g/books/v1/volumes") -> MockResponse().setBody(
                    """{"items":[{"volumeInfo":{"imageLinks":{"thumbnail":"http://books.google.com/x?id=1&zoom=1&edge=curl"}}}]}"""
                )
                else -> MockResponse().setResponseCode(404)
            }
        }
        assertEquals("https://books.google.com/x?id=1&zoom=1", search.bookCover("Duna", "Frank Herbert"))
        val q = server.takeRequest().requestUrl!!.queryParameter("q")
        assertEquals("intitle:Duna inauthor:Frank Herbert", q)
    }

    @Test
    fun falls_back_to_open_library_cover() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = when {
                request.path!!.startsWith("/g/") -> MockResponse().setBody("""{"totalItems":0}""")
                request.path!!.startsWith("/ol/search.json") -> MockResponse().setBody("""{"docs":[{"title":"Duna"},{"cover_i":12345}]}""")
                else -> MockResponse().setResponseCode(404)
            }
        }
        assertEquals("https://covers.test/b/id/12345-L.jpg", search.bookCover("Duna", "Frank Herbert"))
    }

    @Test
    fun candidates_combine_google_and_open_library() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = when {
                request.path!!.startsWith("/g/") -> MockResponse().setBody(
                    """{"items":[{"volumeInfo":{"imageLinks":{"thumbnail":"http://g/1"}}},{"volumeInfo":{}},{"volumeInfo":{"imageLinks":{"thumbnail":"http://g/2"}}}]}"""
                )
                request.path!!.startsWith("/ol/search.json") -> MockResponse().setBody("""{"docs":[{"cover_i":7},{"cover_i":8}]}""")
                else -> MockResponse().setResponseCode(404)
            }
        }
        assertEquals(
            listOf("https://g/1", "https://g/2", "https://covers.test/b/id/7-L.jpg", "https://covers.test/b/id/8-L.jpg"),
            search.candidates("Duna", "Frank Herbert"),
        )
        val q = server.takeRequest().requestUrl!!.queryParameter("q")
        assertEquals("Duna Frank Herbert", q)
    }

    @Test
    fun nothing_found_is_null() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setBody("{}")
        }
        assertNull(search.bookCover("Neexistuje", "Nikdo"))
    }

    @Test
    fun author_photo_from_open_library() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = when {
                request.path!!.startsWith("/ol/search/authors.json") -> MockResponse().setBody("""{"docs":[{"key":"OL123A","name":"Frank Herbert"}]}""")
                else -> MockResponse().setResponseCode(404)
            }
        }
        assertEquals("https://covers.test/a/olid/OL123A-M.jpg?default=false", search.authorPhoto("Frank Herbert"))
    }
}

class CoversTest {
    private val cache = object : CoverCache {
        val map = mutableMapOf<String, CachedCover>()
        override suspend fun get(key: String) = map[key]
        override suspend fun put(c: CachedCover) { map[c.key] = c }
    }
    private var bookResult: String? = null
    private var authorResult: String? = null
    var bookCalls = 0
    private val online = object : OnlineCovers {
        override suspend fun bookCover(title: String, author: String): String? { bookCalls++; return bookResult }
        override suspend fun authorPhoto(author: String): String? = authorResult
    }
    private val covers = Covers(cache, online, plexUrl = { thumb, _ -> "plex:$thumb" }, clock = { 1_000L })

    private fun book(cover: String? = null, authorThumb: String? = null) =
        Book("/b", "Duna", "Frank Herbert", cover, listOf(track("/b/1.mp3")), authorThumb = authorThumb)

    @Test
    fun plex_cover_wins() = runTest {
        bookResult = "net"
        assertEquals("plex:/t", covers.resolve(book(cover = "/t"), 300))
        assertEquals(0, bookCalls)
    }

    @Test
    fun online_cover_when_plex_has_none_and_cached() = runTest {
        bookResult = "net"
        assertEquals("net", covers.resolve(book(), 300))
        assertEquals("net", covers.resolve(book(), 300))
        assertEquals(1, bookCalls)
        assertEquals("net", covers.immediate(book(), 300))
    }

    @Test
    fun plex_author_image_when_no_cover_anywhere() = runTest {
        assertEquals("plex:/a", covers.resolve(book(authorThumb = "/a"), 300))
    }

    @Test
    fun online_author_photo_last() = runTest {
        authorResult = "author-net"
        assertEquals("author-net", covers.resolve(book(), 300))
    }

    @Test
    fun tries_title_variants_until_found() = runTest {
        val asked = mutableListOf<String>()
        val c = Covers(cache, object : OnlineCovers {
            override suspend fun bookCover(title: String, author: String): String? { asked += title; return if (title == "Hra o trůny") "got" else null }
            override suspend fun authorPhoto(author: String): String? = null
        }, plexUrl = { t, _ -> t }, clock = { 1L })
        val b = Book("/g", "Hra o trůny: Píseň ledu a ohně 1", "G. R. R. Martin", null, listOf(track("/g/1.mp3")))
        assertEquals("got", c.resolve(b, 300))
        assertEquals(listOf("Hra o trůny: Píseň ledu a ohně 1", "Hra o trůny"), asked)
    }

    @Test
    fun manual_choice_beats_plex_cover_and_can_be_cleared() = runTest {
        val b = book(cover = "/t")
        covers.setOverride(b, "chosen")
        assertEquals("chosen", covers.resolve(b, 300))
        assertEquals("chosen", covers.immediate(b, 300))
        covers.clearOverride(b)
        assertEquals("plex:/t", covers.resolve(b, 300))
        assertEquals("plex:/t", covers.immediate(b, 300))
    }

    @Test
    fun manual_choice_survives_new_instance_via_cache() = runTest {
        val b = book()
        covers.setOverride(b, "chosen")
        val fresh = Covers(cache, online, plexUrl = { thumb, _ -> "plex:$thumb" }, clock = { 1_000L })
        assertEquals("chosen", fresh.resolve(b, 300))
    }

    @Test
    fun negative_result_is_cached_too() = runTest {
        assertNull(covers.resolve(book(), 300))
        assertNull(covers.resolve(book(), 300))
        assertEquals(1, bookCalls)
    }
}
