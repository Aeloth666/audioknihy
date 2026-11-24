package cz.audioknihy.ui

import android.Manifest
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import cz.audioknihy.playback.PlaybackService
import cz.audioknihy.settings.Settings
import cz.audioknihy.ui.theme.AudioknihyTheme
import cz.audioknihy.ui.theme.AppTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settings: Settings
    @Inject lateinit var covers: cz.audioknihy.covers.Covers

    private var controller: ListenableFuture<MediaController>? = null

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            val themeName by settings.theme.collectAsState(initial = AppTheme.CLOTH.name)
            val theme = AppTheme.from(themeName)
            LaunchedEffect(theme) {
                val style = if (theme.palette.dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            AudioknihyTheme(theme) {
                val designName by settings.design.collectAsState(initial = cz.audioknihy.ui.theme.AppDesign.RIBBON.name)
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalCovers provides covers,
                    cz.audioknihy.ui.theme.LocalDesign provides cz.audioknihy.ui.theme.AppDesign.from(designName),
                ) {
                    Surface { AppNav(settings) }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Připojení k přehrávací službě: díky tomu běží notifikace a ovládání z obrazovky zámku.
        controller = MediaController.Builder(this, SessionToken(this, ComponentName(this, PlaybackService::class.java))).buildAsync()
    }

    override fun onStop() {
        controller?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onStop()
    }
}
