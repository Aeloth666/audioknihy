package cz.audioknihy.progress

import androidx.room.Entity
import androidx.room.PrimaryKey
import cz.audioknihy.library.Book
import cz.audioknihy.plex.ActiveServer
import cz.audioknihy.plex.PlexSource
import java.io.IOException

@Entity(tableName = "pending_reports")
data class PendingReport(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,
    val ratingKey: String,
    val state: String?,
    val timeMs: Long,
    val durationMs: Long,
    val createdAt: Long,
)

/** Hlášení přehrávání do Plexu (rozhraní kvůli testům). */
interface Reporter {
    suspend fun timeline(ratingKey: String, state: String, timeMs: Long, durationMs: Long)
    suspend fun scrobbleBook(book: Book)
    suspend fun unscrobbleBook(book: Book)
}

/**
 * Hlášení do Plexu. Zkusí poslat hned; při výpadku sítě uloží do databáze
 * a naplánuje [schedule] (WorkManager), který zavolá [flush].
 */
class ReportQueue(
    private val dao: ReportDao,
    private val active: ActiveServer,
    private val schedule: () -> Unit,
    private val clock: () -> Long,
) : Reporter {
    override suspend fun timeline(ratingKey: String, state: String, timeMs: Long, durationMs: Long) {
        val r = PendingReport(kind = KIND_TIMELINE, ratingKey = ratingKey, state = state, timeMs = timeMs, durationMs = durationMs, createdAt = clock())
        if (trySend(r)) {
            // Novější pozice dorazila: starší čekající hlášení by ji na serveru vrátila zpět.
            dao.deleteAllTimelines()
        } else {
            dao.deleteTimeline(ratingKey)
            persist(r)
        }
    }

    override suspend fun scrobbleBook(book: Book) = sendAll(book, KIND_SCROBBLE)

    override suspend fun unscrobbleBook(book: Book) = sendAll(book, KIND_UNSCROBBLE)

    private suspend fun sendAll(book: Book, kind: String) {
        val reports = book.files.map { PendingReport(kind = kind, ratingKey = it.ratingKey, state = null, timeMs = 0, durationMs = 0, createdAt = clock()) }
        var failed = false
        for (r in reports) {
            if (failed || !trySend(r)) {
                failed = true
                dao.insert(r)
            }
        }
        if (failed) schedule()
    }

    /** Pošle uložená hlášení v pořadí. Vrátí true, když je fronta prázdná. */
    suspend fun flush(): Boolean {
        // Z pozic posíláme jen tu poslední, starší by na serveru posunuly pozici zpět.
        val pending = dao.all()
        val latestTimeline = pending.filter { it.kind == KIND_TIMELINE }.maxByOrNull { it.createdAt * 1_000_000 + it.id }
        for (r in pending) {
            if (r.kind == KIND_TIMELINE && r.id != latestTimeline?.id) {
                dao.delete(r.id)
                continue
            }
            if (!trySend(r)) return false
            dao.delete(r.id)
        }
        return true
    }

    private suspend fun persist(r: PendingReport) {
        dao.insert(r)
        schedule()
    }

    private suspend fun trySend(r: PendingReport): Boolean {
        val source = active.get()?.source ?: return false
        return try {
            send(source, r)
            true
        } catch (e: IOException) {
            false
        }
    }

    private suspend fun send(s: PlexSource, r: PendingReport) = when (r.kind) {
        KIND_TIMELINE -> s.timeline(r.ratingKey, r.state ?: "stopped", r.timeMs, r.durationMs)
        KIND_SCROBBLE -> s.scrobble(r.ratingKey)
        KIND_UNSCROBBLE -> s.unscrobble(r.ratingKey)
        else -> Unit
    }

    companion object {
        const val KIND_TIMELINE = "timeline"
        const val KIND_SCROBBLE = "scrobble"
        const val KIND_UNSCROBBLE = "unscrobble"
    }
}
