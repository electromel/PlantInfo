package ch.electromel.plantinfo.data.keys

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import ch.electromel.plantinfo.domain.model.AiProviderType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stockage chiffré des clés API et de l'ordre de repli entre fournisseurs IA (§3.1).
 *
 * Les clés sont chiffrées au repos via l'Android Keystore (EncryptedSharedPreferences). Elles ne
 * sont jamais écrites en clair. Au premier accès, une clé absente est pré-remplie depuis la valeur
 * par défaut de BuildConfig (dev-keys.properties) ; une clé déjà saisie n'est jamais écrasée (§3.2).
 */
@Singleton
class ApiKeyStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "plantinfo_secure_keys",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _state = MutableStateFlow(readSnapshot())

    /** État observable pour l'UI des paramètres (clés présentes/absentes, ordre de repli). */
    val state: StateFlow<KeysSnapshot> = _state.asStateFlow()

    init {
        prefillDefaultsIfAbsent()
    }

    /** Copie les valeurs par défaut de BuildConfig dans le stockage pour les clés encore absentes. */
    private fun prefillDefaultsIfAbsent() {
        var changed = false
        ApiProvider.entries.forEach { provider ->
            val existing = prefs.getString(provider.prefKey, null)
            if (existing.isNullOrBlank() && provider.default.isNotBlank()) {
                prefs.edit().putString(provider.prefKey, provider.default).apply()
                changed = true
            }
        }
        if (changed) _state.value = readSnapshot()
    }

    /** Retourne la clé enregistrée pour un fournisseur, ou null si aucune. */
    fun getKey(provider: ApiProvider): String? =
        prefs.getString(provider.prefKey, null)?.takeIf { it.isNotBlank() }

    /** Enregistre (ou efface si blank) la clé d'un fournisseur. */
    fun setKey(provider: ApiProvider, key: String) {
        prefs.edit().apply {
            if (key.isBlank()) remove(provider.prefKey) else putString(provider.prefKey, key.trim())
        }.apply()
        _state.value = readSnapshot()
    }

    /** Ordre de repli IA configuré (défaut Claude → Gemini → GPT). */
    fun fallbackOrder(): List<AiProviderType> {
        val stored = prefs.getString(KEY_FALLBACK_ORDER, null)
        if (stored.isNullOrBlank()) return AiProviderType.DEFAULT_FALLBACK_ORDER
        return stored.split(",").mapNotNull { name ->
            runCatching { AiProviderType.valueOf(name) }.getOrNull()
        }.filter { it != AiProviderType.NONE }.ifEmpty { AiProviderType.DEFAULT_FALLBACK_ORDER }
    }

    fun setFallbackOrder(order: List<AiProviderType>) {
        prefs.edit().putString(
            KEY_FALLBACK_ORDER,
            order.filter { it != AiProviderType.NONE }.joinToString(",") { it.name },
        ).apply()
        _state.value = readSnapshot()
    }

    /**
     * Mode « Gemini gratuit seul » : n'essaie que Gemini et ignore Claude/GPT (payants). Activé par
     * défaut pour éviter de consommer des crédits sur des comptes Anthropic/OpenAI sans solde.
     */
    fun freeGeminiOnly(): Boolean = prefs.getBoolean(KEY_FREE_GEMINI_ONLY, DEFAULT_FREE_GEMINI_ONLY)

    fun setFreeGeminiOnly(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FREE_GEMINI_ONLY, enabled).apply()
        _state.value = readSnapshot()
    }

    /**
     * Fournisseurs IA à essayer, dans l'ordre de repli, filtrés à ceux qui ont une clé.
     * Si le mode « Gemini gratuit seul » est actif, Claude et GPT sont exclus.
     * Vide = aucune IA disponible → l'appelant se rabat sur le résultat Pl@ntNet brut (§3.1).
     */
    fun availableAiProvidersInOrder(): List<AiProviderType> {
        val geminiOnly = freeGeminiOnly()
        return fallbackOrder().filter { type ->
            if (geminiOnly && type != AiProviderType.GEMINI) return@filter false
            ApiProvider.forAiType(type)?.let { getKey(it) != null } == true
        }
    }

    fun hasPlantNetKey(): Boolean = getKey(ApiProvider.PLANTNET) != null

    private fun readSnapshot(): KeysSnapshot = KeysSnapshot(
        present = ApiProvider.entries.associateWith { getKey(it) != null },
        fallbackOrder = fallbackOrder(),
        freeGeminiOnly = freeGeminiOnly(),
    )

    companion object {
        private const val KEY_FALLBACK_ORDER = "ai_fallback_order"
        private const val KEY_FREE_GEMINI_ONLY = "ai_free_gemini_only"
        private const val DEFAULT_FREE_GEMINI_ONLY = true
    }
}

/** Instantané non sensible de l'état des clés, pour l'UI (ne contient jamais les clés en clair). */
data class KeysSnapshot(
    val present: Map<ApiProvider, Boolean>,
    val fallbackOrder: List<AiProviderType>,
    val freeGeminiOnly: Boolean = true,
) {
    val hasPlantNet: Boolean get() = present[ApiProvider.PLANTNET] == true

    /**
     * Au moins un fournisseur IA réellement utilisable. Le mode « Gemini gratuit seul » est pris en
     * compte : une clé Claude enregistrée mais mise de côté par ce mode ne rend pas l'IA disponible,
     * et l'écran ne doit donc pas prétendre le contraire.
     */
    val hasAi: Boolean get() = ApiProvider.entries.any { provider ->
        present[provider] == true && provider.aiType != null &&
            (!freeGeminiOnly || provider == ApiProvider.GEMINI)
    }

    /** Aucune clé du tout : l'identification ne peut même pas démarrer. */
    val isEmpty: Boolean get() = !hasPlantNet && !hasAi
}
