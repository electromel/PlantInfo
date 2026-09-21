package ch.electromel.plantinfo

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ch.electromel.plantinfo.ui.capture.CaptureScreen
import ch.electromel.plantinfo.ui.components.DebugWatermark
import ch.electromel.plantinfo.ui.detail.DetailScreen
import ch.electromel.plantinfo.ui.history.HistoryScreen
import ch.electromel.plantinfo.ui.navigation.Routes
import ch.electromel.plantinfo.ui.navigation.TopLevelDestination
import ch.electromel.plantinfo.ui.result.ResultScreen
import ch.electromel.plantinfo.ui.settings.SettingsScreen
import ch.electromel.plantinfo.ui.setup.SetupFocus
import ch.electromel.plantinfo.ui.setup.SetupScreen
import ch.electromel.plantinfo.ui.startup.KeyProblemDialog
import ch.electromel.plantinfo.ui.startup.StartupViewModel
import ch.electromel.plantinfo.ui.theme.PlantInfoTheme
import ch.electromel.plantinfo.util.AppLocales
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // Langue choisie dans les Paramètres, appliquée aux ressources de l'activité. Sans effet sur
    // Android 13+, où le système a déjà localisé le contexte (voir util/AppLocales).
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocales.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlantInfoTheme {
                // Le filigrane des builds de test se superpose à toute l'application, quel que soit
                // l'écran affiché ; il ne rend rien en release.
                Box(Modifier.fillMaxSize()) {
                    PlantInfoRoot()
                    DebugWatermark()
                }
            }
        }
    }
}

@Composable
private fun PlantInfoRoot(startupViewModel: StartupViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val topLevelRoutes = TopLevelDestination.entries.map { it.route }
    val showBottomBar = currentDestination?.route in topLevelRoutes

    val startup by startupViewModel.state.collectAsStateWithLifecycle()
    val openSettings = {
        navController.navigate(Routes.SETTINGS) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val openSetup = { focus: String -> navController.navigate(Routes.setup(focus)) }

    // Premier lancement (ou assistant abandonné en cours de route) : on y emmène directement.
    // L'assistant ne se referme pas tout seul — c'est un écran, pas une fenêtre — et il ne marque le
    // parcours terminé qu'à son récapitulatif.
    LaunchedEffect(startup.showSetup) {
        if (startup.showSetup) {
            startupViewModel.onSetupOpened()
            openSetup(SetupFocus.ALL)
        }
    }

    // Clés devenues inutilisables : signalées au lancement, une fois le parcours d'accueil derrière.
    if (startup.keyProblems.isNotEmpty()) {
        KeyProblemDialog(
            problems = startup.keyProblems,
            onFix = { provider ->
                startupViewModel.dismissKeyProblems()
                openSetup(SetupFocus.provider(provider))
            },
            onOpenSettings = {
                startupViewModel.dismissKeyProblems()
                openSettings()
            },
            onDismiss = startupViewModel::dismissKeyProblems,
        )
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { dest ->
                        val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    if (selected) dest.selectedIcon else dest.icon,
                                    contentDescription = null,
                                )
                            },
                            label = { Text(stringResource(dest.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.CAPTURE,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.CAPTURE) {
                CaptureScreen(
                    onResultReady = { id -> navController.navigate(Routes.result(id)) },
                    onOpenSetup = { focus -> openSetup(focus) },
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(
                    onOpenDetail = { id -> navController.navigate(Routes.detail(id)) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onOpenSetup = { focus -> openSetup(focus) })
            }
            composable(
                Routes.SETUP,
                arguments = listOf(
                    navArgument(SetupFocus.ARG) {
                        type = NavType.StringType
                        defaultValue = SetupFocus.ALL
                    },
                ),
            ) {
                SetupScreen(
                    onExit = {
                        // Rien à dépiler si l'assistant est le premier écran atteint : on retombe
                        // alors sur l'onglet Capture plutôt que de fermer l'application.
                        if (!navController.popBackStack()) {
                            navController.navigate(Routes.CAPTURE) { launchSingleTop = true }
                        }
                    },
                )
            }
            composable(Routes.RESULT) { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull() ?: return@composable
                ResultScreen(
                    identificationId = id,
                    onBack = { navController.popBackStack() },
                    onOpenSetup = { focus -> openSetup(focus) },
                )
            }
            composable(Routes.DETAIL) { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull() ?: return@composable
                DetailScreen(
                    identificationId = id,
                    onBack = { navController.popBackStack() },
                    onOpenSetup = { focus -> openSetup(focus) },
                )
            }
        }
    }
}
