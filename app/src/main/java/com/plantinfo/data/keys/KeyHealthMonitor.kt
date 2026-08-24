package com.plantinfo.data.keys

import android.content.Context
import android.content.SharedPreferences
import com.plantinfo.data.remote.ai.AiFailureReason
import com.plantinfo.data.remote.ai.AiOrchestrator
import com.plantinfo.data.remote.plantnet.PlantNetClient
import com.plantinfo.data.remote.plantnet.PlantNetError
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verdict de la dernière vérification d'une clé. [UNVERIFIABLE] existe pour ne **jamais** confondre
 * « la clé est refusée » avec « on n'a pas pu joindre le service » : sans réseau, une clé valide
 * serait sinon marquée invalide et l'utilisateur irait la remplacer pour rien.
 */
enum class KeyHealth { UNKNOWN, VALID, INVALID, UNVERIFIABLE }

/** Une clé enregistrée qui ne fonctionne plus, avec la raison à montrer telle quelle. */
data class KeyProblem(
    val provider: ApiProvider,
    val reason: String,
)

/**
 * Surveille la validité des clés enregistrées et signale au lancement celles qui ne marchent plus
 * (§3.1) : clé révoquée, compte sans crédit, quota épuisé.
 *
 * **Coût maîtrisé.** Vérifier une clé consomme une vraie requête chez le fournisseur — et le palier
 * gratuit de Gemini se compte en dizaines de requêtes par jour. La vérification automatique est donc
 * limitée à une fois par [CHECK_INTERVAL_MS] et par clé ; le dernier verdict est conservé, si bien
 * que l'avertissement reste affiché à chaque lancement tant que la clé n'est pas corrigée, sans
 * relancer d'appel. L'écran Paramètres peut forcer une revérification immédiate.
 *
 * Un échec réseau ne dégrade jamais un verdict : il laisse le précédent en place.
 */
