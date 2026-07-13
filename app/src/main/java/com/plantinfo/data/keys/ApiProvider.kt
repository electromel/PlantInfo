package com.plantinfo.data.keys

import com.plantinfo.BuildConfig
import com.plantinfo.domain.model.AiProviderType

/**
 * Fournisseurs pour lesquels une clé API peut être stockée. Chaque entrée porte la clé de
 * préférence utilisée dans le stockage chiffré et la valeur par défaut injectée depuis
 * dev-keys.properties via BuildConfig (§3.2).
 */
enum class ApiProvider(
    val prefKey: String,
    val default: String,
    val createKeyUrl: String,
    val label: String,
) {
    PLANTNET("key_plantnet", BuildConfig.DEFAULT_PLANTNET_API_KEY,
        "https://my.plantnet.org/account/settings", "Pl@ntNet"),
    CLAUDE("key_claude", BuildConfig.DEFAULT_CLAUDE_API_KEY,
        "https://console.anthropic.com/settings/keys", "Claude (Anthropic)"),
    GEMINI("key_gemini", BuildConfig.DEFAULT_GEMINI_API_KEY,
        "https://aistudio.google.com/apikey", "Gemini (Google)"),
    OPENAI("key_openai", BuildConfig.DEFAULT_OPENAI_API_KEY,
        "https://platform.openai.com/api-keys", "GPT (OpenAI)");

    /** Type de fournisseur IA correspondant, ou null pour Pl@ntNet (non IA). */
    val aiType: AiProviderType?
        get() = when (this) {
            CLAUDE -> AiProviderType.CLAUDE
            GEMINI -> AiProviderType.GEMINI
            OPENAI -> AiProviderType.GPT
            PLANTNET -> null
        }

    companion object {
        fun forAiType(type: AiProviderType): ApiProvider? = when (type) {
            AiProviderType.CLAUDE -> CLAUDE
            AiProviderType.GEMINI -> GEMINI
            AiProviderType.GPT -> OPENAI
            AiProviderType.NONE -> null
        }
    }
}
