package com.plantinfo.data.repo

import android.util.Log
import com.plantinfo.data.db.SpeciesRangeCacheEntity
import com.plantinfo.data.db.SpeciesRangeDao
import com.plantinfo.data.remote.gbif.GbifClient
import com.plantinfo.domain.model.LatLng
import com.plantinfo.domain.model.SpeciesRange
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fournit l'aire de répartition d'une espèce en privilégiant le cache local **par espèce** (§3),
 * pour éviter de rappeler GBIF à chaque consultation d'une même espèce dans l'historique.
 *
 * Politique : cache valable 90 jours. Un échec réseau n'est jamais mis en cache (retour d'un
 * résultat vide non persistant) afin de réessayer plus tard ; en revanche une réponse GBIF légitime
 * « aucune donnée » est mise en cache pour ne pas insister inutilement.
 */
@Singleton
class RangeRepository @Inject constructor(
    private val dao: SpeciesRangeDao,
    private val gbifClient: GbifClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val pointSerializer = ListSerializer(LatLng.serializer())

    suspend fun getRange(scientificName: String): SpeciesRange {
        val key = scientificName.trim()
        if (key.isBlank()) return SpeciesRange(scientificName, emptyList(), hasData = false)

        // 1. Cache frais ?
        dao.get(key)?.let { cached ->
            if (System.currentTimeMillis() - cached.fetchedAt < CACHE_TTL_MS) {
                return cached.toRange()
            }
        }

        // 2. Récupération réseau ; en cas d'échec, retomber sur un cache périmé s'il existe.
        return try {
            val range = gbifClient.fetchRange(key)
            dao.upsert(range.toEntity())
            range
        } catch (e: Exception) {
            Log.w(TAG, "GBIF indisponible pour $key : ${e.message}")
            dao.get(key)?.toRange() ?: SpeciesRange(scientificName, emptyList(), hasData = false)
        }
    }

    private fun SpeciesRangeCacheEntity.toRange(): SpeciesRange = SpeciesRange(
        scientificName = scientificName,
        points = runCatching { json.decodeFromString(pointSerializer, pointsJson) }
            .getOrDefault(emptyList()),
        hasData = hasData,
    )

    private fun SpeciesRange.toEntity(): SpeciesRangeCacheEntity = SpeciesRangeCacheEntity(
        scientificName = scientificName,
        pointsJson = json.encodeToString(pointSerializer, points),
        hasData = hasData,
        fetchedAt = System.currentTimeMillis(),
    )

    private companion object {
        val CACHE_TTL_MS = TimeUnit.DAYS.toMillis(90)
        const val TAG = "RangeRepository"
    }
}
