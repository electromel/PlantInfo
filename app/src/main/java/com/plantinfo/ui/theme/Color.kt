package com.plantinfo.ui.theme

import androidx.compose.ui.graphics.Color

// Palette « nature » à fort contraste pour un usage en extérieur (§5).
// Tons dérivés d'un vert forêt (primaire), vert olive (secondaire) et brun terre (tertiaire).

// --- Thème clair ---
val GreenPrimary = Color(0xFF2E7D32)
val OnPrimaryLight = Color(0xFFFFFFFF)
val GreenPrimaryContainer = Color(0xFFB7F0B7)
val OnGreenPrimaryContainer = Color(0xFF00210A)

val GreenSecondary = Color(0xFF558B2F)
val OnSecondaryLight = Color(0xFFFFFFFF)
val GreenSecondaryContainer = Color(0xFFD7E8CB)
val OnGreenSecondaryContainer = Color(0xFF131F0D)

val BrownTertiary = Color(0xFF6D5348)
val OnTertiaryLight = Color(0xFFFFFFFF)
val BrownTertiaryContainer = Color(0xFFF4DED4)
val OnBrownTertiaryContainer = Color(0xFF261510)

val LightBackground = Color(0xFFF8FBF2)
val OnLightBackground = Color(0xFF191D17)
val LightSurface = Color(0xFFFFFFFF)
val OnLightSurface = Color(0xFF191D17)
val LightSurfaceVariant = Color(0xFFE0E4D6)
val OnLightSurfaceVariant = Color(0xFF43483E)
val LightOutline = Color(0xFF74796D)
val LightOutlineVariant = Color(0xFFC4C8BB)

// --- Thème sombre ---
val GreenPrimaryDark = Color(0xFFA5D6A7)
val OnPrimaryDark = Color(0xFF0A390F)
val GreenPrimaryContainerDark = Color(0xFF1B5E20)
val OnGreenPrimaryContainerDark = Color(0xFFC0EFC1)

val GreenSecondaryDark = Color(0xFFBBCCA9)
val OnSecondaryDark = Color(0xFF27341B)
val GreenSecondaryContainerDark = Color(0xFF3D4B30)
val OnGreenSecondaryContainerDark = Color(0xFFD7E8C4)

val BrownTertiaryDark = Color(0xFFDABFB1)
val OnTertiaryDark = Color(0xFF3C2A21)
val BrownTertiaryContainerDark = Color(0xFF543F36)
val OnBrownTertiaryContainerDark = Color(0xFFF7DFD2)

val DarkBackground = Color(0xFF101410)
val OnDarkBackground = Color(0xFFE1E4DB)
val DarkSurface = Color(0xFF1B211B)
val OnDarkSurface = Color(0xFFE1E4DB)
val DarkSurfaceVariant = Color(0xFF43483E)
val OnDarkSurfaceVariant = Color(0xFFC4C8BB)
val DarkOutline = Color(0xFF8E9387)
val DarkOutlineVariant = Color(0xFF43483E)

// --- Couleurs sémantiques pour les scores et alertes ---
// Fonds de badge (saturés, identiques dans les deux thèmes ; le texte s'adapte).
val ScoreHigh = Color(0xFF2E7D32)
val ScoreMedium = Color(0xFFF9A825)
val ScoreLow = Color(0xFFC62828)

// Variantes « texte sur fond de thème » (lisibles sur surface claire / sombre).
val ScoreHighOnDark = Color(0xFFA5D6A7)
val ScoreMediumOnDark = Color(0xFFFFD54F)
val ScoreLowOnDark = Color(0xFFEF9A9A)
val ScoreMediumOnLight = Color(0xFF9C6F00)

// Bannières d'avertissement, avec variantes pour le thème sombre.
val WarningContainer = Color(0xFFFFF3CD)
val WarningOnContainer = Color(0xFF5F4B00)
val WarningContainerDark = Color(0xFF3E3200)
val WarningOnContainerDark = Color(0xFFF2D77A)
val DangerContainer = Color(0xFFFDECEA)
val DangerOnContainer = Color(0xFF7F1D1D)
val DangerContainerDark = Color(0xFF4A211C)
val DangerOnContainerDark = Color(0xFFFFB4AB)
