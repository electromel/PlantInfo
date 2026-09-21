package ch.electromel.plantinfo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.electromel.plantinfo.BuildConfig

/**
 * Filigrane des builds de test : version, numéro de build et horodatage, en surimpression.
 *
 * Deux installations de test se ressemblent trait pour trait ; sans cette étiquette, rien ne dit
 * laquelle est sur le téléphone. Le numéro de version ne suffit pas non plus — il ne change pas
 * entre deux builds du même jour, d'où l'horodatage (`BuildConfig.BUILD_STAMP`).
 *
 * Ne s'affiche **que** dans un build debug : la garde `BuildConfig.DEBUG` est ce qui garantit que le
 * filigrane ne partira jamais dans l'application publiée.
 *
 * Volontairement en bas à gauche : les barres d'actions vivent en haut, et l'étiquette y masquerait
 * un bouton. Purement décoratif, elle n'intercepte aucun geste — un appui la traverse et atteint ce
 * qu'elle recouvre.
 */
@Composable
fun BoxScope.DebugWatermark() {
    if (!BuildConfig.DEBUG) return

    val stamp = BuildConfig.BUILD_STAMP.takeIf { it.isNotBlank() }
    val label = buildString {
        append("DEBUG ")
        append(BuildConfig.VERSION_NAME)
        append(" (")
        append(BuildConfig.VERSION_CODE)
        append(")")
        stamp?.let { append(" · ").append(it) }
    }

    Box(
        Modifier
            .align(Alignment.BottomStart)
            .padding(start = 6.dp, bottom = 6.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xCCB3261E)),
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
        )
    }
}
