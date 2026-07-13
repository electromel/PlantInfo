package com.plantinfo.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cache local des données de répartition GBIF, indexé **par espèce** (nom scientifique) et non par
 * observation (§3), pour éviter un appel réseau répété à chaque consultation d'une même espèce.
 */
@Entity(tableName = "species_range_cache")
data class SpeciesRangeCacheEntity(
    @PrimaryKey val scientificName: String,
    val pointsJson: String, // List<LatLng> sérialisée (échantillon d'occurrences)
    val hasData: Boolean,   // false = GBIF ne connaît pas l'espèce
    val fetchedAt: Long,
)
