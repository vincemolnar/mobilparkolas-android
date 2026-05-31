package hu.mobilparkolas.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import hu.mobilparkolas.MobilParkolasApp
import hu.mobilparkolas.di.ServiceLocator
import hu.mobilparkolas.ui.active.ActiveScreen
import hu.mobilparkolas.ui.history.HistoryScreen
import hu.mobilparkolas.ui.main.MainScreen
import hu.mobilparkolas.ui.main.MainViewModel
import hu.mobilparkolas.ui.settings.SettingsScreen
import hu.mobilparkolas.ui.theme.MobilParkolasTheme

object Routes {
    const val MAIN = "main"
    const val SETTINGS = "settings"
    const val ACTIVE = "active"
    const val HISTORY = "history"
}

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels {
        MainViewModel.Factory((application as MobilParkolasApp).locator)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The system splash just provides a quick branded flash; the in-app Compose
        // loading screen (with logo + status) covers the actual DB load / locating.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Cold start from the widget: arm auto quick-park; the permission/locate flow runs it.
        if (intent?.getBooleanExtra(EXTRA_QUICK_PARK, false) == true) {
            mainViewModel.requestAutoQuickPark(carIdFrom(intent))
        }

        val locator = (application as MobilParkolasApp).locator
        setContent {
            MobilParkolasTheme {
                val navController = rememberNavController()
                AppNavHost(navController, locator, mainViewModel)
            }
        }
    }

    // Warm start from the widget: arm and immediately re-run detection.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_QUICK_PARK, false)) {
            mainViewModel.requestAutoQuickPark(carIdFrom(intent))
            mainViewModel.locate()
        }
    }

    private fun carIdFrom(intent: Intent?): Long? =
        if (intent?.hasExtra(EXTRA_CAR_ID) == true) intent.getLongExtra(EXTRA_CAR_ID, -1L) else null

    companion object {
        const val EXTRA_QUICK_PARK = "hu.mobilparkolas.QUICK_PARK"
        const val EXTRA_CAR_ID = "hu.mobilparkolas.CAR_ID"
    }
}

@Composable
fun AppNavHost(navController: NavHostController, locator: ServiceLocator, mainViewModel: MainViewModel) {
    // If reopened while a parking session is active (e.g. from the ongoing notification),
    // jump straight to the Active screen.
    LaunchedEffect(Unit) {
        if (locator.parkingRepository.getActive() != null) {
            navController.navigate(Routes.ACTIVE)
        }
    }
    NavHost(navController = navController, startDestination = Routes.MAIN) {
        composable(Routes.MAIN) {
            MainScreen(
                vm = mainViewModel,
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenHistory = { navController.navigate(Routes.HISTORY) },
                onParkingStarted = {
                    navController.navigate(Routes.ACTIVE) { popUpTo(Routes.MAIN) }
                },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(locator = locator, onBack = { navController.popBackStack() })
        }
        composable(Routes.ACTIVE) {
            ActiveScreen(locator = locator, onBack = { navController.popBackStack() })
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                locator = locator,
                onBack = { navController.popBackStack() },
                onOpenActive = { navController.navigate(Routes.ACTIVE) },
            )
        }
    }
}
