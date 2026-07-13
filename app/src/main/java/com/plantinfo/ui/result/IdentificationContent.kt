package com.plantinfo.ui.result

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.plantinfo.R
import com.plantinfo.data.db.IdentificationEntity
import com.plantinfo.data.repo.toResult
import com.plantinfo.domain.model.AiProviderType
import com.plantinfo.domain.model.EdibilityVerdict
import com.plantinfo.domain.model.IdentificationResult
import com.plantinfo.domain.model.SpeciesCandidate
import com.plantinfo.domain.model.edibilityVerdict
import com.plantinfo.domain.model.label
import com.plantinfo.ui.components.BannerSeverity
import com.plantinfo.ui.components.FullscreenPhotoViewer
import com.plantinfo.ui.components.ScoreBadge
import com.plantinfo.ui.components.SectionCard
import com.plantinfo.ui.components.WarningBanner
import com.plantinfo.ui.components.scoreColor
import com.plantinfo.ui.components.scoreTextColor
import com.plantinfo.ui.map.SpeciesMap
import com.plantinfo.ui.qa.PlantQaSection
import java.io.File

/**
 * Contenu complet d'une fiche d'identification, partagé entre l'écran Résultat (juste après l'ID)
 * et l'écran Détail (depuis l'historique). Applique tous les affichages exigés au §2.4 :
 * score sur 100, avertissements, habitat, santé, infos, alternatives, localisation.
 */
