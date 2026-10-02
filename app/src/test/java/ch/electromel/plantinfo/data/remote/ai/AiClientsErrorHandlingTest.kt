package ch.electromel.plantinfo.data.remote.ai

import ch.electromel.plantinfo.FakeHttp
import ch.electromel.plantinfo.VALID_ANALYSIS
import ch.electromel.plantinfo.aiInput
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/**
 * Aucune réponse de fournisseur — coupée, illisible, bloquée, au format inattendu — ne doit sortir
 * d'un client autrement qu'en [AiException] typée : c'est ce que l'orchestrateur sait traiter, et
 * tout le reste faisait tomber l'application.
 */
class AiClientsErrorHandlingTest {

    private val claude = { client: OkHttpClient -> ClaudeClient(client) }
    private val gemini = { client: OkHttpClient -> GeminiClient(client) }
    private val gpt = { client: OkHttpClient -> OpenAiClient(client) }
    private val mistral = { client: OkHttpClient ->
        OpenAiCompatibleClient(client, CompatibleProviderConfig.MISTRAL)
    }
    private val allClients: Map<String, (OkHttpClient) -> AiProvider> =
        mapOf("Claude" to claude, "Gemini" to gemini, "GPT" to gpt, "Mistral" to mistral)

    /** Lève l'AiException attendue et en rend le motif ; échoue si autre chose est levé. */
    private fun reasonOf(block: suspend () -> Unit): AiFailureReason = runBlocking {
        try {
            block()
            fail("une AiException était attendue")
            error("inatteignable")
        } catch (e: AiException) {
            e.reason
        }
    }

    // --- Réseau ---

    @Test
    fun `une coupure pendant la lecture du corps est une erreur reseau typee`() {
        allClients.forEach { (name, make) ->
            val client = make(FakeHttp.client { FakeHttp.brokenBody(it) })

            assertEquals(name, AiFailureReason.NETWORK, reasonOf { client.analyze(aiInput(), "k") })
            assertEquals(name, AiFailureReason.NETWORK, reasonOf { client.ask("question", "k") })
            assertEquals(name, AiFailureReason.NETWORK, reasonOf { client.testKey("k") })
        }
    }

    @Test
    fun `une panne a l'ouverture de la connexion est une erreur reseau typee`() {
        allClients.forEach { (name, make) ->
            val client = make(FakeHttp.client { throw IOException("Unable to resolve host") })

            assertEquals(name, AiFailureReason.NETWORK, reasonOf { client.analyze(aiInput(), "k") })
        }
    }

    // --- Format de réponse ---

    @Test
    fun `une page de portail captif en 200 est une reponse illisible, pas une exception brute`() {
        allClients.forEach { (name, make) ->
            val client = make(FakeHttp.client { FakeHttp.html(it, "<html><body>Wi-Fi login</body></html>") })

            assertEquals(name, AiFailureReason.PARSE, reasonOf { client.analyze(aiInput(), "k") })
            assertEquals(name, AiFailureReason.PARSE, reasonOf { client.ask("question", "k") })
        }
    }

    @Test
    fun `un JSON qui n'est pas un objet est une reponse illisible`() {
        allClients.forEach { (name, make) ->
            val client = make(FakeHttp.client { FakeHttp.json(it, "[1, 2, 3]") })

            assertEquals(name, AiFailureReason.PARSE, reasonOf { client.analyze(aiInput(), "k") })
        }
    }

    @Test
    fun `une reponse sans contenu n'est jamais rendue comme du texte`() {
        // Avant : le corps brut était renvoyé tel quel — du JSON affiché comme réponse du Q&A.
        allClients.forEach { (name, make) ->
            val client = make(FakeHttp.client { FakeHttp.json(it, """{"id":"abc","object":"response"}""") })

            assertEquals(name, AiFailureReason.PARSE, reasonOf { client.ask("question", "k") })
        }
    }

