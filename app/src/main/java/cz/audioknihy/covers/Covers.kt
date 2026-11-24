package cz.audioknihy.covers

import androidx.room.Entity
import androidx.room.PrimaryKey
import cz.audioknihy.library.Book
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Výsledek hledání obalu uložený v telefonu (i „nenalezeno“, aby se nehledalo pořád). */
@Entity(tableName = "covers")
data class CachedCover(@PrimaryKey val key: String, val url: String?, val checkedAt: Long)

interface CoverCache {
    suspend fun get(key: String): CachedCover?
    suspend fun put(c: CachedCover)
}

/**
 * Obal knihy v pořadí: obal z Plexu → obal z internetu → obrázek autora z Plexu
 * → fotka autora z internetu → nic (zástupná ikona).
 */
class Covers(
    private val cache: CoverCache,
    private val online: OnlineCovers,
    private val plexUrl: (thumb: String, sizePx: Int) -> String?,
    private val clock: () -> Long,
) {
    private val memory = ConcurrentHashMap<String, String>()
    private val overrides = ConcurrentHashMap<String, String>()
    private val _version = kotlinx.coroutines.flow.MutableStateFlow(0)
    /** Mění se po ručním výběru obalu (obrazovky si pak obal načtou znovu). */
    val version: kotlinx.coroutines.flow.StateFlow<Int> = _version

    suspend fun candidates(query: String, author: String?): List<String> = online.candidates(query, author)

    /** Ručně vybraný obal: má přednost i před obalem z Plexu. */
    suspend fun setOverride(book: Book, url: String) {
        cache.put(CachedCover(OVERRIDE + book.id, url, clock()))
        overrides[book.id] = url
        memory.remove(book.id)
        _version.value++
    }

    suspend fun clearOverride(book: Book) {
        cache.put(CachedCover(OVERRIDE + book.id, null, clock()))
        overrides.remove(book.id)
        memory.remove(book.id)
        _version.value++
    }

    /** Co je k dispozici hned, bez sítě (pro první vykreslení a notifikaci). */
    fun immediate(book: Book, sizePx: Int): String? =
        overrides[book.id] ?: book.coverThumb?.let { plexUrl(it, sizePx) } ?: memory[book.id]

    suspend fun resolve(book: Book, sizePx: Int): String? {
        overrides[book.id]?.let { return it }
        cache.get(OVERRIDE + book.id)?.url?.let {
            overrides[book.id] = it
            return it
        }
        book.coverThumb?.let { return plexUrl(it, sizePx) }
        memory[book.id]?.let { return it }
        val url = cachedOrFetch("book4:${book.id}") {
            CoverSearch.searchVariants(book.title, book.author).firstNotNullOfOrNull { online.bookCover(it, book.author) }
        } ?: book.authorThumb?.let { plexUrl(it, sizePx) }
            ?: cachedOrFetch("author:${book.author}") { online.authorPhoto(book.author) }
        if (url != null) memory[book.id] = url
        return url
    }

    private suspend fun cachedOrFetch(key: String, fetch: suspend () -> String?): String? {
        val hit = cache.get(key)
        if (hit != null && (hit.url != null || clock() - hit.checkedAt < RETRY_MS)) return hit.url
        val url = try {
            fetch()
        } catch (e: IOException) {
            return null // výpadek sítě: neukládat, zkusit příště
        }
        cache.put(CachedCover(key, url, clock()))
        return url
    }

    companion object {
        /** „Nenalezeno“ se zkusí znovu po 30 dnech. */
        private const val OVERRIDE = "override:"
        const val RETRY_MS = 30L * 24 * 3600 * 1000
    }
}
