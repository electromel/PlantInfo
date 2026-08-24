package ch.electromel.plantinfo.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/** Routes de navigation de l'application. */
object Routes {
    const val CAPTURE = "capture"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val RESULT = "result/{id}"
    const val DETAIL = "detail/{id}"

    fun result(id: Long) = "result/$id"
    fun detail(id: Long) = "detail/$id"
}

/** Onglets de la barre de navigation inférieure (icône pleine quand l'onglet est actif). */
enum class TopLevelDestination(
    val route: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val labelRes: Int,
) {
    CAPTURE(
        Routes.CAPTURE,
        Icons.Outlined.PhotoCamera,
        Icons.Filled.PhotoCamera,
        ch.electromel.plantinfo.R.string.nav_capture,
    ),
    HISTORY(
        Routes.HISTORY,
        Icons.Outlined.History,
        Icons.Filled.History,
        ch.electromel.plantinfo.R.string.nav_history,
    ),
    SETTINGS(
        Routes.SETTINGS,
        Icons.Outlined.Settings,
        Icons.Filled.Settings,
        ch.electromel.plantinfo.R.string.nav_settings,
    ),
}
