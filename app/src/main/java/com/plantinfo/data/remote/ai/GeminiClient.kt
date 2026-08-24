package com.plantinfo.data.remote.ai

import com.plantinfo.domain.model.AiProviderType
import com.plantinfo.domain.model.TokenUsage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
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

/** Client Google Gemini generateContent (parties inlineData). */
@Singleton
class GeminiClient @Inject constructor(
    private val client: OkHttpClient,
) : AiProvider {

    override val type = AiProviderType.GEMINI
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun analyze(input: AiAnalysisInput, apiKey: String): AiAnalysis {
        val instruction = AiPrompt.buildInstruction(input)
        val body = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject { put("text", instruction) }
                        input.images.forEach { img ->
                            addJsonObject {
                                putJsonObject("inline_data") {
                                    put("mime_type", img.mimeType)
                                    put("data", HttpSupport.base64(img.bytes))
                                }
                            }
                        }
                    }
                }
            }
        }
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey")
            .header("content-type", HttpSupport.JSON_MEDIA)
            .post(body.toString().toRequestBody())
            .build()

        val response = HttpSupport.execute(client, request, "Gemini")
        return AiPrompt.parse(extractText(response)).copy(usage = extractUsage(response))
    }

    override suspend fun ask(prompt: String, apiKey: String): AiAnswer {
        val body = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") {
                        addJsonObject { put("text", prompt) }
                    }
                }
            }
        }
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey")
            .header("content-type", HttpSupport.JSON_MEDIA)
            .post(body.toString().toRequestBody())
            .build()
        val response = HttpSupport.execute(client, request, "Gemini")
        return AiAnswer(extractText(response).trim(), extractUsage(response))
    }

    override suspend fun testKey(apiKey: String): Boolean {
        // Mini-génération plutôt que lister les modèles : ce dernier réussit même quand le quota
        // de génération est à zéro, ce qui affichait « Clé valide » pour une clé inutilisable.
        val body = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    putJsonArray("parts") { addJsonObject { put("text", "ping") } }
                }
            }
            putJsonObject("generationConfig") { put("maxOutputTokens", 1) }
        }
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey")
            .header("content-type", HttpSupport.JSON_MEDIA)
            .post(body.toString().toRequestBody())
            .build()
        HttpSupport.execute(client, request, "Gemini")
        return true
    }

    /**
     * Bloc `usageMetadata` de la réponse Gemini. Les jetons de raisonnement (`thoughtsTokenCount`)
     * sont facturés comme de la sortie : on les y ajoute, sans quoi le coût serait sous-estimé.
     */
    private fun extractUsage(response: String): TokenUsage? {
        val usage = runCatching {
            json.parseToJsonElement(response).jsonObject["usageMetadata"]?.jsonObject
        }.getOrNull() ?: return null
        fun count(name: String) = usage[name]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        return TokenUsage(
            model = MODEL,
            inputTokens = count("promptTokenCount"),
            outputTokens = count("candidatesTokenCount") + count("thoughtsTokenCount"),
        ).takeIf { !it.isEmpty }
    }

    private fun extractText(response: String): String {
        val obj = json.parseToJsonElement(response).jsonObject
        val parts = obj["candidates"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray
            ?: return response
        return parts.joinToString("\n") { it.jsonObject["text"]?.jsonPrimitive?.content ?: "" }
    }

    private companion object {
        // Alias « latest » : suit le dernier modèle Flash et conserve un quota gratuit, contrairement
        // à gemini-2.0-flash dont le niveau gratuit a été supprimé (429 avec limit: 0).
        const val MODEL = "gemini-flash-latest"
    }
}
