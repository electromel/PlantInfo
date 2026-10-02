package ch.electromel.plantinfo.data.remote.ai

import ch.electromel.plantinfo.domain.model.AiProviderType
import ch.electromel.plantinfo.domain.model.TokenUsage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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

/**
 * Ce qui distingue un fournisseur « compatible OpenAI » d'un autre : l'adresse, le modèle et deux
 * variantes de format. Le **modèle** doit avoir un tarif dans `AiPricing.rates` — c'est aussi lui
 * qui est rapporté dans [TokenUsage] — et doit accepter les **images** : l'identification envoie
 * les photos.
 */
data class CompatibleProviderConfig(
    val type: AiProviderType,
    /** URL complète de l'appel « chat completions ». */
    val endpoint: String,
    val model: String,
    /**
     * Mistral attend `"image_url": "data:…"` (une chaîne) là où OpenAI attend `{"url": "data:…"}`.
     */
    val imageUrlAsString: Boolean = false,
    val extraHeaders: Map<String, String> = emptyMap(),
) {
    val label: String get() = type.label

    companion object {
        // Relevé du 2026-09-28 ; tarifs correspondants dans AiPricing.rates.
        val OPENAI = CompatibleProviderConfig(
            AiProviderType.GPT, "https://api.openai.com/v1/chat/completions", "gpt-4o",
        )
        val DEEPSEEK = CompatibleProviderConfig(
            AiProviderType.DEEPSEEK, "https://api.deepseek.com/chat/completions", "deepseek-flash",
        )
        val GROK = CompatibleProviderConfig(
            AiProviderType.GROK, "https://api.x.ai/v1/chat/completions", "grok-4.3",
        )
        // Endpoint international (Singapour) : c'est là que se créent les clés de la console
        // internationale, et la seule région dont les tarifs figurent dans AiPricing.
        val QWEN = CompatibleProviderConfig(
            AiProviderType.QWEN,
            "https://dashscope-intl.aliyuncs.com/compatible-mode/v1/chat/completions",
            "qwen3-vl-plus",
        )
        val KIMI = CompatibleProviderConfig(
            AiProviderType.KIMI, "https://api.moonshot.ai/v1/chat/completions", "kimi-k2.6",
        )
        val MISTRAL = CompatibleProviderConfig(
            AiProviderType.MISTRAL, "https://api.mistral.ai/v1/chat/completions", "mistral-small-latest",
            imageUrlAsString = true,
        )
        // OpenRouter relaie des centaines de modèles ; on en fixe un, multimodal, au tarif connu.
        // Les en-têtes identifient l'app dans le tableau de bord de l'utilisateur.
        val OPENROUTER = CompatibleProviderConfig(
            AiProviderType.OPENROUTER, "https://openrouter.ai/api/v1/chat/completions",
            "~google/gemini-flash-latest",
            extraHeaders = mapOf("X-Title" to "PlantInfo"),
        )

        /** Tous les fournisseurs servis par [OpenAiCompatibleClient] hormis GPT (câblé à part). */
        val ADDITIONAL = listOf(DEEPSEEK, GROK, QWEN, KIMI, MISTRAL, OPENROUTER)
    }
}

/**
 * Client générique pour les API au format OpenAI « Chat Completions » (contenu `image_url` en data
 * URI), que partagent OpenAI, DeepSeek, xAI, Alibaba (mode compatible), Moonshot, Mistral et
 * OpenRouter. Le prompt et le schéma de réponse sont ceux d'[AiPrompt], communs à tous.
 */
