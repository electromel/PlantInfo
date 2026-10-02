package ch.electromel.plantinfo

import ch.electromel.plantinfo.data.db.IdentificationEntity
import ch.electromel.plantinfo.data.remote.ai.AiAnalysisInput
import ch.electromel.plantinfo.util.AppLanguage
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import java.io.IOException

/** Faux serveur HTTP : un intercepteur qui répond à la place du réseau, sans MockWebServer. */
object FakeHttp {

    /** Client dont chaque requête est servie par [handler]. Lever une IOException simule une panne. */
    fun client(handler: (Request) -> Response): OkHttpClient =
        OkHttpClient.Builder().addInterceptor { chain -> handler(chain.request()) }.build()

    fun json(request: Request, body: String, code: Int = 200): Response =
        response(request, code, body.toResponseBody("application/json".toMediaType()))

    fun html(request: Request, body: String, code: Int = 200): Response =
        response(request, code, body.toResponseBody("text/html".toMediaType()))

    /** Réponse dont les en-têtes arrivent, puis dont le corps se coupe en cours de lecture. */
    fun brokenBody(request: Request): Response = response(
        request, 200,
        object : ResponseBody() {
            override fun contentType() = "application/json".toMediaType()
            override fun contentLength() = -1L
            override fun source(): BufferedSource = object : Source {
                override fun read(sink: Buffer, byteCount: Long): Long =
                    throw IOException("Connection reset by peer")
                override fun timeout() = Timeout.NONE
                override fun close() = Unit
            }.buffer()
        },
    )

    private fun response(request: Request, code: Int, body: ResponseBody): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("test")
            .body(body)
            .build()
}

/** Entrée IA minimale : pas de photo, donc aucun encodage base64 dans les tests. */
fun aiInput(language: AppLanguage = AppLanguage.FRENCH) = AiAnalysisInput(
    images = emptyList(),
    plantNetCandidates = emptyList(),
    gps = null,
    language = language,
)

/** Réponse JSON d'identification minimale mais valide. */
const val VALID_ANALYSIS = """{"commonName":"Rosier des chiens","scientificName":"Rosa canina","confidence":80}"""

/** Fiche d'historique type, avec un sujet bénin ; chaque test ne surcharge que ce qui l'intéresse. */
fun testEntity(
    id: Long = 7,
    scientificName: String = "Rosa canina",
    commonName: String = "Rosier des chiens",
    isFungus: Boolean = false,
    isProtected: Boolean = false,
    edible: Boolean? = null,
    toxic: Boolean? = null,
    isFavorite: Boolean = false,
    notes: String? = null,
    photoPaths: List<String> = listOf("/data/photo1.jpg"),
    latitude: Double? = 46.2,
    longitude: Double? = 6.15,
) = IdentificationEntity(
    id = id,
    dateTime = 1_700_000_000_000,
    photoPaths = photoPaths,
    latitude = latitude,
    longitude = longitude,
    altitude = 400.0,
    gpsAccuracy = 12f,
    commonName = commonName,
    scientificName = scientificName,
    scorePlantNet = 62,
    scoreAi = 71,
    scoreFinal = 68,
    aiProvider = "GEMINI",
    isProtected = isProtected,
    isFungus = isFungus,
    healthStatus = null,
    isHealthy = null,
    recommendations = emptyList(),
    habitat = null,
    description = null,
    matureHeight = null,
    matureDiameter = null,
    timeToMaturity = null,
    edible = edible,
    toxic = toxic,
    edibilityNote = null,
    careCalendarJson = null,
    usesJson = null,
    propagationJson = null,
    photoSuggestionsJson = null,
    symbolism = null,
    gbifKey = null,
    iucnCategory = null,
    usageModel = null,
    usageInputTokens = null,
    usageOutputTokens = null,
    alternativesJson = "[]",
    sourcesDisagree = false,
    isFavorite = isFavorite,
    notes = notes,
)
