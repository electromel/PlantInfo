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
import ch.electromel.plantinfo.domain.model.label
import ch.electromel.plantinfo.domain.model.maturityLines
import ch.electromel.plantinfo.domain.model.tokensText
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
        // Confusion possible avec une espèce toxique : avertissement permanent, au même titre que
        // celui des champignons. Seuils réglables dans les Paramètres (voir ToxicConfusionBanner).
        ToxicConfusionBanner(result, Modifier.fillMaxWidth())
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

        // Statut de conservation UICN (rapporté par Pl@ntNet, absent pour les champignons).
        ConservationSection(result)

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

        // Dimensions à maturité (IA uniquement : absentes d'un résultat Pl@ntNet brut).
        MaturitySection(result)

        // Calendrier de plantation et d'entretien, usages et symbolique (IA uniquement).
        CareCalendarSection(result)
        UsesSection(result)
        SymbolismSection(result)

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
                gbifKey = result.gbifKey,
            )
        }

        // Consommation de l'appel IA d'identification (jetons + coût estimé).
        result.usage?.let { AiUsageSection("Coût de l'identification", it, result.aiProvider) }

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
    SectionCard("Fiche incomplète", Modifier.fillMaxWidth()) {
        Text(advice.message, style = MaterialTheme.typography.bodyMedium)
        val label = advice.actionLabel
        val focus = advice.focus
        if (label != null && focus != null && onOpenSetup != null) {
            Button(onClick = { onOpenSetup(focus) }) { Text(label) }
        }
    }
}

@Composable
private fun MaturitySection(result: IdentificationResult) {
    val lines = result.maturityLines()
    if (lines.isEmpty()) return

    SectionCard("Dimensions à maturité", Modifier.fillMaxWidth()) {
        lines.forEach { (label, value) ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                Text(value, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium)
            }
        }
        Text(
            "Taille de l'espèce adulte, à titre indicatif : elle varie avec le sol, l'exposition et le climat.",
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

    val title = if (result.isFungus) "Période de pousse et de cueillette" else "Quand planter et entretenir"
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
            "Périodes indicatives pour le climat du lieu de prise de vue : décalez-les selon " +
                "l'altitude, l'exposition et la météo de l'année.",
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

    SectionCard("Usages", Modifier.fillMaxWidth()) {
        grouped.forEach { (domain, details) ->
            Text(domain.label, style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold)
            details.forEach { detail ->
                Text("•  $detail", style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (grouped.any { (domain, _) -> domain == UseDomain.MEDICINAL }) {
            Text(
                "Usages traditionnels ou documentés, cités à titre informatif : ce ne sont pas des " +
                    "conseils thérapeutiques. Demandez l'avis d'un professionnel de santé avant tout usage.",
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

    SectionCard("Symbolique", Modifier.fillMaxWidth()) {
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

    SectionCard("Statut de conservation", Modifier.fillMaxWidth()) {
        Text(
            "${status.label} (${status.code})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (status.threatened) scoreTextColor(0) else MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "Liste rouge UICN mondiale, via Pl@ntNet. Un statut de conservation ne vaut pas " +
                "protection juridique : la réglementation locale peut être plus stricte.",
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
) {
    SectionCard(title, modifier) {
        UsageLine("Fournisseur", "${provider.label} · ${usage.model}")
        UsageLine("Jetons", usage.tokensText())
        UsageLine("Coût estimé", usage.costText() ?: "tarif du modèle inconnu")
        Text(
            "Estimation au tarif public du modèle. Nulle si votre compte est encore sur un palier " +
                "gratuit du fournisseur.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun UsageLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}
