package ch.electromel.plantinfo.data.remote.plantnet

import ch.electromel.plantinfo.data.remote.ai.AiImage
import ch.electromel.plantinfo.data.remote.fetch
import ch.electromel.plantinfo.domain.model.SpeciesCandidate
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Motif d'échec Pl@ntNet. Non bloquant : le pipeline peut continuer en IA seule (champignons). */
enum class PlantNetError { INVALID_KEY, QUOTA, NETWORK, SERVER, NO_MATCH, UNKNOWN }

/** Résultat Pl@ntNet : liste de candidats (peut être vide) + éventuel motif d'erreur. */
data class PlantNetResult(
    val candidates: List<SpeciesCandidate>,
    val error: PlantNetError?,
)

/**
 * Client de l'API Pl@ntNet (§2.3, étape 1). Envoie la/les photo(s) en multipart et renvoie les
 * candidats taxonomiques avec leur score. Bonne couverture plantes/arbres, faible pour champignons.
 */
@Singleton
class PlantNetClient @Inject constructor(
    private val client: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * @param images photos à identifier
     * @param organs indices d'organe alignés sur les images (valeurs Pl@ntNet : leaf, flower, …)
     * @param language code de langue des noms vernaculaires (`fr`, `en`, `de`, `it`, `es`) : celle
     *   de l'application, pour qu'un résultat Pl@ntNet brut ne soit pas dans une autre langue que
     *   l'interface.
     */
    suspend fun identify(
        images: List<AiImage>,
        organs: List<String>,
        apiKey: String,
        language: String,
    ): PlantNetResult {
        val bodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
        images.forEachIndexed { i, img ->
            val mediaType = img.mimeType.toMediaType()
            bodyBuilder.addFormDataPart(
                "images", "photo_$i.jpg", img.bytes.toRequestBody(mediaType),
            )
            val organ = organs.getOrElse(i) { "auto" }
            bodyBuilder.addFormDataPart("organs", organ)
        }
        val request = Request.Builder()
            .url(
                identifyUrl(apiKey)
                    .newBuilder()
                    .addQueryParameter("nb-results", "5")
                    .addQueryParameter("lang", language)
                    .build(),
            )
            .post(bodyBuilder.build())
            .build()

        return try {
            val reply = client.fetch(request)
            if (!reply.isSuccessful) {
                return PlantNetResult(emptyList(), errorForStatus(reply.code))
            }
            val candidates = parseCandidates(reply.body)
            PlantNetResult(candidates, if (candidates.isEmpty()) PlantNetError.NO_MATCH else null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            PlantNetResult(emptyList(), PlantNetError.NETWORK)
        } catch (e: Exception) {
            PlantNetResult(emptyList(), PlantNetError.UNKNOWN)
        }
    }

    /**
     * Valide une clé pour l'écran Paramètres. Pl@ntNet n'offre pas d'endpoint de validation dédié :
     * on interprète le code HTTP d'un appel léger (401/403 → clé invalide ; 400 « image manquante »
     * ou autre → clé acceptée). Renvoie null si la clé semble valide, sinon le motif d'erreur.
     */
    suspend fun testKey(apiKey: String): PlantNetError? {
        val request = Request.Builder().url(identifyUrl(apiKey)).get().build()
        return try {
            when (client.fetch(request).code) {
                401, 403 -> PlantNetError.INVALID_KEY
                429 -> PlantNetError.QUOTA
                in 500..599 -> PlantNetError.SERVER
                else -> null // 400/405 = clé acceptée mais requête incomplète → OK
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            PlantNetError.NETWORK
        }
    }

    internal companion object {
        /**
         * URL de base portant la clé. Construite par `HttpUrl` et non par interpolation : une clé
         * collée avec un caractère réservé (`&`, `#`, espace) casserait sinon la requête, voire en
         * injecterait un paramètre.
         */
        fun identifyUrl(apiKey: String): HttpUrl =
            "https://my-api.plantnet.org/v2/identify/all".toHttpUrl().newBuilder()
                .addQueryParameter("api-key", apiKey)
                .build()

        fun errorForStatus(code: Int): PlantNetError = when (code) {
            401, 403 -> PlantNetError.INVALID_KEY
            404 -> PlantNetError.NO_MATCH
            429 -> PlantNetError.QUOTA
            in 500..599 -> PlantNetError.SERVER
            else -> PlantNetError.UNKNOWN
        }
    }

    internal fun parseCandidates(raw: String): List<SpeciesCandidate> {
        val dto = json.decodeFromString<PlantNetResponse>(raw)
        return dto.results.orEmpty().mapNotNull { r ->
            val sci = r.species?.scientificNameWithoutAuthor ?: return@mapNotNull null
            SpeciesCandidate(
                scientificName = sci,
                commonName = r.species.commonNames?.firstOrNull(),
                score = ((r.score ?: 0.0) * 100).toInt().coerceIn(0, 100),
                gbifKey = r.gbif?.id?.contentOrNull?.toLongOrNull(),
                iucnCategory = r.iucn?.category?.takeIf { it.isNotBlank() },
            )
        }
    }

    @Serializable
    private data class PlantNetResponse(val results: List<PlantNetItem>? = null)

    @Serializable
    private data class PlantNetItem(
        val score: Double? = null,
        val species: PlantNetSpecies? = null,
        val gbif: PlantNetGbif? = null,
        val iucn: PlantNetIucn? = null,
    )

    @Serializable
    private data class PlantNetSpecies(
        val scientificNameWithoutAuthor: String? = null,
        val commonNames: List<String>? = null,
    )

    /** L'API rend `id` tantôt en nombre, tantôt en chaîne : JsonPrimitive absorbe les deux. */
    @Serializable
    private data class PlantNetGbif(val id: JsonPrimitive? = null)

    @Serializable
    private data class PlantNetIucn(val category: String? = null)
}
