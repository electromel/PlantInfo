package ch.electromel.plantinfo.domain

import ch.electromel.plantinfo.data.remote.ai.AiAnalysis
import ch.electromel.plantinfo.domain.model.ComplementaryPhotoRequest
import ch.electromel.plantinfo.domain.model.HealthAssessment
import ch.electromel.plantinfo.domain.model.IdentificationResult
import ch.electromel.plantinfo.domain.model.PhotoOrgan
import ch.electromel.plantinfo.domain.model.SpeciesCandidate
import kotlin.math.roundToInt

/**
 * Fusionne les sorties Pl@ntNet et IA en un IdentificationResult, en calculant le score
 * d'exactitude final sur 100 (§2.3) selon l'accord ou le désaccord entre les deux sources.
 *
 * Règles :
 * - Accord (mêmes genre+espèce) → le score est renforcé (moyenne pondérée + bonus).
 * - Désaccord alors que Pl@ntNet est confiant → score réduit et signalement de divergence.
 * - Champignon / plante non couverte par Pl@ntNet → score = confiance IA seule.
 * - Aucune IA disponible → résultat Pl@ntNet brut (score = meilleur candidat, infos minimales).
 */
object ConfidenceEngine {

    private const val AGREEMENT_BONUS = 10
    private const val DISAGREEMENT_PLANTNET_THRESHOLD = 40

    /** Combine une analyse IA (prioritaire) avec les candidats Pl@ntNet. */
    fun combineWithAi(
        ai: AiAnalysis,
        plantNetCandidates: List<SpeciesCandidate>,
    ): IdentificationResult {
        val plantNetTop = plantNetCandidates.firstOrNull()
        val agree = plantNetTop != null && namesMatch(ai.scientificName, plantNetTop.scientificName)

        val scoreFinal: Int
        val sourcesDisagree: Boolean
        when {
            plantNetTop == null -> {
                // Pas de référence Pl@ntNet (champignon typiquement) : on suit l'IA.
                scoreFinal = ai.confidence
                sourcesDisagree = false
            }
            agree -> {
                scoreFinal = (ai.confidence * 0.6 + plantNetTop.score * 0.4).roundToInt()
                    .plus(AGREEMENT_BONUS).coerceIn(0, 100)
                sourcesDisagree = false
            }
            else -> {
                // Désaccord : on privilégie l'IA mais on abaisse le score ; divergence signalée si
                // Pl@ntNet était lui aussi raisonnablement confiant.
                scoreFinal = (ai.confidence * 0.7).roundToInt().coerceIn(0, 100)
                sourcesDisagree = plantNetTop.score >= DISAGREEMENT_PLANTNET_THRESHOLD
            }
        }

        val alternatives = buildAlternatives(
            chosenScientific = ai.scientificName,
            aiAlternatives = ai.alternatives,
            plantNetCandidates = plantNetCandidates,
            disagreeingTop = if (sourcesDisagree) plantNetTop else null,
        )

        val complementary = ai.complementary?.let {
            ComplementaryPhotoRequest(it.organ, it.reason)
        } ?: complementaryFallback(scoreFinal, ai.isFungus)

        // Clé GBIF et statut UICN appartiennent au taxon reconnu par Pl@ntNet. L'espèce finalement
        // retenue est celle de l'IA, qui a pu corriger Pl@ntNet : on ne reprend ces métadonnées que
        // du candidat portant réellement le même nom, sinon la carte interrogerait le mauvais taxon.
        val matched = plantNetCandidates.firstOrNull { namesMatch(it.scientificName, ai.scientificName) }

        return IdentificationResult(
            commonName = ai.commonName,
            scientificName = ai.scientificName,
            scorePlantNet = plantNetTop?.score,
            scoreAi = ai.confidence,
            scoreFinal = scoreFinal,
            aiProvider = ch.electromel.plantinfo.domain.model.AiProviderType.NONE, // renseigné par le repository
            alternatives = alternatives,
            isFungus = ai.isFungus,
            isProtected = ai.isProtected,
            health = HealthAssessment(ai.healthStatus, ai.isHealthy, ai.recommendations),
            habitat = ai.habitat,
            description = ai.description,
            matureHeight = ai.matureHeight,
            matureDiameter = ai.matureDiameter,
            timeToMaturity = ai.timeToMaturity,
            edible = ai.edible,
            toxic = ai.toxic,
            edibilityNote = ai.edibilityNote,
            careCalendar = ai.careCalendar,
            uses = ai.uses,
            symbolism = ai.symbolism,
            gbifKey = matched?.gbifKey,
            iucnCategory = matched?.iucnCategory,
            sourcesDisagree = sourcesDisagree,
            complementaryPhotoRequest = complementary,
            usage = ai.usage,
        )
    }