class OpenAiCompatibleClient(
    private val client: OkHttpClient,
    private val config: CompatibleProviderConfig,
) : AiProvider {

    override val type: AiProviderType = config.type
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun analyze(input: AiAnalysisInput, apiKey: String): AiAnalysis {
        val instruction = AiPrompt.buildInstruction(input)
        val body = buildJsonObject {
            put("model", config.model)
            // Plafond large : plusieurs de ces modèles « réfléchissent » avant de répondre, et ces
            // jetons de raisonnement comptent dans la limite. Une fiche tronquée serait un JSON
            // illisible ; seuls les jetons réellement produits sont facturés.
            put("max_tokens", ANALYZE_MAX_TOKENS)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        addJsonObject { put("type", "text"); put("text", instruction) }
                        input.images.forEach { img ->
                            val dataUri = "data:${img.mimeType};base64,${HttpSupport.base64(img.bytes)}"
                            addJsonObject {
                                put("type", "image_url")
                                if (config.imageUrlAsString) {
                                    put("image_url", dataUri)
                                } else {
                                    putJsonObject("image_url") { put("url", dataUri) }
                                }
                            }
                        }
                    }
                }
            }
        }
        val response = post(body, apiKey)
        val text = HttpSupport.parseResponse(config.label) { extractText(response) }
        return AiPrompt.parse(text).copy(usage = extractUsage(response))
    }

    override suspend fun ask(prompt: String, apiKey: String): AiAnswer {
        val body = buildJsonObject {
            put("model", config.model)
            put("max_tokens", ASK_MAX_TOKENS)
            putJsonArray("messages") {
                addJsonObject { put("role", "user"); put("content", prompt) }
            }
        }
        val response = post(body, apiKey)
        val text = HttpSupport.parseResponse(config.label) { extractText(response) }
        return AiAnswer(text.trim(), extractUsage(response))
    }

    override suspend fun testKey(apiKey: String): Boolean {
        // Mini-génération plutôt que lister les modèles : la liste répond même sans crédit, ce qui
        // afficherait « Clé valide » pour une clé inutilisable.
        val body = buildJsonObject {
            put("model", config.model)
            put("max_tokens", 1)
            putJsonArray("messages") {
                addJsonObject { put("role", "user"); put("content", "ping") }
            }
        }
        post(body, apiKey)
        return true
    }

    private suspend fun post(body: JsonObject, apiKey: String): String {
        val request = Request.Builder()
            .url(config.endpoint)
            .header("Authorization", "Bearer $apiKey")
            .header("content-type", HttpSupport.JSON_MEDIA)
            .apply { config.extraHeaders.forEach { (name, value) -> header(name, value) } }
            .post(body.toString().toRequestBody())
            .build()
        return HttpSupport.execute(client, request, config.label)
    }

    /** Bloc `usage` de la réponse ; null si absent ou vide (rien à afficher). */
    private fun extractUsage(response: String): TokenUsage? {
        val usage = runCatching {
            json.parseToJsonElement(response).jsonObject["usage"]?.jsonObject
        }.getOrNull() ?: return null
        return usageFromJson(config.model, usage)
    }

    private fun extractText(response: String): String = textOfCompletion(response, config.label)

    internal companion object {
        const val ANALYZE_MAX_TOKENS = 16_000
        const val ASK_MAX_TOKENS = 4_000

        /**
         * Texte de la première réponse d'un « chat completions ». `content` est une chaîne chez la
         * plupart des fournisseurs, mais certains (Mistral en mode raisonnement, des modèles servis
         * par OpenRouter) le rendent en **liste de blocs** `[{"type":"text","text":"…"}]` ; `null`
         * quand le modèle n'a rien produit. Aucun de ces cas ne doit faire lever une exception
         * brute, ni passer le corps entier de la réponse pour du texte.
         */
        fun textOfCompletion(response: String, label: String): String {
            val message = (Json.parseToJsonElement(response).jsonObject["choices"] as? JsonArray)
                ?.firstOrNull()?.let { it as? JsonObject }?.get("message") as? JsonObject
            val text = when (val content = message?.get("content")) {
                is JsonArray -> content.mapNotNull { block ->
                    when (block) {
                        is JsonObject -> (block["text"] as? JsonPrimitive)?.contentOrNull
                        is JsonPrimitive -> block.contentOrNull
                        else -> null
                    }
                }.joinToString("\n")
                is JsonPrimitive -> content.contentOrNull
                else -> null
            }
            return text?.takeIf { it.isNotBlank() }
                ?: throw AiException(AiFailureReason.PARSE, "$label : réponse sans texte")
        }

        /**
         * Jetons facturés d'après le bloc `usage`. La sortie vaut `total_tokens − prompt_tokens`
         * quand le total est fourni : certains fournisseurs comptent le raisonnement dans
         * `completion_tokens`, d'autres à part (`reasoning_tokens`) mais toujours dans le total —
         * or il est facturé comme de la sortie. Sans total, on se contente de `completion_tokens`.
         */
        fun usageFromJson(model: String, usage: JsonObject): TokenUsage? {
            fun int(name: String) = usage[name]?.jsonPrimitive?.content?.toIntOrNull()
            val input = int("prompt_tokens") ?: 0
            val completion = int("completion_tokens") ?: 0
            val total = int("total_tokens")
            val output = if (total != null && total - input > completion) total - input else completion
            return TokenUsage(model = model, inputTokens = input, outputTokens = output).takeIf { !it.isEmpty }
        }
    }
}

/** GPT (OpenAI), injecté à part pour garder son nom dans le graphe Hilt. */
@Singleton
class OpenAiClient @Inject constructor(
    client: OkHttpClient,
) : AiProvider by OpenAiCompatibleClient(client, CompatibleProviderConfig.OPENAI)

/** Les fournisseurs compatibles OpenAI ajoutés à Claude, Gemini et GPT. */
@Singleton
class AdditionalAiProviders @Inject constructor(
    client: OkHttpClient,
) {
    val all: List<AiProvider> = CompatibleProviderConfig.ADDITIONAL.map { OpenAiCompatibleClient(client, it) }
}
