package cz.audioknihy.covers

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** Obaly a fotky autorů z veřejných databází (Apple Books CZ, Google Books, Open Library). */
interface OnlineCovers {
    /** Vrátí URL obalu, null = nenalezeno. Při výpadku sítě nebo služby vyhodí IOException. */
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
    private val itunesBase: String = "https://itunes.apple.com",
) : OnlineCovers {

    private val mapAdapter = Moshi.Builder().build()
        .adapter<Map<String, Any?>>(Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java))

    /**
     * Zkouší zdroje postupně. Nenalezeno všude = null (smí se uložit).
     * Když některý zdroj selhal (síť, limit 429) a nic se nenašlo = IOException (zkusit příště).
     */
    override suspend fun bookCover(title: String, author: String): String? {
        var transient: IOException? = null
        val sources: List<suspend () -> String?> = listOf(
            { apple(title, author, "ebook").firstOrNull() },
            { apple(title, author, "audiobook").firstOrNull() },
            { google("intitle:$title inauthor:$author", 5).firstOrNull() },
            { openLibrary(title, author, 5).firstOrNull() },
        )
        for (source in sources) {
            try {
                source()?.let { return it }
            } catch (e: IOException) {
                transient = e
            }
        }
        transient?.let { throw it }
        return null
    }

    override suspend fun candidates(query: String, author: String?): List<String> {
        val out = mutableListOf<String>()
        val term = listOf(query, author.orEmpty()).filter { it.isNotBlank() }.joinToString(" ")
        var anyOk = false
        var failure: IOException? = null
        suspend fun add(block: suspend () -> List<String>) {
            try {
                out += block(); anyOk = true
            } catch (e: IOException) {
                failure = e
            }
        }
        add { apple(query, author.orEmpty(), "ebook", limit = 10) }
        add { apple(query, author.orEmpty(), "audiobook", limit = 10) }
        add { google(term, 20) }
        add { openLibrary(query, author, 20) }
        if (!anyOk) failure?.let { throw it }
        return out.distinct()
    }

    /** Apple Books, český obchod: e-knihy i audioknihy; obal ve 600×600. */
    private suspend fun apple(title: String, author: String, media: String, limit: Int = 5): List<String> {
        val url = "$itunesBase/search".toHttpUrl().newBuilder()
            .addQueryParameter("term", listOf(title, author).filter { it.isNotBlank() }.joinToString(" "))
            .addQueryParameter("media", media)
            .addQueryParameter("country", "cz")
            .addQueryParameter("limit", limit.toString())
            .build()
        val results = getJson(url.toString())?.get("results") as? List<*> ?: return emptyList()
        return results.mapNotNull { r ->
            ((r as? Map<*, *>)?.get("artworkUrl100") as? String)?.replace("100x100bb", "600x600bb")
        }
    }

    private suspend fun google(q: String, max: Int): List<String> {
        val url = "$googleBase/books/v1/volumes".toHttpUrl().newBuilder()
            .addQueryParameter("q", q)
            .addQueryParameter("maxResults", max.toString())
            .addQueryParameter("printType", "books")
            .build()
        val items = getJson(url.toString())?.get("items") as? List<*> ?: return emptyList()
        return items.mapNotNull { item ->
            val links = ((item as? Map<*, *>)?.get("volumeInfo") as? Map<*, *>)?.get("imageLinks") as? Map<*, *>
            ((links?.get("thumbnail") ?: links?.get("smallThumbnail")) as? String)
                ?.replace("http://", "https://")?.replace("&edge=curl", "")
        }
    }

    private suspend fun openLibrary(title: String, author: String?, limit: Int): List<String> {
        val url = "$openLibraryBase/search.json".toHttpUrl().newBuilder()
            .addQueryParameter("title", title)
            .apply { if (!author.isNullOrBlank()) addQueryParameter("author", author) }
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("fields", "cover_i,title")
            .build()
        val docs = getJson(url.toString())?.get("docs") as? List<*> ?: return emptyList()
        return docs.mapNotNull { d -> ((d as? Map<*, *>)?.get("cover_i") as? Number)?.let { "$coversBase/b/id/${it.toLong()}-L.jpg" } }
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

    /**
     * null = nenalezeno (404, prázdná nebo nečitelná odpověď).
     * IOException = síť, překročený limit (429) nebo chyba serveru (5xx): nic neukládat.
     */
    private suspend fun getJson(url: String): Map<String, Any?>? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", "Audioknihy (Android)").build()
        http.newCall(req).execute().use { r ->
            if (r.code == 403 || r.code == 429 || r.code >= 500) throw IOException("HTTP ${r.code}")
            if (!r.isSuccessful) return@withContext null
            val body = r.body?.string() ?: return@withContext null
            runCatching { mapAdapter.fromJson(body) }.getOrNull()
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