    /** Résultat Pl@ntNet brut, sans IA (§3.1 : aucune clé IA configurée ou toutes en échec). */
    fun plantNetOnly(plantNetCandidates: List<SpeciesCandidate>): IdentificationResult {
        val top = plantNetCandidates.first()
        val alternatives = plantNetCandidates.drop(1).take(3)
        return IdentificationResult(
            commonName = top.commonName ?: top.scientificName,
            scientificName = top.scientificName,
            scorePlantNet = top.score,
            scoreAi = null,
            scoreFinal = top.score,
            aiProvider = ch.electromel.plantinfo.domain.model.AiProviderType.NONE,
            alternatives = alternatives,
            isFungus = false, // Pl@ntNet n'identifie pas les champignons ; par défaut non
            isProtected = false,
            health = null,
            habitat = null,
            description = null,
            // Dimensions à maturité : information IA uniquement, absente d'un résultat Pl@ntNet brut.
            matureHeight = null,
            matureDiameter = null,
            timeToMaturity = null,
            edible = null,
            toxic = null,
            edibilityNote = null,
            // Calendrier, usages et symbolique : informations IA uniquement.
            careCalendar = emptyList(),
            uses = emptyList(),
            symbolism = null,
            gbifKey = top.gbifKey,
            iucnCategory = top.iucnCategory,
            sourcesDisagree = false,
            complementaryPhotoRequest = complementaryFallback(top.score, isFungus = false),
            // Aucune IA interrogée : aucun jeton consommé, donc rien à facturer ni à afficher.
            usage = null,
        )
    }

    private fun buildAlternatives(
        chosenScientific: String,
        aiAlternatives: List<SpeciesCandidate>,
        plantNetCandidates: List<SpeciesCandidate>,
        disagreeingTop: SpeciesCandidate?,
    ): List<SpeciesCandidate> {
        val merged = LinkedHashMap<String, SpeciesCandidate>()
        // Le candidat Pl@ntNet en désaccord devient la première alternative (hypothèse concurrente).
        disagreeingTop?.let { merged[normalize(it.scientificName)] = it }
        aiAlternatives.forEach { merged.putIfAbsent(normalize(it.scientificName), it) }
        plantNetCandidates.forEach { merged.putIfAbsent(normalize(it.scientificName), it) }
        return merged.values
            .filter { !namesMatch(it.scientificName, chosenScientific) }
            .sortedByDescending { it.score }
            .take(3)
    }

    private fun complementaryFallback(scoreFinal: Int, isFungus: Boolean): ComplementaryPhotoRequest? {
        if (scoreFinal >= IdentificationResult.LOW_CONFIDENCE_THRESHOLD) return null
        return if (isFungus) {
            ComplementaryPhotoRequest(
                PhotoOrgan.GILLS,
                "Photographiez le dessous du chapeau (lamelles/pores) et le pied pour préciser l'identification.",
            )
        } else {
            ComplementaryPhotoRequest(
                PhotoOrgan.LEAF,
                "Ajoutez un gros plan net d'une feuille (et d'une fleur ou d'un fruit si présents).",
            )
        }
    }

    private fun normalize(name: String): String =
        name.trim().lowercase().split(Regex("\\s+")).take(2).joinToString(" ")

    private fun namesMatch(a: String, b: String): Boolean = normalize(a) == normalize(b)
}
