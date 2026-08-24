package com.plantinfo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.plantinfo.ui.capture.CaptureScreen
import com.plantinfo.ui.detail.DetailScreen
import com.plantinfo.ui.history.HistoryScreen
import com.plantinfo.ui.navigation.Routes
import com.plantinfo.ui.navigation.TopLevelDestination
import com.plantinfo.ui.result.ResultScreen
import com.plantinfo.ui.settings.SettingsScreen
import com.plantinfo.ui.startup.KeyProblemDialog
import com.plantinfo.ui.startup.StartupViewModel
import com.plantinfo.ui.startup.WelcomeDialog
import com.plantinfo.ui.theme.PlantInfoTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlantInfoTheme {
                PlantInfoRoot()
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

    // Messages d'ouverture : accueil du premier lancement, puis alerte sur les clés devenues
    // inutilisables. Posés au-dessus du NavHost pour rester visibles quel que soit l'onglet ouvert.
    val startup by startupViewModel.state.collectAsStateWithLifecycle()
    val openSettings = {
        navController.navigate(Routes.SETTINGS) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    if (startup.showWelcome) {
        WelcomeDialog(
            onOpenSettings = {
                startupViewModel.dismissWelcome()
                openSettings()
            },
            onDismiss = startupViewModel::dismissWelcome,
        )
    } else if (startup.keyProblems.isNotEmpty()) {
        KeyProblemDialog(
            problems = startup.keyProblems,
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
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.HISTORY) {
                HistoryScreen(
                    onOpenDetail = { id -> navController.navigate(Routes.detail(id)) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
            composable(Routes.RESULT) { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull() ?: return@composable
                ResultScreen(
                    identificationId = id,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.DETAIL) { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull() ?: return@composable
                DetailScreen(
                    identificationId = id,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
