package ch.electromel.plantinfo.domain.model

import kotlinx.serialization.Serializable

/** Point géographique simple (indépendant de la bibliothèque de carte). */
@Serializable
data class LatLng(val lat: Double, val lng: Double)

/**
 * Aire de répartition approximative d'une espèce, dérivée des occurrences GBIF (§2.4).
 * [points] est un échantillon d'occurrences, qui ne sert qu'à **cadrer** la carte : la zone tracée
 * vient des tuiles de densité GBIF de [usageKey], calculées sur toutes les occurrences.
 * [hasData] = false quand GBIF ne connaît pas l'espèce → n'afficher que le point de prise de vue.
 * [usageKey] est la clé taxonomique GBIF ; null pour une ligne de cache d'avant son enregistrement.
 */
data class SpeciesRange(
    val scientificName: String,
    val points: List<LatLng>,
    val hasData: Boolean,
    val usageKey: Long? = null,
)
