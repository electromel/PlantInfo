package com.plantinfo.domain.model

import kotlinx.serialization.Serializable

/** Point géographique simple (indépendant de la bibliothèque de carte). */
@Serializable
data class LatLng(val lat: Double, val lng: Double)

/**
 * Aire de répartition approximative d'une espèce, dérivée des occurrences GBIF (§2.4).
 * [points] est un échantillon d'occurrences ; l'UI en trace une enveloppe semi-transparente.
 * [hasData] = false quand GBIF ne connaît pas l'espèce → n'afficher que le point de prise de vue.
 */
data class SpeciesRange(
    val scientificName: String,
    val points: List<LatLng>,
    val hasData: Boolean,
)