@Singleton
class KeyHealthMonitor @Inject constructor(
    @ApplicationContext context: Context,
    private val keyStore: ApiKeyStore,
    private val aiOrchestrator: AiOrchestrator,
    private val plantNetClient: PlantNetClient,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("plantinfo_key_health", Context.MODE_PRIVATE)

    // Une seule vérification à la fois : le démarrage de l'app et un appui sur « Revérifier »
    // peuvent se chevaucher, et rien ne justifie de payer deux fois les mêmes appels.
    private val mutex = Mutex()

    private val _problems = MutableStateFlow(readProblems())

    /** Clés enregistrées connues comme inutilisables. Vide = rien à signaler. */
    val problems: StateFlow<List<KeyProblem>> = _problems.asStateFlow()

    /**
     * Revérifie les clés enregistrées. Sans [force], ne teste que celles dont le dernier contrôle
     * remonte à plus de [CHECK_INTERVAL_MS] — ou dont on ne sait encore rien.
     */
    suspend fun refresh(force: Boolean = false) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            ApiProvider.entries.forEach { provider ->
                val key = keyStore.getKey(provider)
                if (key == null) {
                    // Clé effacée : son verdict n'a plus d'objet et ne doit plus être signalé.
                    forget(provider)
                    return@forEach
                }
                val due = force || now - lastCheckedAt(provider) >= CHECK_INTERVAL_MS ||
                    healthOf(provider) == KeyHealth.UNKNOWN
                if (due) record(provider, test(provider, key))
            }
            _problems.value = readProblems()
        }
    }

    /**
     * Enregistre le verdict d'un test déjà effectué ailleurs (écran Paramètres, à la saisie d'une
     * clé) : inutile de repayer l'appel, et l'avertissement doit disparaître dès la correction.
     */
    fun record(provider: ApiProvider, verdict: Verdict) {
        // Un échec réseau n'apprend rien sur la clé : on garde le verdict précédent intact.
        if (verdict.health != KeyHealth.UNVERIFIABLE) {
            prefs.edit()
                .putString(healthKey(provider), verdict.health.name)
                .putString(reasonKey(provider), verdict.reason)
                .putLong(checkedKey(provider), System.currentTimeMillis())
                .apply()
        }
        _problems.value = readProblems()
    }

    /** Oublie tout verdict pour un fournisseur (clé effacée ou remplacée). */
    fun forget(provider: ApiProvider) {
        prefs.edit()
            .remove(healthKey(provider))
            .remove(reasonKey(provider))
            .remove(checkedKey(provider))
            .apply()
        _problems.value = readProblems()
    }

    /** Verdict d'un test de clé : l'état retenu et, s'il est mauvais, la phrase à afficher. */
    data class Verdict(val health: KeyHealth, val reason: String?)

    private suspend fun test(provider: ApiProvider, key: String): Verdict {
        if (provider == ApiProvider.PLANTNET) return verdictFor(plantNetClient.testKey(key))
        // Tout fournisseur non-Pl@ntNet est un fournisseur IA ; l'absence de type ne peut venir que
        // d'une entrée ajoutée sans être câblée — on ne condamne alors pas la clé.
        val type = provider.aiType ?: return Verdict(KeyHealth.UNVERIFIABLE, null)
        return verdictFor(aiOrchestrator.testKey(type, key))
    }

    private fun readProblems(): List<KeyProblem> = ApiProvider.entries
        .filter { keyStore.getKey(it) != null && healthOf(it) == KeyHealth.INVALID }
        .map { KeyProblem(it, prefs.getString(reasonKey(it), null) ?: "clé inutilisable") }

    private fun healthOf(provider: ApiProvider): KeyHealth =
        prefs.getString(healthKey(provider), null)
            ?.let { runCatching { KeyHealth.valueOf(it) }.getOrNull() }
            ?: KeyHealth.UNKNOWN

    private fun lastCheckedAt(provider: ApiProvider): Long = prefs.getLong(checkedKey(provider), 0L)

    private fun healthKey(provider: ApiProvider) = "health_${provider.prefKey}"
    private fun reasonKey(provider: ApiProvider) = "reason_${provider.prefKey}"
    private fun checkedKey(provider: ApiProvider) = "checked_${provider.prefKey}"

    companion object {
        /** Une vérification par jour et par clé : assez pour prévenir, assez peu pour ne rien coûter. */
        const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000

        /**
         * Traduit l'échec d'un test Pl@ntNet en verdict. Quota et crédit épuisé comptent comme
         * « inutilisable » : du point de vue de l'utilisateur, la clé ne marche plus aujourd'hui,
         * même si elle n'est pas révoquée — et le message le dit sans mentir sur la cause.
         */
        fun verdictFor(error: PlantNetError?): Verdict = when (error) {
            null -> Verdict(KeyHealth.VALID, null)
            PlantNetError.INVALID_KEY -> Verdict(KeyHealth.INVALID, "clé refusée par Pl@ntNet (401/403)")
            PlantNetError.QUOTA -> Verdict(KeyHealth.INVALID, "quota Pl@ntNet atteint")
            PlantNetError.NETWORK, PlantNetError.SERVER -> Verdict(KeyHealth.UNVERIFIABLE, null)
            else -> Verdict(KeyHealth.UNVERIFIABLE, null)
        }

        /** Même traduction pour les fournisseurs IA. */
        fun verdictFor(reason: AiFailureReason?): Verdict = when (reason) {
            null -> Verdict(KeyHealth.VALID, null)
            AiFailureReason.INVALID_KEY -> Verdict(KeyHealth.INVALID, "clé refusée (401/403)")
            AiFailureReason.MISSING_KEY -> Verdict(KeyHealth.INVALID, "aucune clé")
            AiFailureReason.BILLING ->
                Verdict(KeyHealth.INVALID, "crédit épuisé sur le compte du fournisseur")
            AiFailureReason.QUOTA -> Verdict(KeyHealth.INVALID, "quota atteint")
            AiFailureReason.NETWORK, AiFailureReason.SERVER -> Verdict(KeyHealth.UNVERIFIABLE, null)
            // Réponse illisible ou motif inconnu : le service a répondu, mais rien ne prouve que la
            // clé soit en cause. On ne condamne pas la clé sur un doute.
            AiFailureReason.PARSE, AiFailureReason.UNKNOWN -> Verdict(KeyHealth.UNVERIFIABLE, null)
        }
    }
}
