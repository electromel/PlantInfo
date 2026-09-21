package ch.electromel.plantinfo.ui.result

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import ch.electromel.plantinfo.R
import ch.electromel.plantinfo.data.db.IdentificationEntity
import ch.electromel.plantinfo.data.repo.toResult
import ch.electromel.plantinfo.domain.model.AiProviderType
import ch.electromel.plantinfo.domain.model.EdibilityVerdict
import ch.electromel.plantinfo.domain.model.IdentificationResult
import ch.electromel.plantinfo.domain.model.SpeciesCandidate
import ch.electromel.plantinfo.domain.model.TokenUsage
import ch.electromel.plantinfo.domain.model.UseDomain
import ch.electromel.plantinfo.domain.model.careCalendarLines
import ch.electromel.plantinfo.domain.model.costText
import ch.electromel.plantinfo.domain.model.edibilityVerdict
import ch.electromel.plantinfo.domain.model.iucnStatus
import ch.electromel.plantinfo.domain.model.labelRes
import ch.electromel.plantinfo.domain.model.maturityLines
import ch.electromel.plantinfo.domain.model.tokensCountText
import ch.electromel.plantinfo.domain.model.usesByDomain
import ch.electromel.plantinfo.ui.components.BannerSeverity
import ch.electromel.plantinfo.ui.components.FullscreenPhotoViewer
import ch.electromel.plantinfo.ui.components.ScoreBadge
import ch.electromel.plantinfo.ui.components.SectionCard
import ch.electromel.plantinfo.ui.components.WarningBanner
import ch.electromel.plantinfo.ui.components.scoreColor
import ch.electromel.plantinfo.ui.components.scoreTextColor
import ch.electromel.plantinfo.ui.map.SpeciesMap
import ch.electromel.plantinfo.ui.qa.PlantQaSection
import ch.electromel.plantinfo.util.StringProvider
import ch.electromel.plantinfo.util.rememberStringProvider
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
    advice: FicheAdvice? = null,
    onOpenSetup: ((String) -> Unit)? = null,
    // Dernier paramètre : l'écran Détail le passe en lambda finale (en-tête « notes »).
    header: (@Composable () -> Unit)? = null,
) {
    val result = entity.toResult()
    // Une fiche peut arriver sans nom exploitable (réponse d'IA tronquée) : on n'affiche jamais un
    // titre vide.
    val displayName = result.commonName.ifBlank { stringResource(R.string.species_unknown) }

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

        // Ce qui manque à la fiche, et par quoi le combler : posé avant tout le reste, faute de
        // quoi on laisse croire que l'espèce n'a rien de plus à dire.
        advice?.let { AdviceCard(it, onOpenSetup) }

        // Photos : une seule → image « héro » pleine largeur ; plusieurs → carrousel à largeur fixe.
        if (entity.photoPaths.size == 1) {
            val path = entity.photoPaths.first()
            AsyncImage(
                model = File(path),
                contentDescription = stringResource(R.string.fiche_photo_of, displayName),
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
                        contentDescription = stringResource(R.string.fiche_photo_of, displayName),
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
                Text(displayName, style = MaterialTheme.typography.headlineMedium)
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
                stringResource(R.string.fiche_score_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }

        if (entity.userConfirmed) {
            WarningBanner(
                text = stringResource(R.string.fiche_user_confirmed),
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
                text = stringResource(R.string.fiche_no_ai_banner),
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
        // Confusion possible avec une espèce toxique : avertissement permanent, au même titre que
        // celui des champignons. Seuils réglables dans les Paramètres (voir ToxicConfusionBanner).
        ToxicConfusionBanner(result, Modifier.fillMaxWidth())
        // Divergence et faible confiance ne s'affichent plus une fois l'espèce validée par l'utilisateur.
        if (!entity.userConfirmed && result.sourcesDisagree) {
            WarningBanner(
                text = stringResource(R.string.fiche_sources_disagree),
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
            SectionCard(stringResource(R.string.fiche_score_section), Modifier.fillMaxWidth()) {
                ScoreLine(stringResource(R.string.fiche_score_final), result.scoreFinal)
                result.scorePlantNet?.let { ScoreLine("Pl@ntNet", it) }
                result.scoreAi?.let {
                    ScoreLine(stringResource(R.string.fiche_score_ai, result.aiProvider.label), it)
                }
            }
        }

        // Comestibilité / toxicité
        EdibilitySection(result)

        // Habitat
        result.habitat?.let {
            SectionCard(stringResource(R.string.fiche_habitat), Modifier.fillMaxWidth()) {
                Text(it, style = MaterialTheme.typography.bodyLarge)
            }
        }

        // Statut de conservation UICN (rapporté par Pl@ntNet, absent pour les champignons).
        ConservationSection(result)

        // État de santé
        result.health?.let { health ->
            SectionCard(stringResource(R.string.fiche_health), Modifier.fillMaxWidth()) {
                Text(
                    health.status.ifBlank { stringResource(R.string.health_not_assessed) },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                if (health.recommendations.isNotEmpty()) {
                    Text(
                        stringResource(R.string.fiche_health_recommendations),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    health.recommendations.forEach { rec ->
                        Text("•  $rec", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }

        // Informations générales
        result.description?.let {
            SectionCard(stringResource(R.string.fiche_information), Modifier.fillMaxWidth()) {
                Text(it, style = MaterialTheme.typography.bodyLarge)
            }
        }

        // Dimensions à maturité (IA uniquement : absentes d'un résultat Pl@ntNet brut).
        MaturitySection(result)

        // Calendrier de plantation et d'entretien, usages et symbolique (IA uniquement).
        CareCalendarSection(result)
        UsesSection(result)
        SymbolismSection(result)

        // Alternatives (masquées avec le détail du score quand la confiance est bonne).
        if (result.alternatives.isNotEmpty() && showScoreDetails) {
            val title = stringResource(
                if (onSelectAlternative != null) R.string.fiche_alternatives_selectable
                else R.string.fiche_alternatives,
            )
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
                                Text(stringResource(R.string.fiche_alternative_choose))
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
            stringResource(
                if (hasCapturePoint) R.string.fiche_location_title else R.string.fiche_range_title,
            ),
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
                        stringResource(
                            if (it > 50f) R.string.fiche_gps_accuracy_low else R.string.fiche_gps_accuracy,
                            it.toInt(),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            } else {
                Text(
                    stringResource(R.string.fiche_no_gps_explanation),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            SpeciesMap(
                latitude = entity.latitude,
                longitude = entity.longitude,
                accuracyMeters = entity.gpsAccuracy,
                scientificName = result.scientificName,
                title = displayName,
                modifier = Modifier.fillMaxWidth(),
                gbifKey = result.gbifKey,
            )
        }

        // Consommation de l'appel IA d'identification (jetons + coût estimé).
        result.usage?.let {
            AiUsageSection(stringResource(R.string.fiche_identification_cost), it, result.aiProvider)
        }

        // Questions à l'IA sur la plante (texte ou dictée vocale).
        PlantQaSection(entity = entity, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * Section « Dimensions à maturité » : taille de l'espèce adulte, pas du sujet photographié.
 * Masquée tant qu'aucune des trois mesures n'est renseignée (résultat Pl@ntNet brut, ou IA restée
 * prudente) plutôt que d'afficher des tirets.
 */
/**
 * Explique pourquoi la fiche est partielle et emmène, quand c'est le cas, vers l'assistant de
 * configuration. Le texte et la cible viennent de [FicheAdvice] : ils sont recalculés à l'affichage,
 * donc justes aussi longtemps que la fiche existe.
 */
@Composable
private fun AdviceCard(advice: FicheAdvice, onOpenSetup: ((String) -> Unit)?) {
    SectionCard(stringResource(R.string.fiche_incomplete), Modifier.fillMaxWidth()) {
        Text(stringResource(advice.messageRes), style = MaterialTheme.typography.bodyMedium)
        val labelRes = advice.actionLabelRes
        val focus = advice.focus
        if (labelRes != null && focus != null && onOpenSetup != null) {
            Button(onClick = { onOpenSetup(focus) }) { Text(stringResource(labelRes)) }
        }
    }
}

@Composable
private fun MaturitySection(result: IdentificationResult) {
    val lines = result.maturityLines()
    if (lines.isEmpty()) return

    SectionCard(stringResource(R.string.fiche_maturity_title), Modifier.fillMaxWidth()) {
        lines.forEach { (labelRes, value) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(labelRes),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End,
                )
            }
        }
        Text(
            stringResource(R.string.fiche_maturity_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

/**
 * Section « Quand planter et entretenir » : opérations saisonnières (semis, taille, récolte…).
 * Pour un champignon, l'IA y décrit la période de pousse et de cueillette. Masquée si le calendrier
 * est vide (résultat Pl@ntNet brut, ou espèce sans conduite de culture connue).
 */
@Composable
private fun CareCalendarSection(result: IdentificationResult) {
    val tasks = result.careCalendarLines()
    if (tasks.isEmpty()) return

    val title = stringResource(
        if (result.isFungus) R.string.fiche_calendar_fungus_title else R.string.fiche_calendar_title,
    )
    SectionCard(title, Modifier.fillMaxWidth()) {
        tasks.forEach { task ->
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        task.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        task.period,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
                task.note?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }
        }
        Text(
            stringResource(R.string.fiche_calendar_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

/**
 * Section « Usages » : ce qui est fait de l'espèce, regroupé par domaine (santé, alimentation,
 * cosmétique/parfumerie, chimie, artisanat…). Les usages médicinaux sont rapportés à titre
 * documentaire : la mise en garde n'est affichée que lorsqu'ils sont réellement présents.
 */
@Composable
private fun UsesSection(result: IdentificationResult) {
    val grouped = result.usesByDomain()
    if (grouped.isEmpty()) return

    SectionCard(stringResource(R.string.fiche_uses_title), Modifier.fillMaxWidth()) {
        grouped.forEach { (domain, details) ->
            Text(stringResource(domain.labelRes), style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold)
            details.forEach { detail ->
                Text("•  $detail", style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (grouped.any { (domain, _) -> domain == UseDomain.MEDICINAL }) {
            Text(
                stringResource(R.string.fiche_uses_medicinal_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}

/** Section « Symbolique » : charge culturelle, religieuse ou langage des fleurs. Masquée si aucune. */
@Composable
private fun SymbolismSection(result: IdentificationResult) {
    val symbolism = result.symbolism?.takeIf { it.isNotBlank() } ?: return

    SectionCard(stringResource(R.string.fiche_symbolism_title), Modifier.fillMaxWidth()) {
        Text(symbolism, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Section « Statut de conservation » : catégorie UICN telle que rapportée par Pl@ntNet.
 * Distincte du drapeau « espèce protégée » : l'UICN est un statut mondial, la protection une règle
 * locale. Les deux peuvent diverger, et les afficher séparément évite de les confondre.
 */
@Composable
private fun ConservationSection(result: IdentificationResult) {
    val status = result.iucnStatus ?: return

    SectionCard(stringResource(R.string.fiche_conservation_title), Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.fiche_conservation_status, stringResource(status.labelRes), status.code),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (status.threatened) scoreTextColor(0) else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            stringResource(R.string.fiche_conservation_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
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
            text = stringResource(R.string.fiche_toxic_banner),
            icon = Icons.Filled.Warning,
            severity = BannerSeverity.DANGER,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    SectionCard(stringResource(R.string.fiche_edibility_title), Modifier.fillMaxWidth()) {
        verdict.labelRes?.let { textRes ->
            val color = when (verdict) {
                EdibilityVerdict.EDIBLE -> scoreTextColor(100)
                EdibilityVerdict.TOXIC -> scoreTextColor(0)
                else -> MaterialTheme.colorScheme.onSurface
            }
            Text(stringResource(textRes), style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = color)
        }
        note?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        if (verdict == EdibilityVerdict.EDIBLE) {
            Text(
                stringResource(R.string.fiche_edible_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun ScoreLine(label: String, score: Int) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
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
        Text(stringResource(R.string.fiche_confirmed_pill), color = androidx.compose.ui.graphics.Color.White,
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
            Text(stringResource(R.string.fiche_reanalyzing))
        } else {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.fiche_reanalyze_ai))
        }
    }
}

@Composable
private fun ProviderChip(provider: AiProviderType) {
    val text = if (provider == AiProviderType.NONE) {
        stringResource(R.string.fiche_provider_none)
    } else {
        stringResource(R.string.fiche_provider_analyzed_by, provider.label)
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

/**
 * Jetons consommés et coût estimé d'un appel IA (§3.x). Réutilisée telle quelle par la zone de
 * questions, pour que le même appel soit chiffré de la même façon partout.
 *
 * Le montant est présenté comme une **estimation** : le tarif public du modèle ne tient compte ni
 * des paliers gratuits (le mode « Gemini gratuit seul » ne coûte rien tant que le quota tient) ni
 * des remises de cache. Un modèle sans tarif connu n'affiche que les jetons — jamais un faux zéro.
 */
@Composable
fun AiUsageSection(
    title: String,
    usage: TokenUsage,
    provider: AiProviderType,
    modifier: Modifier = Modifier.fillMaxWidth(),
    strings: StringProvider = rememberStringProvider(),
) {
    SectionCard(title, modifier) {
        UsageLine(stringResource(R.string.usage_provider), "${provider.label} · ${usage.model}")
        UsageLine(stringResource(R.string.usage_tokens), usage.tokensCountText(strings))
        UsageLine(
            stringResource(R.string.usage_cost),
            usage.costText() ?: stringResource(R.string.usage_cost_unknown),
        )
        Text(
            stringResource(R.string.usage_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

/**
 * Une ligne « libellé — valeur » de la carte de consommation.
 *
 * Les deux textes portent un poids : sans cela, une valeur longue (les jetons détaillés, un nom de
 * modèle) prend toute la largeur qu'elle demande et comprime le libellé jusqu'à l'écrire une lettre
 * par ligne. Avec un poids de part et d'autre, c'est la valeur qui passe à la ligne.
 */
@Composable
private fun UsageLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.weight(0.42f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.58f),
        )
    }
}
