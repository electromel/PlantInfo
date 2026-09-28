package ch.electromel.plantinfo.data.keys

import ch.electromel.plantinfo.BuildConfig
import ch.electromel.plantinfo.domain.model.AiProviderType

/**
 * Fournisseurs pour lesquels une clé API peut être stockée. Chaque entrée porte la clé de
 * préférence utilisée dans le stockage chiffré, la valeur par défaut injectée depuis
 * dev-keys.properties via BuildConfig (§3.2), et le type d'IA qu'elle alimente (null pour Pl@ntNet).
 *
 * L'ordre de déclaration est celui du menu « Ajouter une clé » des Paramètres.
 */
enum class ApiProvider(
    val prefKey: String,
    val default: String,
    val createKeyUrl: String,
    val label: String,
    val aiType: AiProviderType?,
) {
    PLANTNET("key_plantnet", BuildConfig.DEFAULT_PLANTNET_API_KEY,
        "https://my.plantnet.org/account/settings", "Pl@ntNet", null),
    CLAUDE("key_claude", BuildConfig.DEFAULT_CLAUDE_API_KEY,
        "https://console.anthropic.com/settings/keys", "Claude (Anthropic)", AiProviderType.CLAUDE),
    GEMINI("key_gemini", BuildConfig.DEFAULT_GEMINI_API_KEY,
        "https://aistudio.google.com/apikey", "Gemini (Google)", AiProviderType.GEMINI),
    OPENAI("key_openai", BuildConfig.DEFAULT_OPENAI_API_KEY,
        "https://platform.openai.com/api-keys", "GPT (OpenAI)", AiProviderType.GPT),
    DEEPSEEK("key_deepseek", BuildConfig.DEFAULT_DEEPSEEK_API_KEY,
        "https://platform.deepseek.com/api_keys", "DeepSeek", AiProviderType.DEEPSEEK),
    GROK("key_grok", BuildConfig.DEFAULT_GROK_API_KEY,
        "https://console.x.ai", "Grok (xAI)", AiProviderType.GROK),
    // Console internationale (Singapour) : les clés de la console chinoise ne marchent pas sur
    // l'endpoint international utilisé par l'app, et inversement.
    QWEN("key_qwen", BuildConfig.DEFAULT_QWEN_API_KEY,
        "https://modelstudio.console.alibabacloud.com/ap-southeast-1/?tab=playground#/api-key",
        "Qwen (Alibaba Cloud)", AiProviderType.QWEN),
    // Plateforme internationale (.ai) ; celle de Chine continentale (.cn) a ses propres clés.
    KIMI("key_kimi", BuildConfig.DEFAULT_KIMI_API_KEY,
        "https://platform.moonshot.ai/console/api-keys", "Kimi (Moonshot AI)", AiProviderType.KIMI),
    MISTRAL("key_mistral", BuildConfig.DEFAULT_MISTRAL_API_KEY,
        "https://console.mistral.ai/api-keys", "Mistral AI", AiProviderType.MISTRAL),
    OPENROUTER("key_openrouter", BuildConfig.DEFAULT_OPENROUTER_API_KEY,
        "https://openrouter.ai/settings/keys", "OpenRouter", AiProviderType.OPENROUTER);

    companion object {
        fun forAiType(type: AiProviderType): ApiProvider? = entries.firstOrNull { it.aiType == type }
    }
}
