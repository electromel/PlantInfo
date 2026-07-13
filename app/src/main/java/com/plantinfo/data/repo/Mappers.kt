package com.plantinfo.data.repo

import com.plantinfo.data.db.IdentificationEntity
import com.plantinfo.domain.model.AiProviderType
import com.plantinfo.domain.model.GpsLocation
import com.plantinfo.domain.model.HealthAssessment
import com.plantinfo.domain.model.IdentificationResult
import com.plantinfo.domain.model.SpeciesCandidate
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }
private val candidateListSerializer = ListSerializer(SpeciesCandidate.serializer())

/** Convertit un résultat de domaine + contexte en entité persistable. */
fun IdentificationResult.toEntity(
    gps: GpsLocation?,
    photoPaths: List<String>,
    provider: AiProviderType,
    dateTime: Long,
): IdentificationEntity = IdentificationEntity(
    dateTime = dateTime,
    photoPaths = photoPaths,
    latitude = gps?.latitude,
    longitude = gps?.longitude,
    altitude = gps?.altitude,
    gpsAccuracy = gps?.accuracyMeters,
    commonName = commonName,
    scientificName = scientificName,
    scorePlantNet = scorePlantNet,
    scoreAi = scoreAi,
    scoreFinal = scoreFinal,
    aiProvider = provider.name,
    isProtected = isProtected,
    isFungus = isFungus,
    healthStatus = health?.status,
    isHealthy = health?.isHealthy,
    recommendations = health?.recommendations ?: emptyList(),
    habitat = habitat,
    description = description,
    edible = edible,
    toxic = toxic,
    edibilityNote = edibilityNote,
    alternativesJson = json.encodeToString(candidateListSerializer, alternatives),
    sourcesDisagree = sourcesDisagree,
)

/** Reconstruit un résultat de domaine depuis une entité (pour l'affichage résultat/détail). */
fun IdentificationEntity.toResult(): IdentificationResult {
    val alternatives = runCatching {
        json.decodeFromString(candidateListSerializer, alternativesJson)
    }.getOrDefault(emptyList())
    val health = healthStatus?.let {
        HealthAssessment(it, isHealthy ?: true, recommendations)
    }
    return IdentificationResult(
        commonName = commonName,
        scientificName = scientificName,
        scorePlantNet = scorePlantNet,
        scoreAi = scoreAi,
        scoreFinal = scoreFinal,
        aiProvider = runCatching { AiProviderType.valueOf(aiProvider) }.getOrDefault(AiProviderType.NONE),
        alternatives = alternatives,
        isFungus = isFungus,
        isProtected = isProtected,
        health = health,
        habitat = habitat,
        description = description,
        edible = edible,
        toxic = toxic,
        edibilityNote = edibilityNote,
        sourcesDisagree = sourcesDisagree,
        complementaryPhotoRequest = null, // non persisté ; pertinent uniquement au moment de l'ID
    )
}

fun GpsLocation.toReadableCoordinates(): String {
    val alt = altitude?.let { ", %.0f m".format(it) } ?: ""
    return "%.5f, %.5f%s".format(latitude, longitude, alt)
}

/** Sérialise une liste de candidats pour la colonne alternativesJson. */
fun encodeAlternatives(alternatives: List<SpeciesCandidate>): String =
    json.encodeToString(candidateListSerializer, alternatives)
