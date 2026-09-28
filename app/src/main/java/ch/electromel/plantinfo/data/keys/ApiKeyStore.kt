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
 * sont jamais écrites en clair. Une clé absente est pré-remplie **une seule fois** depuis la valeur
 * par défaut de BuildConfig (dev-keys.properties, build debug uniquement) ; une clé saisie n'est
 * jamais écrasée, et une clé effacée ne revient pas au lancement suivant (§3.2).
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

    /**
     * Copie les valeurs par défaut de BuildConfig dans le stockage pour les clés encore absentes, et
     * marque chaque fournisseur comme « pris en main » dès qu'il a eu une clé. Sans ce marqueur, une
     * clé effacée par l'utilisateur (périmée, par exemple) revenait à chaque lancement depuis
     * dev-keys.properties, avec l'alerte « clé à corriger » qui l'accompagne.
     */
    private fun prefillDefaultsIfAbsent() {
        var changed = false
        ApiProvider.entries.forEach { provider ->
            val handled = prefs.getBoolean(handledKey(provider), false)
            val existing = prefs.getString(provider.prefKey, null)
            when {
                !existing.isNullOrBlank() -> {
                    if (!handled) prefs.edit().putBoolean(handledKey(provider), true).apply()
                }
                shouldPrefill(handled, existing, provider.default) -> {
                    prefs.edit()
                        .putString(provider.prefKey, provider.default)
                        .putBoolean(handledKey(provider), true)
                        .apply()
                    changed = true
                }
            }
        }
        if (changed) _state.value = readSnapshot()
    }

    private fun handledKey(provider: ApiProvider) = "handled_${provider.prefKey}"

    /** Retourne la clé enregistrée pour un fournisseur, ou null si aucune. */
    fun getKey(provider: ApiProvider): String? =
        prefs.getString(provider.prefKey, null)?.takeIf { it.isNotBlank() }

    /** Enregistre (ou efface si blank) la clé d'un fournisseur. */
    fun setKey(provider: ApiProvider, key: String) {
        prefs.edit().apply {
            if (key.isBlank()) remove(provider.prefKey) else putString(provider.prefKey, key.trim())
            // Choix de l'utilisateur, y compris l'effacement : la valeur de dev ne revient plus.
            putBoolean(handledKey(provider), true)
        }.apply()
        _state.value = readSnapshot()
    }

    /**
     * Ordre de repli IA configuré (défaut [AiProviderType.DEFAULT_FALLBACK_ORDER]). Voir
     * [completeFallbackOrder] : un ordre enregistré avant l'ajout d'un fournisseur est complété.
     */
    fun fallbackOrder(): List<AiProviderType> =
        completeFallbackOrder(prefs.getString(KEY_FALLBACK_ORDER, null))

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

/**
 * Faut-il pré-remplir la clé depuis dev-keys.properties ? Seulement si le fournisseur n'a jamais eu
 * de clé ([handled] faux), qu'il n'en a pas, et qu'une valeur de dev existe.
 */
internal fun shouldPrefill(handled: Boolean, existing: String?, default: String): Boolean =
    !handled && existing.isNullOrBlank() && default.isNotBlank()

/**
 * Relit un ordre de repli enregistré (« CLAUDE,GEMINI,GPT ») et y ajoute, à la fin, les
 * fournisseurs qu'il ne connaît pas encore. Sans ce complément, un utilisateur qui avait réglé
 * l'ordre avant l'arrivée d'un nouveau fournisseur ne pourrait jamais l'utiliser : l'orchestrateur
 * ne parcourt que cet ordre. Les noms inconnus (fournisseur retiré) sont ignorés.
 */
internal fun completeFallbackOrder(stored: String?): List<AiProviderType> {
    val known = stored.orEmpty().split(",").mapNotNull { name ->
        runCatching { AiProviderType.valueOf(name.trim()) }.getOrNull()
    }.filter { it != AiProviderType.NONE }.distinct()
    return known + AiProviderType.DEFAULT_FALLBACK_ORDER.filter { it !in known }
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
