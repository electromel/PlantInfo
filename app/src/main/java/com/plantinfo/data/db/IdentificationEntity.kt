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

    val edible: Boolean?,               // comestibilité pour l'humain (null = inconnu)
    val toxic: Boolean?,                // toxicité pour l'humain (null = inconnu)
    val edibilityNote: String?,         // précisions comestibilité/toxicité

    val alternativesJson: String,       // List<SpeciesCandidate> sérialisée
    val sourcesDisagree: Boolean,

    val isFavorite: Boolean = false,
    val notes: String? = null,

    // Espèce validée manuellement par l'utilisateur parmi les hypothèses (§2.4).
    val userConfirmed: Boolean = false,
)
