package cz.audioknihy.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cz.audioknihy.settings.Settings
import cz.audioknihy.ui.book.BookDetailScreen
import cz.audioknihy.ui.home.HomeScreen
import cz.audioknihy.ui.login.LoginScreen
import cz.audioknihy.ui.player.PlayerScreen
import cz.audioknihy.ui.settings.SettingsScreen
import cz.audioknihy.ui.setup.SetupScreen
import kotlinx.coroutines.flow.first

object Routes {
    const val LOGIN = "login"
    const val SETUP = "setup"
    const val HOME = "home"
    const val PLAYER = "player"
    const val SETTINGS = "settings"
    const val BOOK = "book/{id}"
    fun book(id: String) = "book/" + Uri.encode(id)
}

@Composable
fun AppNav(settings: Settings, nav: NavHostController = rememberNavController()) {
    var start by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        start = when {
            settings.plexUserToken.first() == null -> Routes.LOGIN
            settings.serverUri.first() == null || settings.sectionKey.first() == null -> Routes.SETUP
            else -> Routes.HOME
        }
    }
    val startRoute = start ?: run {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    fun goClear(route: String) = nav.navigate(route) { popUpTo(0) { inclusive = true } }

    NavHost(nav, startDestination = startRoute) {
        composable(Routes.LOGIN) { LoginScreen(onDone = { goClear(Routes.SETUP) }) }
        composable(Routes.SETUP) { SetupScreen(onDone = { goClear(Routes.HOME) }) }
        composable(Routes.HOME) {
            HomeScreen(
                onOpenPlayer = { nav.navigate(Routes.PLAYER) { launchSingleTop = true } },
                onOpenBook = { nav.navigate(Routes.book(it)) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.BOOK, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
            BookDetailScreen(
                onBack = { nav.popBackStack() },
                onOpenPlayer = { nav.navigate(Routes.PLAYER) { launchSingleTop = true } },
            )
        }
        composable(Routes.PLAYER) { PlayerScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() }, onSignedOut = { goClear(Routes.LOGIN) })
        }
    }
}
