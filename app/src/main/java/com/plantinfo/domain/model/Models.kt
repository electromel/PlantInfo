package com.plantinfo.domain.model

import kotlinx.serialization.Serializable

/**
 * Fournisseurs d'IA générative multimodale supportés, plus NONE (aucune clé configurée →
 * résultat Pl@ntNet brut). L'ordre des constantes n'a pas de sens métier : l'ordre de repli
 * réel est stocké dans les paramètres (voir data.keys.ApiKeyStore).
 */
enum class AiProviderType(val label: String) {
    CLAUDE("Claude"),
    GEMINI("Gemini"),
    GPT("GPT"),
    NONE("Aucun");

    companion object {
        /** Ordre de priorité de repli par défaut (§3.1) : Claude → Gemini → GPT. */
        val DEFAULT_FALLBACK_ORDER = listOf(CLAUDE, GEMINI, GPT)
    }
}

/**
 * Type d'organe photographié, transmis à Pl@ntNet comme indice et utilisé pour formuler
 * les demandes de photos complémentaires (§2.1).
 */
enum class PhotoOrgan(val plantnetValue: String, val label: String) {
    LEAF("leaf", "Feuille"),
    FLOWER("flower", "Fleur"),
    FRUIT("fruit", "Fruit"),
    BARK("bark", "Écorce"),
    HABIT("habit", "Port / silhouette"),
    CAP("auto", "Chapeau (champignon)"),
    GILLS("auto", "Lamelles / dessous du chapeau"),
    OTHER("auto", "Autre");
}

/** Coordonnées de la prise de vue, avec précision rapportée par le capteur (§2.2). */
data class GpsLocation(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,        // mètres, si disponible
    val accuracyMeters: Float?,   // rayon d'incertitude horizontal rapporté par le capteur
) {
    /** Précision jugée faible (couvert forestier, montagne) → afficher un rayon plutôt qu'un point. */
    val isLowAccuracy: Boolean get() = (accuracyMeters ?: Float.MAX_VALUE) > 50f
}

/** Un candidat d'identification (Pl@ntNet ou IA), avec son score propre sur 100. */
@Serializable
data class SpeciesCandidate(
    val scientificName: String,
    val commonName: String?,
    val score: Int, // 0..100
)

/** Diagnostic de santé issu de l'analyse visuelle par l'IA (§2.4). */
data class HealthAssessment(
    val status: String,               // ex. « Bon », « Signes de stress hydrique »
    val isHealthy: Boolean,
    val recommendations: List<String>, // vide si plante saine
)

/**
 * Demande de photo complémentaire quand la confiance est insuffisante (§2.1 / §2.4) :
 * précise quel cliché est utile et pourquoi.
 */
data class ComplementaryPhotoRequest(
    val organ: PhotoOrgan,
    val reason: String,
)

/**
 * Résultat complet d'une identification, prêt à être affiché et persisté.
 * Regroupe les sorties Pl@ntNet + IA et le score combiné (§2.3 / §4).
 */
data class IdentificationResult(
    val commonName: String,
    val scientificName: String,
    val scorePlantNet: Int?,          // null si Pl@ntNet non appelé/sans résultat (ex. champignon)
    val scoreAi: Int?,                // null si aucune IA disponible
    val scoreFinal: Int,              // 0..100, score d'exactitude affiché
    val aiProvider: AiProviderType,   // fournisseur ayant réellement traité (ou NONE)
    val alternatives: List<SpeciesCandidate>, // 2-3 hypothèses alternatives si score non maximal
    val isFungus: Boolean,            // déclenche l'avertissement de sécurité systématique
    val isProtected: Boolean,         // espèce protégée dans la région → avertissement cueillette
    val health: HealthAssessment?,    // null si pas d'analyse IA
    val habitat: String?,             // habitat / répartition typique
    val description: String?,         // infos générales (saisonnalité, usages…)
    val edible: Boolean?,             // true = comestible, false = non comestible, null = inconnu/non évalué
    val toxic: Boolean?,              // true = toxique/vénéneux, false = non toxique, null = inconnu
    val edibilityNote: String?,       // précisions sur comestibilité/toxicité (parties, préparation, dangers)
    val sourcesDisagree: Boolean,     // Pl@ntNet et IA divergent significativement → à signaler
    val complementaryPhotoRequest: ComplementaryPhotoRequest?, // si score sous le seuil
) {
    companion object {
        /** Seuil sous lequel on invite à fournir une photo complémentaire (§2.4). */
        const val LOW_CONFIDENCE_THRESHOLD = 60
    }
}

/** Verdict de comestibilité synthétique dérivé des drapeaux edible/toxic (§2.4). */
enum class EdibilityVerdict { EDIBLE, TOXIC, INEDIBLE, UNKNOWN }

/** La toxicité prime sur la comestibilité (sécurité) ; UNKNOWN si aucune info fiable. */
val IdentificationResult.edibilityVerdict: EdibilityVerdict
    get() = when {
        toxic == true -> EdibilityVerdict.TOXIC
        edible == true -> EdibilityVerdict.EDIBLE
        edible == false -> EdibilityVerdict.INEDIBLE
        else -> EdibilityVerdict.UNKNOWN
    }

/** Libellé court du verdict, ou null si inconnu (rien à afficher comme verdict). */
val EdibilityVerdict.label: String?
    get() = when (this) {
        EdibilityVerdict.EDIBLE -> "Comestible"
        EdibilityVerdict.TOXIC -> "Toxique"
        EdibilityVerdict.INEDIBLE -> "Non comestible"
        EdibilityVerdict.UNKNOWN -> null
    }

/** Résumé textuel (verdict + précisions) pour PDF/partage. null si aucune information. */
fun IdentificationResult.edibilitySummaryText(): String? {
    val verdict = edibilityVerdict.label
    val note = edibilityNote?.takeIf { it.isNotBlank() }
    return when {
        verdict != null && note != null -> "$verdict — $note"
        verdict != null -> verdict
        else -> note
    }
}
