package ch.electromel.plantinfo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.electromel.plantinfo.ui.theme.DangerContainer
import ch.electromel.plantinfo.ui.theme.DangerContainerDark
import ch.electromel.plantinfo.ui.theme.DangerOnContainer
import ch.electromel.plantinfo.ui.theme.DangerOnContainerDark
import ch.electromel.plantinfo.ui.theme.ScoreHigh
import ch.electromel.plantinfo.ui.theme.ScoreHighOnDark
import ch.electromel.plantinfo.ui.theme.ScoreLow
import ch.electromel.plantinfo.ui.theme.ScoreLowOnDark
import ch.electromel.plantinfo.ui.theme.ScoreMedium
import ch.electromel.plantinfo.ui.theme.ScoreMediumOnDark
import ch.electromel.plantinfo.ui.theme.ScoreMediumOnLight
import ch.electromel.plantinfo.ui.theme.WarningContainer
import ch.electromel.plantinfo.ui.theme.WarningContainerDark
import ch.electromel.plantinfo.ui.theme.WarningOnContainer
import ch.electromel.plantinfo.ui.theme.WarningOnContainerDark

/** Couleur de fond de badge selon le niveau de confiance (saturée, texte adapté par [ScoreBadge]). */
fun scoreColor(score: Int): Color = when {
    score >= 75 -> ScoreHigh
    score >= 50 -> ScoreMedium
    else -> ScoreLow
}

/** Couleur de texte de score lisible sur la surface du thème courant (clair ou sombre). */
@Composable
fun scoreTextColor(score: Int): Color {
    val dark = isSystemInDarkTheme()
    return when {
        score >= 75 -> if (dark) ScoreHighOnDark else ScoreHigh
        score >= 50 -> if (dark) ScoreMediumOnDark else ScoreMediumOnLight
        else -> if (dark) ScoreLowOnDark else ScoreLow
    }
}

/** Badge de score d'exactitude sur 100, coloré selon le niveau de confiance (§2.4). */
@Composable
fun ScoreBadge(score: Int, large: Boolean = false, modifier: Modifier = Modifier) {
    val color = scoreColor(score)
    // Le jaune « confiance moyenne » exige un texte sombre pour rester lisible (contraste AA).
    val content = if (color == ScoreMedium) Color(0xFF3E2E00) else Color.White
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = if (large) 20.dp else 12.dp, vertical = if (large) 10.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$score",
            color = content,
            fontWeight = FontWeight.Bold,
            fontSize = if (large) 30.sp else 18.sp,
        )
        Text(
            text = "/100",
            color = content.copy(alpha = 0.85f),
            fontWeight = FontWeight.SemiBold,
            fontSize = if (large) 16.sp else 12.sp,
            modifier = Modifier.padding(start = 2.dp, bottom = if (large) 3.dp else 1.dp),
        )
    }
}

enum class BannerSeverity { WARNING, DANGER }

/** Bannière d'avertissement contrastée (champignons, espèces protégées, divergence de sources). */
@Composable
fun WarningBanner(
    text: String,
    icon: ImageVector,
    severity: BannerSeverity = BannerSeverity.WARNING,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val (bg, fg) = when (severity) {
        BannerSeverity.WARNING ->
            if (dark) WarningContainerDark to WarningOnContainerDark
            else WarningContainer to WarningOnContainer
        BannerSeverity.DANGER ->
            if (dark) DangerContainerDark to DangerOnContainerDark
            else DangerContainer to DangerOnContainer
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = fg)
        Text(text = text, color = fg, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Carte de section titrée réutilisée dans les fiches résultat/détail. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}
