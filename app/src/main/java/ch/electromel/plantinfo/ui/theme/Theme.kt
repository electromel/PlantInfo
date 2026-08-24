package ch.electromel.plantinfo.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Schémas complets : sans surcharge, les slots non définis (containers, surfaceVariant, outline…)
// retombent sur la palette violette Material de référence et cassent l'identité « nature ».
private val LightColors = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = OnPrimaryLight,
    primaryContainer = GreenPrimaryContainer,
    onPrimaryContainer = OnGreenPrimaryContainer,
    secondary = GreenSecondary,
    onSecondary = OnSecondaryLight,
    secondaryContainer = GreenSecondaryContainer,
    onSecondaryContainer = OnGreenSecondaryContainer,
    tertiary = BrownTertiary,
    onTertiary = OnTertiaryLight,
    tertiaryContainer = BrownTertiaryContainer,
    onTertiaryContainer = OnBrownTertiaryContainer,
    background = LightBackground,
    onBackground = OnLightBackground,
    surface = LightSurface,
    onSurface = OnLightSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = OnLightSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
)

private val DarkColors = darkColorScheme(
    primary = GreenPrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = GreenPrimaryContainerDark,
    onPrimaryContainer = OnGreenPrimaryContainerDark,
    secondary = GreenSecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = GreenSecondaryContainerDark,
    onSecondaryContainer = OnGreenSecondaryContainerDark,
    tertiary = BrownTertiaryDark,
    onTertiary = OnTertiaryDark,
    tertiaryContainer = BrownTertiaryContainerDark,
    onTertiaryContainer = OnBrownTertiaryContainerDark,
    background = DarkBackground,
    onBackground = OnDarkBackground,
    surface = DarkSurface,
    onSurface = OnDarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = OnDarkSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
)

@Composable
fun PlantInfoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
