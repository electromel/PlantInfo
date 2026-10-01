package ch.electromel.plantinfo.data.repo

import android.util.Log
import ch.electromel.plantinfo.data.db.SpeciesRangeCacheEntity
import ch.electromel.plantinfo.data.db.SpeciesRangeDao
import ch.electromel.plantinfo.data.remote.gbif.GbifClient
import ch.electromel.plantinfo.domain.model.LatLng
import ch.electromel.plantinfo.domain.model.SpeciesRange
import kotlinx.coroutines.CancellationException
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

    /**
     * @param gbifKey clé taxonomique GBIF si Pl@ntNet l'a rapportée pour cette espèce (évite la
     *   résolution par nom côté GbifClient). Le cache reste indexé par nom scientifique.
     */
    suspend fun getRange(scientificName: String, gbifKey: Long? = null): SpeciesRange {
        val key = scientificName.trim()
        if (key.isBlank()) return SpeciesRange(scientificName, emptyList(), hasData = false)

        // 1. Cache frais ?
        dao.get(key)?.let { cached ->
            if (isCacheFresh(cached, System.currentTimeMillis())) return cached.toRange()
        }

        // 2. Récupération réseau ; en cas d'échec, retomber sur un cache périmé s'il existe.
        return try {
            val range = gbifClient.fetchRange(key, gbifKey)
            dao.upsert(range.toEntity())
            range
        } catch (e: CancellationException) {
            throw e
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
        usageKey = usageKey,
    )

    private fun SpeciesRange.toEntity(): SpeciesRangeCacheEntity = SpeciesRangeCacheEntity(
        scientificName = scientificName,
        pointsJson = json.encodeToString(pointSerializer, points),
        hasData = hasData,
        fetchedAt = System.currentTimeMillis(),
        usageKey = usageKey,
    )

    private companion object {
        const val TAG = "RangeRepository"
    }
}

private val CACHE_TTL_MS = TimeUnit.DAYS.toMillis(90)

/**
 * Une ligne de cache sert tant qu'elle a moins de 90 jours **et** qu'elle porte la clé GBIF dont la
 * carte a besoin pour tracer les zones d'observation. Une espèce connue sans clé date d'avant la
 * v10 de la base : on la redemande une fois plutôt que d'afficher une carte sans zone.
 */
internal fun isCacheFresh(cached: SpeciesRangeCacheEntity, nowMs: Long): Boolean =
    nowMs - cached.fetchedAt < CACHE_TTL_MS && !(cached.hasData && cached.usageKey == null)
