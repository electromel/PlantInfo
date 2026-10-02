package ch.electromel.plantinfo.data.keys

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import ch.electromel.plantinfo.domain.model.AiProviderType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
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
    private val prefs: SharedPreferences = openWithRecovery(
        open = { createEncryptedPrefs(context) },
        reset = { resetEncryptedPrefs(context) },
    )

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
     * Fournisseurs IA à essayer, dans l'ordre de repli, filtrés à ceux qui ont une clé et — voir
     * [usableAiProviders] — au mode « Gemini gratuit seul » quand il s'applique.
     * Vide = aucune IA disponible → l'appelant se rabat sur le résultat Pl@ntNet brut (§3.1).
     */
    fun availableAiProvidersInOrder(): List<AiProviderType> =
        usableAiProviders(fallbackOrder(), freeGeminiOnly()) { type ->
            ApiProvider.forAiType(type)?.let { getKey(it) } != null
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
 * Fournisseurs IA à essayer : ceux de [order] qui ont une clé ([hasKey]).
 *
 * Le mode « Gemini gratuit seul » ([freeGeminiOnly], actif par défaut) n'écarte les autres IA que
 * **s'il y a une clé Gemini** pour prendre le relais. Sans elle, l'appliquer aurait pour seul effet
 * d'ignorer en silence la clé que l'utilisateur vient de saisir et de faire valider — et de lui
 * répondre « aucune clé IA » juste après un test réussi. La protection visée (ne pas consommer des
 * crédits payants par inadvertance quand un palier gratuit est disponible) n'a pas d'objet quand il
 * n'y en a pas.
 */
internal fun usableAiProviders(
    order: List<AiProviderType>,
    freeGeminiOnly: Boolean,
    hasKey: (AiProviderType) -> Boolean,
): List<AiProviderType> {
    val geminiOnly = freeGeminiOnly && hasKey(AiProviderType.GEMINI)
    return order.filter { type -> (!geminiOnly || type == AiProviderType.GEMINI) && hasKey(type) }
}

/**
 * Ouvre un stockage chiffré en se relevant d'une clé maîtresse devenue illisible.
 *
 * Le Keystore Android peut perdre ou invalider la clé maîtresse (réinitialisation par le
 * constructeur, changement de verrouillage, restauration d'un appareil) : le fichier de préférences
 * existe alors encore mais ne peut plus être déchiffré, et chaque lancement de l'application échoue
 * au même endroit — une boucle de plantages dont seule la désinstallation sortait. On retente donc
 * une fois (panne passagère), puis on le recrée à vide ([reset]) : l'utilisateur ressaisit ses clés,
 * ce que l'assistant de configuration sait faire, plutôt que de perdre l'application. Si la dernière
 * tentative échoue aussi, l'erreur remonte.
 */
internal fun <T> openWithRecovery(open: () -> T, reset: () -> Unit): T = try {
    open()
} catch (e: GeneralSecurityException) {
    recover(e, open, reset)
} catch (e: IOException) {
    recover(e, open, reset)
} catch (e: SecurityException) {
    // EncryptedSharedPreferences signale une valeur indéchiffrable par une SecurityException.
    recover(e, open, reset)
}

private fun <T> recover(cause: Exception, open: () -> T, reset: () -> Unit): T {
    Log.w("ApiKeyStore", "Stockage chiffré illisible : ${cause.javaClass.simpleName}")
    // Le Keystore a aussi des pannes passagères : un second essai les règle, et ne coûte rien. Les
    // clés de l'utilisateur ne sont détruites qu'en dernier recours.
    try {
        return open()
    } catch (e: GeneralSecurityException) {
        // illisible deux fois de suite : on recrée
    } catch (e: IOException) {
        // idem
    } catch (e: SecurityException) {
        // idem
    }
    Log.w("ApiKeyStore", "Stockage chiffré recréé à vide : les clés sont à ressaisir")
    reset()
    return open()
}

private const val SECURE_PREFS_FILE = "plantinfo_secure_keys"

// androidx.security:security-crypto est déclaré obsolète par Google depuis la 1.1.0 stable, sans
// remplaçant officiel équivalent. Le format de stockage existant reste lisible : on garde la
// bibliothèque en connaissance de cause (version stable plutôt qu'alpha), et ces deux fonctions sont
// les seuls points de contact — le jour où elle sera retirée, c'est ici qu'on la remplace.
@Suppress("DEPRECATION")
private fun createEncryptedPrefs(context: Context): SharedPreferences {
    val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    return EncryptedSharedPreferences.create(
        context,
        SECURE_PREFS_FILE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    ).also {
        // Lire tout le contenu dès l'ouverture : une valeur indéchiffrable échoue ici, où la
        // récupération est possible, plutôt qu'à la première lecture d'une clé en plein appel d'API.
        it.all
    }
}

@Suppress("DEPRECATION")
private fun resetEncryptedPrefs(context: Context) {
    context.deleteSharedPreferences(SECURE_PREFS_FILE)
    runCatching {
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
            deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        }
    }
}

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
     * Le mode « Gemini gratuit seul » écarte-t-il réellement les autres IA ? Seulement s'il y a une
     * clé Gemini pour prendre le relais : voir [usableAiProviders].
     */
    val geminiOnlyActive: Boolean get() = freeGeminiOnly && present[ApiProvider.GEMINI] == true

    /**
     * Au moins un fournisseur IA réellement utilisable. Le mode « Gemini gratuit seul » est pris en
     * compte : une clé Claude enregistrée mais mise de côté par ce mode ne rend pas l'IA disponible,
     * et l'écran ne doit donc pas prétendre le contraire.
     */
    val hasAi: Boolean get() = ApiProvider.entries.any { provider ->
        present[provider] == true && provider.aiType != null &&
            (!geminiOnlyActive || provider == ApiProvider.GEMINI)
    }

    /** Aucune clé du tout : l'identification ne peut même pas démarrer. */
    val isEmpty: Boolean get() = !hasPlantNet && !hasAi
}
