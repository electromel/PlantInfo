package ch.electromel.plantinfo.data.remote.ai

import ch.electromel.plantinfo.util.AppLanguage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Décodage des réponses des trois familles de clients IA (Claude, Gemini, compatibles OpenAI), sans
 * réseau : un intercepteur OkHttp sert une réponse enregistrée.
 *
 * Les tests préfixés « DEFAUT CONNU » figent un comportement fautif relevé par l'audit du
 * 2026-11-10 (voir `log/2026.11.10-Audit v2 - rapport.md`). Ils servent de filet : la correction
 * devra les inverser, pas les supprimer.
 */
class AiClientResponseTest {

    /** Client HTTP qui répond toujours [body] avec le code [code], sans toucher au réseau. */
    private fun http(body: String, code: Int = 200): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("")
                .body(body.toResponseBody())
                .build()
        }
        .build()

    // Sans image : l'encodage base64 passe par android.util.Base64, absent des tests JVM.
    private val input = AiAnalysisInput(
        images = emptyList(),
        plantNetCandidates = emptyList(),
        gps = null,
        language = AppLanguage.FRENCH,
    )

    private val ficheJson =
        """{"commonName":"Chêne pédonculé","scientificName":"Quercus robur","confidence":88,""" +
            """"isFungus":false,"isProtected":false,"toxic":false,"edible":false}"""

    private fun geminiEnvelope(text: String): String = buildJsonObject {
        putJsonArray("candidates") {
            addJsonObject {
                putJsonObject("content") {
                    putJsonArray("parts") { addJsonObject { put("text", text) } }
                }
            }
        }
        putJsonObject("usageMetadata") {
            put("promptTokenCount", 1000)
            put("candidatesTokenCount", 200)
            put("thoughtsTokenCount", 50)
        }
    }.toString()

    private fun openAiEnvelope(text: String): String = buildJsonObject {
        putJsonArray("choices") {
            addJsonObject { putJsonObject("message") { put("content", text) } }
        }
        putJsonObject("usage") {
            put("prompt_tokens", 900)
            put("completion_tokens", 100)
            put("total_tokens", 1300) // 300 jetons de raisonnement comptés à part
        }
    }.toString()

    private fun claudeEnvelope(text: String): String = buildJsonObject {
        putJsonArray("content") { addJsonObject { put("type", "text"); put("text", text) } }
        putJsonObject("usage") { put("input_tokens", 800); put("output_tokens", 150) }
    }.toString()

    private fun failureOf(block: suspend () -> Unit): Throwable? =
        runCatching { runBlocking { block() } }.exceptionOrNull()

    // --- Réponses nominales ---

    @Test
    fun `Gemini - la fiche est decodee et le raisonnement compte dans la sortie`() = runBlocking {
        val analysis = GeminiClient(http(geminiEnvelope(ficheJson))).analyze(input, "cle")

        assertEquals("Quercus robur", analysis.scientificName)
        assertEquals(88, analysis.confidence)
        assertEquals(1000, analysis.usage?.inputTokens)
        assertEquals(250, analysis.usage?.outputTokens)
    }

    @Test
    fun `Gemini - une reponse entouree de texte reste lisible`() = runBlocking {
        val wrapped = "Voici la fiche :\n```json\n$ficheJson\n```"
        val analysis = GeminiClient(http(geminiEnvelope(wrapped))).analyze(input, "cle")

        assertEquals("Quercus robur", analysis.scientificName)
    }

    @Test
    fun `compatible OpenAI - la sortie facturee inclut le raisonnement`() = runBlocking {
        val client = OpenAiCompatibleClient(http(openAiEnvelope(ficheJson)), CompatibleProviderConfig.OPENAI)
        val analysis = client.analyze(input, "cle")

        assertEquals("Quercus robur", analysis.scientificName)
        assertEquals(900, analysis.usage?.inputTokens)
        assertEquals(400, analysis.usage?.outputTokens)
    }

    @Test
    fun `Claude - la reponse a une question est rendue avec sa consommation`() = runBlocking {
        val answer = ClaudeClient(http(claudeEnvelope("  Arrosez peu.  "))).ask("question", "cle")

        assertEquals("Arrosez peu.", answer.text)
        assertEquals(800, answer.usage?.inputTokens)
        assertEquals(150, answer.usage?.outputTokens)
    }

    // --- Erreurs typées ---

    @Test
    fun `un code HTTP d'erreur devient une AiException au motif adequat`() {
        val quota = failureOf { GeminiClient(http("{}", 429)).ask("q", "cle") }
        val key = failureOf { ClaudeClient(http("{}", 401)).ask("q", "cle") }
        val billing = failureOf {
            OpenAiCompatibleClient(http("""{"error":"insufficient_quota"}""", 429), CompatibleProviderConfig.OPENAI)
                .ask("q", "cle")
        }

        assertEquals(AiFailureReason.QUOTA, (quota as AiException).reason)
        assertEquals(AiFailureReason.INVALID_KEY, (key as AiException).reason)
        assertEquals(AiFailureReason.BILLING, (billing as AiException).reason)
    }

    @Test
    fun `une fiche qui n'est pas du JSON leve PARSE`() {
        val failure = failureOf { GeminiClient(http(geminiEnvelope("Je ne sais pas."))).analyze(input, "cle") }

        assertEquals(AiFailureReason.PARSE, (failure as AiException).reason)
    }

    // --- Défauts connus (audit du 2026-11-10) ---

    @Test
    fun `DEFAUT CONNU F4 - une reponse 200 non JSON leve une exception non typee`() {
        val html = "<html><body>Portail captif</body></html>"
        val failures = listOf(
            failureOf { GeminiClient(http(html)).ask("q", "cle") },
            failureOf { ClaudeClient(http(html)).ask("q", "cle") },
            failureOf { OpenAiCompatibleClient(http(html), CompatibleProviderConfig.OPENAI).ask("q", "cle") },
        )

        failures.forEach { failure ->
            assertNotNull(failure)
            // Attendu après correction : AiException(PARSE), seule exception que l'orchestrateur attrape.
            assertFalse(failure is AiException)
        }
    }

    @Test
    fun `DEFAUT CONNU F5 - une reponse Gemini sans contenu est decodee comme une fiche vide`() = runBlocking {
        val blocked = """{"promptFeedback":{"blockReason":"SAFETY"},"usageMetadata":{"promptTokenCount":10}}"""

        // Attendu après correction : AiException(PARSE), pour que le repli passe au fournisseur suivant.
        val analysis = GeminiClient(http(blocked)).analyze(input, "cle")

        assertEquals("", analysis.scientificName)
        assertEquals(0, analysis.confidence)
    }

    @Test
    fun `DEFAUT CONNU F5 - une reponse sans contenu a une question affiche l'enveloppe brute`() = runBlocking {
        val empty = """{"choices":[],"usage":{"prompt_tokens":5}}"""

        // Attendu après correction : AiException(PARSE).
        val answer = OpenAiCompatibleClient(http(empty), CompatibleProviderConfig.OPENAI).ask("q", "cle")

        assertTrue(answer.text.contains("\"choices\""))
    }

    @Test
    fun `DEFAUT CONNU F6 - une cle avec un caractere non ASCII leve une exception non typee`() {
        val pasted = "AIza cle" // espace insécable, typique d'un copier-coller depuis une page web
        val failures = listOf(
            failureOf { GeminiClient(http("{}")).testKey(pasted) },
            failureOf { ClaudeClient(http("{}")).testKey(pasted) },
            failureOf { OpenAiCompatibleClient(http("{}"), CompatibleProviderConfig.OPENAI).testKey(pasted) },
        )

        failures.forEach { failure ->
            // Attendu après correction : clé nettoyée ou refusée proprement, sans exception brute.
            assertTrue(failure is IllegalArgumentException)
        }
    }
}
