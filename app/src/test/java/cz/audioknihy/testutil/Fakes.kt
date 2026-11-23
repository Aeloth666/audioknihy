package cz.audioknihy.testutil

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cz.audioknihy.plex.ActiveServer
import cz.audioknihy.plex.Connected
import cz.audioknihy.plex.PlexSource
import cz.audioknihy.plex.PlexTrack
import cz.audioknihy.progress.AppDatabase
import java.io.IOException

fun inMemoryDb(): AppDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Application>(), AppDatabase::class.java)
        .allowMainThreadQueries()
        .build()

/** Falešný Plex server: zaznamenává volání, umí selhat. */
class FakeSource(var tracks: List<PlexTrack> = emptyList()) : PlexSource {
    var failing = false
    val calls = mutableListOf<String>()
    var heads: Map<String, ByteArray> = emptyMap()

    private fun maybeFail() { if (failing) throw IOException("offline") }

    override suspend fun allTracks(sectionKey: String): List<PlexTrack> { maybeFail(); return tracks }
    override suspend fun tracksMetadata(ratingKeys: List<String>): List<PlexTrack> {
        maybeFail(); return tracks.filter { it.ratingKey in ratingKeys }
    }
    override suspend fun timeline(ratingKey: String, state: String, timeMs: Long, durationMs: Long) {
        maybeFail(); calls += "timeline:$ratingKey:$state:$timeMs"
    }
    override suspend fun scrobble(ratingKey: String) { maybeFail(); calls += "scrobble:$ratingKey" }
    override suspend fun unscrobble(ratingKey: String) { maybeFail(); calls += "unscrobble:$ratingKey" }
    override suspend fun headRange(partKey: String, start: Long, length: Int): ByteArray {
        maybeFail()
        val all = heads[partKey] ?: ByteArray(0)
        val s = start.toInt().coerceAtMost(all.size)
        return all.copyOfRange(s, (s + length).coerceAtMost(all.size))
    }
    override fun streamUrl(partKey: String) = "http://pms$partKey"
    override fun imageUrl(thumb: String, sizePx: Int) = "http://pms$thumb?s=$sizePx"
}

class FakeActiveServer(val source: FakeSource?, val section: String = "5") : ActiveServer {
    override suspend fun get(): Connected? = source?.let { Connected(it, section) }
}
