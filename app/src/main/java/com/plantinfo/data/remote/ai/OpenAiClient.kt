package com.plantinfo.data.remote.ai

import com.plantinfo.domain.model.AiProviderType
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

/** Client OpenAI Chat Completions (contenu image_url en data URI). */
@Singleton
class OpenAiClient @Inject constructor(
    private val client: OkHttpClient,
) : AiProvider {

    override val type = AiProviderType.GPT
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun analyze(input: AiAnalysisInput, apiKey: String): AiAnalysis {
        val instruction = AiPrompt.buildInstruction(input)
        val body = buildJsonObject {
            put("model", MODEL)
            put("max_tokens", 1500)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        addJsonObject { put("type", "text"); put("text", instruction) }
                        input.images.forEach { img ->
                            addJsonObject {
                                put("type", "image_url")
                                putJsonObject("image_url") {
                                    put("url", "data:${img.mimeType};base64,${HttpSupport.base64(img.bytes)}")
                                }
                            }
                        }
                    }
                }
            }
        }
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("content-type", HttpSupport.JSON_MEDIA)
            .post(body.toString().toRequestBody())
            .build()

        val response = HttpSupport.execute(client, request, "GPT")
        return AiPrompt.parse(extractText(response))
    }

    override suspend fun ask(prompt: String, apiKey: String): String {
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
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("content-type", HttpSupport.JSON_MEDIA)
            .post(body.toString().toRequestBody())
            .build()
        return extractText(HttpSupport.execute(client, request, "GPT")).trim()
    }

    override suspend fun testKey(apiKey: String): Boolean {
        // Mini-génération plutôt que lister les modèles : ce dernier réussit même sans crédit
        // (insufficient_quota), ce qui affichait « Clé valide » pour une clé inutilisable.
        val body = buildJsonObject {
            put("model", MODEL)
            put("max_tokens", 1)
            putJsonArray("messages") {
                addJsonObject { put("role", "user"); put("content", "ping") }
            }
        }
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("content-type", HttpSupport.JSON_MEDIA)
            .post(body.toString().toRequestBody())
            .build()
        HttpSupport.execute(client, request, "GPT")
        return true
    }

    private fun extractText(response: String): String {
        val obj = json.parseToJsonElement(response).jsonObject
        return obj["choices"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
            ?: response
    }

    private companion object {
        const val MODEL = "gpt-4o"
    }
}