    @Test
    fun `une demande bloquee par Gemini est un echec, pas une identification vide`() {
        val client = GeminiClient(
            FakeHttp.client {
                FakeHttp.json(it, """{"promptFeedback":{"blockReason":"SAFETY"},"usageMetadata":{"promptTokenCount":10}}""")
            },
        )

        assertEquals(AiFailureReason.PARSE, reasonOf { client.analyze(aiInput(), "k") })
        val message = runBlocking {
            try {
                client.ask("question", "k"); ""
            } catch (e: AiException) {
                e.message.orEmpty()
            }
        }
        assertTrue("le motif du blocage doit figurer dans le message : $message", message.contains("SAFETY"))
    }

    @Test
    fun `un JSON valide sans espece n'est pas une identification`() {
        val client = ClaudeClient(FakeHttp.client { FakeHttp.json(it, claudeReply("{}")) })

        assertEquals(AiFailureReason.PARSE, reasonOf { client.analyze(aiInput(), "k") })
    }

    // --- Cas valides, pour ne pas avoir durci à l'excès ---

    @Test
    fun `une reponse correcte est toujours lue`() {
        val analysis = runBlocking {
            ClaudeClient(FakeHttp.client { FakeHttp.json(it, claudeReply(VALID_ANALYSIS)) })
                .analyze(aiInput(), "k")
        }

        assertEquals("Rosa canina", analysis.scientificName)
        assertEquals(80, analysis.confidence)
        assertEquals(120, analysis.usage?.inputTokens)
    }

    @Test
    fun `Gemini - la reponse correcte est lue`() {
        val body = buildJsonObject {
            putJsonArray("candidates") {
                addJsonObject {
                    putJsonObject("content") { putJsonArray("parts") { addJsonObject { put("text", VALID_ANALYSIS) } } }
                }
            }
        }.toString()

        val analysis = runBlocking {
            GeminiClient(FakeHttp.client { FakeHttp.json(it, body) }).analyze(aiInput(), "k")
        }

        assertEquals("Rosa canina", analysis.scientificName)
    }

    @Test
    fun `un content en liste de blocs est lu comme un texte`() {
        val body = buildJsonObject {
            putJsonArray("choices") {
                addJsonObject {
                    putJsonObject("message") {
                        putJsonArray("content") {
                            addJsonObject { put("type", "text"); put("text", VALID_ANALYSIS) }
                        }
                    }
                }
            }
        }.toString()

        val analysis = runBlocking {
            OpenAiCompatibleClient(FakeHttp.client { FakeHttp.json(it, body) }, CompatibleProviderConfig.MISTRAL)
                .analyze(aiInput(), "k")
        }

        assertEquals("Rosa canina", analysis.scientificName)
    }

    @Test
    fun `un content null est une reponse illisible`() {
        val body = """{"choices":[{"message":{"role":"assistant","content":null}}]}"""
        val client = OpenAiClient(FakeHttp.client { FakeHttp.json(it, body) })

        assertEquals(AiFailureReason.PARSE, reasonOf { client.analyze(aiInput(), "k") })
    }

    @Test
    fun `les codes HTTP gardent leur motif`() {
        val client = ClaudeClient(FakeHttp.client { FakeHttp.json(it, """{"error":"x"}""", code = 401) })
        assertEquals(AiFailureReason.INVALID_KEY, reasonOf { client.testKey("k") })

        val limited = ClaudeClient(FakeHttp.client { FakeHttp.json(it, """{"error":"x"}""", code = 429) })
        assertEquals(AiFailureReason.QUOTA, reasonOf { limited.testKey("k") })

        val down = ClaudeClient(FakeHttp.client { FakeHttp.json(it, "oops", code = 503) })
        assertEquals(AiFailureReason.SERVER, reasonOf { down.testKey("k") })
    }

    private fun claudeReply(text: String): String = buildJsonObject {
        putJsonArray("content") { addJsonObject { put("type", "text"); put("text", text) } }
        putJsonObject("usage") { put("input_tokens", 120); put("output_tokens", 60) }
    }.toString()
}
