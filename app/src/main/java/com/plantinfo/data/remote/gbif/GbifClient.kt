package com.plantinfo.data.remote.gbif

import com.plantinfo.domain.model.LatLng
import com.plantinfo.domain.model.SpeciesRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Client de l'API GBIF (gratuite) pour dériver une aire de répartition approximative à partir des
 * occurrences connues d'une espèce (§2.4). Deux étapes : résolution du taxon (species/match) puis
 * récupération d'un échantillon d'occurrences géolocalisées (occurrence/search).
 *
 * En cas d'échec réseau, lève IOException (le RangeRepository ne met alors rien en cache et
 * réessaiera). Une espèce inconnue de GBIF renvoie un SpeciesRange avec hasData = false.
 */
@Singleton
class GbifClient @Inject constructor(
    private val client: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchRange(scientificName: String): SpeciesRange = withContext(Dispatchers.IO) {
        val usageKey = matchTaxon(scientificName)
            ?: return@withContext SpeciesRange(scientificName, emptyList(), hasData = false)

        val points = fetchOccurrences(usageKey)
        SpeciesRange(scientificName, points, hasData = points.isNotEmpty())
    }

    private fun matchTaxon(scientificName: String): Long? {
        val url = "https://api.gbif.org/v1/species/match".toHttpUrl().newBuilder()
            .addQueryParameter("name", scientificName)
            .build()
        val response = execute(Request.Builder().url(url).build())
        val match = json.decodeFromString<MatchDto>(response)
        return match.usageKey?.takeIf { match.matchType != null && match.matchType != "NONE" }
    }

    private fun fetchOccurrences(usageKey: Long): List<LatLng> {
        val url = "https://api.gbif.org/v1/occurrence/search".toHttpUrl().newBuilder()
            .addQueryParameter("taxonKey", usageKey.toString())
            .addQueryParameter("hasCoordinate", "true")
            .addQueryParameter("hasGeospatialIssue", "false")
            .addQueryParameter("limit", "300")
            .build()
        val response = execute(Request.Builder().url(url).build())
        val dto = json.decodeFromString<OccurrenceSearchDto>(response)
        return dto.results.orEmpty().mapNotNull { r ->
            val lat = r.decimalLatitude ?: return@mapNotNull null
            val lng = r.decimalLongitude ?: return@mapNotNull null
            LatLng(lat, lng)
        }
    }

    private fun execute(request: Request): String {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("GBIF HTTP ${response.code}")
            return response.body?.string().orEmpty()
        }
    }

    @Serializable
    private data class MatchDto(val usageKey: Long? = null, val matchType: String? = null)

    @Serializable
    private data class OccurrenceSearchDto(val results: List<OccurrenceDto>? = null)

    @Serializable
    private data class OccurrenceDto(
        val decimalLatitude: Double? = null,
        val decimalLongitude: Double? = null,
    )
}
