package com.plantinfo.data.remote.plantnet

import com.plantinfo.data.remote.ai.AiImage
import com.plantinfo.domain.model.SpeciesCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
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
     */
    suspend fun identify(
        images: List<AiImage>,
        organs: List<String>,
        apiKey: String,
    ): PlantNetResult = withContext(Dispatchers.IO) {
        val bodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
        images.forEachIndexed { i, img ->
            val mediaType = img.mimeType.toMediaType()
            bodyBuilder.addFormDataPart(
                "images", "photo_$i.jpg", img.bytes.toRequestBody(mediaType),
            )
            val organ = organs.getOrElse(i) { "auto" }
            bodyBuilder.addFormDataPart("organs", organ)
        }
        // lang=fr : noms vernaculaires en français plutôt que le défaut anglais de l'API.
        val request = Request.Builder()
            .url("https://my-api.plantnet.org/v2/identify/all?api-key=$apiKey&nb-results=5&lang=fr")
            .post(bodyBuilder.build())
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val err = when (response.code) {
                        401, 403 -> PlantNetError.INVALID_KEY
                        404 -> PlantNetError.NO_MATCH
                        429 -> PlantNetError.QUOTA
                        in 500..599 -> PlantNetError.SERVER
                        else -> PlantNetError.UNKNOWN
                    }
                    return@withContext PlantNetResult(emptyList(), err)
                }
                val dto = json.decodeFromString<PlantNetResponse>(raw)
                val candidates = dto.results.orEmpty().mapNotNull { r ->
                    val sci = r.species?.scientificNameWithoutAuthor ?: return@mapNotNull null
                    SpeciesCandidate(
                        scientificName = sci,
                        commonName = r.species.commonNames?.firstOrNull(),
                        score = ((r.score ?: 0.0) * 100).toInt().coerceIn(0, 100),
                        gbifKey = r.gbif?.id?.contentOrNull?.toLongOrNull(),
                        iucnCategory = r.iucn?.category?.takeIf { it.isNotBlank() },
                    )
                }
                PlantNetResult(candidates, if (candidates.isEmpty()) PlantNetError.NO_MATCH else null)
            }
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
    suspend fun testKey(apiKey: String): PlantNetError? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://my-api.plantnet.org/v2/identify/all?api-key=$apiKey")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    401, 403 -> PlantNetError.INVALID_KEY
                    429 -> PlantNetError.QUOTA
                    in 500..599 -> PlantNetError.SERVER
                    else -> null // 400/405 = clé acceptée mais requête incomplète → OK
                }
            }
        } catch (e: IOException) {
            PlantNetError.NETWORK
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
