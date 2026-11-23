package cz.audioknihy.covers

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** Obaly a fotky autorů z veřejných databází (Google Books, Open Library). */
interface OnlineCovers {
    /** Vrátí URL obalu, null = nenalezeno. Při výpadku sítě vyhodí IOException. */
    suspend fun bookCover(title: String, author: String): String?
    suspend fun authorPhoto(author: String): String?
    /** Více kandidátů pro ruční výběr obalu. */
    suspend fun candidates(query: String, author: String?): List<String> = emptyList()
}

class CoverSearch(
    private val http: OkHttpClient,
    private val googleBase: String = "https://www.googleapis.com",
    private val openLibraryBase: String = "https://openlibrary.org",
    private val coversBase: String = "https://covers.openlibrary.org",
) : OnlineCovers {

    private val mapAdapter = Moshi.Builder().build()
        .adapter<Map<String, Any?>>(Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java))

    override suspend fun bookCover(title: String, author: String): String? {
        google(title, author)?.let { return it }
        return openLibrary(title, author)
    }

    private suspend fun google(title: String, author: String): String? {
        val url = "$googleBase/books/v1/volumes".toHttpUrl().newBuilder()
            .addQueryParameter("q", "intitle:$title inauthor:$author")
            .addQueryParameter("maxResults", "5")
            .addQueryParameter("printType", "books")
            .build()
        val json = getJson(url.toString()) ?: return null
        val items = json["items"] as? List<*> ?: return null
        for (item in items) {
            val links = ((item as? Map<*, *>)?.get("volumeInfo") as? Map<*, *>)?.get("imageLinks") as? Map<*, *>
            val thumb = (links?.get("thumbnail") ?: links?.get("smallThumbnail")) as? String ?: continue
            return thumb.replace("http://", "https://").replace("&edge=curl", "")
        }
        return null
    }

    private suspend fun openLibrary(title: String, author: String): String? {
        val url = "$openLibraryBase/search.json".toHttpUrl().newBuilder()
            .addQueryParameter("title", title)
            .addQueryParameter("author", author)
            .addQueryParameter("limit", "5")
            .addQueryParameter("fields", "cover_i,title")
            .build()
        val docs = getJson(url.toString())?.get("docs") as? List<*> ?: return null
        val id = docs.firstNotNullOfOrNull { (it as? Map<*, *>)?.get("cover_i") as? Number } ?: return null
        return "$coversBase/b/id/${id.toLong()}-L.jpg"
    }

    override suspend fun candidates(query: String, author: String?): List<String> {
        val out = mutableListOf<String>()
        val g = "$googleBase/books/v1/volumes".toHttpUrl().newBuilder()
            .addQueryParameter("q", listOf(query, author.orEmpty()).filter { it.isNotBlank() }.joinToString(" "))
            .addQueryParameter("maxResults", "20")
            .addQueryParameter("printType", "books")
            .build()
        (getJson(g.toString())?.get("items") as? List<*>).orEmpty().forEach { item ->
            val links = ((item as? Map<*, *>)?.get("volumeInfo") as? Map<*, *>)?.get("imageLinks") as? Map<*, *>
            ((links?.get("thumbnail") ?: links?.get("smallThumbnail")) as? String)
                ?.let { out += it.replace("http://", "https://").replace("&edge=curl", "") }
        }
        val ol = "$openLibraryBase/search.json".toHttpUrl().newBuilder()
            .addQueryParameter("title", query)
            .apply { if (!author.isNullOrBlank()) addQueryParameter("author", author) }
            .addQueryParameter("limit", "20")
            .addQueryParameter("fields", "cover_i,title")
            .build()
        (getJson(ol.toString())?.get("docs") as? List<*>).orEmpty().forEach { d ->
            ((d as? Map<*, *>)?.get("cover_i") as? Number)?.let { out += "$coversBase/b/id/${it.toLong()}-L.jpg" }
        }
        return out.distinct()
    }

    override suspend fun authorPhoto(author: String): String? {
        val url = "$openLibraryBase/search/authors.json".toHttpUrl().newBuilder()
            .addQueryParameter("q", author)
            .addQueryParameter("limit", "1")
            .build()
        val docs = getJson(url.toString())?.get("docs") as? List<*> ?: return null
        val key = (docs.firstOrNull() as? Map<*, *>)?.get("key") as? String ?: return null
        return "$coversBase/a/olid/$key-M.jpg?default=false"
    }

    /** null = služba odpověděla chybou nebo prázdně (nenalezeno); IOException = síť. */
    private suspend fun getJson(url: String): Map<String, Any?>? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", "Audioknihy (Android)").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return@withContext null
            val body = r.body?.string() ?: return@withContext null
            try {
                mapAdapter.fromJson(body)
            } catch (e: Exception) {
                if (e is IOException) null else null
            }
        }
    }

    companion object {
        private val PARENS = Regex("\\s*\\([^)]*\\)")
        private val BOOK_NO = Regex("^kniha\\s*\\d+\\.?\\s*", RegexOption.IGNORE_CASE)
        private val LEADING_NO = Regex("^\\d+\\s*[.\\-–]?\\s*")
        private val INNER_NO = Regex("\\s+[-–]\\s+\\d+\\s+[-–]\\s+")
        private val SPACES = Regex("\\s+")

        /**
         * Názvy k vyzkoušení: nejdřív celý vyčištěný název, pak části kolem dvojtečky
         * (před, za) a kolem pomlčky (poslední, první), bez čísel dílů.
         */
        fun searchVariants(title: String, author: String? = null): List<String> {
            val full = cleanTitle(title, author)
            val out = mutableListOf(full)
            if (':' in full) {
                val (a, b) = full.split(':', limit = 2)
                out += cleanTitle(a); out += cleanTitle(b)
            }
            val dash = Regex("""\s+[-–]\s+""")
            if (dash.containsMatchIn(full)) {
                val segs = full.split(dash)
                out += cleanTitle(segs.last()); out += cleanTitle(segs.first())
            }
            return out.map { it.trim() }.filter { it.length >= 3 }.distinct()
        }

        /** Název pro hledání: bez čísel dílů, jména autora na začátku a „(čte …)“. */
        fun cleanTitle(title: String, author: String? = null): String {
            var t = PARENS.replace(title, "")
            if (!author.isNullOrBlank() && t.startsWith(author, ignoreCase = true)) {
                t = t.substring(author.length).trimStart(' ', '-', '–')
            }
            t = BOOK_NO.replace(t, "")
            repeat(2) { t = LEADING_NO.replace(t.trim(), "") }
            t = INNER_NO.replace(t, " ")
            return SPACES.replace(t, " ").trim().trimEnd('-', '–', ' ')
        }
    }
}
