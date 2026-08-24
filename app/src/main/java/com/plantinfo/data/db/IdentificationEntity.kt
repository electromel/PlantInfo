package com.plantinfo.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Une entrée d'historique persistée localement (§4). Toutes les données restent sur l'appareil.
 * Les listes (chemins de photos, recommandations, alternatives) sont sérialisées via Converters.
 */
@Entity(tableName = "identifications")
data class IdentificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateTime: Long,                 // epoch millis de la prise de vue / identification

    val photoPaths: List<String>,       // chemins locaux des photos compressées

    val latitude: Double?,
    val longitude: Double?,
    val altitude: Double?,
    val gpsAccuracy: Float?,            // rayon d'incertitude horizontal (m)

    val commonName: String,
    val scientificName: String,

    val scorePlantNet: Int?,
    val scoreAi: Int?,
    val scoreFinal: Int,

    val aiProvider: String,             // AiProviderType.name (CLAUDE/GEMINI/GPT/NONE)

    val isProtected: Boolean,
    val isFungus: Boolean,

    val healthStatus: String?,
    val isHealthy: Boolean?,
    val recommendations: List<String>,

    val habitat: String?,
    val description: String?,

    val matureHeight: String?,          // hauteur à maturité (null = non évaluée)
    val matureDiameter: String?,        // diamètre / étalement à maturité
    val timeToMaturity: String?,        // temps pour atteindre la maturité

    val edible: Boolean?,               // comestibilité pour l'humain (null = inconnu)
    val toxic: Boolean?,                // toxicité pour l'humain (null = inconnu)
    val edibilityNote: String?,         // précisions comestibilité/toxicité

    // Nullables (et non « JSON vide ») pour rester compatibles avec les fiches d'avant la v6, qui
    // n'ont jamais eu ces informations : null et liste vide s'affichent de la même façon.
    val careCalendarJson: String?,      // List<CareTask> sérialisée : plantation, taille, récolte…
    val usesJson: String?,              // List<SpeciesUse> sérialisée : santé, chimie, parfumerie…
    val symbolism: String?,             // signification symbolique / culturelle

    val gbifKey: Long?,                 // clé taxonomique GBIF de l'espèce retenue (Pl@ntNet)
    val iucnCategory: String?,          // code UICN de l'espèce retenue (Pl@ntNet)

    // Consommation de l'appel IA de cette identification (v7). Le modèle est stocké, pas le coût :
    // celui-ci est recalculé à l'affichage, donc une mise à jour des tarifs corrige l'historique.
    // Toutes nullables : les fiches d'avant la v7 n'ont jamais eu ces mesures.
    val usageModel: String?,
    val usageInputTokens: Int?,
    val usageOutputTokens: Int?,

    val alternativesJson: String,       // List<SpeciesCandidate> sérialisée
    val sourcesDisagree: Boolean,

    val isFavorite: Boolean = false,
    val notes: String? = null,

    // Espèce validée manuellement par l'utilisateur parmi les hypothèses (§2.4).
    val userConfirmed: Boolean = false,
)
