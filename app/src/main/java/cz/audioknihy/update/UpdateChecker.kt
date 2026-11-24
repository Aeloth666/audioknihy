package cz.audioknihy.update

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Nová verze aplikace na GitHubu. */
data class AppRelease(val version: String, val apkUrl: String, val notes: String, val sizeBytes: Long)

object Versions {
    private fun parts(v: String): List<Int>? {
        val clean = v.trim().removePrefix("v").removePrefix("V")
        if (clean.isEmpty()) return null
        return clean.split('.').map { it.toIntOrNull() ?: return null }
    }

    /** Je [candidate] novější než [current]? Neznámý formát = ne. */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = parts(candidate) ?: return false
        val b = parts(current) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}

/** Zjistí poslední vydanou verzi v GitHub Releases daného repozitáře. */
class UpdateChecker(
    private val http: OkHttpClient,
    private val repo: String = REPO,
    private val apiBase: String = "https://api.github.com",
) {
    private val adapter = Moshi.Builder().build()
        .adapter<Map<String, Any?>>(Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java))

    /** Novější vydání s APK, nebo null (žádné, chyba sítě, limit GitHubu). */
    suspend fun newerThan(currentVersion: String): AppRelease? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("$apiBase/repos/$repo/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Audioknihy-Android")
                .build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@runCatching null
                val json = adapter.fromJson(r.body?.string().orEmpty()) ?: return@runCatching null
                val tag = json["tag_name"] as? String ?: return@runCatching null
                if (!Versions.isNewer(tag, currentVersion)) return@runCatching null
                val apk = (json["assets"] as? List<*>).orEmpty().mapNotNull { it as? Map<*, *> }
                    .firstOrNull { (it["name"] as? String)?.endsWith(".apk", ignoreCase = true) == true }
                    ?: return@runCatching null
                AppRelease(
                    version = tag.removePrefix("v"),
                    apkUrl = apk["browser_download_url"] as? String ?: return@runCatching null,
                    notes = json["body"] as? String ?: "",
                    sizeBytes = (apk["size"] as? Number)?.toLong() ?: 0L,
                )
            }
        }.getOrNull()
    }

    companion object {
        const val REPO = "Aeloth666/audioknihy"
    }
}
