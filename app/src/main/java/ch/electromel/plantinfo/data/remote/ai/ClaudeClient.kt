package ch.electromel.plantinfo.data.remote.ai

import ch.electromel.plantinfo.domain.model.AiProviderType
import ch.electromel.plantinfo.domain.model.TokenUsage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/** Client Anthropic Messages API (blocs image base64). */
@Singleton
class ClaudeClient @Inject constructor(
    private val client: OkHttpClient,
) : AiProvider {

    override val type = AiProviderType.CLAUDE
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun analyze(input: AiAnalysisInput, apiKey: String): AiAnalysis {
        val instruction = AiPrompt.buildInstruction(input)
        val body = buildJsonObject {
            put("model", MODEL)
            // La fiche complète (calendrier, multiplication, usages, photos à prendre…) dépasse
            // 1500 jetons en français : une réponse tronquée serait un JSON illisible.
            put("max_tokens", 4000)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        input.images.forEach { img ->
                            addJsonObject {
                                put("type", "image")
                                putJsonObject("source") {
                                    put("type", "base64")
                                    put("media_type", img.mimeType)
                                    put("data", HttpSupport.base64(img.bytes))
                                }
                            }
                        }
                        addJsonObject {
                            put("type", "text")
                            put("text", instruction)
                        }
                    }
                }
            }
        }
        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", HttpSupport.JSON_MEDIA)
            .post(body.toString().toRequestBody())
            .build()

        val response = HttpSupport.execute(client, request, "Claude")
        val text = HttpSupport.parseResponse("Claude") { extractText(response) }
        return AiPrompt.parse(text).copy(usage = extractUsage(response))
    }

    override suspend fun ask(prompt: String, apiKey: String): AiAnswer {
        val body = buildJsonObject {
            put("model", MODEL)
            put("max_tokens", 1000)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        addJsonObject { put("type", "text"); put("text", prompt) }
                    }
                }
            }
        }
        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", HttpSupport.JSON_MEDIA)
            .post(body.toString().toRequestBody())
            .build()
        val response = HttpSupport.execute(client, request, "Claude")
        val text = HttpSupport.parseResponse("Claude") { extractText(response) }
        return AiAnswer(text.trim(), extractUsage(response))
    }

    override suspend fun testKey(apiKey: String): Boolean {
        // Appel minimal : un simple "ping" avec max_tokens=1 suffit à valider l'authentification.
        val body = buildJsonObject {
            put("model", MODEL)
            put("max_tokens", 1)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        addJsonObject { put("type", "text"); put("text", "ping") }
                    }
                }
            }
        }
        val request = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", HttpSupport.JSON_MEDIA)
            .post(body.toString().toRequestBody())
            .build()
        HttpSupport.execute(client, request, "Claude")
        return true
    }

    /** Bloc `usage` de la réponse Anthropic ; null si absent ou vide (rien à afficher). */
    private fun extractUsage(response: String): TokenUsage? {
        val usage = runCatching {
            json.parseToJsonElement(response).jsonObject["usage"]?.jsonObject
        }.getOrNull() ?: return null
        return TokenUsage(
            model = MODEL,
            inputTokens = usage["input_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            outputTokens = usage["output_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
        ).takeIf { !it.isEmpty }
    }

    private fun extractText(response: String): String {
        val obj = json.parseToJsonElement(response).jsonObject
        // Sans bloc de contenu il n'y a pas de réponse : renvoyer le corps brut ferait afficher du
        // JSON à l'utilisateur dans le Q&A, et passer un objet vide pour une identification.
        val content = obj["content"]?.jsonArray
            ?: throw AiException(AiFailureReason.PARSE, "Claude : réponse sans contenu")
        return content.joinToString("\n") { block ->
            (block as? JsonObject)?.get("text")?.let { it as? JsonPrimitive }?.contentOrNull ?: ""
        }
    }

    private companion object {
        const val MODEL = "claude-sonnet-5"
    }
}
