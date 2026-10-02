package ch.electromel.plantinfo.data.remote.gbif

import ch.electromel.plantinfo.data.remote.fetch
import ch.electromel.plantinfo.domain.model.LatLng
import ch.electromel.plantinfo.domain.model.SpeciesRange
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

    /**
     * @param gbifKey clé taxonomique déjà connue (rapportée par Pl@ntNet). Quand elle est fournie,
     *   l'étape species/match est sautée : une requête réseau de moins et surtout aucun risque que
     *   la correspondance floue par nom retombe sur un homonyme.
     */
    suspend fun fetchRange(
        scientificName: String,
        gbifKey: Long? = null,
    ): SpeciesRange {
        val usageKey = gbifKey ?: matchTaxon(scientificName)
            ?: return SpeciesRange(scientificName, emptyList(), hasData = false)

        val points = fetchOccurrences(usageKey)
        return SpeciesRange(scientificName, points, hasData = points.isNotEmpty())
    }

    private suspend fun matchTaxon(scientificName: String): Long? {
        val url = "https://api.gbif.org/v1/species/match".toHttpUrl().newBuilder()
            .addQueryParameter("name", scientificName)
            .build()
        val body = execute(Request.Builder().url(url).build())
        val match = json.decodeFromString<MatchDto>(body)
        return acceptedUsageKey(match.usageKey, match.matchType, match.rank)
    }

    private suspend fun fetchOccurrences(usageKey: Long): List<LatLng> {
        val url = "https://api.gbif.org/v1/occurrence/search".toHttpUrl().newBuilder()
            .addQueryParameter("taxonKey", usageKey.toString())
            .addQueryParameter("hasCoordinate", "true")
            .addQueryParameter("hasGeospatialIssue", "false")
            .addQueryParameter("occurrenceStatus", "PRESENT")
            .apply { WILD_BASIS_OF_RECORD.forEach { addQueryParameter("basisOfRecord", it) } }
            .addQueryParameter("limit", "300")
            .build()
        val body = execute(Request.Builder().url(url).build())
        // Près d'un mégaoctet de JSON pour 300 occurrences : le décoder hors du thread appelant.
        return withContext(Dispatchers.Default) {
            json.decodeFromString<OccurrenceSearchDto>(body).results.orEmpty().mapNotNull { r ->
                val lat = r.decimalLatitude ?: return@mapNotNull null
                val lng = r.decimalLongitude ?: return@mapNotNull null
                LatLng(lat, lng)
            }
        }
    }

    private suspend fun execute(request: Request): String {
        val reply = client.fetch(request)
        if (!reply.isSuccessful) throw IOException("GBIF HTTP ${reply.code}")
        return reply.body
    }

    @Serializable
    private data class MatchDto(
        val usageKey: Long? = null,
        val matchType: String? = null,
        val rank: String? = null,
    )

    @Serializable
    private data class OccurrenceSearchDto(val results: List<OccurrenceDto>? = null)

    @Serializable
    private data class OccurrenceDto(
        val decimalLatitude: Double? = null,
        val decimalLongitude: Double? = null,
    )

    internal companion object {
        /**
         * Observations de terrain et spécimens récoltés, à l'exclusion des `LIVING_SPECIMEN` (jardins
         * botaniques, collections vivantes) et des fossiles : ceux-là situent la plante là où on la
         * cultive ou là où elle a vécu, pas là où elle pousse aujourd'hui, et étiraient l'enveloppe
         * jusqu'à d'autres continents.
         */
        val WILD_BASIS_OF_RECORD = listOf(
            "HUMAN_OBSERVATION", "OBSERVATION", "MACHINE_OBSERVATION",
            "PRESERVED_SPECIMEN", "MATERIAL_SAMPLE", "OCCURRENCE",
        )

        private val SPECIES_OR_BELOW = setOf(
            "SPECIES", "SUBSPECIES", "VARIETY", "FORM", "INFRASPECIFIC_NAME", "INFRASUBSPECIFIC_NAME",
            "HYBRID", "CULTIVAR", "ABERRATION", "STRAIN",
        )

        /**
         * Clé GBIF à retenir pour un nom, ou null si la correspondance ne désigne pas **l'espèce**.
         *
         * Un nom que GBIF ne connaît pas rend `HIGHERRANK` avec la clé du *genre* (« Lavandula
         * inventus » → Lavandula) : l'aire affichée serait celle de tout le genre, présentée comme
         * celle de l'espèce. Le rang est donc vérifié en plus du type de correspondance.
         */
        fun acceptedUsageKey(usageKey: Long?, matchType: String?, rank: String?): Long? {
            if (usageKey == null) return null
            if (matchType != "EXACT" && matchType != "FUZZY") return null
            if (rank != null && rank !in SPECIES_OR_BELOW) return null
            return usageKey
        }
    }
}
