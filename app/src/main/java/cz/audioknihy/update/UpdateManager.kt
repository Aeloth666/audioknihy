package cz.audioknihy.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.core.content.FileProvider
import cz.audioknihy.BuildConfig
import cz.audioknihy.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

sealed interface UpdateState {
    data object Idle : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data class Downloading(val release: AppRelease, val progress: Float) : UpdateState
    data class Ready(val release: AppRelease, val file: File) : UpdateState
    data class Failed(val release: AppRelease) : UpdateState
}

/**
 * Aktualizace z GitHub Releases: kontrola (nejvýš jednou za [CHECK_INTERVAL_MS]),
 * stažení APK a předání systémovému instalátoru (instalaci uživatel potvrdí).
 */
class UpdateManager(
    private val context: Context,
    private val http: OkHttpClient,
    private val settings: Settings,
    private val checker: UpdateChecker,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    /** Vrátí true, když je k dispozici novější verze. */
    suspend fun check(force: Boolean = false): Boolean {
        if (!force) {
            if (!settings.autoUpdate.first()) return false
            if (System.currentTimeMillis() - settings.lastUpdateCheck.first() < CHECK_INTERVAL_MS) {
                return _state.value is UpdateState.Available
            }
        }
        val release = checker.newerThan(currentVersion)
        settings.setLastUpdateCheck(System.currentTimeMillis())
        if (release != null && release.version != settings.dismissedVersion.first().takeIf { !force }) {
            _state.value = UpdateState.Available(release)
            return true
        }
        if (_state.value !is UpdateState.Downloading) _state.value = UpdateState.Idle
        return false
    }

    suspend fun dismiss() {
        (_state.value as? UpdateState.Available)?.let { settings.setDismissedVersion(it.release.version) }
        _state.value = UpdateState.Idle
    }

    /** Stáhne APK do mezipaměti a spustí instalaci. */
    suspend fun downloadAndInstall() {
        val release = when (val s = _state.value) {
            is UpdateState.Available -> s.release
            is UpdateState.Failed -> s.release
            is UpdateState.Ready -> { install(s.file); return }
            else -> return
        }
        _state.value = UpdateState.Downloading(release, 0f)
        val file = try {
            download(release)
        } catch (e: IOException) {
            _state.value = UpdateState.Failed(release)
            return
        }
        _state.value = UpdateState.Ready(release, file)
        install(file)
    }

    private suspend fun download(release: AppRelease): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "Audioknihy-${release.version}.apk")
        val req = Request.Builder().url(release.apkUrl).header("User-Agent", "Audioknihy-Android").build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val body = r.body ?: throw IOException("prázdná odpověď")
            val total = body.contentLength().takeIf { it > 0 } ?: release.sizeBytes
            body.byteStream().use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) _state.value = UpdateState.Downloading(release, (done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        target
    }

    /** Smí aplikace instalovat? Na Androidu 8+ to uživatel povoluje jednou pro tuto aplikaci. */
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission() {
        val intent = Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun install(file: File) {
        if (!canInstall()) {
            openInstallPermission()
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    companion object {
        const val CHECK_INTERVAL_MS = 6L * 3600 * 1000
    }
}