@Composable
fun IdentificationContent(
    entity: IdentificationEntity,
    modifier: Modifier = Modifier,
    onSelectAlternative: ((SpeciesCandidate) -> Unit)? = null,
    onReanalyze: (() -> Unit)? = null,
    reanalyzing: Boolean = false,
    header: (@Composable () -> Unit)? = null,
) {
    val result = entity.toResult()

    // Le détail du score et les autres hypothèses sont masqués par défaut quand la confiance est
    // bonne (> 50) : un appui sur le badge de score les révèle, un second appui les masque.
    val canCollapseScore = !entity.userConfirmed && result.scoreFinal > 50
    var showScoreDetails by remember(entity.id) { mutableStateOf(!canCollapseScore) }

    // Photo affichée en plein écran (zoom par pincement) après un appui sur une vignette.
    var fullscreenPhoto by remember(entity.id) { mutableStateOf<String?>(null) }
    fullscreenPhoto?.let { FullscreenPhotoViewer(path = it, onDismiss = { fullscreenPhoto = null }) }

    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        header?.invoke()

        // Photos : une seule → image « héro » pleine largeur ; plusieurs → carrousel à largeur fixe.
        if (entity.photoPaths.size == 1) {
            val path = entity.photoPaths.first()
            AsyncImage(
                model = File(path),
                contentDescription = "Photo de ${result.commonName} — appuyer pour agrandir",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { fullscreenPhoto = path },
            )
        } else if (entity.photoPaths.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(entity.photoPaths) { path ->
                    AsyncImage(
                        model = File(path),
                        contentDescription = "Photo de ${result.commonName} — appuyer pour agrandir",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 170.dp, height = 220.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { fullscreenPhoto = path },
                    )
                }
            }
        }

        // En-tête nom + score
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(result.commonName, style = MaterialTheme.typography.headlineMedium)
                Text(
                    result.scientificName,
                    style = MaterialTheme.typography.titleMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            if (entity.userConfirmed) {
                ConfirmedPill()
            } else {
                ScoreBadge(
                    result.scoreFinal,
                    large = true,
                    modifier = if (canCollapseScore) {
                        Modifier.clickable { showScoreDetails = !showScoreDetails }
                    } else {
                        Modifier
                    },
                )
            }
        }

        // Invite discrète, uniquement quand le détail est replié (le rappel permanent alourdit la fiche).
        if (canCollapseScore && !showScoreDetails) {
            Text(
                "Appuyez sur le score pour afficher le détail et les autres hypothèses.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }

        if (entity.userConfirmed) {
            WarningBanner(
                text = "Espèce validée manuellement parmi les hypothèses proposées.",
                icon = Icons.Filled.CheckCircle,
                severity = BannerSeverity.WARNING,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            ProviderChip(result.aiProvider)
        }

        // Aucune IA n'a pu être interrogée (pas de clé, quota, ou service inaccessible) : le signaler
        // clairement et proposer de relancer l'analyse — utile aussi après ajout d'une clé IA.
        if (result.aiProvider == AiProviderType.NONE) {
            WarningBanner(
                text = "Aucune IA n'a pu être interrogée : résultat Pl@ntNet brut, sans description, " +
                    "diagnostic de santé ni comestibilité. Vérifiez vos clés IA dans les paramètres " +
                    "ou relancez l'analyse.",
                icon = Icons.Filled.Info,
                severity = BannerSeverity.WARNING,
                modifier = Modifier.fillMaxWidth(),
            )
            if (onReanalyze != null) {
                ReanalyzeButton(onReanalyze, reanalyzing, Modifier.fillMaxWidth())
            }
        }

        // --- Avertissements ---
        // L'avertissement champignon reste systématique, même après validation manuelle (§2.4).
        if (result.isFungus) {
            WarningBanner(
                text = stringResource(R.string.fungus_safety_warning),
                icon = Icons.Filled.Warning,
                severity = BannerSeverity.DANGER,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (result.isProtected) {
            WarningBanner(
                text = stringResource(R.string.protected_species_warning),
                icon = Icons.Filled.Warning,
                severity = BannerSeverity.WARNING,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // Divergence et faible confiance ne s'affichent plus une fois l'espèce validée par l'utilisateur.
        if (!entity.userConfirmed && result.sourcesDisagree) {
            WarningBanner(
                text = "Identification incertaine : Pl@ntNet et l'IA proposent des espèces différentes. " +
                    "Voir les hypothèses alternatives ci-dessous.",
                icon = Icons.AutoMirrored.Filled.CompareArrows,
                severity = BannerSeverity.WARNING,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (!entity.userConfirmed && result.scoreFinal < IdentificationResult.LOW_CONFIDENCE_THRESHOLD) {
            WarningBanner(
                text = stringResource(R.string.low_confidence_hint),
                icon = Icons.Filled.Info,
                severity = BannerSeverity.WARNING,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Détail des scores (masqué si l'espèce validée manuellement, ou replié si confiance > 50).
        if (!entity.userConfirmed && showScoreDetails) {
            SectionCard("Score d'exactitude", Modifier.fillMaxWidth()) {
                ScoreLine("Score final", result.scoreFinal)
                result.scorePlantNet?.let { ScoreLine("Pl@ntNet", it) }
                result.scoreAi?.let { ScoreLine("IA (${result.aiProvider.label})", it) }
            }
        }

        // Comestibilité / toxicité
        EdibilitySection(result)

        // Habitat
        result.habitat?.let {
            SectionCard("Habitat et répartition", Modifier.fillMaxWidth()) {
                Text(it, style = MaterialTheme.typography.bodyLarge)
            }
        }

        // État de santé
        result.health?.let { health ->
            SectionCard("État de santé", Modifier.fillMaxWidth()) {
                Text(health.status, style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium)
                if (health.recommendations.isNotEmpty()) {
                    Text("Recommandations :", style = MaterialTheme.typography.titleMedium)
                    health.recommendations.forEach { rec ->
                        Text("•  $rec", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        // Informations générales
        result.description?.let {
            SectionCard("Informations", Modifier.fillMaxWidth()) {
                Text(it, style = MaterialTheme.typography.bodyLarge)
            }
        }

        // Alternatives (masquées avec le détail du score quand la confiance est bonne).
        if (result.alternatives.isNotEmpty() && showScoreDetails) {
            val title = if (onSelectAlternative != null) {
                "Autres hypothèses — appuyez pour valider"
            } else {
                "Autres hypothèses"
            }
            SectionCard(title, Modifier.fillMaxWidth()) {
                result.alternatives.forEach { alt ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(alt.commonName ?: alt.scientificName,
                                    style = MaterialTheme.typography.bodyLarge)
                                if (alt.commonName != null) {
                                    Text(alt.scientificName, fontStyle = FontStyle.Italic,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                }
                            }
                            ScoreBadge(alt.score)
                        }
                        if (onSelectAlternative != null) {
                            TextButton(
                                onClick = { onSelectAlternative(alt) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null,
                                    modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Choisir cette espèce")
                            }
                        }
                    }
                }
            }
        }

        // Localisation + carte interactive (osmdroid) et aire de répartition (GBIF).
        // La carte s'affiche même sans coordonnées de prise de vue (ex. photo de galerie sans EXIF
        // GPS) : elle est alors centrée sur l'aire de répartition connue de l'espèce.
        val hasCapturePoint = entity.latitude != null && entity.longitude != null
        SectionCard(
            if (hasCapturePoint) "Lieu de la prise de vue" else "Où trouver cette espèce",
            Modifier.fillMaxWidth(),
        ) {
            if (hasCapturePoint) {
                Text(
                    buildString {
                        append("%.5f, %.5f".format(entity.latitude, entity.longitude))
                        entity.altitude?.let { append("  •  %.0f m".format(it)) }
                    },
                    style = MaterialTheme.typography.bodyLarge,
                )
                entity.gpsAccuracy?.let {
                    Text(
                        "Précision GPS ~${it.toInt()} m" + if (it > 50f) " (faible)" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            } else {
                Text(
                    "Photo sans position GPS : la carte est centrée sur l'aire de répartition connue " +
                        "de l'espèce (occurrences GBIF), et non sur le lieu de la prise de vue.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            SpeciesMap(
                latitude = entity.latitude,
                longitude = entity.longitude,
                accuracyMeters = entity.gpsAccuracy,
                scientificName = result.scientificName,
                title = result.commonName,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Questions à l'IA sur la plante (texte ou dictée vocale).
        PlantQaSection(entity = entity, modifier = Modifier.fillMaxWidth())
    }
}

/** Section comestibilité/toxicité : bannière de danger si toxique, verdict coloré + précisions. */
@Composable
private fun EdibilitySection(result: IdentificationResult) {
    val verdict = result.edibilityVerdict
    val note = result.edibilityNote?.takeIf { it.isNotBlank() }
    if (verdict == EdibilityVerdict.UNKNOWN && note == null) return

    if (verdict == EdibilityVerdict.TOXIC) {
        WarningBanner(
            text = "Espèce signalée comme toxique. Ne pas consommer ni porter à la bouche.",
            icon = Icons.Filled.Warning,
            severity = BannerSeverity.DANGER,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    SectionCard("Comestibilité", Modifier.fillMaxWidth()) {
        verdict.label?.let { text ->
            val color = when (verdict) {
                EdibilityVerdict.EDIBLE -> scoreTextColor(100)
                EdibilityVerdict.TOXIC -> scoreTextColor(0)
                else -> MaterialTheme.colorScheme.onSurface
            }
            Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                color = color)
        }
        note?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        if (verdict == EdibilityVerdict.EDIBLE) {
            Text(
                "Ne consommez jamais une plante ou un champignon sauvage sans confirmation par un " +
                    "expert : une identification peut être erronée et certaines espèces ont des sosies dangereux.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun ScoreLine(label: String, score: Int) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text("$score/100", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = scoreTextColor(score))
    }
}

@Composable
private fun ConfirmedPill() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(scoreColor(100))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier.size(20.dp))
        Text("Confirmé", color = androidx.compose.ui.graphics.Color.White,
            fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}

/** Bouton de relance du pipeline d'identification (Pl@ntNet + IA) sur la fiche courante. */
@Composable
fun ReanalyzeButton(
    onReanalyze: () -> Unit,
    reanalyzing: Boolean,
    modifier: Modifier = Modifier,
) {
    Button(onClick = onReanalyze, enabled = !reanalyzing, modifier = modifier) {
        if (reanalyzing) {
            CircularProgressIndicator(
                Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = LocalContentColor.current,
            )
            Spacer(Modifier.width(8.dp))
            Text("Nouvelle analyse…")
        } else {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Relancer l'analyse IA")
        }
    }
}

@Composable
private fun ProviderChip(provider: AiProviderType) {
    val text = if (provider == AiProviderType.NONE) {
        "Résultat Pl@ntNet seul (aucune IA)"
    } else {
        "Analysé par ${provider.label}"
    }
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
