package cz.audioknihy.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Uložené nastavení a přihlašovací údaje k Plexu. */
class Settings(context: Context) {
    private val store = context.applicationContext.dataStore

    private object K {
        val skipBack = longPreferencesKey("skip_back_ms")
        val skipForward = longPreferencesKey("skip_forward_ms")
        val resumeRewind = longPreferencesKey("resume_rewind_ms")
        val syncToServer = booleanPreferencesKey("sync_position_to_server")
        val userToken = stringPreferencesKey("plex_user_token")
        val serverId = stringPreferencesKey("server_id")
        val serverName = stringPreferencesKey("server_name")
        val serverUri = stringPreferencesKey("server_uri")
        val serverToken = stringPreferencesKey("server_token")
        val sectionKey = stringPreferencesKey("section_key")
        val sectionTitle = stringPreferencesKey("section_title")
        val inProgressSort = stringPreferencesKey("in_progress_sort")
        val theme = stringPreferencesKey("theme")
        val viz = stringPreferencesKey("visualizer")
        val design = stringPreferencesKey("design")
    }

    val skipBackMs: Flow<Long> = store.data.map { it[K.skipBack] ?: DEFAULT_SKIP_BACK }
    val skipForwardMs: Flow<Long> = store.data.map { it[K.skipForward] ?: DEFAULT_SKIP_FORWARD }
    val resumeRewindMs: Flow<Long> = store.data.map { it[K.resumeRewind] ?: DEFAULT_RESUME_REWIND }
    val syncPositionToServer: Flow<Boolean> = store.data.map { it[K.syncToServer] ?: true }

    val plexUserToken: Flow<String?> = store.data.map { it[K.userToken] }
    val serverUri: Flow<String?> = store.data.map { it[K.serverUri] }
    val serverToken: Flow<String?> = store.data.map { it[K.serverToken] }
    val serverId: Flow<String?> = store.data.map { it[K.serverId] }
    val serverName: Flow<String?> = store.data.map { it[K.serverName] }
    val sectionKey: Flow<String?> = store.data.map { it[K.sectionKey] }
    val inProgressSort: Flow<String?> = store.data.map { it[K.inProgressSort] }

    suspend fun setInProgressSort(v: String) = store.edit { it[K.inProgressSort] = v }

    val theme: Flow<String?> = store.data.map { it[K.theme] }
    suspend fun setTheme(v: String) = store.edit { it[K.theme] = v }

    val visualizer: Flow<String?> = store.data.map { it[K.viz] }
    suspend fun setVisualizer(v: String) = store.edit { it[K.viz] = v }

    val design: Flow<String?> = store.data.map { it[K.design] }
    suspend fun setDesign(v: String) = store.edit { it[K.design] = v }

    private val prefs = context.applicationContext.getSharedPreferences("device", Context.MODE_PRIVATE)

    /** Trvalé ID zařízení pro Plex (čte se synchronně, bez čekání na DataStore). */
    fun clientId(): String {
        prefs.getString("client_id", null)?.let { return it }
        val id = UUID.randomUUID().toString()
        prefs.edit().putString("client_id", id).apply()
        return id
    }

    suspend fun setSkipBackMs(v: Long) = store.edit { it[K.skipBack] = v }
    suspend fun setSkipForwardMs(v: Long) = store.edit { it[K.skipForward] = v }
    suspend fun setResumeRewindMs(v: Long) = store.edit { it[K.resumeRewind] = v }
    suspend fun setSyncPositionToServer(v: Boolean) = store.edit { it[K.syncToServer] = v }
    suspend fun setUserToken(v: String) = store.edit { it[K.userToken] = v }

    suspend fun setServer(id: String, name: String, uri: String, token: String) = store.edit {
        it[K.serverId] = id; it[K.serverName] = name; it[K.serverUri] = uri; it[K.serverToken] = token
    }

    suspend fun setServerUri(uri: String) = store.edit { it[K.serverUri] = uri }
    suspend fun setSectionKey(v: String) = store.edit { it[K.sectionKey] = v }
    suspend fun setSection(key: String, title: String) = store.edit { it[K.sectionKey] = key; it[K.sectionTitle] = title }
    val sectionTitle: Flow<String?> = store.data.map { it[K.sectionTitle] }

    suspend fun signOut() = store.edit {
        it.remove(K.userToken); it.remove(K.serverId); it.remove(K.serverName)
        it.remove(K.serverUri); it.remove(K.serverToken); it.remove(K.sectionKey)
    }

    companion object {
        const val DEFAULT_SKIP_BACK = 15_000L
        const val DEFAULT_SKIP_FORWARD = 30_000L
        const val DEFAULT_RESUME_REWIND = 10_000L
    }
}
